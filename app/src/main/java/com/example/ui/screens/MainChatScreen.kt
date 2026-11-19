package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.edge.EdgeLightStateMapper
import com.example.domain.model.MemoryCategory
import com.example.domain.model.ScreenShareStatus
import com.example.ui.components.ChatMessagesList
import com.example.ui.components.DrawingAnnotationOverlay
import com.example.ui.components.EdgeLightPerimeterOverlay
import com.example.ui.components.GeminiBottomDock
import com.example.ui.components.GeminiTopBar
import com.example.ui.components.GeminiWelcomeCanvas
import com.example.ui.components.InAppFloatingOrbOverlay
import com.example.ui.components.geminiBackgroundGlow
import com.example.ui.model.MousomiUiState
import com.example.ui.model.SuggestionItem
import com.example.ui.viewmodel.MousomiViewModel
import java.util.Calendar
import kotlinx.coroutines.launch

@Composable
fun MainChatScreen(
  uiState: MousomiUiState,
  suggestions: List<SuggestionItem>,
  viewModel: MousomiViewModel,
  onNavigateToSettings: () -> Unit,
  onNavigateToControlCenter: () -> Unit = {},
  onRequestStartScreenShare: () -> Unit = {},
  onStopScreenShare: () -> Unit = {},
) {
  val context = LocalContext.current
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val coroutineScope = rememberCoroutineScope()

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        viewModel.onAttachPickedPhoto(uri.toString())
      }
    }

  val multiPhotoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.onAttachPickedPhotos(uris.map { it.toString() })
      }
    }

  val videoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        viewModel.onAttachPickedVideo(uri.toString())
      }
    }

  val audioPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        viewModel.onAttachPickedAudio(uri.toString())
      }
    }

  val documentPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
      if (uris.isNotEmpty()) {
        viewModel.onAttachPickedDocuments(uris.map { it.toString() })
      }
    }

  val audioPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      viewModel.onAudioPermissionResult(granted)
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

  ModalNavigationDrawer(
    drawerState = drawerState,
    gesturesEnabled = !uiState.isGeminiLiveOpen && !uiState.isDrawingOverlayOpen,
    drawerContent = {
      MousomiDrawerContent(
        conversations = uiState.conversations,
        activeConversationId = uiState.activeConversationId,
        onSelectConversation = { id ->
          viewModel.onSelectConversation(id)
          coroutineScope.launch { drawerState.close() }
        },
        onDeleteConversation = viewModel::onDeleteConversation,
        onNewChat = {
          viewModel.onNewChat()
          coroutineScope.launch { drawerState.close() }
        },
        onPreviewChatBubbles = {
          viewModel.onLoadSampleConversation()
          coroutineScope.launch { drawerState.close() }
        },
        onOpenSettings = {
          coroutineScope.launch { drawerState.close() }
          onNavigateToSettings()
        },
        onOpenUpgrade = {
          coroutineScope.launch { drawerState.close() }
          viewModel.onToggleUpgradeSheet(true)
        },
        onOpenControlCenter = {
          coroutineScope.launch { drawerState.close() }
          onNavigateToControlCenter()
        },
      )
    },
  ) {
    Box(modifier = Modifier.fillMaxSize().geminiBackgroundGlow()) {
      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize(),
      ) { _ ->
        Column(
          modifier = Modifier.fillMaxSize(),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          GeminiTopBar(
            selectedModel = uiState.selectedModel,
            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
            onOpenModelSelector = { viewModel.onToggleModelMenu(true) },
            onNewChat = viewModel::onNewChat,
            onOpenUpgrade = { viewModel.onToggleUpgradeSheet(true) },
            onOpenProfile = { viewModel.onToggleProfileDialog(true) },
            modifier = Modifier.widthIn(max = 760.dp),
          )

          Box(
            modifier = Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp),
            contentAlignment = Alignment.Center,
          ) {
            Crossfade(
              targetState = uiState.messages.isEmpty(),
              label = "welcome_or_chat_list",
            ) { isWelcome ->
              if (isWelcome) {
                GeminiWelcomeCanvas(
                  suggestions = suggestions,
                  onSuggestionClick = { item -> viewModel.onSelectSuggestion(item.promptText) },
                )
              } else {
                ChatMessagesList(
                  messages = uiState.messages,
                  responseState = uiState.responseState,
                  voiceState = uiState.voiceState,
                  onOpenSettings = onNavigateToSettings,
                  onEditUserMessage = viewModel::onBeginEditMessage,
                  onRegenerateMessage = viewModel::onRegenerateFromMessage,
                  onSpeakMessage = viewModel::onSpeakMessage,
                  onStopSpeaking = viewModel::onStopSpeaking,
                )
              }
            }
          }

          GeminiBottomDock(
            inputMode = uiState.inputMode,
            inputText = uiState.inputText,
            pendingAttachments = uiState.pendingAttachments,
            isGenerating = uiState.isGenerating,
            isEditingMessage = uiState.editingMessageId != null,
            onSelectMode = viewModel::onSelectMode,
            onInputChanged = viewModel::onInputChanged,
            onOpenAttachments = { viewModel.onToggleAttachmentSheet(true) },
            onRemoveAttachment = viewModel::onRemovePendingAttachment,
            onCancelEditing = viewModel::onCancelEditing,
            onVoiceClick = {
              val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                  PackageManager.PERMISSION_GRANTED
              if (granted) {
                viewModel.onAudioPermissionResult(true)
              } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            onLiveClick = { viewModel.onToggleGeminiLive(true) },
            onSendClick = viewModel::onSendMessage,
            onStopGeneration = viewModel::onStopGeneration,
            modifier = Modifier.widthIn(max = 760.dp),
          )
        }
      }

      if (uiState.screenShareStatus != ScreenShareStatus.STOPPED) {
        Surface(
          shape = CircleShape,
          color = Color(0xFF0E172A).copy(alpha = 0.94f),
          border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
          modifier =
            Modifier
              .align(Alignment.TopCenter)
              .statusBarsPadding()
              .padding(top = 58.dp)
              .testTag("screen_share_active_pill"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.AutoMirrored.Outlined.ScreenShare, contentDescription = null, tint = Color(0xFF38BDF8))
            Text(
              text = "${uiState.screenShareStatus.label} (${uiState.capturedScreenFrames.size})",
              color = Color.White,
              fontSize = 12.sp,
            )
            TextButton(onClick = onStopScreenShare) {
              Text("Stop", color = Color(0xFFF87171))
            }
          }
        }
      }

      if (uiState.isModelMenuOpen) {
        ModelSelectionBottomSheet(
          selectedModel = uiState.selectedModel,
          onSelectModel = viewModel::onSelectModel,
          onDismiss = { viewModel.onToggleModelMenu(false) },
        )
      }

      if (uiState.isAttachmentSheetOpen) {
        AttachmentBottomSheet(
          onPickFromGallery = {
            viewModel.onToggleAttachmentSheet(false)
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          onPickMultipleFromGallery = {
            viewModel.onToggleAttachmentSheet(false)
            multiPhotoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          onPickVideo = {
            viewModel.onToggleAttachmentSheet(false)
            videoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          },
          onPickAudio = {
            viewModel.onToggleAttachmentSheet(false)
            audioPickerLauncher.launch(arrayOf("audio/*"))
          },
          onPickDocuments = {
            viewModel.onToggleAttachmentSheet(false)
            documentPickerLauncher.launch(
              arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/csv",
                "text/comma-separated-values",
                "text/plain",
                "application/json",
              )
            )
          },
          onAttachSamplePreview = viewModel::onAttachSampleImage,
          onDismiss = { viewModel.onToggleAttachmentSheet(false) },
        )
      }

      if (uiState.isUpgradeSheetOpen) {
        UpgradeModalSheet(onDismiss = { viewModel.onToggleUpgradeSheet(false) })
      }

      if (uiState.isProfileDialogOpen) {
        ProfileAccountDialog(
          onOpenSettings = onNavigateToSettings,
          onDismiss = { viewModel.onToggleProfileDialog(false) },
        )
      }

      if (uiState.isVoiceDialogOpen) {
        VoiceInputDialog(
          hasAudioPermission = uiState.hasAudioPermission,
          voiceState = uiState.voiceState,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
          onUseTranscript = viewModel::onInputChanged,
          onDismiss = { viewModel.onToggleVoiceDialog(false) },
        )
      }

      AnimatedVisibility(
        visible = uiState.isGeminiLiveOpen,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        GeminiLiveOverlay(
          voiceState = uiState.voiceState,
          onStartListening = viewModel::onStartVoiceListening,
          onStopListening = viewModel::onStopVoiceListening,
          onClose = { viewModel.onToggleGeminiLive(false) },
        )
      }

      if (uiState.isQuickNoteDialogOpen) {
        QuickNoteCaptureDialog(
          onSaveNote = { text -> viewModel.onSaveMemoryItem(MemoryCategory.QUICK_NOTE, text) },
          onDismiss = { viewModel.onToggleQuickNoteDialog(false) },
        )
      }

      if (uiState.isDrawingOverlayOpen) {
        DrawingAnnotationOverlay(
          onAnalyzeMarkedRegion = viewModel::onAnalyzeAnnotatedRegion,
          onClose = { viewModel.onToggleDrawingOverlay(false) },
        )
      }

      InAppFloatingOrbOverlay(
        config = uiState.floatingOrbConfig,
        visualState = edgeParams.visualState,
        onPositionCommitted = viewModel::onOrbPositionCommitted,
        onQuickAction = { action ->
          viewModel.onHandleOrbQuickAction(
            action = action,
            onRequestScreenShare = onRequestStartScreenShare,
            onNavigateToControlCenter = onNavigateToControlCenter,
          )
        },
      )

      // Edge light overlay is globally handled at the root MainActivity container level
    }
  }
}
