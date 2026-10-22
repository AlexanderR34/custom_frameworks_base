/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.ui.compose

import android.annotation.IdRes
import android.content.res.ColorStateList
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.Expandable
import com.android.compose.modifiers.thenIf
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.animation.Expandable
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.common.ui.compose.load
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.res.R
import com.android.systemui.statusbar.StatusBarIconView
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.notification.icon.ui.viewbinder.NotificationIconContainerViewBinder

@Composable
fun OngoingActivityChip(
    model: OngoingActivityChipModel.Active,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    modifier: Modifier = Modifier,
) {
    val contentDescription =
        when (val icon = model.icon) {
            is OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon ->
                icon.contentDescription.load()
            is OngoingActivityChipModel.ChipIcon.SingleColorIcon,
            is OngoingActivityChipModel.ChipIcon.FullColorIcon,
            null -> null
        }

    val borderStroke =
        model.colors.outline(LocalContext.current)?.let {
            BorderStroke(dimensionResource(R.dimen.ongoing_activity_chip_outline_width), Color(it))
        }

    val onClick =
        when (val clickBehavior = model.clickBehavior) {
            is OngoingActivityChipModel.ClickBehavior.ExpandAction -> { expandable: Expandable ->
                    clickBehavior.onClick(expandable)
                }
            is OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification -> { _ ->
                    clickBehavior.onClick()
                }
            is OngoingActivityChipModel.ClickBehavior.HideHeadsUpNotification -> { _ ->
                    clickBehavior.onClick()
                }
            is OngoingActivityChipModel.ClickBehavior.None -> null
        }
    val onClickLabel = model.clickBehavior.customOnClickLabel?.let { stringResource(it) }
    val isClickable = onClick != null

    val chipSidePaddingTotal = 20.dp
    val isIconOnly = model.content is OngoingActivityChipModel.Content.IconOnly
    val minWidth =
        if (isIconOnly) {
            dimensionResource(id = R.dimen.ongoing_appops_chip_height)
        } else if (isClickable) {
            dimensionResource(id = R.dimen.min_clickable_item_size)
        } else if (model.icon != null) {
            dimensionResource(id = R.dimen.ongoing_activity_chip_icon_size) + chipSidePaddingTotal
        } else {
            dimensionResource(id = R.dimen.ongoing_activity_chip_min_text_width) +
                chipSidePaddingTotal
        }

    val context = LocalContext.current
    val targetBgColor = Color(model.colors.background(context).defaultColor)
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "chipBgColor",
    )

    Expandable(
        color = animatedBgColor,
        shape =
            RoundedCornerShape(dimensionResource(id = R.dimen.ongoing_activity_chip_corner_radius)),
        modifier =
            modifier
                .wrapContentSize()
                .semantics {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                    }
                    if (model.content is OngoingActivityChipModel.Content.Countdown) {
                        liveRegion = LiveRegionMode.Assertive
                    }
                }
                .widthIn(min = minWidth)
                // For non-privacy-related chips, only show the chip if there's enough space for at
                // least the minimum width.
                .thenIf(!model.isImportantForPrivacy) {
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (constraints.maxWidth >= minWidth.roundToPx()) {
                                placeable.place(0, 0)
                            }
                        }
                    }
                }
                .graphicsLayer(
                    alpha =
                        if (model.transitionManager?.hideChipForTransition == true) {
                            0f
                        } else {
                            1f
                        }
                ),
        borderStroke = borderStroke,
        onClick = onClick,
        onClickLabel = onClickLabel,
        useModifierBasedImplementation = StatusBarChipsReturnAnimations.isEnabled,
        // Don't use the default minimum size for 2 reasons:
        //   1. Some chips like the 3-2-1 countdown chip should have a very small width.
        //   2. All chips need a background height that's much smaller than 48dp.
        // For clickable chips, Compose will automatically increase the touch target size outside
        // the bounds of the composable if needed, so the smaller chip size isn't an accessibility
        // concern.
        defaultMinSize = false,
        transitionControllerFactory = model.transitionManager?.controllerFactory,
    ) {
        ChipBody(model, iconViewStore, minWidth = minWidth)
    }
}

