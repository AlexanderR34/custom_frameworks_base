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

package com.android.systemui.statusbar.chips.bluetooth.domain.interactor

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.android.settingslib.bluetooth.BluetoothUtils
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.bluetooth.domain.model.BluetoothChipModel
import com.android.systemui.statusbar.policy.BluetoothController
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Interactor for tracking connected Bluetooth accessories and earbuds battery in the status bar. */
@SysUISingleton
class BluetoothChipInteractor
@Inject
constructor(
    @Application private val context: Context,
    @Application private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val controller: BluetoothController,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "BluetoothChip".pad())

    val bluetoothState: StateFlow<BluetoothChipModel> =
        conflatedCallbackFlow {
            val registeredDevices = mutableSetOf<CachedBluetoothDevice>()

            fun cleanRegisteredDevices(cb: CachedBluetoothDevice.Callback) {
                for (dev in registeredDevices) {
                    dev.unregisterCallback(cb)
                }
                registeredDevices.clear()
            }

            fun getDeviceBattery(dev: CachedBluetoothDevice): Int {
                var b = dev.minBatteryLevelWithMemberDevices
                if (b < 0 || b == BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                    b = dev.batteryLevel
                }
                return b
            }

            fun updateChip(cb: CachedBluetoothDevice.Callback) {
                if (!controller.isBluetoothEnabled || !controller.isBluetoothConnected) {
                    cleanRegisteredDevices(cb)
                    trySend(BluetoothChipModel.Inactive)
                    return
                }

                val connectedDevices = controller.connectedDevices
                if (connectedDevices.isNullOrEmpty()) {
                    cleanRegisteredDevices(cb)
                    trySend(BluetoothChipModel.Inactive)
                    return
                }

                // Update callbacks for connected devices
                val currentSet = connectedDevices.toSet()
                for (dev in registeredDevices - currentSet) {
                    dev.unregisterCallback(cb)
                }
                for (dev in currentSet - registeredDevices) {
                    dev.registerCallback(cb)
                }
                registeredDevices.clear()
                registeredDevices.addAll(currentSet)

                // Pick the most relevant connected device:
                // 1) Audio/earbuds device with known battery
                // 2) Any device with known battery
                // 3) Audio/earbuds device
                // 4) First connected device
                val primaryDevice =
                    connectedDevices.firstOrNull { dev ->
                        getDeviceBattery(dev) in 0..100 && (dev.isHearingDevice ||
                            BluetoothUtils.isAdvancedUntetheredDevice(dev.device) ||
                            (dev.btClass?.doesClassMatch(BluetoothClass.PROFILE_A2DP) == true) ||
                            (dev.btClass?.doesClassMatch(BluetoothClass.PROFILE_HEADSET) == true))
                    } ?: connectedDevices.firstOrNull { dev ->
                        getDeviceBattery(dev) in 0..100
                    } ?: connectedDevices.firstOrNull { dev ->
                        dev.isHearingDevice ||
                            BluetoothUtils.isAdvancedUntetheredDevice(dev.device) ||
                            (dev.btClass?.doesClassMatch(BluetoothClass.PROFILE_A2DP) == true) ||
                            (dev.btClass?.doesClassMatch(BluetoothClass.PROFILE_HEADSET) == true)
                    } ?: connectedDevices.first()

                val deviceName = primaryDevice.name ?: controller.connectedDeviceName ?: "Bluetooth"

                // Determine battery level
                var battery = getDeviceBattery(primaryDevice)
                if (battery < 0 || battery == BluetoothDevice.BATTERY_LEVEL_UNKNOWN) {
                    battery = controller.batteryLevel
                }

                val icon =
                    try {
                        if (BluetoothUtils.isAdvancedUntetheredDevice(primaryDevice.device)) {
                            context.getDrawable(com.android.settingslib.R.drawable.ic_earbuds_advanced)
                        } else {
                            primaryDevice.drawableWithDescription?.first
                                ?: BluetoothUtils.getBtClassDrawableWithDescription(context, primaryDevice).first
                        }
                    } catch (e: Exception) {
                        null
                    }

                val isAudio =
                    primaryDevice.isHearingDevice ||
                        BluetoothUtils.isAdvancedUntetheredDevice(primaryDevice.device) ||
                        (primaryDevice.btClass?.doesClassMatch(BluetoothClass.PROFILE_A2DP) == true) ||
                        (primaryDevice.btClass?.doesClassMatch(BluetoothClass.PROFILE_HEADSET) == true)

                val activeModel =
                    BluetoothChipModel.Active(
                        deviceName = deviceName,
                        batteryLevel = battery,
                        deviceIcon = icon,
                        isAudioDevice = isAudio,
                    )

                trySend(activeModel)
            }

            val deviceCallback =
                object : CachedBluetoothDevice.Callback {
                    override fun onDeviceAttributesChanged() {
                        updateChip(this)
                    }
                }

            val bluetoothCallback =
                object : BluetoothController.Callback {
                    override fun onBluetoothStateChange(enabled: Boolean) {
                        updateChip(deviceCallback)
                    }

                    override fun onBluetoothDevicesChanged() {
                        updateChip(deviceCallback)
                    }
                }

            controller.addCallback(bluetoothCallback)
            updateChip(deviceCallback)

            awaitClose {
                controller.removeCallback(bluetoothCallback)
                cleanRegisteredDevices(deviceCallback)
            }
        }
        .flowOn(bgDispatcher)
        .onEach {
            logger.d({ "Bluetooth chip state updated: newState=$str1" }) { str1 = it.logString() }
        }
        .stateIn(scope, SharingStarted.Lazily, BluetoothChipModel.Inactive)
}
