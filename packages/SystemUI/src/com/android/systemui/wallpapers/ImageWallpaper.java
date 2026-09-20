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
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
import android.util.DisplayMetrics;
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
    public static final String KEY_JELLY_LIGHT_CUSTOM_COLOR = "jelly_wallpaper_light_custom_color";
    public static final String KEY_JELLY_LIGHT_INTENSITY = "jelly_wallpaper_light_intensity";
    public static final String KEY_JELLY_LIGHT_ANGLE = "jelly_wallpaper_light_angle";
    public static final String KEY_JELLY_LIGHT_BEAM_SPREAD = "jelly_wallpaper_light_beam_spread";
    public static final String KEY_JELLY_LIGHT_HARDNESS = "jelly_wallpaper_light_hardness";
    public static final String KEY_JELLY_LIGHT_SHIMMER_SPEED = "jelly_wallpaper_light_shimmer_speed";
    public static final String KEY_JELLY_LIGHT_SPECULAR_COLUMN = "jelly_wallpaper_light_specular_column";
    public static final String KEY_JELLY_LIGHT_WATER_WAVES = "jelly_wallpaper_light_water_waves";
    public static final String KEY_JELLY_LIGHT_WATER_WAVES_INTENSITY = "jelly_wallpaper_light_water_waves_intensity";
    public static final String KEY_JELLY_WATER_PRESET = "jelly_wallpaper_water_preset";
    public static final String KEY_JELLY_WATER_WAVE_SIZE = "jelly_wallpaper_water_wave_size";
    public static final String KEY_JELLY_WATER_WAVE_SPEED = "jelly_wallpaper_water_wave_speed";
    public static final String KEY_JELLY_WATER_GLITTER_DENSITY = "jelly_wallpaper_water_glitter_density";
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
        private String mLightCustomColor = "";
        private float mLightAngle = 45.0f;
        private int mLightIntensity = 75;
        private int mLightBeamSpread = 50;
        private int mLightHardness = 50;
        private int mLightShimmerSpeed = 50;
        private boolean mLightSpecularColumn = true;
        private boolean mLightWaterWaves = false;
        private int mLightWaterWavesIntensity = 75;
        private int mWaterPreset = 1;
        private int mWaterWaveSize = 50;
        private int mWaterWaveSpeed = 60;
        private int mWaterGlitterDensity = 70;
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
        private BroadcastReceiver mTimeReceiver = null;
        private Bitmap mForegroundBitmap = null;
        private static final PorterDuffXfermode XFERMODE_SCREEN = new PorterDuffXfermode(PorterDuff.Mode.SCREEN);
        private static final PorterDuffXfermode XFERMODE_MULTIPLY = new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY);
        private static final PorterDuffXfermode XFERMODE_DST_IN = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        private static final PorterDuffColorFilter SHADOW_FILTER = new PorterDuffColorFilter(0x22000000, PorterDuff.Mode.SRC_IN);
        private static final PorterDuffColorFilter CONTACT_SHADOW_FILTER = new PorterDuffColorFilter(0x3B000000, PorterDuff.Mode.SRC_IN);

        private static final float[] SPOT_GRAD_POS = new float[] { 0.0f, 0.20f, 0.55f, 1.0f };
        private static final float[] LATERAL_GRAD_POS = new float[] { 0.0f, 0.18f, 0.38f, 0.50f, 0.62f, 0.82f, 1.0f };
        private static final float[] LENGTH_GRAD_POS = new float[] { 0.0f, 0.12f, 0.60f, 1.0f };
        private static final float[] EDGE_GRAD_POS = new float[] { 0.0f, 0.75f, 1.0f };
        private static final float[] SUNSET_GRAD_POS = new float[] { 0.0f, 0.5f, 1.0f };
        private static final float[] RAY_GRAD_POS = new float[] { 0.0f, 0.45f, 1.0f };

        private static final int[] LENGTH_GRAD_COLORS = new int[] { 0x00FFFFFF, 0xFFFFFFFF, 0x66FFFFFF, 0x00FFFFFF };
        private static final int[] SUNSET_GRAD_COLORS = new int[] { 0x50FFA726, 0x2AFFF3E0, 0x00000000 };

        private final float[] mHsvBuffer = new float[3];
        private final int[] mSpotGradColors = new int[4];
        private final int[] mLateralGradColors = new int[7];
        private final int[] mEdgeGradColors = new int[3];
        private final int[] mRayGradColors = new int[3];

        private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG) {{ setColorFilter(SHADOW_FILTER); }};
        private final Paint mContactShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG) {{ setColorFilter(CONTACT_SHADOW_FILTER); }};
        private float[] mShadowVerts = new float[(JellyMesh.COLS + 1) * (JellyMesh.ROWS + 1) * 2];
        private float[] mContactShadowVerts = new float[(JellyMesh.COLS + 1) * (JellyMesh.ROWS + 1) * 2];
        private float[] mWaterVerts = new float[(JellyMesh.COLS + 1) * (JellyMesh.ROWS + 1) * 2];
        private int[] mWaterColors = new int[(JellyMesh.COLS + 1) * (JellyMesh.ROWS + 1)];
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
                if (!mJellyEnabled || (!mGyroEnabled && !mGyroInertiaEnabled && !(mLightSourceEnabled && mLightGyroParallax)) || !mIsVisible || !isCurrentTargetActive()) return;
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
                        float finalLightAngle;
                        if (mLightSolarTracking) {
                            finalLightAngle = calculateSolarTrackingAngle() + (mLightGyroParallax ? finalRoll * 30.0f : 0f);
                        } else {
                            finalLightAngle = mLightGyroParallax ? (float) Math.toDegrees(Math.atan2(finalPitch, -finalRoll)) : mLightAngle;
                        }
                        mLightDynamicAngle = (finalLightAngle % 360.0f + 360.0f) % 360.0f;
                        mJellyMesh.setLightSource(true, mLightDynamicAngle, mLightIntensity / 100.0f);
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
                        float finalLightAngle;
                        if (mLightSolarTracking) {
                            finalLightAngle = calculateSolarTrackingAngle() + (mLightGyroParallax ? finalRoll * 30.0f : 0f);
                        } else {
                            finalLightAngle = mLightGyroParallax ? (float) Math.toDegrees(Math.atan2(finalPitch, -finalRoll)) : mLightAngle;
                        }
                        mLightDynamicAngle = (finalLightAngle % 360.0f + 360.0f) % 360.0f;
                        mJellyMesh.setLightSource(true, mLightDynamicAngle, mLightIntensity / 100.0f);
                        startAnimationLoopIfNeeded();
                    }
                } else if (type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    float lx = event.values[0];
                    float ly = event.values[1];
                    float shakeIntensity = (float) Math.sqrt(lx * lx + ly * ly);

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
                    float shakeIntensity = (float) Math.sqrt(linX * linX + linY * linY);

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

        private Rect getDisplayBounds() {
            Rect displayBounds = null;
            try {
                if (mWindowManagerProvider != null) {
                    displayBounds = mWindowManagerProvider.getWindowManager(getDisplayContext())
                            .getCurrentWindowMetrics()
                            .getBounds();
                }
            } catch (Exception ignored) {}
            if (displayBounds == null || displayBounds.width() <= 0 || displayBounds.height() <= 0) {
                try {
                    DisplayMetrics dm = getDisplayContext().getResources().getDisplayMetrics();
                    displayBounds = new Rect(0, 0, dm.widthPixels, dm.heightPixels);
                } catch (Exception ignored) {}
            }
            if (displayBounds == null || displayBounds.width() <= 0 || displayBounds.height() <= 0) {
                displayBounds = new Rect(0, 0, DEFAULT_PANEL_WIDTH, DEFAULT_PANEL_HEIGHT);
            }
            return displayBounds;
        }

        private void handleDisplayOrSurfaceChange() {
            Rect bounds = getDisplayBounds();
            int targetW = bounds.width();
            int targetH = bounds.height();

            boolean needsReload = false;
            synchronized (mSurfaceLock) {
                if (mJellyEnabled) {
                    if (mSurfaceHolder != null) {
                        mSurfaceHolder.setFixedSize(targetW, targetH);
                    }
                    mJellyMesh.setSize(targetW, targetH);
                    if (mBitmap == null || mBitmap.isRecycled()
                            || mBitmap.getWidth() != targetW
                            || mBitmap.getHeight() != targetH) {
                        needsReload = true;
                    }
                } else {
                    if (mBitmap == null || mBitmap.isRecycled()) {
                        needsReload = true;
                    }
                }
            }

            if (needsReload) {
                mDrawn = false;
                mLongExecutor.execute(this::loadWallpaperAndDrawFrameInternal);
            } else {
                mDrawn = false;
                drawFrame();
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
            Rect displayBounds = getDisplayBounds();
            int width = displayBounds.width();
            int height = displayBounds.height();
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
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_CUSTOM_COLOR),
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
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_WATER_WAVES),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_LIGHT_WATER_WAVES_INTENSITY),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_WATER_PRESET),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_WATER_WAVE_SIZE),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_WATER_WAVE_SPEED),
                    false,
                    mSettingsObserver
            );
            getDisplayContext().getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(KEY_JELLY_WATER_GLITTER_DENSITY),
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

            IntentFilter timeFilter = new IntentFilter();
            timeFilter.addAction(Intent.ACTION_TIME_TICK);
            timeFilter.addAction(Intent.ACTION_TIME_CHANGED);
            timeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            mTimeReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mJellyEnabled && mLightSourceEnabled && mLightSolarTracking && mIsVisible) {
                        float solarAngle = calculateSolarTrackingAngle();
                        mLightDynamicAngle = solarAngle;
                        mJellyMesh.setLightSource(true, solarAngle, mLightIntensity / 100.0f);
                        if (mBitmap != null && !mBitmap.isRecycled()) {
                            synchronized (mSurfaceLock) {
                                drawActiveFrameOnCanvas();
                            }
                        }
                    }
                }
            };
            try {
                ImageWallpaper.this.registerReceiver(mTimeReceiver, timeFilter, Context.RECEIVER_EXPORTED);
            } catch (Exception e) {
                try {
                    getDisplayContext().registerReceiver(mTimeReceiver, timeFilter, Context.RECEIVER_EXPORTED);
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
            if (!mJellyEnabled) return false;
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

        private float calculateSolarTrackingAngle() {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            int minute = cal.get(java.util.Calendar.MINUTE);
            int second = cal.get(java.util.Calendar.SECOND);
            int millis = cal.get(java.util.Calendar.MILLISECOND);

            // Ciclo solar continuo de 24 horas alrededor del dispositivo:
            // 06:00 (Amanecer en el Este / Izquierda = 180°)
            // 12:00 (Cenit solar / Arriba = 270°)
            // 18:00 (Puesta de sol en el Oeste / Derecha = 360° / 0°)
            // 00:00 (Medianoche / Nadir lunar / Abajo = 90°)
            float secOfDayFrom6 = (((hour - 6 + 24) % 24) * 3600f) + (minute * 60f) + second + (millis / 1000f);
            float solarProgress = secOfDayFrom6 / 86400f; // 0.0f a 1.0f continuo
            return (180.0f + solarProgress * 360.0f) % 360.0f;
        }

        private static class CircadianColors {
            int rgbCenter;
            int rgbMid;
            int rgbEdge;
            int rgbGlitter;
        }

        private int lerpColor(int c1, int c2, float frac) {
            frac = Math.max(0.0f, Math.min(1.0f, frac));
            int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
            int r = (int) (r1 + (r2 - r1) * frac);
            int g = (int) (g1 + (g2 - g1) * frac);
            int b = (int) (b1 + (b2 - b1) * frac);
            return (r << 16) | (g << 8) | b;
        }

        private CircadianColors getCircadianColors(int hour, int minute) {
            float timeOfDay = hour + (minute / 60.0f);
            CircadianColors c = new CircadianColors();

            // 4 Paletas clave del ciclo circadiano continuo:
            // Amanecer (06:00): Dorado cálido rosáceo
            // Mediodía (12:00): Blanco solar puro cenit
            // Atardecer (18:00): Ámbar y fuego crepuscular
            // Noche (00:00): Luz de luna plateada y azul profundo
            if (timeOfDay >= 6.0f && timeOfDay < 12.0f) {
                float frac = (timeOfDay - 6.0f) / 6.0f;
                c.rgbCenter = lerpColor(0xFFF3E0, 0xFFFFFF, frac);
                c.rgbMid = lerpColor(0xFFB74D, 0xFFF8E1, frac);
                c.rgbEdge = lerpColor(0xE65100, 0xFFD54F, frac);
                c.rgbGlitter = lerpColor(0xFFE082, 0xFFFFFF, frac);
            } else if (timeOfDay >= 12.0f && timeOfDay < 18.0f) {
                float frac = (timeOfDay - 12.0f) / 6.0f;
                c.rgbCenter = lerpColor(0xFFFFFF, 0xFFE082, frac);
                c.rgbMid = lerpColor(0xFFF8E1, 0xFF9800, frac);
                c.rgbEdge = lerpColor(0xFFD54F, 0xDD2C00, frac);
                c.rgbGlitter = lerpColor(0xFFFFFF, 0xFFCC80, frac);
            } else if (timeOfDay >= 18.0f && timeOfDay < 24.0f) {
                float frac = (timeOfDay - 18.0f) / 6.0f;
                c.rgbCenter = lerpColor(0xFFE082, 0xF0F8FF, frac);
                c.rgbMid = lerpColor(0xFF9800, 0x81D4FA, frac);
                c.rgbEdge = lerpColor(0xDD2C00, 0x3949AB, frac);
                c.rgbGlitter = lerpColor(0xFFCC80, 0xB3E5FC, frac);
            } else {
                float frac = timeOfDay / 6.0f;
                c.rgbCenter = lerpColor(0xF0F8FF, 0xFFF3E0, frac);
                c.rgbMid = lerpColor(0x81D4FA, 0xFFB74D, frac);
                c.rgbEdge = lerpColor(0x3949AB, 0xE65100, frac);
                c.rgbGlitter = lerpColor(0xB3E5FC, 0xFFE082, frac);
            }
            return c;
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
            float effectiveLightAngle = mLightAngle;
            if (mLightSolarTracking) {
                effectiveLightAngle = calculateSolarTrackingAngle();
            }
            mJellyMesh.setLightSource(mLightSourceEnabled, effectiveLightAngle, mLightIntensity / 100.0f);
        }

        private void updateVisualizer() {
            if (!mJellyEnabled || !mMusicReactiveEnabled || !mIsVisible) {
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
            boolean newJellyEnabled = (enabledVal == 1);
            boolean needsReload = (mJellyEnabled != newJellyEnabled);
            mJellyEnabled = newJellyEnabled;

            if (!mJellyEnabled) {
                mGyroEnabled = false;
                mGyroInertiaEnabled = false;
                mLightSourceEnabled = false;
                mMusicReactiveEnabled = false;
                mDepthEffectEnabled = false;
                mChargingWaveEnabled = false;
                mSnapBackEnabled = false;
                mTapShockwaveEnabled = false;
                mJellyRippleEnabled = false;

                mIsLoopRunning = false;
                try {
                    Choreographer.getInstance().removeFrameCallback(this);
                } catch (Exception ignored) {}
                mLastFrameTimeNanos = 0;

                synchronized (mSurfaceLock) {
                    mJellyMesh.reset();
                    if (mForegroundBitmap != null) {
                        mForegroundBitmap.recycle();
                        mForegroundBitmap = null;
                    }
                }

                updateSensorRegistration();
                updateVisualizer();

                if (needsReload) {
                    mDrawn = false;
                    mLongExecutor.execute(this::loadWallpaperAndDrawFrameInternal);
                } else {
                    mDrawn = false;
                    drawFrame();
                }
                Log.i(TAG, "updateJellySetting: Jelly wallpaper disabled. Restored to clean static wallpaper.");
                return;
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
            mLightCustomColor = Settings.System.getString(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_CUSTOM_COLOR);
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
            mLightWaterWaves = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_WATER_WAVES, 0) == 1;
            mLightWaterWavesIntensity = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_LIGHT_WATER_WAVES_INTENSITY, 75);
            mWaterPreset = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_WATER_PRESET, 1);
            mWaterWaveSize = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_WATER_WAVE_SIZE, 50);
            mWaterWaveSpeed = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_WATER_WAVE_SPEED, 60);
            mWaterGlitterDensity = Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_WATER_GLITTER_DENSITY, 70);
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

            boolean newGyro = (Settings.System.getInt(
                    getDisplayContext().getContentResolver(),
                    KEY_JELLY_GYRO, 0) == 1);

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
            mDrawn = false;
            startAnimationLoopIfNeeded();
            if (needsReload) {
                mLongExecutor.execute(this::loadWallpaperAndDrawFrameInternal);
            } else {
                if (mBitmap != null && !mBitmap.isRecycled()) {
                    synchronized (mSurfaceLock) {
                        drawActiveFrameOnCanvas();
                    }
                }
                drawFrame();
            }
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras,
                boolean resultRequested) {
            if (!mJellyEnabled) {
                return super.onCommand(action, x, y, z, extras, resultRequested);
            }
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
            if (!mJellyEnabled) return;
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
            if (!mIsLoopRunning || !mJellyEnabled || !mIsVisible) {
                mIsLoopRunning = false;
                return;
            }

            float dt = (mLastFrameTimeNanos > 0)
                    ? (frameTimeNanos - mLastFrameTimeNanos) / 1_000_000_000.0f
                    : 0.016f;
            mLastFrameTimeNanos = frameTimeNanos;

            boolean stillActive = false;
            synchronized (mSurfaceLock) {
                stillActive = mJellyMesh.stepPhysics(dt);

                if (mLightSourceEnabled && isCurrentTargetActive()) {
                    if (mLightShimmerSpeed > 0 || mLightMode == 7 || (mLightWaterWaves && mLightWaterWavesIntensity > 0)) {
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
            if (!mJellyEnabled || mSurfaceHolder == null || mBitmap == null || mBitmap.isRecycled()) return;
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

                    if (mLightSourceEnabled && mLightWaterWaves && isCurrentTargetActive() && mLightWaterWavesIntensity > 0) {
                        float wavesMotion = Math.max(0.0f, Math.min(1.0f, mLightWaterWavesIntensity / 100.0f));
                        float baseWaveAmp = Math.min(mBitmap.getWidth(), mBitmap.getHeight()) * 0.018f * wavesMotion;
                        if (baseWaveAmp > 0.05f) {
                            System.arraycopy(verts, 0, mWaterVerts, 0, verts.length);
                            System.arraycopy(colors, 0, mWaterColors, 0, colors.length);

                            float waveScale = 0.35f + 1.65f * (Math.max(10, Math.min(100, mWaterWaveSize)) / 100.0f);
                            float waveSpeedMult = 0.20f + 1.80f * (Math.max(10, Math.min(100, mWaterWaveSpeed)) / 100.0f);

                            // Adapt physical parameters according to active water effect preset
                            if (mWaterPreset == 2) { // Deep ocean swell
                                baseWaveAmp *= 1.40f;
                                waveScale *= 1.35f;
                            } else if (mWaterPreset == 0) { // Calm lake ripples
                                baseWaveAmp *= 0.45f;
                                waveScale *= 0.60f;
                            } else if (mWaterPreset == 3) { // Crystal pool caustics
                                baseWaveAmp *= 0.70f;
                                waveScale *= 0.85f;
                            } else if (mWaterPreset == 4) { // Golden sunset shoreline
                                baseWaveAmp *= 1.15f;
                            } else if (mWaterPreset == 5) { // Underwater god rays
                                baseWaveAmp *= 0.65f;
                            } else if (mWaterPreset == 7) { // Splash droplets
                                baseWaveAmp *= 1.25f;
                            }

                            float t = mLightShimmerPhase * waveSpeedMult;

                            double radAngle = Math.toRadians(mLightDynamicAngle + 180f);
                            float wDirX = (float) Math.cos(radAngle);
                            float wDirY = (float) Math.sin(radAngle);
                            float minDim = Math.min(mBitmap.getWidth(), mBitmap.getHeight());

                            // Gerstner Wave Parameters (4 Octaves: Primary Swell, Secondary, Cross, Capillary)
                            float k1 = (float) (2.0 * Math.PI / (minDim * 0.50f * waveScale));
                            float k2 = (float) (2.0 * Math.PI / (minDim * 0.32f * waveScale));
                            float k3 = (float) (2.0 * Math.PI / (minDim * 0.18f * waveScale));
                            float k4 = (float) (2.0 * Math.PI / (minDim * 0.09f * waveScale));

                            float dir1X = wDirX, dir1Y = wDirY;
                            float dir2X = (float) Math.cos(radAngle + 0.52), dir2Y = (float) Math.sin(radAngle + 0.52);
                            float dir3X = (float) Math.cos(radAngle - 0.44), dir3Y = (float) Math.sin(radAngle - 0.44);
                            float dir4X = (float) Math.cos(radAngle + 1.15), dir4Y = (float) Math.sin(radAngle + 1.15);

                            float a1 = baseWaveAmp * 0.50f;
                            float a2 = baseWaveAmp * 0.28f;
                            float a3 = baseWaveAmp * 0.15f;
                            float a4 = baseWaveAmp * 0.07f;

                            float q1 = 0.45f, q2 = 0.40f, q3 = 0.35f, q4 = 0.25f;

                            // Light source position for specular shading
                            float cx = mBitmap.getWidth() * 0.5f;
                            float cy = mBitmap.getHeight() * 0.5f;
                            float lRad = (float) Math.toRadians(mLightDynamicAngle);
                            float lightPosX = cx + (float) Math.cos(lRad) * (mBitmap.getWidth() * 0.48f);
                            float lightPosY = cy + (float) Math.sin(lRad) * (mBitmap.getHeight() * 0.48f);

                            int totalVerts = (JellyMesh.COLS + 1) * (JellyMesh.ROWS + 1);
                            for (int vi = 0; vi < totalVerts; vi++) {
                                float vx = verts[vi * 2];
                                float vy = verts[vi * 2 + 1];

                                float p1 = (vx * dir1X + vy * dir1Y) * k1 - t * 2.0f;
                                float p2 = (vx * dir2X + vy * dir2Y) * k2 - t * 2.8f;
                                float p3 = (vx * dir3X + vy * dir3Y) * k3 - t * 3.6f;
                                float p4 = (vx * dir4X + vy * dir4Y) * k4 - t * 4.8f;

                                float s1 = (float) Math.sin(p1), c1 = (float) Math.cos(p1);
                                float s2 = (float) Math.sin(p2), c2 = (float) Math.cos(p2);
                                float s3 = (float) Math.sin(p3), c3 = (float) Math.cos(p3);
                                float s4 = (float) Math.sin(p4), c4 = (float) Math.cos(p4);

                                // Trochoidal Gerstner Displacement
                                float dx = -(q1 * dir1X * a1 * s1 + q2 * dir2X * a2 * s2 + q3 * dir3X * a3 * s3 + q4 * dir4X * a4 * s4);
                                float dy = -(q1 * dir1Y * a1 * s1 + q2 * dir2Y * a2 * s2 + q3 * dir3Y * a3 * s3 + q4 * dir4Y * a4 * s4);

                                mWaterVerts[vi * 2] = vx + dx;
                                mWaterVerts[vi * 2 + 1] = vy + dy;

                                // Surface Normal derivatives
                                float nx = -(dir1X * k1 * a1 * s1 + dir2X * k2 * a2 * s2 + dir3X * k3 * a3 * s3 + dir4X * k4 * a4 * s4);
                                float ny = -(dir1Y * k1 * a1 * s1 + dir2Y * k2 * a2 * s2 + dir3Y * k3 * a3 * s3 + dir4Y * k4 * a4 * s4);
                                float nz = 1.0f - (q1 * k1 * a1 * c1 + q2 * k2 * a2 * c2 + q3 * k3 * a3 * c3 + q4 * k4 * a4 * c4);
                                float invN = 1.0f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                                nx *= invN; ny *= invN; nz *= invN;

                                // Light vector
                                float lx = lightPosX - vx;
                                float ly = lightPosY - vy;
                                float lz = 320.0f;
                                float invL = 1.0f / (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                                lx *= invL; ly *= invL; lz *= invL;

                                // Half vector (View towards camera = 0, 0, 1)
                                float hx = lx;
                                float hy = ly;
                                float hz = lz + 1.0f;
                                float invH = 1.0f / (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
                                hx *= invH; hy *= invH; hz *= invH;

                                float dotNL = Math.max(0.0f, nx * lx + ny * ly + nz * lz);
                                float dotNH = Math.max(0.0f, nx * hx + ny * hy + nz * hz);
                                float waterSpecular = (float) Math.pow(dotNH, 24.0) * (65.0f * (mLightIntensity / 100.0f) * wavesMotion);
                                float diffuseShade = 0.88f + 0.22f * dotNL;

                                if (mWaterPreset == 2) { // Deep ocean trough shadowing
                                    diffuseShade = 0.72f + 0.38f * dotNL;
                                }

                                int origCol = colors[vi];
                                int rBase = (origCol >> 16) & 0xFF;
                                int gBase = (origCol >> 8) & 0xFF;
                                int bBase = origCol & 0xFF;

                                int rFinal = Math.min(255, Math.max(0, (int) (rBase * diffuseShade + waterSpecular * 0.9f)));
                                int gFinal = Math.min(255, Math.max(0, (int) (gBase * diffuseShade + waterSpecular * 0.95f)));
                                int bFinal = Math.min(255, Math.max(0, (int) (bBase * diffuseShade + waterSpecular * 1.0f)));

                                if (mWaterPreset == 2) { // Deep oceanic blue tint
                                    bFinal = Math.min(255, (int) (bFinal * 1.08f + 12));
                                } else if (mWaterPreset == 3) { // Crystal pool turquoise tint
                                    gFinal = Math.min(255, (int) (gFinal * 1.05f + 8));
                                    bFinal = Math.min(255, (int) (bFinal * 1.10f + 14));
                                } else if (mWaterPreset == 4) { // Golden sunset shoreline amber tint
                                    rFinal = Math.min(255, (int) (rFinal * 1.12f + 14));
                                    gFinal = Math.min(255, (int) (gFinal * 1.04f + 4));
                                } else if (mWaterPreset == 5) { // Underwater god rays absorption
                                    rFinal = (int) (rFinal * 0.85f);
                                    bFinal = Math.min(255, (int) (bFinal * 1.12f + 10));
                                }

                                mWaterColors[vi] = (origCol & 0xFF000000) | (rFinal << 16) | (gFinal << 8) | bFinal;
                            }
                            verts = mWaterVerts;
                            colors = mWaterColors;
                        }
                    }

                    canvas.drawBitmapMesh(mBitmap, JellyMesh.COLS, JellyMesh.ROWS, verts, 0, colors, 0, null);

                    if (mDepthEffectEnabled && mForegroundBitmap != null && !mForegroundBitmap.isRecycled()) {
                        float[] fgVerts = mJellyMesh.getForegroundVertices();
                        float sepFactor = mDepthSeparation / 100.0f;

                        // Ángulo de la fuente de luz dinámica o virtual (por defecto 55 grados)
                        float lightAngle = mLightSourceEnabled ? mLightDynamicAngle : 55.0f;
                        float shadowAngleRad = (float) Math.toRadians(lightAngle + 180.0f);
                        float cosA = (float) Math.cos(shadowAngleRad);
                        float sinA = (float) Math.sin(shadowAngleRad);

                        // Proyección direccional de sombras con paralaje giroscópico y separación de profundidad
                        float penumbraDist = 18.0f + 32.0f * sepFactor;
                        float penumbraOffsetX = cosA * penumbraDist - (mSmoothRoll * 40.0f * sepFactor);
                        float penumbraOffsetY = sinA * penumbraDist + (mSmoothPitch * 40.0f * sepFactor);

                        float contactDist = 6.0f + 10.0f * sepFactor;
                        float contactOffsetX = cosA * contactDist - (mSmoothRoll * 14.0f * sepFactor);
                        float contactOffsetY = sinA * contactDist + (mSmoothPitch * 14.0f * sepFactor);

                        int numVertCoords = (JellyMesh.COLS + 1) * (JellyMesh.ROWS + 1) * 2;
                        for (int k = 0; k < numVertCoords; k += 2) {
                            // Penumbra: sombra ambiental difuminada con mayor desplazamiento proyectada sobre el fondo
                            mShadowVerts[k] = fgVerts[k] * 0.40f + verts[k] * 0.60f + penumbraOffsetX;
                            mShadowVerts[k + 1] = fgVerts[k + 1] * 0.40f + verts[k + 1] * 0.60f + penumbraOffsetY;

                            // Contact Shadow (Umbra): sombra de oclusión cercana y oscura que ancla físicamente el sujeto
                            mContactShadowVerts[k] = fgVerts[k] * 0.75f + verts[k] * 0.25f + contactOffsetX;
                            mContactShadowVerts[k + 1] = fgVerts[k + 1] * 0.75f + verts[k + 1] * 0.25f + contactOffsetY;
                        }

                        // Capa 1: Sombra penumbra difuminada proyectada en profundidad
                        canvas.drawBitmapMesh(mForegroundBitmap, JellyMesh.COLS, JellyMesh.ROWS, mShadowVerts, 0, null, 0, mShadowPaint);

                        // Capa 2: Sombra de contacto cercana para definición y anclaje físico 3D
                        canvas.drawBitmapMesh(mForegroundBitmap, JellyMesh.COLS, JellyMesh.ROWS, mContactShadowVerts, 0, null, 0, mContactShadowPaint);

                        // Capa 3: Sujeto en primer plano renderizado con paralaje flotante
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

        private final Paint mLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mEdgeSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mSpecularPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mGlitterPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mCausticPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mFoamPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mGodRayPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mBokehPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Paint mSplashPaint = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setXfermode(XFERMODE_SCREEN); }};
        private final Path mEffectPath = new Path();

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
                baseAngle = calculateSolarTrackingAngle();
            }

            if (mLightGyroParallax) {
                baseAngle += (mSmoothRoll * 30.0f);
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

            float spreadProgress = Math.max(0.0f, Math.min(1.0f, mLightBeamSpread / 100.0f));
            float spreadFactor = 0.15f + 2.85f * spreadProgress;
            float hardnessFactor = 0.05f + 0.90f * (mLightHardness / 100.0f);

            // Preset color palettes
            int rgbCenter = 0xFFFFFF;
            int rgbMid = 0xFFFFFF;
            int rgbEdge = 0xFFFFFF;
            int rgbGlitter = 0xFFFFFF;

            if (mLightSolarTracking) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                CircadianColors circ = getCircadianColors(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
                rgbCenter = circ.rgbCenter;
                rgbMid = circ.rgbMid;
                rgbEdge = circ.rgbEdge;
                rgbGlitter = circ.rgbGlitter;
            } else if (mLightMode == 2) {
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
                mHsvBuffer[0] = huePhase; mHsvBuffer[1] = 0.75f; mHsvBuffer[2] = 1.0f;
                int c1 = Color.HSVToColor(mHsvBuffer) & 0x00FFFFFF;
                mHsvBuffer[0] = (huePhase + 60.0f) % 360.0f; mHsvBuffer[1] = 0.65f; mHsvBuffer[2] = 1.0f;
                int c2 = Color.HSVToColor(mHsvBuffer) & 0x00FFFFFF;
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

            if (!mLightSolarTracking && mLightCustomColor != null && !mLightCustomColor.trim().isEmpty()) {
                try {
                    String hex = mLightCustomColor.trim();
                    if (!hex.startsWith("#")) {
                        hex = "#" + hex;
                    }
                    int customColor = Color.parseColor(hex);
                    int r = Color.red(customColor);
                    int g = Color.green(customColor);
                    int b = Color.blue(customColor);

                    int rCenter = Math.min(255, (int) (r * 0.35f + 255 * 0.65f));
                    int gCenter = Math.min(255, (int) (g * 0.35f + 255 * 0.65f));
                    int bCenter = Math.min(255, (int) (b * 0.35f + 255 * 0.65f));
                    rgbCenter = (rCenter << 16) | (gCenter << 8) | bCenter;
                    rgbMid = (r << 16) | (g << 8) | b;
                    int rEdge = (int) (r * 0.70f);
                    int gEdge = (int) (g * 0.70f);
                    int bEdge = (int) (b * 0.70f);
                    rgbEdge = (rEdge << 16) | (gEdge << 8) | bEdge;
                    int rGlitter = Math.min(255, (int) (r * 0.65f + 255 * 0.35f));
                    int gGlitter = Math.min(255, (int) (g * 0.65f + 255 * 0.35f));
                    int bGlitter = Math.min(255, (int) (b * 0.65f + 255 * 0.35f));
                    rgbGlitter = (rGlitter << 16) | (gGlitter << 8) | bGlitter;
                } catch (Exception ignored) {
                }
            }

            // 1. Halo Solar / Destello Radial Suave y Natural (Seamless Multi-Stop Radial Flare)
            float spotRadius = Math.max(w, h) * (0.35f + 1.15f * spreadProgress);
            int alphaCenter = (int) (180 * intensity);
            int alphaBloom = (int) (85 * intensity * (1.0f - hardnessFactor * 0.35f));
            int alphaHalo = (int) (25 * intensity * (1.0f - hardnessFactor * 0.5f));

            mSpotGradColors[0] = (alphaCenter << 24) | rgbCenter;
            mSpotGradColors[1] = (alphaBloom << 24) | rgbMid;
            mSpotGradColors[2] = (alphaHalo << 24) | rgbEdge;
            mSpotGradColors[3] = 0x00000000;

            RadialGradient spotGradient = new RadialGradient(
                    lightX, lightY, spotRadius,
                    mSpotGradColors,
                    SPOT_GRAD_POS,
                    Shader.TileMode.CLAMP);
            mLightPaint.setShader(spotGradient);
            canvas.drawRect(0, 0, w, h, mLightPaint);

            // 2. Haz de Luz Volumétrico Suave con Gran Apertura y Degradado Bilateral
            float targetX = cx + (mSmoothRoll * w * 0.35f);
            float targetY = cy + (mSmoothPitch * h * 0.35f);
            float dirX = targetX - lightX;
            float dirY = targetY - lightY;
            float dist = (float) Math.sqrt(dirX * dirX + dirY * dirY);
            if (dist < 1.0f) {
                dirX = (float) Math.cos(rad);
                dirY = (float) Math.sin(rad);
            } else {
                dirX /= dist;
                dirY /= dist;
            }

            float beamAngleDeg = (float) Math.toDegrees(Math.atan2(dirY, dirX));
            float maxDim = (float) Math.sqrt(w * w + h * h) * 1.8f;
            float beamHalfW = w * (0.15f + 1.45f * spreadProgress);

            int beamCoreAlpha = (int) (140 * intensity);
            int beamMidAlpha = (int) (65 * intensity * (1.0f - hardnessFactor * 0.3f));

            // Degradado lateral dinámico (Eje Y rotado): Fades suave de 0% -> Suave -> Centro -> Suave -> 0%
            mLateralGradColors[0] = 0x00000000;
            mLateralGradColors[1] = ((int)(beamMidAlpha * 0.25f) << 24) | rgbEdge;
            mLateralGradColors[2] = (beamMidAlpha << 24) | rgbMid;
            mLateralGradColors[3] = (beamCoreAlpha << 24) | rgbCenter;
            mLateralGradColors[4] = (beamMidAlpha << 24) | rgbMid;
            mLateralGradColors[5] = ((int)(beamMidAlpha * 0.25f) << 24) | rgbEdge;
            mLateralGradColors[6] = 0x00000000;

            LinearGradient lateralGrad = new LinearGradient(
                    0, -beamHalfW, 0, beamHalfW,
                    mLateralGradColors,
                    LATERAL_GRAD_POS,
                    Shader.TileMode.CLAMP);

            // Degradado longitudinal (Eje X rotado): Fades suave a lo largo del haz
            LinearGradient lengthGrad = new LinearGradient(
                    0, 0, maxDim, 0,
                    LENGTH_GRAD_COLORS,
                    LENGTH_GRAD_POS,
                    Shader.TileMode.CLAMP);

            // 2. Haz de Luz Volumétrico Suave con Gran Apertura y Degradado Bilateral
            if (mLightSpecularColumn) {
                ComposeShader beamShader = new ComposeShader(lateralGrad, lengthGrad, XFERMODE_MULTIPLY);
                mSpecularPaint.setShader(beamShader);

                canvas.save();
                canvas.translate(lightX, lightY);
                canvas.rotate(beamAngleDeg);
                canvas.drawRect(0, -beamHalfW, maxDim, beamHalfW, mSpecularPaint);
                canvas.restore();
            }

            // 3. Reflejo Sutil en Borde de Cristal (Subtle Soft Rim Sheen)
            float startX = cx - cosA * (w * 0.5f);
            float startY = cy - sinA * (h * 0.5f);
            float borderEndX = cx + cosA * (w * 0.5f);
            float borderEndY = cy + sinA * (h * 0.5f);

            int alphaSheen = (int) (70 * intensity);
            int sheenColor = (alphaSheen << 24) | rgbEdge;
            mEdgeGradColors[0] = 0x00000000;
            mEdgeGradColors[1] = 0x00000000;
            mEdgeGradColors[2] = sheenColor;

            LinearGradient edgeGradient = new LinearGradient(
                    startX, startY, borderEndX, borderEndY,
                    mEdgeGradColors,
                    EDGE_GRAD_POS,
                    Shader.TileMode.CLAMP);
            mEdgeSheenPaint.setShader(edgeGradient);
            canvas.drawRect(0, 0, w, h, mEdgeSheenPaint);

            // 4. Destellos Solares Oceánicos y Efectos Especiales de Agua según Preset
            if (mLightWaterWaves) {
                mGlitterPaint.setStyle(Paint.Style.FILL);
                float waveSpeedMult = 0.20f + 1.80f * (Math.max(10, Math.min(100, mWaterWaveSpeed)) / 100.0f);
                float shimmerTime = mLightShimmerPhase * waveSpeedMult;
                float wavesMotion = Math.max(0.0f, Math.min(1.0f, mLightWaterWavesIntensity / 100.0f));
                float glitterFactor = Math.max(0.0f, Math.min(1.0f, mWaterGlitterDensity / 100.0f));
                float beamLength = maxDim * 0.75f;
                float perpX = -dirY;
                float perpY = dirX;

                // 4a. Efectos avanzados de agua según el preset seleccionado
                if (mWaterPreset == 3) {
                    drawPoolCaustics(canvas, w, h, shimmerTime, intensity, wavesMotion, rgbCenter, rgbEdge);
                } else if (mWaterPreset == 4) {
                    drawGoldenSunsetShorelineAndFoam(canvas, w, h, shimmerTime, intensity, wavesMotion, lightX, lightY, dirX, dirY);
                } else if (mWaterPreset == 5) {
                    drawUnderwaterGodRays(canvas, w, h, shimmerTime, intensity, wavesMotion, lightX, lightY);
                } else if (mWaterPreset == 6) {
                    drawDiamondGlitterAndBokeh(canvas, w, h, shimmerTime, intensity, wavesMotion, lightX, lightY, dirX, dirY, beamHalfW, rgbCenter, rgbEdge);
                } else if (mWaterPreset == 7) {
                    drawSplashDropletsAndSpray(canvas, w, h, shimmerTime, intensity, wavesMotion, lightX, lightY, dirX, dirY);
                }

                // 4b. Campo de destellos estándar para presets 0, 1, 2, 4, 7, 8
                if (mWaterPreset != 3 && mWaterPreset != 5 && mWaterPreset != 6) {
                    int numSparkles = (int) (12 + 52 * glitterFactor);
                    for (int s = 0; s < numSparkles; s++) {
                        float sFrac = (s + 0.5f) / (float) numSparkles;
                        float sparkX = lightX + dirX * (beamLength * sFrac);
                        float sparkY = lightY + dirY * (beamLength * sFrac);
                        float latPhase = shimmerTime * 1.7f + s * 2.3f;
                        float lateralOffset = (float) (Math.sin(latPhase) * 0.75f + Math.sin(latPhase * 1.8f) * 0.25f)
                                * (beamHalfW * 0.55f * sFrac) * (0.3f + 0.7f * wavesMotion);
                        sparkX += perpX * lateralOffset;
                        sparkY += perpY * lateralOffset;

                        // Scintillation de faceta de ola
                        float twinkle = (float) Math.sin(shimmerTime * 3.6f + s * 2.8f);
                        if (twinkle < 0.15f) continue;
                        float blink = (float) Math.pow((twinkle - 0.15f) / 0.85f, 2.2);

                        int sparkAlpha = (int) (190 * intensity * blink * (0.25f + 0.75f * wavesMotion) * (0.3f + 0.7f * glitterFactor));
                        if (sparkAlpha > 6) {
                            float sparkRadius = (2.2f + 4.0f * blink) * (0.6f + 0.4f * hardnessFactor) * (0.7f + 0.3f * wavesMotion) * (0.5f + 0.5f * glitterFactor);

                            // 1. Halo difuso del destello (Bloom halo)
                            int bloomAlpha = sparkAlpha / 3;
                            mGlitterPaint.setColor((bloomAlpha << 24) | rgbEdge);
                            canvas.drawCircle(sparkX, sparkY, sparkRadius * 2.4f, mGlitterPaint);

                            // 2. Destello en cruz / estrella de 4 puntas (Sun Star Glint)
                            if (blink > 0.50f) {
                                mGlitterPaint.setColor(((int)(sparkAlpha * 0.75f) << 24) | rgbCenter);
                                float flareLen = sparkRadius * 3.5f;
                                canvas.drawLine(sparkX - flareLen, sparkY, sparkX + flareLen, sparkY, mGlitterPaint);
                                canvas.drawLine(sparkX, sparkY - flareLen, sparkX, sparkY + flareLen, mGlitterPaint);
                            }

                            // 3. Núcleo brillante ultra-intenso (Specular Hotspot Core)
                            mGlitterPaint.setColor((sparkAlpha << 24) | rgbCenter);
                            canvas.drawCircle(sparkX, sparkY, sparkRadius, mGlitterPaint);
                        }
                    }
                }
            }
        }

        /**
         * Preset 3: Cáusticas Submarinas de Piscina (Pool Caustics Web)
         * Simula la red orgánica de refracción solar en agua cristalina turquesa.
         */
        private void drawPoolCaustics(Canvas canvas, int w, int h, float shimmerTime, float intensity, float wavesMotion, int rgbCenter, int rgbEdge) {
            mCausticPaint.setStyle(Paint.Style.STROKE);

            int causticTurquoise = 0x5500E5FF;
            int causticBright = 0x9980FFFF;

            float scale = Math.min(w, h) * 0.12f;
            int gridX = 9;
            int gridY = 16;
            float stepX = (float) w / (gridX - 1);
            float stepY = (float) h / (gridY - 1);

            for (int gy = 0; gy < gridY; gy++) {
                mEffectPath.reset();
                boolean first = true;
                for (int gx = 0; gx < gridX; gx++) {
                    float px = gx * stepX;
                    float py = gy * stepY;

                    float phase1 = (px * 0.012f + py * 0.009f) + shimmerTime * 1.8f;
                    float phase2 = (px * 0.008f - py * 0.014f) - shimmerTime * 1.4f;
                    float phase3 = (px * 0.015f + py * 0.015f) + shimmerTime * 2.2f;

                    float dx = (float) (Math.sin(phase1) * 0.55 + Math.cos(phase2) * 0.45) * scale * 0.6f;
                    float dy = (float) (Math.cos(phase1) * 0.45 + Math.sin(phase3) * 0.55) * scale * 0.6f;

                    float cx = px + dx;
                    float cy = py + dy;

                    if (first) {
                        mEffectPath.moveTo(cx, cy);
                        first = false;
                    } else {
                        mEffectPath.lineTo(cx, cy);
                    }
                }
                mCausticPaint.setStrokeWidth(3.0f * wavesMotion);
                mCausticPaint.setColor(causticTurquoise);
                canvas.drawPath(mEffectPath, mCausticPaint);
            }

            for (int gx = 0; gx < gridX; gx++) {
                mEffectPath.reset();
                boolean first = true;
                for (int gy = 0; gy < gridY; gy++) {
                    float px = gx * stepX;
                    float py = gy * stepY;

                    float phase1 = (px * 0.012f + py * 0.009f) + shimmerTime * 1.8f;
                    float phase2 = (px * 0.008f - py * 0.014f) - shimmerTime * 1.4f;
                    float phase3 = (px * 0.015f + py * 0.015f) + shimmerTime * 2.2f;

                    float dx = (float) (Math.sin(phase1) * 0.55 + Math.cos(phase2) * 0.45) * scale * 0.6f;
                    float dy = (float) (Math.cos(phase1) * 0.45 + Math.sin(phase3) * 0.55) * scale * 0.6f;

                    float cx = px + dx;
                    float cy = py + dy;

                    if (first) {
                        mEffectPath.moveTo(cx, cy);
                        first = false;
                    } else {
                        mEffectPath.lineTo(cx, cy);
                    }
                }
                mCausticPaint.setStrokeWidth(2.0f * wavesMotion);
                mCausticPaint.setColor(causticBright);
                canvas.drawPath(mEffectPath, mCausticPaint);
            }
        }

        /**
         * Preset 4: Marea Dorada en Orilla con Espuma (Golden Sunset Shoreline & Foam)
         * Simula oleaje rítmico en la playa al atardecer con filamentos de espuma en las crestas.
         */
        private void drawGoldenSunsetShorelineAndFoam(Canvas canvas, int w, int h, float shimmerTime, float intensity, float wavesMotion, float lightX, float lightY, float dirX, float dirY) {
            // 1. Resplandor dorado longitudinal
            LinearGradient sunsetGlow = new LinearGradient(
                    lightX, lightY, lightX + dirX * h * 0.85f, lightY + dirY * h * 0.85f,
                    SUNSET_GRAD_COLORS,
                    SUNSET_GRAD_POS,
                    Shader.TileMode.CLAMP);
            mFoamPaint.setStyle(Paint.Style.FILL);
            mFoamPaint.setShader(sunsetGlow);
            canvas.drawRect(0, 0, w, h, mFoamPaint);
            mFoamPaint.setShader(null);

            // 2. Filamentos de espuma sobre crestas de olas que rompen
            mFoamPaint.setStyle(Paint.Style.STROKE);
            float perpX = -dirY;
            float perpY = dirX;
            int numWaves = 4;
            float maxDist = (float) Math.sqrt(w * w + h * h) * 0.85f;

            for (int i = 0; i < numWaves; i++) {
                float wavePhase = (shimmerTime * 0.40f + i / (float) numWaves) % 1.0f;
                float waveDist = wavePhase * maxDist;

                float foamFade = (float) Math.sin(wavePhase * Math.PI);
                int foamAlpha = (int) (180 * foamFade * intensity * wavesMotion);
                if (foamAlpha < 10) continue;

                mEffectPath.reset();
                int steps = 18;
                boolean first = true;
                for (int s = 0; s <= steps; s++) {
                    float sFrac = (s / (float) steps) - 0.5f;
                    float lateral = sFrac * maxDist * 1.3f;

                    float wobble = (float) (Math.sin(sFrac * 8.0f + shimmerTime * 2.0f + i) * 28.0f
                                          + Math.sin(sFrac * 16.0f - shimmerTime * 1.5f) * 12.0f);

                    float px = lightX + dirX * (waveDist + wobble) + perpX * lateral;
                    float py = lightY + dirY * (waveDist + wobble) + perpY * lateral;

                    if (first) {
                        mEffectPath.moveTo(px, py);
                        first = false;
                    } else {
                        mEffectPath.lineTo(px, py);
                    }
                }

                // Espuma difusa exterior
                mFoamPaint.setColor((foamAlpha / 2 << 24) | 0xFFF8E7);
                mFoamPaint.setStrokeWidth(9.0f * (0.6f + 0.4f * foamFade));
                canvas.drawPath(mEffectPath, mFoamPaint);

                // Cresta brillante interior
                mFoamPaint.setColor((foamAlpha << 24) | 0xFFFFFF);
                mFoamPaint.setStrokeWidth(3.2f);
                canvas.drawPath(mEffectPath, mFoamPaint);
            }
        }

        /**
         * Preset 5: Rayos Crepusculares Submarinos (Underwater God Rays)
         * Simula haces volumétricos de luz solar que penetran el agua profunda con oscilación angular.
         */
        private void drawUnderwaterGodRays(Canvas canvas, int w, int h, float shimmerTime, float intensity, float wavesMotion, float lightX, float lightY) {
            mGodRayPaint.setStyle(Paint.Style.FILL);

            int numRays = 7;
            float topY = 0f;
            float maxDepth = h * 1.1f;

            for (int r = 0; r < numRays; r++) {
                float rFrac = (r + 0.5f) / (float) numRays;
                float originX = w * (0.2f + 0.6f * rFrac);

                float angleOffset = (float) (Math.sin(shimmerTime * 1.1f + r * 1.7f) * 0.14f);
                float beamBaseWidth = w * 0.06f;
                float beamSpreadWidth = w * (0.22f + 0.08f * (float) Math.sin(r * 2.3f));

                float rayPulse = (float) (0.50f + 0.50f * Math.sin(shimmerTime * 1.6f + r * 1.9f));
                int rayAlpha = (int) (125 * intensity * rayPulse * wavesMotion);
                if (rayAlpha < 8) continue;

                float botCenterX = originX + (float) Math.tan(angleOffset) * maxDepth + (rFrac - 0.5f) * (w * 0.35f);

                mEffectPath.reset();
                mEffectPath.moveTo(originX - beamBaseWidth * 0.5f, topY);
                mEffectPath.lineTo(originX + beamBaseWidth * 0.5f, topY);
                mEffectPath.lineTo(botCenterX + beamSpreadWidth * 0.5f, maxDepth);
                mEffectPath.lineTo(botCenterX - beamSpreadWidth * 0.5f, maxDepth);
                mEffectPath.close();

                mRayGradColors[0] = (rayAlpha << 24) | 0x80DEEA;
                mRayGradColors[1] = ((int) (rayAlpha * 0.45f) << 24) | 0x00838F;
                mRayGradColors[2] = 0x00002040;

                LinearGradient rayGrad = new LinearGradient(
                        originX, topY, botCenterX, maxDepth,
                        mRayGradColors,
                        RAY_GRAD_POS,
                        Shader.TileMode.CLAMP);
                mGodRayPaint.setShader(rayGrad);
                canvas.drawPath(mEffectPath, mGodRayPaint);
            }
            mGodRayPaint.setShader(null);

            // Partículas de polvo marino flotantes en suspensión
            int numMotes = 22;
            for (int m = 0; m < numMotes; m++) {
                float mx = ((m * 173.3f + shimmerTime * 15.0f) % w);
                float my = ((m * 239.7f - shimmerTime * 22.0f) % h);
                if (my < 0) my += h;
                float twinkle = (float) (0.5f + 0.5f * Math.sin(shimmerTime * 2.4f + m * 3.1f));
                int moteAlpha = (int) (140 * twinkle * intensity);
                mGodRayPaint.setColor((moteAlpha << 24) | 0xB2EBF2);
                canvas.drawCircle(mx, my, 2.2f + twinkle * 1.8f, mGodRayPaint);
            }
        }

        /**
         * Preset 6: Centelleo Diamante y Bokeh Solar (Diamond Sun Glitter & Bokeh)
         * Simula destellos fulgurantes en forma de estrellas de 8 puntas con discos bokeh desenfocados.
         */
        private void drawDiamondGlitterAndBokeh(Canvas canvas, int w, int h, float shimmerTime, float intensity, float wavesMotion, float lightX, float lightY, float dirX, float dirY, float beamHalfW, int rgbCenter, int rgbEdge) {
            float perpX = -dirY;
            float perpY = dirX;
            float maxDim = (float) Math.sqrt(w * w + h * h);
            float beamLength = maxDim * 0.85f;

            // 1. Discos de bokeh luminoso desenfocados
            mBokehPaint.setStyle(Paint.Style.FILL);
            int numBokeh = 28;
            for (int b = 0; b < numBokeh; b++) {
                float bFrac = (b + 0.5f) / (float) numBokeh;
                float baseX = lightX + dirX * (beamLength * bFrac);
                float baseY = lightY + dirY * (beamLength * bFrac);

                float latPhase = shimmerTime * 1.3f + b * 2.1f;
                float lateral = (float) Math.sin(latPhase) * (beamHalfW * 0.75f * bFrac);
                float bx = baseX + perpX * lateral;
                float by = baseY + perpY * lateral;

                float pulse = (float) Math.sin(shimmerTime * 3.2f + b * 2.7f);
                if (pulse < 0.2f) continue;
                float bStrength = (pulse - 0.2f) / 0.8f;

                float radius = (12.0f + 26.0f * (float) Math.sin(b * 1.7f + 1.0f)) * (0.6f + 0.4f * bStrength);
                int alphaBokeh = (int) (95 * intensity * bStrength * wavesMotion);
                if (alphaBokeh < 6) continue;

                mBokehPaint.setColor((alphaBokeh / 3 << 24) | rgbEdge);
                canvas.drawCircle(bx, by, radius, mBokehPaint);

                mBokehPaint.setStyle(Paint.Style.STROKE);
                mBokehPaint.setStrokeWidth(2.2f);
                mBokehPaint.setColor((alphaBokeh << 24) | rgbCenter);
                canvas.drawCircle(bx, by, radius, mBokehPaint);
                mBokehPaint.setStyle(Paint.Style.FILL);
            }

            // 2. Destellos diamante en cruz de 8 puntas
            int numDiamondStars = 42;
            for (int s = 0; s < numDiamondStars; s++) {
                float sFrac = (s + 0.5f) / (float) numDiamondStars;
                float sx = lightX + dirX * (beamLength * sFrac);
                float sy = lightY + dirY * (beamLength * sFrac);

                float latPhase = shimmerTime * 2.1f + s * 3.7f;
                float latOffset = (float) (Math.sin(latPhase) * 0.7f + Math.sin(latPhase * 1.7f) * 0.3f)
                        * (beamHalfW * 0.60f * sFrac);
                sx += perpX * latOffset;
                sy += perpY * latOffset;

                float twinkle = (float) Math.sin(shimmerTime * 4.8f + s * 3.9f);
                if (twinkle < 0.35f) continue;
                float blink = (float) Math.pow((twinkle - 0.35f) / 0.65f, 2.5);

                int starAlpha = (int) (240 * intensity * blink * wavesMotion);
                if (starAlpha < 10) continue;

                float coreRadius = 3.5f + 4.5f * blink;
                float primarySpike = coreRadius * 4.8f;
                float diagSpike = primarySpike * 0.65f;

                mBokehPaint.setColor(((starAlpha / 2) << 24) | rgbEdge);
                canvas.drawCircle(sx, sy, coreRadius * 2.5f, mBokehPaint);

                mBokehPaint.setColor((starAlpha << 24) | rgbCenter);
                mBokehPaint.setStrokeWidth(1.8f);
                // Puntas ortogonales (+)
                canvas.drawLine(sx - primarySpike, sy, sx + primarySpike, sy, mBokehPaint);
                canvas.drawLine(sx, sy - primarySpike, sx, sy + primarySpike, mBokehPaint);
                // Puntas diagonales (X)
                canvas.drawLine(sx - diagSpike, sy - diagSpike, sx + diagSpike, sy + diagSpike, mBokehPaint);
                canvas.drawLine(sx - diagSpike, sy + diagSpike, sx + diagSpike, sy - diagSpike, mBokehPaint);

                // Núcleo brillante
                mBokehPaint.setColor((255 << 24) | 0xFFFFFF);
                canvas.drawCircle(sx, sy, coreRadius * 0.7f, mBokehPaint);
            }
        }

        /**
         * Preset 7: Salpicaduras y Gotas en Crestas (Splash Droplets & Spray)
         * Simula microgotas y salpicaduras balísticas en contraluz solar sobre las crestas.
         */
        private void drawSplashDropletsAndSpray(Canvas canvas, int w, int h, float shimmerTime, float intensity, float wavesMotion, float lightX, float lightY, float dirX, float dirY) {
            float perpX = -dirY;
            float perpY = dirX;
            int numDroplets = 36;
            float maxRange = (float) Math.sqrt(w * w + h * h) * 0.75f;

            for (int d = 0; d < numDroplets; d++) {
                float cycleSpeed = 0.85f + (d % 5) * 0.25f;
                float cycle = (shimmerTime * cycleSpeed + d * 0.38f) % 1.0f;

                float crestDist = ((d * 97.1f) % maxRange);
                float lateral = ((d * 63.7f) % (w * 0.8f)) - (w * 0.4f);
                float baseX = lightX + dirX * crestDist + perpX * lateral;
                float baseY = lightY + dirY * crestDist + perpY * lateral;

                float jumpHeight = 35.0f + (d % 7) * 14.0f;
                float arc = 4.0f * cycle * (1.0f - cycle);
                if (arc <= 0.01f) continue;

                float dropX = baseX + perpX * (cycle * 22.0f - 11.0f);
                float dropY = baseY - arc * jumpHeight;

                int dropAlpha = (int) (220 * (1.0f - cycle * 0.3f) * intensity * wavesMotion);
                if (dropAlpha < 10) continue;

                float dropRadius = 2.4f + (d % 4) * 1.2f;

                mSplashPaint.setStyle(Paint.Style.FILL);
                mSplashPaint.setColor(((dropAlpha / 2) << 24) | 0xB2EBF2);
                canvas.drawCircle(dropX, dropY, dropRadius, mSplashPaint);

                mSplashPaint.setColor((dropAlpha << 24) | 0xFFFFFF);
                canvas.drawCircle(dropX - dropRadius * 0.35f, dropY - dropRadius * 0.35f, dropRadius * 0.45f, mSplashPaint);

                if (cycle > 0.25f && cycle < 0.75f) {
                    mSplashPaint.setColor(((int) (dropAlpha * 0.6f) << 24) | 0xE0F7FA);
                    canvas.drawCircle(dropX - perpX * 4.0f, dropY + 6.0f, dropRadius * 0.5f, mSplashPaint);
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

                // 1. Muestreo espacial de bordes para fondo variable (evita falsos positivos en degradados cielo/suelo)
                int[] topBorder = new int[sampleW];
                int[] botBorder = new int[sampleW];
                for (int x = 0; x < sampleW; x++) {
                    topBorder[x] = pixels[x];
                    botBorder[x] = pixels[(sampleH - 1) * sampleW + x];
                }
                int[] leftBorder = new int[sampleH];
                int[] rightBorder = new int[sampleH];
                for (int y = 0; y < sampleH; y++) {
                    leftBorder[y] = pixels[y * sampleW];
                    rightBorder[y] = pixels[y * sampleW + sampleW - 1];
                }

                // 2. Mapa de luminancia para cálculo de gradiente de bordes estructurales (alta frecuencia)
                float[] lum = new float[sampleW * sampleH];
                for (int i = 0; i < sampleW * sampleH; i++) {
                    int c = pixels[i];
                    lum[i] = 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c);
                }

                int[] rawMask = new int[sampleW * sampleH];
                float invSampleW = 1.0f / Math.max(1, sampleW - 1);
                float invSampleH = 1.0f / Math.max(1, sampleH - 1);
                float centerX = sampleW * 0.50f;
                float centerY = sampleH * 0.52f;
                float sigmaX = sampleW * 0.44f;
                float sigmaY = sampleH * 0.46f;

                for (int y = 0; y < sampleH; y++) {
                    float v = y * invSampleH;
                    float dy = (y - centerY) / sigmaY;
                    int rowOffset = y * sampleW;
                    int cLeft = leftBorder[y];
                    int cRight = rightBorder[y];
                    float rHBase = Color.red(cLeft);
                    float gHBase = Color.green(cLeft);
                    float bHBase = Color.blue(cLeft);
                    float rHDelta = Color.red(cRight) - rHBase;
                    float gHDelta = Color.green(cRight) - gHBase;
                    float bHDelta = Color.blue(cRight) - bHBase;

                    for (int x = 0; x < sampleW; x++) {
                        float u = x * invSampleW;
                        float dx = (x - centerX) / sigmaX;
                        float centerPrior = (float) Math.exp(-(dx * dx + dy * dy) * 0.85f);

                        // Gradiente estructural para detectar contornos y texturas nítidas del sujeto
                        float gx = 0f;
                        float gy = 0f;
                        if (x > 0 && x < sampleW - 1) {
                            gx = lum[rowOffset + (x + 1)] - lum[rowOffset + (x - 1)];
                        }
                        if (y > 0 && y < sampleH - 1) {
                            gy = lum[rowOffset + sampleW + x] - lum[rowOffset - sampleW + x];
                        }
                        float edgeEnergy = (Math.abs(gx) + Math.abs(gy)) / 255.0f;

                        // Estimación de color de fondo local interpolado bilinealmente
                        int cTop = topBorder[x];
                        int cBot = botBorder[x];
                        float rV = (1.0f - v) * Color.red(cTop) + v * Color.red(cBot);
                        float gV = (1.0f - v) * Color.green(cTop) + v * Color.green(cBot);
                        float bV = (1.0f - v) * Color.blue(cTop) + v * Color.blue(cBot);

                        float rH = rHBase + u * rHDelta;
                        float gH = gHBase + u * gHDelta;
                        float bH = bHBase + u * bHDelta;

                        float estBgR = (rV + rH) * 0.5f;
                        float estBgG = (gV + gH) * 0.5f;
                        float estBgB = (bV + bH) * 0.5f;

                        int p = pixels[rowOffset + x];
                        float dR = Color.red(p) - estBgR;
                        float dG = Color.green(p) - estBgG;
                        float dB = Color.blue(p) - estBgB;
                        float colorDist = (float) Math.hypot(Math.hypot(dR, dG), dB) / 255.0f;

                        // Atenuación progresiva hacia los límites de pantalla para evitar cortes duros
                        float edgeFadeX = Math.min(1.0f, Math.min(x, sampleW - 1 - x) / (sampleW * 0.06f));
                        float edgeFadeY = Math.min(1.0f, Math.min(y, sampleH - 1 - y) / (sampleH * 0.05f));
                        float edgeFade = edgeFadeX * edgeFadeY;

                        // Saliencia compuesta con filtrado de ruido
                        float salience = (colorDist * 0.75f + edgeEnergy * 0.55f) * centerPrior * edgeFade;
                        float alphaNorm = 0.0f;
                        if (salience > 0.16f) {
                            alphaNorm = Math.min(1.0f, (salience - 0.16f) / 0.22f);
                        }
                        rawMask[rowOffset + x] = (int) (alphaNorm * 255.0f);
                    }
                }
                small.recycle();

                // 3. Suavizado y difuminado por blur en caja separable (Separable Box Blur anti-aliasing / feathering)
                int rBlur = 3;
                int[] blurH = new int[sampleW * sampleH];
                for (int y = 0; y < sampleH; y++) {
                    int rowOffset = y * sampleW;
                    for (int x = 0; x < sampleW; x++) {
                        int sum = 0;
                        int count = 0;
                        for (int k = -rBlur; k <= rBlur; k++) {
                            int nx = x + k;
                            if (nx >= 0 && nx < sampleW) {
                                sum += rawMask[rowOffset + nx];
                                count++;
                            }
                        }
                        blurH[rowOffset + x] = sum / count;
                    }
                }

                byte[] mask = new byte[sampleW * sampleH];
                for (int y = 0; y < sampleH; y++) {
                    for (int x = 0; x < sampleW; x++) {
                        int sum = 0;
                        int count = 0;
                        for (int k = -rBlur; k <= rBlur; k++) {
                            int ny = y + k;
                            if (ny >= 0 && ny < sampleH) {
                                sum += blurH[ny * sampleW + x];
                                count++;
                            }
                        }
                        mask[y * sampleW + x] = (byte) (sum / count);
                    }
                }

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
                int finalW = Math.min(effectiveW, scaledW - cropX);
                int finalH = Math.min(effectiveH, scaledH - cropY);

                Bitmap result = Bitmap.createBitmap(scaled, cropX, cropY, finalW, finalH);
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
            if (mSensorManager == null) return;

            if (!mJellyEnabled || !mIsVisible) {
                mSensorManager.unregisterListener(mSensorListener);
                mJellyMesh.resetGyro();
                return;
            }

            mSensorManager.unregisterListener(mSensorListener);

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
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mIsVisible = visible;
            if (!visible) {
                mIsLoopRunning = false;
                Choreographer.getInstance().removeFrameCallback(this);
            } else {
                mSmoothRoll = 0f;
                mSmoothPitch = 0f;
                mLastAppliedRoll = 0f;
                mLastAppliedPitch = 0f;
                handleDisplayOrSurfaceChange();
                if (mLightSourceEnabled && mLightSolarTracking) {
                    float solarAngle = calculateSolarTrackingAngle();
                    mLightDynamicAngle = solarAngle;
                    mJellyMesh.setLightSource(true, solarAngle, mLightIntensity / 100.0f);
                }
            }
            updateSensorRegistration();
            updateVisualizer();
            if (visible) {
                mDrawn = false;
                drawFrame();
                if (mJellyEnabled && isCurrentTargetActive()) {
                    startAnimationLoopIfNeeded();
                }
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
            if (mTimeReceiver != null) {
                try {
                    ImageWallpaper.this.unregisterReceiver(mTimeReceiver);
                } catch (Exception e) {
                    try {
                        getDisplayContext().unregisterReceiver(mTimeReceiver);
                    } catch (Exception ignored) {}
                }
                mTimeReceiver = null;
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
            handleDisplayOrSurfaceChange();
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
            mDrawn = false;
            drawFrame();
        }

        private void drawFrame() {
            mLongExecutor.execute(this::drawFrameSynchronized);
        }

        private void drawFrameSynchronized() {
            synchronized (mLock) {
                Rect bounds = getDisplayBounds();
                int targetW = bounds.width();
                int targetH = bounds.height();

                if (mDrawn && mBitmap != null && !mBitmap.isRecycled()
                        && (!mJellyEnabled || (mBitmap.getWidth() == targetW && mBitmap.getHeight() == targetH))) {
                    return;
                }
                mDrawn = false;
                drawFrameInternal();
            }
        }

        private void drawFrameInternal() {
            Rect bounds = getDisplayBounds();
            int targetW = bounds.width();
            int targetH = bounds.height();

            // load the wallpaper if not already done or if orientation/dimension mismatch
            if (!isBitmapLoaded() || (mJellyEnabled && (mBitmap.getWidth() != targetW || mBitmap.getHeight() != targetH))) {
                loadWallpaperAndDrawFrameInternal();
                return;
            }

            synchronized (mSurfaceLock) {
                if (mSurfaceHolder == null) {
                    Log.i(TAG, "Surface released before the image could be drawn");
                    return;
                }
                mBitmapUsages++;
                boolean activeMode = mJellyEnabled;
                Rect dest = mSurfaceHolder.getSurfaceFrame();
                if (dest != null && dest.width() > 0 && dest.height() > 0) {
                    mJellyMesh.setSize(dest.width(), dest.height());
                } else {
                    mJellyMesh.setSize(targetW, targetH);
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
                    if (mJellyEnabled && mLightSourceEnabled && dest != null) {
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
                Rect bounds = getDisplayBounds();
                int targetW = bounds.width();
                int targetH = bounds.height();
                
                if (mJellyEnabled) {
                    bitmap = centerCropBitmapForDisplay(bitmap, targetW, targetH);
                }

                synchronized (mSurfaceLock) {
                    if (mSurfaceHolder != null) {
                        if (mJellyEnabled) {
                            mSurfaceHolder.setFixedSize(targetW, targetH);
                        } else {
                            mSurfaceHolder.setFixedSize(bitmap.getWidth(), bitmap.getHeight());
                        }
                    }
                    mJellyMesh.setSize(bitmap.getWidth(), bitmap.getHeight());

                    // recycle the previously loaded bitmap
                    if (mBitmap != null && mBitmap != bitmap) {
                        Trace.beginSection("WPMS.mBitmap.recycle");
                        mBitmap.recycle();
                        Trace.endSection();
                    }
                    if (mForegroundBitmap != null) {
                        mForegroundBitmap.recycle();
                        mForegroundBitmap = null;
                    }
                    mBitmap = bitmap;
                }
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
                    handleDisplayOrSurfaceChange();
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
