package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Brand / Accent Colors
val LavenderPrimary = Color(0xFF7C5CFF)
val LavenderSecondary = Color(0xFFB19DFF)
val LavenderContainer = Color(0xFFF1EEFF)
val YellowAccent = Color(0xFFFFD54F)
val YellowContainer = Color(0xFFFFF9C4)
val OffWhiteBg = Color(0xFFFAF9FE)
val DarkText = Color(0xFF1C1B22)
val LightText = Color(0xFF767482)
val SoftGrayCard = Color(0xFFF6F5FA)

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF7C5CFF)
val PurpleGrey40 = Color(0xFFB19DFF)
val Pink40 = Color(0xFFFFD54F)

// Additional PDF Reader Pro Primary & Status Colors
val PrimaryDark = Color(0xFF9D84FF)
val PrimaryLight = Color(0xFF7C5CFF)

// Surface Palettes for App Themes
object DarkSurfaces {
    val background = Color(0xFF12111A)
    val surface = Color(0xFF1C1B26)
    val surfaceVariant = Color(0xFF2E2C3F)
    val onSurface = Color(0xFFE6E0FF)
    val onSurfaceVariant = Color(0xFFBBB8CF)
    val outlineVariant = Color(0xFF3E3B54)
}

object BlackSurfaces {
    val background = Color(0xFF000000)
    val surface = Color(0xFF121212)
    val surfaceVariant = Color(0xFF1E1E1E)
    val onSurface = Color(0xFFF0F0F0)
    val onSurfaceVariant = Color(0xFFA0A0A0)
    val outlineVariant = Color(0xFF2C2C2C)
}

object LightSurfaces {
    val background = OffWhiteBg
    val surface = Color.White
    val surfaceVariant = SoftGrayCard
    val onSurface = DarkText
    val onSurfaceVariant = LightText
    val outlineVariant = Color(0xFFE0E0E8)
}

// Reader Theme Palettes for Document Viewing
object ReaderColors {
    val LightBg = Color(0xFFFFFFFF)
    val LightText = Color(0xFF1C1B22)

    val SepiaBg = Color(0xFFFBF0D9)
    val SepiaText = Color(0xFF5F4B32)

    val DarkBg = Color(0xFF1C1B26)
    val DarkText = Color(0xFFE6E0FF)

    val BlackBg = Color(0xFF000000)
    val BlackText = Color(0xFFE0E0E0)
}

