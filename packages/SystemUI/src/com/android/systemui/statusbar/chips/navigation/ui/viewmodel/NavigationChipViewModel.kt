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

package com.android.systemui.statusbar.chips.navigation.ui.viewmodel

import android.content.Context
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.chips.navigation.domain.interactor.NavigationChipInteractor
import com.android.systemui.statusbar.chips.navigation.domain.model.NavigationChipModel
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

/** View model for the turn-by-turn GPS navigation chip shown in the status bar. */
@SysUISingleton
class NavigationChipViewModel
@Inject
constructor(
    @Main private val context: Context,
    @Application private val scope: CoroutineScope,
    private val interactor: NavigationChipInteractor,
    private val activityStarter: ActivityStarter,
    private val uiEventLogger: StatusBarChipsUiEventLogger,
) : OngoingActivityChipViewModel {

    override val chip: StateFlow<OngoingActivityChipModel> =
        interactor.navigationState
            .map { state ->
                when (state) {
                    is NavigationChipModel.Inactive -> OngoingActivityChipModel.Inactive()
                    is NavigationChipModel.Active -> prepareChip(state)
                }
            }
            .stateIn(
                scope,
                SharingStarted.Lazily,
                OngoingActivityChipModel.Inactive(),
            )

    private fun prepareChip(state: NavigationChipModel.Active): OngoingActivityChipModel.Active {
        val key = "$KEY_PREFIX${state.packageName}"
        val contentDesc = ContentDescription.Loaded(
            state.distanceText?.toString() ?: state.directionText?.toString() ?: "Navigation"
        )

        val iconDrawable = state.maneuverIcon ?: state.appIcon
        val icon: OngoingActivityChipModel.ChipIcon? =
            if (iconDrawable != null) {
                OngoingActivityChipModel.ChipIcon.FullColorIcon(
                    Icon.Loaded(
                        drawable = iconDrawable,
                        contentDescription = contentDesc,
                    )
                )
            } else {
                OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon(
                    notificationKey = state.notificationKey,
                    contentDescription = contentDesc,
                )
            }

        val variants = mutableListOf<String>()
        val fallbackText = state.distanceText?.toString()?.takeIf { it.isNotBlank() }
            ?: state.directionText?.toString()?.takeIf { it.isNotBlank() }
            ?: state.etaText?.toString()?.takeIf { it.isNotBlank() }
            ?: "GPS"

        state.directionText?.toString()?.takeIf { it.isNotBlank() }?.let { variants.add(it) }
        state.distanceText?.toString()?.takeIf { it.isNotBlank() }?.let { variants.add(it) }
        state.etaText?.toString()?.takeIf { it.isNotBlank() }?.let { variants.add(it) }

        val content =
            when {
                variants.size == 1 -> OngoingActivityChipModel.Content.Text(variants.first())
                variants.size > 1 -> OngoingActivityChipModel.Content.TextVariants(variants)
                else -> OngoingActivityChipModel.Content.Text(fallbackText)
            }

        val colors = ColorsModel.AccentThemed

        val clickBehavior =
            if (state.intent != null) {
                OngoingActivityChipModel.ClickBehavior.ExpandAction {
                    try {
                        activityStarter.postStartActivityDismissingKeyguard(state.intent)
                    } catch (ignored: Exception) {}
                }
            } else {
                OngoingActivityChipModel.ClickBehavior.None
            }

        return OngoingActivityChipModel.Active(
            key = key,
            notificationKey = state.notificationKey,
            managingPackageName = state.packageName,
            icon = icon,
            content = content,
            colors = colors,
            clickBehavior = clickBehavior,
        )
    }

    companion object {
        const val KEY_PREFIX = "navigationChip-"
    }
}
