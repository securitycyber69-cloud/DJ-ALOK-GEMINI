package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.edge.EdgeLightStateMapper
import com.example.core.voice.VoiceState
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AgentStepPlan
import com.example.domain.model.AgentTaskLogItem
import com.example.domain.model.AiResponseState
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.AutomationWorkflowType
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.MemoryCategory
import com.example.domain.model.MemoryRecord
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.NotificationSummaryRecord
import com.example.domain.model.PermissionDashboardItem
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.ScreenShareStatus
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.model.MousomiUiState
import com.example.ui.theme.LocalMousomiColors

@Composable
fun StudioCard(
  title: String,
  subtitle: String? = null,
  content: @Composable () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = LocalMousomiColors.current.mutedText,
          )
        }
      }
      content()
    }
  }
}

@Composable
fun ToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = LocalMousomiColors.current.mutedText,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
  }
}

@Composable
fun EdgeLightStudioSection(
  config: EdgeLightConfig,
  voiceRms: Float,
  screenShareStatus: ScreenShareStatus,
  onUpdate: (EdgeLightConfig) -> Unit,
  onToggleSystemOverlay: (Boolean) -> Unit,
) {
  val previewParams =
    EdgeLightStateMapper.resolve(
      config = config,
      responseState = AiResponseState.Idle,
      voiceState = VoiceState.Idle,
      voiceRmsNormalized = voiceRms,
      screenShareStatus = screenShareStatus,
      notificationAlertActive = false,
      isSystemPowerSave = false,
      currentHour = 12,
      forcePreview = config.enabled,
    )

  StudioCard(
    title = "1. Edge Light Studio",
    subtitle = "Full four-side animated perimeter glow synced to AI, Voice, Search & Research",
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(110.dp)
          .background(Color(0xFF05070E), RoundedCornerShape(20.dp)),
      contentAlignment = Alignment.Center,
    ) {
      EdgeLightPerimeterOverlay(params = previewParams.copy(cornerRadiusDp = 20f))
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = config.preset.label,
          color = Color.White,
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text = if (config.enabled) "Live Perimeter Active" else "Edge Light Off",
          color = Color.White.copy(alpha = 0.65f),
          fontSize = 12.sp,
        )
      }
    }

    ToggleRow(
      title = "Enable Full Edge Light",
      subtitle = "Illuminates all 4 sides cleanly without blocking touches",
      checked = config.enabled,
      onCheckedChange = { onUpdate(config.copy(enabled = it)) },
      testTag = "edge_light_master_switch",
    )

    Text("Animation Preset (11 Styles)", style = MaterialTheme.typography.labelLarge)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      EdgeLightPreset.entries.forEach { preset ->
        FilterChip(
          selected = config.preset == preset,
          onClick = { onUpdate(config.copy(preset = preset)) },
          label = { Text(preset.label) },
          modifier = Modifier.testTag("edge_preset_${preset.name}"),
        )
      }
    }

    Text("Brightness (${(config.brightness * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium)
    Slider(
      value = config.brightness,
      onValueChange = { onUpdate(config.copy(brightness = it)) },
      valueRange = 0.15f..1.0f,
    )

    Text("Speed (${"%.1f".format(config.animationSpeed)}x)", style = MaterialTheme.typography.labelMedium)
    Slider(
      value = config.animationSpeed,
      onValueChange = { onUpdate(config.copy(animationSpeed = it)) },
      valueRange = 0.3f..2.2f,
    )

    Text("Border Thickness (${config.borderThicknessDp.toInt()} dp)", style = MaterialTheme.typography.labelMedium)
    Slider(
      value = config.borderThicknessDp,
      onValueChange = { onUpdate(config.copy(borderThicknessDp = it)) },
      valueRange = 2f..12f,
    )

    ToggleRow(
      title = "Battery Saver Dimming",
      subtitle = "Reduces glow intensity & frame sweep rate to save battery",
      checked = config.batterySaverMode,
      onCheckedChange = { onUpdate(config.copy(batterySaverMode = it)) },
      testTag = "edge_battery_saver_switch",
    )

    ToggleRow(
      title = "Reduced Motion",
      subtitle = "Static soft rim glow with zero continuous motion",
      checked = config.reducedMotion,
      onCheckedChange = { onUpdate(config.copy(reducedMotion = it)) },
      testTag = "edge_reduced_motion_switch",
    )

    ToggleRow(
      title = "Schedule (${config.scheduleStartHour}:00 - ${config.scheduleEndHour}:00)",
      subtitle = "Only animate during active hours",
      checked = config.scheduleEnabled,
      onCheckedChange = { onUpdate(config.copy(scheduleEnabled = it)) },
      testTag = "edge_schedule_switch",
    )

    ToggleRow(
      title = "System-Wide Overlay Permission Mode",
      subtitle = "Allow optional OS-level overlay service across apps",
      checked = config.systemOverlayEnabled,
      onCheckedChange = { enabled ->
        onUpdate(config.copy(systemOverlayEnabled = enabled))
        onToggleSystemOverlay(enabled)
      },
      testTag = "edge_system_overlay_switch",
    )
  }
}

