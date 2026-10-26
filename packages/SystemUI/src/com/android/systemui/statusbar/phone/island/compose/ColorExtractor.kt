package com.android.systemui.statusbar.phone.island.compose

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class AlbumPalette(
    val primary: Color = Color(0xFF30D158),
    val secondary: Color = Color(0xFFFF2D55),
    val tertiary: Color = Color(0xFF5856D6),
    val highlight: Color = Color(0xFFFFD60A)
)

object ColorExtractor {

    fun extractVibrantAccent(bitmap: Bitmap?): Color {
        return extractPalette(bitmap).primary
    }

    fun extractPalette(bitmap: Bitmap?): AlbumPalette {
        if (bitmap == null) return AlbumPalette()

        try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return AlbumPalette()

            val stepX = (width / 12).coerceAtLeast(1)
            val stepY = (height / 12).coerceAtLeast(1)

            data class ScoredColor(val color: Int, val hue: Float, val sat: Float, val score: Float)
            val scoredList = mutableListOf<ScoredColor>()

            val hsv = FloatArray(3)

            for (x in 0 until width step stepX) {
                for (y in 0 until height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = (pixel ushr 24) and 0xFF
                    if (alpha < 140) continue

                    android.graphics.Color.colorToHSV(pixel, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]

                    if (value in 0.20f..0.98f && sat in 0.25f..1.0f) {
                        val score = sat * 1.8f + value
                        scoredList.add(ScoredColor(pixel, hsv[0], sat, score))
                    }
                }
            }

            if (scoredList.isEmpty()) {
                return AlbumPalette()
            }

            scoredList.sortByDescending { it.score }
            val first = scoredList.first()

            val primary = Color(first.color)

            val second = scoredList.firstOrNull {
                val diff = abs(it.hue - first.hue)
                val hueDiff = if (diff > 180f) 360f - diff else diff
                hueDiff > 35f
            }

            val secondary = if (second != null) {
                Color(second.color)
            } else {
                shiftHue(first.hue + 45f, first.sat)
            }

            val secondHue = second?.hue ?: (first.hue + 45f)
            val third = scoredList.firstOrNull {
                val diff1 = abs(it.hue - first.hue)
                val hDiff1 = if (diff1 > 180f) 360f - diff1 else diff1
                val diff2 = abs(it.hue - secondHue)
                val hDiff2 = if (diff2 > 180f) 360f - diff2 else diff2
                hDiff1 > 30f && hDiff2 > 30f
            }

            val tertiary = if (third != null) {
                Color(third.color)
            } else {
                shiftHue(first.hue - 45f, first.sat)
            }

            val highlight = Color(
                red = (primary.red * 1.25f).coerceIn(0f, 1f),
                green = (primary.green * 1.25f).coerceIn(0f, 1f),
                blue = (primary.blue * 1.25f).coerceIn(0f, 1f),
                alpha = 1.0f
            )

            return AlbumPalette(
                primary = primary,
                secondary = secondary,
                tertiary = tertiary,
                highlight = highlight
            )
        } catch (_: Exception) {
            return AlbumPalette()
        }
    }

    private fun shiftHue(hue: Float, sat: Float): Color {
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(normalizedHue, sat, 0.85f))
        return Color(colorInt)
    }
}
