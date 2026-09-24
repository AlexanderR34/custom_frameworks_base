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

import android.app.UiModeManager
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformSliderDefaults
import com.android.systemui.brightness.ui.compose.BrightnessSliderContainer
import com.android.systemui.brightness.ui.compose.ContainerColors
import com.android.systemui.qs.composefragment.ui.GridAnchor
import com.android.systemui.qs.flags.TileRevealFlag
import com.android.systemui.qs.panels.ui.compose.TileGrid
import com.android.systemui.qs.panels.ui.viewmodel.toolbar.ToolbarViewModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.dialog.AudioDetailsViewModel
import com.android.systemui.qs.ui.viewmodel.QuickSettingsContainerViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.ui.composable.OverlayShade
import com.android.systemui.volume.panel.component.volume.slider.ui.viewmodel.AudioStreamSliderViewModel
import com.android.systemui.volume.panel.component.volume.ui.composable.VolumeSlider

private val OneUICardRadius = 24.dp
private val OneUITextSecondary = Color(0xB3FFFFFF)

@Composable
private fun oneUiCardBackground(): Color {
    // Tinta translúcida con acento Monet del sistema
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
}

/**
 * Samsung One UI (One UI 6 / 7) Style Control Center Layout
 */
@Composable
fun OneUIControlCenter(
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
    val uiModeManager = remember { context.getSystemService(UiModeManager::class.java) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Header superior: Operador a la izquierda, acciones a la derecha
        OneUIHeader(
            onEditClick = { qsContainerViewModel.editModeViewModel.startEditing() },
            onPowerClick = { toolbarViewModel.powerButtonViewModel?.onClick?.invoke() },
            onSettingsClick = { toolbarViewModel.settingsButtonViewModel?.onClick?.invoke() },
        )

        // 2. Tarjetas principales superiores (Wi-Fi y Bluetooth)
        OneUIPrimaryCards(
            wifiManager = wifiManager,
            onWifiTileClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("internet"))
            },
            onBluetoothTileClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("bt"))
            },
        )

        // 3. Tarjeta Matriz 4x2 de Mosaicos Circulares Unificada con barra de arrastre
        OneUIMatrixCard(
            onTileClick = { spec ->
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create(spec))
            }
        )

        // 4. Tarjeta de Sliders Horizontales (Brillo + Modo Oscuro, Volumen + Perfil de Sonido)
        OneUISlidersCard(
            qsContainerViewModel = qsContainerViewModel,
            volumeSliderViewModel = volumeSliderViewModel,
            uiModeManager = uiModeManager,
            audioManager = audioManager,
            isTransparencyEnabled = isTransparencyEnabled,
        )

        // 5. Barra de Reproducción y Salida Multimedia
        OneUIMediaRow(
            onPlayMusicClick = {
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                audioManager?.dispatchMediaKeyEvent(down)
                audioManager?.dispatchMediaKeyEvent(up)
            },
            onMediaOutputClick = {
                qsContainerViewModel.detailsViewModel.onVolumeSettingsButtonClicked(
                    audioDetailsViewModelFactory.create()
                )
            },
        )

        // 6. Panel inferior de Controles de Dispositivo (SmartThings / Nearby / Modes)
        OneUIDeviceControls(
            onNearbyClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("nearby"))
            },
            onSmartThingsClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("controls"))
            },
            onSmartViewClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("cast"))
            },
            onModesClick = {
                qsContainerViewModel.detailsViewModel.onTileClicked(TileSpec.create("dnd"))
            },
        )

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Header superior alineado estilo One UI (Operador / Estado a la izquierda, Iconos a la derecha)
 */
@Composable
private fun OneUIHeader(
    onEditClick: () -> Unit,
    onPowerClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Emergency calls only",
            color = Color(0xB3FFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Botón Editar (Lápiz)
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_menu_edit),
                contentDescription = "Edit",
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable(onClick = onEditClick),
            )

            // Botón Power (Apagado)
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_lock_power_off),
                contentDescription = "Power",
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable(onClick = onPowerClick),
            )

            // Botón Ajustes (Engranaje)
            Icon(
                painter = painterResource(com.android.internal.R.drawable.ic_menu_preferences),
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(20.dp).clickable(onClick = onSettingsClick),
            )
        }
    }
}

/**
 * Tarjeta Matriz 4x2 de iconos circulares One UI con barra de arrastre inferior
 */
