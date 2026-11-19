package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.overlay.OrbPositionHelper
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.FloatingOrbConfig
import com.example.domain.model.OrbQuickAction
import kotlin.math.roundToInt

/**
 * Draggable in-app Floating AI Orb with edge snapping, animated halo synced to AI/Voice state,
 * double-tap Voice shortcut, and single-tap compact 7-action quick launcher.
 */
@Composable
fun InAppFloatingOrbOverlay(
  config: FloatingOrbConfig,
  visualState: EdgeLightVisualState,
  onPositionCommitted: (xFraction: Float, yFraction: Float) -> Unit,
  onQuickAction: (OrbQuickAction) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!config.enabled || !config.inAppPreviewVisible) return

  var menuOpen by remember { mutableStateOf(false) }
  var isDragging by remember { mutableStateOf(false) }
  val density = LocalDensity.current

  val transition = rememberInfiniteTransition(label = "orb_halo")
  val haloScale by
    transition.animateFloat(
      initialValue = 0.96f,
      targetValue = if (visualState == EdgeLightVisualState.IDLE) 1.04f else 1.18f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(
            durationMillis = if (visualState == EdgeLightVisualState.IDLE) 2000 else 800,
            easing = FastOutSlowInEasing,
          ),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "halo_scale",
    )

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val maxW = with(density) { maxWidth.toPx() }.coerceAtLeast(100f)
    val maxH = with(density) { maxHeight.toPx() }.coerceAtLeast(100f)
    val orbPx = with(density) { config.sizeDp.dp.toPx() }.coerceIn(64f, 220f)
    val usableW = (maxW - orbPx).coerceAtLeast(1f)
    val usableH = (maxH - orbPx).coerceAtLeast(1f)

    var dragX by remember(config.positionXFraction, usableW) {
      mutableFloatStateOf(config.positionXFraction.coerceIn(0f, 1f) * usableW)
    }
    var dragY by remember(config.positionYFraction, usableH) {
      mutableFloatStateOf(config.positionYFraction.coerceIn(0.08f, 0.88f) * usableH)
    }

    val haloColors =
      when (visualState) {
        EdgeLightVisualState.LISTENING -> listOf(Color(0xFF0EA5E9), Color(0xFF38BDF8))
        EdgeLightVisualState.THINKING -> listOf(Color(0xFF4F46E5), Color(0xFFA855F7))
        EdgeLightVisualState.SEARCHING, EdgeLightVisualState.RESEARCHING ->
          listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))
        EdgeLightVisualState.GENERATING, EdgeLightVisualState.SPEAKING ->
          listOf(Color(0xFF3B82F6), Color(0xFFEC4899))
        EdgeLightVisualState.ERROR -> listOf(Color(0xFFEF4444), Color(0xFFF97316))
        else -> listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
      }

    AnimatedVisibility(
      visible = menuOpen,
      enter = fadeIn() + scaleIn(initialScale = 0.9f),
      exit = fadeOut() + scaleOut(targetScale = 0.9f),
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
    ) {
      FloatingOrbQuickPanel(
        onSelectAction = { action ->
          menuOpen = false
          onQuickAction(action)
        },
        onDismiss = { menuOpen = false },
      )
    }

    Box(
      modifier =
        Modifier
          .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
          .size(config.sizeDp.dp)
          .alpha(config.opacity.coerceIn(0.35f, 1.0f))
          .pointerInput(config.snapToEdge, config.doubleTapForVoice, usableW, usableH) {
            detectDragGestures(
              onDragStart = { isDragging = true },
              onDragEnd = {
                isDragging = false
                val rawXFrac = (dragX / usableW).coerceIn(0f, 1f)
                val snappedXFrac = OrbPositionHelper.computeSnappedXFraction(rawXFrac, config.snapToEdge)
                val clampedYFrac = OrbPositionHelper.clampFraction(dragY / usableH, 0.08f, 0.88f)
                dragX = snappedXFrac * usableW
                dragY = clampedYFrac * usableH
                onPositionCommitted(snappedXFrac, clampedYFrac)
              },
              onDragCancel = { isDragging = false },
              onDrag = { change, dragAmount ->
                change.consume()
                dragX = (dragX + dragAmount.x).coerceIn(0f, usableW)
                dragY = (dragY + dragAmount.y).coerceIn(0.08f * usableH, 0.88f * usableH)
              },
            )
          }
          .pointerInput(config.doubleTapForVoice) {
            detectTapGestures(
              onTap = { menuOpen = !menuOpen },
              onDoubleTap = {
                if (config.doubleTapForVoice) {
                  menuOpen = false
                  onQuickAction(OrbQuickAction.START_VOICE)
                }
              },
            )
          }
          .testTag("floating_ai_orb"),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .scale(if (isDragging) 1.15f else haloScale)
            .clip(CircleShape)
            .background(Brush.sweepGradient(haloColors + haloColors.first()))
      )
      Surface(
        shape = CircleShape,
        color = Color(0xFF0A0F1F),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.25f)),
        shadowElevation = 10.dp,
        modifier = Modifier.size((config.sizeDp - 8f).coerceAtLeast(36f).dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          GeminiFourPointSparkle(
            size = (config.sizeDp * 0.45f).coerceAtLeast(20f).dp,
            animated = visualState != EdgeLightVisualState.IDLE,
          )
        }
      }
    }
  }
}

@Composable
private fun FloatingOrbQuickPanel(
  onSelectAction: (OrbQuickAction) -> Unit,
  onDismiss: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = Color(0xFF0D1324).copy(alpha = 0.96f),
    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
    shadowElevation = 18.dp,
    modifier = Modifier.fillMaxWidth().testTag("floating_orb_quick_panel"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          GeminiFourPointSparkle(size = 20.dp, animated = true)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Mousomi Quick Actions",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Rounded.Close, contentDescription = "Close quick panel", tint = Color.White.copy(alpha = 0.7f))
        }
      }

      QuickActionRow(Icons.Outlined.AutoAwesome, OrbQuickAction.ASK_MOUSOMI, onSelectAction)
      QuickActionRow(Icons.Outlined.Mic, OrbQuickAction.START_VOICE, onSelectAction)
      QuickActionRow(Icons.Outlined.Screenshot, OrbQuickAction.ANALYZE_SCREEN, onSelectAction)
      QuickActionRow(Icons.AutoMirrored.Outlined.ScreenShare, OrbQuickAction.START_SCREEN_SHARE, onSelectAction)
      QuickActionRow(Icons.Outlined.Brush, OrbQuickAction.DRAW_ON_SCREEN, onSelectAction)
      QuickActionRow(Icons.AutoMirrored.Outlined.NoteAdd, OrbQuickAction.QUICK_NOTE, onSelectAction)
      QuickActionRow(Icons.Outlined.Fullscreen, OrbQuickAction.OPEN_FULL_ASSISTANT, onSelectAction)
    }
  }
}

@Composable
private fun QuickActionRow(
  icon: ImageVector,
  action: OrbQuickAction,
  onSelectAction: (OrbQuickAction) -> Unit,
) {
  Surface(
    onClick = { onSelectAction(action) },
    shape = RoundedCornerShape(14.dp),
    color = Color.White.copy(alpha = 0.08f),
    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("orb_action_${action.name}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = action.label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}
