package com.example

import com.example.core.edge.EdgeLightStateMapper
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.overlay.OrbPositionHelper
import com.example.core.screen.RegionCropper
import com.example.core.security.KeystoreCryptoManager
import com.example.core.voice.SpeechToTextGateway
import com.example.core.voice.TextToSpeechGateway
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.domain.agent.AgentExecutionResult
import com.example.domain.agent.AutonomousAgentOrchestrator
import com.example.domain.context.UnifiedContextEngine
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStepStatus
import com.example.domain.model.AiResponseState
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.DomainMessage
import com.example.domain.model.DrawingTool
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import com.example.domain.model.NormPoint
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.ScreenShareStatus
import com.example.domain.model.VerifiedModelCatalog
import com.example.domain.model.VoicePreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Part4UltraUnitTest {

  @Test
  fun `edge light maps all AI states, battery saver, reduced motion, and overnight schedule accurately`() {
    val baseConfig =
      EdgeLightConfig(
        enabled = true,
        preset = EdgeLightPreset.GEMINI_FLOW,
        brightness = 0.9f,
        animationSpeed = 1.5f,
        scheduleEnabled = true,
        scheduleStartHour = 22,
        scheduleEndHour = 6,
      )

    assertTrue(EdgeLightStateMapper.isWithinSchedule(baseConfig, 23))
    assertTrue(EdgeLightStateMapper.isWithinSchedule(baseConfig, 4))
    assertFalse(EdgeLightStateMapper.isWithinSchedule(baseConfig, 14))

    val listening =
      EdgeLightStateMapper.resolve(
        config = baseConfig.copy(scheduleEnabled = false),
        responseState = AiResponseState.Idle,
        voiceState = VoiceState.Listening("hello"),
        voiceRmsNormalized = 0.8f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
      )
    assertEquals(EdgeLightVisualState.LISTENING, listening.visualState)
    assertEquals(EdgeLightPreset.VOICE_LISTENING, listening.effectivePreset)
    assertTrue(listening.thicknessDp > baseConfig.borderThicknessDp)

    val batterySaver =
      EdgeLightStateMapper.resolve(
        config = baseConfig.copy(scheduleEnabled = false, batterySaverMode = true),
        responseState = AiResponseState.Generating("Gemini 4 Hydrogen"),
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
      )
    assertTrue(batterySaver.brightness <= 0.45f)
    assertTrue(batterySaver.speedMultiplier <= 0.5f)
    assertEquals(1, batterySaver.glowLayerCount)

    val reduced =
      EdgeLightStateMapper.resolve(
        config = baseConfig.copy(scheduleEnabled = false, reducedMotion = true),
        responseState = AiResponseState.Searching("Deep Research phase 2"),
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0.5f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
      )
    assertEquals(EdgeLightVisualState.RESEARCHING, reduced.visualState)
    assertEquals(0f, reduced.speedMultiplier, 0.001f)
    assertTrue(reduced.breathingOnly)
  }

  @Test
  fun `floating orb clamps bounds and snaps to nearest left or right screen edge`() {
    assertEquals(0.06f, OrbPositionHelper.computeSnappedXFraction(0.32f, snapToEdge = true), 0.001f)
    assertEquals(0.94f, OrbPositionHelper.computeSnappedXFraction(0.75f, snapToEdge = true), 0.001f)
    assertEquals(0.04f, OrbPositionHelper.clampFraction(-0.5f), 0.001f)
    assertEquals(0.96f, OrbPositionHelper.clampFraction(1.4f), 0.001f)
  }

  @Test
  fun `region cropper computes valid normalized box ignoring eraser strokes`() {
    val strokes =
      listOf(
        AnnotationStroke(
          id = "1",
          tool = DrawingTool.RECTANGLE,
          colorArgb = 0xFF38BDF8L,
          strokeWidthPx = 8f,
          alpha = 1f,
          points = listOf(NormPoint(0.2f, 0.3f), NormPoint(0.6f, 0.7f)),
        ),
        AnnotationStroke(
          id = "2",
          tool = DrawingTool.ERASER,
          colorArgb = 0xFF000000L,
          strokeWidthPx = 8f,
          alpha = 1f,
          points = listOf(NormPoint(0.0f, 0.0f), NormPoint(1.0f, 1.0f)),
        ),
      )

    val box = RegionCropper.computeBoundingBox(strokes, paddingFraction = 0.05f)
    assertNotNull(box)
    assertEquals(0.15f, box!!.left, 0.01f)
    assertEquals(0.25f, box.top, 0.01f)
    assertEquals(0.65f, box.right, 0.01f)
    assertEquals(0.75f, box.bottom, 0.01f)
  }

  @Test
  fun `multi-agent orchestrator plans roles, enforces approval gate, retries boundedly, and cancels`() = runTest {
    val orchestrator = AutonomousAgentOrchestrator()

    // Sensitive Android assistant action requires explicit user approval first
    val plan =
      orchestrator.planWorkflow(
        goal = "Open Spotify and analyze coding architecture with Python",
        budget = AgentBudgetConfig(maxSteps = 8, maxRetriesPerStep = 2),
      )
    assertTrue(plan.any { it.agent == AgentRole.ANDROID_ASSISTANT && it.requiresUserApproval })
    assertTrue(plan.any { it.agent == AgentRole.CODING })
    assertEquals(AgentRole.VERIFICATION, plan.last().agent)

    val awaiting =
      orchestrator.executePlan(
        goal = "Open Spotify",
        initialSteps = plan,
        budget = AgentBudgetConfig(maxSteps = 8, maxRetriesPerStep = 2),
        approvedSensitiveActions = false,
      ) { Result.success("done") }
    assertTrue(awaiting is AgentExecutionResult.AwaitingApproval)

    // Bounded retries succeed on 2nd attempt
    var attempts = 0
    val simplePlan =
      orchestrator.planWorkflow(goal = "Analyze sales CSV statistics", budget = AgentBudgetConfig(maxSteps = 4))
    val retryResult =
      orchestrator.executePlan(
        goal = "Analyze sales CSV statistics",
        initialSteps = simplePlan,
        budget = AgentBudgetConfig(maxSteps = 4, maxRetriesPerStep = 2),
        approvedSensitiveActions = true,
      ) { step ->
        if (step.agent == AgentRole.DATA_ANALYST && attempts == 0) {
          attempts++
          Result.failure(IllegalStateException("Transient timeout"))
        } else {
          Result.success("ok")
        }
      }
    assertTrue(retryResult is AgentExecutionResult.Completed)
    assertEquals(1, attempts)

    // Cancellation stops cleanly
    val gate = CompletableDeferred<Unit>()
    lateinit var cancelResult: AgentExecutionResult
    val job =
      launch {
        cancelResult =
          orchestrator.executePlan(
            goal = "Long running plan",
            initialSteps = simplePlan,
            budget = AgentBudgetConfig(),
            approvedSensitiveActions = true,
          ) {
            gate.await()
            Result.success("done")
          }
      }
    kotlinx.coroutines.yield()
    job.cancel()
    job.join()
    assertTrue(orchestrator.activeSteps.value.any { it.status == AgentStepStatus.CANCELLED } || job.isCancelled)
  }

  @Test
  fun `memory manager ranks relevant items, detects duplicates, and blocks secrets`() {
    val crypto = KeystoreCryptoManager()
    assertTrue(crypto.containsSensitiveCredentials("Your OTP verification code is 492011"))
    assertTrue(crypto.containsSensitiveCredentials("Here is my key gsk_1234567890abcdefghijklmno"))
    assertFalse(crypto.containsSensitiveCredentials("I prefer dark mode and concise Kotlin answers"))

    val now = System.currentTimeMillis()
    val memories =
      listOf(
        MemoryRecord("m1", MemoryCategory.PREFERENCE, "User prefers Kotlin and Jetpack Compose examples", listOf("kotlin", "compose"), 0.8f, true, now, now),
        MemoryRecord("m2", MemoryCategory.FACT, "Likes Italian espresso in the morning", listOf("coffee", "espresso"), 0.5f, true, now - 10_000_000L, now - 10_000_000L),
      )

    val ranked = LongTermMemoryManager.rankMemoriesForQuery("Help me write Jetpack Compose Kotlin code", memories, limit = 1)
    assertEquals("m1", ranked.single().id)

    val dup = LongTermMemoryManager.findDuplicate("User prefers Kotlin and Jetpack Compose examples!", memories)
    assertNotNull(dup)
    assertEquals("m1", dup!!.id)
  }

  @Test
  fun `notification intelligence blocks OTP 2FA alerts and enforces package allowlist`() {
    val engine = NotificationIntelligenceEngine(KeystoreCryptoManager())
    engine.updateConfig(
      NotificationIntelligenceConfig(
        enabled = true,
        allowedPackages = setOf("com.whatsapp"),
        generateDraftRepliesForReview = true,
        redactSensitiveCodes = true,
      )
    )

    // Disallowed package filtered
    assertNull(engine.onNotificationPosted("com.other.app", "Other", "Hi", "Hey there?"))

    // OTP blocked
    assertNull(
      engine.onNotificationPosted(
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        title = "Bank Alert",
        bodyText = "Your verification code is 842910. Do not share OTP.",
      )
    )

    // Normal message summarized with manual draft suggestion
    val valid =
      engine.onNotificationPosted(
        packageName = "com.whatsapp",
        appLabel = "WhatsApp",
        title = "Alex",
        bodyText = "Can you review the architecture doc today?",
      )
    assertNotNull(valid)
    assertEquals("Normal", valid!!.importanceLevel)
    assertNotNull(valid.suggestedDraftReply)
  }

  @Test
  fun `unified context engine deduplicates, enforces token cap, and adds non-vision notice`() {
    val now = System.currentTimeMillis()
    val snapshot =
      UnifiedContextEngine.assembleContext(
        query = "Explain chart",
        memories =
          listOf(
            MemoryRecord("1", MemoryCategory.FACT, "Prefers metric units", createdAtMs = now, updatedAtMs = now)
          ),
        historyMessages =
          listOf(
            DomainMessage("u1", "c1", true, "Explain my sales chart", now),
            DomainMessage("a1", "c1", false, "Sure, share the table.", now + 1),
          ),
        hasActiveScreenFrame = true,
        capabilityNotice = "Model (${VerifiedModelCatalog.groqReasoning.displayName}) lacks raw vision tokens.",
        tokenBudget = 1200,
      )

    assertTrue(snapshot.contextBlock.contains("Prefers metric units"))
    assertTrue(snapshot.contextBlock.contains("Live Screen Context"))
    assertTrue(snapshot.contextBlock.contains("Model Capability Check"))
    assertTrue(snapshot.estimatedTokens > 0)
  }

  @Test
  fun `voice controller interrupts active TTS playback and emits normalized audio RMS`() {
    var ttsStopped = false
    val fakeStt =
      object : SpeechToTextGateway {
        override fun startListening(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
        ) {
          onPartial("Hey Mousomi")
        }

        override fun startListeningWithRms(
          languageTag: String,
          onPartial: (String) -> Unit,
          onFinal: (String) -> Unit,
          onError: (String) -> Unit,
          onRmsNormalized: (Float) -> Unit,
        ) {
          onRmsNormalized(0.74f)
          onPartial("Hey Mousomi")
        }

        override fun stopListening() = Unit
        override fun release() = Unit
      }
    val fakeTts =
      object : TextToSpeechGateway {
        override fun speak(
          messageId: String,
          text: String,
          preferences: VoicePreferences,
          onStart: (String) -> Unit,
          onDone: () -> Unit,
          onError: (String) -> Unit,
        ) {
          onStart(messageId)
        }

        override fun stop() {
          ttsStopped = true
        }

        override fun release() = Unit
      }

    val controller = VoiceController(fakeStt, fakeTts)
    controller.speakResponse("m1", "Reading response aloud", VoicePreferences())
    assertTrue(controller.voiceState.value is VoiceState.Speaking)

    controller.interruptSpeakingAndListen(ResponseLanguage.ENGLISH)
    assertTrue(ttsStopped)
    assertTrue(controller.voiceState.value is VoiceState.Listening)
    assertEquals(0.74f, controller.audioRmsNormalized.value, 0.001f)
  }
}
