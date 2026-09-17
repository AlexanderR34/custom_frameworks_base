/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.app.WallpaperManager.SetWallpaperFlags;

import android.annotation.Nullable;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import java.nio.ByteBuffer;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Trace;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.NonNull;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.dagger.qualifiers.LongRunning;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.concurrency.DelayableExecutor;
import com.android.systemui.utils.windowmanager.WindowManagerProvider;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.MotionEvent;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Default built-in wallpaper that simply shows a static image.
 */
@SuppressWarnings({"UnusedDeclaration"})
public class ImageWallpaper extends WallpaperService {

    private static final String TAG = ImageWallpaper.class.getSimpleName();
    private static final boolean DEBUG = false;
    public static final String KEY_JELLY_WALLPAPER = "jelly_wallpaper_enabled";
    public static final String KEY_JELLY_MODE = "jelly_wallpaper_mode";
    public static final String KEY_JELLY_PRESET = "jelly_wallpaper_preset";
    public static final String KEY_JELLY_STIFFNESS = "jelly_wallpaper_stiffness";
    public static final String KEY_JELLY_DAMPING = "jelly_wallpaper_damping";
    public static final String KEY_JELLY_RADIUS = "jelly_wallpaper_radius";
    public static final String KEY_JELLY_ELASTICITY = "jelly_wallpaper_elasticity";
    public static final String KEY_JELLY_RIPPLE = "jelly_wallpaper_ripple";
    public static final String KEY_JELLY_RIPPLE_STRENGTH = "jelly_wallpaper_ripple_strength";
    public static final String KEY_JELLY_GYRO = "jelly_wallpaper_gyro_parallax";
    public static final String KEY_JELLY_GYRO_MODE = "jelly_wallpaper_gyro_mode";
    public static final String KEY_JELLY_GYRO_INERTIA = "jelly_wallpaper_gyro_inertia";
    public static final String KEY_JELLY_DEPTH_EFFECT = "jelly_wallpaper_depth_effect";
    public static final String KEY_JELLY_DEPTH_SEPARATION = "jelly_wallpaper_depth_separation";
    public static final String KEY_JELLY_CHARGING_WAVE = "jelly_wallpaper_charging_wave";
    public static final String KEY_JELLY_CHARGING_WAVE_STRENGTH = "jelly_wallpaper_charging_wave_strength";
    public static final String KEY_JELLY_AO = "jelly_wallpaper_ambient_occlusion";
    public static final String KEY_JELLY_AO_INTENSITY = "jelly_wallpaper_ambient_occlusion_intensity";
    public static final String KEY_JELLY_SNAP_BACK = "jelly_wallpaper_snap_back";
    public static final String KEY_JELLY_MUSIC_REACTIVE = "jelly_wallpaper_music_reactive";
    public static final String KEY_JELLY_MUSIC_STRENGTH = "jelly_wallpaper_music_reactive_strength";
    public static final String KEY_JELLY_MUSIC_PATTERN = "jelly_wallpaper_music_reactive_pattern";
    public static final String KEY_JELLY_HAPTICS = "jelly_wallpaper_haptics";
    public static final String KEY_JELLY_HAPTICS_INTENSITY = "jelly_wallpaper_haptics_intensity";
    public static final String KEY_JELLY_TAP_SHOCKWAVE = "jelly_wallpaper_tap_shockwave";
    public static final String KEY_JELLY_MULTITOUCH = "jelly_wallpaper_multitouch";
    public static final String KEY_JELLY_GYRO_INERTIA_STRENGTH = "jelly_wallpaper_gyro_inertia_strength";
    public static final String KEY_JELLY_LIGHT_ENABLED = "jelly_wallpaper_light_source_enabled";
    public static final String KEY_JELLY_LIGHT_MODE = "jelly_wallpaper_light_mode";
    public static final String KEY_JELLY_LIGHT_INTENSITY = "jelly_wallpaper_light_intensity";
    public static final String KEY_JELLY_LIGHT_ANGLE = "jelly_wallpaper_light_angle";
    public static final String KEY_JELLY_LIGHT_BEAM_SPREAD = "jelly_wallpaper_light_beam_spread";
    public static final String KEY_JELLY_LIGHT_HARDNESS = "jelly_wallpaper_light_hardness";
    public static final String KEY_JELLY_LIGHT_SHIMMER_SPEED = "jelly_wallpaper_light_shimmer_speed";
    public static final String KEY_JELLY_LIGHT_SPECULAR_COLUMN = "jelly_wallpaper_light_specular_column";
    public static final String KEY_JELLY_LIGHT_SOLAR_TRACKING = "jelly_wallpaper_light_solar_tracking";
    public static final String KEY_JELLY_LIGHT_GYRO_PARALLAX = "jelly_wallpaper_light_gyro_parallax";
    public static final String KEY_JELLY_SNAPBACK_RECOIL = "jelly_wallpaper_snapback_recoil";
    public static final String KEY_JELLY_INTERNAL_TENSION = "jelly_wallpaper_internal_tension";
    public static final String KEY_JELLY_MAX_STRETCH = "jelly_wallpaper_max_stretch";
    public static final String KEY_JELLY_SURFACE_MASS = "jelly_wallpaper_surface_mass";
    public static final String KEY_JELLY_WAVE_SPEED = "jelly_wallpaper_wave_speed";
    public static final String KEY_JELLY_LEGACY = "jelly_wallpaper_lockscreen_enabled";

    // keep track of the number of pages of the launcher for local color extraction purposes
    private volatile int mPages = 1;
    private boolean mPagesComputed = false;

    private final UserTracker mUserTracker;
    private final WindowManagerProvider mWindowManagerProvider;

    // used to handle WallpaperService messages (e.g. DO_ATTACH, MSG_UPDATE_SURFACE)
    // and to receive WallpaperService callbacks (e.g. onCreateEngine, onSurfaceRedrawNeeded)
    private HandlerThread mWorker;

    // used for most tasks (call canvas.drawBitmap, load/unload the bitmap)
    @LongRunning
    private final DelayableExecutor mLongExecutor;

    // wait at least this duration before unloading the bitmap
    private static final int DELAY_UNLOAD_BITMAP = 2000;

    @Inject
    public ImageWallpaper(@LongRunning DelayableExecutor longExecutor, UserTracker userTracker,
            WindowManagerProvider windowManagerProvider) {
        super();
        mLongExecutor = longExecutor;
        mUserTracker = userTracker;
        mWindowManagerProvider = windowManagerProvider;
    }

    @Override
    public Looper onProvideEngineLooper() {
        // Receive messages on mWorker thread instead of SystemUI's main handler.
        // All other wallpapers have their own process, and they can receive messages on their own
        // main handler without any delay. But since ImageWallpaper lives in SystemUI, performance
        // of the image wallpaper could be negatively affected when SystemUI's main handler is busy.
        return mWorker != null ? mWorker.getLooper() : super.onProvideEngineLooper();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWorker = new HandlerThread(TAG);
        mWorker.start();
    }

    @Override
    public Engine onCreateEngine() {
        return new CanvasEngine();
    }

    class CanvasEngine extends WallpaperService.Engine implements DisplayListener, Choreographer.FrameCallback {
        private WallpaperManager mWallpaperManager;
        private final ImageWallpaperColorExtractor mColorExtractor;
        private SurfaceHolder mSurfaceHolder;
        private boolean mDrawn = false;
        @VisibleForTesting
        static final int MIN_SURFACE_WIDTH = 128;
        @VisibleForTesting
        static final int MIN_SURFACE_HEIGHT = 128;
        static final int DEFAULT_PANEL_WIDTH = 1220;
        static final int DEFAULT_PANEL_HEIGHT = 2712;
        private Bitmap mBitmap;
        private boolean mWideColorGamut = false;

        // Jelly Wallpaper Elastic Mesh Simulation
        private final JellyMesh mJellyMesh = new JellyMesh();
        private boolean mJellyEnabled = true;
        private boolean mIsLoopRunning = false;
        private long mLastFrameTimeNanos = 0;

        // 3D Gyroscope Parallax & Shake
        private SensorManager mSensorManager;
        private Sensor mRotationSensor;
        private Sensor mLinearAccelSensor;
        private Sensor mAccelSensor;
        private boolean mGyroEnabled = false;
        private boolean mGyroInertiaEnabled = true;
        private int mGyroMode = 2;
        private float mSmoothRoll = 0f;
        private float mSmoothPitch = 0f;
        private float mLastAppliedRoll = 0f;
        private float mLastAppliedPitch = 0f;
        private long mLastShakeTime = 0;

