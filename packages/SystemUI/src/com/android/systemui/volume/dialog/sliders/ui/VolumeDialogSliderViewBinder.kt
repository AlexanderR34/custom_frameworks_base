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

package com.android.systemui.volume.dialog.sliders.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.haptics.slider.SliderHapticFeedbackFilter
import com.android.systemui.haptics.slider.compose.ui.SliderHapticsViewModel
import com.android.systemui.res.R
import com.android.systemui.volume.dialog.domain.interactor.ExpandedAudioTileDetailsFeatureInteractor
import com.android.systemui.volume.dialog.sliders.dagger.VolumeDialogSliderScope
import com.android.systemui.volume.dialog.sliders.ui.compose.SliderTrack
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogOverscrollViewModel
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderStateModel
import com.android.systemui.volume.dialog.sliders.ui.viewmodel.VolumeDialogSliderViewModel
import com.android.systemui.volume.haptics.ui.VolumeHapticsConfigsProvider
import com.android.systemui.volume.ui.compose.slider.AccessibilityParams
import com.android.systemui.volume.ui.compose.slider.Haptics
import com.android.systemui.volume.ui.compose.slider.Slider
import com.android.systemui.volume.ui.compose.slider.SliderIcon
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@VolumeDialogSliderScope
class VolumeDialogSliderViewBinder
@Inject
constructor(
    private val viewModel: VolumeDialogSliderViewModel,
    private val overscrollViewModel: VolumeDialogOverscrollViewModel,
    private val hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    private val expandedAudioTileDetailsFeatureInteractor: ExpandedAudioTileDetailsFeatureInteractor,
) {
    fun bind(view: View) {
        // Use horizontal volume dialog if the audio tile details view is enabled
        val isVolumeDialogVertical = !expandedAudioTileDetailsFeatureInteractor.isEnabled()
        val sliderComposeViewId =
            if (isVolumeDialogVertical) {
                R.id.volume_dialog_slider
            } else {
                R.id.volume_dialog_slider_horizontal
            }
        val sliderComposeView: ComposeView = view.requireViewById(sliderComposeViewId)
        sliderComposeView.setContent {
            PlatformTheme {
                VolumeDialogSlider(
                    viewModel = viewModel,
                    overscrollViewModel = overscrollViewModel,
                    hapticsViewModelFactory = hapticsViewModelFactory,
                    isVolumeDialogVertical = isVolumeDialogVertical,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VolumeDialogSlider(
    viewModel: VolumeDialogSliderViewModel,
    overscrollViewModel: VolumeDialogOverscrollViewModel,
    hapticsViewModelFactory: SliderHapticsViewModel.Factory,
    isVolumeDialogVertical: Boolean,
    modifier: Modifier = Modifier,
    dimensions: VolumeSliderDimensions =
        if (isVolumeDialogVertical) {
            VolumeSliderDimensions.Vertical
        } else {
            VolumeSliderDimensions.Horizontal
        },
) {
    val context = LocalContext.current
    val isHyperOS = remember {
        Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.HYPEROS_VOLUME_PANEL_STYLE,
            0,
            UserHandle.USER_CURRENT
        ) == 1
    }

    val collectedSliderStateModel by viewModel.state.collectAsStateWithLifecycle(null)
    val sliderStateModel = collectedSliderStateModel ?: return

    if (isHyperOS && isVolumeDialogVertical) {
        HyperOSVolumeVerticalLayout(
            viewModel = viewModel,
            sliderStateModel = sliderStateModel,
            overscrollViewModel = overscrollViewModel,
        )
        return
    }

    val colors =
        if (isVolumeDialogVertical) {
            SliderDefaults.colors(
                activeTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledActiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            SliderDefaults.colors(
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTickColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledActiveTickColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is DragInteraction.Start -> viewModel.onSliderDragStarted()
                is DragInteraction.Cancel -> viewModel.onSliderDragFinished()
                is DragInteraction.Stop -> viewModel.onSliderDragFinished()
            }
        }
    }

    Slider(
        value = sliderStateModel.value,
        valueRange = sliderStateModel.valueRange,
        onValueChanged = { value ->
            overscrollViewModel.setSlider(
                value = value,
                min = sliderStateModel.valueRange.start,
                max = sliderStateModel.valueRange.endInclusive,
            )
            viewModel.setStreamVolume(value, true)
        },
        onValueChangeFinished = { viewModel.onSliderChangeFinished(it) },
        isEnabled = !sliderStateModel.isDisabled,
        isReverseDirection = true,
        isVertical = isVolumeDialogVertical,
        colors = colors,
        interactionSource = interactionSource,
        haptics =
            Haptics.Enabled(
                hapticsViewModelFactory = hapticsViewModelFactory,
                hapticConfigs =
                    VolumeHapticsConfigsProvider.continuousConfigs(SliderHapticFeedbackFilter()),
                orientation =
                    if (isVolumeDialogVertical) {
                        Orientation.Vertical
                    } else {
                        Orientation.Horizontal
                    },
            ),
        stepDistance = 1f,
        track = { sliderState ->
            SliderTrack(
                sliderState,
                colors = colors,
                isEnabled = !sliderStateModel.isDisabled,
                isVertical = isVolumeDialogVertical,
                thumbTrackGapSize = 6.dp,
                trackCornerSize = 12.dp,
                trackInsideCornerSize = 2.dp,
                activeTrackEndIcon = { iconsState ->
                    SliderIcon(
                        icon = {
                            Icon(
                                icon = sliderStateModel.icon,
                                tint = null,
                                modifier = Modifier.size(dimensions.iconSize),
                            )
                        },
                        isVisible = !iconsState.isInactiveTrackEndIconVisible,
                    )
                },
                inactiveTrackEndIcon = { iconsState ->
                    SliderIcon(
                        icon = {
                            Icon(
                                icon = sliderStateModel.icon,
                                tint = null,
                                modifier = Modifier.size(dimensions.iconSize),
                            )
                        },
                        isVisible = iconsState.isInactiveTrackEndIconVisible,
                    )
                },
                trackSize = dimensions.trackSize,
            )
        },
        thumb = { sliderState, interactions ->
            SliderDefaults.Thumb(
                sliderState = sliderState,
                interactionSource = interactions,
                enabled = !sliderStateModel.isDisabled,
                colors = colors,
                thumbSize = DpSize(dimensions.thumbWidth, dimensions.thumbHeight),
            )
        },
        accessibilityParams = AccessibilityParams(contentDescription = sliderStateModel.label),
        modifier =
            modifier.pointerInput(Unit) {
                coroutineScope {
                    val currentContext = currentCoroutineContext()
                    awaitPointerEventScope {
                        while (currentContext.isActive) {
                            viewModel.onTouchEvent(awaitPointerEvent())
                        }
                    }
                }
            },
    )
}

/**
 * Authentic HyperOS Vertical Volume Panel layout matching official Xiaomi design.
 * Features:
 * - Left App Volume / Sound Assistant Floating Button (46dp circle).
 * - Wide rounded volume capsule (62dp x 210dp) with frosted translucent dark background and subtle border.
 * - Top 3 dots ••• for expanding full volume panel.
 * - Solid white progress fill from bottom with smooth dragging.
 * - Dynamic animated Speaker Icon with progressive wave arcs (1, 2, 3 waves / mute) and color switching.
 * - Bottom Ringer Mode stadium button (62dp x 48dp) for normal/vibrate/silent.
 * - Bottom DND Mode stadium button (62dp x 48dp) with crescent moon icon.
 */
@Composable
private fun HyperOSVolumeVerticalLayout(
    viewModel: VolumeDialogSliderViewModel,
    sliderStateModel: VolumeDialogSliderStateModel,
    overscrollViewModel: VolumeDialogOverscrollViewModel,
) {
    val context = LocalContext.current
    val min = sliderStateModel.valueRange.start
    val max = sliderStateModel.valueRange.endInclusive
    val range = (max - min).coerceAtLeast(1f)
    val currentVal = sliderStateModel.value.coerceIn(min, max)
    val progressFraction = ((currentVal - min) / range).coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .wrapContentSize()
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        // 1. HyperOS Left App Volume / Sound Assistant Button
        HyperOSSoundAssistantButton(context = context)

        // 2. Right Vertical Volume Stack
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            // Main Volume Slider Capsule
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 210.dp)
                    .clip(RoundedCornerShape(31.dp))
                    .background(Color(0x8A1A1A1A))
                    .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(31.dp))
                    .pointerInput(min, max, range) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                viewModel.onSliderDragStarted()
                            },
                            onDragEnd = {
                                viewModel.onSliderDragFinished()
                            },
                            onDragCancel = {
                                viewModel.onSliderDragFinished()
                            },
                            onVerticalDrag = { change, _ ->
                                change.consume()
                                val touchY = change.position.y
                                val heightPx = size.height.toFloat()
                                val frac = 1f - (touchY / heightPx).coerceIn(0f, 1f)
                                val targetVal = min + frac * range
                                overscrollViewModel.setSlider(targetVal, min, max)
                                viewModel.setStreamVolume(targetVal, true)
                            }
                        )
                    }
                    .pointerInput(min, max, range) {
                        detectTapGestures { offset ->
                            val touchY = offset.y
                            val heightPx = size.height.toFloat()
                            val frac = 1f - (touchY / heightPx).coerceIn(0f, 1f)
                            val targetVal = min + frac * range
                            overscrollViewModel.setSlider(targetVal, min, max)
                            viewModel.setStreamVolume(targetVal, true)
                            viewModel.onSliderChangeFinished(targetVal)
                        }
                    }
            ) {
                // White solid progress fill from bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(progressFraction)
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(31.dp))
                        .background(Color.White)
                )

                // Top 3 dots ••• (Expand / Sound Settings button)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 15.dp)
                        .clickable {
                            try {
                                val intent = Intent("android.settings.panel.action.VOLUME").apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e2: Exception) {}
                            }
                        }
                        .padding(4.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB0B0B0))
                        )
                    }
                }

                // Dynamic Speaker Icon with Animated Waves
                // Selects wave count based on volume level: Mute -> Low (1 wave) -> Mid (2 waves) -> High (3 waves)
                val speakerIconRes = when {
                    progressFraction <= 0.01f || sliderStateModel.isDisabled -> R.drawable.ic_hyperos_speaker_mute
                    progressFraction < 0.34f -> R.drawable.ic_hyperos_speaker_low
                    progressFraction < 0.67f -> R.drawable.ic_hyperos_speaker_mid
                    else -> R.drawable.ic_hyperos_speaker_high
                }

                // When covered by white fill (progress >= 0.20): Blue #2A72E5. Otherwise: Light grey #EEEEEE.
                val iconTint = if (progressFraction >= 0.20f) Color(0xFF2A72E5) else Color(0xFFEEEEEE)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .size(26.dp)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(id = speakerIconRes),
                        contentDescription = sliderStateModel.label,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // HyperOS Ringer Mode Toggle Button (Stadium Pill: 62dp x 48dp)
            HyperOSRingerPill(context = context)

            // HyperOS DND Mode Toggle Button (Stadium Pill: 62dp x 48dp)
            HyperOSDndPill(context = context)
        }
    }
}

