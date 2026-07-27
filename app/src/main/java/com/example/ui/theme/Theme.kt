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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  colorPresetIndex: Int = 0,
  content: @Composable () -> Unit,
) {
  val preset = BottomBarPresets.getOrElse(colorPresetIndex) { BottomBarPresets[0] }

  val darkScheme = darkColorScheme(
    primary = preset.darkOnSelected,
    secondary = preset.darkOnSelected,
    tertiary = YellowAccent,
    background = Color(0xFF12111A),
    surface = Color(0xFF1C1B26),
    primaryContainer = preset.darkSelectedContainer,
    onPrimaryContainer = preset.darkOnSelected,
    secondaryContainer = preset.darkSelectedContainer,
    onSecondaryContainer = preset.darkOnSelected,
    onBackground = Color(0xFFE6E0FF),
    onSurface = Color(0xFFE6E0FF),
    surfaceVariant = Color(0xFF2E2C3F),
    onSurfaceVariant = preset.darkUnselected
  )

  val lightScheme = lightColorScheme(
    primary = preset.lightOnSelected,
    secondary = preset.lightOnSelected,
    tertiary = YellowAccent,
    background = OffWhiteBg,
    surface = Color.White,
    primaryContainer = preset.lightSelectedContainer,
    onPrimaryContainer = preset.lightOnSelected,
    secondaryContainer = preset.lightSelectedContainer,
    onSecondaryContainer = preset.lightOnSelected,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = preset.lightUnselected,
    surfaceVariant = SoftGrayCard
  )

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> darkScheme
      else -> lightScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !darkTheme
      }
    }
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

