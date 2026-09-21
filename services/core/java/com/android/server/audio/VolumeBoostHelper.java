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
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

/**
 * VolumeBoostHelper:
 * Utiliza LoudnessEnhancer en Session 0 para la amplificacion de volumen limpia (+0.0 a +10.0 dB / 1000 mB).
 * Mantiene el efecto persistentemente habilitado en AudioFlinger (gain = 0 cuando el boost esta en 0%)
 * para garantizar cambios instantaneos en caliente sin reinicios de audioserver ni perdida de sesion.
 * Evita DynamicsProcessing para no bloquear el controlador de volumen AIDL HAL en estado IDLE.
 */
public class VolumeBoostHelper {
    private static final String TAG = "VolumeBoostHelper";
    public static final String SETTING_CALL_GAIN_KEY = "volume_boost_call_gain";

    private static final int MAX_BOOST_GAIN_MB = 1000; // +10.0 dB (1000 mB)
    private static final int CALL_GAIN_MB = 800;        // +8.0 dB (800 mB)

    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateVolumeBoost();
        }
    };

    private LoudnessEnhancer mLoudnessEnhancer;
    private int mCurrentAudioMode = AudioSystem.MODE_NORMAL;
    private int mLastAppliedGainMb = -1;

    public VolumeBoostHelper(Context context) {
        mContext = context;
        initAudioFx();
        registerObservers();
    }

    public synchronized void onAudioServerDied() {
        Slog.i(TAG, "onAudioServerDied: reinicializando efectos de VolumeBoostHelper");
        initAudioFx();
    }

    private synchronized void initAudioFx() {
        releaseAudioFx();

        try {
            mLoudnessEnhancer = new LoudnessEnhancer(0 /* session 0 */);
            mLoudnessEnhancer.setTargetGain(0);
            mLoudnessEnhancer.setEnabled(true);
        } catch (Exception e) {
            Slog.e(TAG, "Fallo al inicializar LoudnessEnhancer en VolumeBoostHelper: " + e.getMessage(), e);
            mLoudnessEnhancer = null;
        }

        mLastAppliedGainMb = -1;
        applyGain(getCurrentTargetGainMb());
    }

    private void registerObservers() {
        try {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.VOLUME_BOOST_LEVEL),
                    false, mContentObserver, UserHandle.USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(SETTING_CALL_GAIN_KEY),
                    false, mContentObserver, UserHandle.USER_ALL);
        } catch (Exception e) {
            Slog.e(TAG, "Fallo al registrar observers en VolumeBoostHelper: " + e.getMessage(), e);
        }
    }

    public synchronized void onModeChanged(int newMode) {
        if (mCurrentAudioMode != newMode) {
            mCurrentAudioMode = newMode;
            updateVolumeBoost();
        }
    }

    private int getCurrentTargetGainMb() {
        int level = Settings.System.getInt(mContext.getContentResolver(), Settings.System.VOLUME_BOOST_LEVEL, 0);
        int clampedLevel = Math.max(0, Math.min(100, level));

        boolean isCallGainEnabled = Settings.System.getInt(mContext.getContentResolver(), SETTING_CALL_GAIN_KEY, 1) == 1;
        boolean isInCall = (mCurrentAudioMode == AudioSystem.MODE_IN_CALL ||
                            mCurrentAudioMode == AudioSystem.MODE_IN_COMMUNICATION ||
                            mCurrentAudioMode == AudioSystem.MODE_RINGTONE);

        int targetGainMb = 0;

        if (isInCall && isCallGainEnabled) {
            int mediaGainMb = Math.round((clampedLevel / 100.0f) * MAX_BOOST_GAIN_MB);
            targetGainMb = Math.max(CALL_GAIN_MB, mediaGainMb);
        } else if (clampedLevel > 0) {
            targetGainMb = Math.round((clampedLevel / 100.0f) * MAX_BOOST_GAIN_MB);
        }

        return targetGainMb;
    }

    public synchronized void updateVolumeBoost() {
        int targetGainMb = getCurrentTargetGainMb();
        if (mLastAppliedGainMb == targetGainMb) {
            return;
        }
        applyGain(targetGainMb);
    }

    private void applyGain(int targetGainMb) {
        mLastAppliedGainMb = targetGainMb;

        if (mLoudnessEnhancer == null) {
            initAudioFx();
            return;
        }

        try {
            mLoudnessEnhancer.setTargetGain(targetGainMb);
        } catch (Exception e) {
            Slog.e(TAG, "Error aplicando setTargetGain en LoudnessEnhancer: " + e.getMessage() + ", reinicializando...", e);
            mLastAppliedGainMb = -1;
            initAudioFx();
        }
    }

    private synchronized void releaseAudioFx() {
        if (mLoudnessEnhancer != null) {
            try {
                mLoudnessEnhancer.release();
            } catch (Exception ignored) {}
            mLoudnessEnhancer = null;
        }
    }

    public synchronized void release() {
        releaseAudioFx();
    }
}
