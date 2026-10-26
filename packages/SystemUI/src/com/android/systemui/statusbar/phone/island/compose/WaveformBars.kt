package com.android.systemui.statusbar.phone.island.compose

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun WaveformBars(
    isPlaying: Boolean = true,
    barCount: Int = 4,
    colors: List<Color> = listOf(Color(0xFF30D158), Color(0xFFFF2D55)),
    color: Color? = null,
    maxHeight: Dp = 17.dp,
    modifier: Modifier = Modifier
) {
    var animTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(16)
                val now = SystemClock.elapsedRealtime()
                animTimeMs += (now - lastTime)
                lastTime = now
            }
        }
    }

    val barBrush = remember(colors, color) {
        if (color != null) {
            Brush.verticalGradient(listOf(color, color))
        } else if (colors.size >= 2) {
            Brush.verticalGradient(
                colors = listOf(colors[0], colors[1])
            )
        } else {
            val fallback = colors.firstOrNull() ?: Color(0xFF30D158)
            Brush.verticalGradient(listOf(fallback, fallback))
        }
    }

    val t = animTimeMs / 1000f

    val dynamicHeights = listOf(
        0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(t * 8.0f).toFloat()),
        0.20f + 0.80f * (0.5f + 0.5f * kotlin.math.sin(t * 12.0f + 1.2f).toFloat()),
        0.40f + 0.60f * (0.5f + 0.5f * kotlin.math.sin(t * 7.0f + 2.4f).toFloat()),
        0.25f + 0.75f * (0.5f + 0.5f * kotlin.math.sin(t * 10.5f + 3.8f).toFloat())
    )

    val restingHeights = listOf(0.40f, 0.60f, 0.45f, 0.55f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val scale = if (isPlaying) {
                dynamicHeights[index % dynamicHeights.size]
            } else {
                restingHeights[index % restingHeights.size]
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((maxHeight * scale).coerceAtLeast(4.dp))
                    .background(barBrush, RoundedCornerShape(1.5.dp))
            )
        }
    }
}