@Composable
private fun ChipBody(
    model: OngoingActivityChipModel.Active,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    minWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .heightIn(min = dimensionResource(R.dimen.ongoing_appops_chip_height))
                // Set the minWidth here as well as on the Expandable so that the content within
                // this row is still centered correctly horizontally
                .widthIn(min = minWidth)
                .padding(
                    // Always keep start & end padding the same so that if the text has to hide for
                    // some reason, the content is still centered
                    horizontal =
                        if (model.icon?.hasEmbeddedPadding == true) {
                            dimensionResource(
                                R.dimen.ongoing_activity_chip_side_padding_for_embedded_padding_icon
                            )
                        } else {
                            6.dp
                        }
                ),
    ) {
        model.icon?.let {
            ChipIcon(viewModel = it, iconViewStore = iconViewStore, colors = model.colors)
        }

        val isIconOnly = model.content is OngoingActivityChipModel.Content.IconOnly
        if (!isIconOnly) {
            val progressFraction = model.playbackProgressFraction
            if (progressFraction != null) {
                val context = LocalContext.current
                val chipIcon = model.icon
                val startPadding =
                    if (chipIcon != null && !chipIcon.hasEmbeddedPadding) {
                        dimensionResource(id = R.dimen.ongoing_activity_chip_icon_text_padding)
                    } else {
                        0.dp
                    }
                val endPadding =
                    if (model.icon?.hasEmbeddedPadding == true) {
                        dimensionResource(
                            id = R.dimen.ongoing_activity_chip_text_end_padding_for_embedded_padding_icon
                        )
                    } else {
                        0.dp
                    }
                val animatedProgress by animateFloatAsState(
                    targetValue = progressFraction,
                    animationSpec = tween(durationMillis = 350, easing = LinearEasing),
                    label = "mediaPlaybackProgress",
                )
                val baseProgressColor = Color(model.colors.text(context))
                val iconImpl = when (val chipIcon = model.icon) {
                    is OngoingActivityChipModel.ChipIcon.FullColorIcon -> chipIcon.impl
                    is OngoingActivityChipModel.ChipIcon.SingleColorIcon -> chipIcon.impl
                    else -> null
                }
                val artworkDrawable = (iconImpl as? com.android.systemui.common.shared.model.Icon.Loaded)?.drawable

                androidx.compose.ui.layout.Layout(
                    content = {
                        ChipContent(
                            viewModel = model.content,
                            icon = model.icon,
                            colors = model.colors,
                            modifier = Modifier.sysuiResTag(STATUS_BAR_CHIP_CONTENT_ID),
                        )
                        SquigglyProgressBar(
                            progress = animatedProgress,
                            baseColor = baseProgressColor,
                            artworkDrawable = artworkDrawable,
                            modifier = Modifier
                                .padding(start = startPadding, end = endPadding)
                                .height(6.dp),
                        )
                    },
                    modifier = Modifier.padding(bottom = 1.dp)
                ) { measurables, constraints ->
                    // Measure content (ChipContent) first to get natural width without intrinsic measurements
                    val contentPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
                    val width = contentPlaceable.width

                    // Measure progress bar to exactly match content width
                    val spacerHeight = 1.dp.roundToPx()
                    val barPlaceable = if (measurables.size > 1) {
                        measurables[1].measure(
                            androidx.compose.ui.unit.Constraints.fixed(
                                width = width,
                                height = 6.dp.roundToPx()
                            )
                        )
                    } else null

                    val totalHeight = contentPlaceable.height + spacerHeight + (barPlaceable?.height ?: 0)
                    layout(width, totalHeight) {
                        contentPlaceable.placeRelative(0, 0)
                        barPlaceable?.placeRelative(0, contentPlaceable.height + spacerHeight)
                    }
                }
            } else {
                ChipContent(
                    viewModel = model.content,
                    icon = model.icon,
                    colors = model.colors,
                    modifier = Modifier.sysuiResTag(STATUS_BAR_CHIP_CONTENT_ID),
                )
            }
        }

        model.decorativeIcon?.let {
            val context = LocalContext.current
            Box(
                modifier =
                    modifier
                        .size(width = 24.dp, height = 16.dp)
                        .background(
                            color = Color(it.colors.background(context).defaultColor),
                            shape = it.backgroundShape,
                        )
            ) {
                Icon(
                    icon = it.icon,
                    tint = Color(it.colors.text(context)),
                    modifier = Modifier.align(Alignment.Center).size(14.dp),
                )
            }

            Spacer(modifier.width(4.dp))
        }
    }
}

