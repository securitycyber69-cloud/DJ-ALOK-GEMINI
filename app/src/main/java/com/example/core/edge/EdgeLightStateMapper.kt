package com.example.core.edge

import com.example.core.voice.VoiceState
import com.example.domain.model.AiResponseState
import com.example.domain.model.EdgeLightConfig
import com.example.domain.model.EdgeLightPreset
import com.example.domain.model.EdgeLightVisualState
import com.example.domain.model.ScreenShareStatus

data class EdgeLightRenderParams(
  val isVisible: Boolean,
  val visualState: EdgeLightVisualState,
  val effectivePreset: EdgeLightPreset,
  val brightness: Float,
  val speedMultiplier: Float,
  val thicknessDp: Float,
  val cornerRadiusDp: Float,
  val colorArgs: List<Long>,
  val glowLayerCount: Int,
  val voiceReactiveBoost: Float,
  val breathingOnly: Boolean,
)

object EdgeLightStateMapper {

  fun isWithinSchedule(
    config: EdgeLightConfig,
    currentHour: Int,
  ): Boolean {
    if (!config.scheduleEnabled) return true
    val start = config.scheduleStartHour.coerceIn(0, 23)
    val end = config.scheduleEndHour.coerceIn(0, 23)
    val hour = currentHour.coerceIn(0, 23)
    return if (start <= end) {
      hour in start..end
    } else {
      hour >= start || hour <= end
    }
  }

  fun resolve(
    config: EdgeLightConfig,
    responseState: AiResponseState,
    voiceState: VoiceState,
    voiceRmsNormalized: Float,
    screenShareStatus: ScreenShareStatus,
    notificationAlertActive: Boolean,
    isSystemPowerSave: Boolean,
    currentHour: Int,
    forcePreview: Boolean = false,
  ): EdgeLightRenderParams {
    val visualState =
      when {
        voiceState is VoiceState.Listening -> EdgeLightVisualState.LISTENING
        voiceState is VoiceState.Speaking -> EdgeLightVisualState.SPEAKING
        responseState is AiResponseState.Searching -> {
          if (responseState.detail.contains("Research", ignoreCase = true)) {
            EdgeLightVisualState.RESEARCHING
          } else {
            EdgeLightVisualState.SEARCHING
          }
        }
        responseState is AiResponseState.Thinking || responseState is AiResponseState.Loading ->
          EdgeLightVisualState.THINKING
        responseState is AiResponseState.Generating || responseState is AiResponseState.Streaming ->
          EdgeLightVisualState.GENERATING
        responseState is AiResponseState.Completed -> EdgeLightVisualState.COMPLETED
        responseState is AiResponseState.Error -> EdgeLightVisualState.ERROR
        screenShareStatus == ScreenShareStatus.ACTIVE ||
          screenShareStatus == ScreenShareStatus.ANALYZING -> EdgeLightVisualState.GENERATING
        else -> EdgeLightVisualState.IDLE
      }

    val scheduled = isWithinSchedule(config, currentHour)
    val shouldShow =
      forcePreview ||
        notificationAlertActive ||
        (config.enabled &&
          scheduled &&
          (visualState != EdgeLightVisualState.IDLE || config.preset != EdgeLightPreset.GEMINI_FLOW || config.brightness > 0.1f))

    val effectivePreset =
      when {
        forcePreview -> config.preset
        notificationAlertActive -> EdgeLightPreset.NOTIFICATION_ALERT
        visualState == EdgeLightVisualState.LISTENING -> EdgeLightPreset.VOICE_LISTENING
        visualState == EdgeLightVisualState.SEARCHING -> EdgeLightPreset.LIVE_SEARCH
        visualState == EdgeLightVisualState.RESEARCHING -> EdgeLightPreset.DEEP_RESEARCH
        visualState == EdgeLightVisualState.THINKING -> EdgeLightPreset.AI_THINKING
        visualState == EdgeLightVisualState.GENERATING ||
          visualState == EdgeLightVisualState.SPEAKING -> EdgeLightPreset.AI_GENERATING
        else -> config.preset
      }

    val colors = colorsForPreset(effectivePreset, visualState, config)
    val lowPower = config.batterySaverMode || isSystemPowerSave
    val rawBrightness = config.brightness.coerceIn(0.1f, 1.0f)
    val effectiveBrightness =
      when {
        lowPower -> (rawBrightness * 0.55f).coerceAtMost(0.45f)
        visualState == EdgeLightVisualState.IDLE && !forcePreview -> (rawBrightness * 0.68f).coerceIn(0.32f, 0.68f)
        else -> rawBrightness
      }

    val effectiveSpeed =
      when {
        config.reducedMotion -> 0f
        lowPower -> (config.animationSpeed * 0.35f).coerceIn(0.1f, 0.5f)
        visualState == EdgeLightVisualState.LISTENING -> config.animationSpeed * (1f + voiceRmsNormalized * 0.6f)
        else -> config.animationSpeed.coerceIn(0.25f, 2.5f)
      }

    val effectiveThickness =
      when {
        lowPower -> (config.borderThicknessDp * 0.7f).coerceAtLeast(2.5f)
        visualState == EdgeLightVisualState.LISTENING ->
          config.borderThicknessDp + (voiceRmsNormalized * 3.5f)
        else -> config.borderThicknessDp.coerceIn(2f, 14f)
      }

    val glowLayers = if (lowPower || config.reducedMotion) 1 else 3

    return EdgeLightRenderParams(
      isVisible = shouldShow,
      visualState = visualState,
      effectivePreset = effectivePreset,
      brightness = effectiveBrightness,
      speedMultiplier = effectiveSpeed,
      thicknessDp = effectiveThickness,
      cornerRadiusDp = config.cornerRadiusDp.coerceIn(0f, 52f),
      colorArgs = colors,
      glowLayerCount = glowLayers,
      voiceReactiveBoost = if (config.reducedMotion) 0f else voiceRmsNormalized.coerceIn(0f, 1f),
      breathingOnly =
        effectivePreset == EdgeLightPreset.BLUE_PURPLE_BREATHING ||
          effectivePreset == EdgeLightPreset.NOTIFICATION_ALERT ||
          config.reducedMotion,
    )
  }

