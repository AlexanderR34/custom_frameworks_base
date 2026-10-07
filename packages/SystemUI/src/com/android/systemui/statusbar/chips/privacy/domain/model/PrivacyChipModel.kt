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

package com.android.systemui.statusbar.chips.privacy.domain.model

import android.graphics.drawable.Drawable

/** Represents the privacy state (mic, camera, location sensor usage) for the status bar chip. */
sealed interface PrivacyChipModel {
    fun logString(): String

    /** No sensor is currently active. */
    data object Inactive : PrivacyChipModel {
        override fun logString(): String = "Inactive"
    }

    /** One or more apps are actively using camera, microphone, or location. */
    data class Active(
        val packageName: String,
        val appName: String,
        val appIcon: Drawable?,
        val hasMic: Boolean,
        val hasCam: Boolean,
        val hasLocation: Boolean,
    ) : PrivacyChipModel {
        override fun logString(): String =
            "Active(package=$packageName, app=$appName, mic=$hasMic, cam=$hasCam, location=$hasLocation)"
    }
}
