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

package com.android.systemui.statusbar.chips.bluetooth.domain.model

import android.graphics.drawable.Drawable

/** Represents the state of a connected Bluetooth accessory / earbuds battery for the status bar chip. */
sealed interface BluetoothChipModel {
    fun logString(): String

    /** No Bluetooth device is connected or chip timed out. */
    data object Inactive : BluetoothChipModel {
        override fun logString(): String = "Inactive"
    }

    /** An active Bluetooth device is connected with live battery information. */
    data class Active(
        val deviceName: String,
        val batteryLevel: Int, // -1 if unknown, 0..100
        val deviceIcon: Drawable?,
        val isAudioDevice: Boolean,
    ) : BluetoothChipModel {
        override fun logString(): String =
            "Active(device=$deviceName, battery=$batteryLevel%, isAudio=$isAudioDevice)"
    }
}
