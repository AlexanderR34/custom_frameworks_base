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

package com.android.systemui.qs.ui.composable

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.media.controls.ui.composable.Media
import com.android.systemui.media.controls.ui.view.MediaPresentationStyle
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

private val HyperOSBlue = Color(0xFF0084FF)
private val HyperOSGreen = Color(0xFF10B981)
private val HyperOSCardBg = Color(0x35FFFFFF)
private val HyperOSTileBg = Color(0x24FFFFFF)

/**
 * Xiaomi HyperOS Control Center Layout (1:1 Réplica de la captura de referencia)
 */
@Composable
fun HyperOSControlCenter(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    toolbarViewModel: ToolbarViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioDetailsViewModelFactory: AudioDetailsViewModel.Factory,
    isTransparencyEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val wifiManager = remember { context.getSystemService(WifiManager::class.java) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Fila 1: Dos grandes cápsulas blancas superiores (Wi-Fi y Datos móviles)
        HyperOSTopCapsules(
            wifiManager = wifiManager,
            onWifiClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("internet"))
            },
            onCellularClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cell"))
            },
        )

        // Fila 2: Media Card Cuadrada + Sliders Verticales de Brillo y Volumen
        HyperOSMidRow(
            qsContainerViewModel = qsContainerViewModel,
            volumeSliderViewModel = volumeSliderViewModel,
            audioManager = audioManager,
            audioDetailsViewModelFactory = audioDetailsViewModelFactory,
        )

        // Fila 3: Xiaomi Smart Hub Cápsula Ancha
        HyperOSSmartHubPill(
            onClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("controls"))
            },
        )

        // Filas 4 a 7: Matriz 4x4 de 16 Mosaicos Circulares Fieles a HyperOS
        HyperOSCircularTilesMatrix(
            bluetoothAdapter = bluetoothAdapter,
            onTileClick = { spec ->
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create(spec))
            },
            onSettingsClick = {
                toolbarViewModel.settingsButtonViewModel?.onClick?.invoke()
            },
        )

        Spacer(Modifier.height(4.dp))

        // Botón Editar estilo cápsula centrada en la base
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x28FFFFFF))
                        .clickable { qsContainerViewModel.editModeViewModel.startEditing() }
                        .padding(horizontal = 24.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "Editar",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HyperOSTopCapsules(
    wifiManager: WifiManager?,
    onWifiClick: () -> Unit,
    onCellularClick: () -> Unit,
) {
    val isWifiOn = remember(wifiManager) { wifiManager?.isWifiEnabled == true }
    val wifiSsid = remember(isWifiOn, wifiManager) {
        if (isWifiOn) {
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") ssid else "FAMILIA_OS"
        } else {
            "Desactivado"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Cápsula Wi-Fi
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isWifiOn) Color.White else HyperOSCardBg)
                    .clickable(onClick = onWifiClick)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_wifi_signal_4),
                    contentDescription = "Wi-Fi",
                    tint = if (isWifiOn) HyperOSBlue else Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        text = wifiSsid,
                        color = if (isWifiOn) Color.Black else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isWifiOn) "Conectado" else "Desactivado",
                        color = if (isWifiOn) Color(0xFF666666) else Color(0xB3FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Cápsula Datos Móviles
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White)
                    .clickable(onClick = onCellularClick)
                    .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_qs_data_saver),
                    contentDescription = "Datos móviles",
                    tint = HyperOSGreen,
                    modifier = Modifier.size(28.dp),
                )
                Column {
                    Text(
                        text = "Datos móviles",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Activado",
                        color = Color(0xFF666666),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun HyperOSMidRow(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioManager: AudioManager?,
    audioDetailsViewModelFactory: AudioDetailsViewModel.Factory,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Media Card Cuadrada
        Box(
            modifier =
                Modifier
                    .weight(1.8f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(HyperOSCardBg)
                    .padding(12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_audio_vol),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_media_route_off),
                        contentDescription = "Cast",
                        tint = Color(0xB3FFFFFF),
                        modifier = Modifier.size(20.dp).clickable {
                            qsContainerViewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                                audioDetailsViewModelFactory.create()
                            )
                        },
                    )
                }

                Column {
                    Text(
                        text = "One More Time",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Daft Punk",
                        color = Color(0xB3FFFFFF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_media_previous),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).clickable {
                            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            audioManager?.dispatchMediaKeyEvent(down)
                            audioManager?.dispatchMediaKeyEvent(up)
                        },
                    )
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_media_play),
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp).clickable {
                            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                            audioManager?.dispatchMediaKeyEvent(down)
                            audioManager?.dispatchMediaKeyEvent(up)
                        },
                    )
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_media_next),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).clickable {
                            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
                            audioManager?.dispatchMediaKeyEvent(down)
                            audioManager?.dispatchMediaKeyEvent(up)
                        },
                    )
                }
            }
        }

        // Slider Vertical de Brillo
        HyperOSVerticalSlider(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            iconRes = com.android.internal.R.drawable.ic_btn_speak_now,
            iconTint = Color(0xFFF59E0B),
            contentDescription = "Brightness",
            initialValue = 0.55f,
            onValueChange = {},
        )

        // Slider Vertical de Volumen
        HyperOSVerticalSlider(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            iconRes = com.android.internal.R.drawable.ic_audio_vol,
            iconTint = HyperOSBlue,
            contentDescription = "Volume",
            initialValue = 0.50f,
            onValueChange = { percent ->
                audioManager?.let { am ->
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent).toInt(), 0)
                }
            },
        )
    }
}

