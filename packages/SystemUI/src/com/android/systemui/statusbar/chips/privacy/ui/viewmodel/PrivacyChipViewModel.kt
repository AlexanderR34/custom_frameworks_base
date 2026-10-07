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

package com.android.systemui.statusbar.chips.privacy.ui.viewmodel

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.privacy.PrivacyDialogControllerV2
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.privacy.domain.interactor.PrivacyChipInteractor
import com.android.systemui.statusbar.chips.privacy.domain.model.PrivacyChipModel
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the Camera / Mic / Location Privacy chip shown in the status bar. */
@SysUISingleton
class PrivacyChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: PrivacyChipInteractor,
    private val privacyDialogControllerV2: PrivacyDialogControllerV2,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {

    private val privacySettingsVersion: Flow<Long> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(System.currentTimeMillis())
                    }
                }
            try {
                val keys = listOf(
                    SETTING_MONET_COLORS,
                    SETTING_MONET_ICONS,
                    SETTING_COLOR_CAM_MIC,
                    SETTING_COLOR_LOCATION,
                    SETTING_COLOR_COMBO,
                    SETTING_COLOR_ALL,
                )
                for (key in keys) {
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

    override val chip: StateFlow<OngoingActivityChipModel> =
        combine(interactor.privacyState, privacySettingsVersion) { state, _ ->
            when (state) {
                is PrivacyChipModel.Inactive -> OngoingActivityChipModel.Inactive()
                is PrivacyChipModel.Active -> prepareChip(state)
            }
        }
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(),
            OngoingActivityChipModel.Inactive(),
        )

    private fun prepareChip(state: PrivacyChipModel.Active): OngoingActivityChipModel.Active {
        val key = KEY
        val contentDesc = ContentDescription.Loaded(state.appName)

        val icon =
            if (state.appIcon != null) {
                OngoingActivityChipModel.ChipIcon.FullColorIcon(
                    Icon.Loaded(
                        drawable = state.appIcon,
                        contentDescription = contentDesc,
                    )
                )
            } else {
                null
            }

        val sensorIconsList = mutableListOf<Icon>()
        if (state.hasCam) {
            sensorIconsList.add(
                Icon.Resource(
                    R.drawable.ic_privacy_camera,
                    ContentDescription.Loaded("Camera"),
                )
            )
        }
        if (state.hasMic) {
            sensorIconsList.add(
                Icon.Resource(
                    R.drawable.ic_privacy_mic,
                    ContentDescription.Loaded("Microphone"),
                )
            )
        }
        if (state.hasLocation) {
            sensorIconsList.add(
                Icon.Resource(
                    R.drawable.ic_privacy_location,
                    ContentDescription.Loaded("Location"),
                )
            )
        }

        val content = if (sensorIconsList.isNotEmpty()) {
            OngoingActivityChipModel.Content.SensorIcons(sensorIconsList)
        } else {
            OngoingActivityChipModel.Content.IconOnly
        }

        val isMonetEnabled = try {
            android.provider.Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_MONET_COLORS,
                0,
                android.os.UserHandle.USER_CURRENT,
            ) == 1
        } catch (e: Exception) {
            false
        }
        val isMonetIconsEnabled = try {
            android.provider.Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_MONET_ICONS,
                0,
                android.os.UserHandle.USER_CURRENT,
            ) == 1
        } catch (e: Exception) {
            false
        }

        val colors: ColorsModel = if (isMonetEnabled) {
            ColorsModel.AccentThemed(useSecondaryForIcons = isMonetIconsEnabled)
        } else {
            val fgColor = when {
                // 1. All three sensors active
                state.hasCam && state.hasMic && state.hasLocation ->
                    getCustomColor(SETTING_COLOR_ALL, DEFAULT_COLOR_ALL)

                // 2. Camera or Mic with Location
                (state.hasCam || state.hasMic) && state.hasLocation ->
                    getCustomColor(SETTING_COLOR_COMBO, DEFAULT_COLOR_COMBO)

                // 3. Location only
                state.hasLocation && !state.hasCam && !state.hasMic ->
                    getCustomColor(SETTING_COLOR_LOCATION, DEFAULT_COLOR_LOCATION)

                // 4. Camera, Mic, or Camera+Mic
                else ->
                    getCustomColor(SETTING_COLOR_CAM_MIC, DEFAULT_COLOR_CAM_MIC)
            }
            buildCustomColorsModel(fgColor)
        }

        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ExpandAction {
                try {
                    privacyDialogControllerV2.showDialog(context)
                } catch (ignored: Exception) {}
            }

        return OngoingActivityChipModel.Active(
            key = key,
            managingPackageName = state.packageName,
            isImportantForPrivacy = true,
            icon = icon,
            content = content,
            colors = colors,
            clickBehavior = clickBehavior,
        )
    }

    private fun getCustomColor(settingKey: String, defaultHex: String): Int {
        val hex = try {
            android.provider.Settings.System.getString(
                context.contentResolver,
                settingKey
            )?.trim()
        } catch (e: Exception) {
            null
        }
        if (!hex.isNullOrEmpty()) {
            try {
                val formatted = if (hex.startsWith("#")) hex else "#$hex"
                return android.graphics.Color.parseColor(formatted)
            } catch (ignored: Exception) {}
        }
        return android.graphics.Color.parseColor(defaultHex)
    }

    private fun buildCustomColorsModel(textColor: Int): ColorsModel {
        val r = (android.graphics.Color.red(textColor) * 0.22f).toInt()
        val g = (android.graphics.Color.green(textColor) * 0.22f).toInt()
        val b = (android.graphics.Color.blue(textColor) * 0.22f).toInt()
        val bgColor = android.graphics.Color.rgb(r, g, b)
        return ColorsModel.Custom(bgColor, textColor)
    }

    companion object {
        const val KEY = "privacyChip"
        const val SETTING_MONET_COLORS = "status_bar_chips_monet_colors"
        const val SETTING_MONET_ICONS = "status_bar_chips_monet_icons"
        const val SETTING_COLOR_CAM_MIC = "status_bar_chips_color_cam_mic"
        const val SETTING_COLOR_LOCATION = "status_bar_chips_color_location"
        const val SETTING_COLOR_COMBO = "status_bar_chips_color_combo"
        const val SETTING_COLOR_ALL = "status_bar_chips_color_all"

        private const val DEFAULT_COLOR_CAM_MIC = "#00E676"
        private const val DEFAULT_COLOR_LOCATION = "#0091EA"
        private const val DEFAULT_COLOR_COMBO = "#00F5D4"
        private const val DEFAULT_COLOR_ALL = "#FFB300"
    }
}
