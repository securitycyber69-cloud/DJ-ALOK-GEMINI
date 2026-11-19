package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.assistant.AssistantRoleHelper
import com.example.core.permissions.PermissionCenterHelper
import com.example.domain.model.OrbQuickAction
import com.example.service.MediaProjectionCaptureService
import com.example.service.MousomiOverlayService
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.MainChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MousomiTheme
import com.example.ui.viewmodel.MousomiViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

object MousomiRoutes {
  const val CHAT = "chat"
  const val SETTINGS = "settings"
  const val CONTROL_CENTER = "control_center"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    enableEdgeToEdge()

    val initialQuickAction = extractQuickAction(intent)

    setContent {
      val viewModel: MousomiViewModel = hiltViewModel()
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      val navController = rememberNavController()

      val screenCaptureLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
          val data = result.data
          if (result.resultCode == Activity.RESULT_OK && data != null) {
            val svcIntent =
              MediaProjectionCaptureService.buildStartIntent(
                context = this@MainActivity,
                resultCode = result.resultCode,
                data = data,
              )
            ContextCompat.startForegroundService(this@MainActivity, svcIntent)
          } else {
            viewModel.onStopScreenShare()
          }
        }

      val micPermissionLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
          viewModel.onAudioPermissionResult(granted)
        }

      val assistantRoleLauncher =
        rememberLauncherForActivityResult(
          contract = ActivityResultContracts.StartActivityForResult()
        ) { _ -> }

      val startScreenShareFlow: () -> Unit = {
        viewModel.onMarkScreenShareRequestingConsent()
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mgr != null) {
          screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
        } else {
          viewModel.onStopScreenShare()
        }
      }

      val stopScreenShareFlow: () -> Unit = {
        startService(MediaProjectionCaptureService.buildStopIntent(this@MainActivity))
        viewModel.onStopScreenShare()
      }

      LaunchedEffect(initialQuickAction) {
        if (initialQuickAction != null) {
          viewModel.onHandleOrbQuickAction(
            action = initialQuickAction,
            onRequestScreenShare = startScreenShareFlow,
            onNavigateToControlCenter = {
              navController.navigate(MousomiRoutes.CONTROL_CENTER) { launchSingleTop = true }
            },
          )
        }
      }

      // Sync optional system-wide floating overlay service safely when explicitly allowed
      LaunchedEffect(uiState.edgeLightConfig.systemOverlayEnabled) {
        if (uiState.edgeLightConfig.systemOverlayEnabled && Settings.canDrawOverlays(this@MainActivity)) {
          val intent = Intent(this@MainActivity, MousomiOverlayService::class.java)
          ContextCompat.startForegroundService(this@MainActivity, intent)
        } else {
          stopService(Intent(this@MainActivity, MousomiOverlayService::class.java))
        }
      }

      MousomiTheme(themeMode = uiState.themeMode) {
        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(32.dp))
              .clipToBounds()
              .background(MaterialTheme.colorScheme.background)
        ) {
          NavHost(
            navController = navController,
            startDestination = MousomiRoutes.CHAT,
            enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
          ) {
            composable(MousomiRoutes.CHAT) {
              MainChatScreen(
                uiState = uiState,
                suggestions = viewModel.defaultSuggestions,
                viewModel = viewModel,
                onNavigateToSettings = {
                  navController.navigate(MousomiRoutes.SETTINGS) { launchSingleTop = true }
                },
                onNavigateToControlCenter = {
                  navController.navigate(MousomiRoutes.CONTROL_CENTER) { launchSingleTop = true }
                },
                onRequestStartScreenShare = startScreenShareFlow,
                onStopScreenShare = stopScreenShareFlow,
              )
            }

            composable(MousomiRoutes.CONTROL_CENTER) {
              ControlCenterScreen(
                uiState = uiState,
                onUpdateEdgeLight = viewModel::onUpdateEdgeLightConfig,
                onUpdateFloatingOrb = viewModel::onUpdateFloatingOrbConfig,
                onUpdateScreenConfig = viewModel::onUpdateScreenIntelligenceConfig,
                onRequestStartScreenShare = startScreenShareFlow,
                onStopScreenShare = stopScreenShareFlow,
                onAnalyzeCurrentScreen = {
                  navController.popBackStack()
                  viewModel.analyzeLatestScreenFrameOrPrompt(startScreenShareFlow)
                },
                onOpenDrawingStudio = { viewModel.onToggleDrawingOverlay(true) },
                onUpdateAgentBudget = viewModel::onUpdateAgentBudgetConfig,
                onSaveMemory = viewModel::onSaveMemoryItem,
                onDeleteMemory = viewModel::onDeleteMemoryItem,
                onClearMemories = viewModel::onClearAllMemories,
                onUpdateNotificationConfig = viewModel::onUpdateNotificationConfig,
                onDismissNotificationSummary = viewModel::onDismissNotificationSummary,
                onRunAutomation = viewModel::onRunAutomationWorkflow,
                onConfirmAutomation = viewModel::onConfirmAutomationTask,
                onCancelAutomation = viewModel::onCancelAutomationTask,
                onUpdatePrivacyPerformance = viewModel::onUpdatePrivacyPerformanceConfig,
                onClearTempMediaCache = viewModel::onClearTempMediaCache,
                onToggleSystemOverlay = { enabled ->
                  if (enabled && !Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(PermissionCenterHelper.createOverlaySettingsIntent(this@MainActivity))
                  }
                },
                onOpenOverlaySettings = {
                  startActivity(PermissionCenterHelper.createOverlaySettingsIntent(this@MainActivity))
                },
                onOpenNotificationAccessSettings = {
                  startActivity(PermissionCenterHelper.createNotificationListenerSettingsIntent())
                },
                onRequestAssistantRole = {
                  assistantRoleLauncher.launch(AssistantRoleHelper.createRequestAssistantIntent(this@MainActivity))
                },
                onRequestMicPermission = {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onOpenApiAndModelSettings = {
                  navController.navigate(MousomiRoutes.SETTINGS) { launchSingleTop = true }
                },
                onBack = { navController.popBackStack() },
              )
            }

            composable(MousomiRoutes.SETTINGS) {
              SettingsScreen(
                geminiApiKey = uiState.geminiApiKey,
                groqApiKey = uiState.groqApiKey,
                themeMode = uiState.themeMode,
                connectionMode = uiState.connectionMode,
                proxyBaseUrl = uiState.proxyBaseUrl,
                hydrogenBackingModelId = uiState.hydrogenBackingModelId,
                groqReasoningModelId = uiState.groqReasoningModelId,
                externalSearchUrl = uiState.externalSearchUrl,
                externalSearchApiKey = uiState.externalSearchApiKey,
                analyticsSandboxEnabled = uiState.analyticsSandboxEnabled,
                voicePreferences = uiState.voicePreferences,
                settingsErrorMessage = uiState.settingsErrorMessage,
                onGeminiApiKeyChange = viewModel::onUpdateGeminiApiKey,
                onGroqApiKeyChange = viewModel::onUpdateGroqApiKey,
                onThemeModeChange = viewModel::onUpdateThemeMode,
                onConnectionModeChange = viewModel::onUpdateConnectionMode,
                onProxyBaseUrlChange = viewModel::onUpdateProxyBaseUrl,
                onHydrogenBackingModelIdChange = viewModel::onUpdateHydrogenBackingModelId,
                onGroqReasoningModelIdChange = viewModel::onUpdateGroqReasoningModelId,
                onExternalSearchUrlChange = viewModel::onUpdateExternalSearchUrl,
                onExternalSearchApiKeyChange = viewModel::onUpdateExternalSearchApiKey,
                onAnalyticsSandboxEnabledChange = viewModel::onUpdateAnalyticsSandboxEnabled,
                onClearTempMediaCache = viewModel::onClearTempMediaCache,
                onPreferredLanguageChange = viewModel::onUpdatePreferredLanguage,
                onAutoSpeakChange = viewModel::onUpdateAutoSpeak,
                onSpeechRateChange = viewModel::onUpdateSpeechRate,
                onSaveSettings = viewModel::onSaveSettings,
                onBack = { navController.popBackStack() },
              )
            }
          }

          val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
          val edgeParams =
            EdgeLightStateMapper.resolve(
              config = uiState.edgeLightConfig,
              responseState = uiState.responseState,
              voiceState = uiState.voiceState,
              voiceRmsNormalized = uiState.voiceRmsNormalized,
              screenShareStatus = uiState.screenShareStatus,
              notificationAlertActive = uiState.notificationPulseActive,
              isSystemPowerSave = uiState.isSystemPowerSave,
              currentHour = currentHour,
            )
          EdgeLightPerimeterOverlay(params = edgeParams)
        }
      }
    }
  }

  private fun extractQuickAction(intent: Intent?): OrbQuickAction? {
    if (intent == null) return null
    val raw = intent.getStringExtra(EXTRA_QUICK_ACTION)
    if (!raw.isNullOrBlank()) {
      return runCatching { OrbQuickAction.valueOf(raw) }.getOrNull()
    }
    if (intent.action == Intent.ACTION_ASSIST) {
      return OrbQuickAction.START_VOICE
    }
    return null
  }

  companion object {
    const val EXTRA_QUICK_ACTION = "extra_orb_quick_action"
  }
}
