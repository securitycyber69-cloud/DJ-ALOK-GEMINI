package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class EdgeLightPreset(val label: String, val subtitle: String) {
  GEMINI_FLOW("Gemini Flow", "Flowing blue, cyan, purple & pink perimeter gradient"),
  RAINBOW_SWEEP("Rainbow Sweep", "Full-spectrum smooth continuous perimeter sweep"),
  BLUE_PURPLE_BREATHING("Blue-Purple Breath", "Soft breathing indigo & violet halo"),
  ROTATING_GRADIENT("Rotating Aura", "Continuous orbital color rotation around screen edges"),
  AI_THINKING("AI Thinking", "Focused deep-blue & violet cognitive spin"),
  VOICE_LISTENING("Voice Listening", "Audio-reactive cyan-blue voice pulse"),
  LIVE_SEARCH("Live Search", "Crisp sky-blue perimeter scan sweep"),
  DEEP_RESEARCH("Deep Research", "Continuous emerald-indigo multi-pass research aura"),
  AI_GENERATING("AI Generating", "Vivid flowing multicolor generation shimmer"),
  NOTIFICATION_ALERT("Notification Alert", "Brief luminous notification rim pulse"),
  CUSTOM_GRADIENT("Custom Gradient", "User-customized 4-stop perimeter gradient"),
}

enum class EdgeLightVisualState(val label: String) {
  IDLE("Idle"),
  LISTENING("Listening"),
  THINKING("Thinking"),
  SEARCHING("Searching"),
  RESEARCHING("Deep Research"),
  GENERATING("Generating"),
  SPEAKING("Speaking"),
  COMPLETED("Completed"),
  ERROR("Error"),
}

@Serializable
data class EdgeLightConfig(
  val enabled: Boolean = true,
  val preset: EdgeLightPreset = EdgeLightPreset.GEMINI_FLOW,
  val brightness: Float = 0.85f,
  val animationSpeed: Float = 1.0f,
  val borderThicknessDp: Float = 5.5f,
  val cornerRadiusDp: Float = 36f,
  val primaryColorArgb: Long = 0xFF3B82F6,
  val secondaryColorArgb: Long = 0xFF06B6D4,
  val tertiaryColorArgb: Long = 0xFFA855F7,
  val quaternaryColorArgb: Long = 0xFFEC4899,
  val systemOverlayEnabled: Boolean = false,
  val batterySaverMode: Boolean = false,
  val reducedMotion: Boolean = false,
  val scheduleEnabled: Boolean = false,
  val scheduleStartHour: Int = 7,
  val scheduleEndHour: Int = 23,
  val notificationAlertPulseEnabled: Boolean = true,
  val enabledPackageAllowlist: Set<String> = emptySet(),
)

@Serializable
data class FloatingOrbConfig(
  val enabled: Boolean = false,
  val inAppPreviewVisible: Boolean = true,
  val sizeDp: Float = 56f,
  val opacity: Float = 0.94f,
  val positionXFraction: Float = 0.92f,
  val positionYFraction: Float = 0.62f,
  val snapToEdge: Boolean = true,
  val doubleTapForVoice: Boolean = true,
  val systemOverlayActive: Boolean = false,
)

enum class OrbQuickAction(val label: String) {
  ASK_MOUSOMI("Ask Mousomi AI"),
  START_VOICE("Start Voice Assistant"),
  ANALYZE_SCREEN("Analyze Current Screen"),
  START_SCREEN_SHARE("Start Screen Sharing"),
  DRAW_ON_SCREEN("Draw on Screen"),
  QUICK_NOTE("Quick Note"),
  OPEN_FULL_ASSISTANT("Open Full Assistant"),
}

@Serializable
enum class DrawingTool(val label: String) {
  FREEHAND("Pen"),
  HIGHLIGHTER("Highlight"),
  ARROW("Arrow"),
  CIRCLE("Circle"),
  RECTANGLE("Box"),
  POINTER("Pointer"),
  TEXT("Text"),
  ERASER("Eraser"),
}

@Serializable
data class NormPoint(
  val x: Float,
  val y: Float,
)

@Serializable
data class AnnotationStroke(
  val id: String,
  val tool: DrawingTool,
  val colorArgb: Long,
  val strokeWidthPx: Float,
  val alpha: Float,
  val points: List<NormPoint> = emptyList(),
  val textLabel: String = "",
)

@Serializable
data class NormBoundingBox(
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) {
  fun isValid(): Boolean = right > left && bottom > top
}

enum class ScreenShareStatus(val label: String) {
  STOPPED("Stopped"),
  REQUESTING_CONSENT("Waiting for Consent"),
  ACTIVE("Screen Sharing Active"),
  ANALYZING("Analyzing Frame"),
}

