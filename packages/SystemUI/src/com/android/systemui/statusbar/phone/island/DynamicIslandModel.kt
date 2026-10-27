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

package com.android.systemui.statusbar.phone.island

import android.app.PendingIntent
import android.graphics.Bitmap

enum class IslandActivityType {
    NONE,
    MEDIA,
    CALL,
    TIMER,
    CHARGING,
    SCREEN_RECORD,
    NAVIGATION,
    DOWNLOAD,
    TORCH,
    HOTSPOT,
    CAST,
    PRIVACY,
    CLIPBOARD,
    TRANSIENT_BANNER
}

data class IslandMediaState(
    val hasActiveMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val packageName: String = "",
    val dominantColor: Int? = null,
)

data class IslandCallState(
    val isActive: Boolean = false,
    val contactName: String = "",
    val startTimeMs: Long = 0L,
    val isMuted: Boolean = false,
    val intent: PendingIntent? = null,
)

data class IslandTimerState(
    val isActive: Boolean = false,
    val label: String = "",
    val remainingSeconds: Int = 0,
    val isPaused: Boolean = false,
    val resetIntent: PendingIntent? = null,
    val pauseResumeIntent: PendingIntent? = null,
)

data class IslandChargingState(
    val isCharging: Boolean = false,
    val level: Int = 100,
    val watts: Double = 0.0,
    val isNewConnection: Boolean = false,
    val title: String = "Cargando",
)

data class IslandRecordState(
    val isRecording: Boolean = false,
    val startTimeMs: Long = 0L,
    val stopIntent: PendingIntent? = null,
)

data class IslandNavigationState(
    val isActive: Boolean = false,
    val maneuver: String = "TURN_RIGHT", // TURN_LEFT, TURN_RIGHT, STRAIGHT, UTURN
    val distanceText: String = "",
    val instructionText: String = "",
    val etaText: String = "",
    val openIntent: PendingIntent? = null,
)

data class IslandDownloadState(
    val isDownloading: Boolean = false,
    val fileName: String = "",
    val progress: Int = 0,
    val speedText: String = "",
    val totalSizeText: String = "",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val isCancelled: Boolean = false,
    val cancelIntent: PendingIntent? = null,
    val sourcePackage: String = "",
)

data class IslandTorchState(
    val isOn: Boolean = false,
    val isIntro: Boolean = false,
)

data class IslandHotspotState(
    val isEnabled: Boolean = false,
    val clientCount: Int = 0,
    val showIntroBanner: Boolean = false,
)

data class IslandCastState(
    val isCasting: Boolean = false,
    val deviceName: String = "",
    val castType: String = "",
)

data class IslandPrivacyState(
    val isCameraInUse: Boolean = false,
    val isMicInUse: Boolean = false,
    val isLocationInUse: Boolean = false,
    val appName: String = "",
) {
    val hasAnyPrivacyActive: Boolean get() = isCameraInUse || isMicInUse || isLocationInUse
}

data class IslandClipboardState(
    val isActive: Boolean = false,
    val copiedText: String = "",
)

data class IslandBannerState(
    val isActive: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val badge: String? = null,
    val iconRes: Int = 0,
    val tintColor: Int = 0xFF38BDF8.toInt(),
)
