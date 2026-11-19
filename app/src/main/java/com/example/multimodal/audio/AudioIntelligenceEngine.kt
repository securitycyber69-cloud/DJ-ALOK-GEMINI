package com.example.multimodal.audio

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.api.GroqApiService
import com.example.ai.provider.api.ProxyApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiInlineData
import com.example.ai.provider.dto.GeminiPart
import com.example.ai.provider.dto.ProxyAudioRequest
import com.example.core.network.RetryPolicy
import com.example.core.security.SecurityValidator
import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.AudioAnalysisResult
import com.example.domain.model.AudioTranscriptSegment
import com.example.domain.model.ConnectionMode
import com.example.domain.model.ProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class LlmAudioInsights(
  val detectedLanguage: String = "Auto",
  val summary: String = "",
  val transcript: String = "",
  val segments: List<AudioTranscriptSegment> = emptyList(),
  val keyPoints: List<String> = emptyList(),
  val translatedText: String? = null,
)

@Singleton
class AudioIntelligenceEngine @Inject constructor(
  private val audioProcessor: AudioProcessor,
  private val groqApiService: GroqApiService,
  private val geminiApiService: GeminiApiService,
  private val proxyApiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val json: Json,
) {

  suspend fun analyzeAudio(
    uriString: String,
    userPrompt: String,
    model: AiModelSpec,
    settings: AppSettings,
    onProgress: suspend (String) -> Unit,
  ): Result<AudioAnalysisResult> {
    val prepared =
      audioProcessor.prepareAudioTempFile(uriString, onProgress).getOrElse {
        return Result.failure(it)
      }

    try {
      // Strategy 1: Backend HTTPS Proxy audio endpoint
      if (settings.connectionMode == ConnectionMode.BACKEND_PROXY) {
        val baseUrl =
          securityValidator.validateHttpsUrl(settings.proxyBaseUrl).getOrElse {
            return Result.failure(it)
          }
        onProgress("Uploading audio to HTTPS backend transcription gateway…")
        val base64 = audioProcessor.readAsBase64(prepared.tempFile)
        val resp =
          RetryPolicy.withExponentialBackoff {
            proxyApiService.transcribeAudioViaProxy(
              fullHttpsUrl = "${baseUrl}v1/audio/transcribe",
              request =
                ProxyAudioRequest(
                  mimeType = prepared.metadata.mimeType,
                  base64Audio = base64,
                  languageHint = settings.voicePreferences.preferredLanguage.localeTag,
                  fileName = prepared.metadata.displayName,
                ),
            )
          }
        if (!resp.isSuccessful) {
          return Result.failure(IllegalStateException("Proxy audio transcription failed (HTTP ${resp.code()})."))
        }
        val body = resp.body() ?: return Result.failure(IllegalStateException("Empty proxy audio response."))
        return Result.success(
          AudioAnalysisResult(
            metadata = prepared.metadata,
            detectedLanguage = body.detectedLanguage,
            transcriptionEngine = "HTTPS Backend Proxy",
            summary = body.summary.ifBlank { body.text.take(280) },
            fullTranscript = body.text,
            segments = emptyList(),
            keyPoints = body.keyPoints,
          )
        )
      }

      // Strategy 2: Groq Whisper timestamped transcription + reasoning summary
      if (settings.groqApiKey.isNotBlank()) {
        val groqWhisperResult = transcribeWithGroqWhisper(prepared, settings, onProgress)
        if (groqWhisperResult.isSuccess) {
          val whisper = groqWhisperResult.getOrThrow()
          onProgress("Summarizing transcript & extracting key discussion points…")
          val enriched = enrichTranscriptWithLlm(whisper, userPrompt, model, settings)
          return Result.success(enriched)
        }
      }

      // Strategy 3: Gemini Multimodal Audio understanding
      if (settings.geminiApiKey.isNotBlank()) {
        return analyzeWithGeminiAudio(prepared, userPrompt, model, settings, onProgress)
      }

      return Result.failure(
        IllegalStateException(
          "Audio transcription requires a Groq API key (Whisper), a Gemini API key (multimodal audio), or an HTTPS Backend Proxy configured in Settings."
        )
      )
    } finally {
      audioProcessor.cleanup(prepared.tempFile)
    }
  }

  private suspend fun transcribeWithGroqWhisper(
    prepared: PreparedAudioFile,
    settings: AppSettings,
    onProgress: suspend (String) -> Unit,
  ): Result<AudioAnalysisResult> = runCatching {
    onProgress("Transcribing audio via Groq Whisper (whisper-large-v3-turbo)…")
    val apiKey = settings.groqApiKey.trim()
    val mediaType = prepared.metadata.mimeType.toMediaType()
    val filePart =
      MultipartBody.Part.createFormData(
        name = "file",
        filename = prepared.metadata.displayName,
        body = prepared.tempFile.asRequestBody(mediaType),
      )
    val modelPart = "whisper-large-v3-turbo".toRequestBody("text/plain".toMediaType())
    val formatPart = "verbose_json".toRequestBody("text/plain".toMediaType())

    val resp =
      RetryPolicy.withExponentialBackoff {
        groqApiService.transcribeAudio(
          authorization = "Bearer $apiKey",
          file = filePart,
          model = modelPart,
          responseFormat = formatPart,
        )
      }
    if (!resp.isSuccessful) {
      error("Groq Whisper error (HTTP ${resp.code()}).")
    }
    val body = resp.body() ?: error("Empty Whisper transcription response.")
    val segments =
      body.segments.map { seg ->
        AudioTranscriptSegment(
          startTimeFormatted = audioProcessor.formatSeconds(seg.start),
          endTimeFormatted = audioProcessor.formatSeconds(seg.end),
          text = seg.text.trim(),
        )
      }
    AudioAnalysisResult(
      metadata = prepared.metadata,
      detectedLanguage = body.language ?: "Detected automatically",
      transcriptionEngine = "Groq whisper-large-v3-turbo",
      summary = "",
      fullTranscript = body.text.trim(),
      segments = segments,
      keyPoints = emptyList(),
    )
  }

  private suspend fun enrichTranscriptWithLlm(
    rawResult: AudioAnalysisResult,
    userPrompt: String,
    model: AiModelSpec,
    settings: AppSettings,
  ): AudioAnalysisResult {
    val targetProvider =
      if (settings.geminiApiKey.isNotBlank() && model.provider == ProviderType.GEMINI) {
        ProviderType.GEMINI
      } else if (settings.groqApiKey.isNotBlank()) {
        ProviderType.GROQ
      } else {
        model.provider
      }

    val provider = providers[targetProvider] ?: return rawResult.copy(summary = rawResult.fullTranscript.take(260))
    val activeModel =
      if (targetProvider == ProviderType.GROQ && model.provider != ProviderType.GROQ) {
        com.example.domain.model.VerifiedModelCatalog.createGroqReasoningSpec(settings.groqReasoningModelId)
      } else {
        model
      }

    val prompt =
      """
      User request regarding audio (${rawResult.metadata.displayName}, language: ${rawResult.detectedLanguage}): "$userPrompt"
      Transcript:
      ${rawResult.fullTranscript.take(16_000)}

      Return ONLY JSON:
      {
        "detectedLanguage": "${rawResult.detectedLanguage}",
        "summary": "...",
        "keyPoints": ["..."],
        "translatedText": null
      }
      If the user asked for translation, include translatedText; otherwise keep null.
      """.trimIndent()

    val out = StringBuilder()
    runCatching {
      provider
        .streamChat(
          AiChatRequest(
            model = activeModel,
            systemPrompt = "Analyze the verified audio transcript accurately in the user's preferred language (${settings.voicePreferences.preferredLanguage.label}).",
            turns = listOf(AiTurn(isUser = true, text = prompt)),
            settings = settings,
            temperature = 0.25,
          )
        )
        .collect { evt ->
          if (evt is AiStreamEvent.Delta) out.append(evt.text)
        }
    }

    val parsed = parseAudioJson(out.toString())
    return rawResult.copy(
      detectedLanguage = parsed?.detectedLanguage?.ifBlank { rawResult.detectedLanguage } ?: rawResult.detectedLanguage,
      summary = parsed?.summary?.ifBlank { rawResult.fullTranscript.take(260) } ?: out.toString().ifBlank { rawResult.fullTranscript.take(260) },
      keyPoints = parsed?.keyPoints.orEmpty(),
      translatedText = parsed?.translatedText,
    )
  }

  private suspend fun analyzeWithGeminiAudio(
    prepared: PreparedAudioFile,
    userPrompt: String,
    model: AiModelSpec,
    settings: AppSettings,
    onProgress: suspend (String) -> Unit,
  ): Result<AudioAnalysisResult> = runCatching {
    onProgress("Processing audio via Gemini multimodal audio understanding…")
    val apiKey = settings.geminiApiKey.trim()
    val base64Audio = audioProcessor.readAsBase64(prepared.tempFile)
    val geminiModelId =
      if (model.provider == ProviderType.GEMINI) model.id else settings.hydrogenBackingModelId

    val prompt =
      """
      Listen carefully to the attached audio file (${prepared.metadata.displayName}).
      User request: "$userPrompt"
      Preferred response language: ${settings.voicePreferences.preferredLanguage.label}.
      Return ONLY valid JSON:
      {
        "detectedLanguage": "...",
        "summary": "...",
        "transcript": "...",
        "segments": [{"startTimeFormatted":"00:00","endTimeFormatted":"00:10","text":"..."}],
        "keyPoints": ["..."],
        "translatedText": null
      }
      """.trimIndent()

    val resp =
      RetryPolicy.withExponentialBackoff {
        geminiApiService.generateContent(
          model = geminiModelId,
          apiKey = apiKey,
          request =
            GeminiGenerateRequest(
              contents =
                listOf(
                  GeminiContent(
                    role = "user",
                    parts =
                      listOf(
                        GeminiPart(
                          inlineData =
                            GeminiInlineData(
                              mimeType = prepared.metadata.mimeType,
                              data = base64Audio,
                            )
                        ),
                        GeminiPart(text = prompt),
                      ),
                  )
                ),
              generationConfig = GeminiGenerationConfig(temperature = 0.2),
            ),
        )
      }

    if (!resp.isSuccessful) {
      error("Gemini multimodal audio request failed (HTTP ${resp.code()}).")
    }

    val rawText =
      resp.body()
        ?.candidates
        ?.firstOrNull()
        ?.content
        ?.parts
        ?.mapNotNull { it.text }
        ?.joinToString("")
        .orEmpty()

    val parsed = parseAudioJson(rawText)
    AudioAnalysisResult(
      metadata = prepared.metadata,
      detectedLanguage = parsed?.detectedLanguage ?: "Detected by Gemini",
      transcriptionEngine = "Gemini Multimodal Audio ($geminiModelId)",
      summary = parsed?.summary?.ifBlank { rawText.take(300) } ?: rawText,
      fullTranscript = parsed?.transcript?.ifBlank { rawText } ?: rawText,
      segments = parsed?.segments.orEmpty(),
      keyPoints = parsed?.keyPoints.orEmpty(),
      translatedText = parsed?.translatedText,
    )
  }

  private fun parseAudioJson(raw: String): LlmAudioInsights? {
    val cleaned =
      raw
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching {
      json.decodeFromString<LlmAudioInsights>(cleaned.substring(start, end + 1))
    }.getOrNull()
  }
}
