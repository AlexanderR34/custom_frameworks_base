/*
 * Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.android.systemui.res.R
import kotlin.math.roundToInt

/**
 * Native Vector-based HyperOS Battery Meter Drawable.
 * Directly renders official Xiaomi HyperOS vector assets extracted from firmware:
 * - Solid Style: battery_meter_bg, battery_meter_progress_*, battery_meter_charging, etc.
 * - Performance / Ultimate Mode: battery_meter_bg_performance, battery_meter_progress_performance (Blue #007AFF)
 * - Hollow Style: hollow_battery_meter_normal, hollow_battery_meter_progress_*, etc.
 *
 * Implements high-fidelity dynamic clipping, charging bolt overlay, status bar tinting,
 * ultimate/performance chevrons, and low-battery/power-save color states.
 */
class HyperOSOfficialVectorBatteryDrawable(
    private val context: Context,
    frameColor: Int
) : Drawable() {

    private val density: Float = context.resources.displayMetrics.density

    // Current State
    private var batteryLevel: Int = 100
    private var singleToneColor: Int = frameColor
    private var fgColor: Int = frameColor
    private var bgColor: Int = Color.TRANSPARENT

    var charging: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var isFastCharging: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var powerSaveEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var isPerformanceMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var isHollowStyle: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var isMiuiStyle: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var windows11Style: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    var showPercent: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    // Official Vector Drawables Cache - Solid HyperOS Normal & Charging
    private val bgSolidNormal: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_bg)?.mutate() }
    private val bgSolidCharge: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_bg_charge)?.mutate() }
    private val bgSolidQuickCharge: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_bg_quick_charge)?.mutate() }

    private val progressSolidNormal: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_normal)?.mutate() }
    private val progressSolidCharging: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_charging)?.mutate() }
    private val progressSolidQuickCharging: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_quick_charging)?.mutate() }
    private val progressSolidLow: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_low)?.mutate() }
    private val progressSolidPowerSave: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_power_save)?.mutate() }

    // Official Vector Drawables Cache - Solid HyperOS Performance / Ultimate Mode
    private val bgSolidPerformance: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_bg_performance)?.mutate() }
    private val bgSolidPerformanceCharge: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_bg_performance_charge)?.mutate() }
    private val bgSolidPerformanceQuickCharge: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_bg_performance_quick_charge)?.mutate() }
    private val progressSolidPerformance: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_performance)?.mutate() }
    private val progressSolidPerformanceCharging: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_performance_charging)?.mutate() }
    private val progressSolidPerformanceQuickCharging: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.battery_meter_progress_performance_quick_charging)?.mutate() }

    // Official Vector Drawables Cache - Hollow HyperOS
    private val bgHollowNormal: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_normal)?.mutate() }
    private val bgHollowCharge: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_charging)?.mutate() }
    private val bgHollowQuickCharge: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_quick_charging)?.mutate() }
    private val bgHollowPerformance: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_performance)?.mutate() }
    private val bgHollowPerformanceMark: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_performance_mark)?.mutate() }

    private val progressHollowNormal: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_progress_normal)?.mutate() }
    private val progressHollowCharging: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_progress_charging)?.mutate() }
    private val progressHollowLow: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_progress_low)?.mutate() }
    private val progressHollowPowerSave: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_progress_power_save)?.mutate() }
    private val progressHollowPerformance: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_progress_performance)?.mutate() }
    private val progressHollowPerformanceCharging: Drawable? by lazy { ContextCompat.getDrawable(context, R.drawable.hollow_battery_meter_progress_performance_charging)?.mutate() }

    // Text Paint for Inner Percentage (Hollow & MIUI styles)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun setBatteryLevel(level: Int) {
        val clamped = level.coerceIn(0, 100)
        if (batteryLevel != clamped) {
            batteryLevel = clamped
            invalidateSelf()
        }
    }

    fun getBatteryLevel(): Int = batteryLevel

    fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        this.fgColor = fgColor
        this.bgColor = bgColor
        this.singleToneColor = singleToneColor
        invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int {
        val baseWidth = if (isHollowStyle || (isMiuiStyle && showPercent)) 23.5f else 19f
        return (baseWidth * density).roundToInt()
    }

    override fun getIntrinsicHeight(): Int {
        return (13.5f * density).roundToInt()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val b = bounds
        val bWidth = b.width().toFloat()
        val bHeight = b.height().toFloat()

        // 1. Select Active Background & Progress Drawables
        val activeBg: Drawable?
        val activeProgress: Drawable?

        if (isHollowStyle) {
            activeBg = when {
                charging && isFastCharging -> bgHollowQuickCharge ?: bgHollowCharge
                charging -> bgHollowCharge
                isPerformanceMode -> bgHollowPerformance ?: bgHollowNormal
                else -> bgHollowNormal
            }
            activeProgress = when {
                charging && isPerformanceMode -> progressHollowPerformanceCharging ?: progressHollowCharging
                charging -> progressHollowCharging
                powerSaveEnabled -> progressHollowPowerSave
                batteryLevel <= 15 -> progressHollowLow
                isPerformanceMode -> progressHollowPerformance
                else -> progressHollowNormal
            }
        } else {
            // Solid HyperOS
            activeBg = when {
                isPerformanceMode && charging && isFastCharging -> bgSolidPerformanceQuickCharge ?: bgSolidQuickCharge
                isPerformanceMode && charging -> bgSolidPerformanceCharge ?: bgSolidCharge
                isPerformanceMode -> bgSolidPerformance ?: bgSolidNormal
                charging && isFastCharging -> bgSolidQuickCharge ?: bgSolidCharge
                charging -> bgSolidCharge
                else -> bgSolidNormal
            }
            activeProgress = when {
                isPerformanceMode && charging && isFastCharging -> progressSolidPerformanceQuickCharging ?: progressSolidQuickCharging
                isPerformanceMode && charging -> progressSolidPerformanceCharging ?: progressSolidCharging
                isPerformanceMode -> progressSolidPerformance
                charging && isFastCharging -> progressSolidQuickCharging ?: progressSolidCharging
                charging -> progressSolidCharging
                powerSaveEnabled -> progressSolidPowerSave
                batteryLevel <= 15 -> progressSolidLow
                else -> progressSolidNormal
            }
        }

        // 2. Draw Shell / Background Vector
        activeBg?.let { bg ->
            bg.bounds = b
            bg.setTint(singleToneColor)
            bg.draw(canvas)
        }

        // 2b. In Hollow Performance Mode, draw the double chevron mark
        if (isHollowStyle && isPerformanceMode && !charging) {
            bgHollowPerformanceMark?.let { mark ->
                mark.bounds = b
                mark.setTint(singleToneColor)
                mark.draw(canvas)
            }
        }

        // 3. Draw Dynamic Progress Fill (Clipped by Percentage)
        if (batteryLevel > 0 && activeProgress != null) {
            val clipEndPx: Float
            if (isHollowStyle) {
                // Hollow viewport: X in [2.6, 25.594], total viewport width 28.0
                val startRatio = 2.6f / 28.0f
                val fillRatio = (25.594f - 2.6f) / 28.0f
                clipEndPx = b.left + bWidth * (startRatio + fillRatio * (batteryLevel / 100f))
            } else {
                // Solid viewport: X in [4.954, 19.105], total viewport width 28.0
                val startRatio = 4.954f / 28.0f
                val fillRatio = (19.105f - 4.954f) / 28.0f
                clipEndPx = b.left + bWidth * (startRatio + fillRatio * (batteryLevel / 100f))
            }

            canvas.save()
            canvas.clipRect(b.left.toFloat(), b.top.toFloat(), clipEndPx, b.bottom.toFloat())
            activeProgress.bounds = b

            if (!isPerformanceMode) {
                activeProgress.setTint(singleToneColor)
            }
            activeProgress.draw(canvas)
            canvas.restore()
        }

        // 4. Draw Inner Percentage Text for Hollow or MIUI styles (when not charging)
        if ((isHollowStyle || isMiuiStyle) && showPercent && !charging) {
            val isColorAccent = powerSaveEnabled || batteryLevel <= 15 || isPerformanceMode
            textPaint.color = if (isColorAccent) Color.WHITE else singleToneColor
            textPaint.textSize = bHeight * 0.54f
            val fm = textPaint.fontMetrics
            val baseline = b.centerY() - (fm.ascent + fm.descent) / 2f
            // Cap terminal is on the right side (~17% width), center text slightly to left of geometric center
            val textCenterX = b.left + bWidth * 0.44f
            canvas.drawText("$batteryLevel", textCenterX, baseline, textPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        bgSolidNormal?.alpha = alpha
        bgSolidCharge?.alpha = alpha
        bgSolidQuickCharge?.alpha = alpha
        bgSolidPerformance?.alpha = alpha
        bgSolidPerformanceCharge?.alpha = alpha
        bgSolidPerformanceQuickCharge?.alpha = alpha
        progressSolidNormal?.alpha = alpha
        progressSolidCharging?.alpha = alpha
        progressSolidQuickCharging?.alpha = alpha
        progressSolidLow?.alpha = alpha
        progressSolidPowerSave?.alpha = alpha
        progressSolidPerformance?.alpha = alpha
        progressSolidPerformanceCharging?.alpha = alpha
        progressSolidPerformanceQuickCharging?.alpha = alpha
        bgHollowNormal?.alpha = alpha
        bgHollowCharge?.alpha = alpha
        bgHollowQuickCharge?.alpha = alpha
        bgHollowPerformance?.alpha = alpha
        bgHollowPerformanceMark?.alpha = alpha
        progressHollowNormal?.alpha = alpha
        progressHollowCharging?.alpha = alpha
        progressHollowLow?.alpha = alpha
        progressHollowPowerSave?.alpha = alpha
        progressHollowPerformance?.alpha = alpha
        progressHollowPerformanceCharging?.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgSolidNormal?.colorFilter = colorFilter
        bgSolidCharge?.colorFilter = colorFilter
        bgSolidQuickCharge?.colorFilter = colorFilter
        bgSolidPerformance?.colorFilter = colorFilter
        bgSolidPerformanceCharge?.colorFilter = colorFilter
        bgSolidPerformanceQuickCharge?.colorFilter = colorFilter
        progressSolidNormal?.colorFilter = colorFilter
        progressSolidCharging?.colorFilter = colorFilter
        progressSolidQuickCharging?.colorFilter = colorFilter
        progressSolidLow?.colorFilter = colorFilter
        progressSolidPowerSave?.colorFilter = colorFilter
        progressSolidPerformance?.colorFilter = colorFilter
        progressSolidPerformanceCharging?.colorFilter = colorFilter
        progressSolidPerformanceQuickCharging?.colorFilter = colorFilter
        bgHollowNormal?.colorFilter = colorFilter
        bgHollowCharge?.colorFilter = colorFilter
        bgHollowQuickCharge?.colorFilter = colorFilter
        bgHollowPerformance?.colorFilter = colorFilter
        bgHollowPerformanceMark?.colorFilter = colorFilter
        progressHollowNormal?.colorFilter = colorFilter
        progressHollowCharging?.colorFilter = colorFilter
        progressHollowLow?.colorFilter = colorFilter
        progressHollowPowerSave?.colorFilter = colorFilter
        progressHollowPerformance?.colorFilter = colorFilter
        progressHollowPerformanceCharging?.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
