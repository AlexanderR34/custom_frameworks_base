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
 * Exact HyperOS-style Battery Meter Drawable.
 * Matches official Xiaomi HyperOS status bar battery design:
 * - Rounded rectangle capsule frame with terminal cap on right.
 * - Uniform inner margin between frame and level fill.
 * - Dynamic vibrant green fill (0xFF34C759) when charging.
 * - Sharp geometric white charging lightning bolt with solid black outline (Miter joins).
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

    // Outer frame paint (always uses fillColor/status bar tint - white/dark)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = frameColor
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
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

    // Empty cavity background paint
    private val cavityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.FILL
    }

    // Inner battery level fill paint (HyperOS green for charging)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isDither = true
    }

    // Sharp white interior of the lightning bolt
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isDither = true
    }

    // Solid black outline for the lightning bolt (sharp Miter join matching HyperOS)
    private val boltStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.SQUARE
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

        framePaint.color = neutralColor
        capPaint.color = neutralColor
        cavityPaint.color = if (isDark) Color.argb(30, 0, 0, 0) else Color.argb(40, 255, 255, 255)
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
        val capWidth = 1.8f * density
        val capHeight = max(5.6f * density, height * 0.50f)
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
            frameRect.right + 0.6f * density,
            capTop,
            frameRect.right + 0.6f * density + capWidth,
            capTop + capHeight
        )

        // Inner cavity for fill with uniform inset
        val inset = stroke + 1.1f * density
        innerCavityRect.set(
            frameRect.left + inset,
            frameRect.top + inset,
            frameRect.right - inset,
            frameRect.bottom - inset
        )

        // Sharp geometric HyperOS charging bolt centered on the battery body
        val centerX = frameRect.centerX()
        val centerY = frameRect.centerY()
        val boltH = height * 1.0f
        val boltW = height * 0.55f

        boltPath.reset()
        val topX = centerX + boltW * 0.14f
        val topY = centerY - boltH * 0.50f
        val midLeftX = centerX - boltW * 0.50f
        val midLeftY = centerY + boltH * 0.04f
        val notchLeftX = centerX - boltW * 0.04f
        val notchLeftY = centerY + boltH * 0.04f
        val botX = centerX - boltW * 0.18f
        val botY = centerY + boltH * 0.50f
        val midRightX = centerX + boltW * 0.50f
        val midRightY = centerY - boltH * 0.04f
        val notchRightX = centerX + boltW * 0.04f
        val notchRightY = centerY - boltH * 0.04f

        boltPath.moveTo(topX, topY)
        boltPath.lineTo(midLeftX, midLeftY)
        boltPath.lineTo(notchLeftX, notchLeftY)
        boltPath.lineTo(botX, botY)
        boltPath.lineTo(midRightX, midRightY)
        boltPath.lineTo(notchRightX, notchRightY)
        boltPath.close()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val cornerRadius = 3.2f * density
        val capCornerRadius = 1.2f * density
        val innerCornerRadius = 2.0f * density

        // 1. Draw outer pill frame (white / theme color)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, framePaint)

        // 2. Draw terminal cap
        canvas.drawRoundRect(capRect, capCornerRadius, capCornerRadius, capPaint)

        // 3. Draw empty cavity background
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

        // 6. Draw sharp HyperOS charging bolt (black outline + white fill)
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

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = (22 * density).toInt()

    override fun getIntrinsicHeight(): Int = (11.5f * density).toInt()

    companion object {
        const val COLOR_CHARGING_GREEN = 0xFF34C759.toInt()
        const val COLOR_POWERSAVE_YELLOW = 0xFFF59E0B.toInt()
        const val COLOR_CRITICAL_RED = 0xFFEF4444.toInt()
    }
}
