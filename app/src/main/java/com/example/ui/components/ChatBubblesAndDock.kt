package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.MediaAttachmentKind
import com.example.ui.model.AiModelOption
import com.example.ui.model.AttachmentPreview
import com.example.ui.model.ChatMessage
import com.example.ui.model.InputMode
import com.example.ui.model.SuggestionIconType
import com.example.ui.model.SuggestionItem
import com.example.ui.theme.DisplayFontFamily
import com.example.ui.theme.LocalMousomiColors

@Composable
fun GeminiTopBar(
  selectedModel: AiModelOption,
  onOpenDrawer: () -> Unit,
  onOpenModelSelector: () -> Unit,
  onNewChat: () -> Unit,
  onOpenUpgrade: () -> Unit,
  onOpenProfile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = onOpenDrawer,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("hamburger_menu_button"),
    ) {
      Icon(
        imageVector = Icons.Rounded.Menu,
        contentDescription = stringResource(R.string.cd_open_drawer),
        tint = onBg,
        modifier = Modifier.size(25.dp),
      )
    }

    Spacer(modifier = Modifier.width(2.dp))

    Row(
      modifier =
        Modifier
          .clip(RoundedCornerShape(20.dp))
          .clickable(onClick = onOpenModelSelector)
          .padding(horizontal = 10.dp, vertical = 10.dp)
          .testTag("model_selector_button"),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.top_bar_gemini),
        color = onBg,
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = selectedModel.shortLabel,
        color = colors.mutedText,
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(modifier = Modifier.width(2.dp))
      Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = stringResource(R.string.cd_model_selector),
        tint = colors.mutedText,
        modifier = Modifier.size(18.dp),
      )
    }

    Spacer(modifier = Modifier.weight(1f))

    IconButton(
      onClick = onNewChat,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("new_chat_button"),
    ) {
      GeminiNewChatPencilIcon(tint = onBg, size = 22.dp)
    }

    Spacer(modifier = Modifier.width(2.dp))

    Surface(
      onClick = onOpenUpgrade,
      shape = CircleShape,
      color = colors.upgradeButtonBlue,
      modifier = Modifier.height(36.dp).testTag("upgrade_button"),
    ) {
      Box(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(R.string.top_bar_upgrade),
          color = Color.White,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Medium,
          fontSize = 14.sp,
        )
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    Box(
      modifier =
        Modifier
          .size(48.dp)
          .clip(CircleShape)
          .clickable(onClick = onOpenProfile)
          .testTag("profile_avatar_button"),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.avatarOrange),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "C",
          color = Color.White,
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 16.sp,
        )
      }
    }
  }
}

@Composable
fun GeminiWelcomeCanvas(
  suggestions: List<SuggestionItem>,
  onSuggestionClick: (SuggestionItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground

  BoxWithConstraints(
    modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
  ) {
    val isCompactHeight = maxHeight < 420.dp
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = maxHeight)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.weight(1f))

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = if (isCompactHeight) 8.dp else 20.dp),
      ) {
        GeminiFourPointSparkle(size = if (isCompactHeight) 36.dp else 46.dp)

        Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 20.dp))

        Text(
          text = stringResource(R.string.greeting_line_one),
          color = onBg,
          fontFamily = DisplayFontFamily,
          fontWeight = FontWeight.Normal,
          fontSize = if (isCompactHeight) 24.sp else 28.sp,
          lineHeight = if (isCompactHeight) 30.sp else 34.sp,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = stringResource(R.string.greeting_line_two),
          style =
            TextStyle(
              brush = Brush.linearGradient(colors = colors.hackerGradient),
              fontFamily = DisplayFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = if (isCompactHeight) 26.sp else 30.sp,
              lineHeight = if (isCompactHeight) 32.sp else 36.sp,
              letterSpacing = 0.8.sp,
              textAlign = TextAlign.Center,
            ),
        )
      }

      Spacer(modifier = Modifier.weight(1f))

      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = if (isCompactHeight) 8.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 2.dp else 6.dp),
      ) {
        suggestions.forEach { item ->
          SuggestionRow(
            item = item,
            onClick = { onSuggestionClick(item) },
          )
        }
      }
    }
  }
}

