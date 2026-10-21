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

package com.android.systemui.statusbar.chips.flashlight.domain.model

/** Represents the state of the Flashlight for the status bar ongoing activity chip. */
sealed interface FlashlightChipModel {
    fun logString(): String

    /** Flashlight is off. */
    data object Inactive : FlashlightChipModel {
        override fun logString(): String = "Inactive"
    }

    /** Flashlight is currently active / on. */
    data class Active(
        val isEnabled: Boolean = true,
        val level: Int = 1,
        val maxLevel: Int = 1,
    ) : FlashlightChipModel {
        override fun logString(): String = "Active(level=$level, maxLevel=$maxLevel)"
    }
}
