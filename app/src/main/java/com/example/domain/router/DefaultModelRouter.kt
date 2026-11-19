package com.example.domain.router

import com.example.domain.model.AiModelSpec
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.ProviderType
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TaskCategory
import com.example.domain.model.VerifiedModelCatalog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultModelRouter @Inject constructor() : ModelRouter {

  override fun classifyTask(prompt: String, hasImages: Boolean): TaskCategory {
    if (hasImages) return TaskCategory.IMAGE_ANALYSIS

    val q = prompt.lowercase()
    return when {
      codingKeywords.any { it in q } || "```" in q -> TaskCategory.CODING
      dataKeywords.any { it in q } -> TaskCategory.DATA_ANALYSIS
      documentKeywords.any { it in q } -> TaskCategory.DOCUMENT_ANALYSIS
      researchKeywords.any { it in q } -> TaskCategory.RESEARCH_REQUESTS
      reasoningKeywords.any { it in q } || q.length > 260 -> TaskCategory.COMPLEX_REASONING
      knowledgeKeywords.any { it in q } -> TaskCategory.GENERAL_KNOWLEDGE
      else -> TaskCategory.CASUAL_CONVERSATION
    }
  }

  override fun route(
    prompt: String,
    hasImages: Boolean,
    imageCount: Int,
    requestedModelId: String?,
    settings: AppSettings,
  ): Result<RoutingDecision> {
    val category = classifyTask(prompt, hasImages)
    val useProxy = settings.connectionMode == ConnectionMode.BACKEND_PROXY

    if (useProxy && settings.proxyBaseUrl.isBlank()) {
      return Result.failure(
        IllegalStateException(
          "Backend Proxy mode is active, but no HTTPS Proxy URL is configured in Settings."
        )
      )
    }

    val hasGemini = useProxy || settings.geminiApiKey.isNotBlank()
    val hasGroq = useProxy || settings.groqApiKey.isNotBlank()

    if (!hasGemini && !hasGroq) {
      return Result.failure(
        IllegalStateException(
          "No API key or HTTPS backend proxy configured. Open Settings to add a Gemini or Groq key."
        )
      )
    }

    val hydrogenSpec = VerifiedModelCatalog.createHydrogenSpec(settings.hydrogenBackingModelId)
    val groqReasoningSpec = VerifiedModelCatalog.createGroqReasoningSpec(settings.groqReasoningModelId)

    val req = requestedModelId?.trim().orEmpty()
    val isHydrogenOrAuto =
      req.isEmpty() ||
        req == AUTO_MODEL_ID ||
        req == VerifiedModelCatalog.HYDROGEN_SLOT_ID ||
        req == "GEMINI_4_HYDROGEN"

    if (!isHydrogenOrAuto) {
      val explicitSpec: AiModelSpec? =
        when (req) {
          VerifiedModelCatalog.geminiPro.id, "GEMINI_PRO" -> VerifiedModelCatalog.geminiPro
          VerifiedModelCatalog.geminiFlash.id, "GEMINI_FLASH" -> VerifiedModelCatalog.geminiFlash
          groqReasoningSpec.id, VerifiedModelCatalog.DEFAULT_GROQ_REASONING_MODEL_ID, "GROQ_REASONING" ->
            groqReasoningSpec
          VerifiedModelCatalog.groqLlama70b.id -> VerifiedModelCatalog.groqLlama70b
          VerifiedModelCatalog.groqLlama8bInstant.id -> VerifiedModelCatalog.groqLlama8bInstant
          else -> VerifiedModelCatalog.allModels.firstOrNull { it.id == req }
        }

      if (explicitSpec != null) {
        if (hasImages && !explicitSpec.capabilities.supportsVision) {
          if (hasGemini) {
            return Result.success(
              RoutingDecision(
                selectedModel = VerifiedModelCatalog.geminiFlash,
                category = category,
                reason =
                  "${explicitSpec.displayName} is text-only; auto-switched to ${VerifiedModelCatalog.geminiFlash.displayName} for verified vision.",
                useProxy = useProxy,
              )
            )
          }
          return Result.failure(
            IllegalStateException(
              "${explicitSpec.displayName} does not support image/video analysis, and no Gemini vision key/proxy is configured."
            )
          )
        }

        if (imageCount > 1 && !explicitSpec.capabilities.supportsMultiImage) {
          return Result.failure(
            IllegalStateException(
              "${explicitSpec.displayName} does not support multiple simultaneous image attachments."
            )
          )
        }

        val providerReady =
          useProxy ||
            (explicitSpec.provider == ProviderType.GEMINI && hasGemini) ||
            (explicitSpec.provider == ProviderType.GROQ && hasGroq)

        if (providerReady) {
          return Result.success(
            RoutingDecision(
              selectedModel = explicitSpec,
              category = category,
              reason = "Selected model: ${explicitSpec.displayName} (${explicitSpec.id})",
              useProxy = useProxy,
            )
          )
        }

        // Graceful provider failover if requested provider lacks credentials but another provider is configured
        if (!hasImages && hasGroq && explicitSpec.provider == ProviderType.GEMINI) {
          return Result.success(
            RoutingDecision(
              selectedModel = groqReasoningSpec,
              category = category,
              reason =
                "Gemini key missing; gracefully routed to ${groqReasoningSpec.displayName} (${groqReasoningSpec.id}).",
              useProxy = useProxy,
            )
          )
        }
        if (hasGemini && explicitSpec.provider == ProviderType.GROQ) {
          return Result.success(
            RoutingDecision(
              selectedModel = VerifiedModelCatalog.geminiFlash,
              category = category,
              reason =
                "Groq key missing; gracefully routed to ${VerifiedModelCatalog.geminiFlash.displayName}.",
              useProxy = useProxy,
            )
          )
        }

        val missingProvider =
          if (explicitSpec.provider == ProviderType.GEMINI) "Google Gemini" else "Groq"
        return Result.failure(
          IllegalStateException(
            "$missingProvider API key is missing in Settings for ${explicitSpec.displayName}."
          )
        )
      }
    }

    // Default: Gemini 4 Hydrogen (or auto-routed Phase 2 compatibility)
    val preferredOrder: List<AiModelSpec> =
      when (category) {
        TaskCategory.IMAGE_ANALYSIS ->
          listOf(hydrogenSpec, VerifiedModelCatalog.geminiFlash, VerifiedModelCatalog.geminiPro)
        TaskCategory.COMPLEX_REASONING,
        TaskCategory.CODING,
        TaskCategory.DOCUMENT_ANALYSIS,
        TaskCategory.DATA_ANALYSIS ->
          listOf(
            hydrogenSpec,
            VerifiedModelCatalog.geminiPro,
            groqReasoningSpec,
            VerifiedModelCatalog.geminiFlash,
          )
        TaskCategory.RESEARCH_REQUESTS,
        TaskCategory.GENERAL_KNOWLEDGE ->
          listOf(
            hydrogenSpec,
            VerifiedModelCatalog.geminiFlash,
            groqReasoningSpec,
            VerifiedModelCatalog.groqLlama70b,
          )
        TaskCategory.CASUAL_CONVERSATION ->
          if (req == AUTO_MODEL_ID && hasGroq && !hasGemini) {
            listOf(VerifiedModelCatalog.groqLlama8bInstant, groqReasoningSpec, hydrogenSpec)
          } else {
            listOf(
              hydrogenSpec,
              VerifiedModelCatalog.geminiFlash,
              groqReasoningSpec,
              VerifiedModelCatalog.groqLlama8bInstant,
            )
          }
      }

    val chosen =
      preferredOrder.firstOrNull { spec ->
        val hasCreds =
          useProxy ||
            (spec.provider == ProviderType.GEMINI && hasGemini) ||
            (spec.provider == ProviderType.GROQ && hasGroq)
        val supportsVisionOk = !hasImages || spec.capabilities.supportsVision
        val supportsMultiOk = imageCount <= 1 || spec.capabilities.supportsMultiImage
        hasCreds && supportsVisionOk && supportsMultiOk
      }
        ?: return Result.failure(
          IllegalStateException(
            if (hasImages) {
              "Image/video understanding requires a Gemini API key or HTTPS proxy configured in Settings."
            } else {
              "No compatible model available for current credentials."
            }
          )
        )

    return Result.success(
      RoutingDecision(
        selectedModel = chosen,
        category = category,
        reason = "Routed ${category.label} -> ${chosen.displayName} (${chosen.id})",
        useProxy = useProxy,
      )
    )
  }

  companion object {
    const val AUTO_MODEL_ID = "auto"

    private val codingKeywords =
      listOf(
        "kotlin", "java", "python", "javascript", "typescript", "compose", "coroutine",
        "sql", "bug", "compile", "stacktrace", "refactor", "algorithm", "function", "class",
        "code", "কোড", "প্রোগ্রাম", "कोड",
      )

    private val dataKeywords =
      listOf(
        "csv", "json", "dataset", "metrics", "chart", "statistics", "table", "sql query",
        "data analysis", "correlation", "excel", "xlsx", "ডেটা", "ডাটা", "डेटा",
      )

    private val documentKeywords =
      listOf(
        "summarize", "summary", "document", "pdf", "docx", "report", "contract", "essay", "notes",
        "সারাংশ", "নথি", "सारांश", "दस्तावेज़",
      )

    private val researchKeywords =
      listOf(
        "research", "compare", "pros and cons", "deep dive", "literature", "citations",
        "গবেষণা", "तुलना", "शोध",
      )

    private val reasoningKeywords =
      listOf(
        "why", "prove", "architect", "strategy", "design system", "tradeoff", "step by step",
        "solve", "equation", "যুক্তি", "ব্যাখ্যা", "तर्क", "विश्लेषण",
      )

    private val knowledgeKeywords =
      listOf(
        "what is", "who is", "when did", "capital of", "explain", "history", "define",
        "কি", "কী", "কাকে বলে", "क्या है", "कौन",
      )
  }
}