@Composable
private fun SuggestionRow(
  item: SuggestionItem,
  onClick: () -> Unit,
) {
  val onBg = MaterialTheme.colorScheme.onBackground
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 10.dp)
        .testTag("suggestion_row_${item.id}"),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    when (item.iconType) {
      SuggestionIconType.PENCIL ->
        Icon(
          imageVector = Icons.Outlined.Edit,
          contentDescription = null,
          tint = onBg.copy(alpha = 0.9f),
          modifier = Modifier.size(20.dp),
        )
      SuggestionIconType.VIDEO_CAPTIONS ->
        GeminiCaptionsSparkleIcon(
          tint = onBg.copy(alpha = 0.9f),
          size = 20.dp,
        )
      SuggestionIconType.WALLPAPER_SPARKLES ->
        GeminiMultiSparkleIcon(
          tint = onBg.copy(alpha = 0.9f),
          size = 20.dp,
        )
    }

    Spacer(modifier = Modifier.width(18.dp))

    Text(
      text = item.promptText,
      color = onBg.copy(alpha = 0.94f),
      style = MaterialTheme.typography.bodyLarge,
      fontSize = 15.5.sp,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun ChatMessagesList(
  messages: List<ChatMessage>,
  responseState: AiResponseState,
  voiceState: VoiceState,
  onOpenSettings: () -> Unit,
  onEditUserMessage: (ChatMessage) -> Unit,
  onRegenerateMessage: (ChatMessage) -> Unit,
  onSpeakMessage: (ChatMessage) -> Unit,
  onStopSpeaking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val lastTextLen = messages.lastOrNull()?.text?.length ?: 0

  LaunchedEffect(messages.size, lastTextLen) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxSize().testTag("chat_messages_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    items(items = messages, key = { it.id }) { message ->
      if (message.isUser) {
        UserChatBubble(
          message = message,
          onEdit = { onEditUserMessage(message) },
        )
      } else {
        val isLast = message.id == messages.lastOrNull()?.id
        AssistantChatBubble(
          message = message,
          responseState = if (isLast) responseState else AiResponseState.Idle,
          isSpeakingThis = voiceState is VoiceState.Speaking && voiceState.messageId == message.id,
          onOpenSettings = onOpenSettings,
          onRegenerate = { onRegenerateMessage(message) },
          onSpeakToggle = {
            if (voiceState is VoiceState.Speaking && voiceState.messageId == message.id) {
              onStopSpeaking()
            } else {
              onSpeakMessage(message)
            }
          },
        )
      }
    }
  }
}

@Composable
private fun UserChatBubble(
  message: ChatMessage,
  onEdit: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.End,
  ) {
    Surface(
      shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp),
      color = colors.userBubbleContainer,
      modifier = Modifier.widthIn(max = 316.dp).testTag("user_chat_bubble_${message.id}"),
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (message.attachments.isNotEmpty()) {
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp),
          ) {
            message.attachments.forEach { attachment ->
              when (attachment) {
                is AttachmentPreview.MediaFile -> MediaAttachmentBubbleChip(attachment = attachment)
                else ->
                  InlineImagePreviewCard(
                    attachment = attachment,
                    badgeLabel = "Attached Image",
                  )
              }
            }
          }
        }
        Text(
          text = message.text,
          color = onBg,
          style = MaterialTheme.typography.bodyLarge,
        )
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = message.timestampLabel,
            color = colors.mutedText,
            style = MaterialTheme.typography.labelSmall,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "Edit message",
            tint = colors.mutedText,
            modifier =
              Modifier
                .size(16.dp)
                .clip(CircleShape)
                .clickable(onClick = onEdit),
          )
        }
      }
    }
  }
}

