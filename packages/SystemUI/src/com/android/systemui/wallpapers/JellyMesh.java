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

import android.graphics.Color;
import java.util.Arrays;

/**
 * Simulación física de cuerpo elástico blando (soft-body spring-mass)
 * para deformación de textura tipo gelatina en hardware canvas con:
 * - Perspectiva 3D con giroscopio
 * - Inercia física por acelerómetro
 * - Sombra de profundidad proyectada (Faux 3D Ambient Occlusion)
 * - Desgarro / Ruptura elástica por tensión extrema (Snap-Back)
 * - Reactividad a pulsos de bajo musical (Audio Visualizer Bass Bounce)
 */
public class JellyMesh {
    public static final int COLS = 24;
    public static final int ROWS = 24;
    private static final int NUM_VERTICES = (COLS + 1) * (ROWS + 1);

    public interface SnapBackListener {
        void onSnapBack(float x, float y);
    }

    // Parámetros de física de resortes y amortiguamiento
    private float mKAnchor = 340.0f;
    private float mKInternal = 120.0f;
    private float mDamping = 8.5f;
    private float mDragRadiusRatio = 0.055f;
    private float mDragRadius = 60.0f;
    private float mDragElasticity = 0.70f;
    private static final float SLEEP_ENERGY_THRESHOLD = 20.0f;

    public void setDragElasticity(float elasticity) {
        mDragElasticity = Math.max(0.05f, Math.min(1.0f, elasticity));
    }

    private float mWidth = 1080f;
    private float mHeight = 2400f;

    // Coordenadas de reposo y dinámicas en píxeles
    private final float[] mRestX = new float[NUM_VERTICES];
    private final float[] mRestY = new float[NUM_VERTICES];
    private final float[] mPosX = new float[NUM_VERTICES];
    private final float[] mPosY = new float[NUM_VERTICES];
    private final float[] mVelX = new float[NUM_VERTICES];
    private final float[] mVelY = new float[NUM_VERTICES];
    private final float[] mForcesX = new float[NUM_VERTICES];
    private final float[] mForcesY = new float[NUM_VERTICES];

    // Buffer plano de vértices para Canvas.drawBitmapMesh [x0, y0, x1, y1, ...]
    private final float[] mVerts = new float[NUM_VERTICES * 2];
    private final float[] mForegroundVerts = new float[NUM_VERTICES * 2];
    private final float[] mShadowVerts = new float[NUM_VERTICES * 2];
    private float mDepthSeparation = 0.60f;

    public void setDepthSeparation(float separation) {
        mDepthSeparation = Math.max(0.0f, Math.min(1.0f, separation));
    }

    public float[] getForegroundVertices() {
        return mForegroundVerts;
    }

    public float[] getShadowVertices() {
        return mShadowVerts;
    }

    // Buffer de colores para iluminación / sombra de oclusión ambiental (Faux 3D Ambient Occlusion)
    // y foco de luz / brillo especular en el borde de la pantalla (Specular Light Sheen)
    private final int[] mColors = new int[NUM_VERTICES];
    private final int[] mSpecularColors = new int[NUM_VERTICES];
    private final short[] mIndices = new short[COLS * ROWS * 6];
    private boolean mAmbientOcclusionEnabled = true;
    private boolean mLightSourceEnabled = false;
    private float mLightAngle = 45.0f;
    private float mLightIntensity = 0.75f;
    private float mLightSourceX = 0f;
    private float mLightSourceY = 0f;

    private int mLightMode = 2;
    private boolean mLightSolarTracking = false;
    private boolean mLightSpecularColumn = true;
    private boolean mLightGyroParallax = true;
    private float mLightBeamSpread = 1.0f;
    private float mLightHardness = 0.5f;
    private float mLightShimmerSpeed = 0.6f;

    public void setLightSource(boolean enabled, float angleDegrees) {
        setLightSource(enabled, angleDegrees, mLightIntensity, mLightMode);
    }

    public void setLightSource(boolean enabled, float angleDegrees, float intensity) {
        setLightSource(enabled, angleDegrees, intensity, mLightMode);
    }

    public void setLightSource(boolean enabled, float angleDegrees, float intensity, int mode) {
        mLightSourceEnabled = enabled;
        mLightAngle = angleDegrees;
        mLightIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        mLightMode = mode;
        updateAllColors();
        mIsAsleep = false;
    }

    public void setLightCustomization(boolean solarTracking, boolean specularColumn, boolean gyroParallax,
            float beamSpread, float hardness, float shimmerSpeed) {
        mLightSolarTracking = solarTracking;
        mLightSpecularColumn = specularColumn;
        mLightGyroParallax = gyroParallax;
        mLightBeamSpread = Math.max(0.2f, Math.min(2.5f, beamSpread));
        mLightHardness = Math.max(0.05f, Math.min(1.0f, hardness));
        mLightShimmerSpeed = Math.max(0.1f, Math.min(2.0f, shimmerSpeed));
        updateAllColors();
        mIsAsleep = false;
    }

    public int getLightMode() {
        return mLightMode;
    }

    public boolean isSolarTrackingEnabled() {
        return mLightSolarTracking;
    }

    public short[] getIndices() {
        return mIndices;
    }

    public int[] getSpecularColors() {
        return mLightSourceEnabled ? mSpecularColors : null;
    }

    public float getLightSourceX() {
        return mLightSourceX;
    }

    public float getLightSourceY() {
        return mLightSourceY;
    }

    public float getWidth() {
        return mWidth;
    }

    public float getHeight() {
        return mHeight;
    }

    // Control táctil y límite de tensión elástica (Snap-Back Cracking)
    private boolean mIsTouching = false;
    private float mTouchX = 0f;
    private float mTouchY = 0f;
    private float mTouchStartX = 0f;
    private float mTouchStartY = 0f;
    private float mPrevTouchX = 0f;
    private float mPrevTouchY = 0f;
    private boolean mSnapBackEnabled = true;
    private SnapBackListener mSnapBackListener;
    private boolean mIsAsleep = true;
    private float mAnimTime = 0f;

    // Control multitáctil (Pellizco y tensión entre dedos)
    private boolean mMultiTouchEnabled = true;
    private int mActivePointerCount = 0;
    private float mTouchX1 = 0f;
    private float mTouchY1 = 0f;
    private float mTouchStartX1 = 0f;
    private float mTouchStartY1 = 0f;
    private float mPrevTouchX1 = 0f;
    private float mPrevTouchY1 = 0f;

    public void setMultiTouchEnabled(boolean enabled) {
        mMultiTouchEnabled = enabled;
        if (!enabled) {
            mActivePointerCount = Math.min(mActivePointerCount, 1);
            mTouchX1 = 0f;
            mTouchY1 = 0f;
            mTouchStartX1 = 0f;
            mTouchStartY1 = 0f;
        }
    }

    // Perspectiva 3D con deformación de profundidad
    private float mTargetRoll = 0f;
    private float mTargetPitch = 0f;
    private float mCurrentRoll = 0f;
    private float mCurrentPitch = 0f;
    private boolean mGyroActive = false;

    // Inercia física global por acelerómetro (jiggle al sacudir o mover el móvil)
    private float mInertialForceX = 0f;
    private float mInertialForceY = 0f;

