package com.example.ui.theme

import androidx.compose.ui.graphics.Color

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

data class BottomBarColorPreset(
    val name: String,
    val lightBg: Color,
    val darkBg: Color,
    val lightOnSelected: Color,
    val darkOnSelected: Color,
    val lightUnselected: Color,
    val darkUnselected: Color,
    val lightSelectedContainer: Color,
    val darkSelectedContainer: Color
)

val BottomBarPresets = listOf(
    BottomBarColorPreset(
        name = "الافتراضي",
        lightBg = Color.White,
        darkBg = Color(0xFF1C1B26),
        lightOnSelected = Color(0xFF7C5CFF),
        darkOnSelected = Color(0xFFB19DFF),
        lightUnselected = Color(0xFF767482),
        darkUnselected = Color(0xFFBBB8CF),
        lightSelectedContainer = Color(0xFFF1EEFF),
        darkSelectedContainer = Color(0xFF2A283E)
    ),
    BottomBarColorPreset(
        name = "الأزرق الملكي",
        lightBg = Color(0xFFE3F2FD),
        darkBg = Color(0xFF0D47A1),
        lightOnSelected = Color(0xFF1565C0),
        darkOnSelected = Color(0xFF90CAF9),
        lightUnselected = Color(0xFF546E7A),
        darkUnselected = Color(0xFFB0BEC5),
        lightSelectedContainer = Color(0xFFBBDEFB),
        darkSelectedContainer = Color(0xFF1565C0)
    ),
    BottomBarColorPreset(
        name = "الأخضر الزمردي",
        lightBg = Color(0xFFE8F5E9),
        darkBg = Color(0xFF1B5E20),
        lightOnSelected = Color(0xFF2E7D32),
        darkOnSelected = Color(0xFFA5D6A7),
        lightUnselected = Color(0xFF4F5B66),
        darkUnselected = Color(0xFFC8E6C9),
        lightSelectedContainer = Color(0xFFC8E6C9),
        darkSelectedContainer = Color(0xFF2E7D32)
    ),
    BottomBarColorPreset(
        name = "البرتقالي الدافئ",
        lightBg = Color(0xFFFFF3E0),
        darkBg = Color(0xFFE65100),
        lightOnSelected = Color(0xFFD84315),
        darkOnSelected = Color(0xFFFFB74D),
        lightUnselected = Color(0xFF5D4037),
        darkUnselected = Color(0xFFFFE0B2),
        lightSelectedContainer = Color(0xFFFFE0B2),
        darkSelectedContainer = Color(0xFFD84315)
    ),
    BottomBarColorPreset(
        name = "الأحمر القرمزي",
        lightBg = Color(0xFFFFEBEE),
        darkBg = Color(0xFFB71C1C),
        lightOnSelected = Color(0xFFC62828),
        darkOnSelected = Color(0xFFFFCDD2),
        lightUnselected = Color(0xFF5D4037),
        darkUnselected = Color(0xFFFFCDD2),
        lightSelectedContainer = Color(0xFFFFCDD2),
        darkSelectedContainer = Color(0xFFC62828)
    ),
    BottomBarColorPreset(
        name = "خيال اللافندر",
        lightBg = Color(0xFFF3E5F5),
        darkBg = Color(0xFF4A148C),
        lightOnSelected = Color(0xFF6A1B9A),
        darkOnSelected = Color(0xFFE1BEE7),
        lightUnselected = Color(0xFF4A148C),
        darkUnselected = Color(0xFFE1BEE7),
        lightSelectedContainer = Color(0xFFE1BEE7),
        darkSelectedContainer = Color(0xFF6A1B9A)
    ),
    BottomBarColorPreset(
        name = "السيبي دافئ",
        lightBg = Color(0xFFEFEBE9),
        darkBg = Color(0xFF3E2723),
        lightOnSelected = Color(0xFF4E342E),
        darkOnSelected = Color(0xFFD7CCC8),
        lightUnselected = Color(0xFF705751),
        darkUnselected = Color(0xFFD7CCC8),
        lightSelectedContainer = Color(0xFFD7CCC8),
        darkSelectedContainer = Color(0xFF4E342E)
    ),
    BottomBarColorPreset(
        name = "نسيم التيل",
        lightBg = Color(0xFFE0F2F1),
        darkBg = Color(0xFF004D40),
        lightOnSelected = Color(0xFF00695C),
        darkOnSelected = Color(0xFF80CBC4),
        lightUnselected = Color(0xFF37474F),
        darkUnselected = Color(0xFFB2DFDB),
        lightSelectedContainer = Color(0xFFB2DFDB),
        darkSelectedContainer = Color(0xFF00695C)
    ),
    BottomBarColorPreset(
        name = "الوردي المرجاني",
        lightBg = Color(0xFFFCE4EC),
        darkBg = Color(0xFF880E4F),
        lightOnSelected = Color(0xFFAD1457),
        darkOnSelected = Color(0xFFF8BBD0),
        lightUnselected = Color(0xFF4A148C),
        darkUnselected = Color(0xFFF8BBD0),
        lightSelectedContainer = Color(0xFFF8BBD0),
        darkSelectedContainer = Color(0xFFAD1457)
    ),
    BottomBarColorPreset(
        name = "إنديغو السيبراني",
        lightBg = Color(0xFFE8EAF6),
        darkBg = Color(0xFF1A237E),
        lightOnSelected = Color(0xFF283593),
        darkOnSelected = Color(0xFFC5CAE9),
        lightUnselected = Color(0xFF3F51B5),
        darkUnselected = Color(0xFFC5CAE9),
        lightSelectedContainer = Color(0xFFC5CAE9),
        darkSelectedContainer = Color(0xFF283593)
    ),
    BottomBarColorPreset(
        name = "الفحم الحجري",
        lightBg = Color(0xFFECEFF1),
        darkBg = Color(0xFF263238),
        lightOnSelected = Color(0xFF37474F),
        darkOnSelected = Color(0xFFCFD8DC),
        lightUnselected = Color(0xFF455A64),
        darkUnselected = Color(0xFFCFD8DC),
        lightSelectedContainer = Color(0xFFCFD8DC),
        darkSelectedContainer = Color(0xFF37474F)
    )
)

