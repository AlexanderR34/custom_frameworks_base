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
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.LoudnessEnhancer;
import android.provider.Settings;
import android.util.Slog;

/**
 * System server helper in AudioService for applying system-wide volume boost
 * up to 200% using Dynamic Processing APIs without distortion.
 */
public class VolumeBoostHelper {
    private static final String TAG = "VolumeBoostHelper";
    private final Context mContext;

    private LoudnessEnhancer mLoudnessEnhancer;
    private DynamicsProcessing mDynamicsProcessing;

    public VolumeBoostHelper(Context context) {
        mContext = context;
        initAudioFx();
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
            Slog.w(TAG, "DynamicsProcessing native effect not directly available, using LoudnessEnhancer: " + e.getMessage());
        }

        updateVolumeBoost();
    }

    public void updateVolumeBoost() {
        int level = Settings.System.getInt(mContext.getContentResolver(), Settings.System.VOLUME_BOOST_LEVEL, 0);
        int clampedLevel = Math.max(0, Math.min(100, level));

        // Map 0-100% boost level to 0-6000 mB (+6 dB boost = 200% volume total output)
        int gainmB = Math.round((clampedLevel / 100.0f) * 6000.0f);

        if (mLoudnessEnhancer != null) {
            try {
                mLoudnessEnhancer.setTargetGain(gainmB);
                mLoudnessEnhancer.setEnabled(gainmB > 0);
            } catch (Exception e) {
                Slog.e(TAG, "Error applying system LoudnessEnhancer target gain: " + e.getMessage());
            }
        }

        if (mDynamicsProcessing != null) {
            try {
                float postGainDb = (clampedLevel / 100.0f) * 6.0f; // up to +6 dB
                DynamicsProcessing.Limiter limiter = new DynamicsProcessing.Limiter(
                        true /* inUse */,
                        true /* enabled */,
                        0 /* linkGroup */,
                        1.0f /* attackTime ms */,
                        100.0f /* releaseTime ms */,
                        10.0f /* ratio */,
                        -0.1f /* threshold dB */,
                        postGainDb /* postGain dB */);

                mDynamicsProcessing.setLimiterAllChannelsTo(limiter);
                mDynamicsProcessing.setEnabled(clampedLevel > 0);
            } catch (Exception e) {
                Slog.w(TAG, "Error applying system DynamicsProcessing limiter: " + e.getMessage());
            }
        }
    }
}
