package com.example.domain.agent

import com.example.data.repository.Part4OperationsRepository
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentStepStatus
import com.example.domain.model.AgentTaskLogItem
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.PendingMediaItem
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface AgentExecutionResult {
  data class Completed(
    val logItem: AgentTaskLogItem,
    val steps: List<AgentStepPlan>,
    val contextSummary: String,
  ) : AgentExecutionResult

  data class AwaitingApproval(
    val taskId: String,
    val goal: String,
    val steps: List<AgentStepPlan>,
    val pendingStep: AgentStepPlan,
  ) : AgentExecutionResult

  data class Cancelled(val steps: List<AgentStepPlan>) : AgentExecutionResult

  data class BudgetExceededOrFailed(
    val reason: String,
    val steps: List<AgentStepPlan>,
  ) : AgentExecutionResult
}

@Singleton
class AutonomousAgentOrchestrator @Inject constructor(
  private val operationsRepository: Part4OperationsRepository?,
) {
  constructor() : this(operationsRepository = null)
  private val _activeSteps = MutableStateFlow<List<AgentStepPlan>>(emptyList())
  val activeSteps: StateFlow<List<AgentStepPlan>> = _activeSteps.asStateFlow()

  private val _pendingApprovalGoal = MutableStateFlow<AgentExecutionResult.AwaitingApproval?>(null)
  val pendingApprovalGoal: StateFlow<AgentExecutionResult.AwaitingApproval?> =
    _pendingApprovalGoal.asStateFlow()

  fun planWorkflow(
    goal: String,
    attachments: List<PendingMediaItem> = emptyList(),
    hasImages: Boolean = false,
    hasActiveScreen: Boolean = false,
    budget: AgentBudgetConfig = AgentBudgetConfig(),
  ): List<AgentStepPlan> {
    val lower = goal.lowercase()
    val steps = mutableListOf<AgentStepPlan>()

    val planStepId = "step_plan"
    steps.add(
      AgentStepPlan(
        id = planStepId,
        agent = AgentRole.TASK_PLANNING,
        description = "Decompose goal & select specialist agents",
      )
    )

    val memoryStepId = "step_memory"
    steps.add(
      AgentStepPlan(
        id = memoryStepId,
        agent = AgentRole.MEMORY,
        description = "Retrieve relevant long-term memory context",
        dependsOnIds = listOf(planStepId),
      )
    )

    if (hasActiveScreen || lower.containsAny("screen", "what's on my screen", "overlay", "annotate")) {
      steps.add(
        AgentStepPlan(
          id = "step_screen",
          agent = AgentRole.SCREEN_INTELLIGENCE,
          description = "Inspect user-consented screen frame or marked region",
          dependsOnIds = listOf(planStepId),
        )
      )
    }

    if (hasImages || attachments.any { it.kind == MediaAttachmentKind.PHOTO }) {
      steps.add(
        AgentStepPlan(
          id = "step_vision",
          agent = AgentRole.VISION,
          description = "Extract visual details & OCR",
          dependsOnIds = listOf(planStepId),
        )
      )
    }

    if (attachments.any { it.kind == MediaAttachmentKind.VIDEO } || lower.contains("video")) {
      steps.add(
        AgentStepPlan(
          id = "step_video",
          agent = AgentRole.VIDEO_ANALYSIS,
          description = "Analyze video keyframes & motion timeline",
          dependsOnIds = listOf(planStepId),
        )
      )
    }

    if (attachments.any { it.kind == MediaAttachmentKind.AUDIO } || lower.contains("voice")) {
      steps.add(
        AgentStepPlan(
          id = "step_voice",
          agent = AgentRole.VOICE,
          description = "Process audio/voice signal context",
          dependsOnIds = listOf(planStepId),
        )
      )
    }

    if (attachments.any { it.kind == MediaAttachmentKind.DOCUMENT } ||
      lower.containsAny("pdf", "docx", "xlsx", "csv", "document")
    ) {
      steps.add(
        AgentStepPlan(
          id = "step_document",
          agent = AgentRole.DOCUMENT,
          description = "Parse document structure & tables",
          dependsOnIds = listOf(planStepId),
        )
      )
    }

    if (lower.containsAny("chart", "graph", "csv", "dataset", "statistics", "trend", "analytics")) {
      steps.add(
        AgentStepPlan(
          id = "step_data",
          agent = AgentRole.DATA_ANALYST,
          description = "Run deterministic numerical & statistical evaluation",
          dependsOnIds = listOf(memoryStepId),
        )
      )
    }

    if (lower.containsAny("deep research", "comprehensive report", "literature", "whitepaper")) {
      steps.add(
        AgentStepPlan(
          id = "step_deep_research",
          agent = AgentRole.DEEP_RESEARCH,
          description = "Run multi-source iterative deep research",
          dependsOnIds = listOf(planStepId),
        )
      )
    } else if (lower.containsAny("latest", "today", "news", "current", "price", "search", "web")) {
      steps.add(
        AgentStepPlan(
          id = "step_web_search",
          agent = AgentRole.WEB_SEARCH,
          description = "Retrieve and verify real-time web citations",
          dependsOnIds = listOf(planStepId),
        )
      )
    } else if (lower.containsAny("research", "compare", "study")) {
      steps.add(
        AgentStepPlan(
          id = "step_research",
          agent = AgentRole.RESEARCH,
          description = "Synthesize structured multi-angle findings",
          dependsOnIds = listOf(planStepId),
        )
      )
    }

    if (lower.containsAny("code", "kotlin", "python", "sql", "bug", "compile", "algorithm", "function")) {
      steps.add(
        AgentStepPlan(
          id = "step_coding",
          agent = AgentRole.CODING,
          description = "Architect code solution & edge-case checks",
          dependsOnIds = listOf(memoryStepId),
        )
      )
    }

    if (lower.containsAny("open ", "reminder", "alarm", "notify", "automation", "launch ")) {
      steps.add(
        AgentStepPlan(
          id = "step_android_assistant",
          agent = AgentRole.ANDROID_ASSISTANT,
          description = "Prepare confirmation-gated Android action",
          dependsOnIds = listOf(planStepId),
          requiresUserApproval = true,
        )
      )
    }

    val verifyDepends = steps.map { it.id }.takeLast(3)
    steps.add(
      AgentStepPlan(
        id = "step_verify",
        agent = AgentRole.VERIFICATION,
        description = "Verify grounding, safety & schema accuracy",
        dependsOnIds = verifyDepends,
      )
    )

    return steps.take(budget.maxSteps.coerceIn(2, 10))
  }

  suspend fun executePlan(
    goal: String,
    initialSteps: List<AgentStepPlan>,
    budget: AgentBudgetConfig,
    approvedSensitiveActions: Boolean = false,
    stepExecutor: suspend (AgentStepPlan) -> Result<String>,
  ): AgentExecutionResult {
    val startMs = System.currentTimeMillis()
    val mutableSteps = initialSteps.toMutableList()
    _activeSteps.value = mutableSteps.toList()
    _pendingApprovalGoal.value = null

    val taskId = "agent_${UUID.randomUUID()}"

    try {
      for (index in mutableSteps.indices) {
        val elapsed = System.currentTimeMillis() - startMs
        if (elapsed > budget.maxDurationMs) {
          val aborted =
            mutableSteps.mapIndexed { idx, s ->
              if (idx >= index) s.copy(status = AgentStepStatus.CANCELLED) else s
            }
          _activeSteps.value = aborted
          return AgentExecutionResult.BudgetExceededOrFailed(
            reason = "Execution time budget (${budget.maxDurationMs}ms) exceeded.",
            steps = aborted,
          )
        }

        val step = mutableSteps[index]
        val unfinishedDep =
          step.dependsOnIds.any { depId ->
            mutableSteps.firstOrNull { it.id == depId }?.status != AgentStepStatus.COMPLETED
          }
        if (unfinishedDep) {
          mutableSteps[index] =
            step.copy(status = AgentStepStatus.FAILED, outputSummary = "Blocked by unfinished dependency.")
          _activeSteps.value = mutableSteps.toList()
          return AgentExecutionResult.BudgetExceededOrFailed(
            reason = "Dependency failed for ${step.agent.label}.",
            steps = mutableSteps.toList(),
          )
        }

        if (step.requiresUserApproval && !approvedSensitiveActions) {
          mutableSteps[index] = step.copy(status = AgentStepStatus.AWAITING_APPROVAL)
          _activeSteps.value = mutableSteps.toList()
          val awaiting =
            AgentExecutionResult.AwaitingApproval(
              taskId = taskId,
              goal = goal,
              steps = mutableSteps.toList(),
              pendingStep = mutableSteps[index],
            )
          _pendingApprovalGoal.value = awaiting
          return awaiting
        }

        mutableSteps[index] = step.copy(status = AgentStepStatus.RUNNING)
        _activeSteps.value = mutableSteps.toList()

        var attempts = 0
        var lastError: Throwable? = null
        var stepOutput: String? = null

        while (attempts <= budget.maxRetriesPerStep.coerceIn(0, 3)) {
          val attemptResult = stepExecutor(mutableSteps[index])
          if (attemptResult.isSuccess) {
            stepOutput = attemptResult.getOrNull().orEmpty()
            break
          } else {
            lastError = attemptResult.exceptionOrNull()
            attempts++
            mutableSteps[index] = mutableSteps[index].copy(retryCount = attempts)
          }
        }

        if (stepOutput == null) {
          mutableSteps[index] =
            mutableSteps[index].copy(
              status = AgentStepStatus.FAILED,
              outputSummary = lastError?.message ?: "Step execution failed.",
            )
          _activeSteps.value = mutableSteps.toList()
          return AgentExecutionResult.BudgetExceededOrFailed(
            reason = "${step.agent.label} failed after $attempts attempt(s): ${lastError?.message ?: "error"}",
            steps = mutableSteps.toList(),
          )
        } else {
          mutableSteps[index] =
            mutableSteps[index].copy(
              status = AgentStepStatus.COMPLETED,
              outputSummary = stepOutput,
            )
          _activeSteps.value = mutableSteps.toList()
        }
      }

      val duration = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
      val summary =
        mutableSteps.joinToString("\n") { s -> "• ${s.agent.label}: ${s.outputSummary}" }
      val logItem =
        AgentTaskLogItem(
          id = taskId,
          goal = goal.take(140),
          selectedAgents = mutableSteps.map { it.agent.label },
          status = "Completed",
          summary = summary,
          tokenUsage = (summary.length / 4).coerceAtLeast(10),
          durationMs = duration,
          createdAtMs = startMs,
        )
      operationsRepository?.recordAgentTask(logItem)
      return AgentExecutionResult.Completed(
        logItem = logItem,
        steps = mutableSteps.toList(),
        contextSummary = summary,
      )
    } catch (e: CancellationException) {
      val cancelledSteps =
        mutableSteps.map { s ->
          if (s.status == AgentStepStatus.RUNNING || s.status == AgentStepStatus.PENDING) {
            s.copy(status = AgentStepStatus.CANCELLED)
          } else s
        }
      _activeSteps.value = cancelledSteps
      return AgentExecutionResult.Cancelled(cancelledSteps)
    }
  }

  fun dismissPendingApproval() {
    _pendingApprovalGoal.value = null
  }

  fun clearActiveSteps() {
    _activeSteps.value = emptyList()
    _pendingApprovalGoal.value = null
  }

  private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { contains(it) }
}
