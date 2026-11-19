package com.example.ai.provider

import com.example.ai.provider.api.GeminiApiService
import com.example.ai.provider.dto.GeminiContent
import com.example.ai.provider.dto.GeminiGenerateRequest
import com.example.ai.provider.dto.GeminiGenerateResponse
import com.example.ai.provider.dto.GeminiGenerationConfig
import com.example.ai.provider.dto.GeminiInlineData
import com.example.ai.provider.dto.GeminiPart
import com.example.ai.sse.SseStreamParser
import com.example.core.network.AiProviderException
import com.example.domain.model.ProviderType
import com.example.domain.model.TokenUsage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

@Singleton
class GeminiAiProvider @Inject constructor(
  private val apiService: GeminiApiService,
  private val json: Json,
) : AiProvider {

  override val providerType: ProviderType = ProviderType.GEMINI

  override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
    val apiKey = request.settings.geminiApiKey.trim()
    if (apiKey.isEmpty()) {
      throw AiProviderException("Google Gemini API key is not configured.")
    }

    val contents =
      request.turns.map { turn ->
        val parts = buildList {
          turn.images.forEach { img ->
            add(
              GeminiPart(
                inlineData = GeminiInlineData(mimeType = img.mimeType, data = img.base64Data)
              )
            )
          }
          if (turn.text.isNotBlank()) {
            add(GeminiPart(text = turn.text))
          }
        }
        GeminiContent(
          role = if (turn.isUser) "user" else "model",
          parts = parts,
        )
      }

    val payload =
      GeminiGenerateRequest(
        contents = contents,
        systemInstruction =
          request.systemPrompt.takeIf { it.isNotBlank() }?.let {
            GeminiContent(parts = listOf(GeminiPart(text = it)))
          },
        generationConfig = GeminiGenerationConfig(temperature = request.temperature),
      )

    var lastUsage: TokenUsage? = null

    SseStreamParser.parseDataEvents {
      apiService.streamGenerateContent(
        model = request.model.id,
        apiKey = apiKey,
        request = payload,
      )
    }.collect { jsonChunk ->
      val parsed = json.decodeFromString<GeminiGenerateResponse>(jsonChunk)
      val deltaText =
        parsed.candidates
          .firstOrNull()
          ?.content
          ?.parts
          ?.mapNotNull { it.text }
          ?.joinToString(separator = "")
          .orEmpty()

      if (deltaText.isNotEmpty()) {
        emit(AiStreamEvent.Delta(deltaText))
      }

      parsed.usageMetadata?.let { meta ->
        val usage =
          TokenUsage(
            promptTokens = meta.promptTokenCount,
            completionTokens = meta.candidatesTokenCount,
            totalTokens = meta.totalTokenCount,
          )
        lastUsage = usage
        emit(AiStreamEvent.Usage(usage))
      }
    }

    lastUsage?.let { emit(AiStreamEvent.Usage(it)) }
    emit(AiStreamEvent.Completed)
  }
}
