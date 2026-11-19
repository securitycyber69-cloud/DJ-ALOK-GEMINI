package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.automation.ControlledAutomationEngine
import com.example.core.image.ImageProcessor
import com.example.core.notifications.NotificationIntelligenceEngine
import com.example.core.performance.SmartPerformanceMonitor
import com.example.core.screen.RegionCropper
import com.example.core.screen.ScreenShareSessionManager
import com.example.core.security.SecurityValidator
import com.example.core.storage.TempMediaCacheManager
import com.example.core.voice.VoiceController
import com.example.core.voice.VoiceState
import com.example.data.repository.Part4OperationsRepository
import com.example.domain.agent.AutonomousAgentOrchestrator
import com.example.domain.memory.LongTermMemoryManager
import com.example.domain.memory.MemoryWriteOutcome
import com.example.domain.model.AgentBudgetConfig
import com.example.domain.model.AiResponseState
import com.example.domain.model.AnnotationStroke
import com.example.domain.model.AppSettings
import com.example.domain.model.AutomationTaskRecord
import com.example.domain.model.AutomationWorkflowType
import com.example.domain.model.ConnectionMode
import com.example.domain.model.DomainMessage
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.MediaAttachmentKind
import com.example.domain.model.MemoryCategory
import com.example.domain.model.NormBoundingBox
import com.example.domain.model.NotificationIntelligenceConfig
import com.example.domain.model.OrbQuickAction
import com.example.domain.model.PendingMediaItem
import com.example.domain.model.PrivacyAndPerformanceConfig
import com.example.domain.model.ProcessedImagePayload
import com.example.domain.model.ResponseBlock
import com.example.domain.model.ResponseLanguage
import com.example.domain.model.ScreenIntelligenceConfig
import com.example.domain.model.TokenUsage
import com.example.domain.model.VoicePreferences
import com.example.domain.orchestrator.AiOrchestrator
import com.example.domain.orchestrator.OrchestrationEvent
import com.example.domain.orchestrator.OrchestrationRequest
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.SettingsRepository
import com.example.ui.model.AiModelOption
import com.example.ui.model.AttachmentPreview
import com.example.ui.model.ChatMessage
import com.example.ui.model.InputMode
import com.example.ui.model.MousomiUiState
import com.example.ui.model.SuggestionIconType
import com.example.ui.model.SuggestionItem
import com.example.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MousomiViewModel @Inject constructor(
  private val aiOrchestrator: AiOrchestrator,
  private val chatRepository: ChatRepository,
  private val settingsRepository: SettingsRepository,
  private val imageProcessor: ImageProcessor,
  private val voiceController: VoiceController,
  private val securityValidator: SecurityValidator,
  private val tempMediaCacheManager: TempMediaCacheManager? = null,
  private val screenShareSessionManager: ScreenShareSessionManager? = null,
  private val longTermMemoryManager: LongTermMemoryManager? = null,
  private val notificationEngine: NotificationIntelligenceEngine? = null,
  private val automationEngine: ControlledAutomationEngine? = null,
  private val agentOrchestrator: AutonomousAgentOrchestrator? = null,
  private val operationsRepository: Part4OperationsRepository? = null,
  private val performanceMonitor: SmartPerformanceMonitor? = null,
) : ViewModel() {

  constructor(
    aiOrchestrator: AiOrchestrator,
    chatRepository: ChatRepository,
    settingsRepository: SettingsRepository,
    imageProcessor: ImageProcessor,
    voiceController: VoiceController,
    securityValidator: SecurityValidator,
  ) : this(
    aiOrchestrator = aiOrchestrator,
    chatRepository = chatRepository,
    settingsRepository = settingsRepository,
    imageProcessor = imageProcessor,
    voiceController = voiceController,
    securityValidator = securityValidator,
    tempMediaCacheManager = null,
    screenShareSessionManager = null,
    longTermMemoryManager = null,
    notificationEngine = null,
    automationEngine = null,
    agentOrchestrator = null,
    operationsRepository = null,
    performanceMonitor = null,
  )

  val defaultSuggestions: List<SuggestionItem> =
    listOf(
      SuggestionItem(
        id = "story",
        promptText = "Spark a story idea from my experiences",
        iconType = SuggestionIconType.PENCIL,
      ),
      SuggestionItem(
        id = "captions",
        promptText = "Create engaging video captions",
        iconType = SuggestionIconType.VIDEO_CAPTIONS,
      ),
      SuggestionItem(
        id = "wallpaper",
        promptText = "Make a cutout phone wallpaper",
        iconType = SuggestionIconType.WALLPAPER_SPARKLES,
      ),
    )

  private val _uiState = MutableStateFlow(MousomiUiState())
  val uiState: StateFlow<MousomiUiState> = _uiState.asStateFlow()

  private var activeGenerationJob: Job? = null
  private var messagesObserverJob: Job? = null
  private var activeAssistantMessageId: String? = null

  init {
    observeSettings()
    observeConversations()
    observeVoiceState()
    observePart4Streams()
    observeActiveConversationMessages(_uiState.value.activeConversationId)
    refreshPowerState()
  }

  fun refreshPowerState() {
    val powerSave = performanceMonitor?.isPowerSaveMode() ?: false
    _uiState.update { it.copy(isSystemPowerSave = powerSave) }
  }

  private fun observeSettings() {
    viewModelScope.launch {
      settingsRepository.observeSettings().collectLatest { settings ->
        val mode =
          runCatching { AppThemeMode.valueOf(settings.themeModeName) }
            .getOrDefault(AppThemeMode.DARK)
        val savedModel =
          runCatching { AiModelOption.valueOf(settings.selectedModelOptionName) }
            .getOrDefault(AiModelOption.GEMINI_4_HYDROGEN)
        notificationEngine?.updateConfig(settings.notificationConfig)
        screenShareSessionManager?.updateConfig(settings.screenIntelligenceConfig)
        performanceMonitor?.schedulePeriodicCacheCleanup(settings.privacyPerformanceConfig.autoCleanupTempFiles)

        _uiState.update {
          it.copy(
            themeMode = mode,
            selectedModel = savedModel,
            connectionMode = settings.connectionMode,
            proxyBaseUrl = settings.proxyBaseUrl,
            geminiApiKey = settings.geminiApiKey,
            groqApiKey = settings.groqApiKey,
            hydrogenBackingModelId = settings.hydrogenBackingModelId,
            groqReasoningModelId = settings.groqReasoningModelId,
            externalSearchUrl = settings.externalSearchUrl,
            externalSearchApiKey = settings.externalSearchApiKey,
            analyticsSandboxEnabled = settings.analyticsSandboxEnabled,
            voicePreferences = settings.voicePreferences,
            edgeLightConfig = settings.edgeLightConfig,
            floatingOrbConfig = settings.floatingOrbConfig,
            screenIntelligenceConfig = settings.screenIntelligenceConfig,
            agentBudgetConfig = settings.agentBudgetConfig,
            notificationConfig = settings.notificationConfig,
            privacyPerformanceConfig = settings.privacyPerformanceConfig,
          )
        }
      }
    }
  }

  private fun observePart4Streams() {
    viewModelScope.launch {
      voiceController.audioRmsNormalized.collectLatest { rms ->
        _uiState.update { it.copy(voiceRmsNormalized = rms) }
      }
    }
    screenShareSessionManager?.let { mgr ->
      viewModelScope.launch {
        mgr.status.collectLatest { s -> _uiState.update { it.copy(screenShareStatus = s) } }
      }
      viewModelScope.launch {
        mgr.frames.collectLatest { f -> _uiState.update { it.copy(capturedScreenFrames = f) } }
      }
      viewModelScope.launch {
        mgr.lastStatusMessage.collectLatest { msg -> _uiState.update { it.copy(screenShareStatusMessage = msg) } }
      }
    }
    longTermMemoryManager?.let { mem ->
      viewModelScope.launch {
        mem.observeMemories().collectLatest { list -> _uiState.update { it.copy(memories = list) } }
      }
    }
    notificationEngine?.let { engine ->
      viewModelScope.launch {
        engine.summaries.collectLatest { list -> _uiState.update { it.copy(notificationSummaries = list) } }
      }
      viewModelScope.launch {
        engine.notificationPulseTick.collectLatest { tick ->
          if (tick > 0L && _uiState.value.edgeLightConfig.notificationAlertPulseEnabled) {
            _uiState.update { it.copy(notificationPulseActive = true) }
            delay(2800L)
            _uiState.update { it.copy(notificationPulseActive = false) }
          }
        }
      }
    }
    agentOrchestrator?.let { orch ->
      viewModelScope.launch {
        orch.activeSteps.collectLatest { steps -> _uiState.update { it.copy(activeAgentSteps = steps) } }
      }
    }
    operationsRepository?.let { repo ->
      viewModelScope.launch {
        repo.observeAgentTasks().collectLatest { logs -> _uiState.update { it.copy(agentTaskHistory = logs) } }
      }
      viewModelScope.launch {
        repo.observeAutomationTasks().collectLatest { autos -> _uiState.update { it.copy(automationHistory = autos) } }
      }
    }
  }

  private fun observeConversations() {
    viewModelScope.launch {
      chatRepository.observeConversations().collectLatest { list ->
        _uiState.update { it.copy(conversations = list) }
      }
    }
  }

  private fun observeVoiceState() {
    viewModelScope.launch {
      voiceController.voiceState.collectLatest { vState ->
        _uiState.update { state ->
          val updatedInput =
            if (vState is VoiceState.TranscriptReady) vState.transcript else state.inputText
          val closeVoice = vState is VoiceState.TranscriptReady
          state.copy(
            voiceState = vState,
            inputText = updatedInput,
            isVoiceDialogOpen = if (closeVoice) false else state.isVoiceDialogOpen,
          )
        }
        if (vState is VoiceState.TranscriptReady) {
          val transcript = vState.transcript.trim()
          voiceController.resetState()
          if (_uiState.value.privacyPerformanceConfig.continuousVoiceMode && transcript.isNotBlank()) {
            onSendMessage()
          }
        }
      }
    }
  }

  private fun observeActiveConversationMessages(conversationId: String) {
    messagesObserverJob?.cancel()
    messagesObserverJob =
      viewModelScope.launch {
        chatRepository.observeMessages(conversationId).collectLatest { domainMessages ->
          val mapped = domainMessages.map(::toUiMessage)
          _uiState.update { state ->
            val streamingId = activeAssistantMessageId
            val currentStreaming =
              if (state.isGenerating && streamingId != null) {
                state.messages.firstOrNull { it.id == streamingId }
              } else {
                null
              }
            val combined =
              if (currentStreaming != null && mapped.none { it.id == streamingId }) {
                mapped + currentStreaming
              } else {
                mapped
              }
            state.copy(messages = combined)
          }
        }
      }
  }

  fun onInputChanged(newText: String) {
    _uiState.update { it.copy(inputText = newText) }
  }

  fun onSelectSuggestion(prompt: String) {
    _uiState.update { it.copy(inputText = prompt) }
  }

  fun onSelectMode(mode: InputMode) {
    _uiState.update { it.copy(inputMode = mode) }
  }

  fun onToggleModelMenu(open: Boolean) {
    _uiState.update { it.copy(isModelMenuOpen = open) }
  }

  fun onSelectModel(model: AiModelOption) {
    _uiState.update { it.copy(selectedModel = model, isModelMenuOpen = false) }
    persistCurrentSettings()
  }

  fun onToggleAttachmentSheet(open: Boolean) {
    _uiState.update { it.copy(isAttachmentSheetOpen = open) }
  }

  fun onAttachSampleImage() {
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.SampleInlineArtwork).distinct()
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedPhoto(uri: String) {
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.PickedPhotoUri(uri)).take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedPhotos(uris: List<String>) {
    if (uris.isEmpty()) return
    _uiState.update { state ->
      val additions = uris.map { AttachmentPreview.PickedPhotoUri(it) }
      val updated = (state.pendingAttachments + additions).distinct().take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedVideo(uriString: String) {
    val item = buildMediaItem(uriString, MediaAttachmentKind.VIDEO, "video/mp4", "Selected Video")
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.MediaFile(item)).take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedAudio(uriString: String) {
    val item = buildMediaItem(uriString, MediaAttachmentKind.AUDIO, "audio/*", "Selected Audio")
    _uiState.update { state ->
      val updated = (state.pendingAttachments + AttachmentPreview.MediaFile(item)).take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  fun onAttachPickedDocuments(uris: List<String>) {
    if (uris.isEmpty()) return
    val newFiles =
      uris.take(4).map { uri ->
        buildMediaItem(uri, MediaAttachmentKind.DOCUMENT, "application/octet-stream", "Document")
      }
    _uiState.update { state ->
      val updated =
        (state.pendingAttachments + newFiles.map { AttachmentPreview.MediaFile(it) })
          .distinct()
          .take(4)
      state.copy(pendingAttachments = updated, isAttachmentSheetOpen = false)
    }
  }

  private fun buildMediaItem(
    uriString: String,
    kind: MediaAttachmentKind,
    defaultMime: String,
    fallbackLabel: String,
  ): PendingMediaItem {
    val uri = Uri.parse(uriString)
    val resolvedName =
      tempMediaCacheManager?.resolveDisplayName(uri)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { fallbackLabel }
        ?: fallbackLabel
    val resolvedSize = tempMediaCacheManager?.resolveFileSize(uri) ?: 0L
    return PendingMediaItem(
      id = "media_${UUID.randomUUID()}",
      uriString = uriString,
      displayName = securityValidator.sanitizeFilename(resolvedName),
      mimeType = defaultMime,
      byteSize = resolvedSize,
      kind = kind,
    )
  }

  fun onRemovePendingAttachment(attachment: AttachmentPreview) {
    _uiState.update { state ->
      state.copy(pendingAttachments = state.pendingAttachments - attachment)
    }
  }

  fun onClearPendingAttachment() {
    _uiState.update { it.copy(pendingAttachments = emptyList()) }
  }

  fun onBeginEditMessage(message: ChatMessage) {
    if (!message.isUser || _uiState.value.isGenerating) return
    _uiState.update {
      it.copy(
        editingMessageId = message.id,
        inputText = message.text,
        pendingAttachments = message.attachments,
      )
    }
  }

  fun onCancelEditing() {
    _uiState.update {
      it.copy(editingMessageId = null, inputText = "", pendingAttachments = emptyList())
    }
  }

  fun onSendMessage() {
    val state = _uiState.value
    val trimmed = state.inputText.trim()
    val attachments = state.pendingAttachments
    if (trimmed.isEmpty() && attachments.isEmpty()) return

    val editTargetId = state.editingMessageId
    if (editTargetId != null) {
      val editMessage = state.messages.firstOrNull { it.id == editTargetId }
      if (editMessage != null) {
        viewModelScope.launch {
          chatRepository.deleteMessagesFrom(state.activeConversationId, editMessage.timestampMs)
          executePrompt(
            conversationId = state.activeConversationId,
            prompt = trimmed,
            attachments = attachments,
          )
        }
        _uiState.update {
          it.copy(editingMessageId = null, inputText = "", pendingAttachments = emptyList())
        }
        return
      }
    }

    _uiState.update { it.copy(inputText = "", pendingAttachments = emptyList()) }
    viewModelScope.launch {
      executePrompt(
        conversationId = state.activeConversationId,
        prompt = trimmed,
        attachments = attachments,
      )
    }
  }

  fun onRegenerateFromMessage(assistantMessage: ChatMessage) {
    if (_uiState.value.isGenerating) return
    val currentMessages = _uiState.value.messages
    val index = currentMessages.indexOfFirst { it.id == assistantMessage.id }
    if (index <= 0) return

    val precedingUser = currentMessages.subList(0, index).lastOrNull { it.isUser } ?: return
    viewModelScope.launch {
      chatRepository.deleteMessagesFrom(
        conversationId = _uiState.value.activeConversationId,
        fromTimestampMs = assistantMessage.timestampMs,
      )
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = precedingUser.text,
        attachments = precedingUser.attachments,
        skipSavingUserTurn = true,
      )
    }
  }

  fun onStopGeneration() {
    activeGenerationJob?.cancel()
    activeGenerationJob = null
    val streamingId = activeAssistantMessageId
    val streamingMsg = _uiState.value.messages.firstOrNull { it.id == streamingId }
    val partial = streamingMsg?.text.orEmpty()

    _uiState.update { state ->
      val updated =
        state.messages.map { msg ->
          if (msg.id == streamingId) msg.copy(isStreaming = false) else msg
        }
      state.copy(messages = updated, responseState = AiResponseState.Cancelled)
    }

    if (streamingId != null && partial.isNotBlank()) {
      viewModelScope.launch {
        chatRepository.upsertMessage(
          DomainMessage(
            id = streamingId,
            conversationId = _uiState.value.activeConversationId,
            isUser = false,
            text = partial,
            timestampMs = System.currentTimeMillis(),
            modelUsed = _uiState.value.selectedModel.displayName,
            blocks = streamingMsg?.blocks.orEmpty(),
          )
        )
      }
    }
    activeAssistantMessageId = null
  }

  private suspend fun executePrompt(
    conversationId: String,
    prompt: String,
    attachments: List<AttachmentPreview>,
    skipSavingUserTurn: Boolean = false,
  ) {
    activeGenerationJob?.cancel()

    val historyBefore = chatRepository.getMessagesSnapshot(conversationId)
    val nowMs = System.currentTimeMillis()
    val userText = prompt.ifEmpty { "Please analyze the attached file or media." }
    val pendingMedia =
      attachments.mapNotNull { (it as? AttachmentPreview.MediaFile)?.item }

    if (!skipSavingUserTurn) {
      val userDomain =
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = conversationId,
          isUser = true,
          text = userText,
          timestampMs = nowMs,
          imageUris =
            attachments.mapNotNull { (it as? AttachmentPreview.PickedPhotoUri)?.uri },
          isSampleArtwork = attachments.any { it is AttachmentPreview.SampleInlineArtwork },
          attachedMediaSummaries = pendingMedia,
        )
      chatRepository.upsertMessage(userDomain)
    }

    val processedImages = mutableListOf<ProcessedImagePayload>()
    for (attachment in attachments) {
      val result =
        when (attachment) {
          AttachmentPreview.SampleInlineArtwork ->
            imageProcessor.processDrawableResource(R.drawable.img_inline_preview_art_1795023054486)
          is AttachmentPreview.PickedPhotoUri ->
            imageProcessor.processUri(attachment.uri)
          is AttachmentPreview.MediaFile -> null
        }
      result?.getOrNull()?.let { processedImages.add(it) }
    }

    val assistantMsgId = "msg_${UUID.randomUUID()}"
    activeAssistantMessageId = assistantMsgId
    var routedModelName = _uiState.value.selectedModel.displayName
    var routedCategoryLabel: String? = null
    var latestUsage: TokenUsage? = null
    var latestBlocks: List<ResponseBlock> = emptyList()

    val placeholderAssistant =
      ChatMessage(
        id = assistantMsgId,
        isUser = false,
        text = "",
        timestampLabel = formatTime(System.currentTimeMillis()),
        timestampMs = nowMs + 1,
        isStreaming = true,
      )

    _uiState.update { state ->
      state.copy(
        messages = state.messages + placeholderAssistant,
        responseState = AiResponseState.Loading,
      )
    }

    val job =
      viewModelScope.launch {
        try {
          aiOrchestrator
            .processMessage(
              OrchestrationRequest(
                conversationId = conversationId,
                userPrompt = userText,
                historyMessages = historyBefore,
                currentTurnImages = processedImages,
                requestedModelId = _uiState.value.selectedModel.modelId,
                isTaskMode = _uiState.value.inputMode == InputMode.TASK,
                pendingMediaItems = pendingMedia,
              )
            )
            .collect { event ->
              when (event) {
                is OrchestrationEvent.Routed -> {
                  routedModelName = event.decision.selectedModel.displayName
                  routedCategoryLabel = event.decision.category.label
                  _uiState.update { it.copy(activeRoutingReason = event.decision.reason) }
                }
                is OrchestrationEvent.TitleSuggested -> {
                  chatRepository.updateConversationTitle(conversationId, event.title)
                }
                is OrchestrationEvent.BlocksUpdated -> {
                  latestBlocks = event.blocks
                  _uiState.update { ui ->
                    val updated =
                      ui.messages.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(blocks = latestBlocks) else msg
                      }
                    ui.copy(messages = updated)
                  }
                }
                is OrchestrationEvent.StateChanged -> {
                  val stateVal = event.state
                  _uiState.update { ui ->
                    val updatedMessages =
                      when (stateVal) {
                        is AiResponseState.Streaming -> {
                          latestUsage = stateVal.tokenUsage ?: latestUsage
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.partialText,
                                modelUsed = stateVal.modelName,
                                categoryLabel = routedCategoryLabel,
                                tokenUsage = latestUsage,
                                isStreaming = true,
                                blocks = latestBlocks,
                              )
                            } else msg
                          }
                        }
                        is AiResponseState.Completed -> {
                          latestUsage = stateVal.tokenUsage ?: latestUsage
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.fullText,
                                modelUsed = stateVal.modelName,
                                categoryLabel = routedCategoryLabel,
                                tokenUsage = latestUsage,
                                isStreaming = false,
                                blocks = latestBlocks,
                              )
                            } else msg
                          }
                        }
                        is AiResponseState.Error -> {
                          ui.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                              msg.copy(
                                text = stateVal.message,
                                modelUsed = routedModelName,
                                isError = true,
                                isStreaming = false,
                              )
                            } else msg
                          }
                        }
                        else -> ui.messages
                      }
                    ui.copy(messages = updatedMessages, responseState = stateVal)
                  }

                  when (stateVal) {
                    is AiResponseState.Completed -> {
                      val assistantDomain =
                        DomainMessage(
                          id = assistantMsgId,
                          conversationId = conversationId,
                          isUser = false,
                          text = stateVal.fullText,
                          timestampMs = nowMs + 1,
                          modelUsed = stateVal.modelName,
                          categoryLabel = routedCategoryLabel,
                          tokenUsage = stateVal.tokenUsage ?: latestUsage,
                          isError = false,
                          blocks = latestBlocks,
                        )
                      chatRepository.upsertMessage(assistantDomain)
                      activeAssistantMessageId = null
                      val voicePrefs = _uiState.value.voicePreferences
                      if (voicePrefs.autoSpeakResponses) {
                        voiceController.speakResponse(assistantMsgId, stateVal.fullText, voicePrefs)
                      }
                      if (_uiState.value.privacyPerformanceConfig.memoryEnabled && longTermMemoryManager != null) {
                        val updatedHistory = historyBefore + assistantDomain
                        if (updatedHistory.size >= 4) {
                          longTermMemoryManager.summarizeConversationAndPersist(conversationId, updatedHistory)
                        }
                      }
                    }
                    is AiResponseState.Error -> {
                      chatRepository.upsertMessage(
                        DomainMessage(
                          id = assistantMsgId,
                          conversationId = conversationId,
                          isUser = false,
                          text = stateVal.message,
                          timestampMs = nowMs + 1,
                          modelUsed = routedModelName,
                          isError = true,
                        )
                      )
                      activeAssistantMessageId = null
                    }
                    else -> Unit
                  }
                }
              }
            }
        } catch (e: CancellationException) {
          throw e
        }
      }
    activeGenerationJob = job
  }

  // --- Part 4 Actions: Edge Light, Floating Orb, Drawing, Screen Intelligence, Memory, Automation ---

  fun onUpdateEdgeLightConfig(config: EdgeLightConfig) {
    _uiState.update { it.copy(edgeLightConfig = config) }
    persistCurrentSettings()
  }

  fun onUpdateFloatingOrbConfig(config: FloatingOrbConfig) {
    _uiState.update { it.copy(floatingOrbConfig = config) }
    persistCurrentSettings()
  }

  fun onOrbPositionCommitted(xFraction: Float, yFraction: Float) {
    val updated =
      _uiState.value.floatingOrbConfig.copy(
        positionXFraction = xFraction,
        positionYFraction = yFraction,
      )
    onUpdateFloatingOrbConfig(updated)
  }

  fun onHandleOrbQuickAction(
    action: OrbQuickAction,
    onRequestScreenShare: () -> Unit = {},
    onNavigateToControlCenter: () -> Unit = {},
  ) {
    when (action) {
      OrbQuickAction.ASK_MOUSOMI -> _uiState.update { it.copy(inputMode = InputMode.CHAT) }
      OrbQuickAction.START_VOICE -> onToggleVoiceDialog(true)
      OrbQuickAction.ANALYZE_SCREEN -> analyzeLatestScreenFrameOrPrompt(onRequestScreenShare)
      OrbQuickAction.START_SCREEN_SHARE -> onRequestScreenShare()
      OrbQuickAction.DRAW_ON_SCREEN -> _uiState.update { it.copy(isDrawingOverlayOpen = true) }
      OrbQuickAction.QUICK_NOTE -> _uiState.update { it.copy(isQuickNoteDialogOpen = true) }
      OrbQuickAction.OPEN_FULL_ASSISTANT -> onNavigateToControlCenter()
    }
  }

  fun onToggleDrawingOverlay(open: Boolean) {
    _uiState.update { it.copy(isDrawingOverlayOpen = open) }
  }

  fun onToggleQuickNoteDialog(open: Boolean) {
    _uiState.update { it.copy(isQuickNoteDialogOpen = open) }
  }

  fun onAnalyzeAnnotatedRegion(
    box: NormBoundingBox,
    strokes: List<AnnotationStroke>,
  ) {
    _uiState.update { it.copy(isDrawingOverlayOpen = false) }
    val latestFrame = screenShareSessionManager?.getLatestFrame()
    val notesFromLabels =
      strokes.mapNotNull { it.textLabel.takeIf { lbl -> lbl.isNotBlank() } }.joinToString(", ")

    val cropUriString: String? =
      if (latestFrame != null && tempMediaCacheManager != null) {
        val raw = BitmapFactory.decodeFile(latestFrame.filePath)
        val cropped = raw?.let { RegionCropper.cropBitmapToNormBox(it, box) }
        raw?.recycle()
        if (cropped != null) {
          val baos = ByteArrayOutputStream()
          cropped.compress(Bitmap.CompressFormat.JPEG, 85, baos)
          cropped.recycle()
          val savedFile = tempMediaCacheManager.saveTempJpeg(baos.toByteArray(), prefix = "annotated_crop_")
          Uri.fromFile(savedFile).toString()
        } else null
      } else null

    val prompt =
      buildString {
        append("Analyze this annotated screen region")
        if (notesFromLabels.isNotBlank()) {
          append(" (user annotations: $notesFromLabels)")
        }
        append(
          " at normalized area [left=${"%.2f".format(box.left)}, top=${"%.2f".format(box.top)}, right=${"%.2f".format(box.right)}, bottom=${"%.2f".format(box.bottom)}]. Explain clearly what is shown."
        )
      }

    val attachments =
      if (cropUriString != null) listOf(AttachmentPreview.PickedPhotoUri(cropUriString))
      else listOf(AttachmentPreview.SampleInlineArtwork)

    viewModelScope.launch {
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = prompt,
        attachments = attachments,
      )
    }
  }

  fun analyzeLatestScreenFrameOrPrompt(onRequestScreenShare: () -> Unit = {}) {
    val latest = screenShareSessionManager?.getLatestFrame()
    if (latest == null) {
      onRequestScreenShare()
      return
    }
    screenShareSessionManager?.markAnalyzing()
    val uriString = Uri.fromFile(File(latest.filePath)).toString()
    viewModelScope.launch {
      executePrompt(
        conversationId = _uiState.value.activeConversationId,
        prompt = "Analyze what is currently visible on my shared screen and provide concise, helpful insights.",
        attachments = listOf(AttachmentPreview.PickedPhotoUri(uriString)),
      )
      screenShareSessionManager?.markSessionActive()
    }
  }

  fun onMarkScreenShareRequestingConsent() {
    screenShareSessionManager?.markRequestingConsent()
  }

  fun onStopScreenShare() {
    screenShareSessionManager?.stopSession()
  }

  fun onUpdateScreenIntelligenceConfig(config: ScreenIntelligenceConfig) {
    screenShareSessionManager?.updateConfig(config)
    _uiState.update { it.copy(screenIntelligenceConfig = config) }
    persistCurrentSettings()
  }

  fun onUpdateAgentBudgetConfig(config: AgentBudgetConfig) {
    _uiState.update { it.copy(agentBudgetConfig = config) }
    persistCurrentSettings()
  }

  fun onSaveMemoryItem(category: MemoryCategory, content: String, importance: Float = 0.7f) {
    viewModelScope.launch {
      val encrypt = _uiState.value.privacyPerformanceConfig.encryptSensitiveMemory
      val outcome =
        longTermMemoryManager?.saveMemory(
          category = category,
          rawContent = content,
          importance = importance,
          encryptAtRest = encrypt,
        )
      val status =
        when (outcome) {
          is MemoryWriteOutcome.Saved ->
            if (outcome.mergedDuplicate) "Merged with similar existing memory." else "Saved to Memory Center."
          is MemoryWriteOutcome.BlockedSensitive -> outcome.reason
          null -> "Memory manager unavailable."
        }
      _uiState.update { it.copy(memoryStatusMessage = status) }
    }
  }

  fun onDeleteMemoryItem(id: String) {
    viewModelScope.launch { longTermMemoryManager?.deleteMemory(id) }
  }

  fun onClearAllMemories() {
    viewModelScope.launch {
      longTermMemoryManager?.clearAllMemories()
      _uiState.update { it.copy(memoryStatusMessage = "Cleared all stored memories.") }
    }
  }

  fun onUpdateNotificationConfig(config: NotificationIntelligenceConfig) {
    notificationEngine?.updateConfig(config)
    _uiState.update { it.copy(notificationConfig = config) }
    persistCurrentSettings()
  }

  fun onDismissNotificationSummary(id: String) {
    notificationEngine?.dismissSummary(id)
  }

  fun onRunAutomationWorkflow(
    workflowType: AutomationWorkflowType,
    title: String,
    targetArg: String = "",
    triggerSource: String = "Manual",
  ) {
    viewModelScope.launch {
      automationEngine?.createPendingOrExecute(
        workflowType = workflowType,
        title = title,
        targetArg = targetArg,
        triggerSource = triggerSource,
        confirmedByUser = false,
      )
    }
  }

  fun onConfirmAutomationTask(task: AutomationTaskRecord) {
    viewModelScope.launch { automationEngine?.executeConfirmed(task) }
  }

  fun onCancelAutomationTask(task: AutomationTaskRecord) {
    viewModelScope.launch { automationEngine?.cancelPendingTask(task) }
  }

  fun onUpdatePrivacyPerformanceConfig(config: PrivacyAndPerformanceConfig) {
    performanceMonitor?.schedulePeriodicCacheCleanup(config.autoCleanupTempFiles)
    _uiState.update { it.copy(privacyPerformanceConfig = config) }
    persistCurrentSettings()
  }

  // --- Existing Navigation, Voice & Settings Methods ---

  fun onSelectConversation(conversationId: String) {
    activeGenerationJob?.cancel()
    activeAssistantMessageId = null
    _uiState.update {
      it.copy(
        activeConversationId = conversationId,
        responseState = AiResponseState.Idle,
        editingMessageId = null,
        pendingAttachments = emptyList(),
      )
    }
    observeActiveConversationMessages(conversationId)
  }

  fun onDeleteConversation(conversationId: String) {
    viewModelScope.launch {
      chatRepository.deleteConversation(conversationId)
      if (_uiState.value.activeConversationId == conversationId) {
        onNewChat()
      }
    }
  }

  fun onLoadSampleConversation() {
    val convId = "conv_${UUID.randomUUID()}"
    val now = System.currentTimeMillis()
    viewModelScope.launch {
      chatRepository.ensureConversation(convId, "Cutout Phone Wallpaper")
      chatRepository.upsertMessage(
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = convId,
          isUser = true,
          text = "Make a cutout phone wallpaper with glowing midnight blue glass prism vibes",
          timestampMs = now,
          isSampleArtwork = false,
        )
      )
      chatRepository.upsertMessage(
        DomainMessage(
          id = "msg_${UUID.randomUUID()}",
          conversationId = convId,
          isUser = false,
          text =
            "### Prism Cutout Wallpaper Preview\nHere is a sample multimodal preview bubble. Configure your **Gemini** or **Groq** API key (or an HTTPS proxy) in **Settings** to analyze your own photos, videos, audio, documents, or run live research.",
          timestampMs = now + 1,
          isSampleArtwork = true,
          modelUsed = "Gemini 4 Hydrogen",
        )
      )
      onSelectConversation(convId)
    }
  }

  fun onNewChat() {
    activeGenerationJob?.cancel()
    activeAssistantMessageId = null
    val newId = "conv_${UUID.randomUUID()}"
    _uiState.update {
      it.copy(
        activeConversationId = newId,
        messages = emptyList(),
        inputText = "",
        editingMessageId = null,
        pendingAttachments = emptyList(),
        responseState = AiResponseState.Idle,
        activeRoutingReason = null,
      )
    }
    observeActiveConversationMessages(newId)
  }

  fun onToggleUpgradeSheet(open: Boolean) {
    _uiState.update { it.copy(isUpgradeSheetOpen = open) }
  }

  fun onToggleProfileDialog(open: Boolean) {
    _uiState.update { it.copy(isProfileDialogOpen = open) }
  }

  fun onToggleGeminiLive(open: Boolean) {
    if (!open) {
      voiceController.stopListening()
      voiceController.stopSpeaking()
    }
    _uiState.update { it.copy(isGeminiLiveOpen = open) }
  }

  fun onToggleVoiceDialog(open: Boolean) {
    if (!open) {
      voiceController.stopListening()
    }
    _uiState.update { it.copy(isVoiceDialogOpen = open) }
  }

  fun onAudioPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(hasAudioPermission = granted, isVoiceDialogOpen = true) }
    if (granted) {
      voiceController.interruptSpeakingAndListen(_uiState.value.voicePreferences.preferredLanguage)
    }
  }

  fun onStartVoiceListening() {
    if (_uiState.value.privacyPerformanceConfig.allowVoiceInterruption) {
      voiceController.interruptSpeakingAndListen(_uiState.value.voicePreferences.preferredLanguage)
    } else {
      voiceController.startListening(_uiState.value.voicePreferences.preferredLanguage)
    }
  }

  fun onStopVoiceListening() {
    voiceController.stopListening()
  }

  fun onSpeakMessage(message: ChatMessage) {
    voiceController.speakResponse(
      messageId = message.id,
      text = message.text,
      preferences = _uiState.value.voicePreferences,
    )
  }

  fun onStopSpeaking() {
    voiceController.stopSpeaking()
  }

  fun onUpdateThemeMode(mode: AppThemeMode) {
    _uiState.update { it.copy(themeMode = mode) }
    persistCurrentSettings()
  }

  fun onUpdateGeminiApiKey(key: String) {
    _uiState.update { it.copy(geminiApiKey = key, settingsErrorMessage = null) }
  }

  fun onUpdateGroqApiKey(key: String) {
    _uiState.update { it.copy(groqApiKey = key, settingsErrorMessage = null) }
  }

  fun onUpdateConnectionMode(mode: ConnectionMode) {
    _uiState.update { it.copy(connectionMode = mode, settingsErrorMessage = null) }
  }

  fun onUpdateProxyBaseUrl(url: String) {
    _uiState.update { it.copy(proxyBaseUrl = url, settingsErrorMessage = null) }
  }

  fun onUpdateHydrogenBackingModelId(id: String) {
    _uiState.update { it.copy(hydrogenBackingModelId = id, settingsErrorMessage = null) }
  }

  fun onUpdateGroqReasoningModelId(id: String) {
    _uiState.update { it.copy(groqReasoningModelId = id, settingsErrorMessage = null) }
  }

  fun onUpdateExternalSearchUrl(url: String) {
    _uiState.update { it.copy(externalSearchUrl = url, settingsErrorMessage = null) }
  }

  fun onUpdateExternalSearchApiKey(key: String) {
    _uiState.update { it.copy(externalSearchApiKey = key, settingsErrorMessage = null) }
  }

  fun onUpdateAnalyticsSandboxEnabled(enabled: Boolean) {
    _uiState.update { it.copy(analyticsSandboxEnabled = enabled) }
  }

  fun onClearTempMediaCache() {
    tempMediaCacheManager?.cleanupAllTempFiles()
  }

  fun onUpdatePreferredLanguage(language: ResponseLanguage) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(preferredLanguage = language))
    }
  }

  fun onUpdateAutoSpeak(enabled: Boolean) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(autoSpeakResponses = enabled))
    }
  }

  fun onUpdateSpeechRate(rate: Float) {
    _uiState.update {
      it.copy(voicePreferences = it.voicePreferences.copy(speechRate = rate))
    }
  }

  fun onSaveSettings(): Boolean {
    val state = _uiState.value
    if (state.connectionMode == ConnectionMode.BACKEND_PROXY) {
      val validated = securityValidator.validateHttpsUrl(state.proxyBaseUrl)
      if (validated.isFailure) {
        _uiState.update {
          it.copy(
            settingsErrorMessage =
              validated.exceptionOrNull()?.message ?: "Proxy URL must use HTTPS."
          )
        }
        return false
      }
    }
    if (state.externalSearchUrl.isNotBlank()) {
      val validatedSearch = securityValidator.validateHttpsUrl(state.externalSearchUrl)
      if (validatedSearch.isFailure) {
        _uiState.update {
          it.copy(
            settingsErrorMessage =
              validatedSearch.exceptionOrNull()?.message ?: "External Search URL must use HTTPS."
          )
        }
        return false
      }
    }
    persistCurrentSettings()
    return true
  }

  private fun persistCurrentSettings() {
    val state = _uiState.value
    viewModelScope.launch {
      settingsRepository.saveSettings(
        AppSettings(
          connectionMode = state.connectionMode,
          proxyBaseUrl = state.proxyBaseUrl,
          geminiApiKey = state.geminiApiKey,
          groqApiKey = state.groqApiKey,
          themeModeName = state.themeMode.name,
          voicePreferences = state.voicePreferences,
          selectedModelOptionName = state.selectedModel.name,
          hydrogenBackingModelId = state.hydrogenBackingModelId,
          groqReasoningModelId = state.groqReasoningModelId,
          externalSearchUrl = state.externalSearchUrl,
          externalSearchApiKey = state.externalSearchApiKey,
          analyticsSandboxEnabled = state.analyticsSandboxEnabled,
          edgeLightConfig = state.edgeLightConfig,
          floatingOrbConfig = state.floatingOrbConfig,
          screenIntelligenceConfig = state.screenIntelligenceConfig,
          agentBudgetConfig = state.agentBudgetConfig,
          notificationConfig = state.notificationConfig,
          privacyPerformanceConfig = state.privacyPerformanceConfig,
        )
      )
    }
  }

  private fun toUiMessage(domain: DomainMessage): ChatMessage {
    val attachments = buildList {
      if (domain.isSampleArtwork) add(AttachmentPreview.SampleInlineArtwork)
      domain.imageUris.forEach { add(AttachmentPreview.PickedPhotoUri(it)) }
      domain.attachedMediaSummaries.forEach { add(AttachmentPreview.MediaFile(it)) }
    }
    return ChatMessage(
      id = domain.id,
      isUser = domain.isUser,
      text = domain.text,
      timestampLabel = formatTime(domain.timestampMs),
      timestampMs = domain.timestampMs,
      attachments = attachments,
      modelUsed = domain.modelUsed,
      categoryLabel = domain.categoryLabel,
      tokenUsage = domain.tokenUsage,
      isError = domain.isError,
      isStreaming = false,
      blocks = domain.blocks,
    )
  }

  private fun formatTime(timestampMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
  }

  override fun onCleared() {
    super.onCleared()
    voiceController.release()
    tempMediaCacheManager?.cleanupAllTempFiles()
  }
}
