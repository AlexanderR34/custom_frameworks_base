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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.TileRevealFlag
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

private val MagicOSCardRadius = 24.dp
private val MagicOSTextSecondary = Color(0xB3FFFFFF)

@Composable
private fun magicOsCardBackground(): Color {
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
}

/**
 * Honor MagicOS Style Control Center Layout
 */
@Composable
fun MagicOSControlCenter(
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header Superior: "Control Center" a la izquierda, Editar y Ajustes a la derecha
        MagicOSHeader(
            onEditClick = { qsContainerViewModel.editModeViewModel.startEditing() },
            onSettingsClick = { toolbarViewModel.settingsButtonViewModel?.onClick?.invoke() },
        )

        // Fila 1: Tarjetas superiores 2x2 (Conectividad dual izquierda + Sugerencias derecha)
        MagicOSTopCards(
            wifiManager = wifiManager,
            onWifiClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("internet"))
            },
            onBluetoothClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("bt"))
            },
            onFlashlightClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("flashlight"))
            },
            onMobileDataClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cell"))
            },
        )

        // Fila 2: Media Player Cuadrado (Izquierda) + Sliders Verticales (Derecha)
        MagicOSMediaAndSlidersRow(
            brightnessViewModel = qsContainerViewModel.brightnessSliderViewModel,
            volumeSliderViewModel = volumeSliderViewModel,
            audioManager = audioManager,
            onMediaOutputClick = {
                qsContainerViewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                    audioDetailsViewModelFactory.create()
                )
            },
        )

        // Fila 3: Contenedor único de Mosaicos QS con flecha sutil de expansión
        MagicOSCommonTilesCard(
            qsContainerViewModel = qsContainerViewModel,
        )

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Header superior estilo MagicOS: "Control Center" a la izquierda, Editar y Ajustes a la derecha
 */
@Composable
private fun MagicOSHeader(
    onEditClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Control Center",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Botón Editar (Lápiz en recuadro)
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(magicOsCardBackground())
                        .clickable(onClick = onEditClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_menu_edit),
                    contentDescription = "Edit",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            // Botón Ajustes (Engranaje)
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(magicOsCardBackground())
                        .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_menu_preferences),
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Fila 1: Tarjetas superiores 2x2
 * - Bloque izquierdo: Wi-Fi y Bluetooth en lista vertical
 * - Bloque derecho: Accesos directos / Sugerencias de IA
 */
@Composable
private fun MagicOSTopCards(
    wifiManager: WifiManager?,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onFlashlightClick: () -> Unit,
    onMobileDataClick: () -> Unit,
) {
    val isWifiOn = remember(wifiManager) { wifiManager?.isWifiEnabled == true }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val isBtOn = remember(bluetoothAdapter) { bluetoothAdapter?.isEnabled == true }

    val wifiSsid = remember(isWifiOn, wifiManager) {
        if (isWifiOn) {
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") ssid else "Connected"
        } else {
            "Off"
        }
    }

    val btSubtitle = remember(isBtOn) {
        if (isBtOn) "Connected" else "Off"
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Bloque izquierdo: Conectividad Dual (Wi-Fi + Bluetooth)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(MagicOSCardRadius))
                    .background(magicOsCardBackground())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Fila Wi-Fi
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onWifiClick)
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isWifiOn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_wifi_signal_4),
                            contentDescription = "Wi-Fi",
                            tint = if (isWifiOn) MaterialTheme.colorScheme.onPrimary else MagicOSTextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isWifiOn) wifiSsid else "Wi-Fi",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (isWifiOn) "Wi-Fi" else "Off",
                            color = MagicOSTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MagicOSTextSecondary,
                        modifier = Modifier.size(16.dp).rotate(-90f),
                    )
                }

                // Fila Bluetooth
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onBluetoothClick)
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isBtOn) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_bt_hearing_aid),
                            contentDescription = "Bluetooth",
                            tint = if (isBtOn) MaterialTheme.colorScheme.onPrimary else MagicOSTextSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBtOn) btSubtitle else "Bluetooth",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (isBtOn) "Bluetooth" else "Off",
                            color = MagicOSTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MagicOSTextSecondary,
                        modifier = Modifier.size(16.dp).rotate(-90f),
                    )
                }
            }
        }

        // Bloque derecho: Sugerencias / Accesos directos de IA
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(MagicOSCardRadius))
                    .background(magicOsCardBackground())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Fila Linterna / Torch
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onFlashlightClick)
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_menu_today),
                            contentDescription = "Torch",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Torch",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = "AI Suggestions",
                            color = MagicOSTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }

                // Fila Datos Móviles
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onMobileDataClick)
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_menu_preferences),
                            contentDescription = "Mobile data",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mobile data",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = "AI Suggestions",
                            color = MagicOSTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fila 2: Media Player Cuadrado (Izquierda) + Sliders Verticales (Derecha)
 */