        // Nuevas funciones de física y audio
        private boolean mIsVisible = false;
        private AudioManager mAudioManager;
        private boolean mAmbientOcclusionEnabled = true;
        private int mAmbientOcclusionIntensity = 75;
        private int mJellyElasticity = 70;
        private int mJellyRippleStrength = 80;
        private boolean mLightSourceEnabled = false;
        private int mLightMode = 2;
        private float mLightAngle = 45.0f;
        private int mLightIntensity = 75;
        private int mLightBeamSpread = 50;
        private int mLightHardness = 50;
        private int mLightShimmerSpeed = 50;
        private boolean mLightSpecularColumn = true;
        private boolean mLightSolarTracking = false;
        private boolean mLightGyroParallax = true;
        private float mLightDynamicAngle = 45.0f;
        private float mLightShimmerPhase = 0.0f;
        private boolean mIsTouching = false;
        private float mTouchX = 540f;
        private float mTouchY = 1200f;
        private int mGyroInertiaStrength = 65;
        private boolean mDepthEffectEnabled = false;
        private int mDepthSeparation = 60;
        private boolean mChargingWaveEnabled = true;
        private int mChargingWaveStrength = 80;
        private BroadcastReceiver mPowerReceiver = null;
        private Bitmap mForegroundBitmap = null;
        private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private boolean mIsSegmenting = false;
        private boolean mSnapBackEnabled = true;
        private boolean mMusicReactiveEnabled = false;
        private int mMusicStrength = 70;
        private int mMusicPattern = 0;
        private boolean mHapticsEnabled = true;
        private int mHapticsIntensity = 80;
        private boolean mTapShockwaveEnabled = true;
        private boolean mMultiTouchEnabled = true;
        private int mSnapBackRecoil = 75;
        private int mInternalTension = 65;
        private int mMaxStretch = 70;
        private int mSurfaceMass = 50;
        private int mWaveSpeed = 70;
        private Visualizer mVisualizer;
        private Vibrator mVibrator;

        private float mLinearGravityX = 0f;
        private float mLinearGravityY = 0f;
        private float mLinearGravityZ = 0f;

