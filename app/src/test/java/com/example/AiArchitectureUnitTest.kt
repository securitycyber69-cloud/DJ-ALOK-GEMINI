package com.example

import com.example.ai.provider.AiChatRequest
import com.example.ai.provider.AiProvider
import com.example.ai.provider.AiStreamEvent
import com.example.core.network.AiProviderException
import com.example.core.security.ClientRateLimiter
import com.example.core.security.SecurityValidator
import com.example.domain.model.AiResponseState
import com.example.domain.model.AppSettings
import com.example.domain.model.ConnectionMode
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ProviderType
import com.example.domain.model.TaskCategory
import com.example.domain.model.TokenUsage
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.orchestrator.DateTimeContextTool
import com.example.domain.orchestrator.DefaultAiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.SettingsRepository
import com.example.domain.router.DefaultModelRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiArchitectureUnitTest {

  private val router = DefaultModelRouter()
  private val validator = SecurityValidator()

  @Test
  fun `router classifies multilingual and domain task categories accurately`() {
    assertEquals(TaskCategory.IMAGE_ANALYSIS, router.classifyTask("Describe this", hasImages = true))
    assertEquals(TaskCategory.CODING, router.classifyTask("Write a Kotlin coroutine retry function", hasImages = false))
    assertEquals(TaskCategory.DATA_ANALYSIS, router.classifyTask("Analyze this CSV dataset metrics", hasImages = false))
    assertEquals(TaskCategory.DOCUMENT_ANALYSIS, router.classifyTask("Summarize this PDF contract", hasImages = false))
    assertEquals(TaskCategory.RESEARCH_REQUESTS, router.classifyTask("Compare microservices vs monolith pros and cons", hasImages = false))
    assertEquals(TaskCategory.COMPLEX_REASONING, router.classifyTask("Explain step by step why CAP theorem holds", hasImages = false))
    assertEquals(TaskCategory.GENERAL_KNOWLEDGE, router.classifyTask("বাংলাদেশ কি এবং এর রাজধানী কোথায়?", hasImages = false))
    assertEquals(TaskCategory.CASUAL_CONVERSATION, router.classifyTask("Hey there, how are you?", hasImages = false))
  }

  @Test
  fun `router prevents text-only Groq model from handling image analysis`() {
    val settings =
      AppSettings(
        connectionMode = ConnectionMode.DIRECT_DEV_KEYS,
        geminiApiKey = "AIzaTestKey1234567890",
        groqApiKey = "gsk_TestKey1234567890",
      )

    val result =
      router.route(
        prompt = "What is in this screenshot?",
        hasImages = true,
        imageCount = 1,
        requestedModelId = VerifiedModelCatalog.groqLlama70b.id,
        settings = settings,
      )

    assertTrue(result.isSuccess)
    val decision = result.getOrThrow()
    assertEquals(ProviderType.GEMINI, decision.selectedModel.provider)
    assertTrue(decision.selectedModel.capabilities.supportsVision)
  }

  @Test
  fun `router rejects proxy mode when HTTPS URL is empty`() {
    val settings = AppSettings(connectionMode = ConnectionMode.BACKEND_PROXY, proxyBaseUrl = "")
    val result = router.route("Hello", hasImages = false, imageCount = 0, requestedModelId = "auto", settings = settings)
    assertTrue(result.isFailure)
  }

  @Test
  fun `security validator enforces HTTPS and redacts keys`() {
    assertTrue(validator.validateHttpsUrl("http://insecure.example.com").isFailure)
    assertTrue(validator.validateHttpsUrl("https://proxy.mousomi.ai").isSuccess)
    assertEquals("AIza****90", validator.redactForDiagnostic("AIzaTestKey1234567890"))
  }

  @Test
  fun `orchestrator streams deltas tracks usage and generates title`() = runTest {
    val fakeSettingsRepo =
      object : SettingsRepository {
        private val flow =
          MutableStateFlow(
            AppSettings(
              connectionMode = ConnectionMode.DIRECT_DEV_KEYS,
              geminiApiKey = "AIzaTestKey1234567890",
            )
          )

        override fun observeSettings(): Flow<AppSettings> = flow

        override suspend fun getSettingsSnapshot(): AppSettings = flow.value

        override suspend fun saveSettings(settings: AppSettings) {
          flow.value = settings
        }
      }

    val fakeGeminiProvider =
      object : AiProvider {
        override val providerType: ProviderType = ProviderType.GEMINI

        override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
          emit(AiStreamEvent.Delta("Hello "))
          emit(AiStreamEvent.Delta("from Gemini!"))
          emit(AiStreamEvent.Usage(TokenUsage(promptTokens = 10, completionTokens = 5, totalTokens = 15)))
          emit(AiStreamEvent.Completed)
        }
      }

    val orchestrator =
      DefaultAiOrchestrator(
        modelRouter = router,
        settingsRepository = fakeSettingsRepo,
        securityValidator = validator,
        rateLimiter = ClientRateLimiter(),
        providers = mapOf(ProviderType.GEMINI to fakeGeminiProvider),
        tools = setOf(DateTimeContextTool()),
      )

    val events =
      orchestrator
        .processMessage(
          OrchestrationRequest(
            conversationId = "c1",
            userPrompt = "What is the current date today?",
            historyMessages = emptyList(),
            currentTurnImages =
              listOf(
                ProcessedImagePayload(
                  uriString = "content://img",
                  mimeType = "image/jpeg",
                  base64Data = "AQID",
                  width = 100,
                  height = 100,
                  byteSize = 3,
                )
              ),
          )
        )
        .toList()

    assertTrue(events.any { it is OrchestrationEvent.TitleSuggested })
    val completed =
      events
        .filterIsInstance<OrchestrationEvent.StateChanged>()
        .map { it.state }
        .filterIsInstance<AiResponseState.Completed>()
        .single()
    assertEquals("Hello from Gemini!", completed.fullText)
    assertEquals(15, completed.tokenUsage?.totalTokens)
  }

  @Test
  fun `orchestrator surfaces provider error cleanly`() = runTest {
    val fakeSettingsRepo =
      object : SettingsRepository {
        override fun observeSettings(): Flow<AppSettings> =
          MutableStateFlow(AppSettings(groqApiKey = "gsk_TestKey1234567890"))

        override suspend fun getSettingsSnapshot(): AppSettings =
          AppSettings(groqApiKey = "gsk_TestKey1234567890")

        override suspend fun saveSettings(settings: AppSettings) = Unit
      }

    val failingGroq =
      object : AiProvider {
        override val providerType: ProviderType = ProviderType.GROQ

        override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
          throw AiProviderException("Authentication failed (HTTP 401).", httpStatusCode = 401)
        }
      }

    val orchestrator =
      DefaultAiOrchestrator(
        modelRouter = router,
        settingsRepository = fakeSettingsRepo,
        securityValidator = validator,
        rateLimiter = ClientRateLimiter(),
        providers = mapOf(ProviderType.GROQ to failingGroq),
        tools = emptySet(),
      )

    val events =
      orchestrator
        .processMessage(
          OrchestrationRequest(
            conversationId = "c2",
            userPrompt = "Hi",
            historyMessages = emptyList(),
          )
        )
        .toList()

    val errorState =
      events
        .filterIsInstance<OrchestrationEvent.StateChanged>()
        .map { it.state }
        .filterIsInstance<AiResponseState.Error>()
        .single()
    assertFalse(errorState.message.contains("gsk_"))
    assertTrue(errorState.message.contains("401"))
  }
}
