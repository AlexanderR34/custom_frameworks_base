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

package com.android.systemui.common.ui.compose

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.DrawableWrapper
import androidx.compose.foundation.Image
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.common.shared.model.Icon

@Composable
public fun Icon(
    icon: Icon,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val contentDescription = icon.contentDescription?.load()
    when (icon) {
        is Icon.Loaded -> {
            var drawable = icon.drawable
            if (drawable is DrawableWrapper) {
                drawable.drawable?.let { drawable = it }
            }
            if (drawable is AdaptiveIconDrawable) {
                val monochrome = drawable.monochrome
                if (monochrome != null) {
                    Icon(rememberDrawablePainter(monochrome), contentDescription, modifier, tint)
                    return
                }
                val foreground = drawable.foreground
                if (foreground != null) {
                    Icon(rememberDrawablePainter(foreground), contentDescription, modifier, tint)
                    return
                }
                Icon(rememberDrawablePainter(drawable), contentDescription, modifier, tint)
                return
            } else if (drawable is BitmapDrawable) {
                Icon(rememberDrawablePainter(drawable), contentDescription, modifier, tint)
                return
            } else {
                Icon(rememberDrawablePainter(drawable), contentDescription, modifier, tint)
            }
        }
        is Icon.Resource -> {
            Icon(painterResource(icon.resId), contentDescription, modifier, tint)
        }
    }
}

@Composable
public fun Icon(icon: Icon, tint: (() -> Color)?, modifier: Modifier = Modifier) {
    val contentDescription = icon.contentDescription?.load()
    val defaultColor = LocalContentColor.current
    val effectiveTint = tint ?: { defaultColor }
    when (icon) {
        is Icon.Loaded -> {
            var drawable = icon.drawable
            if (drawable is DrawableWrapper) {
                drawable.drawable?.let { drawable = it }
            }
            if (drawable is AdaptiveIconDrawable) {
                val monochrome = drawable.monochrome
                if (monochrome != null) {
                    Icon(
                        rememberDrawablePainter(monochrome),
                        effectiveTint,
                        contentDescription,
                        modifier,
                    )
                    return
                }
                val foreground = drawable.foreground
                if (foreground != null) {
                    Icon(
                        rememberDrawablePainter(foreground),
                        effectiveTint,
                        contentDescription,
                        modifier,
                    )
                    return
                }
                Icon(
                    rememberDrawablePainter(drawable),
                    effectiveTint,
                    contentDescription,
                    modifier,
                )
                return
            } else if (drawable is BitmapDrawable) {
                Icon(
                    rememberDrawablePainter(drawable),
                    effectiveTint,
                    contentDescription,
                    modifier,
                )
                return
            } else {
                Icon(
                    rememberDrawablePainter(drawable),
                    effectiveTint,
                    contentDescription,
                    modifier,
                )
            }
        }
        is Icon.Resource -> {
            Icon(
                painterResource(icon.resId),
                effectiveTint,
                contentDescription,
                modifier,
            )
        }
    }
}

@Composable
private fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = androidx.compose.ui.Alignment.Center,
        contentScale = ContentScale.Fit,
        alpha = 1.0f,
        colorFilter = colorFilter,
    )
}

@Composable
private fun Icon(
    painter: Painter,
    tint: () -> Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val color = tint()
    val colorFilter = if (color == Color.Unspecified) null else ColorFilter.tint(color)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = androidx.compose.ui.Alignment.Center,
        contentScale = ContentScale.Fit,
        alpha = 1.0f,
        colorFilter = colorFilter,
    )
}
