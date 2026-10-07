/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.PerDisplaySingleton
import com.android.systemui.display.domain.interactor.DisplayStateInteractor
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordFeaturesInteractor
import com.android.systemui.statusbar.chips.StatusBarChipLogTags.pad
import com.android.systemui.statusbar.chips.StatusBarChipToHunAnimation
import com.android.systemui.statusbar.chips.StatusBarChipsLog
import com.android.systemui.statusbar.chips.bluetooth.ui.viewmodel.BluetoothChipViewModel
import com.android.systemui.statusbar.chips.call.ui.viewmodel.CallChipViewModel
import com.android.systemui.statusbar.chips.casttootherdevice.ui.viewmodel.CastToOtherDeviceChipViewModel
import com.android.systemui.statusbar.chips.download.ui.viewmodel.DownloadChipViewModel
import com.android.systemui.statusbar.chips.hotspot.ui.viewmodel.HotspotChipViewModel
import com.android.systemui.statusbar.chips.media.ui.viewmodel.MediaChipViewModel
import com.android.systemui.statusbar.chips.navigation.ui.viewmodel.NavigationChipViewModel
import com.android.systemui.statusbar.chips.notification.ui.viewmodel.NotifChipsViewModel
import com.android.systemui.statusbar.chips.privacy.ui.viewmodel.PrivacyChipViewModel
import com.android.systemui.statusbar.chips.screenrecord.ui.viewmodel.ScreenRecordChipViewModel
import com.android.systemui.statusbar.chips.sharetoapp.ui.viewmodel.ShareToAppChipViewModel
import com.android.systemui.statusbar.chips.timer.ui.viewmodel.TimerChipViewModel
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.MultipleOngoingActivityChipsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.notification.shared.StatusBarHeadline
import com.android.systemui.util.kotlin.filterValuesNotNull
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * View model deciding which ongoing activity chip to show in the status bar.
 *
 * There may be multiple ongoing activities at the same time, but we can only ever show one chip at
 * any one time (for now). This class decides which ongoing activity to show if there are multiple.
 */
