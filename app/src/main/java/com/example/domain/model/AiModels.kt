package com.example.domain.model

import kotlinx.serialization.Serializable

enum class ProviderType {
  GEMINI,
  GROQ,
  PROXY,
}

enum class TaskCategory(val label: String) {
  CASUAL_CONVERSATION("Casual Conversation"),
  GENERAL_KNOWLEDGE("General Knowledge"),
  COMPLEX_REASONING("Complex Reasoning"),
  CODING("Coding"),
  IMAGE_ANALYSIS("Image Analysis"),
  DOCUMENT_ANALYSIS("Document Analysis"),
  RESEARCH_REQUESTS("Research"),
  DATA_ANALYSIS("Data Analysis"),
}

enum class LatencyTier {
  ULTRA_FAST,
  BALANCED,
  DEEP_REASONING,
}

enum class ResponseLanguage(val label: String, val localeTag: String) {
  AUTO("Auto (Match Prompt)", "en-US"),
  ENGLISH("English", "en-US"),
  BENGALI("বাংলা (Bengali)", "bn-BD"),
  HINDI("हिन्दी (Hindi)", "hi-IN"),
}

data class AiCapabilities(
  val supportsStreaming: Boolean,
  val supportsVision: Boolean,
  val supportsMultiImage: Boolean,
  val supportsSystemInstruction: Boolean,
  val maxContextTokens: Int,
  val supportsGoogleSearchGrounding: Boolean = false,
  val supportsAudioInput: Boolean = false,
  val supportsVideoFrameAnalysis: Boolean = supportsVision,
)

data class AiModelSpec(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val provider: ProviderType,
  val latencyTier: LatencyTier,
  val capabilities: AiCapabilities,
  val description: String,
  val selectorSlotId: String = id,
)

object VerifiedModelCatalog {
  const val HYDROGEN_SLOT_ID = "gemini_4_hydrogen"
  const val DEFAULT_HYDROGEN_BACKING_MODEL_ID = "gemini-2.5-flash"
  const val DEFAULT_GROQ_REASONING_MODEL_ID = "qwen/qwen3.8-27b"
  const val FALLBACK_GROQ_MODEL_ID = "llama-3.3-70b-versatile"

  fun createHydrogenSpec(backingModelId: String = DEFAULT_HYDROGEN_BACKING_MODEL_ID): AiModelSpec {
    val cleanId = backingModelId.trim().ifBlank { DEFAULT_HYDROGEN_BACKING_MODEL_ID }
    return AiModelSpec(
      id = cleanId,
      selectorSlotId = HYDROGEN_SLOT_ID,
      displayName = "Gemini 4 Hydrogen",
      shortLabel = "4 Hydrogen",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.BALANCED,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 1_048_576,
          supportsGoogleSearchGrounding = true,
          supportsAudioInput = true,
          supportsVideoFrameAnalysis = true,
        ),
      description = "New Base Intelligence · Advanced AI Engine",
    )
  }

  val geminiPro =
    AiModelSpec(
      id = "gemini-2.5-pro",
      selectorSlotId = "gemini_pro",
      displayName = "Gemini Pro",
      shortLabel = "Pro",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.DEEP_REASONING,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 1_048_576,
          supportsGoogleSearchGrounding = true,
          supportsAudioInput = true,
          supportsVideoFrameAnalysis = true,
        ),
      description = "Advanced Reasoning · Coding · Complex Analysis",
    )

  val geminiFlash =
    AiModelSpec(
      id = "gemini-2.5-flash",
      selectorSlotId = "gemini_flash",
      displayName = "Gemini Flash",
      shortLabel = "Flash",
      provider = ProviderType.GEMINI,
      latencyTier = LatencyTier.ULTRA_FAST,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = true,
          supportsMultiImage = true,
          supportsSystemInstruction = true,
          maxContextTokens = 1_048_576,
          supportsGoogleSearchGrounding = true,
          supportsAudioInput = true,
          supportsVideoFrameAnalysis = true,
        ),
      description = "Fast Responses · Everyday AI · Multimodal Tasks",
    )

  fun createGroqReasoningSpec(modelId: String = DEFAULT_GROQ_REASONING_MODEL_ID): AiModelSpec {
    val cleanId = modelId.trim().ifBlank { DEFAULT_GROQ_REASONING_MODEL_ID }
    return AiModelSpec(
      id = cleanId,
      selectorSlotId = "groq_reasoning",
      displayName = "Groq Reasoning",
      shortLabel = "Groq",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.DEEP_REASONING,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 131_072,
          supportsGoogleSearchGrounding = false,
          supportsAudioInput = false,
          supportsVideoFrameAnalysis = false,
        ),
      description = "Fast Inference · Logical Reasoning · Technical Assistance",
    )
  }

  // Preserved aliases for Phase 2 routing and tests
  val groqReasoning: AiModelSpec = createGroqReasoningSpec()

  val groqLlama70b =
    AiModelSpec(
      id = "llama-3.3-70b-versatile",
      displayName = "Groq Llama 3.3 70B",
      shortLabel = "Groq 70B",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.BALANCED,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
        ),
      description = "High-speed versatile text reasoning via Groq",
    )

  val groqLlama8bInstant =
    AiModelSpec(
      id = "llama-3.1-8b-instant",
      displayName = "Groq Llama 3.1 8B",
      shortLabel = "Groq 8B",
      provider = ProviderType.GROQ,
      latencyTier = LatencyTier.ULTRA_FAST,
      capabilities =
        AiCapabilities(
          supportsStreaming = true,
          supportsVision = false,
          supportsMultiImage = false,
          supportsSystemInstruction = true,
          maxContextTokens = 128_000,
        ),
      description = "Ultra-low-latency text chat via Groq",
    )

  val fourSelectableModels: List<AiModelSpec> =
    listOf(createHydrogenSpec(), geminiPro, geminiFlash, groqReasoning)

  val allModels: List<AiModelSpec> =
    listOf(createHydrogenSpec(), geminiPro, geminiFlash, groqReasoning, groqLlama70b, groqLlama8bInstant)
}

