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

package com.android.systemui.statusbar.chips.hotspot.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.island.ChipIslandPopup
import com.android.systemui.statusbar.chips.hotspot.domain.interactor.HotspotChipInteractor
import com.android.systemui.statusbar.chips.hotspot.domain.model.HotspotChipModel
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

/** View model for the Wi-Fi hotspot / tethering chip shown in the status bar. */
@SysUISingleton
class HotspotChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: HotspotChipInteractor,
    private val activityStarter: ActivityStarter,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hotspotPopup: ChipIslandPopup? = null

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.hotspotState
            .map { state ->
                when (state) {
                    is HotspotChipModel.Inactive -> {
                        mainHandler.post {
                            hotspotPopup?.dismissWithAnimation()
                        }
                        OngoingActivityChipModel.Inactive()
                    }
                    is HotspotChipModel.Active -> prepareChip(state)
                }
            }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                OngoingActivityChipModel.Inactive(),
            )

    private fun prepareChip(state: HotspotChipModel.Active): OngoingActivityChipModel.Active {
        val key = KEY
        val contentDesc = ContentDescription.Resource(R.string.quick_settings_hotspot_label)

        val icon =
            OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                Icon.Resource(
                    resId = R.drawable.ic_hotspot,
                    contentDescription = contentDesc,
                )
            )

        val variants = mutableListOf<String>()
        if (state.numConnectedDevices > 0) {
            variants.add("${state.numConnectedDevices} disp.")
            variants.add("${state.numConnectedDevices}")
        } else {
            variants.add(context.getString(R.string.quick_settings_hotspot_label))
            variants.add("Hotspot")
            variants.add("0")
        }

        val content =
            if (variants.size == 1) {
                OngoingActivityChipModel.Content.Text(variants.first())
            } else {
                OngoingActivityChipModel.Content.TextVariants(variants)
            }

        val colors = ColorsModel.DynamicThemed()

        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ExpandAction { _ ->
                mainHandler.post {
                    showOrTogglePopup(state)
                }
            }

        return OngoingActivityChipModel.Active(
            key = key,
            icon = icon,
            content = content,
            colors = colors,
            clickBehavior = clickBehavior,
        )
    }

    private fun showOrTogglePopup(state: HotspotChipModel.Active) {
        val popup = hotspotPopup ?: ChipIslandPopup(context, "HotspotIslandPopup").also { hotspotPopup = it }

        if (popup.isShowing) {
            popup.dismissWithAnimation()
            return
        }

        // Left button: Connected devices / client list
        popup.setLeftButton(
            iconRes = R.drawable.ic_hotspot_island_devices,
            contentDescription = "Connected Devices",
        ) {
            try {
                val intent = Intent(Settings.ACTION_TETHER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                activityStarter.postStartActivityDismissingKeyguard(intent, 0)
                popup.dismissWithAnimation()
            } catch (ignored: Exception) {}
        }

        // Center button: Immediate power killswitch (turns off hotspot)
        popup.setCenterButton(
            iconRes = R.drawable.ic_hotspot_island_power,
            contentDescription = "Turn off Hotspot",
            customTint = Color.parseColor("#00E5FF"), // Vibrant cyan power icon
        ) {
            try {
                interactor.disableHotspot()
                popup.dismissWithAnimation()
            } catch (ignored: Exception) {}
        }

        // Right button: Hotspot settings
        popup.setRightButton(
            iconRes = R.drawable.ic_hotspot_island_settings,
            contentDescription = "Hotspot Settings",
        ) {
            try {
                val intent = Intent(Settings.ACTION_TETHER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                activityStarter.postStartActivityDismissingKeyguard(intent, 0)
                popup.dismissWithAnimation()
            } catch (ignored: Exception) {}
        }

        // Apply Monet theme with cyan / teal accent
        popup.applyMonetTheme(Color.parseColor("#00ACC1"))
        popup.show()
    }

    companion object {
        const val KEY = "hotspotChip"
    }
}
