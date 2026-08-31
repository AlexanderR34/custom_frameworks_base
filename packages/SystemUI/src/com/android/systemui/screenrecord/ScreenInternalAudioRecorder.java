/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.screenrecord;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.util.Log;
import android.util.MathUtils;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Recording internal audio
 * Nintendo switch 1 y 2
 */
public class ScreenInternalAudioRecorder {
    private static String TAG = "ScreenAudioRecorder";
    private static final int TIMEOUT = 500;
    private static final float MIC_VOLUME_SCALE = 1.4f;
    private static final float DUCKED_INTERNAL_VOLUME = 0.35f; // Ducked internal audio volume when user speaks
    private static final float NORMAL_INTERNAL_VOLUME = 1.0f;  // Full internal audio volume when user is silent
    private static final double VOICE_RMS_THRESHOLD = 380.0;   // Microphone RMS threshold for voice detection
    private static final long DUCK_HOLD_TIME_MS = 650;         // Hold time to prevent volume pumping between words
    private static final float ATTACK_STEP = 0.0025f;          // Smooth, pop-free fade down (~7ms)
    private static final float RELEASE_STEP = 0.00009f;        // Smooth natural fade up (~160ms)

    private float mCurrentInternalGain = NORMAL_INTERNAL_VOLUME;
    private long mLastVoiceDetectedTime = 0;
    private float mHpPrevSample = 0f;
    private float mHpPrevOutput = 0f;
    private float mLpPrevOutput = 0f;
    private float mAdaptiveNoiseFloor = 100f;
    private AudioRecord mAudioRecord;
    private AudioRecord mAudioRecordMic;
    private Config mConfig = new Config();
    private Thread mThread;
    private MediaProjection mMediaProjection;
    private MediaCodec mCodec;
    private long mPresentationTime;
    private long mTotalBytes;
    private MediaMuxer mMuxer;
    private boolean mMic;
    private boolean mStarted;

    private int mTrackId = -1;

