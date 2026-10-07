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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextOverflow
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    var isVisible by remember { mutableStateOf(false) }

    var useMonet by remember {
        mutableStateOf(
            try {
                android.provider.Settings.System.getIntForUser(
                    context.contentResolver,
                    "hyperos_volume_use_monet",
                    1,
                    android.os.UserHandle.USER_CURRENT
                ) == 1
            } catch (e: Exception) { true }
        )
    }

    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                useMonet = try {
                    android.provider.Settings.System.getIntForUser(
                        context.contentResolver,
                        "hyperos_volume_use_monet",
                        1,
                        android.os.UserHandle.USER_CURRENT
                    ) == 1
                } catch (e: Exception) { true }
            }
        }
        val uri = android.provider.Settings.System.getUriFor("hyperos_volume_use_monet")
        try {
            context.contentResolver.registerContentObserver(uri, false, observer, android.os.UserHandle.USER_CURRENT)
        } catch (e: Exception) {}
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {}
        }
    }

    BackHandler {
        isVisible = false
        onDismissRequest()
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val topPadding = if (isLandscape) 10.dp else 64.dp
    val bottomPadding = if (isLandscape) 10.dp else 24.dp
    val verticalInnerPadding = if (isLandscape) 12.dp else 20.dp
    val cardCornerRadius = if (isLandscape) 28.dp else 36.dp
    val cardWidth = if (isLandscape) 360.dp else 340.dp

    val cardBg = if (useMonet) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    } else {
        if (isDark) Color(0xEE222327) else Color(0xEEF5F5F7)
    }

    // Outer full-screen container with dismiss-on-tap-outside
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isVisible = false
                onDismissRequest()
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
            // Floating Card (HyperOS full-sized aesthetic)
            Box(
                modifier = Modifier
                    .padding(top = topPadding, end = if (isLandscape) 20.dp else 16.dp, bottom = bottomPadding)
                    .width(cardWidth)
                    .clip(RoundedCornerShape(cardCornerRadius))
                    .background(cardBg)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(cardCornerRadius))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Consume taps inside the card so tapping inside does NOT dismiss
                    }
                    .padding(horizontal = 16.dp, vertical = verticalInnerPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top 4 Vertical Volume Sliders
                    HyperOSVerticalSlidersRow(
                        layout = layout,
                        audioManager = audioManager,
                        view = view,
                        isLandscape = isLandscape,
                        useMonet = useMonet
                    )

                    Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 18.dp))

                    // Bottom Timers: Silent Mode & DND
                    HyperOSTimersSection(
                        audioManager = audioManager,
                        notificationManager = notificationManager,
                        view = view,
                        isLandscape = isLandscape,
                        useMonet = useMonet
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
    isLandscape: Boolean,
    useMonet: Boolean,
    modifier: Modifier = Modifier,
) {
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
            isLandscape = isLandscape,
            useMonet = useMonet,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 2. Notificaciones (STREAM_NOTIFICATION)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_NOTIFICATION,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.ic_hyperos_bell_normal,
            view = view,
            isLandscape = isLandscape,
            useMonet = useMonet,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 3. Alarma (STREAM_ALARM)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_ALARM,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.ic_alarm,
            view = view,
            isLandscape = isLandscape,
            useMonet = useMonet,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // 4. Timbre / Llamada (STREAM_RING)
        HyperOSVerticalStreamSlider(
            streamType = AudioManager.STREAM_RING,
            audioManager = audioManager,
            fallbackIconRes = R.drawable.ic_hyperos_call_volume,
            view = view,
            isLandscape = isLandscape,
            useMonet = useMonet,
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
    isLandscape: Boolean,
    useMonet: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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

    var currentVolume by remember(streamType) {
        mutableIntStateOf(
            try {
                audioManager.getStreamVolume(streamType)
            } catch (e: Exception) {
                (maxVolume / 2)
            }
        )
    }

    DisposableEffect(streamType, context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val st = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (st == streamType || st == -1) {
                        try {
                            currentVolume = audioManager.getStreamVolume(streamType)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
        val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            context.registerReceiver(receiver, filter)
        } catch (e: Exception) {}
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val fraction = remember(currentVolume, minVolume, maxVolume) {
        ((currentVolume - minVolume).toFloat() / (maxVolume - minVolume).toFloat()).coerceIn(0f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "HyperOSVolumeFraction_$streamType"
    )

    val sliderHeight = if (isLandscape) 140.dp else 210.dp
    val bottomPadding = if (isLandscape) 8.dp else 14.dp
    val iconBadgeSize = if (isLandscape) 34.dp else 44.dp
    val iconSize = if (isLandscape) 18.dp else 24.dp

    Column(
        modifier = modifier
            .height(sliderHeight)
            .clip(RoundedCornerShape(if (isLandscape) 20.dp else 26.dp))
            .background(Color(0x597F7F7F)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(minVolume, maxVolume, streamType) {
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
                .pointerInput(minVolume, maxVolume, streamType) {
                    detectTapGestures { offset ->
                        val height = size.height
                        // Only handle tap if not touching the bottom icon area
                        if (offset.y < (height - 38f)) {
                            val calculatedFraction = 1f - (offset.y / height).coerceIn(0f, 1f)
                            val targetVol = (minVolume + calculatedFraction * (maxVolume - minVolume)).roundToInt()
                            if (targetVol != currentVolume) {
                                currentVolume = targetVol
                                try {
                                    audioManager.setStreamVolume(streamType, targetVol, 0)
                                } catch (e: Exception) {}
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            }
                        }
                    }
                }
        ) {
            val sliderFillColor = if (useMonet) MaterialTheme.colorScheme.primary else Color.White
            val activeIconColor = if (useMonet) MaterialTheme.colorScheme.onPrimary else Color(0xFF2A72E5)

            // Active Filled Level (grows from bottom to top)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                    .background(sliderFillColor)
            )

            // Bottom Icon badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding)
                    .size(iconBadgeSize)
                    .pointerInput(minVolume, maxVolume, streamType, currentVolume) {
                        detectTapGestures {
                            // Toggle Mute / Max on icon click
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val targetVol = if (currentVolume > minVolume) minVolume else (maxVolume * 0.7f).roundToInt()
                            currentVolume = targetVol
                            try {
                                audioManager.setStreamVolume(streamType, targetVol, 0)
                            } catch (e: Exception) {}
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconColor = if (animatedFraction > 0.18f) {
                    activeIconColor
                } else {
                    Color.White
                }

                Icon(
                    painter = painterResource(id = fallbackIconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(iconSize)
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
    isLandscape: Boolean,
    useMonet: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isSilentActive = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
    val isDndActive = remember(context) {
        try {
            android.provider.Settings.Global.getInt(
                context.contentResolver,
                android.provider.Settings.Global.ZEN_MODE,
                android.provider.Settings.Global.ZEN_MODE_OFF
            ) != android.provider.Settings.Global.ZEN_MODE_OFF
        } catch (e: Exception) { false }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 12.dp)
    ) {
        // 1. Silent Mode Timer Row (30m to 8h) with Monet adaptive fill
        HyperOSTimerRow(
            settingKey = "hyperos_silent",
            isSystemActive = isSilentActive,
            iconRes = R.drawable.ic_hyperos_bell_normal,
            activeIconRes = R.drawable.ic_hyperos_bell_mute,
            label = "Silenciar dispositivo",
            defaultTimerText = "Deslice para establecer un temporizador",
            minMinutes = 30,
            maxMinutes = 480, // 8 hours
            activeColor = Color(0xFFFF3B30),
            isLandscape = isLandscape,
            useMonet = useMonet,
            onToggle = { isActive ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                try {
                    audioManager.ringerModeInternal = if (isActive) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                } catch (e: Exception) {}
            },
            onTimerChanged = { minutes ->
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            }
        )

        // 2. DND (Do Not Disturb) Timer Row (30m to 8h) with Monet adaptive fill
        HyperOSTimerRow(
            settingKey = "hyperos_dnd",
            isSystemActive = isDndActive,
            iconRes = R.drawable.ic_hyperos_dnd_moon,
            activeIconRes = R.drawable.ic_hyperos_dnd_moon,
            label = "No molestar",
            defaultTimerText = "Deslice para establecer un temporizador",
            minMinutes = 30,
            maxMinutes = 480, // 8 hours
            activeColor = Color(0xFF7C4DFF),
            isLandscape = isLandscape,
            useMonet = useMonet,
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
    settingKey: String,
    isSystemActive: Boolean,
    iconRes: Int,
    activeIconRes: Int,
    label: String,
    defaultTimerText: String,
    minMinutes: Int,
    maxMinutes: Int,
    activeColor: Color,
    isLandscape: Boolean,
    useMonet: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimerChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // Load stored timer from persistent storage
    val now = System.currentTimeMillis()
    val storedEnd = remember(settingKey) {
        try {
            android.provider.Settings.System.getLong(contentResolver, "${settingKey}_timer_end", 0L)
        } catch (e: Exception) { 0L }
    }
    val storedInitial = remember(settingKey) {
        try {
            android.provider.Settings.System.getLong(contentResolver, "${settingKey}_timer_initial", 0L)
        } catch (e: Exception) { 0L }
    }

    val hasValidTimer = isSystemActive && storedEnd > now && storedInitial > 0L
    val initialRemainingSecs = if (hasValidTimer) (storedEnd - now) / 1000L else 0L

    var isActive by remember(isSystemActive) { mutableStateOf(isSystemActive) }
    var remainingSeconds by remember(initialRemainingSecs) { mutableStateOf(initialRemainingSecs) }
    var initialTotalSeconds by remember(storedInitial) { mutableStateOf(if (hasValidTimer) storedInitial else 0L) }
    var isCountingDown by remember(hasValidTimer) { mutableStateOf(hasValidTimer) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val maxSeconds = remember(maxMinutes) { (maxMinutes * 60f).coerceAtLeast(1f) }

    // Real-time 1-second ticker countdown
    LaunchedEffect(isCountingDown, remainingSeconds) {
        if (isCountingDown && remainingSeconds > 0L) {
            kotlinx.coroutines.delay(1000L)
            remainingSeconds -= 1L
            if (remainingSeconds <= 0L) {
                isCountingDown = false
                isActive = false
                dragProgress = 0f
                try {
                    android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_end", 0L)
                    android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_initial", 0L)
                } catch (e: Exception) {}
                onToggle(false)
            }
        }
    }

    // Dynamic progress fraction (shrinks progressively as countdown proceeds)
    val liveFraction = remember(isCountingDown, remainingSeconds, maxSeconds, dragProgress) {
        if (isCountingDown && remainingSeconds > 0L) {
            (remainingSeconds.toFloat() / maxSeconds).coerceIn(0f, 1f)
        } else {
            dragProgress
        }
    }

    val animatedLiveFraction by animateFloatAsState(
        targetValue = liveFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "TimerProgressFraction_$settingKey"
    )

    val displayTimerText = remember(isCountingDown, remainingSeconds, dragProgress, defaultTimerText) {
        if (isCountingDown && remainingSeconds > 0L) {
            val hours = remainingSeconds / 3600L
            val mins = (remainingSeconds % 3600L) / 60L
            val secs = remainingSeconds % 60L
            when {
                hours > 0L -> "Temporizador: ${hours}h ${mins}m ${secs}s"
                mins > 0L -> "Temporizador: ${mins}m ${secs}s"
                else -> "Temporizador: ${secs}s"
            }
        } else if (dragProgress > 0.02f) {
            val totalMins = (((minMinutes + dragProgress * (maxMinutes - minMinutes)) / 15).roundToInt() * 15).coerceIn(minMinutes, maxMinutes)
            val hours = totalMins / 60
            val mins = totalMins % 60
            when {
                hours > 0 -> "Temporizador: ${hours}h ${mins}m 00s"
                else -> "Temporizador: ${mins}m 00s"
            }
        } else {
            defaultTimerText
        }
    }

    val rowHeight = if (isLandscape) 40.dp else 56.dp
    val buttonSize = if (isLandscape) 40.dp else 56.dp
    val iconSize = if (isLandscape) 18.dp else 24.dp

    val monetPrimary = MaterialTheme.colorScheme.primary
    val monetOnPrimary = MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left Circular Button (HyperOS style: Red for Silent, Purple for DND)
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor
                    else Color(0x597F7F7F)
                )
                .clickable {
                    if (isActive) {
                        isActive = false
                        isCountingDown = false
                        remainingSeconds = 0L
                        initialTotalSeconds = 0L
                        dragProgress = 0f
                        try {
                            android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_end", 0L)
                            android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_initial", 0L)
                        } catch (e: Exception) {}
                        onToggle(false)
                    } else {
                        isActive = true
                        onToggle(true)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isActive) activeIconRes else iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }

        // Right Capsule Slider for Timer with Active Color Fill Bar
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(buttonSize)
                .clip(RoundedCornerShape(buttonSize / 2))
                .background(Color(0x597F7F7F))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val width = size.width
                            dragProgress = (offset.x / width).coerceIn(0f, 1f)
                            isCountingDown = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val width = size.width
                            dragProgress = (change.position.x / width).coerceIn(0f, 1f)
                            isCountingDown = false
                        },
                        onDragEnd = {
                            if (dragProgress > 0.05f) {
                                val totalMins = (((minMinutes + dragProgress * (maxMinutes - minMinutes)) / 15).roundToInt() * 15).coerceIn(minMinutes, maxMinutes)
                                val secs = totalMins * 60L
                                remainingSeconds = secs
                                initialTotalSeconds = secs
                                isCountingDown = true
                                isActive = true
                                val endMillis = System.currentTimeMillis() + (secs * 1000L)
                                try {
                                    android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_end", endMillis)
                                    android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_initial", secs)
                                } catch (e: Exception) {}
                                onToggle(true)
                                onTimerChanged(totalMins)
                            } else {
                                dragProgress = 0f
                                remainingSeconds = 0L
                                initialTotalSeconds = 0L
                                isCountingDown = false
                                isActive = false
                                try {
                                    android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_end", 0L)
                                    android.provider.Settings.System.putLong(contentResolver, "${settingKey}_timer_initial", 0L)
                                } catch (e: Exception) {}
                                onToggle(false)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val timerFillColor = if (useMonet) MaterialTheme.colorScheme.primary else Color(0xFF2A72E5)
            val timerTextColor = if (animatedLiveFraction > 0.2f) {
                if (useMonet) MaterialTheme.colorScheme.onPrimary else Color.White
            } else {
                Color.White
            }

            // Dynamic Fill Level (Progressively shrinks from right to left as countdown proceeds)
            if (animatedLiveFraction > 0.005f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(animatedLiveFraction)
                        .background(timerFillColor)
                )
            }

            // Text Label inside the capsule
            Text(
                text = displayTimerText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (isLandscape) 11.sp else 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = timerTextColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            )
        }
    }
}
