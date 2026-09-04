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

package com.android.systemui.qs.panels.ui.compose

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel

@Composable
fun EditMode(viewModel: EditModeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val gridLayout by viewModel.gridLayout.collectAsStateWithLifecycle()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle(emptyList())

    BackHandler { viewModel.stopEditing() }

    DisposableEffect(Unit) { onDispose { viewModel.stopEditing() } }

    var blurIntensity by remember {
        mutableIntStateOf(
            try {
                Settings.System.getInt(context.contentResolver, "custom_blur_intensity", 50)
            } catch (e: Throwable) {
                50
            }
        )
    }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                try {
                    blurIntensity = Settings.System.getInt(context.contentResolver, "custom_blur_intensity", 50)
                } catch (e: Throwable) {
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor("custom_blur_intensity"),
                false,
                observer
            )
        } catch (e: Throwable) {
        }
        onDispose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Throwable) {
            }
        }
    }

    val alpha = if (blurIntensity > 0) {
        (0.85f - (blurIntensity / 100f) * 0.40f).coerceIn(0.35f, 0.95f)
    } else {
        1.0f
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
    ) {
        Column(modifier.fillMaxSize()) {
            gridLayout.EditTileGrid(
                tiles,
                Modifier,
                viewModel::addTile,
                viewModel::removeTile,
                viewModel::setTiles,
                viewModel::stopEditing,
            )
        }
    }
}