@Composable
fun FloatingOrbAndScreenSection(
  orbConfig: FloatingOrbConfig,
  screenConfig: ScreenIntelligenceConfig,
  screenStatus: ScreenShareStatus,
  frameCount: Int,
  screenStatusNote: String?,
  onUpdateOrb: (FloatingOrbConfig) -> Unit,
  onUpdateScreenConfig: (ScreenIntelligenceConfig) -> Unit,
  onStartScreenShare: () -> Unit,
  onStopScreenShare: () -> Unit,
  onAnalyzeCurrentScreen: () -> Unit,
  onOpenDrawingStudio: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    StudioCard(
      title = "2. Floating AI Orb",
      subtitle = "Draggable quick assistant orb with edge snap & synced halo",
    ) {
      ToggleRow(
        title = "Enable Floating AI Orb",
        subtitle = "Single tap for 7 quick actions, double-tap for Voice",
        checked = orbConfig.enabled,
        onCheckedChange = { onUpdateOrb(orbConfig.copy(enabled = it)) },
        testTag = "floating_orb_switch",
      )
      ToggleRow(
        title = "Snap to Screen Edge",
        subtitle = "Automatically anchors orb to left or right edge on release",
        checked = orbConfig.snapToEdge,
        onCheckedChange = { onUpdateOrb(orbConfig.copy(snapToEdge = it)) },
        testTag = "floating_orb_snap_switch",
      )
      ToggleRow(
        title = "Double-Tap for Voice Mode",
        subtitle = "Quickly launch Speech-to-Text when double-tapping orb",
        checked = orbConfig.doubleTapForVoice,
        onCheckedChange = { onUpdateOrb(orbConfig.copy(doubleTapForVoice = it)) },
        testTag = "floating_orb_doubletap_switch",
      )
      Text("Orb Size (${orbConfig.sizeDp.toInt()} dp)", style = MaterialTheme.typography.labelMedium)
      Slider(
        value = orbConfig.sizeDp,
        onValueChange = { onUpdateOrb(orbConfig.copy(sizeDp = it)) },
        valueRange = 44f..76f,
      )
    }

    StudioCard(
      title = "3. Live Screen Intelligence & Drawing",
      subtitle = "Consented MediaProjection capture + On-Screen Annotation Studio",
    ) {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.activeChipContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Text(
            text = "Status: ${screenStatus.label} · Buffered Frames: $frameCount",
            style = MaterialTheme.typography.labelLarge,
          )
          if (!screenStatusNote.isNullOrBlank()) {
            Text(text = screenStatusNote, style = MaterialTheme.typography.bodySmall, color = colors.mutedText)
          }
          Text(
            text = "Privacy: Frames use perceptual-hash deduplication, stay in temp cache, and wipe immediately on Stop.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.mutedText,
          )
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick =
            if (screenStatus == ScreenShareStatus.STOPPED) onStartScreenShare else onStopScreenShare,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (screenStatus == ScreenShareStatus.STOPPED) colors.liveButtonBlue else Color(0xFFDC2626)
            ),
          modifier = Modifier.weight(1f).testTag("control_screen_share_button"),
        ) {
          Icon(
            if (screenStatus == ScreenShareStatus.STOPPED) Icons.Outlined.PlayArrow else Icons.Outlined.Stop,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (screenStatus == ScreenShareStatus.STOPPED) "Share Screen" else "Stop Sharing")
        }

        OutlinedButton(
          onClick = onOpenDrawingStudio,
          modifier = Modifier.weight(1f).testTag("control_open_drawing_button"),
        ) {
          Text("Draw & Ask AI")
        }
      }

      if (frameCount > 0) {
        OutlinedButton(
          onClick = onAnalyzeCurrentScreen,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Analyze Latest Shared Screen Frame")
        }
      }

      Text(
        "Capture Interval (${screenConfig.captureIntervalSeconds}s)",
        style = MaterialTheme.typography.labelMedium,
      )
      Slider(
        value = screenConfig.captureIntervalSeconds.toFloat(),
        onValueChange = { onUpdateScreenConfig(screenConfig.copy(captureIntervalSeconds = it.toInt())) },
        valueRange = 2f..15f,
      )
    }
  }
}

