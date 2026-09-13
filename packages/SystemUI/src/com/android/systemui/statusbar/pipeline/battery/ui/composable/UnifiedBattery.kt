/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pipeline.battery.ui.composable

import android.database.ContentObserver
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastFirstOrNull
import com.android.systemui.common.ui.compose.load
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryColors
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryFrame
import com.android.systemui.statusbar.pipeline.battery.shared.ui.BatteryGlyph
import com.android.systemui.statusbar.pipeline.battery.shared.ui.PathSpec
import com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Draws a battery directly on to a [Canvas]. The canvas is scaled to fill its container, and the
 * resulting battery is scaled using a FIT_CENTER type scaling that preserves the aspect ratio.
 */
@Composable
fun BatteryCanvas(
    path: PathSpec,
    innerWidth: Float,
    innerHeight: Float,
    glyphs: List<BatteryGlyph>,
    level: Int?,
    isFull: Boolean,
    colorsProvider: () -> BatteryColors,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {

    val totalWidth by
        remember(glyphs) {
            mutableFloatStateOf(
                if (glyphs.isEmpty()) {
                    0f
                } else {
                    // Pads in between each glyph, skipping the first
                    glyphs.drop(1).fold(glyphs.first().width) { acc: Float, next: BatteryGlyph ->
                        acc + INTER_GLYPH_PADDING_PX + next.width
                    }
                }
            )
        }

    Canvas(
        modifier = modifier.fillMaxSize().sysuiResTag(BatteryViewModel.TEST_TAG),
        contentDescription = contentDescription,
    ) {
        val scale = path.scaleTo(size.width, size.height)
        val colors = colorsProvider()

        scale(scale, pivot = Offset.Zero) {
            if (isFull) {
                // Saves a layer since we don't need background here
                drawPath(path = path.path, color = colors.fill)
            } else {
                // First draw the body
                val bgColor =
                    if (glyphs.isEmpty()) {
                        colors.backgroundOnly
                    } else {
                        colors.backgroundWithGlyph
                    }
                drawPath(path.path, bgColor)
                // Then draw the body, clipped to the fill level
                if (level != null && level > 0) {
                    clipRect(0f, 0f, level.scaledLevel(), innerHeight) {
                        drawRoundRect(
                            color = colors.fill,
                            topLeft = Offset.Zero,
                            size = Size(width = innerWidth, height = innerHeight),
                            cornerRadius = CornerRadius(2f),
                        )
                    }
                }
            }

            // Now draw the glyphs
            var horizontalOffset = (BatteryFrame.innerWidth - totalWidth) / 2
            for (glyph in glyphs) {
                // Move the glyph to the right spot
                val verticalOffset = (BatteryFrame.innerHeight - glyph.height) / 2
                inset(
                    // Never try and inset more than half of the available size - see b/400246091.
                    minOf(horizontalOffset, size.width / 2),
                    minOf(verticalOffset, size.height / 2),
                ) {
                    glyph.draw(this, colors)
                }

                horizontalOffset += glyph.width + INTER_GLYPH_PADDING_PX
            }
        }
    }
}

// Experimentally-determined value
private const val INTER_GLYPH_PADDING_PX = 0.8f

/** Calculate the right-edge of the clip for the fill-rect, based on a level of [0-100] */
private fun Int.scaledLevel(): Float {
    val endSide = BatteryFrame.innerWidth
    return ceil((toFloat() / 100f) * endSide)
}

/**
 * Samsung One UI style Battery Composable.
 *
 * Features:
 * - Neutral frame border (white in dark mode, black in light mode - NEVER green or yellow).
 * - Rounded rectangle body with positive terminal cap on right.
 * - Translucent inner cavity background.
 * - Dynamic fill level (green when charging, yellow in power save, red when <=15%, neutral otherwise).
 * - Sharp, centered charging bolt inside the battery body when plugged in.
 */
/**
 * HyperOS style Battery Composable (matches real HyperOS status bar design).
 *
 * Visual design:
 * - Horizontal rounded battery frame with terminal cap on the right.
 * - Dynamic inner fill (vibrant green 0xFF2ECC71 when charging).
 * - Prominent white charging lightning bolt centered inside the battery body.
 * - Percentage text (e.g. 100%) placed outside on the right.
 */
@Composable
fun HyperOSBattery(
    level: Int,
    isCharging: Boolean,
    isPowerSave: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    Canvas(
        modifier = modifier.fillMaxSize().sysuiResTag(BatteryViewModel.TEST_TAG),
        contentDescription = contentDescription,
    ) {
        val w = size.width
        val h = size.height

        val strokeWidth = (1.4f * (h / 12f)).coerceIn(1.2f, 1.8f)
        val halfStroke = strokeWidth / 2f
        val capWidth = (1.8f * (h / 12f)).coerceIn(1.5f, 2.2f)
        val capHeight = maxOf(5.6f * (h / 12f), h * 0.50f)
        val capCornerRadius = CornerRadius(1.2f * (h / 12f))
        val rightMargin = capWidth + strokeWidth + 0.8f

        val frameRect = ComposeRect(
            left = halfStroke + 0.5f,
            top = halfStroke + 0.5f,
            right = w - rightMargin,
            bottom = h - halfStroke - 0.5f,
        )
        val frameCornerRadius = CornerRadius(3.2f * (h / 12f))
        val neutralColor = if (isDark) Color.White else Color.Black

        // 1. Draw outer battery frame
        drawRoundRect(
            color = neutralColor,
            topLeft = frameRect.topLeft,
            size = frameRect.size,
            cornerRadius = frameCornerRadius,
            style = Stroke(width = strokeWidth),
        )

        // 2. Draw terminal cap on the right
        val capTop = (h - capHeight) / 2f
        drawRoundRect(
            color = neutralColor,
            topLeft = Offset(frameRect.right + (strokeWidth * 0.6f), capTop),
            size = Size(capWidth, capHeight),
            cornerRadius = capCornerRadius,
        )

        // 3. Inner cavity dimensions with uniform gap
        val inset = strokeWidth + 1.1f * (h / 12f)
        val innerLeft = frameRect.left + inset
        val innerTop = frameRect.top + inset
        val innerMaxRight = frameRect.right - inset
        val innerBottom = frameRect.bottom - inset
        val innerWidth = (innerMaxRight - innerLeft).coerceAtLeast(0f)
        val innerHeight = (innerBottom - innerTop).coerceAtLeast(0f)

        if (innerWidth > 0f && innerHeight > 0f) {
            val innerCornerRadius = CornerRadius(2.4f * (h / 12f))

            // 4. Draw soft cavity background
            drawRoundRect(
                color = neutralColor.copy(alpha = 0.18f),
                topLeft = Offset(innerLeft, innerTop),
                size = Size(innerWidth, innerHeight),
                cornerRadius = innerCornerRadius,
            )

            // 5. Determine active fill color (HyperOS vibrant green for charging: 0xFF34C759)
            val activeFillColor = when {
                isCharging -> Color(0xFF34C759)
                isPowerSave -> Color(0xFFF59E0B)
                level <= 15 -> Color(0xFFEF4444)
                else -> neutralColor
            }

            // 6. Draw dynamic level progress fill
            val currentFillWidth = (innerWidth * (level.coerceIn(0, 100) / 100f)).coerceIn(0f, innerWidth)
            if (currentFillWidth > 0f) {
                drawRoundRect(
                    color = activeFillColor,
                    topLeft = Offset(innerLeft, innerTop),
                    size = Size(currentFillWidth, innerHeight),
                    cornerRadius = innerCornerRadius,
                )
            }

            // 7. Draw sharp HyperOS charging bolt (solid black outline + white sharp fill)
            if (isCharging) {
                val centerX = frameRect.left + (frameRect.width / 2f) - (0.5f * (h / 12f))
                val centerY = h / 2f
                val boltH = h * 1.05f
                val boltW = h * 0.62f

                val boltPath = Path().apply {
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

                    moveTo(topX, topY)
                    lineTo(midLeftX, midLeftY)
                    lineTo(notchLeftX, notchLeftY)
                    lineTo(botX, botY)
                    lineTo(midRightX, midRightY)
                    lineTo(notchRightX, notchRightY)
                    close()
                }
                // Solid black outline for crisp contrast + pure white sharp bolt
                drawPath(boltPath, Color.Black, style = Stroke(width = 1.6f * (h / 12f)))
                drawPath(boltPath, Color.White)
            }
        }
    }
}

/**
 * Samsung One UI style Battery Composable (compact capsule pill).
 */
@Composable
fun SamsungBattery(
    level: Int,
    showPercent: Boolean = true,
    isCharging: Boolean,
    isPowerSave: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    val neutralColor = if (isDark) Color.White else Color.Black
    val textColor = if (isDark) Color.White else Color.Black

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .sysuiResTag(BatteryViewModel.TEST_TAG),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val pillRadius = CornerRadius(h / 2f, h / 2f)

            // 1. Draw capsule background (translucent container)
            drawRoundRect(
                color = neutralColor.copy(alpha = 0.20f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = pillRadius,
            )

            // 2. Dynamic progress fill
            val fillWidth = (w * (level.coerceIn(0, 100) / 100f)).coerceIn(0f, w)
            if (fillWidth > 0f) {
                val fillColor = when {
                    isCharging -> Color(0xFF34C759).copy(alpha = 0.40f)
                    isPowerSave -> Color(0xFFF59E0B).copy(alpha = 0.40f)
                    level <= 15 -> Color(0xFFEF4444).copy(alpha = 0.50f)
                    else -> neutralColor.copy(alpha = 0.25f)
                }
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, h),
                    cornerRadius = pillRadius,
                )
            }
        }

        // Inner content: charging bolt + percentage number inside compact capsule
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 2.dp),
        ) {
            if (isCharging) {
                Canvas(
                    modifier = Modifier
                        .fillMaxHeight(0.70f)
                        .aspectRatio(0.60f)
                        .padding(end = if (showPercent) 1.5.dp else 0.dp)
                ) {
                    val bw = size.width
                    val bh = size.height
                    val cx = bw / 2f
                    val cy = bh / 2f
                    val boltPath = Path().apply {
                        moveTo(cx + bw * 0.20f, cy - bh * 0.50f)
                        lineTo(cx - bw * 0.50f, cy + bh * 0.08f)
                        lineTo(cx - bw * 0.05f, cy + bh * 0.08f)
                        lineTo(cx - bw * 0.20f, cy + bh * 0.50f)
                        lineTo(cx + bw * 0.50f, cy - bh * 0.08f)
                        lineTo(cx + bw * 0.05f, cy - bh * 0.08f)
                        close()
                    }
                    drawPath(boltPath, textColor)
                }
            }
            if (showPercent) {
                Text(
                    text = "$level",
                    color = textColor,
                    fontSize = if (level >= 100) 8.sp else 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(
                            includeFontPadding = false,
                        ),
                    ),
                )
            }
        }
    }
}

