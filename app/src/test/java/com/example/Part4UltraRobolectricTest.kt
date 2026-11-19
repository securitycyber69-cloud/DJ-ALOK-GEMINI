package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.core.automation.ControlledAutomationEngine
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.performance.MousomiMaintenanceWorker
import com.example.core.permissions.PermissionCenterHelper
import com.example.core.screen.RegionCropper
import com.example.core.screen.ScreenFrameDeduplicator
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.security.KeystoreCryptoManager
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.VoiceState
import com.example.data.local.db.AgentTaskEntity
import com.example.data.local.db.MemoryItemEntity
import com.example.data.local.db.MousomiDatabase
import com.example.data.repository.Part4OperationsRepository
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.memory.MemoryWriteOutcome
import com.example.domain.model.AiResponseState
import com.example.domain.model.AutomationWorkflowType
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.MemoryCategory
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.ScreenShareStatus
import com.example.ui.components.DrawingAnnotationOverlay
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.components.InAppFloatingOrbOverlay
import com.example.ui.model.MousomiUiState
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MousomiTheme
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Part4UltraRobolectricTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun `room v3 database migrations and Memory plus Operations DAOs work cleanly`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db =
      Room.inMemoryDatabaseBuilder(context, MousomiDatabase::class.java)
        .addMigrations(MousomiDatabase.MIGRATION_1_2, MousomiDatabase.MIGRATION_2_3)
        .allowMainThreadQueries()
        .build()

    val memDao = db.memoryDao()
    val opsDao = db.part4OperationsDao()

    memDao.upsertMemory(
      MemoryItemEntity(
        id = "m1",
        category = MemoryCategory.PROJECT.name,
        contentPayload = "Building Mousomi AI Part 4",
        keywordsCsv = "mousomi,part4",
        importance = 0.9f,
        isApproved = true,
        isEncrypted = false,
        createdAtMs = 100L,
        updatedAtMs = 100L,
      )
    )
    assertEquals(1, memDao.getApprovedMemoriesSnapshot().size)

    opsDao.upsertAgentTask(
      AgentTaskEntity(
        id = "a1",
        goal = "Test Goal",
        selectedAgentsJson = "[\"Task Planning Agent\"]",
        status = "Completed",
        summary = "Done",
        tokenUsage = 42,
        durationMs = 120L,
        createdAtMs = 200L,
      )
    )
    val repo = Part4OperationsRepository(opsDao)
    assertNotNull(repo)
    db.close()
  }

  @Test
  fun `screen share session deduplicates identical frames and wipes temporary files on stop`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = TempMediaCacheManager(context)
    val session = ScreenShareSessionManager(cache)

    session.markRequestingConsent()
    assertEquals(ScreenShareStatus.REQUESTING_CONSENT, session.status.value)
    session.markSessionActive()

    val bmp1 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    val bmpSame = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
    val bmpDifferent =
      Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until 64) {
          for (y in 0 until 64) {
            setPixel(x, y, if (x < 32) Color.WHITE else Color.BLACK)
          }
        }
      }

    val first = session.ingestCapturedBitmap(bmp1)
    val dup = session.ingestCapturedBitmap(bmpSame)
    val second = session.ingestCapturedBitmap(bmpDifferent)

    assertNotNull(first)
    assertNull(dup)
    assertNotNull(second)
    assertTrue(File(first!!.filePath).exists())
    assertEquals(2, session.frames.value.size)

    session.stopSession()
    assertEquals(ScreenShareStatus.STOPPED, session.status.value)
    assertTrue(session.frames.value.isEmpty())
    assertFalse(File(first.filePath).exists())
    assertFalse(ScreenFrameDeduplicator.isDuplicate(first.perceptualHash, second!!.perceptualHash, 2))
  }

  @Test
  fun `region cropper crops bitmap precisely from normalized bounding box`() {
    val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
    val cropped = RegionCropper.cropBitmapToNormBox(source, NormBoundingBox(0.25f, 0.2f, 0.75f, 0.8f))
    assertNotNull(cropped)
    assertEquals(100, cropped!!.width)
    assertEquals(60, cropped.height)
  }

  @Test
  fun `controlled automation gates sensitive workflows and logs cancellation or completion`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, MousomiDatabase::class.java).allowMainThreadQueries().build()
    val memoryManager = LongTermMemoryManager(db.memoryDao(), KeystoreCryptoManager())
    val opsRepo = Part4OperationsRepository(db.part4OperationsDao())
    val engine = ControlledAutomationEngine(context, memoryManager, opsRepo)

    // Quick note executes immediately
    val noteTask =
      engine.createPendingOrExecute(
        workflowType = AutomationWorkflowType.SAVE_QUICK_NOTE,
        title = "Idea",
        targetArg = "Add Edge Light preset telemetry",
      )
    assertEquals("Completed", noteTask.status)

    // Sensitive task waits for confirmation
    val pendingOpen =
      engine.createPendingOrExecute(
        workflowType = AutomationWorkflowType.CREATE_REMINDER,
        title = "Standup",
        confirmedByUser = false,
      )
    assertEquals("Awaiting Confirmation", pendingOpen.status)

    val cancelled = engine.cancelPendingTask(pendingOpen)
    assertEquals("Cancelled", cancelled.status)
    db.close()
  }

  @Test
  fun `workmanager maintenance worker cleans expired temporary media cache successfully`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    val worker = TestListenableWorkerBuilder<MousomiMaintenanceWorker>(context).build()
    val result = worker.doWork()
    assertEquals(androidx.work.ListenableWorker.Result.success(), result)
  }

  @Test
  fun `permission center lists all privacy disclosures on Android 12+ SDK 36`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val items = PermissionCenterHelper.buildDashboardItems(context, ScreenShareStatus.STOPPED)
    assertEquals(8, items.size)
    assertTrue(items.any { it.id == "media_picker" && it.isOptionalZeroPermissionNote })
    assertTrue(items.any { it.id == "camera_note" && !it.isGranted })
  }

  @Test
  fun `edge light, floating orb quick menu, and drawing studio UI render and respond cleanly`() {
    var tappedAction: OrbQuickAction? = null
    var closedDrawing = false

    val params =
      EdgeLightStateMapper.resolve(
        config = EdgeLightConfig(enabled = true, preset = EdgeLightPreset.GEMINI_FLOW),
        responseState = AiResponseState.Generating("Gemini 4 Hydrogen"),
        voiceState = VoiceState.Idle,
        voiceRmsNormalized = 0f,
        screenShareStatus = ScreenShareStatus.STOPPED,
        notificationAlertActive = false,
        isSystemPowerSave = false,
        currentHour = 12,
      )

    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        EdgeLightPerimeterOverlay(params = params)
        InAppFloatingOrbOverlay(
          config = FloatingOrbConfig(enabled = true, inAppPreviewVisible = true),
          visualState = EdgeLightVisualState.IDLE,
          onPositionCommitted = { _, _ -> },
          onQuickAction = { tappedAction = it },
        )
      }
    }

    composeRule.onNodeWithTag("edge_light_perimeter_canvas").assertIsDisplayed()
    composeRule.onNodeWithTag("floating_ai_orb").assertIsDisplayed().performClick()
    composeRule.mainClock.advanceTimeBy(500L)
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("floating_orb_quick_panel").assertIsDisplayed()
    composeRule.onNodeWithTag("orb_action_DRAW_ON_SCREEN").performClick()
    assertEquals(OrbQuickAction.DRAW_ON_SCREEN, tappedAction)
  }

  @Test
  fun `control center switches cleanly across all four management tabs`() {
    composeRule.setContent {
      MousomiTheme(themeMode = AppThemeMode.DARK) {
        ControlCenterScreen(
          uiState = MousomiUiState(),
          onUpdateEdgeLight = {},
          onUpdateFloatingOrb = {},
          onUpdateScreenConfig = {},
          onRequestStartScreenShare = {},
          onStopScreenShare = {},
          onAnalyzeCurrentScreen = {},
          onOpenDrawingStudio = {},
          onUpdateAgentBudget = {},
          onSaveMemory = { _, _ -> },
          onDeleteMemory = {},
          onClearMemories = {},
          onUpdateNotificationConfig = {},
          onDismissNotificationSummary = {},
          onRunAutomation = { _, _, _ -> },
          onConfirmAutomation = {},
          onCancelAutomation = {},
          onUpdatePrivacyPerformance = {},
          onClearTempMediaCache = {},
          onToggleSystemOverlay = {},
          onOpenOverlaySettings = {},
          onOpenNotificationAccessSettings = {},
          onRequestAssistantRole = {},
          onRequestMicPermission = {},
          onOpenApiAndModelSettings = {},
          onBack = {},
        )
      }
    }

    composeRule.onNodeWithText("1. Edge Light Studio").assertIsDisplayed()
    composeRule.onNodeWithTag("control_tab_1").performClick()
    composeRule.onNodeWithText("5. Autonomous Multi-Agent Orchestrator").assertIsDisplayed()
    composeRule.onNodeWithTag("control_tab_2").performClick()
    composeRule.onNodeWithText("9. Smart Notification Intelligence").assertIsDisplayed()
    composeRule.onNodeWithTag("control_tab_3").performClick()
    composeRule.onNodeWithText("11. Privacy & Permission Center").assertIsDisplayed()
  }
}