@Composable
private fun OneUIMatrixCard(
    onTileClick: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OneUICardRadius))
                .background(oneUiCardBackground())
                .padding(horizontal = 14.dp, vertical = 14.dp),
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
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_screen_rotation,
                    contentDescription = "Rotation",
                    isActive = true,
                    onClick = { onTileClick("rotation") },
                )
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_menu_preferences,
                    contentDescription = "Airplane",
                    isActive = false,
                    onClick = { onTileClick("airplane") },
                )
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_sysbar_flashlight,
                    contentDescription = "Torch",
                    isActive = false,
                    onClick = { onTileClick("flashlight") },
                )
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_qs_data_saver,
                    contentDescription = "Mobile Data",
                    isActive = true,
                    onClick = { onTileClick("cell") },
                )
            }

            // Fila 2 de Mosaicos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_wifi_signal_4,
                    contentDescription = "Hotspot",
                    isActive = false,
                    onClick = { onTileClick("hotspot") },
                )
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_lock_power_off,
                    contentDescription = "Power Saving",
                    isActive = false,
                    onClick = { onTileClick("battery") },
                )
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_menu_today,
                    contentDescription = "Location",
                    isActive = true,
                    onClick = { onTileClick("location") },
                )
                OneUICircleTile(
                    iconRes = com.android.internal.R.drawable.ic_menu_share,
                    contentDescription = "Quick Share",
                    isActive = false,
                    onClick = { onTileClick("nearby") },
                )
            }

            Spacer(Modifier.height(4.dp))

            // Barra pill de arrastre One UI
            Box(
                modifier =
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x40FFFFFF))
            )
        }
    }
}

@Composable
private fun OneUICircleTile(
    iconRes: Int,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isActive) Color.White else Color(0x20FFFFFF))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (isActive) Color.Black else Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Tarjetas principales superiores de Wi-Fi y Bluetooth
 */
