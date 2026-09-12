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
 * - Horizontal pill shape with white/theme outer frame and terminal cap on the right.
 * - Semi-translucent cavity background so battery shape and remaining capacity is visible.
 * - Dynamic inner level progress fill (green in charging, yellow in power save, red in low battery, white normal).
 * - Large, prominent white charging lightning bolt with dark outline.
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

    // Outer frame paint (always uses fillColor/status bar tint - white/dark, NOT green)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frameColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Positive terminal cap on right (always uses fillColor/status bar tint)
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frameColor
        style = Paint.Style.FILL_AND_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Empty cavity background paint so unfilled portion is visible
    private val cavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // Inner battery level fill paint (colored green in charging, yellow in power save, etc.)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    // Bolt paint for charging indicator (large and white)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isDither = true
    }

    // Bolt stroke paint for sharp contrast matching HyperOS design
    private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
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
        val isDark = Color.valueOf(singleToneColor).luminance() < 0.5
        val neutralColor = if (isDark) Color.BLACK else Color.WHITE

        val isColored = isTintColored(singleToneColor)
        val outerColor = if (isColored) neutralColor else singleToneColor
        framePaint.color = outerColor
        capPaint.color = outerColor
        cavityPaint.color = if (isDark) Color.argb(30, 0, 0, 0) else Color.argb(45, 255, 255, 255)
        invalidateSelf()
    }

    private fun isTintColored(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return kotlin.math.abs(r - g) > 25 || kotlin.math.abs(g - b) > 25 || kotlin.math.abs(r - b) > 25
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
        val capWidth = max(1.5f * density, width * 0.06f)
        val capHeight = max(4.0f * density, height * 0.42f)
        val rightMargin = capWidth + 1.2f * density

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
            frameRect.right + 0.8f * density,
            capTop,
            frameRect.right + 0.8f * density + capWidth,
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

        // Generate large, prominent HyperOS lightning bolt path centered on the battery body
        val centerX = frameRect.centerX()
        val centerY = frameRect.centerY()
        val boltW = height * 0.85f
        val boltH = height * 1.35f

        boltPath.reset()
        boltPath.moveTo(centerX + boltW * 0.12f, centerY - boltH * 0.50f)
        boltPath.lineTo(centerX - boltW * 0.50f, centerY + boltH * 0.05f)
        boltPath.lineTo(centerX - boltW * 0.05f, centerY + boltH * 0.05f)
        boltPath.lineTo(centerX - boltW * 0.12f, centerY + boltH * 0.50f)
        boltPath.lineTo(centerX + boltW * 0.50f, centerY - boltH * 0.05f)
        boltPath.lineTo(centerX + boltW * 0.05f, centerY - boltH * 0.05f)
        boltPath.close()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val cornerRadius = frameRect.height() / 2.8f
        val capCornerRadius = capRect.width() / 2f
        val innerCornerRadius = max(1.5f * density, innerCavityRect.height() / 3.0f)

        // 1. Draw outer pill frame (ALWAYS white / theme color, NEVER green)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)

        // 2. Draw terminal cap (ALWAYS white / theme color)
        canvas.drawRoundRect(capRect, capCornerRadius, capCornerRadius, capPaint)

        // 3. Draw empty cavity background so uncharged part is clearly visible
        canvas.drawRoundRect(innerCavityRect, innerCornerRadius, innerCornerRadius, cavityPaint)

        // 4. Determine inner fill color based on state
        val activeFillColor = when {
            charging -> COLOR_CHARGING_GREEN
            powerSaveEnabled -> COLOR_POWERSAVE_YELLOW
            batteryLevel <= 15 -> COLOR_CRITICAL_RED
            else -> fillColor
        }
        fillPaint.color = activeFillColor

        // 5. Calculate dynamic fill width according to percentage
        val availableWidth = innerCavityRect.width()
        val currentFillWidth = (availableWidth * (batteryLevel.coerceIn(0, 100) / 100f)).coerceIn(0f, availableWidth)

        if (currentFillWidth > 0f) {
            levelRect.set(
                innerCavityRect.left,
                innerCavityRect.top,
                innerCavityRect.left + currentFillWidth,
                innerCavityRect.bottom
            )
            canvas.drawRoundRect(levelRect, innerCornerRadius, innerCornerRadius, fillPaint)
        }

        // 6. Draw prominent charging bolt if plugged in
        if (charging) {
            canvas.drawPath(boltPath, boltStrokePaint)
            canvas.drawPath(boltPath, boltPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        framePaint.alpha = alpha
        capPaint.alpha = alpha
        cavityPaint.alpha = alpha
        fillPaint.alpha = alpha
        boltPaint.alpha = alpha
        boltStrokePaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        framePaint.colorFilter = colorFilter
        capPaint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (26 * density).toInt()

    override fun getIntrinsicHeight(): Int = (13 * density).toInt()

    companion object {
        const val COLOR_CHARGING_GREEN = 0xFF34C759.toInt()
        const val COLOR_POWERSAVE_YELLOW = 0xFFF59E0B.toInt()
        const val COLOR_CRITICAL_RED = 0xFFEF4444.toInt()
    }
}
