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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import java.util.regex.Pattern

/**
 * Synchronized Lockscreen & AOD Lyrics Visualizer with word-by-word active vocal track glow highlight.
 *
 * Features:
 * - Positioned below the fingerprint sensor / bottom lockscreen area.
 * - Full AOD (Always-On Display) support: cleanly showcases the active vocal line on AOD.
 * - Row 1: Past line (-2), dim, small.
 * - Row 2: Past line (-1), medium, clean white.
 * - Row 3: Active vocal line: Large, bold, word-by-word synchronized vocal glow highlight.
 * - Row 4: Upcoming line (+1), muted translucent.
 * - Row 5: Upcoming line (+2), dim.
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
    private var isDozing: Boolean = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        val paddingHorizontal = dpToPx(16f)
        val paddingVertical = dpToPx(2f)
        setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)

        // Row 1: -2 (past line, small, subtle)
        row1 = createLyricRow(textSizeSp = 11f, alphaVal = 0.25f, isBold = false)

        // Row 2: -1 (previous vocalized line)
        row2 = createLyricRow(textSizeSp = 13f, alphaVal = 0.65f, isBold = false)

        // Row 3: Active vocal track line with word-by-word glow
        row3 = createLyricRow(textSizeSp = 16f, alphaVal = 1.0f, isBold = true).apply {
            setShadowLayer(14f, 0f, 0f, Color.argb(220, 255, 255, 255))
        }

        // Row 4: +1 (next upcoming line)
        row4 = createLyricRow(textSizeSp = 13f, alphaVal = 0.45f, isBold = false)

        // Row 5: +2 (incoming line)
        row5 = createLyricRow(textSizeSp = 11f, alphaVal = 0.20f, isBold = false)

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
                topMargin = dpToPx(1f)
                bottomMargin = dpToPx(1f)
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

    fun setDozing(dozing: Boolean) {
        if (isDozing == dozing) return
        isDozing = dozing
        if (dozing) {
            // AOD Mode: Clean single vocal line, optimal for OLED and battery
            row1.visibility = View.GONE
            row2.visibility = View.GONE
            row4.visibility = View.GONE
            row5.visibility = View.GONE
            row3.setShadowLayer(8f, 0f, 0f, Color.argb(180, 255, 255, 255))
        } else {
            // Lockscreen Mode: Restore full multi-row view and rich glow
            row3.setShadowLayer(14f, 0f, 0f, Color.argb(220, 255, 255, 255))
            currentLyrics?.let {
                if (lastIndex >= 0) {
                    bindRows(lastIndex, animate = false)
                }
            }
        }
    }

    /**
     * Updates the displayed lyrics and synchronizes active line progress word-by-word.
     */
    fun updateLyrics(lyrics: LyricsData?, newIndex: Int, currentPositionMs: Long = 0L) {
        if (lyrics == null || lyrics.lines.isEmpty()) {
            currentLyrics = null
            lastIndex = -2
            visibility = View.GONE
            return
        }

        currentLyrics = lyrics

        val indexToUse = if (newIndex < 0) 0 else newIndex

        if (indexToUse != lastIndex) {
            val shouldAnimate = !isDozing && lastIndex >= 0 && (indexToUse == lastIndex + 1)
            lastIndex = indexToUse
            bindRows(indexToUse, animate = shouldAnimate)
        }

        // Update word-by-word vocal sync on the active line (Row 3)
        updateActiveRowProgress(lyrics, indexToUse, currentPositionMs)

        visibility = View.VISIBLE
    }

    private fun bindRows(index: Int, animate: Boolean) {
        val lines = currentLyrics?.lines ?: return
        val total = lines.size

        val text1 = if (index >= 2 && index - 2 < total) lines[index - 2].text else ""
        val text2 = if (index >= 1 && index - 1 < total) lines[index - 1].text else ""
        val text4 = if (index + 1 in 0 until total) lines[index + 1].text else ""
        val text5 = if (index + 2 in 0 until total) lines[index + 2].text else ""

        if (animate) {
            scrollAnimator?.cancel()
            val shiftDistance = dpToPx(18f).toFloat()

            scrollAnimator = ValueAnimator.ofFloat(0f, -shiftDistance).apply {
                duration = 220
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
                        applyStaticRowTexts(text1, text2, text4, text5)
                    }
                })
                start()
            }
        } else {
            scrollAnimator?.cancel()
            for (row in rows) {
                row.translationY = 0f
            }
            applyStaticRowTexts(text1, text2, text4, text5)
        }
    }

    private fun applyStaticRowTexts(text1: String, text2: String, text4: String, text5: String) {
        if (isDozing) {
            row1.visibility = View.GONE
            row2.visibility = View.GONE
            row4.visibility = View.GONE
            row5.visibility = View.GONE
            return
        }

        row1.text = text1
        row1.visibility = if (text1.isEmpty()) View.GONE else View.VISIBLE

        row2.text = text2
        row2.visibility = if (text2.isEmpty()) View.GONE else View.VISIBLE

        row4.text = text4
        row4.visibility = if (text4.isEmpty()) View.GONE else View.VISIBLE

        row5.text = text5
        row5.visibility = if (text5.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Updates Row 3 (the active vocal track line) with word-by-word progressive glow highlight.
     */
    private fun updateActiveRowProgress(lyrics: LyricsData, index: Int, positionMs: Long) {
        if (index !in lyrics.lines.indices) {
            row3.text = ""
            return
        }

        val currentLine = lyrics.lines[index]
        val text = currentLine.text
        if (text.isEmpty()) {
            row3.text = ""
            return
        }

        val nextTimestampMs = if (index + 1 in lyrics.lines.indices) {
            lyrics.lines[index + 1].timestampMs
        } else {
            currentLine.timestampMs + 4000L
        }

        val rawGap = maxOf(400L, nextTimestampMs - currentLine.timestampMs)

        val wordRanges = mutableListOf<IntRange>()
        val matcher = Pattern.compile("\\S+").matcher(text)
        while (matcher.find()) {
            wordRanges.add(matcher.start() until matcher.end())
        }

        if (wordRanges.isEmpty()) {
            row3.text = text
            return
        }

        val totalWordChars = wordRanges.sumOf { it.last - it.first + 1 }
        
        // Dynamic vocal duration: songs spend ~280ms-340ms per word on average.
        // Long gaps between lines are instrumental pauses and should not stretch singing time.
        val estimatedSingingDurationMs = (wordRanges.size * 300L + totalWordChars * 30L + 200L).coerceIn(900L, 4000L)
        val activeDuration = if (rawGap <= 2200L) {
            maxOf(400L, rawGap - 100L)
        } else {
            minOf(rawGap - 300L, estimatedSingingDurationMs)
        }

        val elapsed = maxOf(0L, positionMs - currentLine.timestampMs)
        val progress = (elapsed.toFloat() / activeDuration.toFloat()).coerceIn(0.0f, 1.0f)

        var cumulative = 0
        var activeCharEnd = 0

        for (range in wordRanges) {
            val len = range.last - range.first + 1
            cumulative += len
            // Vocal threshold for this word (leads smoothly into word pronunciation)
            val wordThreshold = (cumulative - len * 0.60f) / totalWordChars.toFloat()
            if (progress >= wordThreshold) {
                activeCharEnd = range.last + 1
            } else {
                break
            }
        }

        if (progress >= 0.90f) {
            activeCharEnd = text.length
        }

        val spannable = SpannableStringBuilder(text)
        if (activeCharEnd > 0) {
            val vocalEnd = activeCharEnd.coerceAtMost(text.length)
            // Vocalized words: pure bright glowing white + bold
            spannable.setSpan(
                ForegroundColorSpan(Color.WHITE),
                0,
                vocalEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                vocalEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Upcoming words: dimmed translucent gray-white
            if (vocalEnd < text.length) {
                spannable.setSpan(
                    ForegroundColorSpan(Color.argb(120, 255, 255, 255)),
                    vocalEnd,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.NORMAL),
                    vocalEnd,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            // Before vocal starts for this line: all words dimmed
            spannable.setSpan(
                ForegroundColorSpan(Color.argb(120, 255, 255, 255)),
                0,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.NORMAL),
                0,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        row3.text = spannable
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}