@Serializable
data class ScreenFrameRecord(
  val id: String,
  val filePath: String,
  val width: Int,
  val height: Int,
  val perceptualHash: Long,
  val capturedAtMs: Long,
  val disclosureNote: String = "Periodic frame capture (user-consented MediaProjection)",
)

@Serializable
data class ScreenIntelligenceConfig(
  val captureIntervalSeconds: Int = 4,
  val duplicateThresholdBits: Int = 5,
  val maxSessionFrames: Int = 5,
  val analyzeWithVisionAuto: Boolean = false,
)

@Serializable
enum class AgentRole(val label: String) {
  TASK_PLANNING("Task Planning Agent"),
  RESEARCH("Research Agent"),
  WEB_SEARCH("Web Search Agent"),
  DEEP_RESEARCH("Deep Research Agent"),
  DATA_ANALYST("Data Analyst Agent"),
  CODING("Coding Agent"),
  VISION("Vision Agent"),
  VIDEO_ANALYSIS("Video Analysis Agent"),
  DOCUMENT("Document Agent"),
  MEMORY("Memory Agent"),
  VOICE("Voice Agent"),
  SCREEN_INTELLIGENCE("Screen Intelligence Agent"),
  ANDROID_ASSISTANT("Android Assistant Agent"),
  VERIFICATION("Verification Agent"),
}

@Serializable
enum class AgentStepStatus {
  PENDING,
  AWAITING_APPROVAL,
  RUNNING,
  COMPLETED,
  FAILED,
  CANCELLED,
}

@Serializable
data class AgentStepPlan(
  val id: String,
  val agent: AgentRole,
  val description: String,
  val dependsOnIds: List<String> = emptyList(),
  val requiresUserApproval: Boolean = false,
  val status: AgentStepStatus = AgentStepStatus.PENDING,
  val outputSummary: String = "",
  val retryCount: Int = 0,
)

@Serializable
data class AgentBudgetConfig(
  val multiAgentModeEnabled: Boolean = true,
  val maxSteps: Int = 6,
  val maxRetriesPerStep: Int = 2,
  val maxDurationMs: Long = 75_000L,
  val maxContextTokensBudget: Int = 18_000,
)

@Serializable
data class AgentTaskLogItem(
  val id: String,
  val goal: String,
  val selectedAgents: List<String>,
  val status: String,
  val summary: String,
  val tokenUsage: Int,
  val durationMs: Long,
  val createdAtMs: Long,
)

@Serializable
enum class MemoryCategory(val label: String) {
  PREFERENCE("Preference"),
  FACT("User Fact"),
  PROJECT("Project Context"),
  QUICK_NOTE("Quick Note"),
  CONVERSATION_SUMMARY("Conversation Summary"),
}

@Serializable
data class MemoryRecord(
  val id: String,
  val category: MemoryCategory,
  val content: String,
  val keywords: List<String> = emptyList(),
  val importance: Float = 0.6f,
  val isApproved: Boolean = true,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

@Serializable
data class NotificationSummaryRecord(
  val id: String,
  val packageName: String,
  val appLabel: String,
  val title: String,
  val summary: String,
  val importanceLevel: String,
  val suggestedDraftReply: String? = null,
  val receivedAtMs: Long,
)

@Serializable
enum class AutomationWorkflowType(val label: String, val requiresConfirmation: Boolean) {
  OPEN_APP("Open Selected App", true),
  CREATE_REMINDER("Create Reminder / Alarm", true),
  SAVE_QUICK_NOTE("Save Quick Note", false),
  START_ASSISTANT_MODE("Start Assistant Mode", false),
  ORGANIZE_FILES("Organize Selected Files", true),
  RUN_ROUTINE("Run Approved Routine", true),
}

@Serializable
data class AutomationTaskRecord(
  val id: String,
  val workflowType: AutomationWorkflowType,
  val title: String,
  val targetPackageOrArg: String = "",
  val status: String,
  val resultLog: String,
  val triggerSource: String = "Manual",
  val createdAtMs: Long,
)

@Serializable
data class NotificationIntelligenceConfig(
  val enabled: Boolean = false,
  val allowedPackages: Set<String> = emptySet(),
  val generateDraftRepliesForReview: Boolean = true,
  val redactSensitiveCodes: Boolean = true,
)

@Serializable
data class PrivacyAndPerformanceConfig(
  val memoryEnabled: Boolean = true,
  val encryptSensitiveMemory: Boolean = true,
  val continuousVoiceMode: Boolean = false,
  val allowVoiceInterruption: Boolean = true,
  val adaptiveAnimationQuality: Boolean = true,
  val autoCleanupTempFiles: Boolean = true,
  val offlineCacheEnabled: Boolean = true,
)

data class PermissionDashboardItem(
  val id: String,
  val title: String,
  val reason: String,
  val isGranted: Boolean,
  val howToDisable: String,
  val isOptionalZeroPermissionNote: Boolean = false,
)
