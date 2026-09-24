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
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.systemui.brightness.ui.viewmodel.BrightnessSliderViewModel
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.TileRevealFlag
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel

private val IOSCardRadius = 28.dp
private val IOSTextSecondary = Color(0xB3FFFFFF)
private val IOSBlue = Color(0xFF007AFF)
private val IOSGreen = Color(0xFF34C759)
private val IOSOrange = Color(0xFFFF9500)
private val IOSPurple = Color(0xFFAF52DE)

@Composable
private fun iosCardBackground(): Color {
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
}

/**
 * Apple iOS (iOS 18) Style Multi-Page Bento Control Center Layout
 * Page 0: Favorites / Bento Grid Principal
 * Page 1: Full Music Player
 * Page 2: Full Connectivity Sheet
 */
@Composable
fun IOSControlCenter(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    toolbarViewModel: ToolbarViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioDetailsViewModelFactory: AudioDetailsViewModel.Factory,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val wifiManager = remember { context.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    var activeCategoryPage by remember { mutableIntStateOf(0) }
    var showCameraPopover by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Contenido Principal Dinámico según la página activa
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header Superior: '+' (añadir/editar) a la izquierda y Power a la derecha
                IOSHeader(
                    onAddEditClick = { qsContainerViewModel.editModeViewModel.startEditing() },
                    onPowerClick = { toolbarViewModel.powerButtonViewModel?.onClick?.invoke() },
                )

                when (activeCategoryPage) {
                    1 -> {
                        // PÁGINA 1: REPRODUCTOR MULTIMEDIA DE PANTALLA COMPLETA
                        IOSMusicFullPage(
                            audioManager = audioManager,
                            onAudioOutputClick = {
                                qsContainerViewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                                    audioDetailsViewModelFactory.create()
                                )
                            },
                        )
                    }
                    2 -> {
                        // PÁGINA 2: PÁGINA COMPLETA DE CONECTIVIDAD
                        IOSConnectivityFullPage(
                            wifiManager = wifiManager,
                            bluetoothAdapter = bluetoothAdapter,
                            onWifiClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("internet"))
                            },
                            onBluetoothClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("bt"))
                            },
                            onAirplaneClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("airplane"))
                            },
                            onCellularClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cell"))
                            },
                            onHotspotClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("hotspot"))
                            },
                            onVpnClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("vpn"))
                            },
                        )
                    }
                    else -> {
                        // PÁGINA 0: BENTO GRID PRINCIPAL (FAVORITOS)
                        // Fila 1: Bloques 2x2 (Conectividad a la izquierda + Reproductor a la derecha)
                        IOSRow1Bento(
                            wifiManager = wifiManager,
                            bluetoothAdapter = bluetoothAdapter,
                            onWifiClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("internet"))
                            },
                            onBluetoothClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("bt"))
                            },
                            onAirplaneClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("airplane"))
                            },
                            onCellularClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cell"))
                            },
                            onExpandConnectivity = { activeCategoryPage = 2 },
                            onExpandMusic = { activeCategoryPage = 1 },
                            audioManager = audioManager,
                            onAudioOutputClick = {
                                qsContainerViewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                                    audioDetailsViewModelFactory.create()
                                )
                            },
                        )

                        // Fila 2: Cápsulas 2x1 y Sliders Verticales 1x2
                        IOSRow2Bento(
                            brightnessViewModel = qsContainerViewModel.brightnessSliderViewModel,
                            volumeSliderViewModel = volumeSliderViewModel,
                            audioManager = audioManager,
                            onFocusClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("dnd"))
                            },
                            onRotateClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("rotation"))
                            },
                            onScreenMirrorClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cast"))
                            },
                        )

                        // Fila 3: Accesos Rápidos Circulares 1x1 (Linterna, Temporizador, etc.)
                        IOSRow3QuickTiles(
                            onTorchClick = {
                                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("flashlight"))
                            },
                            onTimerClick = {
                                val timerIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try { context.startActivity(timerIntent) } catch (_: Exception) {}
                            },
                            onCameraClick = {
                                val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(cameraIntent)
                            },
                            onCameraLongClick = {
                                showCameraPopover = true
                            },
                        )

                        // Fila 4: Contenedor de Mosaicos QS interactivos nativos
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(IOSCardRadius))
                                    .background(iosCardBackground())
                                    .padding(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                GridAnchor()
                                TileGrid(
                                    viewModel = qsContainerViewModel.tileGridViewModel,
                                    modifier = Modifier.fillMaxWidth(),
                                    enableRevealEffect = TileRevealFlag.isEnabled,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Riel Lateral de Navegación (Sidebar de categorías iOS 18: Favoritos, Música, Señal)
            IOSCategorySidebar(
                selectedPage = activeCategoryPage,
                onPageSelected = { activeCategoryPage = it },
            )
        }

        // Popover Flotante Contextual de Cámara en Long Press (Selfie, Video, Retrato)
        if (showCameraPopover) {
            IOSCameraPopover(
                onDismiss = { showCameraPopover = false },
                onOptionClick = { actionExtra ->
                    showCameraPopover = false
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        putExtra(actionExtra, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                },
            )
        }
    }
}

