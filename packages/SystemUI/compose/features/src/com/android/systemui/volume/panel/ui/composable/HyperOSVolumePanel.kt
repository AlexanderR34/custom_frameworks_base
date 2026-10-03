/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.volume.panel.ui.composable

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.SliderState
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.SliderViewModel
import com.android.systemui.volume.panel.ui.layout.ComponentsLayout
import com.android.systemui.volume.panel.ui.viewmodel.VolumePanelViewModel
import kotlin.math.roundToInt

@Composable
fun HyperOSVolumePanel(
    viewModel: VolumePanelViewModel,
    layout: ComponentsLayout,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val notificationManager = remember(context) { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Outer full-screen container with dismiss-on-tap-outside
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    isVisible = false
                    onDismissRequest()
                }
            },
        contentAlignment = Alignment.TopEnd
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            ) + fadeOut()
        ) {
            // Compact Floating Card
            Box(
                modifier = Modifier
                    .padding(top = 70.dp, end = 16.dp, bottom = 24.dp)
                    .width(310.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp))
                    .pointerInput(Unit) {
                        // Consume taps inside the card so tapping inside does NOT dismiss
                        detectTapGestures {}
                    }
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top 4 Vertical Volume Sliders
                    HyperOSVerticalSlidersRow(
                        layout = layout,
                        audioManager = audioManager,
                        view = view
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Timers: Silent Mode & DND
                    HyperOSTimersSection(
                        audioManager = audioManager,
                        notificationManager = notificationManager,
                        view = view
                    )
                }
            }
        }
    }
}

