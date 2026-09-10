/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.graph

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * HyperOS-style Battery Meter Drawable.
 *
 * Visual design:
 * - Horizontal pill shape with rounded outer frame and terminal cap on the right.
 * - Inner progress fill that smoothly reduces from right to left as battery discharges.
 * - Dynamic color states:
 *   - Normal: Tinted with status bar icon color (adaptive white / dark).
 *   - Charging: Vibrant green fill with a centered white lightning bolt.
 *   - Power Saver: Amber / Yellow fill.
 *   - Low Battery (<= 15%): Warning red fill.
 */
class HyperOSBatteryDrawable(
    private val context: Context,
    frameColor: Int
) : Drawable() {

    private var density = context.resources.displayMetrics.density
    private var batteryLevel = 100
    private var fillColor = frameColor
    private var backgroundColor = Color.TRANSPARENT

    var charging = false
        set(value) {
            field = value
            invalidateSelf()
        }

    var powerSaveEnabled = false
        set(value) {
            field = value
            invalidateSelf()
        }

    // Outer frame paint
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frameColor
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Positive terminal cap on right
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frameColor
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Inner battery level fill paint
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    // Bolt paint for charging indicator
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isDither = true
    }

    // Bolt stroke paint for contrast if needed
    private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
        strokeJoin = Paint.Join.ROUND
    }

    private val frameRect = RectF()
    private val innerCavityRect = RectF()
    private val levelRect = RectF()
    private val capRect = RectF()
    private val boltPath = Path()

    fun setBatteryLevel(level: Int) {
        batteryLevel = level.coerceIn(0, 100)
        invalidateSelf()
    }

    fun getBatteryLevel(): Int = batteryLevel

    fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        fillColor = singleToneColor
        backgroundColor = bgColor
        framePaint.color = fillColor
        capPaint.color = fillColor
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateGeometry()
    }

    private fun updateGeometry() {
        val b = bounds
        if (b.isEmpty) return

        val width = b.width().toFloat()
        val height = b.height().toFloat()

        val stroke = framePaint.strokeWidth
        val halfStroke = stroke / 2f
        val capWidth = max(1.5f * density, width * 0.05f)
        val capHeight = max(4.0f * density, height * 0.38f)
        val rightMargin = capWidth + 1.0f * density

        // Battery outer pill frame
        frameRect.set(
            b.left + halfStroke + 0.5f * density,
            b.top + halfStroke + 0.5f * density,
            b.right - rightMargin - halfStroke,
            b.bottom - halfStroke - 0.5f * density
        )

        // Terminal cap on right side
        val capTop = b.top + (height - capHeight) / 2f
        capRect.set(
            frameRect.right + 1.0f * density,
            capTop,
            frameRect.right + 1.0f * density + capWidth,
            capTop + capHeight
        )

        // Inner cavity for fill
        val inset = stroke + 1.2f * density
        innerCavityRect.set(
            frameRect.left + inset,
            frameRect.top + inset,
            frameRect.right - inset,
            frameRect.bottom - inset
        )

        // Generate lightning bolt path centered in the battery
        val centerX = innerCavityRect.centerX()
        val centerY = innerCavityRect.centerY()
        val boltW = innerCavityRect.height() * 0.55f
        val boltH = innerCavityRect.height() * 0.85f

        boltPath.reset()
        // Standard sleek charging lightning bolt
        boltPath.moveTo(centerX + boltW * 0.1f, centerY - boltH * 0.5f)
        boltPath.lineTo(centerX - boltW * 0.5f, centerY + boltH * 0.05f)
        boltPath.lineTo(centerX - boltW * 0.05f, centerY + boltH * 0.05f)
        boltPath.lineTo(centerX - boltW * 0.1f, centerY + boltH * 0.5f)
        boltPath.lineTo(centerX + boltW * 0.5f, centerY - boltH * 0.05f)
        boltPath.lineTo(centerX + boltW * 0.05f, centerY - boltH * 0.05f)
        boltPath.close()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val cornerRadius = frameRect.height() / 2.8f
        val capCornerRadius = capRect.width() / 2f
        val innerCornerRadius = max(1.5f * density, innerCavityRect.height() / 3.2f)

        // 1. Draw outer pill frame
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)

        // 2. Draw terminal cap
        canvas.drawRoundRect(capRect, capCornerRadius, capCornerRadius, capPaint)

        // 3. Determine fill color based on state
        val activeFillColor = when {
            charging -> COLOR_CHARGING_GREEN
            powerSaveEnabled -> COLOR_POWERSAVE_YELLOW
            batteryLevel <= 15 -> COLOR_CRITICAL_RED
            else -> fillColor
        }
        fillPaint.color = activeFillColor

        // 4. Calculate dynamic fill width according to percentage
        val availableWidth = innerCavityRect.width()
        val currentFillWidth = (availableWidth * (batteryLevel / 100f)).coerceIn(0f, availableWidth)

        if (currentFillWidth > 0f) {
            levelRect.set(
                innerCavityRect.left,
                innerCavityRect.top,
                innerCavityRect.left + currentFillWidth,
                innerCavityRect.bottom
            )
            canvas.drawRoundRect(levelRect, innerCornerRadius, innerCornerRadius, fillPaint)
        }

        // 5. Draw charging bolt if plugged in
        if (charging) {
            canvas.drawPath(boltPath, boltStrokePaint)
            canvas.drawPath(boltPath, boltPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        framePaint.alpha = alpha
        capPaint.alpha = alpha
        fillPaint.alpha = alpha
        boltPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        framePaint.colorFilter = colorFilter
        capPaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (24 * density).toInt()

    override fun getIntrinsicHeight(): Int = (13 * density).toInt()

    companion object {
        // HyperOS Vibrant charging green (matches screenshot 2)
        const val COLOR_CHARGING_GREEN = 0xFF34C759.toInt()
        // HyperOS Amber / Yellow for power saver mode
        const val COLOR_POWERSAVE_YELLOW = 0xFFF59E0B.toInt()
        // Red warning for critical level
        const val COLOR_CRITICAL_RED = 0xFFEF4444.toInt()
    }
}