/**
 * Header Superior: Botón circular '+' a la izquierda, Botón circular Power a la derecha
 */
@Composable
private fun IOSHeader(
    onAddEditClick: () -> Unit,
    onPowerClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onAddEditClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onPowerClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_lock_power_off),
                contentDescription = "Power",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * Fila 1 Bento:
 * - Izquierda: Módulo de Conectividad 2x2 (Avión, AirDrop, Wi-Fi + Mini-cuadrante de 4 iconos: Datos, BT, Hotspot, VPN)
 * - Derecha: Reproductor Multimedia 2x2 con portada y controles
 */
@Composable
private fun IOSRow1Bento(
    wifiManager: WifiManager?,
    bluetoothAdapter: BluetoothAdapter?,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onAirplaneClick: () -> Unit,
    onCellularClick: () -> Unit,
    onExpandConnectivity: () -> Unit,
    onExpandMusic: () -> Unit,
    audioManager: AudioManager?,
    onAudioOutputClick: () -> Unit,
) {
    val isWifiOn = remember(wifiManager) { wifiManager?.isWifiEnabled == true }
    val isBtOn = remember(bluetoothAdapter) { bluetoothAdapter?.isEnabled == true }

    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Módulo de Conectividad 2x2
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(IOSCardRadius))
                    .background(iosCardBackground())
                    .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Fila Superior: Modo Avión + AirDrop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Modo Avión
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .clickable(onClick = onAirplaneClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_audio_vol_mute),
                            contentDescription = "Airplane",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // AirDrop
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .clickable(onClick = onExpandConnectivity),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_wifi_signal_4),
                            contentDescription = "AirDrop",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // Fila Inferior: Wi-Fi + Mini-cuadrante 2x2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Wi-Fi (Azul iOS activo)
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isWifiOn) IOSBlue else Color(0x28FFFFFF))
                                .clickable(onClick = onWifiClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_wifi_signal_4),
                            contentDescription = "Wi-Fi",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // Mini-cuadrante 2x2 de Datos, BT, Hotspot, VPN (Tocar expande a pantalla de conectividad)
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x20FFFFFF))
                                .clickable(onClick = onExpandConnectivity)
                                .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                // Datos móviles (Verde)
                                Box(
                                    modifier = Modifier.size(20.dp).clip(CircleShape).background(IOSGreen),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(com.android.internal.R.drawable.ic_signal_cellular_4_bar),
                                        contentDescription = "Cellular",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                                // Bluetooth (Azul)
                                Box(
                                    modifier = Modifier.size(20.dp).clip(CircleShape).background(if (isBtOn) IOSBlue else Color(0x40FFFFFF)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(com.android.internal.R.drawable.ic_bt_hearing_aid),
                                        contentDescription = "Bluetooth",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                // Hotspot (Naranja/Gris)
                                Box(
                                    modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0x40FFFFFF)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(com.android.internal.R.drawable.ic_perm_device_information),
                                        contentDescription = "Hotspot",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                                // Satélite/VPN
                                Box(
                                    modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0x40FFFFFF)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(com.android.internal.R.drawable.ic_lock_outline),
                                        contentDescription = "VPN",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reproductor Multimedia 2x2
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(IOSCardRadius))
                    .background(iosCardBackground())
                    .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top: Carátula pequeña + Título y Salida AirPlay
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onExpandMusic),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Thumbnail de Álbum
                        Box(
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33FFFFFF)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(com.android.internal.R.drawable.ic_audio_vol),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Respect",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Aretha Franklin",
                                color = IOSTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Botón AirPlay
                    Box(
                        modifier =
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x28FFFFFF))
                                .clickable(onClick = onAudioOutputClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_media_route_off),
                            contentDescription = "AirPlay",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Transport Controls
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
    }
}