    public ScreenInternalAudioRecorder(String outFile, MediaProjection mp, boolean includeMicInput)
            throws IOException {
        mMic = includeMicInput;
        mMuxer = new MediaMuxer(outFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mMediaProjection = mp;
        Log.d(TAG, "creating audio file " + outFile);
        setupSimple();
    }
    /**
     * Audio recoding configuration
     */
    public static class Config {
        public int channelOutMask = AudioFormat.CHANNEL_OUT_STEREO;
        public int channelInMask = AudioFormat.CHANNEL_IN_STEREO;
        public int encoding = AudioFormat.ENCODING_PCM_16BIT;
        public int sampleRate = 48000;
        public int bitRate = 256000; // 256 kbps High-Fidelity AAC Stereo
        public int bufferSizeBytes = 1 << 17;
        public boolean privileged = true;
        public boolean legacy_app_looback = false;

        @Override
        public String toString() {
            return  "channelMask=" + channelOutMask
                    + "\n   encoding=" + encoding
                    + "\n sampleRate=" + sampleRate
                    + "\n bufferSize=" + bufferSizeBytes
                    + "\n privileged=" + privileged
                    + "\n legacy app looback=" + legacy_app_looback;
        }

    }

    private void setupSimple() throws IOException {
        int size = AudioRecord.getMinBufferSize(
                mConfig.sampleRate, mConfig.channelInMask,
                mConfig.encoding) * 2;

        Log.d(TAG, "audio buffer size: " + size);

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(mConfig.encoding)
                .setSampleRate(mConfig.sampleRate)
                .setChannelMask(mConfig.channelOutMask)
                .build();

        AudioPlaybackCaptureConfiguration playbackConfig =
                new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build();

        mAudioRecord = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(playbackConfig)
                .build();

        if (mMic) {
            mAudioRecordMic = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    mConfig.sampleRate, AudioFormat.CHANNEL_IN_STEREO, mConfig.encoding, size);
        }

        mCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        MediaFormat medFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, mConfig.sampleRate, 2);
        medFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        medFormat.setInteger(MediaFormat.KEY_BIT_RATE, mConfig.bitRate);
        medFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, mConfig.encoding);
        mCodec.configure(medFormat,
                null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mThread = new Thread(() -> {
            short[] bufferInternal = null;
            short[] bufferMic = null;
            byte[] buffer = new byte[size];

            if (mMic) {
                bufferInternal = new short[size / 2];
                bufferMic = new short[size / 2];
            }

            int readBytes = 0;
            int readShortsInternal = 0;
            int offsetShortsInternal = 0;
            int readShortsMic = 0;
            int offsetShortsMic = 0;
            while (mStarted) {
                if (mMic) {
                    readShortsInternal = mAudioRecord.read(bufferInternal, offsetShortsInternal,
                            bufferInternal.length - offsetShortsInternal);
                    readShortsMic = mAudioRecordMic.read(
                            bufferMic, offsetShortsMic, bufferMic.length - offsetShortsMic);

                    // if both error, end the recording
                    if (readShortsInternal < 0 && readShortsMic < 0) {
                        break;
                    }

                    // if one has an errors, fill its buffer with zeros and assume it is mute
                    // with the same size as the other buffer
                    if (readShortsInternal < 0) {
                        readShortsInternal = readShortsMic;
                        offsetShortsInternal = offsetShortsMic;
                        java.util.Arrays.fill(bufferInternal, (short) 0);
                    }

                    if (readShortsMic < 0) {
                        readShortsMic = readShortsInternal;
                        offsetShortsMic = offsetShortsInternal;
                        java.util.Arrays.fill(bufferMic, (short) 0);
                    }

                    // Add offset (previous unmixed values) to the buffer
                    readShortsInternal += offsetShortsInternal;
                    readShortsMic += offsetShortsMic;

                    int minShorts = Math.min(readShortsInternal, readShortsMic);
                    readBytes = minShorts * 2;

                    // Voice Activity Detection with 280Hz - 3400Hz speech bandpass filter
                    long rawSumSquares = 0;
                    long filteredSumSquares = 0;
                    int zeroCrossings = 0;
                    boolean lastSignPositive = false;

                    for (int i = 0; i < minShorts; i++) {
                        short sample = bufferMic[i];
                        rawSumSquares += (long) sample * sample;

                        // High-pass filter (~280 Hz) to reject handling rumble and wind
                        float hp = (sample - mHpPrevSample) + 0.963f * mHpPrevOutput;
                        mHpPrevSample = sample;
                        mHpPrevOutput = hp;

                        // Low-pass filter (~3400 Hz) to reject high frequency friction
                        float lp = mLpPrevOutput + 0.36f * (hp - mLpPrevOutput);
                        mLpPrevOutput = lp;

                        filteredSumSquares += (long) (lp * lp);

                        boolean signPositive = lp > 0;
                        if (i > 0 && signPositive != lastSignPositive) {
                            zeroCrossings++;
                        }
                        lastSignPositive = signPositive;
                    }

                    double rawRms = Math.sqrt((double) rawSumSquares / Math.max(1, minShorts));
                    double filteredRms = Math.sqrt((double) filteredSumSquares / Math.max(1, minShorts));
                    double voiceRatio = (rawRms > 10.0) ? (filteredRms / rawRms) : 0.0;

                    // Dynamically adapt noise floor
                    if (filteredRms < mAdaptiveNoiseFloor * 1.5) {
                        mAdaptiveNoiseFloor = (float) (mAdaptiveNoiseFloor * 0.98 + filteredRms * 0.02);
                    } else {
                        mAdaptiveNoiseFloor = (float) (mAdaptiveNoiseFloor * 0.999 + filteredRms * 0.001);
                    }
                    mAdaptiveNoiseFloor = Math.max(80.0f, Math.min(mAdaptiveNoiseFloor, 800.0f));

                    double dynamicThreshold = Math.max(340.0, mAdaptiveNoiseFloor * 2.8);
                    int zcrPer1024 = (minShorts > 0) ? (zeroCrossings * 1024 / minShorts) : 0;

                    boolean isSpeech = (filteredRms > dynamicThreshold)
                            && (voiceRatio >= 0.40)
                            && (zcrPer1024 >= 8 && zcrPer1024 <= 180);

                    long now = System.currentTimeMillis();
                    float targetInternalGain = NORMAL_INTERNAL_VOLUME;
                    if (isSpeech) {
                        mLastVoiceDetectedTime = now;
                        targetInternalGain = DUCKED_INTERNAL_VOLUME;
                    } else if (now - mLastVoiceDetectedTime < DUCK_HOLD_TIME_MS) {
                        targetInternalGain = DUCKED_INTERNAL_VOLUME; // Hold ducked volume during speech pauses
                    }

                    // Apply dynamic voice ducking and mix the two audio streams
                    applyVoiceDuckingAndMix(bufferInternal, bufferMic, buffer, minShorts, targetInternalGain);

                    // shift unmixed shorts to the beginning of the buffer
                    shiftToStart(bufferInternal, minShorts, offsetShortsInternal);
                    shiftToStart(bufferMic, minShorts, offsetShortsMic);

                    // reset the offset for the next loop
                    offsetShortsInternal = readShortsInternal - minShorts;
                    offsetShortsMic = readShortsMic - minShorts;
                } else {
                    readBytes = mAudioRecord.read(buffer, 0, buffer.length);
                }

                //exit the loop when at end of stream
                if (readBytes < 0) {
                    Log.e(TAG, "read error " + readBytes +
                            ", shorts internal: " + readShortsInternal +
                            ", shorts mic: " + readShortsMic);
                    break;
                }
                encode(buffer, readBytes);
            }
            endStream();
        });
    }

    /**
     * moves all bits from start to end to the beginning of the array
     */
    private void shiftToStart(short[] target, int start, int end) {
        for (int i = 0; i  < end - start; i++) {
            target[i] = target[start + i];
        }
    }

    private void applyVoiceDuckingAndMix(short[] internalBuf, short[] micBuf, byte[] dst, int sizeShorts, float targetInternalGain) {
        for (int i = 0; i < sizeShorts; i++) {
            // Smoothly interpolate internal gain to eliminate pops or clicks
            if (mCurrentInternalGain > targetInternalGain) {
                mCurrentInternalGain = Math.max(targetInternalGain, mCurrentInternalGain - ATTACK_STEP);
            } else if (mCurrentInternalGain < targetInternalGain) {
                mCurrentInternalGain = Math.min(targetInternalGain, mCurrentInternalGain + RELEASE_STEP);
            }

            int duckedInternal = (int) (internalBuf[i] * mCurrentInternalGain);
            int amplifiedMic = (int) (micBuf[i] * MIC_VOLUME_SCALE);

            int sum = (short) MathUtils.constrain(
                    duckedInternal + amplifiedMic, Short.MIN_VALUE, Short.MAX_VALUE);
            int byteIndex = i * 2;
            dst[byteIndex] = (byte) (sum & 0xff);
            dst[byteIndex + 1] = (byte) ((sum >> 8) & 0xff);
        }
    }

    private void encode(byte[] buffer, int readBytes) {
        int offset = 0;
        while (readBytes > 0) {
            int totalBytesRead = 0;
            int bufferIndex = mCodec.dequeueInputBuffer(TIMEOUT);
            if (bufferIndex < 0) {
                writeOutput();
                return;
            }
            ByteBuffer buff = mCodec.getInputBuffer(bufferIndex);
            buff.clear();
            int bufferSize = buff.capacity();
            int bytesToRead = readBytes > bufferSize ? bufferSize : readBytes;
            totalBytesRead += bytesToRead;
            readBytes -= bytesToRead;
            buff.put(buffer, offset, bytesToRead);
            offset += bytesToRead;
            mCodec.queueInputBuffer(bufferIndex, 0, bytesToRead, mPresentationTime, 0);
            mTotalBytes += totalBytesRead;
            // Stereo (2 channels) * 16-bit PCM (2 bytes) = 4 bytes per audio frame
            mPresentationTime = 1000000L * (mTotalBytes / 4) / mConfig.sampleRate;

            writeOutput();
        }
    }

    private void endStream() {
        int bufferIndex = mCodec.dequeueInputBuffer(TIMEOUT);
        mCodec.queueInputBuffer(bufferIndex, 0, 0, mPresentationTime,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        writeOutput();
    }

    private void writeOutput() {
        while (true) {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int bufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT);
            if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mTrackId = mMuxer.addTrack(mCodec.getOutputFormat());
                mMuxer.start();
                continue;
            }
            if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }
            if (mTrackId < 0) return;
            ByteBuffer buff = mCodec.getOutputBuffer(bufferIndex);

            if (!((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    && bufferInfo.size != 0)) {
                mMuxer.writeSampleData(mTrackId, buff, bufferInfo);
            }
            mCodec.releaseOutputBuffer(bufferIndex, false);
        }
    }

    /**
    * start recording
     * @throws IllegalStateException if recording fails to initialize
    */
    public synchronized void start() throws IllegalStateException {
        if (mStarted) {
            if (mThread == null) {
                throw new IllegalStateException("Recording stopped and can't restart (single use)");
            }
            throw new IllegalStateException("Recording already started");
        }
        mStarted = true;
        mAudioRecord.startRecording();
        if (mMic) mAudioRecordMic.startRecording();
        Log.d(TAG, "channel count " + mAudioRecord.getChannelCount());
        mCodec.start();
        if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new IllegalStateException("Audio recording failed to start");
        }
        mThread.start();
    }

    /**
     * end recording
     */
    public synchronized void end() {
        mStarted = false;
        mAudioRecord.stop();
        if (mMic) {
            mAudioRecordMic.stop();
        }
        mAudioRecord.release();
        if (mMic) {
            mAudioRecordMic.release();
        }
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mCodec.stop();
        mCodec.release();
        mMuxer.stop();
        mMuxer.release();
        mThread = null;
    }
}
