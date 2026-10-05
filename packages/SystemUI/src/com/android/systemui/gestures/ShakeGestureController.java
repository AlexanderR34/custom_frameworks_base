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

package com.android.systemui.gestures;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ServiceManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ScreenshotHelper;
import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.util.settings.SystemSettings;

import javax.inject.Inject;

/**
 * Controller that listens to accelerometer sensor events to detect shake motions
 * and perform configured user actions (Moto Actions style).
 */
@SysUISingleton
public class ShakeGestureController implements CoreStartable, SensorEventListener {

    private static final String TAG = "ShakeGestureController";
    private static final boolean DEBUG = false;

    private static final int SHAKE_MIN_INTERVAL_MS = 100;
    private static final int SHAKE_MAX_INTERVAL_MS = 750;
    private static final int SHAKE_COOLDOWN_MS = 800;

    private final Context mContext;
    private final FlashlightController mFlashlightController;
    private final SystemSettings mSystemSettings;
    private final UserTracker mUserTracker;
    private final Handler mMainHandler;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Vibrator mVibrator;

    private boolean mEnabled = false;
    private String mAction = "torch";
    private String mAppPackage = "";
    private int mSensitivity = 3;

    private float[] mGravity = new float[3];
    private boolean mGravityInitialized = false;
    private int mLastPeakSign = 0;
    private long mShakeTimestamp;
    private int mShakeCount;
    private long mLastTriggerTime = 0;

    private final ContentObserver mSettingsObserver;

