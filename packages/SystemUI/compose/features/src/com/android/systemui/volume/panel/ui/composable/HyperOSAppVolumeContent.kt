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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.android.compose.theme.PlatformTheme
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.AppVolume
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

@Composable
fun HyperOSAppVolumeContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val packageManager = remember(context) { context.packageManager }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            } catch (_: Exception) { true }
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
                } catch (_: Exception) { true }
            }
        }
        val uri = android.provider.Settings.System.getUriFor("hyperos_volume_use_monet")
        try {
            context.contentResolver.registerContentObserver(uri, false, observer, android.os.UserHandle.USER_CURRENT)
        } catch (_: Exception) {}
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {}
        }
    }

    BackHandler {
        isVisible = false
        onDismiss()
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    // Registered apps playing volume - periodically polled for real-time reactivity
    var activeAppVolumes by remember {
        mutableStateOf(
            try {
                audioManager.listAppVolumes().filter { it.packageName != "android" && it.packageName != "com.android.systemui" }
            } catch (_: Exception) {
                emptyList<AppVolume>()
            }
        )
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            try {
                val currentList = audioManager.listAppVolumes().filter { it.packageName != "android" && it.packageName != "com.android.systemui" }
                if (currentList.map { "${it.packageName}:${it.volume}:${it.isMuted}" } != 
                    activeAppVolumes.map { "${it.packageName}:${it.volume}:${it.isMuted}" }) {
                    activeAppVolumes = currentList
                }
            } catch (_: Exception) {}
            delay(200)
        }
    }

    val navBarStart = WindowInsets.navigationBars.asPaddingValues().calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)
    val navBarEnd = WindowInsets.navigationBars.asPaddingValues().calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)

    PlatformTheme {
        // Completely transparent backdrop with tap-to-dismiss outside
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isVisible = false
                    onDismiss()
                }
                .then(
                    if (isLandscape) {
                        Modifier.padding(start = navBarStart, end = navBarEnd)
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn(
                    initialScale = 0.85f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = scaleOut(
                    targetScale = 0.85f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeOut()
            ) {
                val sliderWidth = if (isLandscape) 56.dp else 64.dp
                val sliderHeight = if (isLandscape) 170.dp else 220.dp
                val spacing = if (isLandscape) 16.dp else 18.dp

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // 1. General Media Volume Slider (Vertical Capsule)
                    HyperOSMediaVolumeSlider(
                        audioManager = audioManager,
                        sliderWidth = sliderWidth,
                        sliderHeight = sliderHeight,
                        isLandscape = isLandscape,
                        useMonet = useMonet,
                        view = view
                    )

                    // 2. Individual App Volume Sliders (Vertical Capsule)
                    for (appVol in activeAppVolumes) {
                        HyperOSSingleAppVolumeSlider(
                            appVolume = appVol,
                            audioManager = audioManager,
                            packageManager = packageManager,
                            sliderWidth = sliderWidth,
                            sliderHeight = sliderHeight,
                            isLandscape = isLandscape,
                            useMonet = useMonet,
                            view = view
                        )
                    }
                }
            }
        }
    }
}

/* ========================================================================== */
/*                             VERTICAL SLIDERS                               */
/* ========================================================================== */

@Composable
private fun HyperOSMediaVolumeSlider(
    audioManager: AudioManager,
    sliderWidth: androidx.compose.ui.unit.Dp,
    sliderHeight: androidx.compose.ui.unit.Dp,
    isLandscape: Boolean,
    useMonet: Boolean,
    view: android.view.View,
) {
    val context = LocalContext.current
    val streamType = AudioManager.STREAM_MUSIC
    val maxVol = remember { audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1) }
    val minVol = remember { audioManager.getStreamMinVolume(streamType) }

    var currentVol by remember {
        mutableIntStateOf(
            try {
                audioManager.getStreamVolume(streamType)
            } catch (_: Exception) {
                maxVol / 2
            }
        )
    }

    var isDragging by remember { mutableStateOf(false) }

    // Listen to real-time volume changes from hardware keys, bluetooth, etc.
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val st = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (st == streamType || st == -1) {
                        if (!isDragging) {
                            try {
                                currentVol = audioManager.getStreamVolume(streamType)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        try {
            context.registerReceiver(receiver, filter)
        } catch (_: Exception) {}
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    // Periodic polling to keep media volume in absolute sync
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!isDragging) {
                try {
                    val latest = audioManager.getStreamVolume(streamType)
                    if (latest != currentVol) {
                        currentVol = latest
                    }
                } catch (_: Exception) {}
            }
            delay(150)
        }
    }

    val fraction = ((currentVol - minVol).toFloat() / (maxVol - minVol).toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "HyperOSMediaFraction"
    )

    val cornerRadius = if (isLandscape) 28.dp else 32.dp

    Column(
        modifier = Modifier
            .width(sliderWidth)
            .height(sliderHeight)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0x597F7F7F))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Consume click so tapping on the slider body doesn't dismiss
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(minVol, maxVol) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val h = size.height
                            val calcFrac = 1f - (offset.y / h).coerceIn(0f, 1f)
                            val target = (minVol + calcFrac * (maxVol - minVol)).roundToInt()
                            if (target != currentVol) {
                                currentVol = target
                                try {
                                    audioManager.setStreamVolume(streamType, target, 0)
                                } catch (_: Exception) {}
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val h = size.height
                            val calcFrac = 1f - (change.position.y / h).coerceIn(0f, 1f)
                            val target = (minVol + calcFrac * (maxVol - minVol)).roundToInt()
                            if (target != currentVol) {
                                currentVol = target
                                try {
                                    audioManager.setStreamVolume(streamType, target, 0)
                                } catch (_: Exception) {}
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            }
                        }
                    )
                }
                .pointerInput(minVol, maxVol) {
                    detectTapGestures { offset ->
                        val h = size.height
                        // If not tapping directly on the bottom icon badge
                        if (offset.y < (h - 40f)) {
                            val calcFrac = 1f - (offset.y / h).coerceIn(0f, 1f)
                            val target = (minVol + calcFrac * (maxVol - minVol)).roundToInt()
                            if (target != currentVol) {
                                currentVol = target
                                try {
                                    audioManager.setStreamVolume(streamType, target, 0)
                                } catch (_: Exception) {}
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            }
                        }
                    }
                }
        ) {
            val mediaFillColor = if (useMonet) MaterialTheme.colorScheme.primary else Color.White

            // Filled level (from bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                    .background(mediaFillColor)
            )

            // Bottom Speaker Icon badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) 10.dp else 14.dp)
                    .size(if (isLandscape) 36.dp else 40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val target = if (currentVol > minVol) minVol else (maxVol * 0.7f).roundToInt()
                        currentVol = target
                        try {
                            audioManager.setStreamVolume(streamType, target, 0)
                        } catch (_: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                val iconTint = if (animatedFraction > 0.18f) {
                    if (useMonet) MaterialTheme.colorScheme.onPrimary else Color(0xFF0D84FF)
                } else Color.White

                Icon(
                    painter = painterResource(id = R.drawable.ic_hyperos_speaker_mid),
                    contentDescription = "Media",
                    tint = iconTint,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 24.dp)
                )
            }
        }
    }
}

