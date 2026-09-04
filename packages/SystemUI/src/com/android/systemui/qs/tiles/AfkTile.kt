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

package com.android.systemui.qs.tiles

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import androidx.annotation.Nullable
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.qs.QSTile.Icon
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.afk.AfkController
import javax.inject.Inject

/** Quick Settings Tile: AFK Mode / Background Stream **/
class AfkTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val panelInteractor: PanelInteractor,
    private val afkController: AfkController
) : QSTileImpl<BooleanState>(
    host,
    uiEventLogger,
    backgroundLooper,
    mainHandler,
    falsingManager,
    metricsLogger,
    statusBarStateController,
    activityStarter,
    qsLogger
), AfkController.Callback {

    companion object {
        const val TILE_SPEC = "afk_mode"
    }

    private val iconOn: Icon = ResourceIcon.get(R.drawable.ic_qs_afk_on)
    private val iconOff: Icon = ResourceIcon.get(R.drawable.ic_qs_afk_off)

    init {
        afkController.addCallback(this)
    }

    override fun newTileState(): BooleanState {
        return BooleanState().apply {
            handlesLongClick = false
        }
    }

    override fun handleDestroy() {
        super.handleDestroy()
        afkController.removeCallback(this)
    }

    override fun handleClick(@Nullable expandable: Expandable?) {
        // Collapse notification / QS panels immediately
        panelInteractor.collapsePanels()

        // Toggle AFK Mode
        afkController.toggleAfkMode()
    }

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val active = afkController.isAfkActive
        state.value = active
        state.label = mContext.getString(R.string.quick_settings_afk_label)
        state.icon = if (active) iconOn else iconOff
        state.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        state.secondaryLabel = mContext.getString(
            if (active) R.string.quick_settings_afk_active else R.string.quick_settings_afk_inactive
        )
        state.contentDescription = state.label
    }

    override fun getLongClickIntent(): Intent? {
        return null
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_afk_label)
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.VIEW_UNKNOWN
    }

    override fun onAfkStateChanged(isActive: Boolean) {
        refreshState()
    }
}