/**
 * Fila 2 Bento:
 * - Columna 1 y 2: Botón Rotación 1x1 + Botón Screen Mirroring 1x1 arriba + Cápsula 2x1 Focus abajo
 * - Columna 3: Slider vertical 1x2 Brillo
 * - Columna 4: Slider vertical 1x2 Volumen
 */
@Composable
private fun IOSRow2Bento(
    brightnessViewModel: BrightnessSliderViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    audioManager: AudioManager?,
    onFocusClick: () -> Unit,
    onRotateClick: () -> Unit,
    onScreenMirrorClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Dos botones circulares 1x1: Rotación + Screen Mirroring
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Rotación
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(iosCardBackground())
                            .clickable(onClick = onRotateClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_screen_rotation),
                        contentDescription = "Rotation",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Screen Mirroring (Cast / Smart View)
                Box(
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(iosCardBackground())
                            .clickable(onClick = onScreenMirrorClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(com.android.internal.R.drawable.ic_media_route_off),
                        contentDescription = "Screen Mirroring",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Cápsula horizontal 2x1 Focus (Modo Concentración)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(iosCardBackground())
                        .clickable(onClick = onFocusClick)
                        .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(IOSPurple),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_do_not_disturb_on),
                            contentDescription = "Focus",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Text(
                        text = "Focus",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Columna 3: Slider Vertical de Brillo 1x2
        IOSVerticalSlider(
            modifier = Modifier.weight(0.5f).fillMaxHeight(),
            iconRes = com.android.internal.R.drawable.ic_btn_speak_now,
            contentDescription = "Brightness",
            initialValue = 0.65f,
            onValueChange = {},
        )

        // Columna 4: Slider Vertical de Volumen 1x2
        IOSVerticalSlider(
            modifier = Modifier.weight(0.5f).fillMaxHeight(),
            iconRes = com.android.internal.R.drawable.ic_audio_vol,
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

/**
 * Slider Vertical de 1x2 con cápsula redondeada
 */
@Composable
private fun IOSVerticalSlider(
    iconRes: Int,
    contentDescription: String,
    initialValue: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderProgress by remember { mutableFloatStateOf(initialValue) }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(36.dp))
                .background(iosCardBackground())
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(sliderProgress)
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
        )

        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (sliderProgress > 0.45f) Color.Black else Color.White,
            modifier = Modifier.size(22.dp).align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

/**
 * Fila 3: Quick Tiles 1x1 circulares (Linterna, Temporizador, Cámara con Popover)
 */
@Composable
private fun IOSRow3QuickTiles(
    onTorchClick: () -> Unit,
    onTimerClick: () -> Unit,
    onCameraClick: () -> Unit,
    onCameraLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IOSQuickCircleButton(
            iconRes = com.android.internal.R.drawable.ic_sysbar_flashlight,
            contentDescription = "Torch",
            onClick = onTorchClick,
        )

        IOSQuickCircleButton(
            iconRes = com.android.internal.R.drawable.ic_menu_today,
            contentDescription = "Timer",
            onClick = onTimerClick,
        )

        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iosCardBackground())
                    .combinedClickable(
                        onClick = onCameraClick,
                        onLongClick = onCameraLongClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_camera),
                contentDescription = "Camera",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun IOSQuickCircleButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(iosCardBackground())
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * PÁGINA 1: Reproductor Multimedia de Pantalla Completa iOS 18
 */
@Composable
private fun IOSMusicFullPage(
    audioManager: AudioManager?,
    onAudioOutputClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(IOSCardRadius))
                .background(iosCardBackground())
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Gran Portada del Álbum
        Box(
            modifier =
                Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x33FFFFFF))
                    .shadow(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_audio_vol),
                contentDescription = "Album Artwork",
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }

        // Título, Artista y Menú
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Respect",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Aretha Franklin",
                    color = IOSTextSecondary,
                    fontSize = 15.sp,
                )
            }
            Text(
                text = "···",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Timeline Scrubber
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x33FFFFFF))
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.35f)
                            .fillMaxHeight()
                            .background(Color.White)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("0:02", color = IOSTextSecondary, fontSize = 11.sp)
                Text("-2:22", color = IOSTextSecondary, fontSize = 11.sp)
            }
        }

        // Controles de Reproducción
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_media_previous),
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(28.dp).clickable {
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
                modifier = Modifier.size(40.dp).clickable {
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
                modifier = Modifier.size(28.dp).clickable {
                    val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                    val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
                    audioManager?.dispatchMediaKeyEvent(down)
                    audioManager?.dispatchMediaKeyEvent(up)
                },
            )
        }

        // Píldora de Dispositivo Activo (AirPlay / Salida)
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x33FFFFFF))
                    .clickable(onClick = onAudioOutputClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_media_route_off),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "iPhone",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * PÁGINA 2: Página Completa de Conectividad iOS 18
 */