@Composable
private fun AssistantChatBubble(
  message: ChatMessage,
  responseState: AiResponseState,
  isSpeakingThis: Boolean,
  onOpenSettings: () -> Unit,
  onRegenerate: () -> Unit,
  onSpeakToggle: () -> Unit,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground
  @Suppress("DEPRECATION")
  val clipboard = LocalClipboardManager.current

  Row(
    modifier = Modifier.fillMaxWidth().testTag("assistant_chat_bubble_${message.id}"),
    verticalAlignment = Alignment.Top,
  ) {
    Box(
      modifier = Modifier.padding(top = 4.dp),
      contentAlignment = Alignment.Center,
    ) {
      GeminiFourPointSparkle(size = 24.dp, animated = message.isStreaming)
    }

    Spacer(modifier = Modifier.width(12.dp))

    Surface(
      shape = RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
      color = colors.assistantBubbleContainer.copy(alpha = if (colors.isDark) 0.82f else 0.95f),
      border =
        BorderStroke(
          1.dp,
          if (message.isError) Color(0xFFEF4444).copy(alpha = 0.55f)
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
      modifier = Modifier.weight(1f),
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        if (message.isStreaming && message.text.isBlank()) {
          val statusLabel =
            when (responseState) {
              is AiResponseState.Thinking -> responseState.detail
              is AiResponseState.Searching -> responseState.detail
              is AiResponseState.Generating -> "Generating with ${responseState.modelName}…"
              AiResponseState.Loading -> "Preparing request…"
              else -> "Thinking…"
            }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = statusLabel,
              color = colors.mutedText,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else if (message.text.isNotBlank()) {
          MarkdownMessageContent(
            markdown = message.text,
            textColor = if (message.isError) Color(0xFFFCA5A5) else onBg,
          )
        }

        if (message.blocks.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            message.blocks.forEach { block ->
              ResponseBlockRenderer(block = block)
            }
          }
        }

        if (message.attachments.isNotEmpty()) {
          Spacer(modifier = Modifier.height(12.dp))
          message.attachments.forEach { attachment ->
            InlineImagePreviewCard(
              attachment = attachment,
              badgeLabel = "Inline Image Preview",
            )
          }
        }

        if (!message.isStreaming) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              if (message.text.isNotBlank()) {
                IconButton(
                  onClick = { clipboard.setText(AnnotatedString(message.text)) },
                  modifier = Modifier.size(34.dp),
                ) {
                  Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy response",
                    tint = colors.mutedText,
                    modifier = Modifier.size(16.dp),
                  )
                }
                IconButton(
                  onClick = onSpeakToggle,
                  modifier = Modifier.size(34.dp),
                ) {
                  Icon(
                    imageVector =
                      if (isSpeakingThis) Icons.Outlined.StopCircle
                      else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (isSpeakingThis) "Stop speaking" else "Read aloud",
                    tint = if (isSpeakingThis) MaterialTheme.colorScheme.primary else colors.mutedText,
                    modifier = Modifier.size(17.dp),
                  )
                }
              }
              IconButton(
                onClick = onRegenerate,
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Refresh,
                  contentDescription = if (message.isError) "Retry" else "Regenerate response",
                  tint = colors.mutedText,
                  modifier = Modifier.size(17.dp),
                )
              }
              if (message.isError) {
                Surface(
                  onClick = onOpenSettings,
                  shape = RoundedCornerShape(10.dp),
                  color = colors.activeChipContainer,
                ) {
                  Text(
                    text = "Settings",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  )
                }
              }
            }

            val metaLabel =
              buildString {
                message.modelUsed?.let { append(it) }
                message.tokenUsage?.takeIf { it.totalTokens > 0 }?.let {
                  if (isNotEmpty()) append(" • ")
                  append("${it.totalTokens} tok")
                }
              }
            if (metaLabel.isNotBlank()) {
              Text(
                text = metaLabel,
                color = colors.mutedText,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun MediaAttachmentBubbleChip(
  attachment: AttachmentPreview.MediaFile,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val icon =
    when (attachment.item.kind) {
      MediaAttachmentKind.VIDEO -> Icons.Outlined.VideoLibrary
      MediaAttachmentKind.AUDIO -> Icons.Outlined.AudioFile
      MediaAttachmentKind.DOCUMENT -> Icons.Outlined.Description
      MediaAttachmentKind.PHOTO -> Icons.Outlined.Image
    }
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = colors.activeChipContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth().testTag("media_attachment_bubble"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = attachment.item.displayName,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val sizeKb = (attachment.item.byteSize / 1024L).coerceAtLeast(1L)
        Text(
          text = "${attachment.item.kind.name} • ${sizeKb} KB",
          color = colors.mutedText,
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}

@Composable
fun InlineImagePreviewCard(
  attachment: AttachmentPreview,
  badgeLabel: String,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = modifier.fillMaxWidth().testTag("inline_image_preview_bubble"),
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
      when (attachment) {
        AttachmentPreview.SampleInlineArtwork ->
          Image(
            painter = painterResource(id = R.drawable.img_inline_preview_art_1795023054486),
            contentDescription = stringResource(R.string.cd_inline_image_preview),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        is AttachmentPreview.PickedPhotoUri ->
          AsyncImage(
            model = attachment.uri,
            contentDescription = stringResource(R.string.cd_inline_image_preview),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        is AttachmentPreview.MediaFile -> Unit
      }

      Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.62f),
        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = badgeLabel,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }

      if (colors.isDark) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(28.dp)
              .align(Alignment.TopCenter)
              .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))
              )
        )
      }
    }
  }
}

