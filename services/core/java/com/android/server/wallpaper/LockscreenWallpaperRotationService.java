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

package com.android.server.wallpaper;

import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import java.io.File;

/**
 * System service listener that rotates the lock screen wallpaper dynamically from a set of user-selected
 * photos each time the screen turns off or on (Samsung-style Dynamic Lockscreen Multi-pack wallpaper).
 */
public class LockscreenWallpaperRotationService {
    private static final String TAG = "LockscreenWallpaperRotationService";
    private final Context mContext;
    private final Handler mHandler;
    private static LockscreenWallpaperRotationService sInstance;

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
                mHandler.post(() -> rotateLockscreenWallpaper());
            }
        }
    };

    public LockscreenWallpaperRotationService(Context context) {
        mContext = context;
        HandlerThread thread = new HandlerThread("LockscreenWallpaperRotation");
        thread.start();
        mHandler = new Handler(thread.getLooper());
        registerReceiver();
    }

    public static synchronized LockscreenWallpaperRotationService getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LockscreenWallpaperRotationService(context.getApplicationContext());
        }
        return sInstance;
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mScreenReceiver, filter);
    }

    private void rotateLockscreenWallpaper() {
        boolean enabled = Settings.System.getInt(
                mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MULTI_WALLPAPER_ENABLED, 0) == 1;

        if (!enabled) {
            return;
        }

        String rawPaths = Settings.System.getString(
                mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MULTI_WALLPAPER_FILES);

        if (TextUtils.isEmpty(rawPaths)) {
            return;
        }

        String[] paths = rawPaths.split(";");
        if (paths.length == 0) {
            return;
        }

        int currentIndex = Settings.System.getInt(
                mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MULTI_WALLPAPER_INDEX, 0);

        int nextIndex = (currentIndex + 1) % paths.length;
        Settings.System.putInt(
                mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_MULTI_WALLPAPER_INDEX, nextIndex);

        String targetPath = paths[nextIndex];
        File file = new File(targetPath);
        if (!file.exists()) {
            Slog.w(TAG, "Lockscreen wallpaper file does not exist: " + targetPath);
            return;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                WallpaperManager wm = WallpaperManager.getInstance(mContext);
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
                Slog.d(TAG, "Successfully updated lockscreen wallpaper to: " + targetPath);
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error setting lockscreen wallpaper: " + e.getMessage());
        }
    }
}
