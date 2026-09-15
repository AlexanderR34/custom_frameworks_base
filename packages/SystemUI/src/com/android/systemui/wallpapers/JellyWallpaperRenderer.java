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

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class JellyWallpaperRenderer {
    private static final String TAG = "JellyWallpaperRenderer";

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "    vTexCoord = aTexCoord;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}\n";

    private final JellyMesh mMesh = new JellyMesh();
    private int mProgram = 0;
    private int mPositionHandle = 0;
    private int mTexCoordHandle = 0;
    private int mSamplerHandle = 0;
    private int mTextureId = 0;

    private int mVboPosition = 0;
    private int mVboTexCoord = 0;
    private int mEboIndex = 0;

    private int mWidth = 0;
    private int mHeight = 0;
    private Bitmap mPendingBitmap = null;

    public void onSurfaceCreated() {
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) {
            Log.e(TAG, "Failed to create shader program");
            return;
        }

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        mTexCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord");
        mSamplerHandle = GLES20.glGetUniformLocation(mProgram, "uTexture");

        int[] buffers = new int[3];
        GLES20.glGenBuffers(3, buffers, 0);
        mVboPosition = buffers[0];
        mVboTexCoord = buffers[1];
        mEboIndex = buffers[2];

        // VBO Coordenadas de textura (estáticas)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboTexCoord);
        FloatBuffer tcBuf = mMesh.getTexCoordBuffer();
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, tcBuf.capacity() * 4, tcBuf, GLES20.GL_STATIC_DRAW);

        // EBO Índices (estáticos)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mEboIndex);
        ShortBuffer idxBuf = mMesh.getIndexBuffer();
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idxBuf.capacity() * 2, idxBuf, GLES20.GL_STATIC_DRAW);

        // VBO Posiciones (dinámicas)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboPosition);
        FloatBuffer vBuf = mMesh.getVertexBuffer();
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vBuf.capacity() * 4, vBuf, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        if (mPendingBitmap != null && !mPendingBitmap.isRecycled()) {
            updateBitmap(mPendingBitmap);
            mPendingBitmap = null;
        }
    }

    public void onSurfaceChanged(int width, int height) {
        mWidth = width;
        mHeight = height;
        GLES20.glViewport(0, 0, width, height);
    }

    public void updateBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return;

        if (mProgram == 0) {
            mPendingBitmap = bitmap;
            return;
        }

        if (mTextureId == 0) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTextureId = textures[0];
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public boolean renderFrame(float dt) {
        boolean meshActive = mMesh.stepPhysics(dt);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (mTextureId == 0 || mProgram == 0) return meshActive;

        GLES20.glUseProgram(mProgram);

        // Actualizar VBO de posiciones con la física actual
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboPosition);
        FloatBuffer vBuf = mMesh.getVertexBuffer();
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, vBuf.capacity() * 4, vBuf);

        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        // UVs
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVboTexCoord);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        // Textura
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);
        GLES20.glUniform1i(mSamplerHandle, 0);

        // Dibujar elementos triangulares de la malla
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mEboIndex);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mMesh.getIndexCount(), GLES20.GL_UNSIGNED_SHORT, 0);

        // Desvincular buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return meshActive;
    }

    public void onTouchEvent(int action, float screenX, float screenY) {
        if (mWidth <= 0 || mHeight <= 0) return;
        // Convertir de pixeles de pantalla a coordenadas normalizadas NDC [-1.0, 1.0]
        float ndcX = (screenX / mWidth) * 2.0f - 1.0f;
        float ndcY = 1.0f - (screenY / mHeight) * 2.0f;

        switch (action) {
            case android.view.MotionEvent.ACTION_DOWN:
                mMesh.onTouchDown(ndcX, ndcY);
                break;
            case android.view.MotionEvent.ACTION_MOVE:
                mMesh.onTouchMove(ndcX, ndcY);
                break;
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                mMesh.onTouchUp();
                break;
        }
    }

    public boolean isAsleep() {
        return mMesh.isAsleep();
    }

    public void release() {
        if (mTextureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{mTextureId}, 0);
            mTextureId = 0;
        }
        if (mVboPosition != 0) {
            GLES20.glDeleteBuffers(3, new int[]{mVboPosition, mVboTexCoord, mEboIndex}, 0);
            mVboPosition = 0;
            mVboTexCoord = 0;
            mEboIndex = 0;
        }
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
            mProgram = 0;
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vShader == 0 || fShader == 0) return 0;

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vShader);
        GLES20.glAttachShader(program, fShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + type + ": " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}