@PerDisplaySingleton
class OngoingActivityChipsViewModel
@Inject
constructor(
    @Application private val context: Context,
    @DisplayAware scope: CoroutineScope,
    privacyChipViewModel: PrivacyChipViewModel,
    screenRecordChipViewModel: ScreenRecordChipViewModel,
    shareToAppChipViewModel: ShareToAppChipViewModel,
    castToOtherDeviceChipViewModel: CastToOtherDeviceChipViewModel,
    callChipViewModel: CallChipViewModel,
    downloadChipViewModel: DownloadChipViewModel,
    mediaChipViewModel: MediaChipViewModel,
    navigationChipViewModel: NavigationChipViewModel,
    timerChipViewModel: TimerChipViewModel,
    hotspotChipViewModel: HotspotChipViewModel,
    bluetoothChipViewModel: BluetoothChipViewModel,
    notifChipsViewModel: NotifChipsViewModel,
    @DisplayAware displayStateInteractor: DisplayStateInteractor,
    private val screenCaptureRecordFeaturesInteractor: ScreenCaptureRecordFeaturesInteractor,
    private val chipsRefiners: Set<@JvmSuppressWildcards OngoingActivityChipsRefiner>,
    @StatusBarChipsLog private val logger: LogBuffer,
) {
    private enum class ChipType {
        Privacy,
        ScreenRecord,
        ShareToApp,
        CastToOtherDevice,
        Call,
        Timer,
        Navigation,
        Hotspot,
        Download,
        Media,
        Bluetooth,
        Notification,
    }

    /** Model that helps us internally track the various chip states from each of the types. */
    @Deprecated("Since StatusBarChipsModernization, this isn't used anymore")
    private sealed interface InternalChipModel {
        /**
         * Represents that we've internally decided to show the chip with type [type] with the given
         * [model] information.
         */
        data class Active(val type: ChipType, val model: OngoingActivityChipModel.Active) :
            InternalChipModel

        /**
         * Represents that all chip types would like to be hidden. Each value specifies *how* that
         * chip type should get hidden.
         */
        data class Inactive(
            val privacy: OngoingActivityChipModel.Inactive,
            val screenRecord: OngoingActivityChipModel.Inactive,
            val shareToApp: OngoingActivityChipModel.Inactive,
            val castToOtherDevice: OngoingActivityChipModel.Inactive,
            val call: OngoingActivityChipModel.Inactive,
            val timer: OngoingActivityChipModel.Inactive,
            val navigation: OngoingActivityChipModel.Inactive,
            val hotspot: OngoingActivityChipModel.Inactive,
            val download: OngoingActivityChipModel.Inactive,
            val media: OngoingActivityChipModel.Inactive,
            val bluetooth: OngoingActivityChipModel.Inactive,
            val notifs: OngoingActivityChipModel.Inactive,
        ) : InternalChipModel
    }

    private data class ChipBundle(
        val privacy: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val screenRecord: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val shareToApp: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val castToOtherDevice: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val call: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val timer: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val navigation: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val hotspot: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val download: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val media: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val bluetooth: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val notifs: List<OngoingActivityChipModel.Active> = emptyList(),
    )

    private data class SystemChips(
        val privacy: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val screenRecord: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val shareToApp: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val castToOtherDevice: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val call: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val timer: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
    )

    private data class AppChips(
        val navigation: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val hotspot: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val download: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val media: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val bluetooth: OngoingActivityChipModel = OngoingActivityChipModel.Inactive(),
        val notifs: List<OngoingActivityChipModel.Active> = emptyList(),
    )

    private val systemChips: Flow<SystemChips> =
        if (screenCaptureRecordFeaturesInteractor.isLargeScreenScreencaptureEnabled) {
            combine(
                privacyChipViewModel.chip,
                shareToAppChipViewModel.chip,
                castToOtherDeviceChipViewModel.chip,
                callChipViewModel.chip,
                timerChipViewModel.chip,
            ) { privacy, shareToApp, castToOtherDevice, call, timer ->
                SystemChips(
                    privacy,
                    OngoingActivityChipModel.Inactive(),
                    shareToApp,
                    castToOtherDevice,
                    call,
                    timer,
                )
            }
        } else {
            combine(
                combine(
                    privacyChipViewModel.chip,
                    screenRecordChipViewModel.chip,
                    shareToAppChipViewModel.chip,
                ) { privacy, screenRecord, shareToApp ->
                    Triple(privacy, screenRecord, shareToApp)
                },
                castToOtherDeviceChipViewModel.chip,
                callChipViewModel.chip,
                timerChipViewModel.chip,
            ) { (privacy, screenRecord, shareToApp), castToOtherDevice, call, timer ->
                SystemChips(
                    privacy,
                    screenRecord,
                    shareToApp,
                    castToOtherDevice,
                    call,
                    timer,
                )
            }
        }

    private val appChips: Flow<AppChips> =
        combine(
            navigationChipViewModel.chip,
            hotspotChipViewModel.chip,
            downloadChipViewModel.chip,
            mediaChipViewModel.chip,
            bluetoothChipViewModel.chip,
        ) { navigation, hotspot, download, media, bluetooth ->
            AppChips(
                navigation,
                hotspot,
                download,
                media,
                bluetooth,
                emptyList(),
            )
        }.combine(notifChipsViewModel.chips) { chips, notifs ->
            chips.copy(notifs = notifs)
        }

    /** Bundles all the incoming chips into one object to easily pass to various flows. */
    private val incomingChipBundle =
        combine(systemChips, appChips) { sys, app ->
            logChips(
                sys.privacy,
                sys.screenRecord,
                sys.shareToApp,
                sys.castToOtherDevice,
                sys.call,
                sys.timer,
                app.navigation,
                app.hotspot,
                app.download,
                app.media,
                app.bluetooth,
                app.notifs,
            )
            ChipBundle(
                privacy = sys.privacy,
                screenRecord = sys.screenRecord,
                shareToApp = sys.shareToApp,
                castToOtherDevice = sys.castToOtherDevice,
                call = sys.call,
                timer = sys.timer,
                navigation = app.navigation,
                hotspot = app.hotspot,
                download = app.download,
                media = app.media,
                bluetooth = app.bluetooth,
                notifs = app.notifs,
            )
        }
        // Some of the chips could have timers in them and we don't want the start time for
        // those timers to get reset for any reason. So, as soon as any subscriber has requested
        // the chip information, we maintain it forever by using [SharingStarted.Lazily].
        // See b/347726238.
        .stateIn(scope, SharingStarted.Lazily, ChipBundle())

    private fun logChips(
        privacy: OngoingActivityChipModel,
        screenRecord: OngoingActivityChipModel,
        shareToApp: OngoingActivityChipModel,
        castToOtherDevice: OngoingActivityChipModel,
        call: OngoingActivityChipModel,
        timer: OngoingActivityChipModel,
        navigation: OngoingActivityChipModel,
        hotspot: OngoingActivityChipModel,
        download: OngoingActivityChipModel,
        media: OngoingActivityChipModel,
        bluetooth: OngoingActivityChipModel,
        notifs: List<OngoingActivityChipModel.Active>,
    ) {
        logger.log(
            TAG,
            LogLevel.INFO,
            {
                str1 = privacy.logName
                str2 = screenRecord.logName
                str3 = shareToApp.logName
            },
            { "Chips: Privacy=$str1 > ScreenRecord=$str2 > ShareToApp=$str3..." },
        )
        logger.log(
            TAG,
            LogLevel.INFO,
            {
                str1 = call.logName
                str2 = timer.logName
                str3 = "${navigation.logName} > Hotspot=${hotspot.logName} > Download=${download.logName} > Media=${media.logName} > Bluetooth=${bluetooth.logName} > Notifs=${notifs.map { it.logName }}"
            },
            { "... > Call=$str1 > Timer=$str2 > Nav=$str3" },
        )
    }

    private fun OngoingActivityChipModel.Active.shouldSquish(): Boolean {
        if (this.icon == null) {
            // If there's no icon, we can't squish the chip to be icon-only
            return false
        }
        return when (this.content) {
            // Icon-only is already maximum squished
            is OngoingActivityChipModel.Content.IconOnly,
            // Countdown shows just a single digit, so already maximum squished
            is OngoingActivityChipModel.Content.Countdown -> false
            // The other chips have icon+text, so we can squish them by hiding text
            is OngoingActivityChipModel.Content.Timer,
            is OngoingActivityChipModel.Content.ShortTimeDelta,
            is OngoingActivityChipModel.Content.Text,
            is OngoingActivityChipModel.Content.TextVariants,
            is OngoingActivityChipModel.Content.SensorIcons -> true
        }
    }

    private fun OngoingActivityChipModel.Active.toIconOnly(): OngoingActivityChipModel.Active {
        if (icon == null) {
            // If this chip doesn't have an icon, then it only has text and we should continue
            // showing its text. (This is theoretically impossible because [shouldSquish] returns
            // false for a model with a null icon, but protect against it just in case.)
            return this
        }
        return this.copy(content = OngoingActivityChipModel.Content.IconOnly)
    }

    private val chipsConfigVersion: StateFlow<Long> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(System.currentTimeMillis())
                    }
                }
            try {
                val keysToObserve = listOf(
                    SETTING_CHIPS_LIMIT,
                    SETTING_MULTI_STYLE,
                    SETTING_MEDIA_ART_STYLE,
                    SETTING_MONET_COLORS,
                    SETTING_MONET_ICONS,
                    SETTING_ENABLE_PRIVACY,
                    SETTING_ENABLE_SCREEN_RECORD,
                    SETTING_ENABLE_SHARE_TO_APP,
                    SETTING_ENABLE_CAST,
                    SETTING_ENABLE_CALL,
                    SETTING_ENABLE_TIMER,
                    SETTING_ENABLE_NAVIGATION,
                    SETTING_ENABLE_HOTSPOT,
                    SETTING_ENABLE_DOWNLOAD,
                    SETTING_ENABLE_MEDIA,
                    SETTING_ENABLE_BLUETOOTH,
                    SETTING_ENABLE_NOTIFS,
                    SETTING_COLOR_CAM_MIC,
                    SETTING_COLOR_LOCATION,
                    SETTING_COLOR_COMBO,
                    SETTING_COLOR_ALL,
                )
                for (key in keysToObserve) {
                    context.contentResolver.registerContentObserver(
                        Settings.System.getUriFor(key),
                        false,
                        observer,
                        UserHandle.USER_ALL,
                    )
                }
            } catch (e: Exception) {}
            trySend(System.currentTimeMillis())
            awaitClose {
                try {
                    context.contentResolver.unregisterContentObserver(observer)
                } catch (e: Exception) {}
            }
        }
        .stateIn(scope, SharingStarted.Lazily, 0L)

    private fun getChipsLimit(): Int {
        return try {
            Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_CHIPS_LIMIT,
                DEFAULT_CHIPS_LIMIT,
                UserHandle.USER_CURRENT,
            )
        } catch (e: Exception) {
            try {
                Settings.System.getInt(context.contentResolver, SETTING_CHIPS_LIMIT, DEFAULT_CHIPS_LIMIT)
            } catch (e2: Exception) {
                DEFAULT_CHIPS_LIMIT
            }
        }
    }

    private fun getMultiStyle(): Int {
        return try {
            Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_MULTI_STYLE,
                DEFAULT_MULTI_STYLE,
                UserHandle.USER_CURRENT,
            )
        } catch (e: Exception) {
            try {
                Settings.System.getInt(context.contentResolver, SETTING_MULTI_STYLE, DEFAULT_MULTI_STYLE)
            } catch (e2: Exception) {
                DEFAULT_MULTI_STYLE
            }
        }
    }

    private fun isChipEnabled(settingKey: String, defaultValue: Int = 1): Boolean {
        return try {
            Settings.System.getIntForUser(
                context.contentResolver,
                settingKey,
                defaultValue,
                UserHandle.USER_CURRENT,
            ) == 1
        } catch (e: Exception) {
            try {
                Settings.System.getInt(context.contentResolver, settingKey, defaultValue) == 1
            } catch (e2: Exception) {
                defaultValue == 1
            }
        }
    }

    /**
     * A flow modeling the active and inactive chips as well as which should be shown in the status
     * bar after accounting for possibly multiple ongoing activities and animation requirements.
     */
    private val unrefinedChips =
        combine(
            incomingChipBundle,
            chipsConfigVersion,
            displayStateInteractor.isWideScreen,
        ) { bundle, _, isWideScreen ->
            val limit = getChipsLimit()
            val multiStyle = getMultiStyle()
            val isMonetEnabled = isChipEnabled(SETTING_MONET_COLORS, defaultValue = 0)
            val isMonetIconsEnabled = isChipEnabled(SETTING_MONET_ICONS, defaultValue = 0)
            val rankedChips = rankChips(bundle, limit)

            val activeChipsWithMonet = if (isMonetEnabled) {
                rankedChips.active.map { chip ->
                    if (chip.colors is ColorsModel.Red) {
                        chip
                    } else {
                        chip.copy(colors = ColorsModel.AccentThemed(useSecondaryForIcons = isMonetIconsEnabled))
                    }
                }
            } else {
                rankedChips.active
            }

            if (
                !StatusBarHeadline.isEnabled &&
                    !isWideScreen &&
                    activeChipsWithMonet.filter { !it.isHidden }.size >= 2
            ) {
                var visibleCount = 0
                val squishedActiveChips =
                    activeChipsWithMonet.map { chip ->
                        if (!chip.isHidden) {
                            val isSecondary = visibleCount > 0
                            visibleCount++
                            val shouldCondense = when (multiStyle) {
                                0 -> false // All expanded: show full text & details on all chips
                                2 -> true  // All compact: condense all chips to icons only
                                else -> isSecondary // 1: primary expanded, secondaries compact
                            }
                            if (shouldCondense && chip.shouldSquish()) {
                                chip.toIconOnly()
                            } else {
                                chip
                            }
                        } else {
                            chip
                        }
                    }

                MultipleOngoingActivityChipsModel(
                    active = squishedActiveChips,
                    overflow = rankedChips.overflow,
                    inactive = rankedChips.inactive,
                )
            } else {
                MultipleOngoingActivityChipsModel(
                    active = activeChipsWithMonet,
                    overflow = rankedChips.overflow,
                    inactive = rankedChips.inactive,
                )
            }
        }

    val chips: StateFlow<MultipleOngoingActivityChipsModel> =
        unrefinedChips
            .map { unrefinedChips ->
                chipsRefiners.fold(unrefinedChips) { currentOutput, refiner ->
                    refiner.transform(currentOutput)
                }
            }
            .stateIn(scope, SharingStarted.Lazily, MultipleOngoingActivityChipsModel())

    private val activeChips = chips.map { it.active }

    /** Stores the latest on-screen bounds for each of the chips. */
    // Note: This will also store bounds for chips that have been removed. We may want to clear the
    // value for removed chips.
    private val chipBounds = MutableStateFlow<Map<String, RectF>>(emptyMap())

    /**
     * Invoked each time a chip's on-screen bounds have changed.
     *
     * @param key the raw notification key without any prefixes.
     */
    fun onChipBoundsChanged(key: String, newBounds: RectF) {
        if (!StatusBarChipToHunAnimation.isEnabled) {
            return
        }
        val map = chipBounds.value.toMutableMap()
        val currentValue = map[key]
        if (currentValue != null) {
            currentValue.set(newBounds)
        } else {
            map[key] = newBounds
        }
        chipBounds.value = map
    }

    /** A flow modeling just the keys for the currently visible notification chips. */
    private val visibleNotificationChipKeys: Flow<List<String>> =
        activeChips.map { chips -> chips.filter { !it.isHidden }.mapNotNull { it.notificationKey } }

    /** Placeholder chip bounds to use if {@link StatusBarChipToHunAnimation} is disabled. */
    private val placeholderChipBounds = RectF()

    /**
     * A flow modeling the keys and on-screen bounds for the currently visible chips.
     *
     * This only contains bounds for chips tied to notifications. Other chips, like screen sharing
     * chips, are *NOT* in this list.
     */
    val visibleNotificationChipsWithBounds: Flow<Map<String, RectF>> =
        if (StatusBarChipToHunAnimation.isEnabled) {
            combine(visibleNotificationChipKeys, chipBounds) { keys, chipBounds ->
                    keys.associateWith { chipBounds[it] }.filterValuesNotNull()
                }
                .distinctUntilChanged()
        } else {
            visibleNotificationChipKeys
                .map { keys -> keys.associateWith { placeholderChipBounds } }
                .distinctUntilChanged()
        }

    /**
     * Sort the given chip [bundle] in order of priority, and divide the chips between active,
     * overflow, and inactive (see [MultipleOngoingActivityChipsModel] for a description of each).
     */
    private fun rankChips(bundle: ChipBundle, maxVisibleChips: Int = DEFAULT_CHIPS_LIMIT): MultipleOngoingActivityChipsModel {
        val activeChips = mutableListOf<OngoingActivityChipModel.Active>()
        val overflowChips = mutableListOf<OngoingActivityChipModel.Active>()
        val inactiveChips = mutableListOf<OngoingActivityChipModel.Inactive>()

        val privacyChip = if (isChipEnabled(SETTING_ENABLE_PRIVACY)) bundle.privacy else OngoingActivityChipModel.Inactive()
        val screenRecordChip = if (isChipEnabled(SETTING_ENABLE_SCREEN_RECORD)) bundle.screenRecord else OngoingActivityChipModel.Inactive()
        val shareToAppChip = if (isChipEnabled(SETTING_ENABLE_SHARE_TO_APP)) bundle.shareToApp else OngoingActivityChipModel.Inactive()
        val castChip = if (isChipEnabled(SETTING_ENABLE_CAST)) bundle.castToOtherDevice else OngoingActivityChipModel.Inactive()
        val callChip = if (isChipEnabled(SETTING_ENABLE_CALL)) bundle.call else OngoingActivityChipModel.Inactive()
        val timerChip = if (isChipEnabled(SETTING_ENABLE_TIMER)) bundle.timer else OngoingActivityChipModel.Inactive()
        val navChip = if (isChipEnabled(SETTING_ENABLE_NAVIGATION)) bundle.navigation else OngoingActivityChipModel.Inactive()
        val hotspotChip = if (isChipEnabled(SETTING_ENABLE_HOTSPOT)) bundle.hotspot else OngoingActivityChipModel.Inactive()
        val downloadChip = if (isChipEnabled(SETTING_ENABLE_DOWNLOAD)) bundle.download else OngoingActivityChipModel.Inactive()
        val mediaChip = if (isChipEnabled(SETTING_ENABLE_MEDIA)) bundle.media else OngoingActivityChipModel.Inactive()
        val btChip = if (isChipEnabled(SETTING_ENABLE_BLUETOOTH)) bundle.bluetooth else OngoingActivityChipModel.Inactive()
        val specializedChips =
            listOf(
                privacyChip,
                callChip,
                mediaChip,
                screenRecordChip,
                shareToAppChip,
                castChip,
                timerChip,
                navChip,
                downloadChip,
                hotspotChip,
                btChip,
            )

        // Deduplicate: filter out notification chips whose package or notification key is already covered by an active specialized chip
        val activeSpecializedPackages = specializedChips
            .filterIsInstance<OngoingActivityChipModel.Active>()
            .mapNotNull { it.managingPackageName }
            .toSet()

        val activeSpecializedNotifKeys = specializedChips
            .filterIsInstance<OngoingActivityChipModel.Active>()
            .mapNotNull { it.notificationKey }
            .toSet()

        val isMediaChipActive = specializedChips.filterIsInstance<OngoingActivityChipModel.Active>().any { it.key.startsWith("mediaChip-") }
        val notifsChips = if (isChipEnabled(SETTING_ENABLE_NOTIFS)) bundle.notifs else emptyList()
        val filteredNotifsChips = notifsChips.filter { notifChip ->
            val pkg = notifChip.managingPackageName ?: ""
            val isBrowserPkg = pkg.contains("brave") ||
                    pkg.contains("chrome") ||
                    pkg.contains("browser") ||
                    pkg.contains("firefox") ||
                    pkg.contains("opera") ||
                    pkg.contains("edge")
            val pkgMatch = pkg.isNotEmpty() && activeSpecializedPackages.contains(pkg)
            val keyMatch = notifChip.notificationKey != null && activeSpecializedNotifKeys.contains(notifChip.notificationKey)
            val browserMediaMatch = isBrowserPkg && isMediaChipActive
            !pkgMatch && !keyMatch && !browserMediaMatch
        }

        val sortedChips = specializedChips + filteredNotifsChips

        var shownSlotsRemaining = maxVisibleChips
        for (chip in sortedChips) {
            when (chip) {
                is OngoingActivityChipModel.Active -> {
                    val suppressShareToApp =
                        chip == shareToAppChip &&
                            screenRecordChip is OngoingActivityChipModel.Active
                    if (shownSlotsRemaining > 0 && !suppressShareToApp) {
                        activeChips.add(chip)
                        if (!chip.isHidden) shownSlotsRemaining--
                    } else {
                        overflowChips.add(chip)
                    }
                }

                is OngoingActivityChipModel.Inactive -> inactiveChips.add(chip)
            }
        }

        return MultipleOngoingActivityChipsModel(activeChips, overflowChips, inactiveChips)
    }

    companion object {
        private val TAG = "ChipsViewModel".pad()
        const val SETTING_CHIPS_LIMIT = "status_bar_chips_limit"
        const val DEFAULT_CHIPS_LIMIT = 2

        const val SETTING_MULTI_STYLE = "status_bar_chips_multi_style"
        const val DEFAULT_MULTI_STYLE = 0 // 0=All Expanded, 1=Primary Expanded, 2=All Compact

        const val SETTING_MEDIA_ART_STYLE = "status_bar_chips_media_art_style"

        const val SETTING_MONET_COLORS = "status_bar_chips_monet_colors"
        const val SETTING_MONET_ICONS = "status_bar_chips_monet_icons"

        const val SETTING_ENABLE_PRIVACY = "status_bar_chip_enable_privacy"
        const val SETTING_ENABLE_SCREEN_RECORD = "status_bar_chip_enable_screen_record"
        const val SETTING_ENABLE_SHARE_TO_APP = "status_bar_chip_enable_share_to_app"
        const val SETTING_ENABLE_CAST = "status_bar_chip_enable_cast"
        const val SETTING_ENABLE_CALL = "status_bar_chip_enable_call"
        const val SETTING_ENABLE_TIMER = "status_bar_chip_enable_timer"
        const val SETTING_ENABLE_NAVIGATION = "status_bar_chip_enable_navigation"
        const val SETTING_ENABLE_HOTSPOT = "status_bar_chip_enable_hotspot"
        const val SETTING_ENABLE_DOWNLOAD = "status_bar_chip_enable_download"
        const val SETTING_ENABLE_MEDIA = "status_bar_chip_enable_media"
        const val SETTING_ENABLE_BLUETOOTH = "status_bar_chip_enable_bluetooth"
        const val SETTING_ENABLE_NOTIFS = "status_bar_chip_enable_notifs"

        const val SETTING_COLOR_CAM_MIC = "status_bar_chips_color_cam_mic"
        const val SETTING_COLOR_LOCATION = "status_bar_chips_color_location"
        const val SETTING_COLOR_COMBO = "status_bar_chips_color_combo"
        const val SETTING_COLOR_ALL = "status_bar_chips_color_all"
    }
}
