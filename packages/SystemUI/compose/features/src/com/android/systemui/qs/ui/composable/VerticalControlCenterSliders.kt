/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.qs.ui.composable

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.brightness.domain.model.GammaBrightness
import com.android.systemui.brightness.shared.model.GammaBrightness.Drag
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.common.ui.compose.load
import com.android.systemui.res.R
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.ui.viewmodel.SliderState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SliderCornerRadius = 28.dp
private val InactiveSliderBackground = Color(0x28FFFFFF)
private val ActiveSliderProgress = Color(0xFFFFFFFF)
private val ActiveIconColor = Color(0xFF0D84FF)
private val InactiveIconColor = Color(0x8E8E93)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VerticalBrightnessSlider(
    viewModel: BrightnessSliderViewModel,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val gamma = viewModel.currentBrightness.value
    if (gamma == BrightnessSliderViewModel.initialValue.value) return

    val min = viewModel.minBrightness.value.toFloat()
    val max = viewModel.maxBrightness.value.toFloat()
    val range = (max - min).coerceAtLeast(1f)
    val fraction = ((gamma.toFloat() - min) / range).coerceIn(0f, 1f)

    var currentFraction by remember(fraction) { mutableFloatStateOf(fraction) }
    val scale = remember { Animatable(1f) }

    val monetPrimary = MaterialTheme.colorScheme.primary
    val monetBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    val monetInactiveIcon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    BoxWithConstraints(
        modifier =
            modifier
                .clip(RoundedCornerShape(SliderCornerRadius))
                .background(monetBackground)
                .scale(scale.value)
                .pointerInput(min, max) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            viewModel.setIsDragging(true)
                        },
                        onDragEnd = {
                            viewModel.setIsDragging(false)
                            val finalVal = (min + currentFraction * range).roundToInt()
                            coroutineScope.launch {
                                viewModel.onDrag(Drag.Stopped(GammaBrightness(finalVal)))
                            }
                        },
                        onDragCancel = {
                            viewModel.setIsDragging(false)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaFraction = -dragAmount / size.height.toFloat()
                            val newFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
                            if (newFraction != currentFraction) {
                                if ((newFraction == 0f || newFraction == 1f) && currentFraction != newFraction) {
                                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                    coroutineScope.launch {
                                        scale.animateTo(1.05f, spring())
                                        scale.animateTo(1.0f, spring())
                                    }
                                }
                                currentFraction = newFraction
                                val newVal = (min + newFraction * range).roundToInt()
                                coroutineScope.launch {
                                    viewModel.onDrag(Drag.Dragging(GammaBrightness(newVal)))
                                }
                            }
                        }
                    )
                }
                .pointerInteropFilter { motionEvent ->
                    if (motionEvent.actionMasked == MotionEvent.ACTION_UP ||
                        motionEvent.actionMasked == MotionEvent.ACTION_CANCEL) {
                        viewModel.emitBrightnessTouchForFalsing()
                    }
                    false
                }
    ) {
        val totalHeight = maxHeight
        val activeHeight = totalHeight * currentFraction

        // Progreso activo desde la base hacia arriba
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(activeHeight)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
        )

        // Icono nativo en la base enlazado a Monet
        val isIconCovered = currentFraction > 0.25f
        val animatedIconTint by animateColorAsState(
            if (isIconCovered) monetPrimary else monetInactiveIcon,
            label = "BrightnessIconColor"
        )

        Icon(
            painter = painterResource(com.android.internal.R.drawable.ic_menu_today),
            contentDescription = "Brightness",
            tint = animatedIconTint,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .size(26.dp)
        )
    }
}

@Composable
fun VerticalVolumeSlider(
    viewModel: AudioStreamSliderViewModel,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val sliderState by viewModel.slider.collectAsStateWithLifecycle()

    val min = sliderState.valueRange.start
    val max = sliderState.valueRange.endInclusive
    val range = (max - min).coerceAtLeast(1f)
    val fraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)

    var currentFraction by remember(fraction) { mutableFloatStateOf(fraction) }
    val scale = remember { Animatable(1f) }

    val monetPrimary = MaterialTheme.colorScheme.primary
    val monetBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    val monetInactiveIcon = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    BoxWithConstraints(
        modifier =
            modifier
                .clip(RoundedCornerShape(SliderCornerRadius))
                .background(monetBackground)
                .scale(scale.value)
                .pointerInput(min, max, sliderState) {
                    detectVerticalDragGestures(
                        onDragStart = {},
                        onDragEnd = {
                            viewModel.onValueChangeFinished()
                        },
                        onDragCancel = {},
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaFraction = -dragAmount / size.height.toFloat()
                            val newFraction = (currentFraction + deltaFraction).coerceIn(0f, 1f)
                            if (newFraction != currentFraction) {
                                if ((newFraction == 0f || newFraction == 1f) && currentFraction != newFraction) {
                                    view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                    coroutineScope.launch {
                                        scale.animateTo(1.05f, spring())
                                        scale.animateTo(1.0f, spring())
                                    }
                                }
                                currentFraction = newFraction
                                val newVal = min + newFraction * range
                                viewModel.onValueChanged(sliderState, newVal)
                            }
                        }
                    )
                }
    ) {
        val totalHeight = maxHeight
        val activeHeight = totalHeight * currentFraction

        // Progreso activo desde la base hacia arriba
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(activeHeight)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
        )

        // Icono nativo en la base enlazado a Monet
        val isIconCovered = currentFraction > 0.25f
        val animatedIconTint by animateColorAsState(
            if (isIconCovered) monetPrimary else monetInactiveIcon,
            label = "VolumeIconColor"
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(34.dp)
                    .clickable { viewModel.toggleMuted(sliderState) },
            contentAlignment = Alignment.Center
        ) {
            val icon = sliderState.icon
            if (icon != null) {
                Icon(
                    icon = icon,
                    tint = animatedIconTint,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_audio_vol),
                    contentDescription = "Volume",
                    tint = animatedIconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