  fun colorsForPreset(
    preset: EdgeLightPreset,
    visualState: EdgeLightVisualState,
    config: EdgeLightConfig,
  ): List<Long> {
    if (visualState == EdgeLightVisualState.ERROR) {
      return listOf(0xFFEF4444L, 0xFFF97316L, 0xFFDC2626L, 0xFFEF4444L)
    }
    if (visualState == EdgeLightVisualState.COMPLETED) {
      return listOf(0xFF10B981L, 0xFF06B6D4L, 0xFF3B82F6L, 0xFF10B981L)
    }
    return when (preset) {
      EdgeLightPreset.GEMINI_FLOW ->
        listOf(
          0xFF3B82F6L, // Electric Blue
          0xFF6366F1L, // Indigo Bridge
          0xFFA855F7L, // Vibrant Violet / Purple
          0xFFEC4899L, // Magenta / Pink
          0xFFFB7185L, // Soft Coral
          0xFFF59E0BL, // Warm Amber / Gold
          0xFF60A5FAL, // Soft Sky Blue Bridge
          0xFF3B82F6L, // Seamless Loop Closure
        )
      EdgeLightPreset.RAINBOW_SWEEP ->
        listOf(0xFFEF4444L, 0xFFF59E0BL, 0xFF10B981L, 0xFF3B82F6L, 0xFFA855F7L, 0xFFEF4444L)
      EdgeLightPreset.BLUE_PURPLE_BREATHING ->
        listOf(0xFF3B82F6L, 0xFF8B5CF6L, 0xFFEC4899L, 0xFF3B82F6L)
      EdgeLightPreset.ROTATING_GRADIENT ->
        listOf(0xFF3B82F6L, 0xFF6366F1L, 0xFFA855F7L, 0xFFEC4899L, 0xFFF59E0BL, 0xFF3B82F6L)
      EdgeLightPreset.AI_THINKING ->
        listOf(0xFF2563EBL, 0xFF7C3AEDL, 0xFFEC4899L, 0xFF3B82F6L)
      EdgeLightPreset.VOICE_LISTENING ->
        listOf(0xFF0EA5E9L, 0xFF3B82F6L, 0xFFA855F7L, 0xFF0EA5E9L)
      EdgeLightPreset.LIVE_SEARCH ->
        listOf(0xFF0284C7L, 0xFF38BDF8L, 0xFF8B5CF6L, 0xFF0284C7L)
      EdgeLightPreset.DEEP_RESEARCH ->
        listOf(0xFF0D9488L, 0xFF3B82F6L, 0xFFA855F7L, 0xFF0D9488L)
      EdgeLightPreset.AI_GENERATING ->
        listOf(
          0xFF3B82F6L,
          0xFF6366F1L,
          0xFFA855F7L,
          0xFFEC4899L,
          0xFFFB7185L,
          0xFFF59E0BL,
          0xFF60A5FAL,
          0xFF3B82F6L,
        )
      EdgeLightPreset.NOTIFICATION_ALERT ->
        listOf(0xFFF59E0BL, 0xFFEC4899L, 0xFF8B5CF6L, 0xFFF59E0BL)
      EdgeLightPreset.CUSTOM_GRADIENT ->
        listOf(
          config.primaryColorArgb,
          config.secondaryColorArgb,
          config.tertiaryColorArgb,
          config.quaternaryColorArgb,
          config.primaryColorArgb,
        )
    }
  }
}
