/*
 * Copyright (C) 2026 Project Diva
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

package com.android.systemui.statusbar.phone;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MonetAmbientGlowDrawable extends Drawable {

    private final Paint mOrb1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOrb2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOrb3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Context mContext;
    private int mColorPrimary;
    private int mColorSecondary;
    private int mColorTertiary;

    private float mProgress = 0f;
    private ValueAnimator mAnimator;

    public MonetAmbientGlowDrawable(Context context) {
        mContext = context;
        updateMonetColors();

        mAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        mAnimator.setDuration(9000L); // 9 seconds for smooth, visible ambient movement
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setRepeatMode(ValueAnimator.RESTART);
        mAnimator.addUpdateListener(anim -> {
            mProgress = (float) anim.getAnimatedValue();
            invalidateSelf();
        });
        mAnimator.start();
    }

    public void updateMonetColors() {
        if (mContext == null) return;
        final boolean isNight = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (isNight) {
            try {
                mColorPrimary = mContext.getColor(android.R.color.system_accent1_300);
            } catch (Exception e) {
                mColorPrimary = 0xFF8AB4F8;
            }
            try {
                mColorSecondary = mContext.getColor(android.R.color.system_accent2_300);
            } catch (Exception e) {
                mColorSecondary = 0xFFF093FB;
            }
            try {
                mColorTertiary = mContext.getColor(android.R.color.system_accent3_300);
            } catch (Exception e) {
                mColorTertiary = 0xFFFDE293;
            }
        } else {
            try {
                mColorPrimary = mContext.getColor(android.R.color.system_accent1_200);
            } catch (Exception e) {
                mColorPrimary = 0xFFD2E3FC;
            }
            try {
                mColorSecondary = mContext.getColor(android.R.color.system_accent2_200);
            } catch (Exception e) {
                mColorSecondary = 0xFFF8D7DA;
            }
            try {
                mColorTertiary = mContext.getColor(android.R.color.system_accent3_200);
            } catch (Exception e) {
                mColorTertiary = 0xFFFEF7E0;
            }
        }
    }

    public void startGlowAnimation() {
        if (mAnimator != null && !mAnimator.isRunning()) {
            mAnimator.start();
        }
    }

    public void stopGlowAnimation() {
        if (mAnimator != null && mAnimator.isRunning()) {
            mAnimator.cancel();
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        final int w = bounds.width();
        final int h = bounds.height();
        if (w <= 0 || h <= 0) return;

        final float radius = Math.max(w, h) * 0.45f;

        // 1. Point 1 (Monet Primary) - Smooth continuous orbit
        final float cx1 = bounds.left + w * 0.5f + (float) Math.cos(mProgress) * (w * 0.28f);
        final float cy1 = bounds.top + h * 0.25f + (float) Math.sin(mProgress) * (h * 0.15f);

        final int primaryAlpha = Color.argb(190, Color.red(mColorPrimary),
                Color.green(mColorPrimary), Color.blue(mColorPrimary));
        final int primaryTransparent = Color.argb(0, Color.red(mColorPrimary),
                Color.green(mColorPrimary), Color.blue(mColorPrimary));
        final RadialGradient grad1 = new RadialGradient(
                cx1, cy1, radius,
                primaryAlpha, primaryTransparent,
                Shader.TileMode.CLAMP
        );
        mOrb1Paint.setShader(grad1);
        canvas.drawCircle(cx1, cy1, radius, mOrb1Paint);

        // 2. Point 2 (Monet Secondary) - Counter-harmonic figure-8 loop (closed at 2*PI)
        final float cx2 = bounds.left + w * 0.5f + (float) Math.cos(-mProgress + Math.PI) * (w * 0.26f);
        final float cy2 = bounds.top + h * 0.45f + (float) Math.sin(2 * mProgress) * (h * 0.18f);

        final int secondaryAlpha = Color.argb(170, Color.red(mColorSecondary),
                Color.green(mColorSecondary), Color.blue(mColorSecondary));
        final int secondaryTransparent = Color.argb(0, Color.red(mColorSecondary),
                Color.green(mColorSecondary), Color.blue(mColorSecondary));
        final RadialGradient grad2 = new RadialGradient(
                cx2, cy2, radius,
                secondaryAlpha, secondaryTransparent,
                Shader.TileMode.CLAMP
        );
        mOrb2Paint.setShader(grad2);
        canvas.drawCircle(cx2, cy2, radius, mOrb2Paint);

        // 3. Point 3 (Monet Tertiary) - Elliptical Lissajous orbit (closed loop at 2*PI)
        final float cx3 = bounds.left + w * 0.5f + (float) Math.sin(2 * mProgress + Math.PI / 2) * (w * 0.32f);
        final float cy3 = bounds.top + h * 0.65f + (float) Math.cos(mProgress) * (h * 0.20f);

        final int tertiaryAlpha = Color.argb(160, Color.red(mColorTertiary),
                Color.green(mColorTertiary), Color.blue(mColorTertiary));
        final int tertiaryTransparent = Color.argb(0, Color.red(mColorTertiary),
                Color.green(mColorTertiary), Color.blue(mColorTertiary));
        final RadialGradient grad3 = new RadialGradient(
                cx3, cy3, radius,
                tertiaryAlpha, tertiaryTransparent,
                Shader.TileMode.CLAMP
        );
        mOrb3Paint.setShader(grad3);
        canvas.drawCircle(cx3, cy3, radius, mOrb3Paint);
    }

    @Override
    public void setAlpha(int alpha) {
        mOrb1Paint.setAlpha(alpha);
        mOrb2Paint.setAlpha(alpha);
        mOrb3Paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mOrb1Paint.setColorFilter(colorFilter);
        mOrb2Paint.setColorFilter(colorFilter);
        mOrb3Paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
