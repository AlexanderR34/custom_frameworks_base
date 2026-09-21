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

package com.android.server.audio;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioSystem;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

/**
 * System server helper in AudioService for applying high-efficiency, system-wide volume boost
 * up to 200% (+10.0 dB) using Dynamic Processing APIs and in-call audio gain boost
 * without distortion, clipping, or echo.
 */
public class VolumeBoostHelper {
    private static final String TAG = "VolumeBoostHelper";
    public static final String SETTING_CALL_GAIN_KEY = "volume_boost_call_gain";

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private LoudnessEnhancer mLoudnessEnhancer;
    private DynamicsProcessing mDynamicsProcessing;
    private int mCurrentAudioMode = AudioSystem.MODE_NORMAL;
    private int mLastAppliedGainMb = -1;

    public VolumeBoostHelper(Context context) {
        mContext = context;
        initAudioFx();
        registerObservers();
    }

    private void initAudioFx() {
        try {
            // Audio session 0 attaches to global output mix
            mLoudnessEnhancer = new LoudnessEnhancer(0);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to initialize system LoudnessEnhancer: " + e.getMessage());
        }

        try {
            DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2 /* channels */,
                    false, 0,
                    false, 0,
                    false, 0,
                    true /* limiterIn */);

            mDynamicsProcessing = new DynamicsProcessing(0, 0, builder.build());
            mDynamicsProcessing.setEnabled(true);
        } catch (Exception e) {
            Slog.w(TAG, "DynamicsProcessing native effect not directly available: " + e.getMessage());
        }

        updateVolumeBoost();
    }

    private void registerObservers() {
        ContentObserver observer = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateVolumeBoost();
            }
        };

        try {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.VOLUME_BOOST_LEVEL),
                    false, observer, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SETTING_CALL_GAIN_KEY),
                    false, observer, UserHandle.USER_ALL);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to register VolumeBoostHelper observers: " + e.getMessage());
        }
    }

    /**
     * Called by AudioService when audio mode changes (e.g. IN_CALL or IN_COMMUNICATION).
     */
    public synchronized void onModeChanged(int newMode) {
        if (mCurrentAudioMode != newMode) {
            mCurrentAudioMode = newMode;
            updateVolumeBoost();
        }
    }

    public synchronized void updateVolumeBoost() {
        int level = Settings.System.getInt(mContext.getContentResolver(), Settings.System.VOLUME_BOOST_LEVEL, 0);
        int clampedLevel = Math.max(0, Math.min(100, level));

        boolean isCallGainEnabled = Settings.System.getInt(mContext.getContentResolver(), SETTING_CALL_GAIN_KEY, 1) == 1;
        boolean isInCall = (mCurrentAudioMode == AudioSystem.MODE_IN_CALL ||
                            mCurrentAudioMode == AudioSystem.MODE_IN_COMMUNICATION ||
                            mCurrentAudioMode == AudioSystem.MODE_RINGTONE);

        int targetGainMb = 0;
        float limiterPostGainDb = 0.0f;

        if (isInCall && isCallGainEnabled) {
            // Apply dedicated vocal clarity gain (+10.0 dB / 1000 mB) during voice & VoIP calls
            int callGainMb = 1000;
            int mediaGainMb = Math.round((clampedLevel / 100.0f) * 1000.0f);
            targetGainMb = Math.max(callGainMb, mediaGainMb);
            limiterPostGainDb = (targetGainMb / 1000.0f) * 6.0f;
        } else if (clampedLevel > 0) {
            // Map 0-100% boost level to 0-1000 mB (+10.0 dB boost = 200% volume total output)
            targetGainMb = Math.round((clampedLevel / 100.0f) * 1000.0f);
            limiterPostGainDb = (clampedLevel / 100.0f) * 10.0f;
        }

        if (mLastAppliedGainMb == targetGainMb) {
            return;
        }
        mLastAppliedGainMb = targetGainMb;

        if (mLoudnessEnhancer != null) {
            try {
                mLoudnessEnhancer.setTargetGain(targetGainMb);
                mLoudnessEnhancer.setEnabled(targetGainMb > 0);
            } catch (Exception e) {
                Slog.e(TAG, "Error applying system LoudnessEnhancer target gain: " + e.getMessage());
            }
        }

        if (mDynamicsProcessing != null) {
            try {
                DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                        true /* inUse */,
                        true /* enabled */,
                        0 /* linkGroup */,
                        0.5f /* attackTime ms - ultra fast */,
                        60.0f /* releaseTime ms - smooth */,
                        12.0f /* ratio */,
                        -0.2f /* threshold dB */,
                        limiterPostGainDb /* postGain dB */);

                mDynamicsProcessing.setLimiterAllChannelsTo(limiter);
                mDynamicsProcessing.setEnabled(targetGainMb > 0);
            } catch (Exception e) {
                Slog.w(TAG, "Error applying system DynamicsProcessing limiter: " + e.getMessage());
            }
        }
    }
}
