/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.lyrics

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Custom 5-row synchronized lyrics visualizer for the Lockscreen.
 * Replicates the YouTube Music style:
 * - Row 1: Exiting line, low opacity, faded.
 * - Row 2: Previous line, solid white.
 * - Row 3: Current active line, large, bright white with glow effect.
 * - Row 4: Next upcoming line, muted gray.
 * - Row 5: Incoming line, very low opacity.
 *
 * Dynamically handles start of song (first line on Row 3, Rows 1 & 2 empty)
 * and end of song (last line on Row 3, Rows 4 & 5 empty).
 */
class LockscreenLyricsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val row1: TextView
    private val row2: TextView
    private val row3: TextView
    private val row4: TextView
    private val row5: TextView

    private val rows: List<TextView>

    private var currentLyrics: LyricsData? = null
    private var lastIndex: Int = -2
    private var scrollAnimator: ValueAnimator? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        val paddingHorizontal = dpToPx(16f)
        val paddingVertical = dpToPx(8f)
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        // Fila 1: Texto de salida anterior (baja opacidad, desvanecido)
        row1 = createLyricRow(textSizeSp = 14f, alphaVal = 0.32f, isBold = false)

        // Fila 2: Texto anterior ya vocalizado (blanco nítido)
        row2 = createLyricRow(textSizeSp = 16f, alphaVal = 0.75f, isBold = false)

        // Fila 3: Texto vocal actual (línea activa con Glow / resplandor)
        row3 = createLyricRow(textSizeSp = 20f, alphaVal = 1.0f, isBold = true).apply {
            // Efecto Glow / Resplandor blanco
            setShadowLayer(18f, 0f, 0f, Color.WHITE)
        }

        // Fila 4: Siguiente texto en espera (gris translúcido)
        row4 = createLyricRow(textSizeSp = 16f, alphaVal = 0.50f, isBold = false)

        // Fila 5: Texto entrante (baja opacidad)
        row5 = createLyricRow(textSizeSp = 14f, alphaVal = 0.22f, isBold = false)

        rows = listOf(row1, row2, row3, row4, row5)
        for (row in rows) {
            addView(row)
        }
    }

    private fun createLyricRow(textSizeSp: Float, alphaVal: Float, isBold: Boolean): TextView {
        return TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(3f)
                bottomMargin = dpToPx(3f)
            }
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            setTextColor(Color.WHITE)
            alpha = alphaVal
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            text = ""
        }
    }

    /**
     * Updates the displayed lyrics according to the active playback index.
     */
    fun updateLyrics(lyrics: LyricsData?, newIndex: Int) {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            currentLyrics = null
            lastIndex = -2
            visibility = View.GONE
            return
        }

        currentLyrics = lyrics

        if (newIndex < 0) {
            // Before the song starts, position the first line in Row 3
            bindRows(0, animate = false)
            lastIndex = 0
            visibility = View.VISIBLE
            return
        }

        if (newIndex == lastIndex) {
            return
        }

        val shouldAnimate = lastIndex >= 0 && (newIndex == lastIndex + 1)
        lastIndex = newIndex
        visibility = View.VISIBLE

        bindRows(newIndex, animate = shouldAnimate)
    }

    private fun bindRows(index: Int, animate: Boolean) {
        val lines = currentLyrics?.lines ?: return
        val total = lines.size

        // Line 1: index - 2 (empty if before start)
        val text1 = if (index >= 2 && index - 2 < total) lines[index - 2].text else ""
        // Line 2: index - 1 (empty if before start)
        val text2 = if (index >= 1 && index - 1 < total) lines[index - 1].text else ""
        // Line 3: current active line
        val text3 = if (index in 0 until total) lines[index].text else ""
        // Line 4: index + 1 (empty if at end)
        val text4 = if (index + 1 in 0 until total) lines[index + 1].text else ""
        // Line 5: index + 2 (empty if at end)
        val text5 = if (index + 2 in 0 until total) lines[index + 2].text else ""

        if (animate) {
            scrollAnimator?.cancel()
            val shiftDistance = dpToPx(24f).toFloat()

            scrollAnimator = ValueAnimator.ofFloat(0f, -shiftDistance).apply {
                duration = 260
                interpolator = DecelerateInterpolator(1.4f)
                addUpdateListener { animator ->
                    val value = animator.animatedValue as Float
                    for (row in rows) {
                        row.translationY = value
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        for (row in rows) {
                            row.translationY = 0f
                        }
                        row1.text = text1
                        row2.text = text2
                        row3.text = text3
                        row4.text = text4
                        row5.text = text5
                    }
                })
                start()
            }
        } else {
            scrollAnimator?.cancel()
            for (row in rows) {
                row.translationY = 0f
            }
            row1.text = text1
            row2.text = text2
            row3.text = text3
            row4.text = text4
            row5.text = text5
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
