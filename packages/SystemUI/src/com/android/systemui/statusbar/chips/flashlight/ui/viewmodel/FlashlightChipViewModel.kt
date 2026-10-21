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

package com.android.systemui.statusbar.chips.flashlight.ui.viewmodel

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.flashlight.domain.interactor.FlashlightChipInteractor
import com.android.systemui.statusbar.chips.flashlight.domain.model.FlashlightChipModel
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipViewModel
import com.android.systemui.statusbar.phone.island.ChipIslandPopup
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** View model for the Flashlight ongoing activity chip shown in the status bar. */
@SysUISingleton
class FlashlightChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: FlashlightChipInteractor,
) : OngoingActivityChipViewModel {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var flashlightPopup: ChipIslandPopup? = null

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.flashlightState
            .map { state ->
                when (state) {
                    is FlashlightChipModel.Inactive -> {
                        mainHandler.post {
                            flashlightPopup?.dismissWithAnimation()
                        }
                        OngoingActivityChipModel.Inactive()
                    }
                    is FlashlightChipModel.Active -> prepareChip(state)
                }
            }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                OngoingActivityChipModel.Inactive(),
            )

    private fun prepareChip(state: FlashlightChipModel.Active): OngoingActivityChipModel.Active {
        val key = KEY
        val contentDesc = ContentDescription.Resource(R.string.quick_settings_flashlight_label)

        val icon =
            OngoingActivityChipModel.ChipIcon.SingleColorIcon(
                Icon.Resource(
                    resId = R.drawable.ic_flashlight,
                    contentDescription = contentDesc,
                )
            )

        val variants = listOf(
            context.getString(R.string.quick_settings_flashlight_label),
            "Linterna",
            "On",
        )

        val content = OngoingActivityChipModel.Content.TextVariants(variants)
        val colors = ColorsModel.DynamicThemed()

        val clickBehavior =
            OngoingActivityChipModel.ClickBehavior.ExpandAction { _ ->
                mainHandler.post {
                    showOrTogglePopup()
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

    private fun showOrTogglePopup() {
        val popup = flashlightPopup ?: ChipIslandPopup(context, "FlashlightIslandPopup").also { flashlightPopup = it }

        if (popup.isShowing) {
            popup.dismissWithAnimation()
            return
        }

        updatePopupButtons(popup)
        popup.applyMonetTheme(Color.parseColor("#FFD600")) // Vibrant Gold / Flashlight yellow
        popup.show()
    }

    private fun updatePopupButtons(popup: ChipIslandPopup) {
        val isEnabled = interactor.isFlashlightEnabled()

        // Center button: Toggle On/Off dynamically
        val powerIcon = if (isEnabled) R.drawable.vd_flashlight_on else R.drawable.vd_flashlight_off
        val powerTint = if (isEnabled) Color.parseColor("#FFD600") else Color.parseColor("#757575")

        popup.setCenterButton(
            iconRes = powerIcon,
            contentDescription = if (isEnabled) "Turn Off Flashlight" else "Turn On Flashlight",
            customTint = powerTint,
        ) {
            val newState = !interactor.isFlashlightEnabled()
            interactor.setFlashlight(newState)
            if (!newState) {
                popup.dismissWithAnimation()
            } else {
                updatePopupButtons(popup)
            }
        }

        // Left button: Low / Soft Intensity (Min Level)
        popup.setLeftButton(
            iconRes = R.drawable.ic_brightness_low,
            contentDescription = "Soft Flashlight",
            customTint = Color.parseColor("#FFF59D"),
        ) {
            try {
                if (!interactor.isFlashlightEnabled()) {
                    interactor.setFlashlight(true)
                }
                interactor.setLevel(1)
            } catch (ignored: Exception) {}
        }

        // Right button: Turbo / Max Intensity (Max Level)
        popup.setRightButton(
            iconRes = R.drawable.ic_brightness_full,
            contentDescription = "Turbo Flashlight",
            customTint = Color.parseColor("#FF6D00"),
        ) {
            try {
                if (!interactor.isFlashlightEnabled()) {
                    interactor.setFlashlight(true)
                }
                val max = interactor.getMaxLevel()
                interactor.setLevel(max)
            } catch (ignored: Exception) {}
        }
    }

    companion object {
        const val KEY = "flashlightChip"
    }
}