@Composable
fun GeminiBottomDock(
  inputMode: InputMode,
  inputText: String,
  pendingAttachments: List<AttachmentPreview>,
  isGenerating: Boolean,
  isEditingMessage: Boolean,
  onSelectMode: (InputMode) -> Unit,
  onInputChanged: (String) -> Unit,
  onOpenAttachments: () -> Unit,
  onRemoveAttachment: (AttachmentPreview) -> Unit,
  onCancelEditing: () -> Unit,
  onVoiceClick: () -> Unit,
  onLiveClick: () -> Unit,
  onSendClick: () -> Unit,
  onStopGeneration: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground
  val showSendButton = inputText.isNotBlank() || pendingAttachments.isNotEmpty()

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
        .padding(bottom = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (isEditingMessage) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.activeChipContainer,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Editing message",
            color = onBg,
            style = MaterialTheme.typography.labelLarge,
          )
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cancel editing",
            tint = colors.mutedText,
            modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onCancelEditing),
          )
        }
      }
    }

    if (pendingAttachments.isNotEmpty()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pendingAttachments.forEachIndexed { idx, attachment ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.activeChipContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              val chipIcon =
                when (attachment) {
                  is AttachmentPreview.MediaFile ->
                    when (attachment.item.kind) {
                      MediaAttachmentKind.VIDEO -> Icons.Outlined.VideoLibrary
                      MediaAttachmentKind.AUDIO -> Icons.Outlined.AudioFile
                      MediaAttachmentKind.DOCUMENT -> Icons.Outlined.Description
                      MediaAttachmentKind.PHOTO -> Icons.Outlined.Image
                    }
                  else -> Icons.Outlined.Image
                }
              Icon(
                imageVector = chipIcon,
                contentDescription = null,
                tint = onBg,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text =
                  when (attachment) {
                    AttachmentPreview.SampleInlineArtwork -> "Sample Art"
                    is AttachmentPreview.PickedPhotoUri -> "Photo ${idx + 1}"
                    is AttachmentPreview.MediaFile -> attachment.item.displayName.take(18)
                  },
                color = onBg,
                style = MaterialTheme.typography.labelLarge,
              )
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove attachment",
                tint = colors.mutedText,
                modifier =
                  Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onRemoveAttachment(attachment) },
              )
            }
          }
        }
      }
    }

    Row(
      modifier = Modifier.padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ModeSelectorPill(
        label = stringResource(R.string.mode_chat),
        selected = inputMode == InputMode.CHAT,
        onClick = { onSelectMode(InputMode.CHAT) },
        testTag = "mode_chat_button",
      )
      ModeSelectorPill(
        label = stringResource(R.string.mode_task),
        selected = inputMode == InputMode.TASK,
        onClick = { onSelectMode(InputMode.TASK) },
        testTag = "mode_task_button",
      )
    }

    Surface(
      shape = RoundedCornerShape(28.dp),
      color = colors.inputPillContainer,
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 640.dp)
          .padding(horizontal = 14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onOpenAttachments,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("attachment_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.cd_add_attachment),
            tint = onBg.copy(alpha = 0.88f),
            modifier = Modifier.size(25.dp),
          )
        }

        Box(
          modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 10.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          if (inputText.isEmpty()) {
            Text(
              text =
                if (inputMode == InputMode.CHAT) {
                  stringResource(R.string.input_placeholder_chat)
                } else {
                  stringResource(R.string.input_placeholder_task)
                },
              color = colors.mutedText,
              style = MaterialTheme.typography.bodyLarge,
              fontSize = 16.5.sp,
            )
          }

          BasicTextField(
            value = inputText,
            onValueChange = onInputChanged,
            textStyle =
              MaterialTheme.typography.bodyLarge.copy(
                color = onBg,
                fontSize = 16.5.sp,
              ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = 4,
            keyboardOptions =
              KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
              ),
            keyboardActions = KeyboardActions(onSend = { if (!isGenerating) onSendClick() }),
            modifier = Modifier.fillMaxWidth().testTag("chat_input_field"),
          )
        }

        IconButton(
          onClick = onVoiceClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_input_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = stringResource(R.string.cd_voice_input),
            tint = onBg.copy(alpha = 0.88f),
            modifier = Modifier.size(23.dp),
          )
        }

        Spacer(modifier = Modifier.width(2.dp))

        AnimatedContent(
          targetState =
            when {
              isGenerating -> 2
              showSendButton -> 1
              else -> 0
            },
          transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith
              (fadeOut() + scaleOut(targetScale = 0.85f))
          },
          label = "action_circle_mode",
        ) { mode ->
          when (mode) {
            2 ->
              Surface(
                onClick = onStopGeneration,
                shape = CircleShape,
                color = Color(0xFFDC2626),
                modifier = Modifier.size(44.dp).testTag("stop_generation_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = "Stop generation",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                  )
                }
              }
            1 ->
              Surface(
                onClick = onSendClick,
                shape = CircleShape,
                color = colors.liveButtonBlue,
                modifier = Modifier.size(44.dp).testTag("send_message_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.cd_send_message),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            else ->
              Surface(
                onClick = onLiveClick,
                shape = CircleShape,
                color = colors.liveButtonBlue,
                modifier = Modifier.size(44.dp).testTag("gemini_live_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  GeminiLiveWaveformIcon(tint = Color.White, size = 21.dp)
                }
              }
          }
        }
      }
    }
  }
}

@Composable
private fun ModeSelectorPill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val colors = LocalMousomiColors.current
  val onBg = MaterialTheme.colorScheme.onBackground

  Box(
    modifier =
      Modifier
        .heightIn(min = 38.dp)
        .clip(CircleShape)
        .background(if (selected) colors.activeChipContainer else Color.Transparent)
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 8.dp)
        .testTag(testTag),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = if (selected) onBg else colors.mutedText,
      style = MaterialTheme.typography.labelLarge,
      fontSize = 15.sp,
      fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
    )
  }
}
