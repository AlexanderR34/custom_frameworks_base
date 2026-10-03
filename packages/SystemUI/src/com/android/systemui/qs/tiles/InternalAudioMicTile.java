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

package com.android.systemui.qs.tiles;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.UserSettingObserver;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.settings.SystemSettings;

import javax.inject.Inject;

/** Quick settings tile: Internal Audio to Mic Injection **/
public class InternalAudioMicTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "internal_audio_mic";

    @Nullable
    private Icon mIcon = null;

    private static final Intent SOUND_SETTINGS =
            new Intent(Settings.ACTION_SOUND_SETTINGS);

    private final UserSettingObserver mSetting;

    @Inject
    public InternalAudioMicTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            SystemSettings systemSettings,
            UserTracker userTracker
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);

        mSetting = new UserSettingObserver(systemSettings, mHandler,
                Settings.System.INTERNAL_AUDIO_MIC_INJECTION, userTracker.getUserId()) {
            @Override
            protected void handleValueChanged(int value, boolean observedChange) {
                handleRefreshState(value);
            }
        };
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        final boolean newState = !mState.value;
        mSetting.setValue(newState ? 1 : 0);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return SOUND_SETTINGS;
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        final int value = arg instanceof Integer ? (Integer) arg : mSetting.getValue();
        final boolean enabled = value != 0;
        state.value = enabled;
        state.label = mContext.getString(R.string.quick_settings_internal_audio_mic_label);
        if (mIcon == null) {
            mIcon = maybeLoadResourceIcon(R.drawable.ic_qs_internal_audio_mic);
        }
        state.icon = mIcon;
        if (enabled) {
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_internal_audio_mic_on);
            state.state = Tile.STATE_ACTIVE;
        } else {
            state.contentDescription = mContext.getString(
                    R.string.accessibility_quick_settings_internal_audio_mic_off);
            state.state = Tile.STATE_INACTIVE;
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_internal_audio_mic_label);
    }

    @Override
    public int getMetricsCategory() {
        return VIEW_UNKNOWN;
    }

    @Override
    public void handleSetListening(boolean listening) {
        mSetting.setListening(listening);
    }
}