@Composable
private fun OneUIPrimaryCards(
    wifiManager: WifiManager?,
    onWifiTileClick: () -> Unit,
    onBluetoothTileClick: () -> Unit,
) {
    val isWifiOn = remember(wifiManager) { wifiManager?.isWifiEnabled == true }
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    val isBtOn = remember(bluetoothAdapter) { bluetoothAdapter?.isEnabled == true }

    val wifiSsid = remember(isWifiOn, wifiManager) {
        if (isWifiOn) {
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.replace("\"", "")
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") ssid else "Conectado"
        } else {
            "Desactivado"
        }
    }

    val btSubtitle = remember(isBtOn) {
        if (isBtOn) "Activado" else "Desactivado"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Tarjeta Wi-Fi
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(74.dp)
                    .clip(RoundedCornerShape(OneUICardRadius))
                    .background(oneUiCardBackground())
                    .clickable(onClick = onWifiTileClick)
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
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
                        tint = if (isWifiOn) MaterialTheme.colorScheme.onPrimary else OneUITextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wi-Fi",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = wifiSsid,
                        color = OneUITextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Tarjeta Bluetooth
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(74.dp)
                    .clip(RoundedCornerShape(OneUICardRadius))
                    .background(oneUiCardBackground())
                    .clickable(onClick = onBluetoothTileClick)
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
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
                        tint = if (isBtOn) MaterialTheme.colorScheme.onPrimary else OneUITextSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bluetooth",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = btSubtitle,
                        color = OneUITextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta de Sliders Horizontales (Brillo con toggle Modo Oscuro, Volumen con toggle Perfil Sonido)
 */
@Composable
private fun OneUISlidersCard(
    qsContainerViewModel: QuickSettingsContainerViewModel,
    volumeSliderViewModel: AudioStreamSliderViewModel?,
    uiModeManager: UiModeManager?,
    audioManager: AudioManager?,
    isTransparencyEnabled: Boolean,
) {
    var isNightMode by remember {
        mutableStateOf(uiModeManager?.nightMode == UiModeManager.MODE_NIGHT_YES)
    }

    var ringerMode by remember {
        mutableIntStateOf(audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL)
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(OneUICardRadius))
                .background(oneUiCardBackground())
                .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Fila 1: Slider de Brillo + Botón Modo Oscuro
            if (qsContainerViewModel.isBrightnessSliderVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        BrightnessSliderContainer(
                            viewModel = qsContainerViewModel.brightnessSliderViewModel,
                            containerColors =
                                ContainerColors(
                                    idleColor = Color.Transparent,
                                    mirrorColor =
                                        OverlayShade.Colors.panelBackground(isTransparencyEnabled),
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            dimensions = QuickSettingsShade.Dimensions.brightnessSliderDimensions,
                        )
                    }

                    // Botón circular Modo Oscuro (Luna) adaptado a Monet
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isNightMode) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    val newMode =
                                        if (isNightMode) UiModeManager.MODE_NIGHT_NO
                                        else UiModeManager.MODE_NIGHT_YES
                                    uiModeManager?.setNightModeActivated(!isNightMode)
                                    isNightMode = !isNightMode
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(com.android.internal.R.drawable.ic_zen_mode_type_bedtime),
                            contentDescription = "Dark Mode",
                            tint = if (isNightMode) MaterialTheme.colorScheme.onPrimary else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Fila 2: Slider de Volumen + Botón Perfil de Sonido
            if (volumeSliderViewModel != null) {
                val volumeSliderState by volumeSliderViewModel.slider.collectAsStateWithLifecycle()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        VolumeSlider(
                            modifier = Modifier.fillMaxWidth(),
                            showLabel = false,
                            state = volumeSliderState,
                            onValueChange = { newValue: Float ->
                                volumeSliderViewModel.onValueChanged(volumeSliderState, newValue)
                            },
                            onValueChangeFinished = {
                                volumeSliderViewModel.onValueChangeFinished()
                            },
                            onIconTapped = { volumeSliderViewModel.toggleMuted(volumeSliderState) },
                            sliderColors = PlatformSliderDefaults.defaultPlatformSliderColors(),
                            hapticsViewModelFactory =
                                volumeSliderViewModel.getSliderHapticsViewModelFactory(),
                            dimensions = QuickSettingsShade.Dimensions.VolumeSliderDimensions,
                        )
                    }

                    // Botón circular Perfil de Sonido adaptado a Monet
                    val ringerIcon = when (ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> com.android.internal.R.drawable.ic_audio_vol_mute
                        AudioManager.RINGER_MODE_VIBRATE -> com.android.internal.R.drawable.ic_audio_ring_notif_vibrate
                        else -> com.android.internal.R.drawable.ic_audio_vol
                    }

                    val isRingerActive = ringerMode != AudioManager.RINGER_MODE_NORMAL
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isRingerActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    val nextMode = when (ringerMode) {
                                        AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                                        AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                                        else -> AudioManager.RINGER_MODE_NORMAL
                                    }
                                    audioManager?.ringerMode = nextMode
                                    ringerMode = nextMode
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(ringerIcon),
                            contentDescription = "Sound Mode",
                            tint = if (isRingerActive) MaterialTheme.colorScheme.onPrimary else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fila de Reproducción y Salida Multimedia
 */
@Composable
private fun OneUIMediaRow(
    onPlayMusicClick: () -> Unit,
    onMediaOutputClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Botón "Reproducir música"
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(OneUICardRadius))
                    .background(oneUiCardBackground())
                    .clickable(onClick = onPlayMusicClick)
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(com.android.internal.R.drawable.ic_media_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Reproducir música",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Botón "Salida multimedia"
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(OneUICardRadius))
                    .background(oneUiCardBackground())
                    .clickable(onClick = onMediaOutputClick)
                    .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Salida multimedia",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Panel inferior de Controles de Dispositivo (SmartThings / Nearby / Modes)
 */
@Composable
private fun OneUIDeviceControls(
    onNearbyClick: () -> Unit,
    onSmartThingsClick: () -> Unit,
    onSmartViewClick: () -> Unit,
    onModesClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Fila 1: Dispositivos cercanos + SmartThings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OneUIDevicePill(
                title = "Dispositivos cercanos",
                subtitle = "Compartir",
                iconRes = com.android.internal.R.drawable.ic_contact_picture,
                modifier = Modifier.weight(1f),
                onClick = onNearbyClick,
            )
            OneUIDevicePill(
                title = "SmartThings",
                subtitle = "Control de hogar",
                iconRes = com.android.internal.R.drawable.ic_menu_preferences,
                modifier = Modifier.weight(1f),
                onClick = onSmartThingsClick,
            )
        }

        // Fila 2: Smart View + Modos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OneUIDevicePill(
                title = "Smart View",
                subtitle = "Duplicar pantalla",
                iconRes = com.android.internal.R.drawable.ic_btn_speak_now,
                modifier = Modifier.weight(1f),
                onClick = onSmartViewClick,
            )
            OneUIDevicePill(
                title = "Modos",
                subtitle = "Rutinas y DND",
                iconRes = com.android.internal.R.drawable.ic_lock_idle_alarm,
                modifier = Modifier.weight(1f),
                onClick = onModesClick,
            )
        }
    }
}

@Composable
private fun OneUIDevicePill(
    title: String,
    subtitle: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(56.dp)
                .clip(RoundedCornerShape(OneUICardRadius))
                .background(oneUiCardBackground())
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = OneUITextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
