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

package com.android.systemui.statusbar.chips.hotspot.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.hotspot.domain.model.HotspotChipModel
import com.android.systemui.statusbar.policy.HotspotController
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

/** Interactor for tracking Wi-Fi hotspot and connected client devices in the status bar. */
@SysUISingleton
class HotspotChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val controller: HotspotController,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "HotspotChip".pad())

    val hotspotState: StateFlow<HotspotChipModel> =
        conflatedCallbackFlow {
            val callback = object : HotspotController.Callback {
                override fun onHotspotChanged(enabled: Boolean, numDevices: Int) {
                    if (enabled) {
                        trySend(
                            HotspotChipModel.Active(
                                numConnectedDevices = numDevices,
                                isTransient = controller.isHotspotTransient,
                            )
                        )
                    } else {
                        trySend(HotspotChipModel.Inactive)
                    }
                }
            }

            controller.addCallback(callback)

            if (controller.isHotspotEnabled) {
                trySend(
                    HotspotChipModel.Active(
                        numConnectedDevices = controller.numConnectedDevices,
                        isTransient = controller.isHotspotTransient,
                    )
                )
            } else {
                trySend(HotspotChipModel.Inactive)
            }

            awaitClose { controller.removeCallback(callback) }
        }
        .flowOn(bgDispatcher)
        .onEach {
            logger.d({ "Hotspot chip state updated: newState=$str1" }) { str1 = it.logString() }
        }
        .stateIn(scope, SharingStarted.Lazily, HotspotChipModel.Inactive)
}
