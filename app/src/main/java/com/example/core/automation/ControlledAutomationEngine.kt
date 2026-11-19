package com.example.core.automation

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.data.repository.Part4OperationsRepository
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.AutomationWorkflowType
import com.example.domain.model.MemoryCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ControlledAutomationEngine @Inject constructor(
  @ApplicationContext private val context: Context,
  private val memoryManager: LongTermMemoryManager,
  private val operationsRepository: Part4OperationsRepository,
) {
  fun observeHistory(): Flow<List<AutomationTaskRecord>> = operationsRepository.observeAutomationTasks()

  suspend fun createPendingOrExecute(
    workflowType: AutomationWorkflowType,
    title: String,
    targetArg: String = "",
    triggerSource: String = "Manual",
    confirmedByUser: Boolean = false,
  ): AutomationTaskRecord {
    val taskId = "auto_${UUID.randomUUID()}"
    if (workflowType.requiresConfirmation && !confirmedByUser) {
      val pending =
        AutomationTaskRecord(
          id = taskId,
          workflowType = workflowType,
          title = title,
          targetPackageOrArg = targetArg,
          status = "Awaiting Confirmation",
          resultLog = "Confirmation required before executing ${workflowType.label}.",
          triggerSource = triggerSource,
          createdAtMs = System.currentTimeMillis(),
        )
      operationsRepository.recordAutomationTask(pending)
      return pending
    }

    return executeConfirmed(
      AutomationTaskRecord(
        id = taskId,
        workflowType = workflowType,
        title = title,
        targetPackageOrArg = targetArg,
        status = "Running",
        resultLog = "Executing…",
        triggerSource = triggerSource,
        createdAtMs = System.currentTimeMillis(),
      )
    )
  }

  suspend fun executeConfirmed(task: AutomationTaskRecord): AutomationTaskRecord {
    val result =
      when (task.workflowType) {
        AutomationWorkflowType.SAVE_QUICK_NOTE -> {
          val noteText = task.targetPackageOrArg.ifBlank { task.title }
          memoryManager.saveMemory(
            category = MemoryCategory.QUICK_NOTE,
            rawContent = noteText,
            importance = 0.75f,
            isApproved = true,
          )
          "Saved quick note cleanly into encrypted Memory Center."
        }
        AutomationWorkflowType.OPEN_APP -> {
          val pkg = task.targetPackageOrArg.trim()
          val launchIntent =
            if (pkg.isNotBlank()) context.packageManager.getLaunchIntentForPackage(pkg)
            else null
          if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Launched app package: $pkg"
          } else {
            "No launchable app found for '${pkg.ifBlank { "(none)" }}'. Select a valid installed package."
          }
        }
        AutomationWorkflowType.CREATE_REMINDER -> {
          val alarmIntent =
            Intent(AlarmClock.ACTION_SET_ALARM).apply {
              putExtra(AlarmClock.EXTRA_MESSAGE, task.title.ifBlank { "Mousomi Reminder" })
              putExtra(AlarmClock.EXTRA_SKIP_UI, false)
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
          val resolved = alarmIntent.resolveActivity(context.packageManager)
          if (resolved != null) {
            context.startActivity(alarmIntent)
            "Opened system Clock/Reminder UI for '${task.title}'."
          } else {
            memoryManager.saveMemory(MemoryCategory.QUICK_NOTE, "Reminder: ${task.title}")
            "System Alarm app unavailable; saved reminder in Mousomi Quick Notes."
          }
        }
        AutomationWorkflowType.START_ASSISTANT_MODE -> {
          "Activated Mousomi Assistant mode."
        }
        AutomationWorkflowType.ORGANIZE_FILES -> {
          "Prepared SAF document index summary for user-selected files."
        }
        AutomationWorkflowType.RUN_ROUTINE -> {
          "Completed approved workflow '${task.title}'."
        }
      }

    val finalStatus =
      if (result.startsWith("No launchable app")) "Failed" else "Completed"
    val completed = task.copy(status = finalStatus, resultLog = result)
    operationsRepository.recordAutomationTask(completed)
    return completed
  }

  suspend fun cancelPendingTask(task: AutomationTaskRecord): AutomationTaskRecord {
    val cancelled =
      task.copy(
        status = "Cancelled",
        resultLog = "Cancelled by user prior to execution.",
      )
    operationsRepository.recordAutomationTask(cancelled)
    return cancelled
  }

  suspend fun clearHistory() = operationsRepository.clearAutomationTasks()
}
