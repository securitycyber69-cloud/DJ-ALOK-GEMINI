package com.example.ai.provider

import com.example.ai.provider.api.ProxyApiService
import com.example.ai.provider.dto.ProxyChatRequest
import com.example.ai.provider.dto.ProxyImageDto
import com.example.ai.provider.dto.ProxyStreamEventDto
import com.example.ai.provider.dto.ProxyTurnDto
import com.example.ai.sse.SseStreamParser
import com.example.core.network.AiProviderException
import com.example.core.security.SecurityValidator
import com.example.domain.model.ProviderType
import com.example.domain.model.TokenUsage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

@Singleton
class ProxyAiProvider @Inject constructor(
  private val apiService: ProxyApiService,
  private val securityValidator: SecurityValidator,
  private val json: Json,
) : AiProvider {

  override val providerType: ProviderType = ProviderType.PROXY

  override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
    val validatedBase =
      securityValidator
        .validateHttpsUrl(request.settings.proxyBaseUrl)
        .getOrElse { err ->
          throw AiProviderException(err.message ?: "Invalid HTTPS proxy URL.")
        }

    val endpoint = "${validatedBase}v1/chat/stream"

    val payload =
      ProxyChatRequest(
        modelId = request.model.id,
        provider = request.model.provider.name.lowercase(),
        systemInstruction = request.systemPrompt,
        temperature = request.temperature,
        stream = true,
        messages =
          request.turns.map { turn ->
            ProxyTurnDto(
              role = if (turn.isUser) "user" else "assistant",
              text = turn.text,
              images =
                turn.images.map { img ->
                  ProxyImageDto(mimeType = img.mimeType, base64Data = img.base64Data)
                },
            )
          },
      )

    SseStreamParser.parseDataEvents {
      apiService.streamProxyChat(fullHttpsUrl = endpoint, request = payload)
    }.collect { jsonChunk ->
      val event = json.decodeFromString<ProxyStreamEventDto>(jsonChunk)
      if (!event.error.isNullOrBlank()) {
        throw AiProviderException(event.error)
      }
      if (!event.delta.isNullOrEmpty()) {
        emit(AiStreamEvent.Delta(event.delta))
      }
      event.usage?.let { usage ->
        emit(
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

    emit(AiStreamEvent.Completed)
  }
}
