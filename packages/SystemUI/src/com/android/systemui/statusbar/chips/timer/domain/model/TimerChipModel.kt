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

package com.android.systemui.statusbar.chips.timer.domain.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable

/** Represents the state of an active countdown timer for the status bar chip. */
sealed interface TimerChipModel {
    fun logString(): String

    /** No timer is currently running. */
    data object Inactive : TimerChipModel {
        override fun logString(): String = "Inactive"
    }

    /** An active timer or stopwatch is running. */
    data class Active(
        val notificationKey: String,
        val packageName: String,
        val eventTimeRealtimeMs: Long,
        val isCountdown: Boolean = true,
        val isPaused: Boolean = false,
        val pausedDurationMs: Long = 0L,
        val appIcon: Drawable? = null,
        val intent: PendingIntent? = null,
    ) : TimerChipModel {
        override fun logString(): String =
            "Active(pkg=$packageName, eventTime=$eventTimeRealtimeMs, isCountdown=$isCountdown, isPaused=$isPaused)"
    }
}
