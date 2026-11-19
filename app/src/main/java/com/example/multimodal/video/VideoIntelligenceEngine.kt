package com.example.multimodal.video

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.ProviderType
import com.example.domain.model.VideoAnalysisResult
import com.example.domain.model.VideoTimelineSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class LlmVideoAnalysisPayload(
  val topic: String = "",
  val summary: String = "",
  val timeline: List<VideoTimelineSegment> = emptyList(),
  val transcriptSummary: String = "",
  val keyObservations: List<String> = emptyList(),
)

@Singleton
class VideoIntelligenceEngine @Inject constructor(
  private val videoProcessor: VideoProcessor,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val json: Json,
) {
  suspend fun analyzeVideo(
    uriString: String,
    userPrompt: String,
    model: AiModelSpec,
    settings: AppSettings,
    onProgress: suspend (String) -> Unit,
  ): Result<VideoAnalysisResult> {
    val requestedTimestamps = videoProcessor.parseRequestedTimestampsMs(userPrompt)
    val prepared =
      videoProcessor
        .processVideoUri(
          uriString = uriString,
          requestedTimestampsMs = requestedTimestamps,
          onProgress = onProgress,
        )
        .getOrElse { return Result.failure(it) }

    if (!model.capabilities.supportsVision) {
      return Result.failure(
        IllegalStateException(
          "${model.displayName} does not support multimodal video frame analysis. Route to Gemini 4 Hydrogen, Pro, or Flash."
        )
      )
    }

    val targetProviderKey =
      if (settings.connectionMode == ConnectionMode.BACKEND_PROXY) ProviderType.PROXY
      else model.provider
    val provider =
      providers[targetProviderKey]
        ?: return Result.failure(IllegalStateException("Provider $targetProviderKey unavailable."))

    onProgress("Analyzing ${prepared.sampledFrames.size} sampled frames with ${model.displayName}…")

    val frameTimestamps = prepared.sampledFrames.map { it.formattedTimestamp }
    val systemPrompt =
      """
      You are Mousomi AI's Video Content Analytics Engine.
      IMPORTANT TRANSPARENCY RULE: You are receiving ${prepared.sampledFrames.size} representative keyframes sampled at timestamps [${frameTimestamps.joinToString(", ")}] (total video duration ${videoProcessor.formatTimestamp(prepared.metadata.durationMs)}).
      Never pretend you saw frames outside these sampled timestamps. Base timeline and observations strictly on the visible frames and metadata.
      Metadata: ${prepared.metadata.displayName}, ${prepared.metadata.width}x${prepared.metadata.height}, AudioTrack=${prepared.metadata.hasAudioTrack} (${prepared.metadata.audioMimeType ?: "none"}).
      Chunks: ${prepared.chunkSummaries.joinToString("; ")}
      Respond ONLY with valid JSON:
      {
        "topic": "...",
        "summary": "...",
        "timeline": [
          {
            "startTimeFormatted": "00:00",
            "endTimeFormatted": "00:15",
            "sceneDescription": "...",
            "visualEvents": "...",
            "transcriptExcerpt": "Not available from silent keyframes unless captions/text are visible"
          }
        ],
        "transcriptSummary": "...",
        "keyObservations": ["..."]
      }
      """.trimIndent()

    val frameDescriptions =
      prepared.sampledFrames.mapIndexed { idx, frame ->
        "Frame ${idx + 1} captured at ${frame.formattedTimestamp}"
      }.joinToString("; ")

    val userTurnText =
      "User request: \"$userPrompt\"\nSampled frames in chronological order: $frameDescriptions. Analyze topic, scenes, transitions, visible text/captions, and timeline."

    val rawOut = StringBuilder()
    runCatching {
      provider
        .streamChat(
          AiChatRequest(
            model = model,
            systemPrompt = systemPrompt,
            turns =
              listOf(
                AiTurn(
                  isUser = true,
                  text = userTurnText,
                  images = prepared.sampledFrames.map { it.imagePayload },
                )
              ),
            settings = settings,
            temperature = 0.3,
          )
        )
        .collect { evt ->
          if (evt is AiStreamEvent.Delta) rawOut.append(evt.text)
        }
    }.onFailure { err ->
      return Result.failure(err)
    }

    val rawStr = rawOut.toString().trim()
    val jsonCandidate = extractJsonObject(rawStr)
    val parsed =
      runCatching { json.decodeFromString<LlmVideoAnalysisPayload>(jsonCandidate) }
        .getOrElse {
          LlmVideoAnalysisPayload(
            topic = "Video Frame Analysis",
            summary = rawStr.ifBlank { "Analyzed ${prepared.sampledFrames.size} sampled frames." },
            timeline =
              prepared.sampledFrames.map { frame ->
                VideoTimelineSegment(
                  startTimeFormatted = frame.formattedTimestamp,
                  endTimeFormatted = frame.formattedTimestamp,
                  sceneDescription = "Sampled frame inspected",
                  visualEvents = "Keyframe captured from ${prepared.metadata.displayName}",
                )
              },
            transcriptSummary =
              if (prepared.metadata.hasAudioTrack) {
                "Audio track (${prepared.metadata.audioMimeType ?: "audio"}) detected; no spoken words fabricated without audio transcription."
              } else {
                "No audio track detected in video container."
              },
            keyObservations = listOf(prepared.analysisMethodDisclosure),
          )
        }

    return Result.success(
      VideoAnalysisResult(
        metadata = prepared.metadata,
        analysisMethodNote = prepared.analysisMethodDisclosure,
        sampledFrameCount = prepared.sampledFrames.size,
        sampledTimestamps = frameTimestamps,
        summary = parsed.summary,
        topic = parsed.topic.ifBlank { prepared.metadata.displayName },
        timeline = parsed.timeline,
        transcriptSummary = parsed.transcriptSummary,
        keyObservations = parsed.keyObservations,
      )
    )
  }

  private fun extractJsonObject(raw: String): String {
    val cleaned =
      raw
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
  }
}
