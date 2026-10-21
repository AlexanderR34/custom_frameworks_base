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

package com.android.systemui.statusbar.chips.flashlight.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.flashlight.domain.model.FlashlightChipModel
import com.android.systemui.statusbar.policy.FlashlightController
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

/** Interactor for tracking flashlight status in the status bar chips. */
@SysUISingleton
class FlashlightChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val controller: FlashlightController,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "FlashlightChip".pad())

    val flashlightState: StateFlow<FlashlightChipModel> =
        conflatedCallbackFlow {
            val callback = object : FlashlightController.FlashlightListener {
                override fun onFlashlightChanged(enabled: Boolean) {
                    emitState(enabled)
                }

                override fun onFlashlightError() {
                    trySend(FlashlightChipModel.Inactive)
                }

                override fun onFlashlightAvailabilityChanged(available: Boolean) {
                    if (!available) {
                        trySend(FlashlightChipModel.Inactive)
                    } else {
                        emitState(controller.isEnabled)
                    }
                }

                override fun onFlashlightStrengthChanged(level: Int) {
                    if (controller.isEnabled) {
                        emitState(true)
                    }
                }

                private fun emitState(enabled: Boolean) {
                    if (enabled) {
                        val max = if (controller.isStrengthControlSupported) controller.maxLevel else 1
                        val cur = if (controller.isStrengthControlSupported) controller.currentLevel else 1
                        trySend(FlashlightChipModel.Active(isEnabled = true, level = cur, maxLevel = max))
                    } else {
                        trySend(FlashlightChipModel.Inactive)
                    }
                }
            }

            controller.addCallback(callback)

            if (controller.isEnabled) {
                val max = if (controller.isStrengthControlSupported) controller.maxLevel else 1
                val cur = if (controller.isStrengthControlSupported) controller.currentLevel else 1
                trySend(FlashlightChipModel.Active(isEnabled = true, level = cur, maxLevel = max))
            } else {
                trySend(FlashlightChipModel.Inactive)
            }

            awaitClose { controller.removeCallback(callback) }
        }
        .flowOn(bgDispatcher)
        .onEach { logger.d({ "State: ${str1}" }) { str1 = it.logString() } }
        .stateIn(scope, SharingStarted.WhileSubscribed(), FlashlightChipModel.Inactive)

    fun isFlashlightEnabled(): Boolean = controller.isEnabled

    fun setFlashlight(enabled: Boolean) {
        controller.setFlashlight(enabled)
    }

    fun setLevel(level: Int) {
        if (controller.isStrengthControlSupported) {
            controller.setFlashlightStrengthLevel(level)
        }
    }

    fun isStrengthControlSupported(): Boolean = controller.isStrengthControlSupported
    fun getMaxLevel(): Int = if (controller.isStrengthControlSupported) controller.maxLevel else 1
}
