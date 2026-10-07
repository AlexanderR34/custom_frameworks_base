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

package com.android.systemui.statusbar.chips.download.domain.interactor

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.download.domain.model.DownloadChipModel
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.notifcollection.CommonNotifCollection
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Interactor for tracking ongoing download progress notifications in the status bar. */
@SysUISingleton
class DownloadChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val context: Context,
    private val commonNotifCollection: CommonNotifCollection,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "DownloadChip".pad())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _downloadState = MutableStateFlow<DownloadChipModel>(DownloadChipModel.Inactive)

    private val notifListener = object : NotifCollectionListener {
        override fun onEntryAdded(entry: NotificationEntry) {
            mainHandler.post { scanActiveDownloads() }
        }

        override fun onEntryUpdated(entry: NotificationEntry) {
            mainHandler.post { scanActiveDownloads() }
        }

        override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
            mainHandler.post { scanActiveDownloads() }
        }

        override fun onEntryCleanUp(entry: NotificationEntry) {
            mainHandler.post { scanActiveDownloads() }
        }
    }

    init {
        commonNotifCollection.addCollectionListener(notifListener)
        scanActiveDownloads()
    }

    val downloadState: StateFlow<DownloadChipModel> =
        _downloadState
            .asStateFlow()
            .onEach {
                logger.d({ "Download chip state updated: newState=$str1" }) { str1 = it.logString() }
            }
            .stateIn(scope, SharingStarted.Lazily, DownloadChipModel.Inactive)

    private fun scanActiveDownloads() {
        val allNotifs = commonNotifCollection.allNotifs
        var candidate: NotificationEntry? = null
        var candidateProgress = 0
        var candidateMax = 0
        var candidateIndeterminate = false
        var candidateProgressText: String? = null

        for (entry in allNotifs) {
            val sbn = entry.sbn ?: continue
            val notif = sbn.notification ?: continue
            val extras = notif.extras ?: continue
            val pkg = sbn.packageName

            val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
            val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()

            val percentRegex = Regex("""(\d{1,3})\s*%""")
            val foundPercent = percentRegex.find(text ?: "")
                ?: percentRegex.find(subText ?: "")
                ?: percentRegex.find(infoText ?: "")

            // Exclude media playback and transport notifications from ever being treated as downloads
            if (isMediaNotification(notif, extras)) {
                continue
            }

            val isDownloadProvider = pkg == "com.android.providers.downloads"
            val isPlayStore = pkg == "com.android.vending"
            val isProgressCategory = notif.category == Notification.CATEGORY_PROGRESS
            val hasExplicitProgressBar = max > 0 && progress < max
            val hasPercentText = foundPercent != null
            val isIndeterminateProgress = indeterminate && (isProgressCategory || isDownloadProvider || isPlayStore)
            val isOngoing = sbn.isOngoing || (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0

            val isDownload = isOngoing && (
                isDownloadProvider ||
                (isPlayStore && (hasExplicitProgressBar || isIndeterminateProgress || hasPercentText)) ||
                (isProgressCategory && (hasExplicitProgressBar || isIndeterminateProgress || hasPercentText)) ||
                hasExplicitProgressBar ||
                hasPercentText
            )

            if (isDownload) {
                candidate = entry
                candidateProgress = progress
                candidateMax = max
                candidateIndeterminate = indeterminate
                candidateProgressText = when {
                    max > 0 -> "${((progress.toFloat() / max.toFloat()) * 100).toInt().coerceIn(0, 100)}%"
                    foundPercent != null -> "${foundPercent.groupValues[1]}%"
                    subText != null && subText.isNotBlank() && !isWebDomain(subText) -> subText
                    text != null && text.length <= 12 && !isWebDomain(text) -> text
                    else -> context.getString(R.string.download_chip_default_label)
                }
                break
            }
        }

        if (candidate == null) {
            _downloadState.value = DownloadChipModel.Inactive
            return
        }

        val sbn = candidate.sbn
        val notif = sbn.notification
        val packageName = sbn.packageName

        val appName = try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo)
        } catch (e: Exception) {
            null
        }

        // Prioritize largeIcon (which is the actual app being installed/downloaded)
        val targetIcon: Drawable? = try {
            notif.getLargeIcon()?.loadDrawable(context) ?: context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        val dominantColor = extractDominantColor(targetIcon)

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)
            ?: notif.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)

        _downloadState.value = DownloadChipModel.Active(
            notificationKey = candidate.key,
            packageName = packageName,
            appName = appName,
            title = title,
            progress = candidateProgress,
            max = candidateMax,
            isIndeterminate = candidateIndeterminate,
            progressText = candidateProgressText,
            dominantColor = dominantColor,
            appIcon = targetIcon,
            intent = notif.contentIntent,
        )
    }

    private fun extractDominantColor(drawable: Drawable?): Int? {
        if (drawable == null) return null
        return try {
            val bitmap = if (drawable is BitmapDrawable && !drawable.bitmap.isRecycled) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 48
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 48
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            var rSum = 0L; var gSum = 0L; var bSum = 0L; var count = 0
            val stepX = (bitmap.width / 6).coerceAtLeast(1)
            val stepY = (bitmap.height / 6).coerceAtLeast(1)
            for (x in 0 until bitmap.width step stepX) {
                for (y in 0 until bitmap.height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = (pixel ushr 24) and 0xFF
                    if (alpha > 120) {
                        rSum += (pixel ushr 16) and 0xFF
                        gSum += (pixel ushr 8) and 0xFF
                        bSum += pixel and 0xFF
                        count++
                    }
                }
            }
            if (count > 0) {
                val r = (rSum / count).toInt()
                val g = (gSum / count).toInt()
                val b = (bSum / count).toInt()
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isMediaNotification(notif: Notification, extras: android.os.Bundle): Boolean {
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
        val isMediaStyle = template.contains("MediaStyle") ||
                extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val isTransportCategory = notif.category == Notification.CATEGORY_TRANSPORT
        return isMediaStyle || isTransportCategory
    }

    private fun isWebDomain(str: String): Boolean {
        val lower = str.lowercase().trim()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        if (Regex("(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(/.*)?$").matches(lower)) return true
        return lower.contains(".com") ||
                lower.contains(".net") ||
                lower.contains(".org") ||
                lower.contains(".io") ||
                lower.contains(".edu") ||
                lower.contains(".gov") ||
                lower.contains(".site") ||
                lower.contains(".online") ||
                lower.contains(".app") ||
                lower.contains(".xyz") ||
                lower.contains(".me") ||
                lower.contains(".tv") ||
                lower.contains("khinsider") ||
                lower.contains("music.youtube") ||
                lower.contains("youtube.com") ||
                lower.contains(".youtube") ||
                lower.contains("youtube")
    }
}
