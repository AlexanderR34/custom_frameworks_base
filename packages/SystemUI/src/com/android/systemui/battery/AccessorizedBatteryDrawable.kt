/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.DrawableWrapper
import android.util.PathParser
import com.android.settingslib.graph.HyperOSBatteryDrawable
import com.android.settingslib.graph.ThemedBatteryDrawable
import com.android.systemui.res.R
import com.android.systemui.battery.BatterySpecs.BATTERY_HEIGHT
import com.android.systemui.battery.BatterySpecs.BATTERY_HEIGHT_WITH_SHIELD
import com.android.systemui.battery.BatterySpecs.BATTERY_WIDTH
import com.android.systemui.battery.BatterySpecs.BATTERY_WIDTH_WITH_SHIELD
import com.android.systemui.battery.BatterySpecs.SHIELD_LEFT_OFFSET
import com.android.systemui.battery.BatterySpecs.SHIELD_STROKE
import com.android.systemui.battery.BatterySpecs.SHIELD_TOP_OFFSET

/**
 * A battery drawable that accessorizes [ThemedBatteryDrawable] or [HyperOSBatteryDrawable] with
 * additional information if necessary.
 *
 * For now, it adds a shield in the bottom-right corner when [displayShield] is true.
 */
class AccessorizedBatteryDrawable(
    private val context: Context,
    private val frameColor: Int,
) : DrawableWrapper(ThemedBatteryDrawable(context, frameColor)) {
    private val themedBatteryDrawable: ThemedBatteryDrawable
        get() = (drawable as? ThemedBatteryDrawable) ?: defaultThemedDrawable

    private val defaultThemedDrawable by lazy { ThemedBatteryDrawable(context, frameColor) }
    private var hyperOSBatteryDrawable: HyperOSBatteryDrawable? = null

    private var currentLevel: Int = 100
    private var isCharging: Boolean = false
    private var isPowerSave: Boolean = false
    private var fgColor: Int = frameColor
    private var bgColor: Int = Color.TRANSPARENT
    private var singleToneColor: Int = frameColor

    var isHyperOSStyle: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateActiveDrawable()
            }
        }

    var isMiuiStyle: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateActiveDrawable()
            }
        }

    private fun updateActiveDrawable() {
        if (isHyperOSStyle || isMiuiStyle) {
            if (hyperOSBatteryDrawable == null) {
                hyperOSBatteryDrawable = HyperOSBatteryDrawable(context, frameColor)
            }
            hyperOSBatteryDrawable?.miuiStyle = isMiuiStyle
            drawable = hyperOSBatteryDrawable
        } else {
            drawable = defaultThemedDrawable
        }
        syncProperties()
        updateSizes()
        postInvalidate()
    }

    private fun syncProperties() {
        if (isHyperOSStyle || isMiuiStyle) {
            hyperOSBatteryDrawable?.let {
                it.miuiStyle = isMiuiStyle
                it.setBatteryLevel(currentLevel)
                it.charging = isCharging
                it.powerSaveEnabled = isPowerSave
                it.setColors(fgColor, bgColor, singleToneColor)
            }
        } else {
            themedBatteryDrawable.setBatteryLevel(currentLevel)
            themedBatteryDrawable.charging = isCharging
            themedBatteryDrawable.powerSaveEnabled = isPowerSave
            themedBatteryDrawable.setColors(fgColor, bgColor, singleToneColor)
        }
    }

    private val shieldPath = Path()
    private val scaledShield = Path()
    private val scaleMatrix = Matrix()

    private var shieldLeftOffsetScaled = SHIELD_LEFT_OFFSET
    private var shieldTopOffsetScaled = SHIELD_TOP_OFFSET

    private var density = context.resources.displayMetrics.density

    private val dualTone =
        context.resources.getBoolean(com.android.internal.R.bool.config_batterymeterDualTone)

    private val shieldTransparentOutlinePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
            p.color = Color.TRANSPARENT
            p.strokeWidth = ThemedBatteryDrawable.PROTECTION_MIN_STROKE_WIDTH
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            p.style = Paint.Style.FILL_AND_STROKE
        }

    private val shieldPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).also { p ->
            p.color = Color.MAGENTA
            p.style = Paint.Style.FILL
            p.isDither = true
        }

    init {
        loadPaths()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateSizes()
    }

    var displayShield: Boolean = false
        set(value) {
            field = value
            postInvalidate()
        }

    private fun updateSizes() {
        val b = bounds
        if (b.isEmpty) {
            return
        }

        if (isHyperOSStyle || isMiuiStyle) {
            drawable?.setBounds(b.left, b.top, b.right, b.bottom)
            return
        }

        val mainWidth = BatterySpecs.getMainBatteryWidth(b.width().toFloat(), displayShield)
        val mainHeight = BatterySpecs.getMainBatteryHeight(b.height().toFloat(), displayShield)

        drawable?.setBounds(
            b.left,
            b.top,
            /* right= */ b.left + mainWidth.toInt(),
            /* bottom= */ b.top + mainHeight.toInt()
        )

        if (displayShield) {
            val sx = b.right / BATTERY_WIDTH_WITH_SHIELD
            val sy = b.bottom / BATTERY_HEIGHT_WITH_SHIELD
            scaleMatrix.setScale(sx, sy)
            shieldPath.transform(scaleMatrix, scaledShield)

            shieldLeftOffsetScaled = sx * SHIELD_LEFT_OFFSET
            shieldTopOffsetScaled = sy * SHIELD_TOP_OFFSET

            val scaledStrokeWidth =
                (sx * SHIELD_STROKE).coerceAtLeast(
                    ThemedBatteryDrawable.PROTECTION_MIN_STROKE_WIDTH
                )
            shieldTransparentOutlinePaint.strokeWidth = scaledStrokeWidth
        }
    }

    override fun getIntrinsicHeight(): Int {
        if (isHyperOSStyle || isMiuiStyle) {
            return hyperOSBatteryDrawable?.intrinsicHeight ?: (13 * density).toInt()
        }
        val height =
            if (displayShield) {
                BATTERY_HEIGHT_WITH_SHIELD
            } else {
                BATTERY_HEIGHT
            }
        return (height * density).toInt()
    }

    override fun getIntrinsicWidth(): Int {
        if (isHyperOSStyle || isMiuiStyle) {
            return hyperOSBatteryDrawable?.intrinsicWidth ?: (19 * density).toInt()
        }
        val width =
            if (displayShield) {
                BATTERY_WIDTH_WITH_SHIELD
            } else {
                BATTERY_WIDTH
            }
        return (width * density).toInt()
    }

    override fun draw(c: Canvas) {
        if (isHyperOSStyle || isMiuiStyle) {
            drawable?.draw(c)
            return
        }

        c.saveLayer(null, null)
        // Draw the main battery icon
        super.draw(c)

        if (displayShield) {
            c.translate(shieldLeftOffsetScaled, shieldTopOffsetScaled)
            // We need a transparent outline around the shield, so first draw the transparent-ness
            // then draw the shield
            c.drawPath(scaledShield, shieldTransparentOutlinePaint)
            c.drawPath(scaledShield, shieldPaint)
        }
        c.restore()
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }

    override fun setAlpha(p0: Int) {
        drawable?.alpha = p0
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        super.setColorFilter(colorFilter)
        shieldPaint.colorFilter = colorFilter
        drawable?.colorFilter = colorFilter
    }

    /** Sets whether the battery is currently charging. */
    fun setCharging(charging: Boolean) {
        isCharging = charging
        if (isHyperOSStyle) {
            hyperOSBatteryDrawable?.charging = charging
        } else {
            themedBatteryDrawable.charging = charging
        }
    }

    /** Returns whether the battery is currently charging. */
    fun getCharging(): Boolean {
        return isCharging
    }

    /** Sets the current level (out of 100) of the battery. */
    fun setBatteryLevel(level: Int) {
        currentLevel = level
        if (isHyperOSStyle) {
            hyperOSBatteryDrawable?.setBatteryLevel(level)
        } else {
            themedBatteryDrawable.setBatteryLevel(level)
        }
    }

    /** Sets whether power save is enabled. */
    fun setPowerSaveEnabled(powerSaveEnabled: Boolean) {
        isPowerSave = powerSaveEnabled
        if (isHyperOSStyle) {
            hyperOSBatteryDrawable?.powerSaveEnabled = powerSaveEnabled
        } else {
            themedBatteryDrawable.powerSaveEnabled = powerSaveEnabled
        }
    }

    /** Returns whether power save is currently enabled. */
    fun getPowerSaveEnabled(): Boolean {
        return isPowerSave
    }

    /** Sets the colors to use for the icon. */
    fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        this.fgColor = fgColor
        this.bgColor = bgColor
        this.singleToneColor = singleToneColor
        shieldPaint.color = if (dualTone) fgColor else singleToneColor
        if (isHyperOSStyle) {
            hyperOSBatteryDrawable?.setColors(fgColor, bgColor, singleToneColor)
        } else {
            themedBatteryDrawable.setColors(fgColor, bgColor, singleToneColor)
        }
    }

    /** Notifies this drawable that the density might have changed. */
    fun notifyDensityChanged() {
        density = context.resources.displayMetrics.density
        syncProperties()
    }

    private fun loadPaths() {
        val shieldPathString = context.resources.getString(R.string.config_batterymeterShieldPath)
        shieldPath.set(PathParser.createPathFromPathData(shieldPathString))
    }

    private val invalidateRunnable: () -> Unit = { invalidateSelf() }

    private fun postInvalidate() {
        unscheduleSelf(invalidateRunnable)
        scheduleSelf(invalidateRunnable, 0)
    }
}
