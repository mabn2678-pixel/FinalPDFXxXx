package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val glassLavenderColorScheme: ColorScheme
    @Composable
    get() = MaterialTheme.colorScheme

/**
 * Unified reusable Bottom Sheet component for the entire application.
 *
 * Visual & Functional Design:
 * 1. Peek state (~1/3 screen) enabled via skipPartiallyExpanded = false.
 * 2. Drag Handle: short grey horizontal line centered at the top.
 * 3. Drag to expand to full screen.
 * 4. Background: dynamic theme surface color with 28.dp top rounded corners.
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
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
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
