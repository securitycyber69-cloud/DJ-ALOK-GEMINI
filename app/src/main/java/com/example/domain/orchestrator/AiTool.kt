package com.example.domain.orchestrator

import com.example.domain.model.TaskCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ToolContextInput(
  val prompt: String,
  val category: TaskCategory,
)

data class ToolExecutionResult(
  val toolName: String,
  val success: Boolean,
  val contextSnippet: String,
)

interface AiTool {
  val name: String

  fun shouldRun(input: ToolContextInput): Boolean

  suspend fun execute(input: ToolContextInput): ToolExecutionResult

  fun verifyResult(result: ToolExecutionResult): Boolean =
    result.success && result.contextSnippet.isNotBlank()
}

@Singleton
class DateTimeContextTool @Inject constructor() : AiTool {
  override val name: String = "device_datetime"

  override fun shouldRun(input: ToolContextInput): Boolean {
    val q = input.prompt.lowercase()
    return triggers.any { it in q }
  }

  override suspend fun execute(input: ToolContextInput): ToolExecutionResult {
    val now = Date()
    val formatter = SimpleDateFormat("EEEE, MMM d, yyyy 'at' h:mm a z", Locale.getDefault())
    val formatted = formatter.format(now)
    return ToolExecutionResult(
      toolName = name,
      success = true,
      contextSnippet = "Verified current device date and time: $formatted",
    )
  }

  companion object {
    private val triggers =
      listOf("today", "date", "time", "day is it", "current year", "আজকে", "তারিখ", "সময়", "आज", "तारीख")
  }
}

@Singleton
class CodingFormatGuidanceTool @Inject constructor() : AiTool {
  override val name: String = "coding_precision_guard"

  override fun shouldRun(input: ToolContextInput): Boolean =
    input.category == TaskCategory.CODING || input.category == TaskCategory.DATA_ANALYSIS

  override suspend fun execute(input: ToolContextInput): ToolExecutionResult {
    return ToolExecutionResult(
      toolName = name,
      success = true,
      contextSnippet =
        "Format all source code inside fenced markdown blocks with explicit language tags (e.g. ```kotlin) and verify edge cases.",
    )
  }
}