@Composable
private fun IOSConnectivityFullPage(
    wifiManager: WifiManager?,
    bluetoothAdapter: BluetoothAdapter?,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onAirplaneClick: () -> Unit,
    onCellularClick: () -> Unit,
    onHotspotClick: () -> Unit,
    onVpnClick: () -> Unit,
) {
    val isWifiOn = remember(wifiManager) { wifiManager?.isWifiEnabled == true }
    val isBtOn = remember(bluetoothAdapter) { bluetoothAdapter?.isEnabled == true }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Airplane Mode
        IOSConnectivityPill(
            title = "Airplane Mode",
            subtitle = "Off",
            iconRes = com.android.internal.R.drawable.ic_audio_vol_mute,
            isActive = false,
            activeColor = IOSOrange,
            onClick = onAirplaneClick,
        )

        // AirDrop
        IOSConnectivityPill(
            title = "AirDrop",
            subtitle = "Contacts Only",
            iconRes = com.android.internal.R.drawable.ic_wifi_signal_4,
            isActive = true,
            activeColor = IOSBlue,
            hasChevron = true,
            onClick = onWifiClick,
        )

        // Wi-Fi
        IOSConnectivityPill(
            title = "Wi-Fi",
            subtitle = if (isWifiOn) "USS Enterprise" else "Off",
            iconRes = com.android.internal.R.drawable.ic_wifi_signal_4,
            isActive = isWifiOn,
            activeColor = IOSBlue,
            hasChevron = true,
            onClick = onWifiClick,
        )

        // Cellular Data
        IOSConnectivityPill(
            title = "Cellular Data",
            subtitle = "On",
            iconRes = com.android.internal.R.drawable.ic_signal_cellular_4_bar,
            isActive = true,
            activeColor = IOSGreen,
            onClick = onCellularClick,
        )

        // Bluetooth
        IOSConnectivityPill(
            title = "Bluetooth",
            subtitle = if (isBtOn) "On" else "Off",
            iconRes = com.android.internal.R.drawable.ic_bt_hearing_aid,
            isActive = isBtOn,
            activeColor = IOSBlue,
            hasChevron = true,
            onClick = onBluetoothClick,
        )

        // Personal Hotspot
        IOSConnectivityPill(
            title = "Personal Hotspot",
            subtitle = "Off",
            iconRes = com.android.internal.R.drawable.ic_perm_device_information,
            isActive = false,
            activeColor = IOSGreen,
            onClick = onHotspotClick,
        )

        // VPN
        IOSConnectivityPill(
            title = "VPN",
            subtitle = "Off",
            iconRes = com.android.internal.R.drawable.ic_lock_outline,
            isActive = false,
            activeColor = IOSBlue,
            onClick = onVpnClick,
        )
    }
}

