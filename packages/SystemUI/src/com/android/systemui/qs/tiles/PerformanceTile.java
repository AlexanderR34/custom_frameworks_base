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

package com.android.systemui.qs.tiles;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
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
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import javax.inject.Inject;

/**
 * Quick settings tile: Performance Mode (Ultimate).
 * - Locks display refresh rate to 120Hz.
 * - Boosts CPU/GPU responsiveness profiles.
 * - Activates the official HyperOS electric blue battery indicator with speed chevrons.
 */
public class PerformanceTile extends QSTileImpl<BooleanState> {

    public static final String TILE_SPEC = "performance";
    private static final String SAVED_MIN_REFRESH = "performance_mode_saved_min_refresh_rate";

    private final ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            refreshState();
        }
    };

    @Inject
    public PerformanceTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = true;
        return state;
    }

    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        ContentResolver cr = mContext.getContentResolver();
        if (listening) {
            cr.registerContentObserver(
                    Settings.System.getUriFor("performance_mode"),
                    false, mObserver, UserHandle.USER_ALL);
            cr.registerContentObserver(
                    Settings.Secure.getUriFor("power_performance_mode_open"),
                    false, mObserver, UserHandle.USER_ALL);
        } else {
            cr.unregisterContentObserver(mObserver);
        }
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        boolean newState = !(mState != null && mState.value);
        setPerformanceMode(mContext, newState);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_performance_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        boolean isPerf = isPerformanceModeEnabled(mContext);
        state.value = isPerf;
        state.state = isPerf ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.icon = ResourceIcon.get(R.drawable.battery_meter_bg_performance);
        state.label = mContext.getString(R.string.quick_settings_performance_label);
        state.secondaryLabel = isPerf
                ? mContext.getString(R.string.quick_settings_performance_active_label)
                : mContext.getString(R.string.quick_settings_performance_inactive_label);
        state.contentDescription = state.label;
    }

    public static boolean isPerformanceModeEnabled(Context context) {
        ContentResolver cr = context.getContentResolver();
        int u = UserHandle.USER_CURRENT;
        if (Settings.Secure.getIntForUser(cr, "power_performance_mode_open", 0, u) == 1) return true;
        if (Settings.System.getIntForUser(cr, "performance_mode", 0, u) == 1) return true;
        if (Settings.System.getIntForUser(cr, "high_performance_mode", 0, u) == 1) return true;
        String pMode = Settings.System.getStringForUser(cr, "power_mode", u);
        if ("performance".equalsIgnoreCase(pMode) || "ultimate".equalsIgnoreCase(pMode)) return true;
        String secMode = Settings.Secure.getStringForUser(cr, "power_mode", u);
        return "performance".equalsIgnoreCase(secMode) || "ultimate".equalsIgnoreCase(secMode);
    }

    public static void setPerformanceMode(Context context, boolean enabled) {
        ContentResolver cr = context.getContentResolver();
        int u = UserHandle.USER_CURRENT;

        // 1. Lock / Unlock 120Hz refresh rate
        try {
            if (enabled) {
                float currentMin = Settings.System.getFloatForUser(cr, Settings.System.MIN_REFRESH_RATE, 60.0f, u);
                Settings.System.putFloatForUser(cr, SAVED_MIN_REFRESH, currentMin, u);
                Settings.System.putFloatForUser(cr, Settings.System.MIN_REFRESH_RATE, 120.0f, u);
                Settings.System.putFloatForUser(cr, Settings.System.PEAK_REFRESH_RATE, 120.0f, u);
            } else {
                float savedMin = Settings.System.getFloatForUser(cr, SAVED_MIN_REFRESH, 60.0f, u);
                Settings.System.putFloatForUser(cr, Settings.System.MIN_REFRESH_RATE, savedMin, u);
            }
        } catch (Exception ignored) {}

        // 2. Hardware scaling properties
        try {
            android.os.SystemProperties.set("sys.perf.profile", enabled ? "1" : "0");
            android.os.SystemProperties.set("persist.sys.perf_mode", enabled ? "1" : "0");
        } catch (Exception ignored) {}

        // 3. Update Settings keys
        Settings.System.putIntForUser(cr, "performance_mode", enabled ? 1 : 0, u);
        Settings.Secure.putIntForUser(cr, "power_performance_mode_open", enabled ? 1 : 0, u);
        Settings.System.putStringForUser(cr, "power_mode", enabled ? "performance" : "normal", u);

        // 4. Notify SystemUI
        Intent intent = new Intent("com.android.systemui.ACTION_PERFORMANCE_MODE_CHANGED");
        intent.putExtra("enabled", enabled);
        context.sendBroadcastAsUser(intent, UserHandle.ALL);
    }
}