    @Inject
    public ShakeGestureController(
            @Application Context context,
            FlashlightController flashlightController,
            SystemSettings systemSettings,
            UserTracker userTracker,
            @Main Handler mainHandler
    ) {
        mContext = context;
        mFlashlightController = flashlightController;
        mSystemSettings = systemSettings;
        mUserTracker = userTracker;
        mMainHandler = mainHandler;

        mSettingsObserver = new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateSettings();
            }
        };
    }

    @Override
    public void start() {
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        // Observe settings changes
        mSystemSettings.registerContentObserverForUserAsync(
                Settings.System.getUriFor(Settings.System.SHAKE_GESTURE_ENABLED),
                mSettingsObserver,
                mUserTracker.getUserId()
        );
        mSystemSettings.registerContentObserverForUserAsync(
                Settings.System.getUriFor(Settings.System.SHAKE_GESTURE_ACTION),
                mSettingsObserver,
                mUserTracker.getUserId()
        );
        mSystemSettings.registerContentObserverForUserAsync(
                Settings.System.getUriFor(Settings.System.SHAKE_GESTURE_APP),
                mSettingsObserver,
                mUserTracker.getUserId()
        );
        mSystemSettings.registerContentObserverForUserAsync(
                Settings.System.getUriFor(Settings.System.SHAKE_GESTURE_SENSITIVITY),
                mSettingsObserver,
                mUserTracker.getUserId()
        );

        mUserTracker.addCallback(new UserTracker.Callback() {
            @Override
            public void onUserChanged(int newUser, Context userContext) {
                updateSettings();
            }
        }, mContext.getMainExecutor());

        updateSettings();
    }

    private synchronized void updateSettings() {
        final int userId = mUserTracker.getUserId();
        final boolean enabled = mSystemSettings.getIntForUser(
                Settings.System.SHAKE_GESTURE_ENABLED, 0, userId) == 1;
        final String action = mSystemSettings.getStringForUser(
                Settings.System.SHAKE_GESTURE_ACTION, userId);
        final String app = mSystemSettings.getStringForUser(
                Settings.System.SHAKE_GESTURE_APP, userId);
        final int sensitivity = mSystemSettings.getIntForUser(
                Settings.System.SHAKE_GESTURE_SENSITIVITY, 3, userId);

        mEnabled = enabled;
        mAction = (action != null && !action.isEmpty()) ? action : "torch";
        mAppPackage = (app != null) ? app : "";
        mSensitivity = Math.max(1, Math.min(5, sensitivity));

        if (mEnabled) {
            registerSensorListener();
        } else {
            unregisterSensorListener();
        }
    }

    private void registerSensorListener() {
        if (mSensorManager != null && mAccelerometer != null) {
            mSensorManager.unregisterListener(this);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    private void unregisterSensorListener() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(this);
        }
    }

    private float getSensitivityThreshold() {
        switch (mSensitivity) {
            case 1: return 17.0f; // Low (firm shake required)
            case 2: return 14.0f; // Med-Low
            case 4: return 9.5f;  // Med-High
            case 5: return 7.5f;  // High (easy flick)
            case 3:
            default: return 11.5f; // Medium (natural double shake)
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!mEnabled || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        final float x = event.values[0];
        final float y = event.values[1];
        final float z = event.values[2];

        if (!mGravityInitialized) {
            mGravity[0] = x;
            mGravity[1] = y;
            mGravity[2] = z;
            mGravityInitialized = true;
            return;
        }

        // Low-pass filter to isolate static gravity
        final float alpha = 0.8f;
        mGravity[0] = alpha * mGravity[0] + (1.0f - alpha) * x;
        mGravity[1] = alpha * mGravity[1] + (1.0f - alpha) * y;
        mGravity[2] = alpha * mGravity[2] + (1.0f - alpha) * z;

        // High-pass filter to isolate dynamic linear acceleration
        final float linX = x - mGravity[0];
        final float linY = y - mGravity[1];
        final float linZ = z - mGravity[2];

        final float linAcc = (float) Math.sqrt(linX * linX + linY * linY + linZ * linZ);
        final float threshold = getSensitivityThreshold();

        final long now = System.currentTimeMillis();

        // Check if cooldown is active
        if (now - mLastTriggerTime < SHAKE_COOLDOWN_MS) {
            return;
        }

        if (linAcc > threshold) {
            final long timeDiff = now - mShakeTimestamp;

            // Determine dominant acceleration axis and direction sign
            final float absX = Math.abs(linX);
            final float absY = Math.abs(linY);
            final float absZ = Math.abs(linZ);
            final int currentSign;
            if (absX >= absY && absX >= absZ) {
                currentSign = linX > 0 ? 1 : -1;
            } else if (absY >= absX && absY >= absZ) {
                currentSign = linY > 0 ? 2 : -2;
            } else {
                currentSign = linZ > 0 ? 3 : -3;
            }

            if (mShakeCount == 0) {
                mShakeTimestamp = now;
                mShakeCount = 1;
                mLastPeakSign = currentSign;
            } else if (timeDiff >= SHAKE_MIN_INTERVAL_MS && timeDiff <= SHAKE_MAX_INTERVAL_MS) {
                mShakeTimestamp = now;
                mShakeCount++;
                mLastPeakSign = currentSign;

                // 2 distinct rapid motion peaks trigger the action
                if (mShakeCount >= 2) {
                    mLastTriggerTime = now;
                    mShakeCount = 0;
                    mMainHandler.post(this::performShakeAction);
                }
            } else if (timeDiff > SHAKE_MAX_INTERVAL_MS) {
                // Too slow between shakes, reset cycle
                mShakeTimestamp = now;
                mShakeCount = 1;
                mLastPeakSign = currentSign;
            }
        }
    }

    private void performShakeAction() {
        vibrateFeedback();

        switch (mAction) {
            case "screenshot":
                takeScreenshot();
                break;
            case "assistant":
                launchAssistant();
                break;
            case "media_play_pause":
                toggleMediaPlayPause();
                break;
            case "recents":
                toggleRecentApps();
                break;
            case "notifications":
                expandNotifications();
                break;
            case "app":
                launchApp();
                break;
            case "torch":
            default:
                toggleTorch();
                break;
        }
    }

    private void vibrateFeedback() {
        if (mVibrator != null && mVibrator.hasVibrator()) {
            try {
                mVibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Exception ignored) {}
        }
    }

    private void toggleTorch() {
        if (mFlashlightController != null && mFlashlightController.isAvailable()) {
            mFlashlightController.setFlashlight(!mFlashlightController.isEnabled());
        }
    }

    private void takeScreenshot() {
        ScreenshotHelper helper = new ScreenshotHelper(mContext);
        helper.takeScreenshot(WindowManager.TAKE_SCREENSHOT_FULLSCREEN, mMainHandler, null);
    }

    private void launchAssistant() {
        try {
            Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch voice assistant", e);
        }
    }

    private void toggleMediaPlayPause() {
        try {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle media play/pause", e);
        }
    }

    private void toggleRecentApps() {
        try {
            IStatusBarService sb = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
            if (sb != null) {
                sb.toggleRecentApps();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to toggle recent apps", e);
        }
    }

    private void expandNotifications() {
        try {
            IStatusBarService sb = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
            if (sb != null) {
                sb.expandNotificationsPanel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to expand notifications panel", e);
        }
    }

    private void launchApp() {
        if (mAppPackage == null || mAppPackage.isEmpty()) {
            return;
        }
        try {
            Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(mAppPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(launchIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app: " + mAppPackage, e);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
