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

package com.android.systemui.statusbar.chips.privacy.domain.interactor

import android.content.Context
import android.graphics.drawable.Drawable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyItemController
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.privacy.domain.model.PrivacyChipModel
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Interactor for tracking camera, microphone, and location sensor usage for the status bar chip. */
@SysUISingleton
class PrivacyChipInteractor
@Inject
constructor(
    @Application private val context: Context,
    @Application private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val controller: PrivacyItemController,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "PrivacyChip".pad())

    val privacyState: StateFlow<PrivacyChipModel> =
        conflatedCallbackFlow {
            var hideJob: Job? = null

            fun update(items: List<PrivacyItem>) {
                val activeItems = items.filter { !it.paused && (
                    it.privacyType == PrivacyType.TYPE_CAMERA ||
                    it.privacyType == PrivacyType.TYPE_MICROPHONE ||
                    it.privacyType == PrivacyType.TYPE_LOCATION
                )}

                if (activeItems.isEmpty()) {
                    hideJob?.cancel()
                    hideJob = launch {
                        delay(GRACE_PERIOD_DISMISS_DELAY_MS)
                        trySend(PrivacyChipModel.Inactive)
                    }
                    return
                }

                val hasMic = activeItems.any { it.privacyType == PrivacyType.TYPE_MICROPHONE }
                val hasCam = activeItems.any { it.privacyType == PrivacyType.TYPE_CAMERA }
                val hasLocation = activeItems.any { it.privacyType == PrivacyType.TYPE_LOCATION }

                val primaryItem = activeItems.first()
                val pkgName = primaryItem.application.packageName

                val pm = context.packageManager
                val appInfo = try {
                    pm.getApplicationInfo(pkgName, 0)
                } catch (e: Exception) {
                    null
                }
                val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkgName
                val appIcon: Drawable? = appInfo?.let { pm.getApplicationIcon(it) }

                trySend(
                    PrivacyChipModel.Active(
                        packageName = pkgName,
                        appName = appName,
                        appIcon = appIcon,
                        hasMic = hasMic,
                        hasCam = hasCam,
                        hasLocation = hasLocation,
                    )
                )

                // Keep visible for the configured duration (or indefinitely while active if 0)
                hideJob?.cancel()
                val timeoutSecs = getPrivacyTimeoutSeconds()
                if (timeoutSecs > 0) {
                    hideJob = launch {
                        delay(timeoutSecs * 1000L)
                        trySend(PrivacyChipModel.Inactive)
                    }
                }
            }

            val callback = object : PrivacyItemController.Callback {
                override fun onPrivacyItemsChanged(privacyItems: List<PrivacyItem>) {
                    update(privacyItems)
                }
            }

            controller.addCallback(callback)
            update(controller.privacyList)

            awaitClose {
                controller.removeCallback(callback)
                hideJob?.cancel()
            }
        }
        .flowOn(bgDispatcher)
        .onEach {
            logger.d({ "Privacy chip state updated: newState=$str1" }) { str1 = it.logString() }
        }
        .stateIn(scope, SharingStarted.Lazily, PrivacyChipModel.Inactive)

    private fun getPrivacyTimeoutSeconds(): Int {
        return try {
            android.provider.Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_PRIVACY_TIMEOUT,
                DEFAULT_PRIVACY_TIMEOUT_SECONDS,
                android.os.UserHandle.USER_CURRENT,
            )
        } catch (e: Exception) {
            DEFAULT_PRIVACY_TIMEOUT_SECONDS
        }
    }

    companion object {
        const val SETTING_PRIVACY_TIMEOUT = "status_bar_chips_privacy_timeout"
        private const val DEFAULT_PRIVACY_TIMEOUT_SECONDS = 15
        private const val GRACE_PERIOD_DISMISS_DELAY_MS = 3000L
    }
}
