package com.example.ai.provider

import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ProviderType
import com.example.domain.model.TokenUsage
import kotlinx.coroutines.flow.Flow

data class AiTurn(
  val isUser: Boolean,
  val text: String,
  val images: List<ProcessedImagePayload> = emptyList(),
)

data class AiChatRequest(
  val model: AiModelSpec,
  val systemPrompt: String,
  val turns: List<AiTurn>,
  val settings: AppSettings,
  val temperature: Double = 0.7,
)

sealed interface AiStreamEvent {
  data class Delta(val text: String) : AiStreamEvent

  data class Usage(val usage: TokenUsage) : AiStreamEvent

  data object Completed : AiStreamEvent
}

interface AiProvider {
  val providerType: ProviderType

  fun streamChat(request: AiChatRequest): Flow<AiStreamEvent>
}