@Composable
fun AgentsAndMemorySection(
  agentBudget: AgentBudgetConfig,
  activeSteps: List<AgentStepPlan>,
  agentLogs: List<AgentTaskLogItem>,
  memories: List<MemoryRecord>,
  memoryStatus: String?,
  onUpdateAgentBudget: (AgentBudgetConfig) -> Unit,
  onSaveMemory: (MemoryCategory, String) -> Unit,
  onDeleteMemory: (String) -> Unit,
  onClearMemories: () -> Unit,
) {
  var newMemoryText by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf(MemoryCategory.FACT) }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    StudioCard(
      title = "5. Autonomous Multi-Agent Orchestrator",
      subtitle = "14 specialized agents with dependency plans, retries & approval gates",
    ) {
      ToggleRow(
        title = "Enable Multi-Agent Planning",
        subtitle = "Breaks complex goals into verified specialist steps",
        checked = agentBudget.multiAgentModeEnabled,
        onCheckedChange = { onUpdateAgentBudget(agentBudget.copy(multiAgentModeEnabled = it)) },
        testTag = "multi_agent_mode_switch",
      )
      Text("Max Agent Steps (${agentBudget.maxSteps})", style = MaterialTheme.typography.labelMedium)
      Slider(
        value = agentBudget.maxSteps.toFloat(),
        onValueChange = { onUpdateAgentBudget(agentBudget.copy(maxSteps = it.toInt())) },
        valueRange = 2f..10f,
      )
      if (activeSteps.isNotEmpty()) {
        Text("Latest Execution Plan:", style = MaterialTheme.typography.labelLarge)
        activeSteps.forEach { step ->
          Text("• ${step.agent.label} [${step.status.name}]", style = MaterialTheme.typography.bodySmall)
        }
      }
      if (agentLogs.isNotEmpty()) {
        Text("Recent Persisted Agent Runs (${agentLogs.size}):", style = MaterialTheme.typography.labelLarge)
        agentLogs.take(4).forEach { log ->
          Text(
            "• ${log.goal} — ${log.status} (${log.durationMs}ms)",
            style = MaterialTheme.typography.bodySmall,
            color = LocalMousomiColors.current.mutedText,
          )
        }
      }
    }

    StudioCard(
      title = "6. Memory Center (Keystore AES-GCM)",
      subtitle = "Approved facts & preferences. OTPs, passwords, and API keys are automatically blocked.",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        MemoryCategory.entries.forEach { cat ->
          FilterChip(
            selected = selectedCategory == cat,
            onClick = { selectedCategory = cat },
            label = { Text(cat.label) },
          )
        }
      }

      OutlinedTextField(
        value = newMemoryText,
        onValueChange = { newMemoryText = it },
        label = { Text("Add approved memory note or preference") },
        modifier = Modifier.fillMaxWidth().testTag("memory_input_field"),
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            if (newMemoryText.isNotBlank()) {
              onSaveMemory(selectedCategory, newMemoryText.trim())
              newMemoryText = ""
            }
          },
          modifier = Modifier.weight(1f).testTag("save_memory_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save Memory")
        }
        OutlinedButton(onClick = onClearMemories) {
          Text("Clear All")
        }
      }

      if (!memoryStatus.isNullOrBlank()) {
        Text(
          text = memoryStatus,
          style = MaterialTheme.typography.bodySmall,
          color = LocalMousomiColors.current.liveButtonBlue,
        )
      }

      memories.take(10).forEach { mem ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(mem.category.label, style = MaterialTheme.typography.labelSmall, color = LocalMousomiColors.current.mutedText)
              Text(mem.content, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { onDeleteMemory(mem.id) }) {
              Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete memory")
            }
          }
        }
      }
    }
  }
}

