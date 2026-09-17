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
    private float mDragRadiusRatio = 0.22f;
    private float mDragRadius = 240.0f;
    private float mDragElasticity = 0.70f;
    private static final float SLEEP_ENERGY_THRESHOLD = 0.15f;

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

    // Buffer plano de vértices para Canvas.drawBitmapMesh [x0, y0, x1, y1, ...]
    private final float[] mVerts = new float[NUM_VERTICES * 2];
    private final float[] mForegroundVerts = new float[NUM_VERTICES * 2];
    private float mDepthSeparation = 0.60f;

    public void setDepthSeparation(float separation) {
        mDepthSeparation = Math.max(0.0f, Math.min(1.0f, separation));
    }

    public float[] getForegroundVertices() {
        return mForegroundVerts;
    }

    // Buffer de colores para iluminación / sombra de oclusión ambiental (Faux 3D Ambient Occlusion)
    // y foco de luz / brillo especular en el borde de la pantalla (Specular Light Sheen)
    private final int[] mColors = new int[NUM_VERTICES];
    private boolean mAmbientOcclusionEnabled = true;
    private boolean mLightSourceEnabled = false;
    private float mLightAngle = 45.0f;
    private float mLightIntensity = 0.75f;

    public void setLightSource(boolean enabled, float angleDegrees) {
        setLightSource(enabled, angleDegrees, mLightIntensity);
    }

    public void setLightSource(boolean enabled, float angleDegrees, float intensity) {
        mLightSourceEnabled = enabled;
        mLightAngle = angleDegrees;
        mLightIntensity = Math.max(0.0f, Math.min(1.0f, intensity));
        updateAllColors();
        mIsAsleep = false;
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
                mColors[idx] = 0xFFFFFFFF;
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
        mSurfaceMassRatio = Math.max(0.2f, Math.min(2.5f, mass / 50.0f));
        mWaveSpeedRatio = Math.max(0.2f, Math.min(2.5f, waveSpeed / 70.0f));
        mKInternal = Math.max(25.0f, mKAnchor * 0.45f * mInternalTensionRatio);
    }

    public void setPhysicsParams(float stiffness, float damping, float touchRadius) {
        mKAnchor = Math.max(120.0f, stiffness * 4.5f);
        mKInternal = Math.max(25.0f, stiffness * 2.0f * mInternalTensionRatio);
        mDamping = Math.max(4.0f, damping * 1.8f);
        mDragRadiusRatio = Math.max(0.08f, touchRadius * 0.65f);
        mDragRadius = Math.min(mWidth, mHeight) * mDragRadiusRatio;
    }

    public void triggerRipple(float originX, float originY, float strength) {
        float sigma = mDragRadius * 0.75f;
        float twoSigmaSq = 2.0f * sigma * sigma;
        float maxVel = mWidth * strength * 0.05f * mWaveSpeedRatio;

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
        float wavelength = mWidth * 0.18f;
        float sigma = mWidth * 0.45f;
        float twoSigmaSq = 2.0f * sigma * sigma;
        float maxAmp = mWidth * 0.055f * intensity;

        for (int i = 0; i < NUM_VERTICES; i++) {
            float dx = mPosX[i] - originX;
            float dy = mPosY[i] - originY;
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 1.0f) {
                float normX = dx / dist;
                float normY = dy / dist;
                float sinPhase = (float) Math.sin((dist / wavelength) * Math.PI * 2.0);
                float envelope = (float) Math.exp(-(dist * dist) / twoSigmaSq);
                float pulse = sinPhase * envelope * maxAmp;
                mVelX[i] += normX * pulse * 8.0f;
                mVelY[i] += normY * pulse * 8.0f;
            }
        }
        mIsAsleep = false;
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
        mIsAsleep = false;
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
    }

    public void onTouchUp() {
        if (!mIsTouching) return;
        mIsTouching = false;
        mActivePointerCount = 0;
        long duration = android.os.SystemClock.uptimeMillis() - mTouchStartTime;
        float dragVecX = mTouchStartX - mTouchX;
        float dragVecY = mTouchStartY - mTouchY;
        float totalDist = (float) Math.hypot(dragVecX, dragVecY);

        // Solo se activa la onda de choque si fue un tap rápido y estático (sin arrastrar el dedo)
        if (mTapShockwaveEnabled && duration < 240 && totalDist < 16.0f) {
            triggerTapShockwave(mTouchX, mTouchY, 1.0f);
            if (mHapticListener != null) {
                mHapticListener.onStretchTick();
            }
        } else if (totalDist >= 16.0f) {
            // Potente impulso elástico de retroceso al soltar (Slingshot Snap Recoil)
            float normX = dragVecX / totalDist;
            float normY = dragVecY / totalDist;
            float recoilSpeed = Math.min(mWidth * 0.65f, totalDist * 3.2f) * mSnapBackRecoilRatio;
            float twoSigmaSq = 2.0f * mDragRadius * mDragRadius * 2.2f;

            for (int i = 0; i < NUM_VERTICES; i++) {
                float dx = mPosX[i] - mTouchX;
                float dy = mPosY[i] - mTouchY;
                float distSq = dx * dx + dy * dy;
                if (distSq < mDragRadius * mDragRadius * 3.5f) {
                    float weight = (float) Math.exp(-distSq / twoSigmaSq);
                    mVelX[i] += normX * recoilSpeed * weight * 10.0f;
                    mVelY[i] += normY * recoilSpeed * weight * 10.0f;
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
        if (mIsAsleep) return false;

        dt = Math.min(dt, 0.033f);

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
        float unitPinchX = pinchDist > 1.0f ? pinchDx / pinchDist : 1.0f;
        float unitPinchY = pinchDist > 1.0f ? pinchDy / pinchDist : 0f;
        float perpPinchX = -unitPinchY;
        float perpPinchY = unitPinchX;

        float totalKineticEnergy = 0f;
        float maxDisp = 0f;
        float twoSigmaSq = 2.0f * mDragRadius * mDragRadius;
        float maxOffset = mWidth * 0.22f * mMaxStretchRatio;
        float invMass = 1.0f / mSurfaceMassRatio;

        for (int r = 0; r <= ROWS; r++) {
            float fracY = r / (float) ROWS;
            float edgeY = (float) Math.sin(fracY * Math.PI);

            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;
                float fracX = c / (float) COLS;
                float edgeX = (float) Math.sin(fracX * Math.PI);

                float px = mPosX[idx];
                float py = mPosY[idx];

                // 1. Fuerza de retorno elástico a reposo (anclaje firme) + Inercia global
                float fx = -mKAnchor * (px - mRestX[idx]) + currentInertiaX;
                float fy = -mKAnchor * (py - mRestY[idx]) + currentInertiaY;

                // 2. Fuerzas elásticas internas con vecinos
                if (c > 0) {
                    int left = idx - 1;
                    fx += -mKInternal * (px - mPosX[left] - (mRestX[idx] - mRestX[left]));
                    fy += -mKInternal * (py - mPosY[left] - (mRestY[idx] - mRestY[left]));
                }
                if (c < COLS) {
                    int right = idx + 1;
                    fx += -mKInternal * (px - mPosX[right] - (mRestX[idx] - mRestX[right]));
                    fy += -mKInternal * (py - mPosY[right] - (mRestY[idx] - mRestY[right]));
                }
                if (r > 0) {
                    int top = idx - (COLS + 1);
                    fx += -mKInternal * (px - mPosX[top] - (mRestX[idx] - mRestX[top]));
                    fy += -mKInternal * (py - mPosY[top] - (mRestY[idx] - mRestY[top]));
                }
                if (r < ROWS) {
                    int bottom = idx + (COLS + 1);
                    fx += -mKInternal * (px - mPosX[bottom] - (mRestX[idx] - mRestX[bottom]));
                    fy += -mKInternal * (py - mPosY[bottom] - (mRestY[idx] - mRestY[bottom]));
                }

                // 3. Arrastre táctil continuo (sigue y deforma en tiempo real bajo la posición activa del dedo)
                if (mIsTouching) {
                    float dx0 = mRestX[idx] - mTouchX;
                    float dy0 = mRestY[idx] - mTouchY;
                    float distSq0 = dx0 * dx0 + dy0 * dy0;
                    if (distSq0 < mDragRadius * mDragRadius * 2.5f) {
                        float weight0 = (float) Math.exp(-distSq0 / twoSigmaSq);
                        float targetX = mRestX[idx] + totalDragX * weight0;
                        float targetY = mRestY[idx] + totalDragY * weight0;
                        fx += (targetX - px) * (mKAnchor * 4.5f) * weight0;
                        fy += (targetY - py) * (mKAnchor * 4.5f) * weight0;
                    }

                    if (mActivePointerCount >= 2 && mMultiTouchEnabled) {
                        float dx1 = mRestX[idx] - mTouchX1;
                        float dy1 = mRestY[idx] - mTouchY1;
                        float distSq1 = dx1 * dx1 + dy1 * dy1;
                        if (distSq1 < mDragRadius * mDragRadius * 2.5f) {
                            float weight1 = (float) Math.exp(-distSq1 / twoSigmaSq);
                            float targetX1 = mRestX[idx] + totalDragX1 * weight1;
                            float targetY1 = mRestY[idx] + totalDragY1 * weight1;
                            fx += (targetX1 - px) * (mKAnchor * 4.5f) * weight1;
                            fy += (targetY1 - py) * (mKAnchor * 4.5f) * weight1;
                        }

                        // Tensión y deformación de pellizco / estiramiento entre ambos dedos
                        float initPinchDist = (float) Math.hypot(mTouchStartX1 - mTouchStartX, mTouchStartY1 - mTouchStartY);
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
                                    float targetMidX = mRestX[idx] + midDragX * bridgeWeight;
                                    float targetMidY = mRestY[idx] + midDragY * bridgeWeight;
                                    fx += (targetMidX - px) * (mKAnchor * 3.5f) * bridgeWeight;
                                    fy += (targetMidY - py) * (mKAnchor * 3.5f) * bridgeWeight;
                                }
                            }
                        }
                    }
                }

                // 4. Amortiguamiento
                fx -= mDamping * mVelX[idx];
                fy -= mDamping * mVelY[idx];

                // 5. Integración numérica con masa superficial
                mVelX[idx] += fx * dt * invMass;
                mVelY[idx] += fy * dt * invMass;

                mPosX[idx] += mVelX[idx] * dt;
                mPosY[idx] += mVelY[idx] * dt;

                // Límite elástico de deformación para evitar distorsiones excesivas
                float dispX = mPosX[idx] - mRestX[idx];
                float dispY = mPosY[idx] - mRestY[idx];
                if (Math.abs(dispX) > maxOffset) {
                    mPosX[idx] = mRestX[idx] + Math.signum(dispX) * maxOffset;
                    mVelX[idx] *= 0.3f;
                }
                if (Math.abs(dispY) > maxOffset) {
                    mPosY[idx] = mRestY[idx] + Math.signum(dispY) * maxOffset;
                    mVelY[idx] *= 0.3f;
                }

                // Fijar los bordes exteriores al marco de la pantalla para evitar distorsión de bordes
                if (c == 0) {
                    mPosX[idx] = 0f;
                    mVelX[idx] = 0f;
                } else if (c == COLS) {
                    mPosX[idx] = mWidth;
                    mVelX[idx] = 0f;
                }
                if (r == 0) {
                    mPosY[idx] = 0f;
                    mVelY[idx] = 0f;
                } else if (r == ROWS) {
                    mPosY[idx] = mHeight;
                    mVelY[idx] = 0f;
                }

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

                float speedSq = mVelX[idx] * mVelX[idx] + mVelY[idx] * mVelY[idx];
                totalKineticEnergy += speedSq;

                float disp = Math.abs(dispX) + Math.abs(dispY);
                if (disp > maxDisp) maxDisp = disp;
            }
        }

        // 6. Cálculo de sombra de profundidad (AO) y Foco de Luz / Brillo especular en el borde
        if (mAmbientOcclusionEnabled || mLightSourceEnabled) {
            updateAllColors();
        }

        if (!mIsTouching && !gyroMoving && !inertiaMoving && totalKineticEnergy < SLEEP_ENERGY_THRESHOLD && maxDisp < 1.0f) {
            for (int i = 0; i < NUM_VERTICES; i++) {
                mPosX[i] = mRestX[i];
                mPosY[i] = mRestY[i];
                mVelX[i] = 0f;
                mVelY[i] = 0f;
                int r = i / (COLS + 1);
                int c = i % (COLS + 1);
                float fracY = r / (float) ROWS;
                float fracX = c / (float) COLS;
                float edgeFactor = (float) Math.pow(Math.sin(fracX * Math.PI) * Math.sin(fracY * Math.PI), 0.4);
                mVerts[i * 2] = mRestX[i] + transX * edgeFactor * 0.35f;
                mVerts[i * 2 + 1] = mRestY[i] + transY * edgeFactor * 0.35f;
                mForegroundVerts[i * 2] = mRestX[i] - transX * edgeFactor * (1.0f + mDepthSeparation * 1.5f);
                mForegroundVerts[i * 2 + 1] = mRestY[i] - transY * edgeFactor * (1.0f + mDepthSeparation * 1.5f);
            }
            updateAllColors();
            mIsAsleep = true;
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
        float halfW = cx;
        float halfH = cy;
        float rad = (float) Math.toRadians(mLightAngle);
        float cosA = (float) Math.cos(rad);
        float sinA = (float) Math.sin(rad);
        float scaleX = Math.abs(cosA) > 1e-4f ? halfW / Math.abs(cosA) : 1e9f;
        float scaleY = Math.abs(sinA) > 1e-4f ? halfH / Math.abs(sinA) : 1e9f;
        float scale = Math.min(scaleX, scaleY);
        float lightX = cx + cosA * scale;
        float lightY = cy + sinA * scale;

        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                int i = r * (COLS + 1) + c;
                float px = mPosX[i];
                float py = mPosY[i];
                float dispX = px - mRestX[i];
                float dispY = py - mRestY[i];

                float baseShade = 255.0f;
                if (mAmbientOcclusionEnabled && mAoIntensity > 0.001f) {
                    float dispMag = (float) Math.hypot(dispX, dispY);
                    float strainFactor = Math.min(1.0f, dispMag / maxOffset);
                    baseShade = 255.0f - strainFactor * (45.0f * mAoIntensity);
                }

                if (mLightSourceEnabled && mLightIntensity > 0.001f) {
                    // Calcular vector normal de la superficie deformada usando vecinos
                    float dX = 0f;
                    float dY = 0f;
                    if (c > 0 && c < COLS) {
                        dX = (mPosX[i + 1] - mRestX[i + 1]) - (mPosX[i - 1] - mRestX[i - 1]);
                    }
                    if (r > 0 && r < ROWS) {
                        dY = (mPosY[i + (COLS + 1)] - mRestY[i + (COLS + 1)]) - (mPosY[i - (COLS + 1)] - mRestY[i - (COLS + 1)]);
                    }

                    float nx = -dX * 0.08f;
                    float ny = -dY * 0.08f;
                    float nz = 1.0f;
                    float invN = 1.0f / (float) Math.hypot(Math.hypot(nx, ny), nz);
                    nx *= invN; ny *= invN; nz *= invN;

                    // Vector de rayo de luz incidente hacia este punto
                    float lx = lightX - px;
                    float ly = lightY - py;
                    float lz = 320.0f;
                    float invL = 1.0f / (float) Math.hypot(Math.hypot(lx, ly), lz);
                    lx *= invL; ly *= invL; lz *= invL;

                    // Vector intermedio (Half Vector) para reflejo especular de Blinn-Phong
                    float hx = lx;
                    float hy = ly;
                    float hz = lz + 1.0f; // Vista hacia cámara (0, 0, 1)
                    float invH = 1.0f / (float) Math.hypot(Math.hypot(hx, hy), hz);
                    hx *= invH; hy *= invH; hz *= invH;

                    float dotNH = Math.max(0.0f, nx * hx + ny * hy + nz * hz);
                    // Brillo especular concentrado en crestas y pliegues donde impacta el rayo
                    float spec = (float) Math.pow(dotNH, 24.0) * (45.0f * mLightIntensity);

                    // Reflejo direccional suave que barre la superficie
                    float dirRay = Math.max(0.0f, (lx * cosA + ly * sinA));
                    float raySheen = (float) Math.pow(dirRay, 4.0) * (18.0f * mLightIntensity);

                    float finalShade = baseShade - (1.0f - mLightIntensity * 0.3f) * 10.0f + spec + raySheen;
                    int shadeVal = Math.min(255, Math.max(0, (int) finalShade));
                    mColors[i] = 0xFF000000 | (shadeVal << 16) | (shadeVal << 8) | shadeVal;
                } else {
                    int shade = Math.min(255, Math.max(0, (int) baseShade));
                    mColors[i] = 0xFF000000 | (shade << 16) | (shade << 8) | shade;
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
