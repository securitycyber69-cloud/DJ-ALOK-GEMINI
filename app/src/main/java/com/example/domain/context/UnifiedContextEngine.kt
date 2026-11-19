package com.example.domain.context

import com.example.core.screen.ScreenShareSessionManager
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.model.AiModelSpec
import com.example.domain.model.DomainMessage
import com.example.domain.model.MemoryRecord
import com.example.domain.model.PendingMediaItem
import javax.inject.Inject
import javax.inject.Singleton

data class UnifiedContextSnapshot(
  val contextBlock: String,
  val injectedMemoryCount: Int,
  val estimatedTokens: Int,
  val capabilityNotice: String? = null,
)

@Singleton
class UnifiedContextEngine @Inject constructor(
  private val memoryManager: LongTermMemoryManager,
  private val screenShareSessionManager: ScreenShareSessionManager? = null,
) {
  suspend fun buildContext(
    query: String,
    model: AiModelSpec,
    historyMessages: List<DomainMessage>,
    pendingMedia: List<PendingMediaItem>,
    tokenBudget: Int = 18_000,
    memoryEnabled: Boolean = true,
  ): UnifiedContextSnapshot {
    val memories =
      if (memoryEnabled) memoryManager.retrieveRelevantMemories(query = query, limit = 5)
      else emptyList()

    val hasActiveScreen = screenShareSessionManager?.getLatestFrame() != null
    val hasVisualInput = pendingMedia.any { it.kind.name == "PHOTO" || it.kind.name == "VIDEO" } || hasActiveScreen

    val capabilityNotice =
      if (hasVisualInput && !model.capabilities.supportsVision) {
        "Note: Selected model (${model.displayName}) does not support raw vision tokens; fallback summary will be used."
      } else {
        null
      }

    return assembleContext(
      query = query,
      memories = memories,
      historyMessages = historyMessages,
      hasActiveScreenFrame = hasActiveScreen,
      capabilityNotice = capabilityNotice,
      tokenBudget = tokenBudget.coerceAtMost(model.capabilities.maxContextTokens),
    )
  }

  companion object {
    fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)

    fun assembleContext(
      query: String,
      memories: List<MemoryRecord>,
      historyMessages: List<DomainMessage>,
      hasActiveScreenFrame: Boolean,
      capabilityNotice: String?,
      tokenBudget: Int,
    ): UnifiedContextSnapshot {
      val seenLines = LinkedHashSet<String>()
      val sections = mutableListOf<String>()

      if (memories.isNotEmpty()) {
        val memLines =
          memories
            .map { "- [${it.category.label}] ${it.content.trim()}" }
            .filter { seenLines.add(it.lowercase()) }
        if (memLines.isNotEmpty()) {
          sections.add("Approved Long-Term Memory:\n${memLines.joinToString("\n")}")
        }
      }

      if (hasActiveScreenFrame) {
        sections.add("Live Screen Context: An active user-consented screen frame is available for visual grounding.")
      }

      if (!capabilityNotice.isNullOrBlank()) {
        sections.add("Model Capability Check: $capabilityNotice")
      }

      // Include recent non-duplicate conversation summary if helpful
      val recentUserLines =
        historyMessages
          .filter { !it.isError && it.text.isNotBlank() }
          .takeLast(4)
          .map { msg -> "${if (msg.isUser) "User" else "Assistant"}: ${msg.text.trim().take(240)}" }
          .filter { seenLines.add(it.lowercase()) }

      if (recentUserLines.isNotEmpty()) {
        sections.add("Compressed Recent Turns:\n${recentUserLines.joinToString("\n")}")
      }

      var assembled = sections.joinToString("\n\n").trim()
      val maxChars = (tokenBudget.coerceAtLeast(400) * 4)
      if (assembled.length > maxChars) {
        assembled = assembled.take(maxChars - 16).trimEnd() + "\n…[trimmed]"
      }

      return UnifiedContextSnapshot(
        contextBlock = assembled,
        injectedMemoryCount = memories.size,
        estimatedTokens = estimateTokens(assembled + query),
        capabilityNotice = capabilityNotice,
      )
    }
  }
}
