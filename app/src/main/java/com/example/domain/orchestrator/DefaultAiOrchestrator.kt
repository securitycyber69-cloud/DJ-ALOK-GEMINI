package com.example.domain.orchestrator

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.ai.provider.AiTurn
import com.example.core.network.AiProviderException
import com.example.core.security.ClientRateLimiter
import com.example.core.security.SecurityValidator
import com.example.domain.agent.AgentExecutionResult
import com.example.domain.agent.AutonomousAgentOrchestrator
import com.example.domain.context.UnifiedContextEngine
import com.example.domain.model.AiResponseState
import com.example.domain.model.IntelligenceIntent
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.ProviderType
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TokenUsage
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.ModelRouter
import com.example.domain.router.RealtimeIntelligenceRouter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

@Singleton
class DefaultAiOrchestrator @Inject constructor(
  private val modelRouter: ModelRouter,
  private val settingsRepository: SettingsRepository,
  private val securityValidator: SecurityValidator,
  private val rateLimiter: ClientRateLimiter,
  private val providers: Map<ProviderType, @JvmSuppressWildcards AiProvider>,
  private val tools: Set<@JvmSuppressWildcards AiTool>,
  private val realtimeRouter: RealtimeIntelligenceRouter? = null,
  private val part3Pipeline: Part3IntelligencePipeline? = null,
  private val unifiedContextEngine: UnifiedContextEngine? = null,
  private val agentOrchestrator: AutonomousAgentOrchestrator? = null,
) : AiOrchestrator {

  // Backward-compatible constructor for isolated Phase 2 unit tests
  constructor(
    modelRouter: ModelRouter,
    settingsRepository: SettingsRepository,
    securityValidator: SecurityValidator,
    rateLimiter: ClientRateLimiter,
    providers: Map<ProviderType, AiProvider>,
    tools: Set<AiTool>,
  ) : this(
    modelRouter = modelRouter,
    settingsRepository = settingsRepository,
    securityValidator = securityValidator,
    rateLimiter = rateLimiter,
    providers = providers,
    tools = tools,
    realtimeRouter = null,
    part3Pipeline = null,
    unifiedContextEngine = null,
    agentOrchestrator = null,
  )

  override fun processMessage(request: OrchestrationRequest): Flow<OrchestrationEvent> = flow {
    emit(OrchestrationEvent.StateChanged(AiResponseState.Loading))

    val cleanPrompt = securityValidator.sanitizeUserText(request.userPrompt)
    if (cleanPrompt.isBlank() && request.currentTurnImages.isEmpty() && request.pendingMediaItems.isEmpty()) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error("Please enter a prompt or attach a file/media.", recoverable = true)
        )
      )
      return@flow
    }

    rateLimiter.checkAndRecord().onFailure { limitError ->
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(limitError.message ?: "Rate limit reached.", recoverable = true)
        )
      )
      return@flow
    }

    val intent =
      realtimeRouter?.classifyIntent(
        prompt = cleanPrompt,
        hasImages = request.currentTurnImages.isNotEmpty(),
        attachments = request.pendingMediaItems,
        deepResearchRequested = request.deepResearchRequested,
      ) ?: IntelligenceIntent.NORMAL_AI

    emit(
      OrchestrationEvent.StateChanged(
        AiResponseState.Thinking("Intent: ${intent.label} • Routing verified model…")
      )
    )

    val settings = settingsRepository.getSettingsSnapshot()
    val hasVisualInput =
      request.currentTurnImages.isNotEmpty() ||
        request.pendingMediaItems.any { it.kind == MediaAttachmentKind.VIDEO || it.kind == MediaAttachmentKind.PHOTO }

    val baseDecision =
      modelRouter
        .route(
          prompt = cleanPrompt,
          hasImages = hasVisualInput,
          imageCount = request.currentTurnImages.size,
          requestedModelId = request.requestedModelId,
          settings = settings,
        )
        .getOrElse { routingErr ->
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Error(
                routingErr.message ?: "Unable to route request.",
                recoverable = true,
              )
            )
          )
          return@flow
        }

    val decision: RoutingDecision = baseDecision.copy(intelligenceIntent = intent)
    emit(OrchestrationEvent.Routed(decision))

    if (request.historyMessages.isEmpty()) {
      emit(OrchestrationEvent.TitleSuggested(deriveConversationTitle(cleanPrompt)))
    }

    val prep =
      if (part3Pipeline != null && intent != IntelligenceIntent.DIRECT_CHAT && intent != IntelligenceIntent.NORMAL_AI) {
        part3Pipeline.handleSpecialIntent(
          intent = intent,
          cleanPrompt = cleanPrompt,
          request = request,
          decision = decision,
          settings = settings,
          collector = this,
        )
      } else {
        PipelinePreparation.ContinueWithLlm()
      }

    when (prep) {
      is PipelinePreparation.Aborted -> {
        emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Error(prep.errorMessage, recoverable = true)
          )
        )
        return@flow
      }
      is PipelinePreparation.AlreadyCompleted -> {
        emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Completed(
              fullText = prep.fullText,
              modelName = prep.modelName,
              tokenUsage = prep.tokenUsage,
            )
          )
        )
        return@flow
      }
      is PipelinePreparation.ContinueWithLlm -> Unit
    }

    val llmPrep = prep as PipelinePreparation.ContinueWithLlm

    val toolContextInput = ToolContextInput(prompt = cleanPrompt, category = decision.category)
    val verifiedToolNotes =
      if (intent == IntelligenceIntent.DIRECT_CHAT) {
        emptyList()
      } else {
        tools
          .filter { it.shouldRun(toolContextInput) }
          .mapNotNull { tool ->
            val res = tool.execute(toolContextInput)
            if (tool.verifyResult(res)) res.contextSnippet else null
          }
      }

    val unifiedSnapshot =
      unifiedContextEngine?.buildContext(
        query = cleanPrompt,
        model = decision.selectedModel,
        historyMessages = request.historyMessages,
        pendingMedia = request.pendingMediaItems,
        tokenBudget = settings.agentBudgetConfig.maxContextTokensBudget,
        memoryEnabled = settings.privacyPerformanceConfig.memoryEnabled,
      )

    val agentNotes =
      if (agentOrchestrator != null &&
        settings.agentBudgetConfig.multiAgentModeEnabled &&
        (request.isTaskMode || intent != IntelligenceIntent.DIRECT_CHAT)
      ) {
        val plannedSteps =
          agentOrchestrator.planWorkflow(
            goal = cleanPrompt,
            attachments = request.pendingMediaItems,
            hasImages = request.currentTurnImages.isNotEmpty(),
            budget = settings.agentBudgetConfig,
          )
        val agentResult =
          agentOrchestrator.executePlan(
            goal = cleanPrompt,
            initialSteps = plannedSteps,
            budget = settings.agentBudgetConfig,
            approvedSensitiveActions = false,
          ) { step ->
            Result.success("${step.description} ready")
          }
        when (agentResult) {
          is AgentExecutionResult.Completed -> agentResult.contextSummary
          is AgentExecutionResult.AwaitingApproval ->
            "Action requires user confirmation in Control Center / Automation: ${agentResult.pendingStep.description}"
          else -> ""
        }
      } else {
        ""
      }

    val combinedExtraContext =
      listOfNotNull(
        llmPrep.extraSystemContext.takeIf { it.isNotBlank() },
        unifiedSnapshot?.contextBlock?.takeIf { it.isNotBlank() },
        agentNotes.takeIf { it.isNotBlank() }?.let { "Autonomous Multi-Agent Plan:\n$it" },
      ).joinToString("\n\n")

    val systemPrompt =
      buildSystemPrompt(
        language = settings.voicePreferences.preferredLanguage,
        isTaskMode = request.isTaskMode,
        intent = intent,
        categoryLabel = decision.category.label,
        toolNotes = verifiedToolNotes,
        extraContext = combinedExtraContext,
        hasImages = request.currentTurnImages.isNotEmpty() || llmPrep.extraImages.isNotEmpty(),
      )

    val combinedImages = (request.currentTurnImages + llmPrep.extraImages).take(4)

    val recentTurns =
      request.historyMessages
        .filter { !it.isError }
        .takeLast(14)
        .map { msg -> AiTurn(isUser = msg.isUser, text = msg.text) } +
        AiTurn(
          isUser = true,
          text = cleanPrompt.ifBlank { "Please analyze the attached content in detail." },
          images = combinedImages,
        )

    val targetProviderKey =
      if (decision.useProxy) ProviderType.PROXY else decision.selectedModel.provider
    val provider =
      providers[targetProviderKey]
        ?: run {
          emit(
            OrchestrationEvent.StateChanged(
              AiResponseState.Error("Provider $targetProviderKey is not registered.")
            )
          )
          return@flow
        }

    emit(OrchestrationEvent.StateChanged(AiResponseState.Generating(decision.selectedModel.displayName)))

    val accumulated = StringBuilder()
    var latestUsage: TokenUsage? = null

    try {
      withTimeout(90_000L) {
        provider
          .streamChat(
            AiChatRequest(
              model = decision.selectedModel,
              systemPrompt = systemPrompt,
              turns = recentTurns,
              settings = settings,
              temperature = if (request.isTaskMode || intent == IntelligenceIntent.DATA_ANALYSIS) 0.25 else 0.7,
            )
          )
          .collect { event ->
            when (event) {
              is AiStreamEvent.Delta -> {
                accumulated.append(event.text)
                emit(
                  OrchestrationEvent.StateChanged(
                    AiResponseState.Streaming(
                      partialText = accumulated.toString(),
                      modelName = decision.selectedModel.displayName,
                      tokenUsage = latestUsage,
                    )
                  )
                )
              }
              is AiStreamEvent.Usage -> {
                latestUsage = event.usage
              }
              AiStreamEvent.Completed -> Unit
            }
          }
      }

      val finalText = accumulated.toString().trim()
      if (finalText.isEmpty()) {
        emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Error("Model returned an empty response. Try regenerating.")
          )
        )
      } else {
        if (llmPrep.preEmittedBlocks.isNotEmpty()) {
          emit(OrchestrationEvent.BlocksUpdated(llmPrep.preEmittedBlocks))
        }
        emit(
          OrchestrationEvent.StateChanged(
            AiResponseState.Completed(
              fullText = finalText,
              modelName = decision.selectedModel.displayName,
              tokenUsage = latestUsage,
            )
          )
        )
      }
    } catch (e: CancellationException) {
      emit(OrchestrationEvent.StateChanged(AiResponseState.Cancelled))
      throw e
    } catch (e: TimeoutCancellationException) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(
            "Response timed out after 90 seconds. Check your connection or try a faster model.",
            recoverable = true,
          )
        )
      )
    } catch (e: AiProviderException) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(e.message, recoverable = true)
        )
      )
    } catch (e: Exception) {
      emit(
        OrchestrationEvent.StateChanged(
          AiResponseState.Error(
            e.localizedMessage ?: "Unexpected error while communicating with AI provider.",
            recoverable = true,
          )
        )
      )
    }
  }

  override fun deriveConversationTitle(firstPrompt: String): String {
    val cleaned =
      firstPrompt
        .replace(Regex("[\\r\\n]+"), " ")
        .trim()
    if (cleaned.isBlank()) return "Multimodal Analysis"
    val words = cleaned.split(Regex("\\s+")).take(6)
    val title = words.joinToString(" ")
    return if (title.length > 42) "${title.take(39)}…" else title
  }

  private fun buildSystemPrompt(
    language: ResponseLanguage,
    isTaskMode: Boolean,
    intent: IntelligenceIntent,
    categoryLabel: String,
    toolNotes: List<String>,
    extraContext: String,
    hasImages: Boolean,
  ): String {
    return buildString {
      append("You are Mousomi AI, an accurate, helpful, multilingual assistant fluent in English, Bengali (বাংলা), and Hindi (हिन्दी). ")
      when (language) {
        ResponseLanguage.AUTO ->
          append("Respond in the exact language used by the user (English, Bengali, or Hindi). ")
        ResponseLanguage.BENGALI ->
          append("Respond clearly in natural Bengali (বাংলা), keeping technical terms readable when helpful. ")
        ResponseLanguage.HINDI ->
          append("Respond clearly in natural Hindi (हिन्दी). ")
        ResponseLanguage.ENGLISH ->
          append("Respond clearly in English. ")
      }
      if (isTaskMode) {
        append("The user is in Task Mode: deliver structured, actionable steps and concise execution checklists. ")
      }
      if (hasImages) {
        append("Analyze attached image(s) carefully—describe visual contents, text/OCR, and answer accurately without guessing unreadable text. ")
      }
      append("Routed pipeline: ${intent.label} ($categoryLabel). ")
      if (toolNotes.isNotEmpty()) {
        append("\nVerified tool context:\n")
        toolNotes.forEach { append("- $it\n") }
      }
      if (extraContext.isNotBlank()) {
        append("\n$extraContext\n")
      }
    }
  }
}