@Composable
private fun ChipIcon(
    viewModel: OngoingActivityChipModel.ChipIcon,
    iconViewStore: NotificationIconContainerViewBinder.IconViewStore?,
    colors: ColorsModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Hoist the infinite vinyl rotation transition to the top level so it never resets or stutters on recomposition!
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpinTransition")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "vinylRotation",
    )

    when (viewModel) {
        is OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon -> {
            check(iconViewStore != null)

            StatusBarIcon(colors, viewModel.notificationKey, modifier) {
                iconViewStore.iconView(viewModel.notificationKey)
            }
        }

        is OngoingActivityChipModel.ChipIcon.SingleColorIcon -> {
            Icon(
                icon = viewModel.impl,
                tint = Color(colors.icon(context)),
                modifier =
                    modifier.size(dimensionResource(id = R.dimen.ongoing_activity_chip_icon_size)),
            )
        }

        is OngoingActivityChipModel.ChipIcon.FullColorIcon -> {
            val iconModifier = if (viewModel.isMediaArtwork) {
                when (viewModel.artStyle) {
                    0 -> modifier.size(18.dp).graphicsLayer { rotationZ = vinylRotation }.clip(CircleShape)
                    1 -> modifier.size(18.dp).clip(CircleShape)
                    2 -> modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                    3 -> modifier.size(18.dp) // Classic Square
                    else -> modifier.size(18.dp)
                }
            } else {
                // Normal app icons (Download, Navigation, Timer, etc.): static, clean circular
                modifier.size(18.dp).clip(CircleShape)
            }

            // FullColorIcon must ALWAYS keep its authentic vibrant colors and never be tinted by Monet
            val drawable = (viewModel.impl as? com.android.systemui.common.shared.model.Icon.Loaded)?.drawable
            if (drawable != null) {
                Image(
                    painter = rememberDrawablePainter(drawable),
                    contentDescription = null,
                    modifier = iconModifier,
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    icon = viewModel.impl,
                    tint = Color.Unspecified,
                    modifier = iconModifier,
                )
            }
        }
    }
}

/** A Compose wrapper around [StatusBarIconView]. */
@Composable
private fun StatusBarIcon(
    colors: ColorsModel,
    notificationKey: String?,
    modifier: Modifier = Modifier,
    iconFactory: () -> StatusBarIconView?,
) {
    val context = LocalContext.current
    val colorTintList = ColorStateList.valueOf(colors.icon(context))

    val iconSizePx =
        context.resources.getDimensionPixelSize(
            R.dimen.ongoing_activity_chip_embedded_padding_icon_size
        )
    AndroidView(
        modifier = modifier,
        factory = { _ ->
            // Use a wrapper frame layout so that we still return a view even if the icon is null
            val wrapperFrameLayout = FrameLayout(context)

            val icon = iconFactory.invoke()
            if (icon == null) {
                Log.e(TAG, "Missing StatusBarIconView for $notificationKey")
            } else {
                icon.apply {
                    id = CUSTOM_ICON_VIEW_ID
                    layoutParams = ViewGroup.LayoutParams(iconSizePx, iconSizePx)
                }
                // If needed, remove the icon from its old parent (views can only be attached
                // to 1 parent at a time)
                (icon.parent as? ViewGroup)?.apply {
                    this.removeView(icon)
                    this.removeTransientView(icon)
                }
                wrapperFrameLayout.addView(icon)
            }

            wrapperFrameLayout
        },
        update = { frameLayout ->
            frameLayout.findViewById<StatusBarIconView>(CUSTOM_ICON_VIEW_ID)?.apply {
                this.imageTintList = colorTintList
            }
        },
    )
}