private fun getBatteryStyle(context: android.content.Context): Int {
    val style = Settings.System.getIntForUser(
        context.contentResolver,
        "status_bar_battery_style",
        -1,
        UserHandle.USER_CURRENT
    )
    if (style != -1) return style
    val hyperOs = Settings.System.getIntForUser(
        context.contentResolver,
        "status_bar_battery_style_hyperos",
        0,
        UserHandle.USER_CURRENT
    )
    return if (hyperOs == 1) 1 else 0
}

/**
 * A battery icon that supports Stock AOSP, HyperOS, and Samsung One UI styles.
 */
@Composable
fun UnifiedBattery(
    viewModel: BatteryViewModel,
    isDarkProvider: () -> IsAreaDark,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var bounds by remember { mutableStateOf(Rect()) }
    var batteryStyle by remember {
        mutableStateOf(getBatteryStyle(context))
    }
    var showPercentSetting by remember {
        mutableStateOf(
            Settings.System.getIntForUser(
                context.contentResolver,
                Settings.System.SHOW_BATTERY_PERCENT,
                0,
                UserHandle.USER_CURRENT
            ) == 1
        )
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                batteryStyle = getBatteryStyle(context)
                showPercentSetting = Settings.System.getIntForUser(
                    context.contentResolver,
                    Settings.System.SHOW_BATTERY_PERCENT,
                    0,
                    UserHandle.USER_CURRENT
                ) == 1
            }
        }
        val uri1 = Settings.System.getUriFor("status_bar_battery_style_hyperos")
        val uri2 = Settings.System.getUriFor("status_bar_battery_style")
        val uri3 = Settings.System.getUriFor(Settings.System.SHOW_BATTERY_PERCENT)
        context.contentResolver.registerContentObserver(uri1, false, observer, UserHandle.USER_CURRENT)
        context.contentResolver.registerContentObserver(uri2, false, observer, UserHandle.USER_CURRENT)
        context.contentResolver.registerContentObserver(uri3, false, observer, UserHandle.USER_CURRENT)
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    val isDark = isDarkProvider().isDarkTheme(bounds)
    val neutralColor = if (isDark) Color.White else Color.Black
    val contentDesc = viewModel.contentDescription.load() ?: ""

    when (batteryStyle) {
        1 -> {
            // Style 1: HyperOS (Real HyperOS horizontal battery + outer percentage)
            val showPercent = viewModel.glyphList.isNotEmpty() || showPercentSetting

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier
                    .wrapContentWidth()
                    .sysuiResTag(BatteryViewModel.TEST_TAG)
                    .onLayoutRectChanged { relativeLayoutBounds ->
                        bounds = with(relativeLayoutBounds.boundsInScreen) { Rect(left, top, right, bottom) }
                    },
            ) {
                HyperOSBattery(
                    level = viewModel.level ?: 100,
                    isCharging = viewModel.isCharging,
                    isPowerSave = (viewModel.attribution == BatteryGlyph.Plus),
                    isDark = isDark,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .aspectRatio(26.5f / 11.5f)
                        .fillMaxHeight(),
                    contentDescription = contentDesc,
                )
                if (showPercent && viewModel.level != null) {
                    Text(
                        text = "${viewModel.level}%",
                        color = neutralColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(
                                includeFontPadding = false,
                            ),
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        2 -> {
            // Style 2: Samsung (Compact Capsule Pill)
            val showPercent = showPercentSetting || viewModel.glyphList.isNotEmpty()
            val pillAspect = when {
                !showPercent && viewModel.isCharging -> 20f / 11.5f
                !showPercent -> 16f / 11.5f
                viewModel.isCharging && (viewModel.level ?: 0) >= 100 -> 26f / 11.5f
                viewModel.isCharging -> 24f / 11.5f
                (viewModel.level ?: 0) >= 100 -> 23f / 11.5f
                else -> 21f / 11.5f
            }
            SamsungBattery(
                level = viewModel.level ?: 100,
                showPercent = showPercent,
                isCharging = viewModel.isCharging,
                isPowerSave = (viewModel.attribution == BatteryGlyph.Plus),
                isDark = isDark,
                modifier = modifier
                    .sysuiResTag(BatteryViewModel.TEST_TAG)
                    .onLayoutRectChanged { relativeLayoutBounds ->
                        bounds = with(relativeLayoutBounds.boundsInScreen) { Rect(left, top, right, bottom) }
                    }
                    .aspectRatio(pillAspect)
                    .fillMaxHeight(),
                contentDescription = contentDesc,
            )
        }
        else -> {
            // Style 0 (or default): Stock AOSP unified battery
            val colorProvider = {
                if (isDark) {
                    viewModel.colorProfile.dark
                } else {
                    viewModel.colorProfile.light
                }
            }

            BatteryLayout(
                attribution = viewModel.attribution,
                levelProvider = { viewModel.level },
                isFullProvider = { viewModel.isFull },
                glyphsProvider = { viewModel.glyphList },
                colorsProvider = colorProvider,
                modifier = modifier
                    .sysuiResTag(BatteryViewModel.TEST_TAG)
                    .onLayoutRectChanged { relativeLayoutBounds ->
                        bounds = with(relativeLayoutBounds.boundsInScreen) { Rect(left, top, right, bottom) }
                    },
                contentDescription = contentDesc,
            )
        }
    }
}

@Composable
fun BatteryLayout(
    attribution: BatteryGlyph?,
    levelProvider: () -> Int?,
    isFullProvider: () -> Boolean,
    glyphsProvider: () -> List<BatteryGlyph>,
    colorsProvider: () -> BatteryColors,
    modifier: Modifier,
    contentDescription: String = "",
) {
    Layout(
        content = {
            BatteryBody(
                pathSpec = BatteryFrame.bodyPathSpec,
                levelProvider = levelProvider,
                glyphsProvider = glyphsProvider,
                isFullProvider = isFullProvider,
                colorsProvider = colorsProvider,
                modifier = Modifier.layoutId(BatteryMeasurePolicy.LayoutId.Frame),
                contentDescription = contentDescription,
            )
            if (attribution != null) {
                BatteryAttribution(
                    attr = attribution,
                    colorsProvider = colorsProvider,
                    modifier =
                        Modifier.layoutId(
                            BatteryMeasurePolicy.LayoutId.Attribution(wrapped = attribution)
                        ),
                )
            } else {
                BatteryCap(
                    colorsProvider = colorsProvider,
                    isFullProvider = isFullProvider,
                    glyphsProvider = glyphsProvider,
                    modifier = Modifier.layoutId(BatteryMeasurePolicy.LayoutId.Cap),
                )
            }
        },
        measurePolicy = BatteryMeasurePolicy(),
        // [Offscreen] Enables the BlendMode.Clear usage for the battery attribution
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    )
}

class BatteryMeasurePolicy : MeasurePolicy {
    sealed class LayoutId {
        data object Frame : LayoutId()

        data object Cap : LayoutId()

        // We don't have to depend on the whole [BatteryGlyph] here, we just need to know the
        // size so we can scale and measure appropriately
        data class Attribution(val wrapped: BatteryGlyph) : LayoutId()
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val batteryFrame = measurables.fastFirst { it.layoutId == LayoutId.Frame }

        // We will scale the entire battery icon based on the given height
        val scale = constraints.maxHeight / BatteryFrame.innerHeight

        val batterySize = BatteryFrame.bodyPathSpec.scaledSize(scale)
        val batteryFramePlaceable =
            batteryFrame.measure(
                constraints =
                    constraints.copy(
                        minWidth = batterySize.width.roundToInt(),
                        maxWidth = batterySize.width.roundToInt(),
                        minHeight = batterySize.height.roundToInt(),
                        maxHeight = batterySize.height.roundToInt(),
                    )
            )

        val cap = measurables.fastFirstOrNull { it.layoutId == LayoutId.Cap }
        val capPlaceable = run {
            cap?.let {
                val size = BatteryFrame.capPathSpec.scaledSize(scale)
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                it.measure(
                    constraints =
                        constraints.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
                )
            }
        }

        val attr = measurables.fastFirstOrNull { it.layoutId is LayoutId.Attribution }
        val attrPlaceable = run {
            attr?.let {
                val ps = (it.layoutId as LayoutId.Attribution).wrapped
                val size = ps.scaledSize(scale)
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                it.measure(
                    constraints =
                        constraints.copy(minWidth = w, maxWidth = w, minHeight = h, maxHeight = h)
                )
            }
        }

        var totalWidth: Int = batteryFramePlaceable.width
        if (attrPlaceable != null) {
            totalWidth += (attrPlaceable.width * 0.8).roundToInt()
        } else if (capPlaceable != null) {
            // 1dp of padding * scale for the cap
            totalWidth += capPlaceable.width + scale.roundToInt()
        }
        val totalHeight = batterySize.height.roundToInt()
        return layout(totalWidth, totalHeight) {
            if (layoutDirection == LayoutDirection.Rtl) {
                val (offsetX, placeable) =
                    when {
                        // Attr overlaps the battery frame by 20% of its own width
                        attrPlaceable != null ->
                            (attrPlaceable.width * (1 - attrOverlap)).roundToInt() to attrPlaceable

                        // Cap has exactly 1dp (scaled) of space after it
                        capPlaceable != null ->
                            capPlaceable.width + scale.roundToInt() to capPlaceable

                        else -> 0 to null
                    }

                // Place the battery frame first so the layers are in the right order
                batteryFramePlaceable.place(offsetX, 0)

                // Then place the cap or attribution. In RTL, it always is left-aligned
                placeable?.apply {
                    placeCenteredVertically(
                        placeable = this,
                        containerHeight = batteryFramePlaceable.height,
                        xOffset = 0,
                    )
                }
            } else {
                batteryFramePlaceable.place(0, 0)

                val (xOffset, placeable) =
                    when {
                        attrPlaceable != null ->
                            (batteryFramePlaceable.width - (attrOverlap * attrPlaceable.width))
                                .roundToInt() to attrPlaceable
                        capPlaceable != null ->
                            (batteryFramePlaceable.width + scale.roundToInt()) to capPlaceable
                        else -> 0 to null
                    }

                placeable?.apply {
                    placeCenteredVertically(
                        placeable = this,
                        containerHeight = batteryFramePlaceable.height,
                        xOffset = xOffset,
                    )
                }
            }
        }
    }

    private fun Placeable.PlacementScope.placeCenteredVertically(
        placeable: Placeable,
        containerHeight: Int,
        xOffset: Int,
    ) {
        placeable.place(x = xOffset, y = placeable.centerYOffset(containerHeight))
    }

    private fun Placeable.centerYOffset(outerHeight: Int) =
        ((outerHeight - height) / 2f).roundToInt()

    companion object {
        // Overlap the attribution by 20%
        private const val attrOverlap = 0.2f
    }
}

/**
 * Draws just the round-rect piece of the battery frame. If [glyphsProvider] is non-empty, then this
 * composable also renders the glyphs centered in the frame.
 *
 * Always shows the fill amount, clipped to the given [levelProvider]
 */
@Composable
fun BatteryBody(
    pathSpec: PathSpec,
    levelProvider: () -> Int?,
    glyphsProvider: () -> List<BatteryGlyph>,
    isFullProvider: () -> Boolean,
    colorsProvider: () -> BatteryColors,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    Canvas(modifier = modifier, contentDescription = contentDescription) {
        val rtl = layoutDirection == LayoutDirection.Rtl

        val level = levelProvider()
        val colors = colorsProvider()
        val glyphs = glyphsProvider()
        val isFull = isFullProvider()
        val frameColor =
            getBatteryFrameColor(isFull = isFull, hasGlyphs = glyphs.isNotEmpty(), colors = colors)

        val totalGlyphWidth =
            if (glyphs.isEmpty()) {
                0f
            } else {
                // Pads in between each glyph, skipping the first
                glyphs.drop(1).fold(glyphs.first().width) { acc: Float, next: BatteryGlyph ->
                    acc + INTER_GLYPH_PADDING_PX + next.width
                }
            }

        val s = pathSpec.scaleTo(size.width, size.height)
        scale(scale = s, pivot = Offset.Zero) {
            if (isFull) {
                // If full, the frameColor is already the fill color.
                drawPath(pathSpec.path, frameColor)
            } else {
                // 1. Draw body background
                drawPath(pathSpec.path, frameColor)

                // 2. clip the fill to the level if we have it
                if (level != null && level > 0) {
                    clipRect(
                        left = if (!rtl) 0f else BatteryFrame.innerWidth - level.scaledLevel(),
                        top = 0f,
                        right = if (!rtl) level.scaledLevel() else BatteryFrame.innerWidth,
                        bottom = BatteryFrame.innerHeight,
                    ) {
                        // 3. Draw the rounded rect fill fully, it'll be clipped above
                        drawRoundRect(
                            color = colors.fill,
                            topLeft = Offset.Zero,
                            size =
                                Size(
                                    width = BatteryFrame.innerWidth,
                                    height = BatteryFrame.innerHeight,
                                ),
                            CornerRadius(x = BatteryFrame.cornerRadius),
                        )
                    }
                }
            }

            // Next: draw the glyphs
            var horizontalOffset = (BatteryFrame.innerWidth - totalGlyphWidth) / 2f
            for (glyph in glyphs) {
                // Move the glyph to the right spot
                val verticalOffset = (BatteryFrame.innerHeight - glyph.height) / 2
                inset(
                    // Never try and inset more than half of the available size - see b/400246091.
                    minOf(horizontalOffset, size.width / 2),
                    minOf(verticalOffset, size.height / 2),
                ) {
                    glyph.draw(this, colors)
                }
                horizontalOffset += glyph.width + INTER_GLYPH_PADDING_PX
            }
        }
    }
}

@Composable
fun BatteryCap(
    colorsProvider: () -> BatteryColors,
    isFullProvider: () -> Boolean,
    glyphsProvider: () -> List<BatteryGlyph>,
    modifier: Modifier = Modifier,
) {
    val pathSpec = BatteryFrame.capPathSpec
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(modifier = modifier.scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f)) {
        val colors = colorsProvider()
        val isFull = isFullProvider()
        val hasGlyphs = glyphsProvider().isNotEmpty()
        val color = getBatteryFrameColor(isFull = isFull, hasGlyphs = hasGlyphs, colors = colors)

        val s = pathSpec.scaleTo(size.width, size.height)
        scale(s, pivot = Offset.Zero) { drawPath(pathSpec.path, color = color) }
    }
}

@Composable
fun BatteryAttribution(
    attr: BatteryGlyph,
    colorsProvider: () -> BatteryColors,
    modifier: Modifier = Modifier,
) {
    val stroke = remember { Stroke(width = 2f) }
    // Do not RTL the attribution, because they are text-like. '?' shouldn't be flipped, for example
    Canvas(modifier = modifier) {
        val s = attr.scaleTo(size.width, size.height)
        val colors = colorsProvider()
        scale(s, pivot = Offset.Zero) {
            drawPath(
                path = attr.path,
                color = Color.Black,
                style = stroke,
                blendMode = BlendMode.Clear,
            )
            drawPath(attr.path, color = colors.attribution)
        }
    }
}

/** Determines the correct color for the battery frame (body and cap) based on its state. */
private fun getBatteryFrameColor(
    isFull: Boolean,
    hasGlyphs: Boolean,
    colors: BatteryColors,
): Color {
    return when {
        isFull -> colors.fill
        hasGlyphs -> colors.backgroundWithGlyph
        else -> colors.backgroundOnly
    }
}
