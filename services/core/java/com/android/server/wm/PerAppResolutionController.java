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

package com.android.server.wm;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.CompatibilityInfo.CompatScale;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for Per-App Resolution Scaling in WindowManagerService.
 * Applies internal buffer scaling to target application windows while SurfaceFlinger
 * projects them to full screen, and transforms touch coordinates inversely 1:1.
 */
public class PerAppResolutionController implements CompatScaleProvider {
    private static final String TAG = "PerAppResolutionController";
    public static final String SETTING_KEY = "game_resolution_scale_map";

    private final ActivityTaskManagerService mAtmService;
    private final Context mContext;
    private final Handler mHandler;
    private final Map<String, Float> mScaleMap = new ConcurrentHashMap<>();
    private final ContentObserver mObserver;
    private boolean mObserverRegistered = false;

    public PerAppResolutionController(@NonNull ActivityTaskManagerService atmService,
                                     @NonNull Context context,
                                     @NonNull Handler handler) {
        mAtmService = atmService;
        mContext = context;
        mHandler = handler;

        mObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange, @Nullable Uri uri) {
                updateScaleMap();
            }
        };
    }

    public void onSystemReady() {
        mHandler.post(this::init);
    }

    private synchronized void init() {
        if (!mObserverRegistered) {
            try {
                mContext.getContentResolver().registerContentObserver(
                        Settings.Global.getUriFor(SETTING_KEY),
                        false, mObserver, UserHandle.USER_ALL);
                mContext.getContentResolver().registerContentObserver(
                        Settings.System.getUriFor(SETTING_KEY),
                        false, mObserver, UserHandle.USER_ALL);
                mObserverRegistered = true;
            } catch (Throwable t) {
                Slog.w(TAG, "Failed to register content observer for " + SETTING_KEY + ": " + t.getMessage());
            }
        }
        updateScaleMap();
    }

    private void updateScaleMap() {
        try {
            String data = Settings.Global.getString(mContext.getContentResolver(), SETTING_KEY);
            if (TextUtils.isEmpty(data)) {
                data = Settings.System.getString(mContext.getContentResolver(), SETTING_KEY);
            }

            Map<String, Float> oldMap = new HashMap<>(mScaleMap);
            Map<String, Float> newMap = new HashMap<>();
            if (!TextUtils.isEmpty(data)) {
                String[] entries = data.split(",");
                for (String entry : entries) {
                    String[] parts = entry.trim().split(":");
                    if (parts.length == 2) {
                        try {
                            String pkg = parts[0].trim();
                            float scale = Float.parseFloat(parts[1].trim());
                            if (scale >= 0.20f && scale <= 1.0f) {
                                newMap.put(pkg, scale);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            mScaleMap.clear();
            mScaleMap.putAll(newMap);
            Slog.d(TAG, "updateScaleMap: loaded " + mScaleMap.size() + " entries (" + data + ")");

            // Refresh any package whose scale changed
            Set<String> allPackages = new HashSet<>(oldMap.keySet());
            allPackages.addAll(newMap.keySet());
            for (String pkg : allPackages) {
                Float oldVal = oldMap.get(pkg);
                Float newVal = newMap.get(pkg);
                if (!Objects.equals(oldVal, newVal)) {
                    notifyPackageCompatChanged(pkg);
                }
            }
        } catch (Throwable t) {
            Slog.w(TAG, "Failed to update scale map: " + t.getMessage());
        }
    }

    private void notifyPackageCompatChanged(String packageName) {
        if (packageName == null) return;
        mHandler.post(() -> {
            synchronized (mAtmService.mGlobalLock) {
                try {
                    ApplicationInfo ai = null;
                    try {
                        ai = mContext.getPackageManager().getApplicationInfo(packageName, 0);
                    } catch (Throwable ignored) {}

                    final CompatibilityInfo ci = ai != null
                            ? mAtmService.compatibilityInfoForPackageLocked(ai)
                            : CompatibilityInfo.DEFAULT_COMPATIBILITY_INFO;

                    final ArrayList<WindowProcessController> restartedApps = new ArrayList<>();
                    mAtmService.mRootWindowContainer.forAllWindows(w -> {
                        final ActivityRecord ar = w.mActivityRecord;
                        if (ar != null) {
                            if (ar.packageName.equals(packageName) && !restartedApps.contains(ar.app)) {
                                ar.restartProcessIfVisible();
                                restartedApps.add(ar.app);
                            }
                        } else if (w.getProcess() != null && w.getProcess().mInfo != null
                                && packageName.equals(w.getProcess().mInfo.packageName)) {
                            w.updateGlobalScale();
                        }
                    }, true /* traverseTopToBottom */);

                    SparseArray<WindowProcessController> pidMap = mAtmService.mProcessMap.getPidMap();
                    for (int i = pidMap.size() - 1; i >= 0; i--) {
                        final WindowProcessController app = pidMap.valueAt(i);
                        if (app == null || !app.containsPackage(packageName) || restartedApps.contains(app)) {
                            continue;
                        }
                        try {
                            if (app.hasThread()) {
                                app.getThread().updatePackageCompatibilityInfo(packageName, ci);
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) {
                    Slog.w(TAG, "Failed to notify compat change for " + packageName + ": " + t.getMessage());
                }
            }
        });
    }

    @Override
    @Nullable
    public CompatScale getCompatScale(@NonNull String packageName, int uid) {
        if (packageName == null) return null;
        Float scale = mScaleMap.get(packageName);
        if (scale != null && scale >= 0.20f && scale < 0.999f) {
            float compatFactor = 1.0f / scale;
            Slog.d(TAG, "Applying resolution scale for " + packageName + ": scale=" + scale + " -> compatFactor=" + compatFactor);
            return new CompatScale(compatFactor);
        }
        return null;
    }
}