@Composable
fun NotificationsAndAutomationSection(
  uiState: MousomiUiState,
  onUpdateNotificationConfig: (NotificationIntelligenceConfig) -> Unit,
  onDismissSummary: (String) -> Unit,
  onRunAutomation: (AutomationWorkflowType, String, String) -> Unit,
  onConfirmAutomation: (AutomationTaskRecord) -> Unit,
  onCancelAutomation: (AutomationTaskRecord) -> Unit,
  onOpenNotificationAccessSettings: () -> Unit,
  onRequestAssistantRole: () -> Unit,
) {
  var quickNoteArg by remember { mutableStateOf("") }
  var reminderTitle by remember { mutableStateOf("") }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    StudioCard(
      title = "9. Smart Notification Intelligence",
      subtitle = "User-enabled Notification Access with strict OTP & 2FA code blocking",
    ) {
      ToggleRow(
        title = "Enable Notification Summaries",
        subtitle = "Groups allowlisted app alerts; draft replies require manual review",
        checked = uiState.notificationConfig.enabled,
        onCheckedChange = {
          onUpdateNotificationConfig(uiState.notificationConfig.copy(enabled = it))
        },
        testTag = "notification_intel_switch",
      )
      OutlinedButton(
        onClick = onOpenNotificationAccessSettings,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Manage Android Notification Access")
      }
      OutlinedButton(
        onClick = onRequestAssistantRole,
        modifier = Modifier.fillMaxWidth().testTag("set_default_assistant_button"),
      ) {
        Text("Set as Default Android Assistant")
      }
      uiState.notificationSummaries.take(5).forEach { item ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = LocalMousomiColors.current.activeChipContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("${item.appLabel} · ${item.importanceLevel}", style = MaterialTheme.typography.labelSmall)
              IconButton(onClick = { onDismissSummary(item.id) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Dismiss summary", modifier = Modifier.size(16.dp))
              }
            }
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(item.summary, style = MaterialTheme.typography.bodySmall, color = LocalMousomiColors.current.mutedText)
            if (!item.suggestedDraftReply.isNullOrBlank()) {
              Text(
                "Suggested draft (manual review only): ${item.suggestedDraftReply}",
                style = MaterialTheme.typography.labelSmall,
                color = LocalMousomiColors.current.liveButtonBlue,
              )
            }
          }
        }
      }
    }

    StudioCard(
      title = "10. Controlled Android Task Automation",
      subtitle = "Explicit confirmation required before external app/alarm actions",
    ) {
      OutlinedTextField(
        value = quickNoteArg,
        onValueChange = { quickNoteArg = it },
        label = { Text("Quick Note text") },
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = reminderTitle,
        onValueChange = { reminderTitle = it },
        label = { Text("Reminder / Alarm label") },
        modifier = Modifier.fillMaxWidth(),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          onClick = {
            if (quickNoteArg.isNotBlank()) {
              onRunAutomation(AutomationWorkflowType.SAVE_QUICK_NOTE, "Quick Note", quickNoteArg)
              quickNoteArg = ""
            }
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("Save Note")
        }
        OutlinedButton(
          onClick = {
            onRunAutomation(
              AutomationWorkflowType.CREATE_REMINDER,
              reminderTitle.ifBlank { "Mousomi Focus Reminder" },
              "",
            )
          },
          modifier = Modifier.weight(1f),
        ) {
          Text("New Reminder")
        }
      }

      uiState.automationHistory.take(6).forEach { task ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${task.title} · [${task.status}]", style = MaterialTheme.typography.labelLarge)
            Text(task.resultLog, style = MaterialTheme.typography.bodySmall, color = LocalMousomiColors.current.mutedText)
            if (task.status == "Awaiting Confirmation") {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = { onConfirmAutomation(task) },
                  modifier = Modifier.testTag("confirm_automation_${task.id}"),
                ) { Text("Approve") }
                OutlinedButton(
                  onClick = { onCancelAutomation(task) },
                  modifier = Modifier.testTag("cancel_automation_${task.id}"),
                ) { Text("Cancel") }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun PrivacyAndPerformanceSection(
  config: PrivacyAndPerformanceConfig,
  permissions: List<PermissionDashboardItem>,
  onUpdateConfig: (PrivacyAndPerformanceConfig) -> Unit,
  onClearTempMediaCache: () -> Unit,
  onPermissionAction: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    StudioCard(
      title = "11. Privacy & Permission Center",
      subtitle = "Transparent disclosure & one-tap control over every Android capability",
    ) {
      permissions.forEach { item ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = if (item.isGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Security,
              contentDescription = null,
              tint = if (item.isGranted) Color(0xFF10B981) else LocalMousomiColors.current.mutedText,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(item.title, style = MaterialTheme.typography.labelLarge)
              Text(item.reason, style = MaterialTheme.typography.bodySmall, color = LocalMousomiColors.current.mutedText)
              Text("How to turn off: ${item.howToDisable}", style = MaterialTheme.typography.labelSmall, color = LocalMousomiColors.current.mutedText)
            }
            if (!item.isOptionalZeroPermissionNote) {
              OutlinedButton(onClick = { onPermissionAction(item.id) }) {
                Text(if (item.isGranted) "Review" else "Enable")
              }
            }
          }
        }
      }
    }

    StudioCard(
      title = "12. Voice, Security & Performance Engine",
      subtitle = "Keystore memory encryption, continuous voice & WorkManager maintenance",
    ) {
      ToggleRow(
        title = "Encrypt Memory at Rest",
        subtitle = "Uses Android Keystore AES-GCM before writing to Room",
        checked = config.encryptSensitiveMemory,
        onCheckedChange = { onUpdateConfig(config.copy(encryptSensitiveMemory = it)) },
        testTag = "encrypt_memory_switch",
      )
      ToggleRow(
        title = "Continuous Voice Conversation",
        subtitle = "Automatically sends speech transcripts without pressing Send",
        checked = config.continuousVoiceMode,
        onCheckedChange = { onUpdateConfig(config.copy(continuousVoiceMode = it)) },
        testTag = "continuous_voice_switch",
      )
      ToggleRow(
        title = "Voice Barge-In Interruption",
        subtitle = "Interrupt active TTS speech immediately when mic starts",
        checked = config.allowVoiceInterruption,
        onCheckedChange = { onUpdateConfig(config.copy(allowVoiceInterruption = it)) },
        testTag = "voice_interruption_switch",
      )
      ToggleRow(
        title = "WorkManager Temp Media Auto-Cleanup",
        subtitle = "Scheduled 24h cleanup of expired temporary screen/media files",
        checked = config.autoCleanupTempFiles,
        onCheckedChange = { onUpdateConfig(config.copy(autoCleanupTempFiles = it)) },
        testTag = "auto_cleanup_switch",
      )
      OutlinedButton(onClick = onClearTempMediaCache, modifier = Modifier.fillMaxWidth()) {
        Text("Clear Temporary Media & Screen Frames Now")
      }
    }
  }
}
