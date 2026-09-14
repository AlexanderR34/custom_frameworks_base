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
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.UserHandle
import android.provider.Settings
import android.view.View
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import kotlin.math.roundToInt
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val min = sliderStateModel.valueRange.start
    val max = sliderStateModel.valueRange.endInclusive
    val range = (max - min).coerceAtLeast(1f)
    val currentVal = sliderStateModel.value.coerceIn(min, max)
    val rawProgressFraction = ((currentVal - min) / range).coerceIn(0f, 1f)

    val progressFraction by animateFloatAsState(
        targetValue = rawProgressFraction,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 400f
        ),
        label = "VolumeSliderProgress"
    )

    val showCallSlider = remember {
        Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.SHOW_CALL_VOLUME_SLIDER,
            1,
            UserHandle.USER_CURRENT
        ) == 1
    }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val telecomManager = remember { context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager }
    val isInCall = audioManager.mode == AudioManager.MODE_IN_CALL ||
        audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
        audioManager.mode == AudioManager.MODE_RINGTONE ||
        (telecomManager?.isInCall == true)

    val sliderWidth = if (isLandscape) 56.dp else 62.dp
    val sliderHeight = if (isLandscape) 165.dp else 232.dp
    val sliderCornerRadius = if (isLandscape) 28.dp else 31.dp
    val iconSize = if (isLandscape) 22.dp else 26.dp
    val iconBottomPadding = if (isLandscape) 12.dp else 18.dp
    val topPadding = if (isLandscape) 10.dp else 15.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .wrapContentSize()
            .padding(vertical = if (isLandscape) 2.dp else 4.dp, horizontal = 2.dp)
    ) {
        // Dual / Secondary Volume Slider (shown ONLY when in active call)
        if (showCallSlider && isInCall) {
            val isMainVoiceCall = audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION ||
                sliderStateModel.label.contains("call", ignoreCase = true) ||
                sliderStateModel.label.contains("llamada", ignoreCase = true) ||
                sliderStateModel.label.contains("voz", ignoreCase = true) ||
                sliderStateModel.label.contains("voice", ignoreCase = true) ||
                sliderStateModel.label.contains("comunic", ignoreCase = true)

            val targetSecondaryStream = if (isMainVoiceCall) {
                AudioManager.STREAM_MUSIC
            } else {
                AudioManager.STREAM_VOICE_CALL
            }

            HyperOSSecondaryVolumeVerticalCapsule(
                context = context,
                targetStream = targetSecondaryStream,
                isLandscape = isLandscape
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 10.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            // Main Volume Slider Capsule
            Box(
                modifier = Modifier
                    .size(width = sliderWidth, height = sliderHeight)
                    .clip(RoundedCornerShape(sliderCornerRadius))
                    .background(Color(0x8A1A1A1A))
                    .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(sliderCornerRadius))
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
                        .clip(RoundedCornerShape(sliderCornerRadius))
                        .background(Color.White)
                )

                // Top 3 dots ••• (Expand / Sound Settings button)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topPadding)
                        .clickable {
                            viewModel.openVolumePanel()
                        }
                        .padding(4.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 3.5.dp else 4.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB0B0B0))
                        )
                    }
                }

                val isHeadsetOrBt = remember(sliderStateModel, progressFraction) {
                    try {
                        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        devices.any { d ->
                            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            d.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            d.type == AudioDeviceInfo.TYPE_BLE_BROADCAST ||
                            d.type == AudioDeviceInfo.TYPE_HEARING_AID ||
                            d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            d.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        } || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn || audioManager.isWiredHeadsetOn
                    } catch (_: Exception) {
                        false
                    }
                }

                // Dynamic Speaker / Headphone Icon
                val currentIconRes = when {
                    isHeadsetOrBt -> {
                        if (progressFraction <= 0.01f || sliderStateModel.isDisabled) {
                            R.drawable.ic_hyperos_headphone_mute
                        } else {
                            R.drawable.ic_hyperos_headphone
                        }
                    }
                    progressFraction <= 0.01f || sliderStateModel.isDisabled -> R.drawable.ic_hyperos_speaker_mute
                    progressFraction < 0.34f -> R.drawable.ic_hyperos_speaker_low
                    progressFraction < 0.67f -> R.drawable.ic_hyperos_speaker_mid
                    else -> R.drawable.ic_hyperos_speaker_high
                }

                val iconTint = if (progressFraction >= 0.20f) Color(0xFF2A72E5) else Color(0xFFEEEEEE)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = iconBottomPadding)
                        .size(iconSize)
                ) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(id = currentIconRes),
                        contentDescription = sliderStateModel.label,
                        tint = iconTint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            // HyperOS Ringer Mode Toggle Button
            HyperOSRingerPill(context = context, isLandscape = isLandscape)

            // HyperOS DND Mode Toggle Button
            HyperOSDndPill(context = context, isLandscape = isLandscape)
        }
    }
}

/**
 * HyperOS Secondary Volume Vertical Capsule for Dual Sliders in Call Mode.
 * If main slider controls Call, this secondary slider controls Media (STREAM_MUSIC).
 * If main slider controls Media, this secondary slider controls Call (STREAM_VOICE_CALL).
 */
