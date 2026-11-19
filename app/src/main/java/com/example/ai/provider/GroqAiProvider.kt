package com.example.ai.provider

import com.example.ai.provider.api.GroqApiService
import com.example.ai.provider.dto.GroqChatRequest
import com.example.ai.provider.dto.GroqMessage
import com.example.ai.provider.dto.GroqStreamChunk
import com.example.ai.sse.SseStreamParser
import com.example.core.network.AiProviderException
import com.example.domain.model.ProviderType
import com.example.domain.model.TokenUsage
import com.example.domain.model.VerifiedModelCatalog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

@Singleton
class GroqAiProvider @Inject constructor(
  private val apiService: GroqApiService,
  private val json: Json,
) : AiProvider {

  override val providerType: ProviderType = ProviderType.GROQ

  override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
    val apiKey = request.settings.groqApiKey.trim()
    if (apiKey.isEmpty()) {
      throw AiProviderException("Groq API key is not configured.")
    }
    if (request.turns.any { it.images.isNotEmpty() } && !request.model.capabilities.supportsVision) {
      throw AiProviderException(
        "${request.model.displayName} does not support image attachments."
      )
    }

    val messages = buildList {
      if (request.systemPrompt.isNotBlank()) {
        add(GroqMessage(role = "system", content = request.systemPrompt))
      }
      request.turns.forEach { turn ->
        add(
          GroqMessage(
            role = if (turn.isUser) "user" else "assistant",
            content = turn.text,
          )
        )
      }
    }

    val primaryModelId = request.model.id
    val firstAttempt =
      runCatching {
        streamWithModelId(apiKey, primaryModelId, messages, request.temperature) { event ->
          emit(event)
        }
      }

    if (firstAttempt.isFailure) {
      val err = firstAttempt.exceptionOrNull()
      val isModelAvailabilityError =
        err is AiProviderException &&
          (err.httpStatusCode == 400 || err.httpStatusCode == 404) &&
          primaryModelId != VerifiedModelCatalog.FALLBACK_GROQ_MODEL_ID
      if (isModelAvailabilityError) {
        streamWithModelId(
          apiKey,
          VerifiedModelCatalog.FALLBACK_GROQ_MODEL_ID,
          messages,
          request.temperature,
        ) { event ->
          emit(event)
        }
      } else {
        throw err!!
      }
    }

    emit(AiStreamEvent.Completed)
  }

  private suspend fun streamWithModelId(
    apiKey: String,
    modelId: String,
    messages: List<GroqMessage>,
    temperature: Double,
    onEvent: suspend (AiStreamEvent) -> Unit,
  ) {
    val payload =
      GroqChatRequest(
        model = modelId,
        messages = messages,
        stream = true,
        temperature = temperature,
      )

    SseStreamParser.parseDataEvents {
      apiService.streamChatCompletion(
        authorization = "Bearer $apiKey",
        request = payload,
      )
    }.collect { jsonChunk ->
      val chunk = json.decodeFromString<GroqStreamChunk>(jsonChunk)
      val delta = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
      if (delta.isNotEmpty()) {
        onEvent(AiStreamEvent.Delta(delta))
      }
      chunk.usage?.let { usage ->
        onEvent(
          AiStreamEvent.Usage(
            TokenUsage(
              promptTokens = usage.promptTokens,
              completionTokens = usage.completionTokens,
              totalTokens = usage.totalTokens,
            )
          )
        )
      }
    }
  }
}
