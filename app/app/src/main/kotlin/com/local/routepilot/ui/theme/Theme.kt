package com.local.routepilot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class RoutePilotColors(val darkSheet: androidx.compose.ui.graphics.Color, val onDarkSheet: androidx.compose.ui.graphics.Color)

val LocalRoutePilotColors = staticCompositionLocalOf {
  RoutePilotColors(darkSheet = darkSheetLight, onDarkSheet = onDarkSheetLight)
}

private val DarkColorScheme = darkColorScheme(
  primary = primaryDark,
  onPrimary = onPrimaryDark,
  primaryContainer = primaryContainerDark,
  onPrimaryContainer = onPrimaryContainerDark,
  secondary = secondaryDark,
  onSecondary = onSecondaryDark,
  secondaryContainer = secondaryContainerDark,
  onSecondaryContainer = onSecondaryContainerDark,
  tertiary = tertiaryDark,
  onTertiary = onTertiaryDark,
  background = backgroundDark,
  onBackground = onBackgroundDark,
  surface = surfaceDark,
  onSurface = onSurfaceDark,
  surfaceVariant = surfaceVariantDark,
  onSurfaceVariant = onSurfaceVariantDark,
  outline = outlineDark,
)

private val LightColorScheme = lightColorScheme(
  primary = primaryLight,
  onPrimary = onPrimaryLight,
  primaryContainer = primaryContainerLight,
  onPrimaryContainer = onPrimaryContainerLight,
  secondary = secondaryLight,
  onSecondary = onSecondaryLight,
  secondaryContainer = secondaryContainerLight,
  onSecondaryContainer = onSecondaryContainerLight,
  tertiary = tertiaryLight,
  onTertiary = onTertiaryLight,
  background = backgroundLight,
  onBackground = onBackgroundLight,
  surface = surfaceLight,
  onSurface = onSurfaceLight,
  surfaceVariant = surfaceVariantLight,
  onSurfaceVariant = onSurfaceVariantLight,
  outline = outlineLight,
)

object RoutePilotTheme {
  val colors: RoutePilotColors
    @Composable get() = LocalRoutePilotColors.current
}

@Composable
fun RoutePilotTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val extra = if (darkTheme) {
    RoutePilotColors(darkSheetDark, onDarkSheetDark)
  } else {
    RoutePilotColors(darkSheetLight, onDarkSheetLight)
  }
  CompositionLocalProvider(LocalRoutePilotColors provides extra) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