private const val TAG = "OngoingActivityChip"
// Used for end-to-end tests - if changing this, be sure to change the status bar e2e tests also.
private const val STATUS_BAR_CHIP_CONTENT_ID = "ongoing_activity_chip_content"
@IdRes private val CUSTOM_ICON_VIEW_ID = R.id.ongoing_activity_chip_custom_icon

/**
 * Flat clean progress bar for media and download chips.
 * Supports:
 * 1. Clean, straight horizontal progress line.
 * 2. Subtle leading tip glow dot.
 * 3. Color modes: Monet/Artwork (0), Rainbow animated gradient (1), Custom HEX (2).
 */
@Composable
private fun SquigglyProgressBar(
    progress: Float,
    baseColor: Color,
    artworkDrawable: android.graphics.drawable.Drawable? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colorMode = remember {
        Settings.System.getIntForUser(
            context.contentResolver,
            Settings.System.STATUS_BAR_CHIPS_PROGRESS_BAR_COLOR_MODE,
            0,
            UserHandle.myUserId()
        )
    }

    // Mode 0: Adaptada a Monet (Predeterminado)
    val monetColor = remember(context) {
        try {
            Color(com.android.settingslib.Utils.getColorAccentDefaultColor(context))
        } catch (_: Exception) {
            Color(context.getColor(com.android.internal.R.color.system_accent1_500))
        }
    }

    // Mode 1: Color de la portada del álbum (Dominant/Vibrant palette extraction)
    val artworkColor = remember(artworkDrawable, monetColor) {
        extractArtworkDominantColor(artworkDrawable) ?: monetColor
    }

    val infiniteTransition = rememberInfiniteTransition(label = "rainbowTransition")
    val rainbowHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rainbowHue",
    )

    val resolvedColor = remember(colorMode, rainbowHue, monetColor, artworkColor, baseColor) {
        when (colorMode) {
            0 -> monetColor // Adaptada a Monet (Predeterminado)
            1 -> artworkColor // Color de la portada del álbum
            2 -> {
                // Rainbow mode: HSV hue rotation
                val hsv = floatArrayOf(rainbowHue, 0.85f, 1.0f)
                Color(android.graphics.Color.HSVToColor(hsv))
            }
            3 -> {
                // Custom HEX color
                val hex = Settings.System.getStringForUser(
                    context.contentResolver,
                    Settings.System.STATUS_BAR_CHIPS_PROGRESS_BAR_CUSTOM_COLOR,
                    UserHandle.myUserId()
                )
                if (!hex.isNullOrBlank()) {
                    try {
                        Color(android.graphics.Color.parseColor(hex.trim()))
                    } catch (_: Exception) {
                        monetColor
                    }
                } else {
                    monetColor
                }
            }
            else -> monetColor
        }
    }

    val trackColor = resolvedColor.copy(alpha = 0.25f)

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val centerY = totalHeight / 2f
        val strokeWidthPx = 2.dp.toPx()

        // 1. Draw background track (flat line)
        drawLine(
            color = trackColor,
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(totalWidth, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        val progressPx = (totalWidth * progress.coerceIn(0f, 1f))
        if (progressPx <= 0.5f) return@Canvas

        // 2. Draw flat, clean straight progress line
        drawLine(
            color = resolvedColor,
            start = androidx.compose.ui.geometry.Offset(0f, centerY),
            end = androidx.compose.ui.geometry.Offset(progressPx, centerY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
        )

        // 3. Draw clean leading tip dot
        drawCircle(
            color = resolvedColor,
            radius = 2.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(progressPx, centerY),
        )
    }
}

private fun extractArtworkDominantColor(drawable: android.graphics.drawable.Drawable?): Color? {
    if (drawable == null) return null
    return try {
        val bitmap = when (drawable) {
            is android.graphics.drawable.BitmapDrawable -> drawable.bitmap
            else -> {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 64
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 64
                val bmp = android.graphics.Bitmap.createBitmap(
                    w.coerceAtMost(64),
                    h.coerceAtMost(64),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        }
        val palette = androidx.palette.graphics.Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
        swatch?.let { Color(it.rgb) }
    } catch (_: Exception) {
        null
    }
}


