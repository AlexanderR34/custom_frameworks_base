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

package com.android.systemui.statusbar.chips.download.domain.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable

/** Represents the state of an active download for the status bar chip. */
sealed interface DownloadChipModel {
    fun logString(): String

    /** No download is currently active. */
    data object Inactive : DownloadChipModel {
        override fun logString(): String = "Inactive"
    }

    /** An active download with progress is currently ongoing. */
    data class Active(
        val notificationKey: String,
        val packageName: String,
        val appName: CharSequence?,
        val title: CharSequence?,
        val progress: Int,
        val max: Int,
        val isIndeterminate: Boolean,
        val progressText: CharSequence? = null,
        val dominantColor: Int? = null,
        val appIcon: Drawable?,
        val intent: PendingIntent?,
    ) : DownloadChipModel {
        override fun logString(): String =
            "Active(key=$notificationKey, pkg=$packageName, progress=$progress/$max, indeterminate=$isIndeterminate, text=$progressText)"
    }
}
