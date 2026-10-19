/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.qs.ui.composable

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.compose.animation.scene.ContentScope
import com.android.compose.gesture.gesturesDisabled
import com.android.compose.modifiers.thenIf
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.media.remedia.ui.compose.Media
import com.android.systemui.media.remedia.ui.compose.MediaPresentationStyle
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.shared.ui.QuickSettings.Elements
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun ContentScope.QuickSettingsContent(
    viewModel: QuickSettingsContainerViewModel,
    mediaInRow: Boolean,
    modifier: Modifier = Modifier,
    mediaSquishiness: () -> Float = { 1f },
) {
    val context = LocalContext.current

        val mediaCardMode by produceState(
            initialValue = try {
                android.provider.Settings.System.getInt(context.contentResolver, "qs_media_card_mode", 3)
            } catch (e: Exception) { 3 },
            key1 = context
        ) {
            val uri = android.provider.Settings.System.getUriFor("qs_media_card_mode")
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    value = try {
                        android.provider.Settings.System.getInt(context.contentResolver, "qs_media_card_mode", 3)
                    } catch (e: Exception) { 3 }
                }
            }
            try { context.contentResolver.registerContentObserver(uri, false, observer) } catch (e: Exception) {}
            awaitDispose { try { context.contentResolver.unregisterContentObserver(observer) } catch (e: Exception) {} }
        }
    val brightnessPosition by produceState(
        initialValue = try {
            android.provider.Settings.System.getInt(context.contentResolver, "qs_brightness_position", 0)
        } catch (e: Exception) { 0 },
        key1 = context
    ) {
        val uri = android.provider.Settings.System.getUriFor("qs_brightness_position")
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = try {
                    android.provider.Settings.System.getInt(context.contentResolver, "qs_brightness_position", 0)
                } catch (e: Exception) { 0 }
            }
        }
        try { context.contentResolver.registerContentObserver(uri, false, observer) } catch (e: Exception) {}
        awaitDispose { try { context.contentResolver.unregisterContentObserver(observer) } catch (e: Exception) {} }
    }

    val showAutoBrightnessButton by produceState(
        initialValue = try {
            android.provider.Settings.System.getInt(context.contentResolver, "qs_show_auto_brightness_button", 1) == 1
        } catch (e: Exception) { true },
        key1 = context
    ) {
        val uri = android.provider.Settings.System.getUriFor("qs_show_auto_brightness_button")
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = try {
                    android.provider.Settings.System.getInt(context.contentResolver, "qs_show_auto_brightness_button", 1) == 1
                } catch (e: Exception) { true }
            }
        }
        try { context.contentResolver.registerContentObserver(uri, false, observer) } catch (e: Exception) {}
        awaitDispose { try { context.contentResolver.unregisterContentObserver(observer) } catch (e: Exception) {} }
    }

    var isAutoBrightness by remember {
        mutableStateOf(
            try {
                android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                ) == android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } catch (e: Exception) { false }
        )
    }

    DisposableEffect(context) {
        val uri = android.provider.Settings.System.getUriFor(android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE)
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isAutoBrightness = try {
                    android.provider.Settings.System.getInt(
                        context.contentResolver,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                        android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    ) == android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                } catch (e: Exception) { false }
            }
        }
        try { context.contentResolver.registerContentObserver(uri, false, observer) } catch (e: Exception) {}
        onDispose { try { context.contentResolver.unregisterContentObserver(observer) } catch (e: Exception) {} }
    }

    QuickSettingsPanelLayout(
        brightness =
            @Composable {
                if (viewModel.isBrightnessSliderVisible) {
                    var isBrightnessSliderInteractable by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        snapshotFlow { Elements.QuickSettingsContent.currentAlpha() }
                            .filterNotNull()
                            .collect { isBrightnessSliderInteractable = it >= .5f }
                    }
                    Element(modifier = Modifier, key = Elements.BrightnessSlider) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                BrightnessSliderContainer(
                                    viewModel.brightnessSliderViewModel,
                                    containerColors =
                                        ContainerColors(
                                            Color.Transparent,
                                            ContainerColors.defaultContainerColor,
                                        ),
                                    modifier =
                                        Modifier.padding(
                                                vertical =
                                                    dimensionResource(id = R.dimen.qs_brightness_margin_top)
                                            )
                                            .thenIf(!isBrightnessSliderInteractable) {
                                                Modifier.gesturesDisabled()
                                            },
                                )
                            }
                            if (showAutoBrightnessButton) {
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val newMode = if (isAutoBrightness) {
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                                        } else {
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                                        }
                                        try {
                                            android.provider.Settings.System.putInt(
                                                context.contentResolver,
                                                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                                                newMode
                                            )
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(44.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (isAutoBrightness) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isAutoBrightness) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_qs_autobrightness),
                                        contentDescription = "Brillo automático",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
        tiles =
            @Composable {
                var listening by remember { mutableStateOf(false) }
                LifecycleStartEffect(Unit) {
                    listening = true

                    onStopOrDispose { listening = false }
                }

                Box {
                    GridAnchor()
                    TileGrid(
                        viewModel.tileGridViewModel,
                        listening = { listening },
                        modifier = Modifier.element(Elements.QuickSettingsTiles),
                    )
                }
            },
        media =
            @Composable {
                if (isAlwaysComposedContentVisible()) {
                    Element(key = Media.Elements.MediaCarousel, modifier = Modifier) {
                        Media(
                            viewModelFactory = viewModel.mediaViewModelFactory,
                            presentationStyle = MediaPresentationStyle.Default,
                            behavior = QuickSettingsContainerViewModel.mediaUiBehavior,
                            onDismissed = viewModel::onMediaSwipeToDismiss,
                            mediaSquishiness = mediaSquishiness,
                            location = Media.Location.QS,
                        )
                    }
                } else {
                    // Add an empty box when QS content is not visible to keep the same number of
                    // elements.
                    Box(modifier = Modifier)
                }
            },
        mediaInRow = mediaInRow,
        brightnessPosition = brightnessPosition,
        modifier =
            modifier
                .element(Elements.QuickSettingsContent)
                .padding(horizontal = dimensionResource(id = R.dimen.qs_horizontal_margin))
                .sysuiResTag("quick_settings_panel"),
    )
}

@Composable
private fun QuickSettingsPanelLayout(
    brightness: @Composable () -> Unit,
    tiles: @Composable () -> Unit,
    media: @Composable () -> Unit,
    mediaInRow: Boolean,
    brightnessPosition: Int,
    modifier: Modifier = Modifier,
) {
    if (mediaInRow) {
        Column(
            verticalArrangement = spacedBy(QuickSettingsShade.Dimensions.VerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            if (brightnessPosition == 0) {
                brightness()
            }
            Row(
                horizontalArrangement = spacedBy(QuickSettingsShade.Dimensions.HorizontalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) { tiles() }
                Box(modifier = Modifier.weight(1f)) { media() }
            }
            if (brightnessPosition == 1) {
                brightness()
            }
        }
    } else {
        Column(
            verticalArrangement = spacedBy(QuickSettingsShade.Dimensions.VerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            if (brightnessPosition == 0) {
                brightness()
            }
            tiles()
            media()
            if (brightnessPosition == 1) {
                brightness()
            }
        }
    }
}
