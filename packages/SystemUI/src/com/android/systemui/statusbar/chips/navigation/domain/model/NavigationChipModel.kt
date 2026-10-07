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

package com.android.systemui.statusbar.chips.navigation.domain.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable

/** Represents the state of turn-by-turn navigation for the status bar chip. */
sealed interface NavigationChipModel {
    fun logString(): String

    /** No navigation session is active. */
    data object Inactive : NavigationChipModel {
        override fun logString(): String = "Inactive"
    }

    /** Navigation is currently active. */
    data class Active(
        val notificationKey: String,
        val packageName: String,
        val appName: CharSequence?,
        val directionText: CharSequence?,
        val distanceText: CharSequence?,
        val etaText: CharSequence?,
        val maneuverIcon: Drawable?,
        val appIcon: Drawable?,
        val intent: PendingIntent?,
    ) : NavigationChipModel {
        override fun logString(): String =
            "Active(pkg=$packageName, dist=$distanceText, dir=$directionText, eta=$etaText)"
    }
}