@Composable
private fun HyperOSVerticalSlider(
    iconRes: Int,
    iconTint: Color,
    contentDescription: String,
    initialValue: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(initialValue) }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(HyperOSCardBg)
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
        )

        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp).align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun HyperOSSmartHubPill(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(HyperOSCardBg)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
            }
            Text(
                text = "Xiaomi Smart Hub",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HyperOSCircularTilesMatrix(
    bluetoothAdapter: BluetoothAdapter?,
    onTileClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val isBtOn = remember(bluetoothAdapter) { bluetoothAdapter?.isEnabled == true }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Fila A: Bluetooth, Modo Avión, Silencio, Linterna
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_bt_hearing_aid,
                contentDescription = "Bluetooth",
                isActive = isBtOn,
                onClick = { onTileClick("bt") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_menu_preferences,
                contentDescription = "Airplane",
                isActive = false,
                onClick = { onTileClick("airplane") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_audio_vol_mute,
                contentDescription = "Mute",
                isActive = false,
                onClick = { onTileClick("dnd") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_sysbar_flashlight,
                contentDescription = "Torch",
                isActive = false,
                onClick = { onTileClick("flashlight") },
            )
        }

        // Fila B: Captura de Pantalla, Ahorro de Batería, Giro Automático, Ajustes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_menu_edit,
                contentDescription = "Screenshot",
                isActive = false,
                onClick = { onTileClick("screenshot") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_lock_power_off,
                contentDescription = "Battery Saver",
                isActive = false,
                onClick = { onTileClick("battery") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_screen_rotation,
                contentDescription = "Rotation",
                isActive = true,
                onClick = { onTileClick("rotation") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_menu_preferences,
                contentDescription = "Settings",
                isActive = false,
                onClick = onSettingsClick,
            )
        }

        // Fila C: Sincronización, Escáner QR, Mi Wallet, Dolby Atmos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_menu_today,
                contentDescription = "Sync",
                isActive = false,
                onClick = { onTileClick("sync") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_camera,
                contentDescription = "QR Scanner",
                isActive = false,
                onClick = { onTileClick("qr_code_scanner") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_menu_manage,
                contentDescription = "Wallet",
                isActive = false,
                onClick = { onTileClick("wallet") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_audio_vol,
                contentDescription = "Dolby Atmos",
                isActive = true,
                onClick = { onTileClick("sound_effects") },
            )
        }

        // Fila D: Grabar Pantalla, Modo Rendimiento, Zona Wi-Fi, Ubicación GPS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_media_play,
                contentDescription = "Screen Record",
                isActive = false,
                onClick = { onTileClick("screenrecord") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_btn_speak_now,
                contentDescription = "Performance",
                isActive = false,
                onClick = { onTileClick("performance") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_wifi_signal_4,
                contentDescription = "Hotspot",
                isActive = false,
                onClick = { onTileClick("hotspot") },
            )
            HyperOSCircleButton(
                iconRes = com.android.internal.R.drawable.ic_menu_today,
                contentDescription = "Location",
                isActive = true,
                onClick = { onTileClick("location") },
            )
        }
    }
}

@Composable
private fun HyperOSCircleButton(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isActive) Color.White else HyperOSTileBg)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (isActive) HyperOSBlue else Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}
