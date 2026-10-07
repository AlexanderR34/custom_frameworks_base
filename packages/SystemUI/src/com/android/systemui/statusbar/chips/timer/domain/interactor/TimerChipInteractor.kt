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

package com.android.systemui.statusbar.chips.timer.domain.interactor

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.timer.domain.model.TimerChipModel
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import com.android.systemui.util.time.SystemClock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Interactor for tracking clock countdown timers in the status bar. */
@SysUISingleton
class TimerChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val context: Context,
    private val commonNotifCollection: CommonNotifCollection,
    private val systemClock: SystemClock,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "TimerChip".pad())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _timerState = MutableStateFlow<TimerChipModel>(TimerChipModel.Inactive)

    private val notifListener = object : NotifCollectionListener {
        override fun onEntryAdded(entry: NotificationEntry) {
            mainHandler.post { scanActiveTimer() }
        }

        override fun onEntryUpdated(entry: NotificationEntry) {
            mainHandler.post { scanActiveTimer() }
        }

        override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
            mainHandler.post { scanActiveTimer() }
        }

        override fun onEntryCleanUp(entry: NotificationEntry) {
            mainHandler.post { scanActiveTimer() }
        }
    }

    init {
        commonNotifCollection.addCollectionListener(notifListener)
        scanActiveTimer()
    }

    val timerState: StateFlow<TimerChipModel> =
        _timerState
            .asStateFlow()
            .onEach {
                logger.d({ "Timer chip state updated: newState=$str1" }) { str1 = it.logString() }
            }
            .stateIn(scope, SharingStarted.Lazily, TimerChipModel.Inactive)

    private fun scanActiveTimer() {
        val allNotifs = commonNotifCollection.allNotifs
        var candidate: NotificationEntry? = null
        var eventTimeRealtime = 0L
        var isCountdown = false

        for (entry in allNotifs) {
            val sbn = entry.sbn ?: continue
            val notif = sbn.notification ?: continue
            val extras = notif.extras ?: continue
            val pkg = sbn.packageName.lowercase()
            val channelId = notif.channelId?.lowercase() ?: ""
            val tag = sbn.tag?.lowercase() ?: ""
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            val isClockPkg = pkg.contains("deskclock") || pkg.contains("clock") || pkg.contains("timer") || pkg.contains("stopwatch")
            val isCountDownExtra = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
            val showChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
            val isOngoing = sbn.isOngoing || (notif.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR or Notification.FLAG_FOREGROUND_SERVICE)) != 0

            val actionTitles = notif.actions?.mapNotNull { it.title?.toString()?.lowercase() } ?: emptyList()
            val hasTimerAction = actionTitles.any { it.contains("1") || it.contains("min") || it.contains("reset") || it.contains("reiniciar") || it.contains("paus") || it.contains("stop") }
            val hasStopwatchAction = actionTitles.any { it.contains("lap") || it.contains("vuelta") }

            val isTimer = isCountDownExtra ||
                channelId.contains("timer") ||
                tag.contains("timer") ||
                (isClockPkg && hasTimerAction && !hasStopwatchAction) ||
                (isClockPkg && notif.`when` > systemClock.currentTimeMillis())

            val isStopwatch = (showChronometer && !isCountDownExtra) ||
                channelId.contains("stopwatch") ||
                tag.contains("stopwatch") ||
                notif.category == Notification.CATEGORY_STOPWATCH ||
                notif.category == "stopwatch" ||
                hasStopwatchAction

            if ((isOngoing || isClockPkg) && (isTimer || isStopwatch || showChronometer || isCountDownExtra)) {
                val nowTime = systemClock.currentTimeMillis()
                val whenTime = notif.`when`

                if (isTimer) {
                    isCountdown = true
                    val remainingMs = if (whenTime > nowTime) {
                        whenTime - nowTime
                    } else {
                        val timeRegex = Regex("""\b(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b""")
                        val match = timeRegex.find(title) ?: timeRegex.find(text) ?: timeRegex.find(subText)
                        if (match != null) {
                            val hrs = match.groupValues[1].takeIf { it.isNotBlank() }?.toLongOrNull() ?: 0L
                            val mins = match.groupValues[2].toLongOrNull() ?: 0L
                            val secs = match.groupValues[3].toLongOrNull() ?: 0L
                            (hrs * 3600 + mins * 60 + secs) * 1000L
                        } else {
                            0L
                        }
                    }
                    eventTimeRealtime = systemClock.elapsedRealtime() + remainingMs
                    candidate = entry
                    break
                } else if (isStopwatch) {
                    isCountdown = false
                    val baseWhen = if (whenTime in 1..nowTime) whenTime else sbn.postTime
                    val elapsedMs = (nowTime - baseWhen).coerceAtLeast(0L)
                    eventTimeRealtime = systemClock.elapsedRealtime() - elapsedMs
                    candidate = entry
                    break
                }
            }
        }

        if (candidate == null) {
            _timerState.value = TimerChipModel.Inactive
            return
        }

        val sbn = candidate.sbn
        val notif = sbn.notification
        val packageName = sbn.packageName

        val appIcon: Drawable? = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        _timerState.value = TimerChipModel.Active(
            notificationKey = candidate.key,
            packageName = packageName,
            eventTimeRealtimeMs = eventTimeRealtime,
            isCountdown = isCountdown,
            isPaused = false,
            pausedDurationMs = 0L,
            appIcon = appIcon,
            intent = notif.contentIntent,
        )
    }
}