@Composable
private fun IOSConnectivityPill(
    title: String,
    subtitle: String,
    iconRes: Int,
    isActive: Boolean,
    activeColor: Color,
    hasChevron: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(iosCardBackground())
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isActive) activeColor else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = IOSTextSecondary,
                fontSize = 12.sp,
            )
        }

        if (hasChevron) {
            Text(
                text = ">",
                color = IOSTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Riel Lateral de Navegación (Sidebar de categorías iOS 18: Favoritos, Música, Señal)
 */
@Composable
private fun IOSCategorySidebar(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(28.dp)
                .fillMaxHeight()
                .padding(vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icono Favoritos (Corazón) -> Página 0
        Icon(
            painter = painterResource(com.android.internal.R.drawable.ic_menu_today),
            contentDescription = "Favorites",
            tint = if (selectedPage == 0) Color.White else Color(0x66FFFFFF),
            modifier = Modifier.size(18.dp).clickable { onPageSelected(0) },
        )

        // Icono Música (Nota Musical) -> Página 1
        Icon(
            painter = painterResource(com.android.internal.R.drawable.ic_audio_vol),
            contentDescription = "Music",
            tint = if (selectedPage == 1) Color.White else Color(0x66FFFFFF),
            modifier = Modifier.size(18.dp).clickable { onPageSelected(1) },
        )

        // Icono Señal / Conectividad (Antena) -> Página 2
        Icon(
            painter = painterResource(com.android.internal.R.drawable.ic_wifi_signal_4),
            contentDescription = "Connectivity",
            tint = if (selectedPage == 2) Color.White else Color(0x66FFFFFF),
            modifier = Modifier.size(18.dp).clickable { onPageSelected(2) },
        )
    }
}

/**
 * Popover Flotante Contextual de Cámara en Long Press (Selfie, Video, Retrato)
 */
@Composable
private fun IOSCameraPopover(
    onDismiss: () -> Unit,
    onOptionClick: (String) -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier =
                Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE62C2C2E))
                    .padding(vertical = 8.dp)
                    .shadow(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                IOSPopoverItem(
                    title = "Take Selfie",
                    iconRes = com.android.internal.R.drawable.ic_contact_picture,
                    onClick = { onOptionClick("android.intent.extra.USE_FRONT_CAMERA") },
                )
                IOSPopoverItem(
                    title = "Record Video",
                    iconRes = com.android.internal.R.drawable.ic_media_play,
                    onClick = { onOptionClick("android.intent.extra.VIDEO_QUALITY") },
                )
                IOSPopoverItem(
                    title = "Take Portrait",
                    iconRes = com.android.internal.R.drawable.ic_menu_today,
                    onClick = { onOptionClick("android.intent.extra.STILL_IMAGE_CAMERA") },
                )
                IOSPopoverItem(
                    title = "Take Portrait Selfie",
                    iconRes = com.android.internal.R.drawable.ic_contact_picture,
                    onClick = { onOptionClick("android.intent.extra.USE_FRONT_CAMERA") },
                )
            }
        }
    }
}

@Composable
private fun IOSPopoverItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