@Composable
private fun HyperOSVerticalSlidersRow(
    layout: ComponentsLayout,
    audioManager: AudioManager,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    // Collect slider viewmodels from contentComponents
    val sliderViewModels = remember(layout) {
        val result = mutableListOf<SliderViewModel>()
        for (componentState in layout.contentComponents) {
            val component = componentState.component
            if (component is com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlidersComponent) {
                // Sliders component found
            }
        }
        result
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Multimedia (STREAM_MUSIC)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_MUSIC,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.ic_hyperos_speaker_mid,
            view = view,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 2. Notificaciones (STREAM_NOTIFICATION)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_NOTIFICATION,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.ic_hyperos_bell_normal,
            view = view,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 3. Alarma (STREAM_ALARM)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_ALARM,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.stat_sys_alarm,
            view = view,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 4. Timbre / Llamada (STREAM_RING)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_RING,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.ic_hyperos_call_volume,
            view = view,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HyperOSVerticalStreamSlider(
    streamType: Int,
    audioManager: AudioManager,
    fallbackIconRes: Int,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    val maxVolume = remember(streamType) {
        try {
            audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
        } catch (e: Exception) {
            15
        }
    }
    val minVolume = remember(streamType) {
        try {
            audioManager.getStreamMinVolume(streamType)
        } catch (e: Exception) {
            0
        }
    }

    var currentVolume by remember {
        mutableIntStateOf(
            try {
                audioManager.getStreamVolume(streamType)
            } catch (e: Exception) {
                (maxVolume / 2)
            }
        )
    }

    val fraction = remember(currentVolume, minVolume, maxVolume) {
        ((currentVolume - minVolume).toFloat() / (maxVolume - minVolume).toFloat()).coerceIn(0f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "HyperOSVolumeFraction_$streamType"
    )

    val isMuted = currentVolume <= minVolume

    Column(
        modifier = modifier
            .height(175.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val height = size.height
                            val calculatedFraction = 1f - (offset.y / height).coerceIn(0f, 1f)
                            val targetVol = (minVolume + calculatedFraction * (maxVolume - minVolume)).roundToInt()
                            if (targetVol != currentVolume) {
                                currentVolume = targetVol
                                try {
                                    audioManager.setStreamVolume(streamType, targetVol, 0)
                                } catch (e: Exception) {}
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val height = size.height
                            val calculatedFraction = 1f - (change.position.y / height).coerceIn(0f, 1f)
                            val targetVol = (minVolume + calculatedFraction * (maxVolume - minVolume)).roundToInt()
                            if (targetVol != currentVolume) {
                                currentVolume = targetVol
                                try {
                                    audioManager.setStreamVolume(streamType, targetVol, 0)
                                } catch (e: Exception) {}
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            }
                        }
                    )
                }
        ) {
            val totalHeight = maxHeight

            // Active Filled Level (grows from bottom to top)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(totalHeight * animatedFraction)
                    .background(MaterialTheme.colorScheme.primary)
            )

            // Bottom Icon badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Toggle Mute / Max on icon click
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val targetVol = if (currentVolume > minVolume) minVolume else (maxVolume * 0.7f).roundToInt()
                        currentVolume = targetVol
                        try {
                            audioManager.setStreamVolume(streamType, targetVol, 0)
                        } catch (e: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconColor = if (animatedFraction > 0.18f) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Icon(
                    painter = painterResource(id = fallbackIconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun HyperOSTimersSection(
    audioManager: AudioManager,
    notificationManager: NotificationManager,
    view: android.view.View,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Silent Mode Timer Row (30m to 8h)
        HyperOSTimerRow(
            iconRes = R.drawable.ic_hyperos_bell_normal,
            activeIconRes = R.drawable.ic_hyperos_bell_mute,
            label = "Silenciar dispositivo",
            defaultTimerText = "Deslice para establecer un temporizador",
            minMinutes = 30,
            maxMinutes = 480, // 8 hours
            onToggle = { isActive ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                try {
                    audioManager.ringerMode = if (isActive) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                } catch (e: Exception) {}
            },
            onTimerChanged = { minutes ->
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }
        )

        // 2. DND (Do Not Disturb) Timer Row (30m to 8h)
        HyperOSTimerRow(
            iconRes = R.drawable.ic_hyperos_dnd_moon,
            activeIconRes = R.drawable.ic_hyperos_dnd_moon,
            label = "No molestar",
            defaultTimerText = "Deslice para establecer un temporizador",
            minMinutes = 30,
            maxMinutes = 480, // 8 hours
            onToggle = { isActive ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                try {
                    notificationManager.setZenMode(
                        if (isActive) android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
                        else android.provider.Settings.Global.ZEN_MODE_OFF,
                        null,
                        "HyperOSTimer"
                    )
                } catch (e: Exception) {}
            },
            onTimerChanged = { minutes ->
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }
        )
    }
}

@Composable
private fun HyperOSTimerRow(
    iconRes: Int,
    activeIconRes: Int,
    label: String,
    defaultTimerText: String,
    minMinutes: Int,
    maxMinutes: Int,
    onToggle: (Boolean) -> Unit,
    onTimerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isActive by remember { mutableStateOf(false) }
    var timerProgress by remember { mutableFloatStateOf(0f) } // 0f to 1f

    val calculatedMinutes = remember(timerProgress) {
        if (timerProgress <= 0.02f) 0
        else {
            val stepMinutes = ((minMinutes + timerProgress * (maxMinutes - minMinutes)) / 15).roundToInt() * 15
            stepMinutes.coerceIn(minMinutes, maxMinutes)
        }
    }

    val displayTimerText = remember(calculatedMinutes, defaultTimerText) {
        if (calculatedMinutes == 0) {
            defaultTimerText
        } else {
            val hours = calculatedMinutes / 60
            val mins = calculatedMinutes % 60
            when {
                hours > 0 && mins > 0 -> "Temporizador: ${hours}h ${mins}m"
                hours > 0 -> "Temporizador: ${hours}h"
                else -> "Temporizador: ${mins}m"
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Circular Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isActive || calculatedMinutes > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
                .clickable {
                    isActive = !isActive
                    onToggle(isActive)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isActive) activeIconRes else iconRes),
                contentDescription = label,
                tint = if (isActive || calculatedMinutes > 0) MaterialTheme.colorScheme.onPrimary
                       else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }

        // Right Capsule Slider for Timer
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val width = size.width
                            timerProgress = (offset.x / width).coerceIn(0f, 1f)
                            if (timerProgress > 0.05f) {
                                isActive = true
                                onToggle(true)
                            }
                            onTimerChanged(calculatedMinutes)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val width = size.width
                            timerProgress = (change.position.x / width).coerceIn(0f, 1f)
                            if (timerProgress > 0.05f && !isActive) {
                                isActive = true
                                onToggle(true)
                            }
                            onTimerChanged(calculatedMinutes)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth

            // Filled slider track
            if (timerProgress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(totalWidth * timerProgress)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            }

            // Text Label inside the capsule
            Text(
                text = displayTimerText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (timerProgress > 0.5f) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                maxLines = 1
            )
        }
    }
}
