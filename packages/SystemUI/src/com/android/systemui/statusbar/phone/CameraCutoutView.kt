/*
 * Copyright (C) 2024-2026 The Android Open Source Project
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

package com.android.systemui.statusbar.phone

import android.content.Context
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View

/**
 * View that draws a customizable cutout behind/at the camera notch so that
 * screenshots always include the camera notch just like on iOS / iPhone.
 * Configurable with any color, on/off, style (solid vs ring), and customizable width, height, and Y offset.
 */
class CameraCutoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val SETTING_DRAW_CAMERA_CUTOUT = "status_bar_draw_camera_cutout"
        const val SETTING_CUTOUT_COLOR = "status_bar_camera_cutout_color"
        const val SETTING_CUTOUT_WIDTH = "status_bar_camera_cutout_width"
        const val SETTING_CUTOUT_HEIGHT = "status_bar_camera_cutout_height"
        const val SETTING_CUTOUT_OFFSET_X = "status_bar_camera_cutout_offset_x"
        const val SETTING_CUTOUT_OFFSET_Y = "status_bar_camera_cutout_offset_y"
        const val SETTING_CUTOUT_STYLE = "status_bar_camera_cutout_style"
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val cutoutRect = RectF()
    private var isCutoutEnabled = false
    private var customColor: Int = Color.BLACK
    private var customWidthDp: Int = 0
    private var customHeightDp: Int = 0
    private var customOffsetXDp: Int = 0
    private var customOffsetYDp: Int = 0
    private var customStyle: Int = 0

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            updateSettings()
        }
    }

    init {
        setWillNotDraw(false)
        updateSettings()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_DRAW_CAMERA_CUTOUT),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_CUTOUT_COLOR),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_CUTOUT_WIDTH),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_CUTOUT_HEIGHT),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_CUTOUT_OFFSET_X),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_CUTOUT_OFFSET_Y),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_CUTOUT_STYLE),
            false,
            settingsObserver,
            UserHandle.USER_ALL
        )
        updateSettings()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.contentResolver.unregisterContentObserver(settingsObserver)
    }

    fun updateSettings() {
        val resolver = context.contentResolver
        val enabled = Settings.System.getIntForUser(
            resolver,
            SETTING_DRAW_CAMERA_CUTOUT,
            0,
            UserHandle.USER_CURRENT
        ) == 1

        val colorStr = Settings.System.getStringForUser(
            resolver,
            SETTING_CUTOUT_COLOR,
            UserHandle.USER_CURRENT
        )
        val parsedColor = if (!TextUtils.isEmpty(colorStr)) {
            try {
                Color.parseColor(colorStr)
            } catch (e: Exception) {
                Color.BLACK
            }
        } else {
            Color.BLACK
        }

        val wDp = Settings.System.getIntForUser(
            resolver,
            SETTING_CUTOUT_WIDTH,
            0,
            UserHandle.USER_CURRENT
        )

        val hDp = Settings.System.getIntForUser(
            resolver,
            SETTING_CUTOUT_HEIGHT,
            0,
            UserHandle.USER_CURRENT
        )

        val offsetXDp = Settings.System.getIntForUser(
            resolver,
            SETTING_CUTOUT_OFFSET_X,
            0,
            UserHandle.USER_CURRENT
        )

        val offsetYDp = Settings.System.getIntForUser(
            resolver,
            SETTING_CUTOUT_OFFSET_Y,
            0,
            UserHandle.USER_CURRENT
        )

        val style = Settings.System.getIntForUser(
            resolver,
            SETTING_CUTOUT_STYLE,
            0,
            UserHandle.USER_CURRENT
        )

        var needInvalidate = false
        var needRelayout = false
        if (isCutoutEnabled != enabled) {
            isCutoutEnabled = enabled
            needInvalidate = true
            needRelayout = true
        }
        if (customColor != parsedColor) {
            customColor = parsedColor
            paint.color = parsedColor
            needInvalidate = true
        }
        if (customWidthDp != wDp) {
            customWidthDp = wDp
            needInvalidate = true
            needRelayout = true
        }
        if (customHeightDp != hDp) {
            customHeightDp = hDp
            needInvalidate = true
            needRelayout = true
        }
        if (customOffsetXDp != offsetXDp) {
            customOffsetXDp = offsetXDp
            needInvalidate = true
        }
        if (customOffsetYDp != offsetYDp) {
            customOffsetYDp = offsetYDp
            needInvalidate = true
        }
        if (customStyle != style) {
            customStyle = style
            needInvalidate = true
        }

        if (needInvalidate) {
            invalidate()
        }
        if (needRelayout) {
            post {
                requestLayout()
                (parent as? View)?.requestLayout()
            }
        }
    }

    fun isCutoutEnabled(): Boolean = isCutoutEnabled

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isCutoutEnabled) return

        // In landscape or reverse portrait, the camera hardware is on the left/right physical edge,
        // so drawing the notch in the top status bar is wrong. Only draw in portrait (ROTATION_0).
        val rotation = display?.rotation ?: android.view.Surface.ROTATION_0
        if (rotation != android.view.Surface.ROTATION_0) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density

        val targetW = if (customWidthDp > 0) {
            customWidthDp * density
        } else {
            Math.min(w, h)
        }

        val targetH = if (customHeightDp > 0) {
            customHeightDp * density
        } else {
            Math.min(w, h)
        }

        val cx = (w / 2f) + (customOffsetXDp * density)
        val cy = (h / 2f) + (customOffsetYDp * density)

        val strokeWidth = 2.5f * density
        if (customStyle == 1) {
            // Outline ring only
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            val halfStroke = strokeWidth / 2f
            val left = cx - targetW / 2f + halfStroke
            val top = cy - targetH / 2f + halfStroke
            val right = cx + targetW / 2f - halfStroke
            val bottom = cy + targetH / 2f - halfStroke
            val cornerRadius = Math.max(0f, Math.min(right - left, bottom - top) / 2f)
            cutoutRect.set(left, top, right, bottom)
            canvas.drawRoundRect(cutoutRect, cornerRadius, cornerRadius, paint)
        } else {
            // Solid fill
            paint.style = Paint.Style.FILL
            val left = cx - targetW / 2f
            val top = cy - targetH / 2f
            val right = cx + targetW / 2f
            val bottom = cy + targetH / 2f
            val cornerRadius = Math.min(targetW, targetH) / 2f
            cutoutRect.set(left, top, right, bottom)
            canvas.drawRoundRect(cutoutRect, cornerRadius, cornerRadius, paint)
        }
    }
}