/**
 * HyperOS Left Sound Assistant / Per-App Volume Floating Button.
 */
@Composable
private fun HyperOSSoundAssistantButton(context: Context) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color(0x8A1A1A1A))
            .border(0.75.dp, Color(0x33FFFFFF), CircleShape)
            .clickable {
                try {
                    val intent = Intent("android.settings.panel.action.VOLUME").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e2: Exception) {}
                }
            }
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(id = R.drawable.ic_app_volume),
            contentDescription = "App Volume",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HyperOSRingerPill(context: Context) {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var ringerMode by remember { mutableStateOf(audioManager.ringerModeInternal) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 62.dp, height = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x8A1A1A1A))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .clickable {
                val nextMode = when (ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                audioManager.ringerModeInternal = nextMode
                ringerMode = nextMode
            }
    ) {
        val iconRes = when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> R.drawable.ic_hyperos_bell_normal
            AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_volume_ringer_vibrate
            else -> R.drawable.ic_hyperos_bell_mute
        }
        val iconTint = if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Color(0xFFFF5252) // Red accent for silent / muted bell
        } else {
            Color.White
        }
        androidx.compose.material3.Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Ringer Mode",
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HyperOSDndPill(context: Context) {
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var isDndActive by remember {
        mutableStateOf(
            Settings.Global.getInt(context.contentResolver, Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF) != Settings.Global.ZEN_MODE_OFF
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 62.dp, height = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x8A1A1A1A))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .clickable {
                val currentZen = Settings.Global.getInt(context.contentResolver, Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF)
                val newZen = if (currentZen == Settings.Global.ZEN_MODE_OFF) {
                    Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS
                } else {
                    Settings.Global.ZEN_MODE_OFF
                }
                notificationManager.setZenMode(newZen, null, "HyperOSVolumePanel")
                isDndActive = (newZen != Settings.Global.ZEN_MODE_OFF)
            }
    ) {
        androidx.compose.material3.Icon(
            painter = painterResource(id = R.drawable.ic_hyperos_dnd_moon),
            contentDescription = "Do Not Disturb",
            tint = if (isDndActive) Color(0xFF2A72E5) else Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

data class VolumeSliderDimensions(
    val iconSize: Dp,
    val thumbHeight: Dp,
    val thumbWidth: Dp,
    val trackSize: Dp,
) {
    companion object {
        val Vertical =
            VolumeSliderDimensions(
                iconSize = 20.dp,
                thumbWidth = 52.dp,
                thumbHeight = 4.dp,
                trackSize = 40.dp,
            )

        val Horizontal =
            VolumeSliderDimensions(
                iconSize = 24.dp,
                thumbHeight = 40.dp,
                thumbWidth = 3.dp,
                trackSize = 32.dp,
            )
    }
}