@Composable
private fun HyperOSSingleAppVolumeSlider(
    appVolume: AppVolume,
    audioManager: AudioManager,
    packageManager: PackageManager,
    sliderWidth: androidx.compose.ui.unit.Dp,
    sliderHeight: androidx.compose.ui.unit.Dp,
    isLandscape: Boolean,
    useMonet: Boolean,
    view: android.view.View,
) {
    var volumeFraction by remember(appVolume.packageName) { mutableFloatStateOf(appVolume.volume.coerceIn(0f, 1f)) }
    var isDragging by remember { mutableStateOf(false) }

    // Keep app volume in sync with latest changes from system/audio track
    LaunchedEffect(appVolume.volume) {
        if (!isDragging) {
            volumeFraction = appVolume.volume.coerceIn(0f, 1f)
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = volumeFraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "HyperOSAppVol_${appVolume.packageName}"
    )

    val appIconBitmap = remember(appVolume.packageName, packageManager) {
        try {
            val drawable = packageManager.getApplicationIcon(appVolume.packageName)
            drawableToBitmap(drawable)
        } catch (_: Exception) {
            null
        }
    }

    val cornerRadius = if (isLandscape) 28.dp else 32.dp

    Column(
        modifier = Modifier
            .width(sliderWidth)
            .height(sliderHeight)
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(cornerRadius))
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0x597F7F7F))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Consume click so tapping on the slider body doesn't dismiss
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(appVolume.packageName) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val h = size.height
                            val calcFrac = (1f - (offset.y / h)).coerceIn(0f, 1f)
                            volumeFraction = calcFrac
                            try {
                                audioManager.setAppVolume(appVolume.packageName, calcFrac)
                            } catch (_: Exception) {}
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val h = size.height
                            val calcFrac = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                            volumeFraction = calcFrac
                            try {
                                audioManager.setAppVolume(appVolume.packageName, calcFrac)
                            } catch (_: Exception) {}
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                        }
                    )
                }
                .pointerInput(appVolume.packageName) {
                    detectTapGestures { offset ->
                        val h = size.height
                        // If not tapping directly on the bottom icon badge
                        if (offset.y < (h - 40f)) {
                            val calcFrac = (1f - (offset.y / h)).coerceIn(0f, 1f)
                            volumeFraction = calcFrac
                            try {
                                audioManager.setAppVolume(appVolume.packageName, calcFrac)
                            } catch (_: Exception) {}
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                        }
                    }
                }
        ) {
            val appFillColor = if (useMonet) MaterialTheme.colorScheme.primary else Color.White

            // Filled level (from bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                    .background(appFillColor)
            )

            // Bottom App Icon badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (isLandscape) 10.dp else 14.dp)
                    .size(if (isLandscape) 36.dp else 40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        val target = if (volumeFraction > 0.05f) 0f else 1f
                        volumeFraction = target
                        try {
                            audioManager.setAppVolume(appVolume.packageName, target)
                        } catch (_: Exception) {}
                    },
                contentAlignment = Alignment.Center
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap.asImageBitmap(),
                        contentDescription = appVolume.packageName,
                        modifier = Modifier.size(if (isLandscape) 24.dp else 26.dp)
                    )
                } else {
                    val fallbackIconTint = if (animatedFraction > 0.18f) {
                        if (useMonet) MaterialTheme.colorScheme.onPrimary else Color(0xFF0D84FF)
                    } else Color.White

                    Icon(
                        painter = painterResource(id = R.drawable.ic_hyperos_speaker_mid),
                        contentDescription = appVolume.packageName,
                        tint = fallbackIconTint,
                        modifier = Modifier.size(if (isLandscape) 22.dp else 24.dp)
                    )
                }
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
