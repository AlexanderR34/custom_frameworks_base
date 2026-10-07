/*
 * Copyright (C) 2024-2026 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.media.domain.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/** Represents the current state of media playback for the status bar chip. */
sealed interface MediaChipModel {
    fun logString(): String

    /** No media session is active or playing. */
    data object Inactive : MediaChipModel {
        override fun logString(): String = "Inactive"
    }

    /** A media session is actively playing or recently paused. */
    data class Active(
        val packageName: String,
        val appName: CharSequence?,
        val title: CharSequence?,
        val artist: CharSequence?,
        val artwork: Bitmap?,
        val appIcon: Drawable?,
        val isPlaying: Boolean,
        val durationMs: Long,
        val playbackPositionMs: Long,
        val dominantColor: Int?,
        val intent: PendingIntent?,
    ) : MediaChipModel {
        override fun logString(): String =
            "Active(pkg=$packageName, title=$title, isPlaying=$isPlaying, pos=$playbackPositionMs/$durationMs)"
    }
}
