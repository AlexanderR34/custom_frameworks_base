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

package com.android.systemui.statusbar.chips.bluetooth.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.bluetooth.domain.interactor.BluetoothChipInteractor
import com.android.systemui.statusbar.chips.bluetooth.domain.model.BluetoothChipModel
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.chips.uievents.StatusBarChipsUiEventLogger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the Bluetooth accessory / earbuds battery chip shown in the status bar. */
@SysUISingleton
class BluetoothChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: BluetoothChipInteractor,
    private val activityStarter: ActivityStarter,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.bluetoothState
            .map { state ->
                when (state) {
                    is BluetoothChipModel.Inactive -> OngoingActivityChipModel.Inactive()
                    is BluetoothChipModel.Active -> prepareChip(state)
                }
            }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                OngoingActivityChipModel.Inactive(),
            )

    private fun prepareChip(state: BluetoothChipModel.Active): OngoingActivityChipModel.Active {
        val key = KEY
        val contentDesc = ContentDescription.Loaded(state.deviceName)

        val icon =
            if (state.deviceIcon != null) {
                OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                    Icon.Loaded(
                        drawable = state.deviceIcon,
                        contentDescription = contentDesc,
                    )
                )
            } else {
                OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                    Icon.Resource(
                        resId = com.android.settingslib.R.drawable.ic_earbuds_advanced,
                        contentDescription = contentDesc,
                    )
                )
            }

        val variants = mutableListOf<String>()
        if (state.batteryLevel in 0..100) {
            variants.add("${state.batteryLevel}%")
            variants.add("${state.deviceName} ${state.batteryLevel}%")
        } else {
            variants.add("BT")
            variants.add(state.deviceName)
        }

        val content =
            if (variants.size == 1) {
                OngoingActivityChipModel.Content.Text(variants.first())
            } else {
                OngoingActivityChipModel.Content.TextVariants(variants)
            }

        val colors =
            if (state.batteryLevel in 0..20) {
                ColorsModel.Red
            } else {
                ColorsModel.DynamicThemed()
            }

        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ExpandAction {
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    activityStarter.postStartActivityDismissingKeyguard(intent, 0)
                } catch (ignored: Exception) {}
            }

        return OngoingActivityChipModel.Active(
            key = key,
            icon = icon,
            content = content,
            colors = colors,
            clickBehavior = clickBehavior,
        )
    }

    companion object {
        const val KEY = "bluetoothChip"
    }
}
