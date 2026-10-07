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

package com.android.systemui.statusbar.chips.navigation.domain.interactor

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
import com.android.systemui.statusbar.chips.navigation.domain.model.NavigationChipModel
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

/** Interactor for tracking GPS turn-by-turn navigation notifications in the status bar. */
@SysUISingleton
class NavigationChipInteractor
@Inject
constructor(
    @Application private val scope: CoroutineScope,
    private val context: Context,
    private val commonNotifCollection: CommonNotifCollection,
    @StatusBarChipsLog private val logBuffer: LogBuffer,
) {
    private val logger = Logger(logBuffer, "NavigationChip".pad())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _navigationState = MutableStateFlow<NavigationChipModel>(NavigationChipModel.Inactive)

    private val notifListener = object : NotifCollectionListener {
        override fun onEntryAdded(entry: NotificationEntry) {
            mainHandler.post { scanActiveNavigation() }
        }

        override fun onEntryUpdated(entry: NotificationEntry) {
            mainHandler.post { scanActiveNavigation() }
        }

        override fun onEntryRemoved(entry: NotificationEntry, reason: Int) {
            mainHandler.post { scanActiveNavigation() }
        }

        override fun onEntryCleanUp(entry: NotificationEntry) {
            mainHandler.post { scanActiveNavigation() }
        }
    }

    init {
        commonNotifCollection.addCollectionListener(notifListener)
        scanActiveNavigation()
    }

    val navigationState: StateFlow<NavigationChipModel> =
        _navigationState
            .asStateFlow()
            .onEach {
                logger.d({ "Navigation chip state updated: newState=$str1" }) { str1 = it.logString() }
            }
            .stateIn(scope, SharingStarted.Lazily, NavigationChipModel.Inactive)

    private fun scanActiveNavigation() {
        val allNotifs = commonNotifCollection.allNotifs
        var candidate: NotificationEntry? = null

        for (entry in allNotifs) {
            val sbn = entry.sbn ?: continue
            val notif = sbn.notification ?: continue
            val pkg = sbn.packageName.lowercase()

            val isNavCategory = notif.category == Notification.CATEGORY_NAVIGATION
            val isKnownNavApp = KNOWN_NAV_PACKAGES.any { pkg.contains(it.lowercase()) } ||
                pkg.contains("maps") || pkg.contains("navigation") || pkg.contains("waze") ||
                pkg.contains("gps") || pkg.contains("navapp") || pkg.contains("sygic") ||
                pkg.contains("here") || pkg.contains("osmand") || pkg.contains("mapy")

            val channelId = entry.ranking.channel?.id?.lowercase() ?: notif.channelId?.lowercase() ?: ""
            val channelName = entry.ranking.channel?.name?.toString()?.lowercase() ?: ""
            val isNavChannel = channelId.contains("nav") || channelId.contains("direction") ||
                channelId.contains("route") || channelId.contains("drive") || channelId.contains("guidance") ||
                channelName.contains("nav") || channelName.contains("ruta") || channelName.contains("dirección")

            val isOngoing = sbn.isOngoing ||
                (notif.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR)) != 0

            // Filter out pure location sharing background notifications
            val isLocationSharing = (channelId.contains("share") || channelId.contains("sharing") ||
                notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase()?.contains("compart") == true) &&
                !isNavCategory && !isNavChannel

            if (!isLocationSharing && (isNavCategory || isNavChannel || isKnownNavApp) && (isOngoing || isNavCategory || isNavChannel)) {
                if (candidate == null || isNavCategory || isNavChannel) {
                    candidate = entry
                    if (isNavCategory) break
                }
            }
        }

        if (candidate == null) {
            _navigationState.value = NavigationChipModel.Inactive
            return
        }

        val sbn = candidate.sbn
        val notif = sbn.notification
        val extras = notif.extras
        val packageName = sbn.packageName

        val appName = try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo)
        } catch (e: Exception) {
            null
        }

        val appIcon: Drawable? = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }

        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)

        // Try to load largeIcon (typically contains turn/maneuver arrow)
        val maneuverIcon: Drawable? = try {
            notif.getLargeIcon()?.loadDrawable(context)
        } catch (e: Exception) {
            null
        }

        _navigationState.value = NavigationChipModel.Active(
            notificationKey = candidate.key,
            packageName = packageName,
            appName = appName,
            directionText = title,
            distanceText = subText ?: text,
            etaText = text?.takeIf { subText != null },
            maneuverIcon = maneuverIcon,
            appIcon = appIcon,
            intent = notif.contentIntent,
        )
    }

    companion object {
        private val KNOWN_NAV_PACKAGES = setOf(
            "com.google.android.apps.maps",
            "com.waze",
            "com.here.app.maps",
            "osm.and.plus",
            "net.osmand",
            "cz.seznam.mapy",
            "com.sygic.aura",
            "com.sygic.truck",
            "com.tomtom.gplay.navapp",
            "com.tomtom.speedcams.android.map",
            "com.baidu.BaiduMap",
            "com.autonavi.minimap",
            "com.huawei.maps.app",
            "app.organicmaps",
            "com.mapswithme.maps.pro",
            "com.generalmagic.magicearth",
            "com.citymapper.app.release",
            "com.moovit.android",
            "ru.yandex.yandexnavi",
            "ru.yandex.yandexmaps",
            "com.locnall.KimGiSa",
            "com.tmapmobility.tmap",
            "com.nhn.android.nmap"
        )
    }
}