@Composable
private fun MagicOSMediaAndSlidersRow(
    brightnessViewModel: BrightnessSliderViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioManager: AudioManager?,
    onMediaOutputClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Reproductor de Música Cuadrado (Lado izquierdo)
        Box(
            modifier =
                Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(MagicOSCardRadius))
                    .background(magicOsCardBackground())
                    .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Cabecera del reproductor: Icono nota musical + Selector de salida
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_audio_vol),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable(onClick = onMediaOutputClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_btn_speak_now),
                            contentDescription = "Output",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Título central
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Not playing",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Controles de transporte
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Anterior
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    audioManager?.dispatchMediaKeyEvent(down)
                                    audioManager?.dispatchMediaKeyEvent(up)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_media_previous),
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    // Play / Pausa
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                    audioManager?.dispatchMediaKeyEvent(down)
                                    audioManager?.dispatchMediaKeyEvent(up)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_media_play),
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Siguiente
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
                                    audioManager?.dispatchMediaKeyEvent(down)
                                    audioManager?.dispatchMediaKeyEvent(up)
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_media_next),
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Deslizador de Brillo Vertical
        Box(modifier = Modifier.weight(0.48f).fillMaxHeight()) {
            VerticalBrightnessSlider(
                viewModel = brightnessViewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Deslizador de Volumen Vertical
        if (volumeSliderViewModel != null) {
            Box(modifier = Modifier.weight(0.48f).fillMaxHeight()) {
                VerticalVolumeSlider(
                    viewModel = volumeSliderViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Fila 3: Contenedor único de Mosaicos QS con flecha sutil en la base
 */
/**
 * Fila 3: Tarjeta Grande de Mosaicos Circulares con Etiquetas Inferiores estilo MagicOS
 */
@Composable
private fun MagicOSCommonTilesCard(
    qsContainerViewModel: QuickSettingsContainerViewModel,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(MagicOSCardRadius))
                .background(magicOsCardBackground())
                .padding(horizontal = 10.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Fila 1 de Mosaicos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MagicOSTileItem(
                    label = "Personal hotspot",
                    iconRes = com.android.internal.R.drawable.ic_wifi_signal_4,
                    isActive = false,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("hotspot"))
                    },
                )
                MagicOSTileItem(
                    label = "Screenshot",
                    iconRes = com.android.internal.R.drawable.ic_menu_edit,
                    isActive = false,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("screenshot"))
                    },
                )
                MagicOSTileItem(
                    label = "Torch",
                    iconRes = com.android.internal.R.drawable.ic_sysbar_flashlight,
                    isActive = false,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("flashlight"))
                    },
                )
                MagicOSTileItem(
                    label = "Sound",
                    iconRes = com.android.internal.R.drawable.ic_audio_vol,
                    isActive = true,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("dnd"))
                    },
                )
            }

            // Fila 2 de Mosaicos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MagicOSTileItem(
                    label = "Auto-rotate",
                    iconRes = com.android.internal.R.drawable.ic_screen_rotation,
                    isActive = true,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("rotation"))
                    },
                )
                MagicOSTileItem(
                    label = "HONOR Share",
                    iconRes = com.android.internal.R.drawable.ic_menu_share,
                    isActive = false,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("nearby"))
                    },
                )
                MagicOSTileItem(
                    label = "Aeroplane",
                    iconRes = com.android.internal.R.drawable.ic_menu_preferences,
                    isActive = false,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("airplane"))
                    },
                )
                MagicOSTileItem(
                    label = "Mobile data",
                    iconRes = com.android.internal.R.drawable.ic_qs_data_saver,
                    isActive = false,
                    onClick = {
                        qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cell"))
                    },
                )
            }

            // Fila Expandida opcional
            if (isExpanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MagicOSTileItem(
                        label = "Location",
                        iconRes = com.android.internal.R.drawable.ic_menu_today,
                        isActive = false,
                        onClick = {
                            qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("location"))
                        },
                    )
                    MagicOSTileItem(
                        label = "Eye comfort",
                        iconRes = com.android.internal.R.drawable.ic_zen_mode_type_bedtime,
                        isActive = false,
                        onClick = {
                            qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("night"))
                        },
                    )
                    MagicOSTileItem(
                        label = "Screen record",
                        iconRes = com.android.internal.R.drawable.ic_media_play,
                        isActive = false,
                        onClick = {
                            qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("screenrecord"))
                        },
                    )
                    MagicOSTileItem(
                        label = "Dark mode",
                        iconRes = com.android.internal.R.drawable.ic_btn_speak_now,
                        isActive = false,
                        onClick = {
                            qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("dark_theme"))
                        },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            // Flecha sutil en la parte inferior central para expandir
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center,
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    label = "ChevronRotation",
                )
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_expand_more),
                    contentDescription = "Expand/Collapse",
                    tint = MagicOSTextSecondary,
                    modifier = Modifier.size(20.dp).rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun MagicOSTileItem(
    label: String,
    iconRes: Int,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
    ) {
        Box(
            modifier =
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color(0xFF2563EB) else Color(0x28FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

