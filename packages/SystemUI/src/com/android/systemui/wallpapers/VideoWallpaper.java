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

package com.android.systemui.wallpapers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileInputStream;

/**
 * Service that plays high-definition video wallpapers seamlessly with hardware decoding,
 * Center-Crop proportional scaling, infinite looping, and 0% battery usage on sleep/off-screen.
 */
public class VideoWallpaper extends WallpaperService {
    private static final String TAG = "VideoWallpaper";
    public static final String ACTION_VIDEO_CHANGED = "com.android.systemui.wallpapers.VIDEO_WALLPAPER_CHANGED";
    public static final String VIDEO_FILE_NAME = "wallpaper_video.mp4";

    @Override
    public Engine onCreateEngine() {
        return new VideoEngine();
    }

    class VideoEngine extends WallpaperService.Engine {
        private MediaPlayer mMediaPlayer;
        private SurfaceHolder mHolder;
        private boolean mIsVisible = false;
        private boolean mIsPrepared = false;
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        private final BroadcastReceiver mVideoChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_VIDEO_CHANGED.equals(intent.getAction())) {
                    reloadVideo();
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setShowForAllUsers(true);
            mHolder = surfaceHolder;
            IntentFilter filter = new IntentFilter(ACTION_VIDEO_CHANGED);
            registerReceiver(mVideoChangeReceiver, filter, Context.RECEIVER_EXPORTED);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            mHolder = holder;
            startPlayer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            mHolder = holder;
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            releasePlayer();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mIsVisible = visible;
            if (visible) {
                if (mMediaPlayer != null && mIsPrepared) {
                    try {
                        mMediaPlayer.start();
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting video playback", e);
                    }
                } else {
                    startPlayer();
                }
            } else {
                if (mMediaPlayer != null && mIsPrepared) {
                    try {
                        if (mMediaPlayer.isPlaying()) {
                            mMediaPlayer.pause();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        private void startPlayer() {
            if (mHolder == null || mHolder.getSurface() == null || !mHolder.getSurface().isValid()) {
                return;
            }
            releasePlayer();

            File videoFile = new File(getFilesDir(), VIDEO_FILE_NAME);
            if (!videoFile.exists() || videoFile.length() == 0) {
                Log.w(TAG, "No video wallpaper file found at " + videoFile.getAbsolutePath());
                return;
            }

            try {
                mMediaPlayer = new MediaPlayer();
                mMediaPlayer.setSurface(mHolder.getSurface());
                FileInputStream fis = new FileInputStream(videoFile);
                mMediaPlayer.setDataSource(fis.getFD());
                fis.close();

                mMediaPlayer.setLooping(true);
                mMediaPlayer.setVolume(0.0f, 0.0f);
                mMediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);

                mMediaPlayer.setOnPreparedListener(mp -> {
                    mIsPrepared = true;
                    if (mIsVisible) {
                        mp.start();
                    }
                });

                mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                    releasePlayer();
                    return true;
                });

                mMediaPlayer.prepareAsync();
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize MediaPlayer for video wallpaper", e);
                releasePlayer();
            }
        }

        private void reloadVideo() {
            mHandler.post(this::startPlayer);
        }

        private void releasePlayer() {
            mIsPrepared = false;
            if (mMediaPlayer != null) {
                try {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                    }
                    mMediaPlayer.reset();
                    mMediaPlayer.release();
                } catch (Exception ignored) {}
                mMediaPlayer = null;
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            try {
                unregisterReceiver(mVideoChangeReceiver);
            } catch (Exception ignored) {}
            releasePlayer();
        }
    }
}