data class RoutingDecision(
  val selectedModel: AiModelSpec,
  val category: TaskCategory,
  val reason: String,
  val useProxy: Boolean,
  val intelligenceIntent: IntelligenceIntent = IntelligenceIntent.NORMAL_AI,
)

@Serializable
data class TokenUsage(
  val promptTokens: Int = 0,
  val completionTokens: Int = 0,
  val totalTokens: Int = 0,
)

@Serializable
data class ProcessedImagePayload(
  val uriString: String,
  val mimeType: String,
  val base64Data: String,
  val width: Int,
  val height: Int,
  val byteSize: Int,
  val exifSummary: String? = null,
)

sealed interface AiResponseState {
  data object Idle : AiResponseState

  data object Loading : AiResponseState

  data class Thinking(val detail: String = "Classifying intent & routing model…") : AiResponseState

  data class Searching(val detail: String = "Gathering context & running tools…") : AiResponseState

  data class Generating(val modelName: String) : AiResponseState

  data class Streaming(
    val partialText: String,
    val modelName: String,
    val tokenUsage: TokenUsage? = null,
  ) : AiResponseState

  data class Completed(
    val fullText: String,
    val modelName: String,
    val tokenUsage: TokenUsage? = null,
  ) : AiResponseState

  data class Error(
    val message: String,
    val recoverable: Boolean = true,
  ) : AiResponseState

  data object Cancelled : AiResponseState
}

data class DomainConversation(
  val id: String,
  val title: String,
  val createdAtMs: Long,
  val updatedAtMs: Long,
)

data class DomainMessage(
  val id: String,
  val conversationId: String,
  val isUser: Boolean,
  val text: String,
  val timestampMs: Long,
  val imageUris: List<String> = emptyList(),
  val isSampleArtwork: Boolean = false,
  val modelUsed: String? = null,
  val categoryLabel: String? = null,
  val tokenUsage: TokenUsage? = null,
  val isError: Boolean = false,
  val blocks: List<ResponseBlock> = emptyList(),
  val attachedMediaSummaries: List<PendingMediaItem> = emptyList(),
)

enum class ConnectionMode(val label: String) {
  DIRECT_DEV_KEYS("Direct Dev Keys"),
  BACKEND_PROXY("HTTPS Backend Proxy"),
}

data class VoicePreferences(
  val preferredLanguage: ResponseLanguage = ResponseLanguage.AUTO,
  val autoSpeakResponses: Boolean = false,
  val speechRate: Float = 1.0f,
  val pitch: Float = 1.0f,
)

data class AppSettings(
  val connectionMode: ConnectionMode = ConnectionMode.DIRECT_DEV_KEYS,
  val proxyBaseUrl: String = "",
  val geminiApiKey: String = "",
  val groqApiKey: String = "",
  val themeModeName: String = "DARK",
  val voicePreferences: VoicePreferences = VoicePreferences(),
  val selectedModelOptionName: String = "GEMINI_4_HYDROGEN",
  val hydrogenBackingModelId: String = VerifiedModelCatalog.DEFAULT_HYDROGEN_BACKING_MODEL_ID,
  val groqReasoningModelId: String = VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID,
  val externalSearchUrl: String = "",
  val externalSearchApiKey: String = "",
  val analyticsSandboxEnabled: Boolean = false,
  val edgeLightConfig: EdgeLightConfig = EdgeLightConfig(),
  val floatingOrbConfig: FloatingOrbConfig = FloatingOrbConfig(),
  val screenIntelligenceConfig: ScreenIntelligenceConfig = ScreenIntelligenceConfig(),
  val agentBudgetConfig: AgentBudgetConfig = AgentBudgetConfig(),
  val notificationConfig: NotificationIntelligenceConfig = NotificationIntelligenceConfig(),
  val privacyPerformanceConfig: PrivacyAndPerformanceConfig = PrivacyAndPerformanceConfig(),
)
