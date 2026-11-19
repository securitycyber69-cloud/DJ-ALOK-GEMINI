package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode(val label: String) {
  DARK("Dark"),
  LIGHT("Light"),
  SYSTEM("System"),
}

@Immutable
data class MousomiExtendedColors(
  val isDark: Boolean,
  val topBackground: Color,
  val midGlow: Color,
  val bottomGlow: Color,
  val inputPillContainer: Color,
  val activeChipContainer: Color,
  val userBubbleContainer: Color,
  val assistantBubbleContainer: Color,
  val upgradeButtonBlue: Color,
  val liveButtonBlue: Color,
  val avatarOrange: Color,
  val mutedText: Color,
  val hackerGradient: List<Color>,
)

val LocalMousomiColors = staticCompositionLocalOf {
  MousomiExtendedColors(
    isDark = true,
    topBackground = PureBlack,
    midGlow = DeepNavyGlow,
    bottomGlow = RoyalBlueGlow,
    inputPillContainer = GeminiInputCharcoal,
    activeChipContainer = GeminiChipActiveDark,
    userBubbleContainer = GeminiUserBubbleDark,
    assistantBubbleContainer = GeminiSurfaceDark,
    upgradeButtonBlue = GeminiUpgradeBlue,
    liveButtonBlue = GeminiLiveBlue,
    avatarOrange = AvatarOrange,
    mutedText = GeminiTextMuted,
    hackerGradient = listOf(GradientBlue, GradientPurple, GradientPink),
  )
}

private val MousomiDarkColorScheme =
  darkColorScheme(
    primary = GeminiLiveBlue,
    onPrimary = Color.White,
    primaryContainer = GeminiUpgradeBlue,
    onPrimaryContainer = Color.White,
    secondary = GradientPurple,
    onSecondary = Color.White,
    background = PureBlack,
    onBackground = GeminiTextWhite,
    surface = GeminiSurfaceDark,
    onSurface = GeminiTextWhite,
    surfaceVariant = GeminiSurfaceVariantDark,
    onSurfaceVariant = GeminiTextMuted,
    outline = GeminiOutlineDark,
  )

private val MousomiLightColorScheme =
  lightColorScheme(
    primary = GeminiLiveBlue,
    onPrimary = Color.White,
    primaryContainer = LightUserBubble,
    onPrimaryContainer = LightTextPrimary,
    secondary = GradientPurple,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextMuted,
    outline = LightOutline,
  )

@Composable
fun MousomiTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  content: @Composable () -> Unit,
) {
  val useDarkTheme =
    when (themeMode) {
      AppThemeMode.DARK -> true
      AppThemeMode.LIGHT -> false
      AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

  val colorScheme = if (useDarkTheme) MousomiDarkColorScheme else MousomiLightColorScheme
  val extendedColors =
    if (useDarkTheme) {
      MousomiExtendedColors(
        isDark = true,
        topBackground = PureBlack,
        midGlow = DeepNavyGlow,
        bottomGlow = RoyalBlueGlow,
        inputPillContainer = GeminiInputCharcoal,
        activeChipContainer = GeminiChipActiveDark,
        userBubbleContainer = GeminiUserBubbleDark,
        assistantBubbleContainer = GeminiSurfaceDark,
        upgradeButtonBlue = GeminiUpgradeBlue,
        liveButtonBlue = GeminiLiveBlue,
        avatarOrange = AvatarOrange,
        mutedText = GeminiTextMuted,
        hackerGradient = listOf(GradientBlue, GradientPurple, GradientPink),
      )
    } else {
      MousomiExtendedColors(
        isDark = false,
        topBackground = LightBackground,
        midGlow = Color(0xFFEAF0FD),
        bottomGlow = LightGlowBottom,
        inputPillContainer = LightInputPill,
        activeChipContainer = LightChipActive,
        userBubbleContainer = LightUserBubble,
        assistantBubbleContainer = LightSurface,
        upgradeButtonBlue = GeminiUpgradeBlue,
        liveButtonBlue = GeminiLiveBlue,
        avatarOrange = AvatarOrange,
        mutedText = LightTextMuted,
        hackerGradient = listOf(Color(0xFF1D6CE0), Color(0xFF7C3AED), Color(0xFFDB2777)),
      )
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !useDarkTheme
        insetsController.isAppearanceLightNavigationBars = !useDarkTheme
      }
    }
  }

  CompositionLocalProvider(LocalMousomiColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content,
    )
  }
}
