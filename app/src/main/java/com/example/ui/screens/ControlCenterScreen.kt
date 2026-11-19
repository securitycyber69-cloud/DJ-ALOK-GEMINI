package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.permissions.PermissionCenterHelper
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.AutomationWorkflowType
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.MemoryCategory
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.ui.model.MousomiUiState

private val CONTROL_CENTER_TABS =
  listOf("Lighting & Orb", "Agents & Memory", "Assistant & Auto", "Privacy & Perf")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenterScreen(
  uiState: MousomiUiState,
  onUpdateEdgeLight: (EdgeLightConfig) -> Unit,
  onUpdateFloatingOrb: (FloatingOrbConfig) -> Unit,
  onUpdateScreenConfig: (ScreenIntelligenceConfig) -> Unit,
  onRequestStartScreenShare: () -> Unit,
  onStopScreenShare: () -> Unit,
  onAnalyzeCurrentScreen: () -> Unit,
  onOpenDrawingStudio: () -> Unit,
  onUpdateAgentBudget: (com.example.domain.model.AgentBudgetConfig) -> Unit,
  onSaveMemory: (MemoryCategory, String) -> Unit,
  onDeleteMemory: (String) -> Unit,
  onClearMemories: () -> Unit,
  onUpdateNotificationConfig: (NotificationIntelligenceConfig) -> Unit,
  onDismissNotificationSummary: (String) -> Unit,
  onRunAutomation: (AutomationWorkflowType, String, String) -> Unit,
  onConfirmAutomation: (AutomationTaskRecord) -> Unit,
  onCancelAutomation: (AutomationTaskRecord) -> Unit,
  onUpdatePrivacyPerformance: (PrivacyAndPerformanceConfig) -> Unit,
  onClearTempMediaCache: () -> Unit,
  onToggleSystemOverlay: (Boolean) -> Unit,
  onOpenOverlaySettings: () -> Unit,
  onOpenNotificationAccessSettings: () -> Unit,
  onRequestAssistantRole: () -> Unit,
  onRequestMicPermission: () -> Unit,
  onOpenApiAndModelSettings: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  var selectedTab by remember { mutableIntStateOf(0) }
  val permissions =
    remember(
      uiState.screenShareStatus,
      uiState.edgeLightConfig.systemOverlayEnabled,
      uiState.notificationConfig.enabled,
      uiState.hasAudioPermission,
    ) {
      PermissionCenterHelper.buildDashboardItems(context, uiState.screenShareStatus)
    }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Mousomi AI Control Center") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("control_center_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = onOpenApiAndModelSettings, modifier = Modifier.testTag("control_center_to_settings")) {
            Icon(Icons.Outlined.Settings, contentDescription = "API & Model Settings")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        CONTROL_CENTER_TABS.forEachIndexed { idx, label ->
          FilterChip(
            selected = selectedTab == idx,
            onClick = { selectedTab = idx },
            label = { Text(label) },
            modifier = Modifier.testTag("control_tab_$idx"),
          )
        }
      }

      Column(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (selectedTab) {
          0 -> {
            EdgeLightStudioSection(
              config = uiState.edgeLightConfig,
              voiceRms = uiState.voiceRmsNormalized,
              screenShareStatus = uiState.screenShareStatus,
              onUpdate = onUpdateEdgeLight,
              onToggleSystemOverlay = onToggleSystemOverlay,
            )
            FloatingOrbAndScreenSection(
              orbConfig = uiState.floatingOrbConfig,
              screenConfig = uiState.screenIntelligenceConfig,
              screenStatus = uiState.screenShareStatus,
              frameCount = uiState.capturedScreenFrames.size,
              screenStatusNote = uiState.screenShareStatusMessage,
              onUpdateOrb = onUpdateFloatingOrb,
              onUpdateScreenConfig = onUpdateScreenConfig,
              onStartScreenShare = onRequestStartScreenShare,
              onStopScreenShare = onStopScreenShare,
              onAnalyzeCurrentScreen = onAnalyzeCurrentScreen,
              onOpenDrawingStudio = {
                onBack()
                onOpenDrawingStudio()
              },
            )
          }
          1 -> {
            AgentsAndMemorySection(
              agentBudget = uiState.agentBudgetConfig,
              activeSteps = uiState.activeAgentSteps,
              agentLogs = uiState.agentTaskHistory,
              memories = uiState.memories,
              memoryStatus = uiState.memoryStatusMessage,
              onUpdateAgentBudget = onUpdateAgentBudget,
              onSaveMemory = onSaveMemory,
              onDeleteMemory = onDeleteMemory,
              onClearMemories = onClearMemories,
            )
          }
          2 -> {
            NotificationsAndAutomationSection(
              uiState = uiState,
              onUpdateNotificationConfig = onUpdateNotificationConfig,
              onDismissSummary = onDismissNotificationSummary,
              onRunAutomation = onRunAutomation,
              onConfirmAutomation = onConfirmAutomation,
              onCancelAutomation = onCancelAutomation,
              onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
              onRequestAssistantRole = onRequestAssistantRole,
            )
          }
          else -> {
            PrivacyAndPerformanceSection(
              config = uiState.privacyPerformanceConfig,
              permissions = permissions,
              onUpdateConfig = onUpdatePrivacyPerformance,
              onClearTempMediaCache = onClearTempMediaCache,
              onPermissionAction = { id ->
                when (id) {
                  "mic" -> onRequestMicPermission()
                  "overlay" -> onOpenOverlaySettings()
                  "screen_share" ->
                    if (uiState.screenShareStatus.name == "STOPPED") onRequestStartScreenShare()
                    else onStopScreenShare()
                  "notif_listener" -> onOpenNotificationAccessSettings()
                  "assistant" -> onRequestAssistantRole()
                  else -> onOpenApiAndModelSettings()
                }
              },
            )
          }
        }
      }
    }
  }
}
