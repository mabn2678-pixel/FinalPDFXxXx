package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleekSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(30.dp),
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = if (enabled) activeTrackColor else activeTrackColor.copy(alpha = 0.38f),
                        shape = CircleShape
                    )
                    .border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = activeTrackColor,
                    inactiveTrackColor = inactiveTrackColor
                ),
                drawStopIndicator = null
            )
        }
    )
}
