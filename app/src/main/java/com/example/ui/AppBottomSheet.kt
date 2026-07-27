package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val glassLavenderColorScheme = lightColorScheme(
    surface = Color(0xFFECE6F8),
    onSurface = Color(0xFF1C182B),
    surfaceVariant = Color(0xFFE2DBF0),
    onSurfaceVariant = Color(0xFF4C4566),
    primary = Color(0xFF7C5CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2DBF0),
    onPrimaryContainer = Color(0xFF2C1480),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E0F5),
    onSecondaryContainer = Color(0xFF1E192B),
    background = Color(0xFFECE6F8),
    onBackground = Color(0xFF1C182B),
    outline = Color(0xFF8C869C),
    outlineVariant = Color(0xFFCCC6DC)
)

/**
 * Unified reusable Bottom Sheet component for the entire application.
 *
 * Visual & Functional Design:
 * 1. Peek state (~1/3 screen) enabled via skipPartiallyExpanded = false.
 * 2. Drag Handle: short grey horizontal line centered at the top.
 * 3. Drag to expand to full screen.
 * 4. Background: unified glass lavender color with 28.dp top rounded corners.
 * 5. Dismissible via swipe down or clicking outer scrim overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF2ECE6F8),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        MaterialTheme(colorScheme = glassLavenderColorScheme) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                if (!title.isNullOrEmpty()) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                content()
            }
        }
    }
}
