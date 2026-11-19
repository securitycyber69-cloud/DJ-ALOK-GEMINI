package com.example.domain.orchestrator

import com.example.domain.model.AiResponseState
import com.example.domain.model.DomainMessage
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ResponseBlock
import com.example.domain.model.RoutingDecision
import kotlinx.coroutines.flow.Flow

data class OrchestrationRequest(
  val conversationId: String,
  val userPrompt: String,
  val historyMessages: List<DomainMessage>,
  val currentTurnImages: List<ProcessedImagePayload> = emptyList(),
  val requestedModelId: String? = null,
  val isTaskMode: Boolean = false,
  val pendingMediaItems: List<PendingMediaItem> = emptyList(),
  val deepResearchRequested: Boolean = false,
)

sealed interface OrchestrationEvent {
  data class StateChanged(val state: AiResponseState) : OrchestrationEvent

  data class Routed(val decision: RoutingDecision) : OrchestrationEvent

  data class TitleSuggested(val title: String) : OrchestrationEvent

  data class BlocksUpdated(val blocks: List<ResponseBlock>) : OrchestrationEvent
}

interface AiOrchestrator {
  fun processMessage(request: OrchestrationRequest): Flow<OrchestrationEvent>

  fun deriveConversationTitle(firstPrompt: String): String
}
