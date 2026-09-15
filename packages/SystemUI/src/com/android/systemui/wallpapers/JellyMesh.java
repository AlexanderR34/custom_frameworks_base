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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Simulación física de cuerpo elástico blando (soft-body spring-mass)
 * para deformación de textura tipo gelatina en OpenGL ES.
 */
public class JellyMesh {
    public static final int COLS = 32;
    public static final int ROWS = 32;
    private static final int NUM_VERTICES = (COLS + 1) * (ROWS + 1);

    // Parámetros de física de resortes y amortiguamiento
    private static final float K_ANCHOR = 135.0f;     // Rigidez de retorno a la posición original
    private static final float K_INTERNAL = 80.0f;   // Tensión elástica interna entre nodos contiguos
    private static final float DAMPING = 3.4f;        // Fricción/amortiguamiento de velocidad
    private static final float DRAG_RADIUS = 0.38f;   // Radio de influencia del arrastre en NDC [-1, 1]
    private static final float DRAG_FORCE = 200.0f;   // Fuerza aplicada según movimiento del dedo
    private static final float SLEEP_ENERGY_THRESHOLD = 0.00005f;

    // Coordenadas de reposo y dinámicas en NDC
    private final float[] mRestX = new float[NUM_VERTICES];
    private final float[] mRestY = new float[NUM_VERTICES];
    private final float[] mPosX = new float[NUM_VERTICES];
    private final float[] mPosY = new float[NUM_VERTICES];
    private final float[] mVelX = new float[NUM_VERTICES];
    private final float[] mVelY = new float[NUM_VERTICES];

    // Coordenadas de textura UV fijas
    private final float[] mTexCoords = new float[NUM_VERTICES * 2];

    // Buffers de OpenGL ES
    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mTexCoordBuffer;
    private final ShortBuffer mIndexBuffer;
    private final int mIndexCount;

    private boolean mIsTouching = false;
    private float mTouchX = 0f;
    private float mTouchY = 0f;
    private float mPrevTouchX = 0f;
    private float mPrevTouchY = 0f;
    private boolean mIsAsleep = true;

    public JellyMesh() {
        for (int r = 0; r <= ROWS; r++) {
            float v = (float) r / ROWS;
            float y = 1.0f - 2.0f * v; // NDC Y: [1.0 (top) -> -1.0 (bottom)]
            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;
                float u = (float) c / COLS;
                float x = -1.0f + 2.0f * u; // NDC X: [-1.0 (left) -> 1.0 (right)]

                mRestX[idx] = x;
                mRestY[idx] = y;
                mPosX[idx] = x;
                mPosY[idx] = y;
                mVelX[idx] = 0f;
                mVelY[idx] = 0f;

                mTexCoords[idx * 2] = u;
                mTexCoords[idx * 2 + 1] = v;
            }
        }

        mVertexBuffer = ByteBuffer.allocateDirect(NUM_VERTICES * 2 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        updateVertexBuffer();

        mTexCoordBuffer = ByteBuffer.allocateDirect(NUM_VERTICES * 2 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexCoordBuffer.put(mTexCoords).position(0);

        mIndexCount = ROWS * COLS * 6;
        mIndexBuffer = ByteBuffer.allocateDirect(mIndexCount * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                short i0 = (short) (r * (COLS + 1) + c);
                short i1 = (short) (i0 + 1);
                short i2 = (short) ((r + 1) * (COLS + 1) + c);
                short i3 = (short) (i2 + 1);

                // Triángulo 1
                mIndexBuffer.put(i0);
                mIndexBuffer.put(i2);
                mIndexBuffer.put(i1);

                // Triángulo 2
                mIndexBuffer.put(i1);
                mIndexBuffer.put(i2);
                mIndexBuffer.put(i3);
            }
        }
        mIndexBuffer.position(0);
    }

    public void onTouchDown(float ndcX, float ndcY) {
        mIsTouching = true;
        mTouchX = ndcX;
        mTouchY = ndcY;
        mPrevTouchX = ndcX;
        mPrevTouchY = ndcY;
        mIsAsleep = false;
    }

    public void onTouchMove(float ndcX, float ndcY) {
        mPrevTouchX = mTouchX;
        mPrevTouchY = mTouchY;
        mTouchX = ndcX;
        mTouchY = ndcY;
        mIsAsleep = false;
    }

    public void onTouchUp() {
        mIsTouching = false;
    }