    public JellyMesh() {
        int idx = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                short topLeft = (short) (r * (COLS + 1) + c);
                short topRight = (short) (topLeft + 1);
                short bottomLeft = (short) ((r + 1) * (COLS + 1) + c);
                short bottomRight = (short) (bottomLeft + 1);

                mIndices[idx++] = topLeft;
                mIndices[idx++] = bottomLeft;
                mIndices[idx++] = topRight;

                mIndices[idx++] = topRight;
                mIndices[idx++] = bottomLeft;
                mIndices[idx++] = bottomRight;
            }
        }
        setSize(1080f, 2400f);
    }

    public void setSnapBackListener(SnapBackListener listener) {
        mSnapBackListener = listener;
    }

    private float mAoIntensity = 0.75f;

    public void setAmbientOcclusion(boolean enabled, float intensity) {
        mAmbientOcclusionEnabled = enabled;
        mAoIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        if (!enabled || mAoIntensity <= 0.001f) {
            Arrays.fill(mColors, 0xFFFFFFFF);
        } else {
            updateAllColors();
        }
    }

    public void setAmbientOcclusionEnabled(boolean enabled) {
        setAmbientOcclusion(enabled, mAoIntensity);
    }

    public void setSnapBackEnabled(boolean enabled) {
        mSnapBackEnabled = enabled;
    }

    public void applyInertialForce(float forceX, float forceY) {
        applyShakeImpulse(forceX, forceY);
    }

    public void applyShakeImpulse(float impulseX, float impulseY) {
        float maxImpulse = mWidth * 0.30f;
        float baseScale = mWidth * 0.08f;
        float kickX = impulseX * baseScale;
        float kickY = impulseY * baseScale;
        float clampedX = Math.max(-maxImpulse, Math.min(maxImpulse, kickX));
        float clampedY = Math.max(-maxImpulse, Math.min(maxImpulse, kickY));

        for (int r = 0; r <= ROWS; r++) {
            float fracY = r / (float) ROWS;
            float edgeY = (float) Math.sin(fracY * Math.PI);
            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;
                float fracX = c / (float) COLS;
                float edgeX = (float) Math.sin(fracX * Math.PI);
                float edgeFactor = (float) Math.pow(edgeX * edgeY, 0.45);

                mVelX[idx] += clampedX * edgeFactor * 10.0f;
                mVelY[idx] += clampedY * edgeFactor * 10.0f;
            }
        }
        mIsAsleep = false;
    }

    public void triggerMusicBassPulse(float strength) {
        triggerMusicBassPulse(strength, 0);
    }

    public void triggerMusicBassPulse(float strength, int pattern) {
        float pulseStrength = Math.min(1.0f, Math.max(0.05f, strength));
        float centerX = mWidth * 0.5f;
        float centerY = mHeight * 0.5f;
        float maxKick = mWidth * pulseStrength * 0.05f;

        switch (pattern) {
            case 1: { // Subwoofer / Vertical Bounce
                for (int r = 0; r <= ROWS; r++) {
                    float edgeY = (float) Math.sin(Math.PI * r / (float) ROWS);
                    for (int c = 0; c <= COLS; c++) {
                        int idx = r * (COLS + 1) + c;
                        float edgeX = (float) Math.sin(Math.PI * c / (float) COLS);
                        float edgeFactor = edgeX * edgeY;
                        mVelY[idx] += maxKick * edgeFactor * 1.8f;
                    }
                }
                break;
            }
            case 2: { // Heartbeat / Zoom Pulse (Speaker Cone)
                float sigma = mWidth * 0.6f;
                float twoSigmaSq = 2.0f * sigma * sigma;
                for (int i = 0; i < NUM_VERTICES; i++) {
                    float dx = mPosX[i] - centerX;
                    float dy = mPosY[i] - centerY;
                    float distSq = dx * dx + dy * dy;
                    float factor = (float) Math.exp(-distSq / twoSigmaSq);
                    mVelX[i] += (dx / mWidth) * maxKick * factor * 1.5f;
                    mVelY[i] += (dy / mHeight) * maxKick * factor * 1.5f;
                }
                break;
            }
            case 3: { // Wave Cascade / Horizontal Sine
                for (int r = 0; r <= ROWS; r++) {
                    float phase = (float) (r / (float) ROWS * Math.PI * 3.0);
                    float waveKick = (float) Math.sin(phase) * maxKick * 1.4f;
                    float edgeY = (float) Math.sin(Math.PI * r / (float) ROWS);
                    for (int c = 0; c <= COLS; c++) {
                        int idx = r * (COLS + 1) + c;
                        float edgeX = (float) Math.sin(Math.PI * c / (float) COLS);
                        mVelX[idx] += waveKick * edgeX * edgeY;
                    }
                }
                break;
            }
            case 4: { // 4 Corners Shockwave
                float[] cornerX = {0, mWidth, 0, mWidth};
                float[] cornerY = {0, 0, mHeight, mHeight};
                float sigma = mWidth * 0.5f;
                float twoSigmaSq = 2.0f * sigma * sigma;
                for (int i = 0; i < NUM_VERTICES; i++) {
                    float totalVx = 0, totalVy = 0;
                    for (int k = 0; k < 4; k++) {
                        float dx = mPosX[i] - cornerX[k];
                        float dy = mPosY[i] - cornerY[k];
                        float distSq = dx * dx + dy * dy;
                        float dist = (float) Math.sqrt(distSq);
                        if (dist > 1.0f) {
                            float wave = (float) Math.exp(-distSq / twoSigmaSq) * (maxKick * 0.6f);
                            totalVx += (dx / dist) * wave;
                            totalVy += (dy / dist) * wave;
                        }
                    }
                    mVelX[i] += totalVx;
                    mVelY[i] += totalVy;
                }
                break;
            }
            case 0:
            default: { // Central Radial Ripple
                float sigma = mWidth * 0.45f;
                float twoSigmaSq = 2.0f * sigma * sigma;
                for (int i = 0; i < NUM_VERTICES; i++) {
                    float dx = mPosX[i] - centerX;
                    float dy = mPosY[i] - centerY;
                    float distSq = dx * dx + dy * dy;
                    float dist = (float) Math.sqrt(distSq);
                    if (dist > 1.0f) {
                        float normX = dx / dist;
                        float normY = dy / dist;
                        float wave = (float) Math.exp(-distSq / twoSigmaSq) * maxKick;
                        mVelX[i] += normX * wave;
                        mVelY[i] += normY * wave;
                    }
                }
                break;
            }
        }
        mIsAsleep = false;
    }

    public void setGyroTilt(float roll, float pitch) {
        mTargetRoll = Math.max(-1.0f, Math.min(1.0f, roll));
        mTargetPitch = Math.max(-1.0f, Math.min(1.0f, pitch));
        mGyroActive = (Math.abs(mTargetRoll) > 0.005f || Math.abs(mTargetPitch) > 0.005f);
        mIsAsleep = false;
    }

    public void resetGyro() {
        mTargetRoll = 0f;
        mTargetPitch = 0f;
        mCurrentRoll = 0f;
        mCurrentPitch = 0f;
        mInertialForceX = 0f;
        mInertialForceY = 0f;
        mGyroActive = false;
    }

    public void setSize(float width, float height) {
        if (width <= 0 || height <= 0) return;
        mWidth = width;
        mHeight = height;
        mDragRadius = Math.min(width, height) * mDragRadiusRatio;

        for (int r = 0; r <= ROWS; r++) {
            float y = (r / (float) ROWS) * height;
            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;
                float x = (c / (float) COLS) * width;

                mRestX[idx] = x;
                mRestY[idx] = y;
                mPosX[idx] = x;
                mPosY[idx] = y;
                mVelX[idx] = 0f;
                mVelY[idx] = 0f;

                mVerts[idx * 2] = x;
                mVerts[idx * 2 + 1] = y;
                mForegroundVerts[idx * 2] = x;
                mForegroundVerts[idx * 2 + 1] = y;
                mShadowVerts[idx * 2] = x;
                mShadowVerts[idx * 2 + 1] = y;
                mColors[idx] = 0xFFFFFFFF;
                mSpecularColors[idx] = 0x00000000;
            }
        }
        mIsAsleep = true;
    }

    // Parámetros físicos avanzados ajustables
    private float mSnapBackRecoilRatio = 1.0f;
    private float mInternalTensionRatio = 1.0f;
    private float mMaxStretchRatio = 1.0f;
    private float mSurfaceMassRatio = 1.0f;
    private float mWaveSpeedRatio = 1.0f;

    public void setExtendedPhysicsParams(int recoil, int tension, int maxStretch, int mass, int waveSpeed) {
        mSnapBackRecoilRatio = Math.max(0.1f, Math.min(2.0f, recoil / 75.0f));
        mInternalTensionRatio = Math.max(0.1f, Math.min(2.0f, tension / 65.0f));
        mMaxStretchRatio = Math.max(0.15f, Math.min(2.0f, maxStretch / 70.0f));
        mSurfaceMassRatio = Math.max(0.35f, Math.min(2.5f, mass / 50.0f));
        mWaveSpeedRatio = Math.max(0.2f, Math.min(2.5f, waveSpeed / 70.0f));
        mKInternal = Math.max(15.0f, mKAnchor * 0.35f * mInternalTensionRatio);
    }

    public void setPhysicsParams(float stiffness, float damping, float touchRadius) {
        mKAnchor = Math.max(10.0f, stiffness * 1.5f);
        mKInternal = Math.max(5.0f, stiffness * 0.40f * mInternalTensionRatio);
        mDamping = Math.max(1.2f, damping);
        mDragRadiusRatio = Math.max(0.04f, touchRadius);
        mDragRadius = Math.min(mWidth, mHeight) * mDragRadiusRatio;
    }

    public void triggerRipple(float originX, float originY, float strength) {
        float sigma = Math.max(mWidth * 0.12f, mDragRadius * 1.5f);
        float twoSigmaSq = 2.0f * sigma * sigma;
        float maxVel = Math.min(mWidth * 0.35f, mWidth * strength * 0.015f * mWaveSpeedRatio);

        for (int i = 0; i < NUM_VERTICES; i++) {
            float dx = mPosX[i] - originX;
            float dy = mPosY[i] - originY;
            float distSq = dx * dx + dy * dy;
            float dist = (float) Math.sqrt(distSq);
            if (dist > 1.0f) {
                float normX = dx / dist;
                float normY = dy / dist;
                float pulse = (float) Math.exp(-distSq / twoSigmaSq) * maxVel;
                mVelX[i] += normX * pulse;
                mVelY[i] += normY * pulse;
            }
        }
        mIsAsleep = false;
    }

    public void triggerChargingWave(float strength) {
        float waveStrength = Math.min(1.0f, Math.max(0.1f, strength));
        float maxKickY = -mHeight * waveStrength * 0.065f; // Impulso ascendente hacia arriba

        for (int r = 0; r <= ROWS; r++) {
            float fracY = r / (float) ROWS;
            float rowWavePhase = (1.0f - fracY) * (float) Math.PI * 1.6f;
            float edgeY = (float) Math.sin(fracY * Math.PI);
            float rowImpulse = maxKickY * (float) Math.sin(rowWavePhase);

            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;
                float fracX = c / (float) COLS;
                float edgeX = (float) Math.sin(fracX * Math.PI);
                float edgeFactor = (float) Math.pow(edgeX * edgeY, 0.4);

                mVelY[idx] += rowImpulse * edgeFactor;
            }
        }
        mIsAsleep = false;
    }

    public interface HapticFeedbackListener {
        void onStretchTick();
        void onRelease(float displacement);
    }

    private static class WaterRippleWave {
        float originX;
        float originY;
        float age = 0f;
        float maxAge = 1.4f;
        float speed;
        float amplitude;
        float wavelength;
        boolean active = false;
    }

    private static final int MAX_RIPPLES = 6;
    private final WaterRippleWave[] mRipples = new WaterRippleWave[MAX_RIPPLES];
    private boolean mWaterRippleEnabled = false;
    private float mWaterRippleStrength = 0.75f;
    private int mWaterRippleMode = 2; // 0: Solo al tocar, 1: Al deslizar el dedo, 2: Ambos
    private float mRippleSwipeDistance = 0f;

    public void setWaterRippleParams(boolean enabled, int strength, int mode) {
        mWaterRippleStrength = Math.max(0.0f, Math.min(1.0f, strength / 100.0f));
        mWaterRippleEnabled = enabled && (mWaterRippleStrength > 0.001f);
        mWaterRippleMode = mode;
        if (!mWaterRippleEnabled) {
            for (int i = 0; i < MAX_RIPPLES; i++) {
                if (mRipples[i] != null) {
                    mRipples[i].active = false;
                }
            }
        }
    }

    public void setWaterRippleParams(boolean enabled, int strength) {
        setWaterRippleParams(enabled, strength, mWaterRippleMode);
    }

    public void triggerWaterRipple(float originX, float originY, float intensity) {
        if (!mWaterRippleEnabled || mWaterRippleStrength <= 0.001f) return;
        for (int i = 0; i < MAX_RIPPLES; i++) {
            if (mRipples[i] == null) {
                mRipples[i] = new WaterRippleWave();
            }
            if (!mRipples[i].active) {
                mRipples[i].originX = originX;
                mRipples[i].originY = originY;
                mRipples[i].age = 0f;
                mRipples[i].maxAge = 1.3f;
                mRipples[i].speed = Math.max(mWidth, mHeight) * 0.95f * mWaveSpeedRatio;
                mRipples[i].amplitude = mWidth * 0.055f * intensity * mWaterRippleStrength;
                mRipples[i].wavelength = mWidth * 0.14f;
                mRipples[i].active = true;
                break;
            }
        }
        mIsAsleep = false;
    }

    private HapticFeedbackListener mHapticListener;
    private boolean mTapShockwaveEnabled = true;
    private long mTouchStartTime = 0;
    private float mAccumulatedDragDistance = 0f;

    public void setHapticFeedbackListener(HapticFeedbackListener listener) {
        mHapticListener = listener;
    }

    public void setTapShockwaveEnabled(boolean enabled) {
        mTapShockwaveEnabled = enabled;
    }

    public void triggerTapShockwave(float originX, float originY, float intensity) {
        triggerRipple(originX, originY, 50.0f * intensity);
    }

    private boolean mHasSnappedThisGesture = false;

    public void onTouchDown(float x, float y) {
        mIsTouching = true;
        mHasSnappedThisGesture = false;
        mTouchX = x;
        mTouchY = y;
        mTouchStartX = x;
        mTouchStartY = y;
        mPrevTouchX = x;
        mPrevTouchY = y;
        mTouchStartTime = android.os.SystemClock.uptimeMillis();
        mAccumulatedDragDistance = 0f;
        mRippleSwipeDistance = 0f;
        mIsAsleep = false;
        if (mWaterRippleEnabled && (mWaterRippleMode == 0 || mWaterRippleMode == 2)) {
            triggerWaterRipple(x, y, 1.0f);
        }
    }

    public void onTouchMove(float x, float y) {
        mPrevTouchX = mTouchX;
        mPrevTouchY = mTouchY;
        mTouchX = x;
        mTouchY = y;
        mIsAsleep = false;

        float stepDist = (float) Math.hypot(x - mPrevTouchX, y - mPrevTouchY);
        mAccumulatedDragDistance += stepDist;
        if (mAccumulatedDragDistance > 36.0f) {
            mAccumulatedDragDistance = 0f;
            if (mHapticListener != null) {
                mHapticListener.onStretchTick();
            }
        }

        // Generar estela de ondas de agua continuas al deslizar / pasar el dedo por la pantalla
        if (mWaterRippleEnabled && (mWaterRippleMode == 1 || mWaterRippleMode == 2)) {
            mRippleSwipeDistance += stepDist;
            if (mRippleSwipeDistance > 55.0f) {
                mRippleSwipeDistance = 0f;
                triggerWaterRipple(x, y, 0.70f);
            }
        }
    }

    public void onTouchUp() {
        if (!mIsTouching) return;
        mIsTouching = false;
        mActivePointerCount = 0;
        long duration = android.os.SystemClock.uptimeMillis() - mTouchStartTime;
        float dragVecX = mTouchStartX - mTouchX;
        float dragVecY = mTouchStartY - mTouchY;
        float totalDist = (float) Math.hypot(dragVecX, dragVecY);

        // Si fue un tap rápido y estático
        if (duration < 260 && totalDist < 20.0f) {
            if (mTapShockwaveEnabled) {
                triggerTapShockwave(mTouchX, mTouchY, 1.0f);
            }
            if (mHapticListener != null) {
                mHapticListener.onStretchTick();
            }
        } else if (totalDist >= 16.0f) {
            // Potente impulso elástico de retroceso al soltar (Slingshot Snap Recoil)
            float normX = dragVecX / totalDist;
            float normY = dragVecY / totalDist;
            float recoilSpeed = Math.min(mWidth * 0.45f, totalDist * 2.5f) * mSnapBackRecoilRatio;
            float twoSigmaSq = 2.0f * mDragRadius * mDragRadius * 2.2f;

            for (int i = 0; i < NUM_VERTICES; i++) {
                float dx = mPosX[i] - mTouchX;
                float dy = mPosY[i] - mTouchY;
                float distSq = dx * dx + dy * dy;
                if (distSq < mDragRadius * mDragRadius * 3.5f) {
                    float weight = (float) Math.exp(-distSq / twoSigmaSq);
                    mVelX[i] += normX * recoilSpeed * weight;
                    mVelY[i] += normY * recoilSpeed * weight;
                }
            }

            if (mSnapBackListener != null) {
                mSnapBackListener.onSnapBack(mTouchX, mTouchY);
            }
            if (mHapticListener != null) {
                mHapticListener.onRelease(totalDist);
            }
        }
        mIsAsleep = false;
    }

    public void onMultiTouchEvent(int actionMasked, int pointerCount, float x0, float y0, float x1, float y1) {
        if (actionMasked == android.view.MotionEvent.ACTION_UP || actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
            onTouchUp();
            return;
        }

        if (pointerCount <= 1 || !mMultiTouchEnabled) {
            mActivePointerCount = 1;
            if (actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                onTouchDown(x0, y0);
            } else if (actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                if (!mIsTouching) {
                    onTouchDown(x0, y0);
                } else {
                    onTouchMove(x0, y0);
                }
            }
        } else {
            mActivePointerCount = 2;
            mIsTouching = true;
            mIsAsleep = false;
            if (actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN || actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                mTouchX = x0;
                mTouchY = y0;
                mPrevTouchX = x0;
                mPrevTouchY = y0;
                mTouchStartX = x0;
                mTouchStartY = y0;

                mTouchX1 = x1;
                mTouchY1 = y1;
                mPrevTouchX1 = x1;
                mPrevTouchY1 = y1;
                mTouchStartX1 = x1;
                mTouchStartY1 = y1;
            } else if (actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                mPrevTouchX = mTouchX;
                mPrevTouchY = mTouchY;
                mTouchX = x0;
                mTouchY = y0;

                mPrevTouchX1 = mTouchX1;
                mPrevTouchY1 = mTouchY1;
                mTouchX1 = x1;
                mTouchY1 = y1;

                float stepDist = (float) Math.hypot(x0 - mPrevTouchX, y0 - mPrevTouchY);
                mAccumulatedDragDistance += stepDist;
                if (mAccumulatedDragDistance > 36.0f) {
                    mAccumulatedDragDistance = 0f;
                    if (mHapticListener != null) {
                        mHapticListener.onStretchTick();
                    }
                }
            } else if (actionMasked == android.view.MotionEvent.ACTION_POINTER_UP) {
                mActivePointerCount = 1;
                mTouchStartX = x0;
                mTouchStartY = y0;
                mTouchX = x0;
                mTouchY = y0;
                mPrevTouchX = x0;
                mPrevTouchY = y0;
            }
        }
    }

    public boolean stepPhysics(float dt) {
        dt = Math.min(Math.max(dt, 0.001f), 0.033f);

        // Si la malla física está en reposo, animar únicamente el resplandor de la luz sin tocar los resortes de la física
        if (mIsAsleep && !mIsTouching) {
            float deltaRoll = mTargetRoll - mCurrentRoll;
            float deltaPitch = mTargetPitch - mCurrentPitch;
            boolean gyroMoving = Math.abs(deltaRoll) > 0.0005f || Math.abs(deltaPitch) > 0.0005f;
            if (!gyroMoving && mInertialForceX == 0f && mInertialForceY == 0f) {
                if (mLightSourceEnabled && (mLightMode == 7 || mLightSpecularColumn)) {
                    mAnimTime += dt;
                    updateAllColors();
                    return true;
                }
                return false;
            } else {
                mIsAsleep = false;
            }
        }

        mAnimTime += dt;

        // 3D Perspective Projection Parallax (Responsive real-time optical shift)
        float deltaRoll = mTargetRoll - mCurrentRoll;
        float deltaPitch = mTargetPitch - mCurrentPitch;
        mCurrentRoll += deltaRoll * Math.min(1.0f, dt * 25.0f);
        mCurrentPitch += deltaPitch * Math.min(1.0f, dt * 25.0f);

        boolean gyroMoving = Math.abs(deltaRoll) > 0.0005f || Math.abs(deltaPitch) > 0.0005f;

        // Desplazamiento de profundidad cinemática (Parallax 3D suave sin efecto de doblado de página)
        float maxParallaxX = mWidth * 0.060f;
        float maxParallaxY = mHeight * 0.045f;
        float transX = -mCurrentRoll * maxParallaxX;
        float transY = mCurrentPitch * maxParallaxY;

        float currentInertiaX = mInertialForceX;
        float currentInertiaY = mInertialForceY;
        mInertialForceX *= Math.max(0f, 1.0f - dt * 6.0f);
        mInertialForceY *= Math.max(0f, 1.0f - dt * 6.0f);
        if (Math.abs(mInertialForceX) < 0.2f) mInertialForceX = 0f;
        if (Math.abs(mInertialForceY) < 0.2f) mInertialForceY = 0f;
        boolean inertiaMoving = Math.abs(currentInertiaX) > 0.5f || Math.abs(currentInertiaY) > 0.5f;

        float totalDragX = 0f;
        float totalDragY = 0f;
        float totalDragX1 = 0f;
        float totalDragY1 = 0f;

        if (mIsTouching) {
            float elasticityFactor = 0.20f + 0.80f * mDragElasticity;
            float rawDragX = (mTouchX - mTouchStartX) * elasticityFactor;
            float rawDragY = (mTouchY - mTouchStartY) * elasticityFactor;
            float dragLen = (float) Math.hypot(rawDragX, rawDragY);
            float maxDrag = mWidth * (0.08f + 0.22f * mDragElasticity) * mMaxStretchRatio;
            if (dragLen > maxDrag && dragLen > 0.001f) {
                float scale = maxDrag / dragLen;
                totalDragX = rawDragX * scale;
                totalDragY = rawDragY * scale;
                mTouchStartX = mTouchX - totalDragX / elasticityFactor;
                mTouchStartY = mTouchY - totalDragY / elasticityFactor;
            } else {
                totalDragX = rawDragX;
                totalDragY = rawDragY;
            }

            if (mActivePointerCount >= 2 && mMultiTouchEnabled) {
                float rawDragX1 = (mTouchX1 - mTouchStartX1) * elasticityFactor;
                float rawDragY1 = (mTouchY1 - mTouchStartY1) * elasticityFactor;
                float dragLen1 = (float) Math.hypot(rawDragX1, rawDragY1);
                if (dragLen1 > maxDrag && dragLen1 > 0.001f) {
                    float scale1 = maxDrag / dragLen1;
                    totalDragX1 = rawDragX1 * scale1;
                    totalDragY1 = rawDragY1 * scale1;
                    mTouchStartX1 = mTouchX1 - totalDragX1 / elasticityFactor;
                    mTouchStartY1 = mTouchY1 - totalDragY1 / elasticityFactor;
                } else {
                    totalDragX1 = rawDragX1;
                    totalDragY1 = rawDragY1;
                }
            }
        }

        float pinchDx = mTouchX1 - mTouchX;
        float pinchDy = mTouchY1 - mTouchY;
        float pinchDist = (float) Math.hypot(pinchDx, pinchDy);
        float initPinchDist = (float) Math.hypot(mTouchStartX1 - mTouchStartX, mTouchStartY1 - mTouchStartY);
        float unitPinchX = pinchDist > 1.0f ? pinchDx / pinchDist : 1.0f;
        float unitPinchY = pinchDist > 1.0f ? pinchDy / pinchDist : 0f;
        float perpPinchX = -unitPinchY;
        float perpPinchY = unitPinchX;

        float twoRadiusSq = 2.0f * mDragRadius * mDragRadius;
        float twoSigmaSq = twoRadiusSq;
        float maxOffset = mWidth * 0.22f * mMaxStretchRatio;
        float invMass = 1.0f / mSurfaceMassRatio;
        float maxVel = mWidth * 2.5f;

        final int SUB_STEPS = 2;
        float subDt = dt / SUB_STEPS;
        float dampingDecay = (float) Math.exp(-mDamping * subDt);

        for (int step = 0; step < SUB_STEPS; step++) {
            // 1. Calcular todas las fuerzas para cada vértice
            for (int r = 0; r <= ROWS; r++) {
                for (int c = 0; c <= COLS; c++) {
                    int idx = r * (COLS + 1) + c;

                    // Bordes fijos
                    if (r == 0 || r == ROWS || c == 0 || c == COLS) {
                        mForcesX[idx] = 0f;
                        mForcesY[idx] = 0f;
                        continue;
                    }

                    float px = mPosX[idx];
                    float py = mPosY[idx];

                    // Fuerza de anclaje elástico al punto de reposo + Inercia
                    float fx = -mKAnchor * (px - mRestX[idx]) + currentInertiaX;
                    float fy = -mKAnchor * (py - mRestY[idx]) + currentInertiaY;

                    // Fuerzas elásticas internas con vecinos relativos a la malla de reposo
                    float curDispX = px - mRestX[idx];
                    float curDispY = py - mRestY[idx];

                    int left = idx - 1;
                    fx += -mKInternal * (curDispX - (mPosX[left] - mRestX[left]));
                    fy += -mKInternal * (curDispY - (mPosY[left] - mRestY[left]));

                    int right = idx + 1;
                    fx += -mKInternal * (curDispX - (mPosX[right] - mRestX[right]));
                    fy += -mKInternal * (curDispY - (mPosY[right] - mRestY[right]));

                    int top = idx - (COLS + 1);
                    fx += -mKInternal * (curDispX - (mPosX[top] - mRestX[top]));
                    fy += -mKInternal * (curDispY - (mPosY[top] - mRestY[top]));

                    int bottom = idx + (COLS + 1);
                    fx += -mKInternal * (curDispX - (mPosX[bottom] - mRestX[bottom]));
                    fy += -mKInternal * (curDispY - (mPosY[bottom] - mRestY[bottom]));

                    // Fuerzas de ondas concéntricas de agua expansivas (Water Ripple Wavefront)
                    if (mWaterRippleEnabled) {
                        for (int w = 0; w < MAX_RIPPLES; w++) {
                            WaterRippleWave rip = mRipples[w];
                            if (rip != null && rip.active) {
                                float rdx = px - rip.originX;
                                float rdy = py - rip.originY;
                                float rdist = (float) Math.hypot(rdx, rdy);
                                float currentRadius = rip.speed * rip.age;
                                float distDiff = rdist - currentRadius;
                                float sigma = rip.wavelength * 0.75f;
                                if (Math.abs(distDiff) < sigma * 2.2f && rdist > 1.0f) {
                                    float decay = (float) Math.exp(-rip.age * 2.8f);
                                    float crest = (float) (Math.exp(-(distDiff * distDiff) / (2.0f * sigma * sigma))
                                            * Math.sin((distDiff / rip.wavelength) * Math.PI * 2.0));
                                    float waveForce = crest * decay * rip.amplitude * 35.0f;
                                    fx += (rdx / rdist) * waveForce;
                                    fy += (rdy / rdist) * waveForce;
                                }
                            }
                        }
                    }

                    mForcesX[idx] = fx;
                    mForcesY[idx] = fy;
                }
            }

            // 2. Integración simpléctica Euler de velocidad y posición
            for (int r = 0; r <= ROWS; r++) {
                for (int c = 0; c <= COLS; c++) {
                    int idx = r * (COLS + 1) + c;

                    // Fijar bordes exteriores
                    if (c == 0) {
                        mPosX[idx] = 0f;
                        mVelX[idx] = 0f;
                        continue;
                    } else if (c == COLS) {
                        mPosX[idx] = mWidth;
                        mVelX[idx] = 0f;
                        continue;
                    }
                    if (r == 0) {
                        mPosY[idx] = 0f;
                        mVelY[idx] = 0f;
                        continue;
                    } else if (r == ROWS) {
                        mPosY[idx] = mHeight;
                        mVelY[idx] = 0f;
                        continue;
                    }

                    if (mIsTouching) {
                        float touchDispX = 0f;
                        float touchDispY = 0f;
                        float totalW = 0f;

                        float dx0 = mRestX[idx] - mTouchX;
                        float dy0 = mRestY[idx] - mTouchY;
                        float distSq0 = dx0 * dx0 + dy0 * dy0;
                        if (distSq0 < twoRadiusSq * 3.0f) {
                            float w0 = (float) Math.exp(-distSq0 / twoRadiusSq);
                            touchDispX += totalDragX * w0;
                            touchDispY += totalDragY * w0;
                            totalW += w0;
                        }

                        if (mActivePointerCount >= 2 && mMultiTouchEnabled) {
                            float dx1 = mRestX[idx] - mTouchX1;
                            float dy1 = mRestY[idx] - mTouchY1;
                            float distSq1 = dx1 * dx1 + dy1 * dy1;
                            if (distSq1 < twoRadiusSq * 3.0f) {
                                float w1 = (float) Math.exp(-distSq1 / twoRadiusSq);
                                touchDispX += totalDragX1 * w1;
                                touchDispY += totalDragY1 * w1;
                                totalW += w1;
                            }

                            if (pinchDist > 10.0f && initPinchDist > 10.0f) {
                                float relX = mRestX[idx] - mTouchX;
                                float relY = mRestY[idx] - mTouchY;
                                float projT = (relX * unitPinchX + relY * unitPinchY) / pinchDist;
                                if (projT >= 0.0f && projT <= 1.0f) {
                                    float perpDist = Math.abs(relX * perpPinchX + relY * perpPinchY);
                                    float bridgeRadius = mDragRadius * 1.5f;
                                    if (perpDist < bridgeRadius) {
                                        float bridgeWeight = (float) (Math.sin(projT * Math.PI) * Math.exp(-(perpDist * perpDist) / twoSigmaSq));
                                        float pinchDelta = (pinchDist - initPinchDist) * 0.5f;
                                        float stretchFactor = (projT - 0.5f) * pinchDelta * (0.3f + 0.7f * mDragElasticity);

                                        float midDragX = (totalDragX + totalDragX1) * 0.5f + unitPinchX * stretchFactor;
                                        float midDragY = (totalDragY + totalDragY1) * 0.5f + unitPinchY * stretchFactor;
                                        touchDispX += midDragX * bridgeWeight;
                                        touchDispY += midDragY * bridgeWeight;
                                        totalW += bridgeWeight;
                                    }
                                }
                            }
                        }

                        if (totalW > 0.001f) {
                            // Arrastre directo 100% responsivo bajo el dedo
                            float targetX = mRestX[idx] + touchDispX;
                            float targetY = mRestY[idx] + touchDispY;
                            mPosX[idx] = targetX;
                            mPosY[idx] = targetY;
                            mVelX[idx] = 0f;
                            mVelY[idx] = 0f;
                            continue;
                        }
                    }

                    // Actualizar velocidad con amortiguamiento exponencial para vértices libres
                    mVelX[idx] = (mVelX[idx] + mForcesX[idx] * invMass * subDt) * dampingDecay;
                    mVelY[idx] = (mVelY[idx] + mForcesY[idx] * invMass * subDt) * dampingDecay;

                    // Límite de velocidad
                    if (mVelX[idx] > maxVel) mVelX[idx] = maxVel;
                    else if (mVelX[idx] < -maxVel) mVelX[idx] = -maxVel;
                    if (mVelY[idx] > maxVel) mVelY[idx] = maxVel;
                    else if (mVelY[idx] < -maxVel) mVelY[idx] = -maxVel;

                    // Actualizar posición
                    mPosX[idx] += mVelX[idx] * subDt;
                    mPosY[idx] += mVelY[idx] * subDt;

                    // Límite elástico de deformación
                    float dispX = mPosX[idx] - mRestX[idx];
                    float dispY = mPosY[idx] - mRestY[idx];
                    if (Math.abs(dispX) > maxOffset) {
                        mPosX[idx] = mRestX[idx] + Math.signum(dispX) * maxOffset;
                        mVelX[idx] *= 0.5f;
                    }
                    if (Math.abs(dispY) > maxOffset) {
                        mPosY[idx] = mRestY[idx] + Math.signum(dispY) * maxOffset;
                        mVelY[idx] *= 0.5f;
                    }
                }
            }

            // Avanzar edad de las ondas de agua activas
            if (mWaterRippleEnabled) {
                for (int w = 0; w < MAX_RIPPLES; w++) {
                    WaterRippleWave rip = mRipples[w];
                    if (rip != null && rip.active) {
                        rip.age += subDt;
                        if (rip.age >= rip.maxAge) {
                            rip.active = false;
                        }
                    }
                }
            }
        }

        // 3. Post-procesamiento de vértices, Parallax 3D y cálculo de energía
        float totalKineticEnergy = 0f;
        float maxDisp = 0f;

        for (int r = 0; r <= ROWS; r++) {
            float fracY = r / (float) ROWS;
            float edgeY = (float) Math.sin(fracY * Math.PI);

            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;
                float fracX = c / (float) COLS;
                float edgeX = (float) Math.sin(fracX * Math.PI);

                // Factor de borde para fijar las esquinas y suavizar el movimiento hacia los bordes
                float edgeFactor = (float) Math.pow(edgeX * edgeY, 0.35);

                // Desplazamiento de fondo Parallax 3D
                float bgShiftX = transX * edgeFactor * 0.45f;
                float bgShiftY = transY * edgeFactor * 0.45f;
                mVerts[idx * 2] = mPosX[idx] + bgShiftX;
                mVerts[idx * 2 + 1] = mPosY[idx] + bgShiftY;

                // Desplazamiento del sujeto en primer plano (3D Depth Effect)
                float fgMult = 1.0f + mDepthSeparation * 2.2f;
                float fgShiftX = -transX * edgeFactor * fgMult;
                float fgShiftY = -transY * edgeFactor * fgMult;
                mForegroundVerts[idx * 2] = mPosX[idx] + fgShiftX;
                mForegroundVerts[idx * 2 + 1] = mPosY[idx] + fgShiftY;

                // Sombra suave proyectada bajo el sujeto flotante
                mShadowVerts[idx * 2] = mPosX[idx] + fgShiftX * 0.75f + 2.0f;
                mShadowVerts[idx * 2 + 1] = mPosY[idx] + fgShiftY * 0.75f + 6.0f;

                float speedSq = mVelX[idx] * mVelX[idx] + mVelY[idx] * mVelY[idx];
                totalKineticEnergy += speedSq;

                float disp = Math.abs(mPosX[idx] - mRestX[idx]) + Math.abs(mPosY[idx] - mRestY[idx]);
                if (disp > maxDisp) maxDisp = disp;
            }
        }

        // 6. Comprobar si la malla física ya llegó al reposo
        boolean hasActiveRipples = false;
        if (mWaterRippleEnabled) {
            for (int w = 0; w < MAX_RIPPLES; w++) {
                if (mRipples[w] != null && mRipples[w].active) {
                    hasActiveRipples = true;
                    break;
                }
            }
        }
        boolean canSleep = !hasActiveRipples && !mIsTouching && !gyroMoving && !inertiaMoving && totalKineticEnergy < SLEEP_ENERGY_THRESHOLD && maxDisp < 1.0f;
        if (canSleep) {
            for (int i = 0; i < NUM_VERTICES; i++) {
                mPosX[i] = mRestX[i];
                mPosY[i] = mRestY[i];
                mVelX[i] = 0f;
                mVelY[i] = 0f;
                int r = i / (COLS + 1);
                int c = i % (COLS + 1);
                float fracY = r / (float) ROWS;
                float fracX = c / (float) COLS;
                float edgeFactor = (float) Math.pow(Math.sin(fracX * Math.PI) * Math.sin(fracY * Math.PI), 0.35);
                mVerts[i * 2] = mRestX[i] + transX * edgeFactor * 0.45f;
                mVerts[i * 2 + 1] = mRestY[i] + transY * edgeFactor * 0.45f;
                float fgMult = 1.0f + mDepthSeparation * 2.2f;
                float fgShiftX = -transX * edgeFactor * fgMult;
                float fgShiftY = -transY * edgeFactor * fgMult;
                mForegroundVerts[i * 2] = mRestX[i] + fgShiftX;
                mForegroundVerts[i * 2 + 1] = mRestY[i] + fgShiftY;
                mShadowVerts[i * 2] = mRestX[i] + fgShiftX * 0.75f + 2.0f;
                mShadowVerts[i * 2 + 1] = mRestY[i] + fgShiftY * 0.75f + 6.0f;
            }
            mIsAsleep = true;
        } else {
            mIsAsleep = false;
        }

        // 7. Cálculo de sombra de profundidad (AO) y Foco de Luz / Brillo especular en el borde
        if (mAmbientOcclusionEnabled || mLightSourceEnabled) {
            updateAllColors();
        }

        if (mLightSourceEnabled && (mLightMode == 7 || mLightSpecularColumn)) {
            // El modo respiración y la columna de reflejo solar oceánico mantienen el destello de ondas vivo en tiempo real
            return true;
        }

        return !mIsAsleep;
    }

    public void updateAllColors() {
        if (!mAmbientOcclusionEnabled && !mLightSourceEnabled) {
            return;
        }

        float maxOffset = Math.min(mWidth, mHeight) * 0.08f;
        float cx = mWidth * 0.5f;
        float cy = mHeight * 0.5f;

        float effectiveAngle = mLightAngle;
        int solarTint = 0x00FCF9F2;

        float effectiveLightIntensity = mLightIntensity;

        // 1. Seguimiento solar en tiempo real (Solar Tracking continuo según hora local y ciclo circadiano)
        if (mLightSolarTracking) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            float totalDaySeconds = cal.get(java.util.Calendar.HOUR_OF_DAY) * 3600.0f
                    + cal.get(java.util.Calendar.MINUTE) * 60.0f
                    + cal.get(java.util.Calendar.SECOND)
                    + cal.get(java.util.Calendar.MILLISECOND) / 1000.0f;
            float dawnStartSec = 5.5f * 3600.0f; // 05:30 inicio del alba
            float sunriseSec = 6.5f * 3600.0f;   // 06:30 amanecer completo
            float sunsetSec = 18.5f * 3600.0f;   // 18:30 inicio del atardecer
            float duskEndSec = 19.8f * 3600.0f;  // 19:48 anochecer / puesta completa de sol

            if (totalDaySeconds >= dawnStartSec && totalDaySeconds <= duskEndSec) {
                // Diurno continuo: 05:30 (Amanecer / Este 350°) -> 12:45 (Cenit 270°) -> 19:48 (Atardecer / Oeste 190°)
                float dayFrac = (totalDaySeconds - dawnStartSec) / (duskEndSec - dawnStartSec); // 0.0 a 1.0 continuo
                effectiveAngle = (350.0f - dayFrac * 160.0f + 360.0f) % 360.0f;

                // Suavizado de encendido gradual al amanecer y apagado completo al anochecer
                if (totalDaySeconds < sunriseSec) {
                    float dawnFade = (totalDaySeconds - dawnStartSec) / (sunriseSec - dawnStartSec);
                    effectiveLightIntensity *= Math.max(0.0f, Math.min(1.0f, dawnFade));
                } else if (totalDaySeconds > sunsetSec) {
                    float duskFade = 1.0f - (totalDaySeconds - sunsetSec) / (duskEndSec - sunsetSec);
                    effectiveLightIntensity *= Math.max(0.0f, Math.min(1.0f, duskFade));
                }

                if (dayFrac < 0.20f) {
                    float f = dayFrac / 0.20f;
                    // Amanecer dorado cálido suave
                    int r = 255;
                    int g = (int) (165 + (225 - 165) * f);
                    int b = (int) (60 + (160 - 60) * f);
                    solarTint = (r << 16) | (g << 8) | b;
                } else if (dayFrac < 0.70f) {
                    float f = (dayFrac - 0.20f) / 0.50f;
                    // Mediodía blanco radiante solar puro
                    int r = 255;
                    int g = (int) (225 + (255 - 225) * (1.0f - Math.abs(f - 0.5f) * 2.0f));
                    int b = (int) (160 + (245 - 160) * (1.0f - Math.abs(f - 0.5f) * 2.0f));
                    solarTint = (r << 16) | (g << 8) | b;
                } else {
                    float f = (dayFrac - 0.70f) / 0.30f;
                    // Atardecer dorado naranja intenso como en la foto
                    int r = 255;
                    int g = (int) (210 - (210 - 145) * f);
                    int b = (int) (150 - (150 - 28) * f);
                    solarTint = (r << 16) | (g << 8) | b;
                }
            } else {
                // Nocturno: De noche no hay luz de sol (resplandor solar completamente apagado)
                effectiveLightIntensity = 0.0f;
                effectiveAngle = 270.0f;
            }
        }

        if (mLightGyroParallax && (mLightMode != 6 || !mIsTouching)) {
            // Desplazar el ángulo de emisión del foco a lo largo del marco según la inclinación giroscópica
            effectiveAngle = (effectiveAngle - mCurrentRoll * 40.0f - mCurrentPitch * 40.0f + 360.0f) % 360.0f;
        }

        float rad = (float) Math.toRadians(effectiveAngle);
        float cosA = (float) Math.cos(rad);
        float sinA = (float) Math.sin(rad);
        float scaleX = Math.abs(cosA) > 1e-4f ? cx / Math.abs(cosA) : 1e9f;
        float scaleY = Math.abs(sinA) > 1e-4f ? cy / Math.abs(sinA) : 1e9f;
        float scale = Math.min(scaleX, scaleY);
        mLightSourceX = Math.max(0f, Math.min(mWidth, cx + cosA * scale));
        mLightSourceY = Math.max(0f, Math.min(mHeight, cy + sinA * scale));

        // Modo 6: Seguidor táctil (foco dinámico bajo el dedo)
        if (mLightMode == 6 && mIsTouching) {
            mLightSourceX = mTouchX;
            mLightSourceY = mTouchY;
        }

        // Modo 7: Pulso respiratorio
        float breathScale = 1.0f;
        if (mLightMode == 7) {
            breathScale = 0.55f + 0.45f * (float) Math.sin(mAnimTime * 2.4f);
        }

        float lightX = mLightSourceX;
        float lightY = mLightSourceY;

        float diag = (float) Math.hypot(mWidth, mHeight);
        float dirInwardX = (mLightMode == 6 && mIsTouching) ? 0f : -cosA;
        float dirInwardY = (mLightMode == 6 && mIsTouching) ? 0f : -sinA;

        // Vector de visión del observador modulado por la perspectiva tridimensional
        float viewVx = mLightGyroParallax ? -mCurrentRoll * 0.70f : 0f;
        float viewVy = mLightGyroParallax ? mCurrentPitch * 0.70f : 0f;
        float viewVz = 1.0f;

        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                int i = r * (COLS + 1) + c;
                float px = mVerts[i * 2];
                float py = mVerts[i * 2 + 1];
                float dispX = mPosX[i] - mRestX[i];
                float dispY = mPosY[i] - mRestY[i];

                // 1. Sombra de oclusión ambiental en áreas de tensión
                if (mAmbientOcclusionEnabled && mAoIntensity > 0.001f) {
                    float dispMag = (float) Math.hypot(dispX, dispY);
                    float strainFactor = Math.min(1.0f, dispMag / maxOffset);
                    float baseShade = 255.0f - strainFactor * (55.0f * mAoIntensity);
                    int shade = Math.min(255, Math.max(0, (int) baseShade));
                    mColors[i] = 0xFF000000 | (shade << 16) | (shade << 8) | shade;
                } else {
                    mColors[i] = 0xFFFFFFFF;
                }

                // 2. Reflejo especular Blinn-Phong, haz de luz y columna solar oceánica
                if (mLightSourceEnabled && effectiveLightIntensity > 0.001f) {
                    float dX = 0f;
                    float dY = 0f;
                    if (c > 0 && c < COLS) {
                        dX = (mPosX[i + 1] - mRestX[i + 1]) - (mPosX[i - 1] - mRestX[i - 1]);
                    }
                    if (r > 0 && r < ROWS) {
                        dY = (mPosY[i + (COLS + 1)] - mRestY[i + (COLS + 1)]) - (mPosY[i - (COLS + 1)] - mRestY[i - (COLS + 1)]);
                    }

                    float nx = -dX * 0.12f;
                    float ny = -dY * 0.12f;
                    float nz = 1.0f;
                    float invN = 1.0f / (float) Math.hypot(Math.hypot(nx, ny), nz);
                    nx *= invN; ny *= invN; nz *= invN;

                    float lx = lightX - px;
                    float ly = lightY - py;
                    float lz = 260.0f;
                    float dist = (float) Math.hypot(lx, ly);
                    float invL = 1.0f / (float) Math.hypot(dist, lz);
                    lx *= invL; ly *= invL; lz *= invL;

                    float hx = lx + viewVx;
                    float hy = ly + viewVy;
                    float hz = lz + viewVz;
                    float invH = 1.0f / (float) Math.hypot(Math.hypot(hx, hy), hz);
                    hx *= invH; hy *= invH; hz *= invH;

                    float dotNH = Math.max(0.0f, nx * hx + ny * hy + nz * hz);
                    float specExp = 2.0f + (mLightHardness * 22.0f); // 2.0 (mate difuso) a 24.0 (espejo pulido ultra nítido)
                    float spec = (float) Math.pow(dotNH, specExp) * effectiveLightIntensity;

                    // Haz cónico con apertura ajustable
                    float toVX = dist > 1e-4f ? (px - lightX) / dist : 0f;
                    float toVY = dist > 1e-4f ? (py - lightY) / dist : 0f;
                    float beamDot = (mLightMode == 6 && mIsTouching) ? 1.0f : Math.max(0.0f, toVX * dirInwardX + toVY * dirInwardY);
                    float beamExp = 2.5f / mLightBeamSpread;
                    float distFactor = Math.max(0.0f, 1.0f - dist / (diag * 0.85f));
                    float raySheen = (float) Math.pow(beamDot, beamExp) * distFactor * effectiveLightIntensity;

                    float edgeGlow = (float) Math.pow(Math.max(0.0f, 1.0f - dist / (Math.min(mWidth, mHeight) * 0.80f)), 2.0) * effectiveLightIntensity;

                    // 3. Columna de reflejo solar (Sun Glitter Path / Estilo Océano Atardecer)
                    float columnGlitter = 0f;
                    if (mLightSpecularColumn && effectiveAngle >= 180.0f && effectiveAngle <= 360.0f) {
                        float distLateral = Math.abs(px - lightX);
                        float verticalDepth = Math.max(0f, py - lightY);
                        float columnWidth = (45.0f + verticalDepth * 0.22f) * mLightBeamSpread;
                        if (distLateral < columnWidth) {
                            float colFalloff = (float) Math.cos((distLateral / columnWidth) * (Math.PI * 0.5));
                            float shimmerFrequency = mLightShimmerSpeed * 5.0f;
                            float rippleShimmer = 0.5f + 0.5f * (float) Math.sin((py * 0.08f) + (dispY * 0.15f) + mAnimTime * shimmerFrequency);
                            columnGlitter = colFalloff * (0.4f + 0.6f * rippleShimmer) * (float) Math.pow(dotNH, specExp * 0.5f) * effectiveLightIntensity;
                        }
                    }

                    float combinedSpec = 0f;
                    int rgbColor = mLightSolarTracking ? solarTint : 0x00FCF9F2;

                    switch (mLightMode) {
                        case 0: // 0: Cristal Diamante Puro
                            combinedSpec = Math.min(1.0f, spec * 0.85f + raySheen * 0.20f + columnGlitter * 0.40f);
                            if (!mLightSolarTracking) rgbColor = 0x00F8FAFC;
                            break;
                        case 1: // 1: Destello Solar Dorado
                            combinedSpec = Math.min(1.0f, raySheen * 0.50f + edgeGlow * 0.40f + spec * 0.35f + columnGlitter * 0.85f);
                            if (!mLightSolarTracking) rgbColor = 0x00FFD269;
                            break;
                        case 3: // 3: Prisma Holográfico Iridiscente
                            combinedSpec = Math.min(1.0f, spec * 0.50f + raySheen * 0.30f + edgeGlow * 0.35f + columnGlitter * 0.45f);
                            float hue = (effectiveAngle + (nx + ny) * 160.0f + 360.0f) % 360.0f;
                            rgbColor = Color.HSVToColor(new float[]{ hue, 0.60f, 1.0f }) & 0x00FFFFFF;
                            break;
                        case 4: // 4: Neón Cyberpunk (Cian & Magenta)
                            combinedSpec = Math.min(1.0f, spec * 0.55f + raySheen * 0.30f + edgeGlow * 0.40f + columnGlitter * 0.50f);
                            float factor = Math.max(0f, Math.min(1f, (nx + 1.0f) * 0.5f));
                            int rVal = (int) (0 * (1f - factor) + 255 * factor);
                            int gVal = (int) (240 * (1f - factor) + 30 * factor);
                            int bVal = (int) (255 * (1f - factor) + 180 * factor);
                            rgbColor = (rVal << 16) | (gVal << 8) | bVal;
                            break;
                        case 5: // 5: Aurora Boreal Esmeralda
                            combinedSpec = Math.min(1.0f, spec * 0.50f + raySheen * 0.40f + edgeGlow * 0.45f + columnGlitter * 0.50f);
                            float aFactor = Math.max(0f, Math.min(1f, (ny + 1.0f) * 0.5f));
                            int arR = (int) (20 * (1f - aFactor) + 0 * aFactor);
                            int arG = (int) (255 * (1f - aFactor) + 210 * aFactor);
                            int arB = (int) (180 * (1f - aFactor) + 255 * aFactor);
                            rgbColor = (arR << 16) | (arG << 8) | arB;
                            break;
                        case 6: // 6: Seguidor Táctil Linterna
                            combinedSpec = Math.min(1.0f, spec * 0.65f + edgeGlow * 0.65f);
                            if (!mLightSolarTracking) rgbColor = 0x00E0F2FE;
                            break;
                        case 7: // 7: Pulso Respiratorio Orgánico
                            combinedSpec = Math.min(1.0f, (spec * 0.40f + raySheen * 0.35f + edgeGlow * 0.45f + columnGlitter * 0.50f) * breathScale);
                            if (!mLightSolarTracking) rgbColor = 0x00FEF3C7;
                            break;
                        case 2: // 2: Híbrido Luxe (Defecto)
                        default:
                            combinedSpec = Math.min(1.0f, spec * 0.40f + raySheen * 0.35f + edgeGlow * 0.45f + columnGlitter * 0.70f);
                            if (!mLightSolarTracking) rgbColor = 0x00FCF9F2;
                            break;
                    }

                    int alpha = (int) (combinedSpec * 220.0f);
                    alpha = Math.min(240, Math.max(0, alpha));
                    mSpecularColors[i] = (alpha << 24) | rgbColor;
                } else {
                    mSpecularColors[i] = 0x00000000;
                }
            }
        }
    }

    public float[] getVertices() {
        return mVerts;
    }

    public int[] getColors() {
        return (mAmbientOcclusionEnabled || mLightSourceEnabled) ? mColors : null;
    }

    public boolean isAsleep() {
        return mIsAsleep;
    }
}