@Composable
private fun HyperOSSecondaryVolumeVerticalCapsule(
    context: Context,
    targetStream: Int,
    isLandscape: Boolean = false
) {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val minVol = remember(targetStream) { audioManager.getStreamMinVolume(targetStream).toFloat() }
    val maxVol = remember(targetStream) { audioManager.getStreamMaxVolume(targetStream).toFloat() }
    val range = (maxVol - minVol).coerceAtLeast(1f)

    var currentVol by remember(targetStream) {
        mutableStateOf(audioManager.getStreamVolume(targetStream).toFloat())
    }
    val rawProgress = ((currentVol - minVol) / range).coerceIn(0f, 1f)
    val progressFraction by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 400f
        ),
        label = "SecondaryVolumeSliderProgress"
    )
    val iconTint = if (progressFraction >= 0.20f) Color(0xFF2A72E5) else Color(0xFFEEEEEE)

    val capsuleWidth = if (isLandscape) 28.dp else 31.dp
    val capsuleHeight = if (isLandscape) 165.dp else 232.dp
    val capsuleCorner = if (isLandscape) 14.dp else 15.5.dp
    val iconSize = if (isLandscape) 16.dp else 18.dp

    val iconRes = if (targetStream == AudioManager.STREAM_VOICE_CALL) {
        R.drawable.ic_hyperos_call_volume
    } else {
        if (progressFraction <= 0.05f) R.drawable.ic_hyperos_speaker_mute else R.drawable.ic_hyperos_speaker_mid
    }
    val iconDesc = if (targetStream == AudioManager.STREAM_VOICE_CALL) "Call Volume" else "Media Volume"

    Box(
        modifier = Modifier
            .size(width = capsuleWidth, height = capsuleHeight)
            .clip(RoundedCornerShape(capsuleCorner))
            .background(Color(0x8A1A1A1A))
            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(capsuleCorner))
            .pointerInput(minVol, maxVol, range, targetStream) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        val touchY = change.position.y
                        val heightPx = size.height.toFloat()
                        val frac = 1f - (touchY / heightPx).coerceIn(0f, 1f)
                        val targetVal = (minVol + frac * range).roundToInt()
                        currentVol = targetVal.toFloat()
                        try {
                            audioManager.setStreamVolume(targetStream, targetVal, 0)
                        } catch (_: Exception) {}
                    }
                )
            }
            .pointerInput(minVol, maxVol, range, targetStream) {
                detectTapGestures { offset ->
                    val touchY = offset.y
                    val heightPx = size.height.toFloat()
                    val frac = 1f - (touchY / heightPx).coerceIn(0f, 1f)
                    val targetVal = (minVol + frac * range).roundToInt()
                    currentVol = targetVal.toFloat()
                    try {
                        audioManager.setStreamVolume(targetStream, targetVal, 0)
                    } catch (_: Exception) {}
                }
            }
    ) {
        // White solid progress fill from bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(progressFraction)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(capsuleCorner))
                .background(Color.White)
        )

        // Bottom Icon
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isLandscape) 10.dp else 16.dp)
                .size(iconSize)
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(id = iconRes),
                contentDescription = iconDesc,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * HyperOS Left Sound Assistant / Per-App Volume Floating Button.
 */

@Composable
private fun HyperOSRingerPill(context: Context, isLandscape: Boolean = false) {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var ringerMode by remember { mutableStateOf(audioManager.ringerModeInternal) }
    val isSilentOrVibrate = ringerMode != AudioManager.RINGER_MODE_NORMAL

    val pillWidth = if (isLandscape) 56.dp else 62.dp
    val pillHeight = if (isLandscape) 36.dp else 48.dp
    val pillCorner = if (isLandscape) 18.dp else 24.dp
    val iconSize = if (isLandscape) 20.dp else 24.dp

    val pillBackground = if (isSilentOrVibrate) Color.White else Color(0x8A1A1A1A)
    val pillModifier = Modifier
        .size(width = pillWidth, height = pillHeight)
        .clip(RoundedCornerShape(pillCorner))
        .background(pillBackground)
        .then(
            if (!isSilentOrVibrate) {
                Modifier.border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(pillCorner))
            } else {
                Modifier
            }
        )
        .clickable {
            val nextMode = when (ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            audioManager.ringerModeInternal = nextMode
            ringerMode = nextMode
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier = pillModifier
    ) {
        val iconRes = when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> R.drawable.ic_hyperos_bell_normal
            AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_volume_ringer_vibrate
            else -> R.drawable.ic_hyperos_bell_mute
        }
        val iconTint = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> Color(0xFFFF453A) // Red for silent bell
            AudioManager.RINGER_MODE_VIBRATE -> Color(0xFF2A72E5) // Blue for vibrate
            else -> Color.White
        }
        androidx.compose.material3.Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Ringer Mode",
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun HyperOSDndPill(context: Context, isLandscape: Boolean = false) {
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    var isDndActive by remember {
        mutableStateOf(
            Settings.Global.getInt(context.contentResolver, Settings.Global.ZEN_MODE, Settings.Global.ZEN_MODE_OFF) != Settings.Global.ZEN_MODE_OFF
        )
    }

    val pillWidth = if (isLandscape) 56.dp else 62.dp
    val pillHeight = if (isLandscape) 36.dp else 48.dp
    val pillCorner = if (isLandscape) 18.dp else 24.dp
    val iconSize = if (isLandscape) 20.dp else 24.dp

    val pillBackground = if (isDndActive) Color.White else Color(0x8A1A1A1A)
    val pillModifier = Modifier
        .size(width = pillWidth, height = pillHeight)
        .clip(RoundedCornerShape(pillCorner))
        .background(pillBackground)
        .then(
            if (!isDndActive) {
                Modifier.border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(pillCorner))
            } else {
                Modifier
            }
        )
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = pillModifier
    ) {
        val iconTint = if (isDndActive) Color(0xFF5B60F6) else Color.White
        androidx.compose.material3.Icon(
            painter = painterResource(id = R.drawable.ic_hyperos_dnd_moon),
            contentDescription = "Do Not Disturb",
            tint = iconTint,
            modifier = Modifier.size(iconSize)
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