        private final SensorEventListener mSensorListener = new SensorEventListener() {
            private final float[] mRotationMatrix = new float[9];
            private final float[] mOrientationAngles = new float[3];

            @Override
            public void onSensorChanged(SensorEvent event) {
                if ((!mGyroEnabled && !mGyroInertiaEnabled && !(mLightSourceEnabled && mLightGyroParallax)) || !mIsVisible || !isCurrentTargetActive()) return;
                int type = event.sensor.getType();

                if (type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);
                    // Proyección directa del vector de gravedad sobre los ejes del dispositivo (sin Gimbal Lock ni singularidad Euler a 90°)
                    // mRotationMatrix[6] = Componente X (Inclinación lateral Izquierda / Derecha pura)
                    // mRotationMatrix[7] = Componente Y (Inclinación vertical Arriba / Abajo pura)
                    float targetRoll = Math.max(-1.0f, Math.min(1.0f, mRotationMatrix[6]));
                    float targetPitch = Math.max(-1.0f, Math.min(1.0f, -(mRotationMatrix[7] - 0.70f) * 1.6f));

                    // Filtro exponencial de alta respuesta para tracking en tiempo real
                    float alpha = 0.35f;
                    mSmoothRoll += (targetRoll - mSmoothRoll) * alpha;
                    mSmoothPitch += (targetPitch - mSmoothPitch) * alpha;

                    // Zona muerta (Deadzone) mínima
                    float finalRoll = (Math.abs(mSmoothRoll) < 0.005f) ? 0f : mSmoothRoll;
                    float finalPitch = (Math.abs(mSmoothPitch) < 0.005f) ? 0f : mSmoothPitch;

                    if (mGyroEnabled) {
                        float dR = Math.abs(finalRoll - mLastAppliedRoll);
                        float dP = Math.abs(finalPitch - mLastAppliedPitch);
                        if (dR > 0.0005f || dP > 0.0005f) {
                            mLastAppliedRoll = finalRoll;
                            mLastAppliedPitch = finalPitch;
                            mJellyMesh.setGyroTilt(finalRoll, finalPitch);
                            startAnimationLoopIfNeeded();
                        }
                    } else {
                        mJellyMesh.resetGyro();
                    }

                    if (mLightSourceEnabled && (mLightMode == 2 || mLightGyroParallax)) {
                        float dynAngle = (float) Math.toDegrees(Math.atan2(finalPitch, -finalRoll));
                        mLightDynamicAngle = dynAngle;
                        mJellyMesh.setLightSource(true, dynAngle, mLightIntensity / 100.0f);
                        startAnimationLoopIfNeeded();
                    }
                } else if (type == Sensor.TYPE_GRAVITY) {
                    float gx = event.values[0] / 9.81f;
                    float gy = event.values[1] / 9.81f;

                    float targetRoll = Math.max(-1.0f, Math.min(1.0f, gx));
                    float targetPitch = Math.max(-1.0f, Math.min(1.0f, -(gy - 0.70f) * 1.6f));

                    float alpha = 0.35f;
                    mSmoothRoll += (targetRoll - mSmoothRoll) * alpha;
                    mSmoothPitch += (targetPitch - mSmoothPitch) * alpha;

                    float finalRoll = (Math.abs(mSmoothRoll) < 0.005f) ? 0f : mSmoothRoll;
                    float finalPitch = (Math.abs(mSmoothPitch) < 0.005f) ? 0f : mSmoothPitch;

                    if (mGyroEnabled) {
                        float dR = Math.abs(finalRoll - mLastAppliedRoll);
                        float dP = Math.abs(finalPitch - mLastAppliedPitch);
                        if (dR > 0.0005f || dP > 0.0005f) {
                            mLastAppliedRoll = finalRoll;
                            mLastAppliedPitch = finalPitch;
                            mJellyMesh.setGyroTilt(finalRoll, finalPitch);
                            startAnimationLoopIfNeeded();
                        }
                    } else {
                        mJellyMesh.resetGyro();
                    }

                    if (mLightSourceEnabled && (mLightMode == 2 || mLightGyroParallax)) {
                        float dynAngle = (float) Math.toDegrees(Math.atan2(finalPitch, -finalRoll));
                        mLightDynamicAngle = dynAngle;
                        mJellyMesh.setLightSource(true, dynAngle, mLightIntensity / 100.0f);
                        startAnimationLoopIfNeeded();
                    }
                } else if (type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    float lx = event.values[0];
                    float ly = event.values[1];
                    float shakeIntensity = (float) Math.hypot(lx, ly);

                    float strengthRatio = Math.max(0.0f, Math.min(1.0f, mGyroInertiaStrength / 100.0f));
                    long now = SystemClock.uptimeMillis();
                    if (mGyroInertiaEnabled && strengthRatio > 0.01f && shakeIntensity > 1.2f && (now - mLastShakeTime > 80)) {
                        mLastShakeTime = now;
                        float normShakeX = Math.max(-2.5f, Math.min(2.5f, lx / 2.8f));
                        float normShakeY = Math.max(-2.5f, Math.min(2.5f, ly / 2.8f));
                        mJellyMesh.applyShakeImpulse(-normShakeX * strengthRatio, normShakeY * strengthRatio);
                        startAnimationLoopIfNeeded();
                    }
                } else if (type == Sensor.TYPE_ACCELEROMETER) {
                    float ax = event.values[0];
                    float ay = event.values[1];
                    float az = event.values[2];

                    mLinearGravityX = 0.85f * mLinearGravityX + 0.15f * ax;
                    mLinearGravityY = 0.85f * mLinearGravityY + 0.15f * ay;
                    mLinearGravityZ = 0.85f * mLinearGravityZ + 0.15f * az;

                    float linX = ax - mLinearGravityX;
                    float linY = ay - mLinearGravityY;
                    float shakeIntensity = (float) Math.hypot(linX, linY);

                    float strengthRatio = Math.max(0.0f, Math.min(1.0f, mGyroInertiaStrength / 100.0f));
                    long now = SystemClock.uptimeMillis();
                    if (mGyroInertiaEnabled && strengthRatio > 0.01f && shakeIntensity > 1.2f && (now - mLastShakeTime > 80)) {
                        mLastShakeTime = now;
                        float normShakeX = Math.max(-2.5f, Math.min(2.5f, linX / 2.8f));
                        float normShakeY = Math.max(-2.5f, Math.min(2.5f, linY / 2.8f));
                        mJellyMesh.applyShakeImpulse(-normShakeX * strengthRatio, normShakeY * strengthRatio);
                        startAnimationLoopIfNeeded();
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        private final ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateJellySetting();
            }
        };

        /*
         * Counter to unload the bitmap as soon as possible.
         * Before any bitmap operation, this is incremented.
         * After an operation completion, this is decremented (synchronously),
         * and if the count is 0, unload the bitmap
         */
        private int mBitmapUsages = 0;

        /**
         * Main lock for long operations (loading the bitmap or processing colors).
         */
        private final Object mLock = new Object();

        /**
         * Lock for SurfaceHolder operations. Should only be acquired after the main lock.
         */
        private final Object mSurfaceLock = new Object();

        CanvasEngine() {
            super();
            setFixedSizeAllowed(true);
            setShowForAllUsers(true);
            mColorExtractor = new ImageWallpaperColorExtractor(
                    mLongExecutor,
                    mLock,
                    new ImageWallpaperColorExtractor.ImageWallpaperColorExtractorCallback() {

                        @Override
                        public void onColorsProcessed() {
                            CanvasEngine.this.notifyColorsChanged();
                        }

                        @Override
                        public void onColorsProcessed(List<RectF> regions,
                                List<WallpaperColors> colors) {
                            CanvasEngine.this.onColorsProcessed(regions, colors);
                        }

                        @Override
                        public void onMiniBitmapUpdated() {
                            CanvasEngine.this.onMiniBitmapUpdated();
                        }

                        @Override
                        public void onActivated() {
                            setOffsetNotificationsEnabled(true);
                        }

                        @Override
                        public void onDeactivated() {
                            setOffsetNotificationsEnabled(false);
                        }
                    });

            // if the number of pages is already computed, transmit it to the color extractor
            if (mPagesComputed) {
                mColorExtractor.onPageChanged(mPages);
            }
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            Trace.beginSection("ImageWallpaper.CanvasEngine#onCreate");
            if (DEBUG) {
                Log.d(TAG, "onCreate");
            }
            setTouchEventsEnabled(true);
            mWallpaperManager = getDisplayContext().getSystemService(WallpaperManager.class);
            mSurfaceHolder = surfaceHolder;
            Rect displayBounds = null;
            try {
                if (mWindowManagerProvider != null) {
                    displayBounds = mWindowManagerProvider.getWindowManager(getDisplayContext())
                            .getCurrentWindowMetrics()
                            .getBounds();
                }
            } catch (Exception ignored) {}
            int width = (displayBounds != null && displayBounds.width() > 0) ? displayBounds.width() : DEFAULT_PANEL_WIDTH;
            int height = (displayBounds != null && displayBounds.height() > 0) ? displayBounds.height() : DEFAULT_PANEL_HEIGHT;
            mSurfaceHolder.setFixedSize(width, height);
            mJellyMesh.setSize(width, height);

            Context context = getDisplayContext();
            if (context != null) {
                mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            }

            mJellyMesh.setSnapBackListener((x, y) -> {
                if (mSnapBackEnabled && mHapticsEnabled && mHapticsIntensity > 0 && mVibrator != null) {
                    try {
                        int amp = Math.max(1, Math.min(255, (int) (255 * (mHapticsIntensity / 100.0f))));
                        int dur = Math.max(10, (int) (30 * (mHapticsIntensity / 100.0f)));
                        mVibrator.vibrate(VibrationEffect.createOneShot(dur, amp));
                    } catch (Exception e) {
                        try {
                            mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                        } catch (Exception ignored) {}
                    }
                }
            });

            mJellyMesh.setHapticFeedbackListener(new JellyMesh.HapticFeedbackListener() {
                @Override
                public void onStretchTick() {
                    if (mHapticsEnabled && mHapticsIntensity > 0 && mVibrator != null) {
                        try {
                            int amp = Math.max(1, Math.min(255, (int) (120 * (mHapticsIntensity / 100.0f))));
                            int dur = Math.max(4, (int) (8 * (mHapticsIntensity / 100.0f)));
                            mVibrator.vibrate(VibrationEffect.createOneShot(dur, amp));
                        } catch (Exception e) {
                            try {
                                mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                            } catch (Exception ignored) {}
                        }
                    }
                }

                @Override
                public void onRelease(float displacement) {
                    if (mHapticsEnabled && mHapticsIntensity > 0 && mVibrator != null) {
                        try {
                            int amp = Math.max(1, Math.min(255, (int) (190 * (mHapticsIntensity / 100.0f))));
                            int dur = Math.max(8, (int) (16 * (mHapticsIntensity / 100.0f)));
                            mVibrator.vibrate(VibrationEffect.createOneShot(dur, amp));
                        } catch (Exception e) {
                            try {
                                mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
                            } catch (Exception ignored) {}
                        }
                    }
                }
            });

            // Register content observers for live physics updates
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_WALLPAPER),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_MODE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_PRESET),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_STIFFNESS),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_DAMPING),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_RADIUS),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_ELASTICITY),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_RIPPLE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_RIPPLE_STRENGTH),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_GYRO),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_GYRO_MODE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_GYRO_INERTIA),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_AO),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_AO_INTENSITY),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_SNAP_BACK),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_MUSIC_REACTIVE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_HAPTICS),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_HAPTICS_INTENSITY),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_TAP_SHOCKWAVE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_MULTITOUCH),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_ENABLED),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_MODE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_GYRO_INERTIA_STRENGTH),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_INTENSITY),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_ANGLE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_BEAM_SPREAD),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_HARDNESS),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_SHIMMER_SPEED),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_SPECULAR_COLUMN),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_SOLAR_TRACKING),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_GYRO_PARALLAX),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_MUSIC_STRENGTH),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_MUSIC_PATTERN),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_DEPTH_EFFECT),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_DEPTH_SEPARATION),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_CHARGING_WAVE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_CHARGING_WAVE_STRENGTH),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_SNAPBACK_RECOIL),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_INTERNAL_TENSION),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_MAX_STRETCH),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_SURFACE_MASS),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_WAVE_SPEED),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LEGACY),
                    false,
                    mSettingsObserver
            );

            IntentFilter powerFilter = new IntentFilter();
            powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            powerFilter.addAction(android.os.BatteryManager.ACTION_CHARGING);
            mPowerReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mJellyEnabled && mChargingWaveEnabled) {
                        synchronized (mSurfaceLock) {
                            mJellyMesh.triggerChargingWave(mChargingWaveStrength / 100.0f);
                        }
                        startAnimationLoopIfNeeded();
                    }
                }
            };
            try {
                ImageWallpaper.this.registerReceiver(mPowerReceiver, powerFilter, Context.RECEIVER_EXPORTED);
            } catch (Exception e) {
                try {
                    getDisplayContext().registerReceiver(mPowerReceiver, powerFilter, Context.RECEIVER_EXPORTED);
                } catch (Exception ignored) {}
            }
            updateJellySetting();

            getDisplayContext().getSystemService(DisplayManager.class)
                    .registerDisplayListener(this, null);
            getDisplaySizeAndUpdateColorExtractor();
            Trace.endSection();
        }

        private int mJellyMode = 2; // 0: Lockscreen, 1: Homescreen, 2: Both
        private int mJellyPreset = 0; // 0: Gelatina, 1: Goma, 2: Agua, 3: Personalizado
        private int mJellyStiffness = 45;
        private int mJellyDamping = 85;
        private int mJellyRadius = 38;
        private boolean mJellyRippleEnabled = true;

        private boolean isKeyguardShowing() {
            try {
                Context ctx = getDisplayContext();
                if (ctx != null) {
                    KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null) {
                        return km.isKeyguardLocked();
                    }
                }
            } catch (Exception ignored) {}
            return false;
        }

        private boolean isCurrentTargetActive() {
            boolean anyFeatureEnabled = mJellyEnabled || mLightSourceEnabled || mGyroEnabled;
            if (!anyFeatureEnabled) return false;

            int mode = mJellyEnabled ? mJellyMode : mLightMode;
            if (mode == 2) return true; // 2: Both

            int flags = getWallpaperFlags();
            boolean isLockOnlyEngine = (flags == WallpaperManager.FLAG_LOCK);
            boolean isSystemOnlyEngine = (flags == WallpaperManager.FLAG_SYSTEM);
            boolean isLocked = isKeyguardShowing();

            if (mode == 0) {
                // 0: Solo pantalla de bloqueo
                if (isSystemOnlyEngine) return false;
                if (isLockOnlyEngine) return true;
                return isLocked;
            } else if (mode == 1) {
                // 1: Solo pantalla de inicio
                if (isLockOnlyEngine) return false;
                if (isSystemOnlyEngine) return !isLocked;
                return !isLocked;
            }
            return true;
        }

        private void applyPhysicsSettings() {
            float stiffness = (float) mJellyStiffness;
            float damping = mJellyDamping / 20.0f;
            float radius = mJellyRadius / 100.0f;

            mJellyMesh.setPhysicsParams(stiffness, damping, radius);
            mJellyMesh.setExtendedPhysicsParams(mSnapBackRecoil, mInternalTension, mMaxStretch, mSurfaceMass, mWaveSpeed);
            mJellyMesh.setDragElasticity(mJellyElasticity / 100.0f);
            mJellyMesh.setAmbientOcclusion(mAmbientOcclusionEnabled, mAmbientOcclusionIntensity / 100.0f);
            mJellyMesh.setSnapBackEnabled(mSnapBackEnabled);
            mJellyMesh.setTapShockwaveEnabled(mTapShockwaveEnabled);
            mJellyMesh.setMultiTouchEnabled(mMultiTouchEnabled);
            mJellyMesh.setLightSource(mLightSourceEnabled, mLightAngle, mLightIntensity / 100.0f);
        }

        private void updateVisualizer() {
            if (!mMusicReactiveEnabled || !mIsVisible) {
                if (mVisualizer != null) {
                    try {
                        mVisualizer.setEnabled(false);
                        mVisualizer.release();
                    } catch (Exception ignored) {}
                    mVisualizer = null;
                }
                return;
            }
            if (mVisualizer == null) {
                try {
                    mVisualizer = new Visualizer(0);
                    int[] range = Visualizer.getCaptureSizeRange();
                    if (range != null && range.length > 0) {
                        mVisualizer.setCaptureSize(range[0]);
                    }
                    mVisualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                        private long mLastPulseTime = 0;
                        @Override
                        public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {}

                        @Override
                        public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                            if (!mMusicReactiveEnabled || !mIsVisible || !isCurrentTargetActive() || fft == null || fft.length < 8) return;
                            float bassEnergy = 0f;
                            for (int i = 2; i < Math.min(12, fft.length); i += 2) {
                                byte rfk = fft[i];
                                byte ifk = fft[i + 1];
                                bassEnergy += (float) Math.hypot(rfk, ifk);
                            }
                            long now = SystemClock.uptimeMillis();
                            if (bassEnergy > 35.0f && (now - mLastPulseTime > 160)) {
                                mLastPulseTime = now;
                                float baseStrength = Math.min(1.0f, bassEnergy / 110.0f);
                                float finalStrength = baseStrength * (mMusicStrength / 100.0f);
                                if (finalStrength > 0.001f) {
                                    synchronized (mSurfaceLock) {
                                        mJellyMesh.triggerMusicBassPulse(finalStrength, mMusicPattern);
                                    }
                                    startAnimationLoopIfNeeded();
                                }
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true);
                    mVisualizer.setEnabled(true);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to init visualizer", e);
                    mVisualizer = null;
                }
            }
        }

        private void updateJellySetting() {
            int enabledVal = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_WALLPAPER, 1);
            if (enabledVal == -1) {
                enabledVal = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LEGACY, 1);
            }
            mJellyMode = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_MODE, 2);
            mJellyPreset = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_PRESET, 0);
            mJellyStiffness = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_STIFFNESS, 45);
            mJellyDamping = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_DAMPING, 85);
            mJellyRadius = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_RADIUS, 38);
            mJellyElasticity = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_ELASTICITY, 70);
            mJellyRippleEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_RIPPLE, 1) == 1;
            mJellyRippleStrength = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_RIPPLE_STRENGTH, 80);
            mGyroEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_GYRO, 0) == 1;
            mGyroMode = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_GYRO_MODE, 2);
            mGyroInertiaEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_GYRO_INERTIA, 1) == 1;
            mGyroInertiaStrength = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_GYRO_INERTIA_STRENGTH, 65);
            mAmbientOcclusionEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_AO, 1) == 1;
            mAmbientOcclusionIntensity = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_AO_INTENSITY, 75);
            mSnapBackEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_SNAP_BACK, 1) == 1;
            mMusicReactiveEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_MUSIC_REACTIVE, 0) == 1;
            mMusicStrength = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_MUSIC_STRENGTH, 70);
            mMusicPattern = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_MUSIC_PATTERN, 0);
            mHapticsEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_HAPTICS, 1) == 1;
            mHapticsIntensity = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_HAPTICS_INTENSITY, 80);
            mTapShockwaveEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_TAP_SHOCKWAVE, 1) == 1;
            mMultiTouchEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_MULTITOUCH, 1) == 1;
            mLightSourceEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_ENABLED, 0) == 1;
            mLightMode = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_MODE, 2);
            mLightIntensity = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_INTENSITY, 75);
            mLightAngle = (float) Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_ANGLE, 45);
            mLightBeamSpread = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_BEAM_SPREAD, 50);
            mLightHardness = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_HARDNESS, 50);
            mLightShimmerSpeed = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_SHIMMER_SPEED, 50);
            mLightSpecularColumn = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_SPECULAR_COLUMN, 1) == 1;
            mLightSolarTracking = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_SOLAR_TRACKING, 0) == 1;
            mLightGyroParallax = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_GYRO_PARALLAX, 1) == 1;
            mDepthEffectEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_DEPTH_EFFECT, 0) == 1;
            mDepthSeparation = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_DEPTH_SEPARATION, 60);
            mChargingWaveEnabled = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_CHARGING_WAVE, 1) == 1;
            mChargingWaveStrength = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_CHARGING_WAVE_STRENGTH, 80);
            mSnapBackRecoil = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_SNAPBACK_RECOIL, 75);
            mInternalTension = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_INTERNAL_TENSION, 65);
            mMaxStretch = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_MAX_STRETCH, 70);
            mSurfaceMass = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_SURFACE_MASS, 50);
            mWaveSpeed = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_WAVE_SPEED, 70);
            mJellyMesh.setDepthSeparation(mDepthSeparation / 100.0f);
            if (mDepthEffectEnabled && mForegroundBitmap == null && mBitmap != null && !mBitmap.isRecycled()) {
                asyncExtractForeground();
            }

            boolean newJelly = (enabledVal == 1);
            boolean newGyro = (Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_GYRO, 0) == 1);

            boolean needsActiveRendering = newJelly || newGyro || mMusicReactiveEnabled;
            boolean prevNeedsActiveRendering = mJellyEnabled || mGyroEnabled;

            mJellyEnabled = newJelly;
            mGyroEnabled = newGyro;

            Log.i(TAG, "updateJellySetting: jelly=" + mJellyEnabled + ", gyro=" + mGyroEnabled
                    + ", jellyMode=" + mJellyMode + ", gyroMode=" + mGyroMode
                    + ", gyroInertia=" + mGyroInertiaEnabled + ", music=" + mMusicReactiveEnabled
                    + ", multiTouch=" + mMultiTouchEnabled + ", lightSource=" + mLightSourceEnabled
                    + ", lightMode=" + mLightMode + ", lightAngle=" + mLightAngle);
            applyPhysicsSettings();
            updateSensorRegistration();
            updateVisualizer();

            synchronized (mSurfaceLock) {
                mJellyMesh.updateAllColors();
            }
            startAnimationLoopIfNeeded();
            if (mBitmap != null && !mBitmap.isRecycled()) {
                synchronized (mSurfaceLock) {
                    drawActiveFrameOnCanvas();
                }
            }
            drawFrame();
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras,
                boolean resultRequested) {
            if ("jelly_touch".equals(action)) {
                if (mJellyEnabled && (mJellyMode == 0 || mJellyMode == 2) && isKeyguardShowing()) {
                    int actionMasked = z;
                    int pointerCount = 1;
                    float x0 = (float) x;
                    float y0 = (float) y;
                    float x1 = (float) x;
                    float y1 = (float) y;
                    if (extras != null) {
                        pointerCount = extras.getInt("pointerCount", 1);
                        x0 = extras.getFloat("x0", x0);
                        y0 = extras.getFloat("y0", y0);
                        x1 = extras.getFloat("x1", x1);
                        y1 = extras.getFloat("y1", y1);
                    }
                    synchronized (mSurfaceLock) {
                        mJellyMesh.onMultiTouchEvent(actionMasked, pointerCount, x0, y0, x1, y1);
                    }
                    startAnimationLoopIfNeeded();
                }
            } else if ("android.wallpaper.touch".equals(action)) {
                if (mJellyEnabled && (mJellyMode == 1 || mJellyMode == 2) && !isKeyguardShowing()) {
                    int actionMasked = z;
                    int pointerCount = 1;
                    float x0 = (float) x;
                    float y0 = (float) y;
                    float x1 = (float) x;
                    float y1 = (float) y;
                    if (extras != null) {
                        pointerCount = extras.getInt("pointerCount", 1);
                        x0 = extras.getFloat("x0", x0);
                        y0 = extras.getFloat("y0", y0);
                        x1 = extras.getFloat("x1", x1);
                        y1 = extras.getFloat("y1", y1);
                    }
                    synchronized (mSurfaceLock) {
                        mJellyMesh.onMultiTouchEvent(actionMasked, pointerCount, x0, y0, x1, y1);
                    }
                    startAnimationLoopIfNeeded();
                }
            } else if ("jelly_ripple".equals(action)) {
                if (mJellyEnabled && mJellyRippleEnabled && (mJellyMode == 0 || mJellyMode == 2)) {
                    Rect frame = mSurfaceHolder != null ? mSurfaceHolder.getSurfaceFrame() : null;
                    float displayW = (frame != null && frame.width() > 0) ? (float) frame.width() : 1080f;
                    float displayH = (frame != null && frame.height() > 0) ? (float) frame.height() : 2400f;
                    float rippleX = displayW * 0.5f;
                    float rippleY = displayH * 0.77f;
                    if (extras != null) {
                        float ex = extras.getFloat("x", -1f);
                        float ey = extras.getFloat("y", -1f);
                        if (ex > 0 && ex < displayW) rippleX = ex;
                        if (ey > 0 && ey < displayH) rippleY = ey;
                    } else if (x > 0 && x < displayW && y > 0 && y < displayH) {
                        rippleX = (float) x;
                        rippleY = (float) y;
                    }
                    synchronized (mSurfaceLock) {
                        mJellyMesh.triggerRipple(rippleX, rippleY, 18.0f * (mJellyRippleStrength / 100.0f));
                    }
                    startAnimationLoopIfNeeded();
                }
            } else if ("jelly_charging_wave".equals(action)) {
                if (mJellyEnabled && mChargingWaveEnabled && isCurrentTargetActive()) {
                    synchronized (mSurfaceLock) {
                        mJellyMesh.triggerChargingWave(mChargingWaveStrength / 100.0f);
                    }
                    startAnimationLoopIfNeeded();
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int pointerCount = event.getPointerCount();
            float x0 = event.getX(0);
            float y0 = event.getY(0);
            float x1 = pointerCount > 1 ? event.getX(1) : x0;
            float y1 = pointerCount > 1 ? event.getY(1) : y0;

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                mIsTouching = true;
                mTouchX = x0;
                mTouchY = y0;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mIsTouching = false;
            }

            if (!mJellyEnabled || !isCurrentTargetActive()) {
                if (mLightSourceEnabled && mLightMode == 6) {
                    startAnimationLoopIfNeeded();
                }
                return;
            }

            synchronized (mSurfaceLock) {
                mJellyMesh.onMultiTouchEvent(action, pointerCount, x0, y0, x1, y1);
                if (mLightSourceEnabled && mLightMode == 6) {
                    float cx = (mBitmap != null) ? mBitmap.getWidth() * 0.5f : 540f;
                    float cy = (mBitmap != null) ? mBitmap.getHeight() * 0.5f : 1200f;
                    float touchAngle = (float) Math.toDegrees(Math.atan2(y0 - cy, x0 - cx));
                    mJellyMesh.setLightSource(true, touchAngle, mLightIntensity / 100.0f);
                }
            }
            startAnimationLoopIfNeeded();
        }

        private void startAnimationLoopIfNeeded() {
            if (!mIsLoopRunning) {
                if (!isBitmapLoaded()) {
                    loadWallpaperAndDrawFrameInternal();
                }
                mIsLoopRunning = true;
                mLastFrameTimeNanos = System.nanoTime();
                Choreographer.getInstance().postFrameCallback(this);
            }
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mIsLoopRunning) return;

            float dt = (mLastFrameTimeNanos > 0)
                    ? (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000.0f
                    : 0.016f;
            mLastFrameTimeNanos = frameTimeNanos;

            boolean stillActive = false;
            synchronized (mSurfaceLock) {
                stillActive = mJellyMesh.stepPhysics(dt);

                if (mLightSourceEnabled && isCurrentTargetActive()) {
                    if (mLightShimmerSpeed > 0 || mLightMode == 7) {
                        mLightShimmerPhase += dt * (mLightShimmerSpeed / 50.0f) * 2.8f;
                        if (mLightShimmerPhase > (float) (Math.PI * 200)) {
                            mLightShimmerPhase = 0f;
                        }
                        stillActive = true;
                    }
                }

                if (mBitmap != null && !mBitmap.isRecycled()) {
                    drawActiveFrameOnCanvas();
                }
            }

            if (stillActive) {
                Choreographer.getInstance().postFrameCallback(this);
            } else {
                mIsLoopRunning = false;
                mLastFrameTimeNanos = 0;
                if (mBitmap != null && !mBitmap.isRecycled()) {
                    drawActiveFrameOnCanvas();
                }
            }
        }

        private void drawActiveFrameOnCanvas() {
            if (mSurfaceHolder == null || mBitmap == null || mBitmap.isRecycled()) return;
            Surface surface = mSurfaceHolder.getSurface();
            if (surface == null || !surface.isValid()) return;
            Canvas canvas = null;
            try {
                canvas = mWideColorGamut
                        ? surface.lockHardwareWideColorGamutCanvas()
                        : surface.lockHardwareCanvas();
            } catch (Exception e) {
                Log.w(TAG, "Unable to lock canvas", e);
            }
            if (canvas != null) {
                try {
                    float[] verts = mJellyMesh.getVertices();
                    int[] colors = mJellyMesh.getColors();
                    canvas.drawBitmapMesh(mBitmap, JellyMesh.COLS, JellyMesh.ROWS, verts, 0, colors, 0, null);

                    if (mDepthEffectEnabled && mForegroundBitmap != null && !mForegroundBitmap.isRecycled()) {
                        float[] fgVerts = mJellyMesh.getForegroundVertices();
                        mShadowPaint.setColorFilter(new PorterDuffColorFilter(0x35000000, PorterDuff.Mode.SRC_IN));
                        canvas.drawBitmapMesh(mForegroundBitmap, JellyMesh.COLS, JellyMesh.ROWS, verts, 0, null, 0, mShadowPaint);
                        canvas.drawBitmapMesh(mForegroundBitmap, JellyMesh.COLS, JellyMesh.ROWS, fgVerts, 0, null, 0, null);
                    }

                    if (mLightSourceEnabled) {
                        drawLightAndEdgeSheen(canvas, mBitmap.getWidth(), mBitmap.getHeight());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "drawActiveFrameOnCanvas error", e);
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
            }
        }

        private final Paint mLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mEdgeSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mSpecularPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlitterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private void drawLightAndEdgeSheen(Canvas canvas, int w, int h) {
            if (!mLightSourceEnabled || mLightIntensity <= 0 || !isCurrentTargetActive()) return;

            float baseIntensity = Math.max(0.05f, Math.min(1.0f, mLightIntensity / 100.0f));
            float intensity = baseIntensity;

            // Breathing pulse modulation for preset 7
            if (mLightMode == 7) {
                float breath = (float) (0.55f + 0.45f * Math.sin(mLightShimmerPhase * 0.8f));
                intensity *= breath;
            }

            // Effective angle calculation
            float baseAngle = mLightAngle;
            if (mLightSolarTracking) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                int minuteOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
                float sunProgress = (minuteOfDay >= 360 && minuteOfDay <= 1080)
                        ? (minuteOfDay - 360) / 720.0f
                        : 0.5f;
                baseAngle = 180.0f + sunProgress * 180.0f;
            }

            if (mLightGyroParallax) {
                baseAngle += (mSmoothRoll * 45.0f);
            }

            mLightDynamicAngle = (baseAngle % 360.0f + 360.0f) % 360.0f;
            float rad = (float) Math.toRadians(mLightDynamicAngle);
            float cosA = (float) Math.cos(rad);
            float sinA = (float) Math.sin(rad);

            float cx = w * 0.5f;
            float cy = h * 0.5f;
            if (mLightGyroParallax) {
                cx += mSmoothRoll * (w * 0.15f);
                cy += mSmoothPitch * (h * 0.15f);
            }

            float lightX = cx + cosA * (w * 0.48f);
            float lightY = cy + sinA * (h * 0.48f);

            // Preset 6: Touch Follower Beam
            if (mLightMode == 6 && mIsTouching) {
                lightX = mTouchX;
                lightY = mTouchY;
            }

            float spreadFactor = 0.35f + 0.85f * (mLightBeamSpread / 100.0f);
            float hardnessFactor = 0.15f + 0.65f * (mLightHardness / 100.0f);

            // Preset color palettes
            int rgbCenter = 0xFFFFFF;
            int rgbMid = 0xFFFFFF;
            int rgbEdge = 0xFFFFFF;
            int rgbGlitter = 0xFFFFFF;

            if (mLightMode == 2) {
                // 2: Destello Solar Dorado (Sun Glitter / Golden Ocean Sun)
                rgbCenter = 0xFFF8E7;
                rgbMid = 0xFFB300;
                rgbEdge = 0xFF8F00;
                rgbGlitter = 0xFFE082;
            } else if (mLightMode == 0) {
                // 0: Híbrido Luxe (Platinum Gold & Diamond White)
                rgbCenter = 0xFFFFFF;
                rgbMid = 0xFFE6A8;
                rgbEdge = 0xFFD470;
                rgbGlitter = 0xFFFFFF;
            } else if (mLightMode == 1) {
                // 1: Cristal Diamante Puro (Brilliant White 3D)
                rgbCenter = 0xFFFFFF;
                rgbMid = 0xE0F2FE;
                rgbEdge = 0xBAE6FD;
                rgbGlitter = 0xFFFFFF;
            } else if (mLightMode == 3) {
                // 3: Prisma Holográfico (Spectral rainbow shimmer)
                float huePhase = (mLightShimmerPhase * 30.0f) % 360.0f;
                int c1 = Color.HSVToColor(new float[] { huePhase, 0.75f, 1.0f }) & 0x00FFFFFF;
                int c2 = Color.HSVToColor(new float[] { (huePhase + 60.0f) % 360.0f, 0.65f, 1.0f }) & 0x00FFFFFF;
                rgbCenter = 0xFFFFFF;
                rgbMid = c1;
                rgbEdge = c2;
                rgbGlitter = 0xFFFFFF;
            } else if (mLightMode == 4) {
                // 4: Neón Cyberpunk (Cyan & Hot Pink)
                rgbCenter = 0x00F0FF;
                rgbMid = 0x7000FF;
                rgbEdge = 0xFF007F;
                rgbGlitter = 0x00FFFF;
            } else if (mLightMode == 5) {
                // 5: Aurora Boreal Esmeralda (Emerald & Cyan)
                rgbCenter = 0x00FF88;
                rgbMid = 0x00E5FF;
                rgbEdge = 0x10B981;
                rgbGlitter = 0x6EE7B7;
            }

            // 1. Foco de Luz (Spotlight Beam & Radial Glow)
            float spotRadius = Math.max(w, h) * spreadFactor;
            int alphaCenter = (int) (175 * intensity);
            int alphaMid = (int) (75 * intensity * (1.0f - hardnessFactor * 0.35f));
            int colorCenter = (alphaCenter << 24) | rgbCenter;
            int colorMid = (alphaMid << 24) | rgbMid;

            RadialGradient spotGradient = new RadialGradient(
                    lightX, lightY, spotRadius,
                    new int[] { colorCenter, colorMid, rgbCenter & 0x00FFFFFF },
                    new float[] { 0.0f, Math.min(0.85f, hardnessFactor), 1.0f },
                    Shader.TileMode.CLAMP);
            mLightPaint.setShader(spotGradient);
            mLightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            canvas.drawRect(0, 0, w, h, mLightPaint);

            // 2. Reflejo en el Borde de la Pantalla (Edge Rim Sheen / Border Glass Glare)
            float startX = cx - cosA * (w * 0.5f);
            float startY = cy - sinA * (h * 0.5f);
            float endX = cx + cosA * (w * 0.5f);
            float endY = cy + sinA * (h * 0.5f);

            int alphaSheen = (int) (200 * intensity);
            int sheenColor = (alphaSheen << 24) | rgbEdge;
            float stopMid = Math.max(0.50f, 0.90f - 0.30f * (1.0f - hardnessFactor));
            LinearGradient edgeGradient = new LinearGradient(
                    startX, startY, endX, endY,
                    new int[] { rgbEdge & 0x00FFFFFF, rgbEdge & 0x00FFFFFF, sheenColor },
                    new float[] { 0.0f, stopMid, 1.0f },
                    Shader.TileMode.CLAMP);
            mEdgeSheenPaint.setShader(edgeGradient);
            mEdgeSheenPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
            canvas.drawRect(0, 0, w, h, mEdgeSheenPaint);

            // 3. Columna de Reflejo Solar (Sun Glitter / Ocean Specular Path)
            if (mLightSpecularColumn) {
                float colTopWidth = w * (0.15f + 0.25f * spreadFactor);
                float colBottomWidth = w * (0.35f + 0.45f * spreadFactor);

                float pathTopX = lightX;
                float pathTopY = Math.min(lightY, h * 0.35f);
                float pathBottomX = cx + (mSmoothRoll * w * 0.25f);
                float pathBottomY = h;

                int alphaColCore = (int) (140 * intensity * (0.5f + 0.5f * hardnessFactor));
                int colCoreColor = (alphaColCore << 24) | rgbCenter;
                int alphaColGlow = (int) (65 * intensity);
                int colGlowColor = (alphaColGlow << 24) | rgbMid;

                LinearGradient colGrad = new LinearGradient(
                        pathTopX - colTopWidth * 0.5f, pathTopY,
                        pathTopX + colTopWidth * 0.5f, pathTopY,
                        new int[] { rgbMid & 0x00FFFFFF, colGlowColor, colCoreColor, colGlowColor, rgbMid & 0x00FFFFFF },
                        new float[] { 0.0f, 0.25f, 0.50f, 0.75f, 1.0f },
                        Shader.TileMode.CLAMP);
                mSpecularPaint.setShader(colGrad);
                mSpecularPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
                canvas.drawRect(0, 0, w, h, mSpecularPaint);

                // Continuous Fluid Liquid Water Caustic Wave Ribbons (Pure fluid motion, no dots)
                mGlitterPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SCREEN));
                mGlitterPaint.setStyle(Paint.Style.STROKE);
                mGlitterPaint.setStrokeCap(Paint.Cap.ROUND);
                float shimmerTime = mLightShimmerPhase;
                int numWaves = 18;

                Path wavePath = new Path();
                for (int n = 0; n < numWaves; n++) {
                    float vFrac = (n + 1) / (float) (numWaves + 1);
                    float yCenter = pathTopY + vFrac * (pathBottomY - pathTopY);
                    float currentWidth = colTopWidth + vFrac * (colBottomWidth - colTopWidth);
                    float halfSpan = currentWidth * 0.65f;

                    float wavePhase = shimmerTime * 2.0f + n * 0.95f;
                    float waveAmp = (6.0f + 14.0f * vFrac) * (0.4f + 0.6f * spreadFactor);

                    float leftX = pathTopX - halfSpan;
                    float rightX = pathTopX + halfSpan;
                    float midX1 = pathTopX - halfSpan * 0.45f;
                    float midX2 = pathTopX + halfSpan * 0.45f;

                    float yOffset1 = (float) Math.sin(wavePhase) * waveAmp;
                    float yOffset2 = (float) Math.cos(wavePhase * 1.3f) * waveAmp;

                    wavePath.reset();
                    wavePath.moveTo(leftX, yCenter);
                    wavePath.cubicTo(
                            midX1, yCenter + yOffset1,
                            midX2, yCenter + yOffset2,
                            rightX, yCenter);

                    float waveAlphaFrac = (float) Math.sin(vFrac * Math.PI) * (0.6f + 0.4f * (float) Math.sin(wavePhase * 1.5f));
                    int waveAlpha = (int) (120 * intensity * Math.max(0.1f, waveAlphaFrac));
                    int waveColor = (waveAlpha << 24) | rgbGlitter;

                    mGlitterPaint.setColor(waveColor);
                    mGlitterPaint.setStrokeWidth(3.0f + 5.0f * hardnessFactor * (1.0f - vFrac * 0.3f));
                    canvas.drawPath(wavePath, mGlitterPaint);
                }
            }
        }

        private void asyncExtractForeground() {
            if (mIsSegmenting || mBitmap == null || mBitmap.isRecycled()) return;
            mIsSegmenting = true;
            final Bitmap srcCopy = mBitmap;
            mWorker.getThreadHandler().post(() -> {
                try {
                    Bitmap fg = extractForegroundSubject(srcCopy);
                    synchronized (mSurfaceLock) {
                        if (mForegroundBitmap != null && mForegroundBitmap != fg) {
                            mForegroundBitmap.recycle();
                        }
                        mForegroundBitmap = fg;
                    }
                    if (fg != null) {
                        startAnimationLoopIfNeeded();
                    }
                } finally {
                    mIsSegmenting = false;
                }
            });
        }

        private Bitmap extractForegroundSubject(Bitmap source) {
            if (source == null || source.isRecycled()) return null;
            try {
                int w = source.getWidth();
                int h = source.getHeight();
                int sampleW = Math.min(w, 270);
                int sampleH = Math.min(h, 600);
                Bitmap small = Bitmap.createScaledBitmap(source, sampleW, sampleH, true);
                int[] pixels = new int[sampleW * sampleH];
                small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH);

                long bgR = 0, bgG = 0, bgB = 0;
                int bgCount = 0;
                for (int x = 0; x < sampleW; x++) {
                    int cTop = pixels[x];
                    int cBot = pixels[(sampleH - 1) * sampleW + x];
                    bgR += Color.red(cTop) + Color.red(cBot);
                    bgG += Color.green(cTop) + Color.green(cBot);
                    bgB += Color.blue(cTop) + Color.blue(cBot);
                    bgCount += 2;
                }
                for (int y = 0; y < sampleH; y++) {
                    int cLeft = pixels[y * sampleW];
                    int cRight = pixels[y * sampleW + sampleW - 1];
                    bgR += Color.red(cLeft) + Color.red(cRight);
                    bgG += Color.green(cLeft) + Color.green(cRight);
                    bgB += Color.blue(cLeft) + Color.blue(cRight);
                    bgCount += 2;
                }
                int avgBgR = (int) (bgR / Math.max(1, bgCount));
                int avgBgG = (int) (bgG / Math.max(1, bgCount));
                int avgBgB = (int) (bgB / Math.max(1, bgCount));

                byte[] mask = new byte[sampleW * sampleH];
                float centerX = sampleW * 0.5f;
                float centerY = sampleH * 0.48f;
                float sigmaX = sampleW * 0.38f;
                float sigmaY = sampleH * 0.42f;

                for (int y = 0; y < sampleH; y++) {
                    float dy = (y - centerY) / sigmaY;
                    for (int x = 0; x < sampleW; x++) {
                        float dx = (x - centerX) / sigmaX;
                        float centerPrior = (float) Math.exp(-(dx * dx + dy * dy) * 0.8f);

                        int p = pixels[y * sampleW + x];
                        int pr = Color.red(p);
                        int pg = Color.green(p);
                        int pb = Color.blue(p);

                        float colorDist = (float) Math.hypot(Math.hypot(pr - avgBgR, pg - avgBgG), pb - avgBgB);
                        float salience = (colorDist / 255.0f) * 1.35f * centerPrior;

                        if (salience > 0.25f) {
                            float alphaNorm = Math.min(1.0f, (salience - 0.25f) / 0.25f);
                            mask[y * sampleW + x] = (byte) ((int) (alphaNorm * 255));
                        } else {
                            mask[y * sampleW + x] = 0;
                        }
                    }
                }
                small.recycle();

                Bitmap alphaBitmap = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ALPHA_8);
                ByteBuffer buffer = ByteBuffer.wrap(mask);
                alphaBitmap.copyPixelsFromBuffer(buffer);

                Bitmap scaledAlpha = Bitmap.createScaledBitmap(alphaBitmap, w, h, true);
                alphaBitmap.recycle();

                Bitmap fgBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(fgBitmap);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                c.drawBitmap(source, 0, 0, paint);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                c.drawBitmap(scaledAlpha, 0, 0, paint);
                scaledAlpha.recycle();

                return fgBitmap;
            } catch (Throwable t) {
                Log.e(TAG, "extractForegroundSubject error", t);
                return null;
            }
        }

        private Bitmap centerCropBitmapForDisplay(Bitmap source, int targetW, int targetH) {
            if (source == null || source.isRecycled()) {
                return source;
            }
            int effectiveW = (targetW > 0) ? targetW : DEFAULT_PANEL_WIDTH;
            int effectiveH = (targetH > 0) ? targetH : DEFAULT_PANEL_HEIGHT;

            int srcW = source.getWidth();
            int srcH = source.getHeight();
            if (srcW == effectiveW && srcH == effectiveH) {
                return source;
            }

            try {
                float scale = Math.max((float) effectiveW / srcW, (float) effectiveH / srcH);
                int scaledW = Math.round(srcW * scale);
                int scaledH = Math.round(srcH * scale);

                Bitmap scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true);
                int cropX = Math.max(0, (scaledW - effectiveW) / 2);
                int cropY = Math.max(0, (scaledH - effectiveH) / 2);

                if (cropX + effectiveW > scaledW) cropX = Math.max(0, scaledW - effectiveW);
                if (cropY + effectiveH > scaledH) cropY = Math.max(0, scaledH - effectiveH);

                Bitmap result = Bitmap.createBitmap(scaled, cropX, cropY, effectiveW, effectiveH);
                if (scaled != source && scaled != result) {
                    scaled.recycle();
                }
                if (source != result) {
                    source.recycle();
                }
                return result;
            } catch (Throwable t) {
                Log.e(TAG, "centerCropBitmapForDisplay error", t);
                return source;
            }
        }

        private void updateSensorRegistration() {
            if (mSensorManager == null) {
                Context context = getDisplayContext();
                if (context != null) {
                    mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
                }
            }
            if (mSensorManager != null) {
                if (mRotationSensor == null) {
                    mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                    if (mRotationSensor == null) {
                        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
                    }
                }
                if (mLinearAccelSensor == null) {
                    mLinearAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                }
                if (mAccelSensor == null) {
                    mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                }
            }
            if (mSensorManager == null) return;

            if (mIsVisible) {
                if ((mGyroEnabled || (mLightSourceEnabled && (mLightMode == 2 || mLightGyroParallax))) && mRotationSensor != null) {
                    mSensorManager.registerListener(mSensorListener, mRotationSensor, SensorManager.SENSOR_DELAY_GAME);
                }
                if (mGyroInertiaEnabled) {
                    if (mLinearAccelSensor != null) {
                        mSensorManager.registerListener(mSensorListener, mLinearAccelSensor, SensorManager.SENSOR_DELAY_GAME);
                    }
                    if (mAccelSensor != null) {
                        mSensorManager.registerListener(mSensorListener, mAccelSensor, SensorManager.SENSOR_DELAY_GAME);
                    }
                }
            } else {
                mSensorManager.unregisterListener(mSensorListener);
                mJellyMesh.resetGyro();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mIsVisible = visible;
            if (visible) {
                mSmoothRoll = 0f;
                mSmoothPitch = 0f;
                mLastAppliedRoll = 0f;
                mLastAppliedPitch = 0f;
            }
            updateSensorRegistration();
            updateVisualizer();
            if (visible) {
                drawFrame();
            }
        }

        @Override
        public void onDestroy() {
            if (mVisualizer != null) {
                try {
                    mVisualizer.setEnabled(false);
                    mVisualizer.release();
                } catch (Exception ignored) {}
                mVisualizer = null;
            }
            Context context = getDisplayContext();
            if (context != null) {
                context.getContentResolver().unregisterContentObserver(mSettingsObserver);
                DisplayManager displayManager = context.getSystemService(DisplayManager.class);
                if (displayManager != null) displayManager.unregisterDisplayListener(this);
            }
            if (mSensorManager != null) {
                mSensorManager.unregisterListener(mSensorListener);
            }
            if (mPowerReceiver != null) {
                try {
                    ImageWallpaper.this.unregisterReceiver(mPowerReceiver);
                } catch (Exception e) {
                    try {
                        getDisplayContext().unregisterReceiver(mPowerReceiver);
                    } catch (Exception ignored) {}
                }
                mPowerReceiver = null;
            }
            mColorExtractor.cleanUp();
        }

        @Override
        public boolean shouldZoomOutWallpaper() {
            return false;
        }

        @Override
        public boolean shouldWaitForEngineShown() {
            return true;
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceChanged: width=" + width + ", height=" + height);
            }
            synchronized (mSurfaceLock) {
                if (width > 0 && height > 0) {
                    mJellyMesh.setSize(width, height);
                }
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            if (DEBUG) {
                Log.i(TAG, "onSurfaceDestroyed");
            }
            synchronized (mSurfaceLock) {
                mSurfaceHolder = null;
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            if (DEBUG) {
                Log.i(TAG, "onSurfaceCreated");
            }
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceRedrawNeeded");
            }
            drawFrame();
        }

        private void drawFrame() {
            mLongExecutor.execute(this::drawFrameSynchronized);
        }

        private void drawFrameSynchronized() {
            synchronized (mLock) {
                if (mDrawn) return;
                drawFrameInternal();
            }
        }

        private void drawFrameInternal() {
            // load the wallpaper if not already done
            if (!isBitmapLoaded()) {
                loadWallpaperAndDrawFrameInternal();
            } else {
                synchronized (mSurfaceLock) {
                    if (mSurfaceHolder == null) {
                        Log.i(TAG, "Surface released before the image could be drawn");
                        return;
                    }
                    mBitmapUsages++;
                    boolean activeMode = mJellyEnabled || mGyroEnabled;
                    Rect dest = mSurfaceHolder.getSurfaceFrame();
                    if (dest != null && dest.width() > 0 && dest.height() > 0) {
                        mJellyMesh.setSize(dest.width(), dest.height());
                    }
                    if (activeMode) {
                        drawActiveFrameOnCanvas();
                        mDrawn = true;
                    } else {
                        drawFrameOnCanvas(mBitmap);
                    }
                    reportEngineShown(false);
                    if (!activeMode) {
                        unloadBitmapIfNotUsedInternal();
                    }
                }
            }
        }

        @VisibleForTesting
        void drawFrameOnCanvas(Bitmap bitmap) {
            Trace.beginSection("ImageWallpaper.CanvasEngine#drawFrame");
            Surface surface = mSurfaceHolder.getSurface();
            Canvas canvas = null;
            try {
                canvas = mWideColorGamut
                        ? surface.lockHardwareWideColorGamutCanvas()
                        : surface.lockHardwareCanvas();
            } catch (IllegalStateException e) {
                Log.w(TAG, "Unable to lock canvas", e);
            }
            if (canvas != null) {
                Rect dest = mSurfaceHolder.getSurfaceFrame();
                try {
                    canvas.drawBitmap(bitmap, null, dest, null);
                    if (mLightSourceEnabled && dest != null) {
                        drawLightAndEdgeSheen(canvas, dest.width(), dest.height());
                    }
                    mDrawn = true;
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
            }
            Trace.endSection();
        }

        @VisibleForTesting
        boolean isBitmapLoaded() {
            return mBitmap != null && !mBitmap.isRecycled();
        }

        private void unloadBitmapIfNotUsed() {
            mLongExecutor.execute(this::unloadBitmapIfNotUsedSynchronized);
        }

        private void unloadBitmapIfNotUsedSynchronized() {
            synchronized (mLock) {
                unloadBitmapIfNotUsedInternal();
            }
        }

        private void unloadBitmapIfNotUsedInternal() {
            mBitmapUsages -= 1;
            if (mBitmapUsages <= 0) {
                mBitmapUsages = 0;
                unloadBitmapInternal();
            }
        }

        private void unloadBitmapInternal() {
            Trace.beginSection("ImageWallpaper.CanvasEngine#unloadBitmap");
            if (mBitmap != null) {
                mBitmap.recycle();
            }
            if (mForegroundBitmap != null) {
                mForegroundBitmap.recycle();
                mForegroundBitmap = null;
            }
            mBitmap = null;
            synchronized (mSurfaceLock) {
                if (mSurfaceHolder != null) mSurfaceHolder.getSurface().hwuiDestroy();
            }
            mWallpaperManager.forgetLoadedWallpaper();
            Trace.endSection();
        }

        private void loadWallpaperAndDrawFrameInternal() {
            Trace.beginSection("WPMS.ImageWallpaper.CanvasEngine#loadWallpaper");
            boolean loadSuccess = false;
            Bitmap bitmap;
            try {
                Trace.beginSection("WPMS.getBitmapAsUser");
                bitmap = mWallpaperManager.getBitmapAsUser(
                        mUserTracker.getUserId(), false, getSourceFlag(), true);
                if (bitmap != null
                        && bitmap.getByteCount() > RecordingCanvas.MAX_BITMAP_SIZE) {
                    throw new RuntimeException("Wallpaper is too large to draw!");
                }
            } catch (RuntimeException | OutOfMemoryError exception) {

                // Note that if we do fail at this, and the default wallpaper can't
                // be loaded, we will go into a cycle. Don't do a build where the
                // default wallpaper can't be loaded.
                Log.w(TAG, "Unable to load wallpaper!", exception);
                Trace.beginSection("WPMS.clearWallpaper");
                mWallpaperManager.clearWallpaper(getWallpaperFlags(), mUserTracker.getUserId());
                Trace.endSection();

                try {
                    Trace.beginSection("WPMS.getBitmapAsUser_defaultWallpaper");
                    bitmap = mWallpaperManager.getBitmapAsUser(
                            mUserTracker.getUserId(), false, getSourceFlag(), true);
                } catch (RuntimeException | OutOfMemoryError e) {
                    Log.w(TAG, "Unable to load default wallpaper!", e);
                    bitmap = null;
                } finally {
                    Trace.endSection();
                }
            } finally {
                Trace.endSection();
            }

            if (bitmap == null) {
                Log.w(TAG, "Could not load bitmap");
            } else if (bitmap.isRecycled()) {
                Log.e(TAG, "Attempt to load a recycled bitmap");
            } else if (mBitmap == bitmap) {
                Log.e(TAG, "Loaded a bitmap that was already loaded");
            } else {
                // at this point, loading is done correctly.
                loadSuccess = true;

                // Reescalar y centrar la imagen exactamente a la resolución del panel (CenterCrop)
                Rect displayBounds = null;
                try {
                    if (mWindowManagerProvider != null) {
                        displayBounds = mWindowManagerProvider.getWindowManager(getDisplayContext())
                                .getCurrentWindowMetrics()
                                .getBounds();
                    }
                } catch (Exception ignored) {}
                int targetW = (displayBounds != null && displayBounds.width() > 0) ? displayBounds.width() : DEFAULT_PANEL_WIDTH;
                int targetH = (displayBounds != null && displayBounds.height() > 0) ? displayBounds.height() : DEFAULT_PANEL_HEIGHT;
                bitmap = centerCropBitmapForDisplay(bitmap, targetW, targetH);

                // recycle the previously loaded bitmap
                if (mBitmap != null) {
                    Trace.beginSection("WPMS.mBitmap.recycle");
                    mBitmap.recycle();
                    Trace.endSection();
                }
                if (mForegroundBitmap != null) {
                    mForegroundBitmap.recycle();
                    mForegroundBitmap = null;
                }
                mBitmap = bitmap;
                if (mDepthEffectEnabled) {
                    asyncExtractForeground();
                }
                Trace.beginSection("WPMS.wallpaperSupportsWcg");
                mWideColorGamut = mWallpaperManager.wallpaperSupportsWcg(getSourceFlag());
                Trace.endSection();

                // +2 usages for the color extraction and the delayed unload.
                mBitmapUsages += 2;
                Trace.beginSection("WPMS.recomputeColorExtractorMiniBitmap");
                recomputeColorExtractorMiniBitmap();
                Trace.endSection();
                Trace.beginSection("WPMS.drawFrameInternal");
                drawFrameInternal();
                Trace.endSection();

                /*
                 * after loading, the bitmap will be unloaded after all these conditions:
                 *   - the frame is redrawn
                 *   - the mini bitmap from color extractor is recomputed
                 *   - the DELAY_UNLOAD_BITMAP has passed
                 */
                mLongExecutor.executeDelayed(
                        this::unloadBitmapIfNotUsedSynchronized, DELAY_UNLOAD_BITMAP);
            }
            // even if the bitmap cannot be loaded, call reportEngineShown
            if (!loadSuccess) reportEngineShown(false);
            Trace.endSection();
        }

        private void onColorsProcessed(List<RectF> regions, List<WallpaperColors> colors) {
            try {
                notifyLocalColorsChanged(regions, colors);
            } catch (RuntimeException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        /**
         * Helper to return the flag from where the source bitmap is from.
         * Similar to {@link #getWallpaperFlags()}, but returns (FLAG_SYSTEM) instead of
         * (FLAG_LOCK | FLAG_SYSTEM) if this engine is used for both lock screen & home screen.
         */
        private @SetWallpaperFlags int getSourceFlag() {
            return getWallpaperFlags() == FLAG_LOCK ? FLAG_LOCK : FLAG_SYSTEM;
        }

        @VisibleForTesting
        void recomputeColorExtractorMiniBitmap() {
            mColorExtractor.onBitmapChanged(mBitmap);
        }

        @VisibleForTesting
        void onMiniBitmapUpdated() {
            unloadBitmapIfNotUsed();
        }

        @Override
        public @Nullable WallpaperColors onComputeColors() {
            return mColorExtractor.onComputeColors();
        }

        @Override
        public @Nullable WallpaperColors computeColorsWithDim(float dimAmount) {
            return mColorExtractor.onComputeColorsWithDim(dimAmount);
        }

        @Override
        public boolean supportsLocalColorExtraction() {
            return true;
        }

        @Override
        public void addLocalColorsAreas(@NonNull List<RectF> regions) {
            // this call will activate the offset notifications
            // if no colors were being processed before
            mColorExtractor.addLocalColorsAreas(regions);
        }

        @Override
        public void removeLocalColorsAreas(@NonNull List<RectF> regions) {
            // this call will deactivate the offset notifications
            // if we are no longer processing colors
            mColorExtractor.removeLocalColorAreas(regions);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                float xOffsetStep, float yOffsetStep,
                int xPixelOffset, int yPixelOffset) {
            final int pages;
            if (xOffsetStep > 0 && xOffsetStep <= 1) {
                pages = Math.round(1 / xOffsetStep) + 1;
            } else {
                pages = 1;
            }
            if (pages != mPages || !mPagesComputed) {
                mPages = pages;
                mPagesComputed = true;
                mColorExtractor.onPageChanged(mPages);
            }
        }

        @Override
        public void onDimAmountChanged(float dimAmount) {
            mColorExtractor.onDimAmountChanged(dimAmount);
        }

        @Override
        public void onDisplayAdded(int displayId) {

        }

        @Override
        public void onDisplayRemoved(int displayId) {

        }

        @Override
        public void onDisplayChanged(int displayId) {
            Trace.beginSection("ImageWallpaper.CanvasEngine#onDisplayChanged");
            try {
                // changes the display in the color extractor
                // the new display dimensions will be used in the next color computation
                if (displayId == getDisplayContext().getDisplayId()) {
                    getDisplaySizeAndUpdateColorExtractor();
                }
            } finally {
                Trace.endSection();
            }
        }

        private void getDisplaySizeAndUpdateColorExtractor() {
            Rect window = mWindowManagerProvider.getWindowManager(getDisplayContext())
                    .getCurrentWindowMetrics()
                    .getBounds();
            mColorExtractor.setDisplayDimensions(window.width(), window.height());
        }

        @Override
        protected void dump(String prefix, FileDescriptor fd, PrintWriter out, String[] args) {
            super.dump(prefix, fd, out, args);
            out.print(prefix); out.print("Engine="); out.println(this);
            out.print(prefix); out.print("valid surface=");
            out.println(getSurfaceHolder() != null && getSurfaceHolder().getSurface() != null
                    ? getSurfaceHolder().getSurface().isValid()
                    : "null");

            out.print(prefix); out.print("surface frame=");
            out.println(getSurfaceHolder() != null ? getSurfaceHolder().getSurfaceFrame() : "null");

            out.print(prefix); out.print("bitmap=");
            out.println(mBitmap == null ? "null"
                    : mBitmap.isRecycled() ? "recycled"
                    : mBitmap.getWidth() + "x" + mBitmap.getHeight());

            mColorExtractor.dump(prefix, fd, out, args);
        }
    }
}
