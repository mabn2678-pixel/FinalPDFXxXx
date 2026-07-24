package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = YellowAccent,
    background = Color(0xFF12111A),
    surface = Color(0xFF1C1B26),
    primaryContainer = Color(0xFF2A283E),
    onPrimaryContainer = Color(0xFFE6E0FF),
    onBackground = Color(0xFFE6E0FF),
    onSurface = Color(0xFFE6E0FF),
    surfaceVariant = Color(0xFF2E2C3F),
    onSurfaceVariant = Color(0xFFBBB8CF)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LavenderPrimary,
    secondary = LavenderSecondary,
    tertiary = YellowAccent,
    background = OffWhiteBg,
    surface = Color.White,
    primaryContainer = LavenderContainer,
    onPrimaryContainer = LavenderPrimary,
    secondaryContainer = YellowContainer,
    onSecondaryContainer = Color(0xFF5D4037),
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = LightText,
    surfaceVariant = SoftGrayCard
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