    public boolean stepPhysics(float dt) {
        if (mIsAsleep) return false;

        // Limitar dt para estabilidad numérica
        dt = Math.min(dt, 0.033f);

        float deltaTouchX = mTouchX - mPrevTouchX;
        float deltaTouchY = mTouchY - mPrevTouchY;
        mPrevTouchX = mTouchX;
        mPrevTouchY = mTouchY;

        float totalKineticEnergy = 0f;
        float maxDisp = 0f;
        float twoSigmaSq = 2.0f * DRAG_RADIUS * DRAG_RADIUS;

        for (int r = 0; r <= ROWS; r++) {
            for (int c = 0; c <= COLS; c++) {
                int idx = r * (COLS + 1) + c;

                float px = mPosX[idx];
                float py = mPosY[idx];

                // 1. Fuerza de retorno (Hooke con anclaje de posición neutra)
                float fx = -K_ANCHOR * (px - mRestX[idx]);
                float fy = -K_ANCHOR * (py - mRestY[idx]);

                // 2. Fuerzas internas elásticas con nodos vecinos
                if (c > 0) { // Izquierda
                    int left = idx - 1;
                    fx += -K_INTERNAL * (px - mPosX[left] - (mRestX[idx] - mRestX[left]));
                    fy += -K_INTERNAL * (py - mPosY[left] - (mRestY[idx] - mRestY[left]));
                }
                if (c < COLS) { // Derecha
                    int right = idx + 1;
                    fx += -K_INTERNAL * (px - mPosX[right] - (mRestX[idx] - mRestX[right]));
                    fy += -K_INTERNAL * (py - mPosY[right] - (mRestY[idx] - mRestY[right]));
                }
                if (r > 0) { // Arriba
                    int top = idx - (COLS + 1);
                    fx += -K_INTERNAL * (px - mPosX[top] - (mRestX[idx] - mRestX[top]));
                    fy += -K_INTERNAL * (py - mPosY[top] - (mRestY[idx] - mRestY[top]));
                }
                if (r < ROWS) { // Abajo
                    int bottom = idx + (COLS + 1);
                    fx += -K_INTERNAL * (px - mPosX[bottom] - (mRestX[idx] - mRestX[bottom]));
                    fy += -K_INTERNAL * (py - mPosY[bottom] - (mRestY[idx] - mRestY[bottom]));
                }

                // 3. Arrastre táctil gaussiano
                if (mIsTouching) {
                    float dx = px - mTouchX;
                    float dy = py - mTouchY;
                    float distSq = dx * dx + dy * dy;
                    if (distSq < DRAG_RADIUS * DRAG_RADIUS) {
                        float weight = (float) Math.exp(-distSq / twoSigmaSq);
                        fx += weight * deltaTouchX * DRAG_FORCE;
                        fy += weight * deltaTouchY * DRAG_FORCE;
                    }
                }

                // 4. Amortiguamiento
                fx -= DAMPING * mVelX[idx];
                fy -= DAMPING * mVelY[idx];

                // 5. Integración numérica
                mVelX[idx] += fx * dt;
                mVelY[idx] += fy * dt;

                mPosX[idx] += mVelX[idx] * dt;
                mPosY[idx] += mVelY[idx] * dt;

                float speedSq = mVelX[idx] * mVelX[idx] + mVelY[idx] * mVelY[idx];
                totalKineticEnergy += speedSq;

                float disp = Math.abs(mPosX[idx] - mRestX[idx]) + Math.abs(mPosY[idx] - mRestY[idx]);
                if (disp > maxDisp) maxDisp = disp;
            }
        }

        // Suspender simulación si la energía cinética es despreciable
        if (!mIsTouching && totalKineticEnergy < SLEEP_ENERGY_THRESHOLD && maxDisp < 0.001f) {
            for (int i = 0; i < NUM_VERTICES; i++) {
                mPosX[i] = mRestX[i];
                mPosY[i] = mRestY[i];
                mVelX[i] = 0f;
                mVelY[i] = 0f;
            }
            mIsAsleep = true;
        }

        updateVertexBuffer();
        return !mIsAsleep;
    }

    private void updateVertexBuffer() {
        mVertexBuffer.position(0);
        for (int i = 0; i < NUM_VERTICES; i++) {
            mVertexBuffer.put(mPosX[i]);
            mVertexBuffer.put(mPosY[i]);
        }
        mVertexBuffer.position(0);
    }

    public FloatBuffer getVertexBuffer() {
        return mVertexBuffer;
    }

    public FloatBuffer getTexCoordBuffer() {
        return mTexCoordBuffer;
    }

    public ShortBuffer getIndexBuffer() {
        return mIndexBuffer;
    }

    public int getIndexCount() {
        return mIndexCount;
    }

    public boolean isAsleep() {
        return mIsAsleep;
    }
}
