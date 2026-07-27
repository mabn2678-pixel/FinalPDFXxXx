package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
  SYSTEM, LIGHT, DARK, BLACK
}

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme =
  darkColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = YellowAccent,
    background = DarkSurfaces.background,
    surface = DarkSurfaces.surface,
    primaryContainer = Color(0xFF2A283E),
    onPrimaryContainer = Color(0xFFE6E0FF),
    onBackground = DarkSurfaces.onSurface,
    onSurface = DarkSurfaces.onSurface,
    surfaceVariant = DarkSurfaces.surfaceVariant,
    onSurfaceVariant = DarkSurfaces.onSurfaceVariant,
    outlineVariant = DarkSurfaces.outlineVariant
  )

private val BlackColorScheme =
  darkColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = YellowAccent,
    background = BlackSurfaces.background,
    surface = BlackSurfaces.surface,
    primaryContainer = Color(0xFF1F1B36),
    onPrimaryContainer = Color(0xFFE6E0FF),
    onBackground = BlackSurfaces.onSurface,
    onSurface = BlackSurfaces.onSurface,
    surfaceVariant = BlackSurfaces.surfaceVariant,
    onSurfaceVariant = BlackSurfaces.onSurfaceVariant,
    outlineVariant = BlackSurfaces.outlineVariant
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = YellowAccent,
    background = LightSurfaces.background,
    surface = LightSurfaces.surface,
    primaryContainer = LavenderContainer,
    onPrimaryContainer = LavenderPrimary,
    secondaryContainer = YellowContainer,
    onSecondaryContainer = Color(0xFF5D4037),
    onBackground = LightSurfaces.onSurface,
    onSurface = LightSurfaces.onSurface,
    onSurfaceVariant = LightSurfaces.onSurfaceVariant,
    surfaceVariant = LightSurfaces.surfaceVariant,
    outlineVariant = LightSurfaces.outlineVariant
  )

@Composable
fun MyApplicationTheme(
  appTheme: String = "system",
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val mode = when (appTheme) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    "black" -> ThemeMode.BLACK
    else -> if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT
  }

  val isDark = mode == ThemeMode.DARK || mode == ThemeMode.BLACK

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      mode == ThemeMode.BLACK -> BlackColorScheme
      isDark -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !isDark
      }
    }
  }

  CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

@Composable
fun PdfReaderProTheme(
  appTheme: String = "system",
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MyApplicationTheme(
    appTheme = appTheme,
    darkTheme = darkTheme,
    dynamicColor = dynamicColor,
    content = content
  )
}


