package com.android.systemui.statusbar.phone.island.compose

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.android.systemui.res.R

enum class IslandIconType {
    TORCH,
    CALL,
    DOWNLOAD,
    CLIPBOARD,
    CHARGING,
    CHARGING_SUPER,
    CHARGING_TURBO,
    NAVIGATION,
    TIMER,
    HOTSPOT,
    CAST,
    HEADPHONES,
    DND,
    SILENT,
    VIBRATE,
    SOUND,
    MUSIC
}

@Composable
fun IslandIcon(
    type: IslandIconType,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    contentDescription: String? = null,
    styleOverride: IconPackStyle? = null
) {
    val style = styleOverride ?: ConfigManager.config.value.iconPackStyle
    if (style == IconPackStyle.HYPEROS) {
        val resId = when (type) {
            IslandIconType.TORCH -> R.drawable.ic_hyper_torch
            IslandIconType.CALL -> R.drawable.ic_hyper_call
            IslandIconType.DOWNLOAD -> R.drawable.ic_hyper_download
            IslandIconType.CLIPBOARD -> R.drawable.ic_hyper_clipboard
            IslandIconType.CHARGING -> R.drawable.ic_hyper_charging
            IslandIconType.CHARGING_SUPER -> R.drawable.ic_hyper_charging_super
            IslandIconType.CHARGING_TURBO -> R.drawable.ic_hyper_charging_turbo
            IslandIconType.NAVIGATION -> R.drawable.ic_hyper_navigation
            IslandIconType.TIMER -> R.drawable.ic_hyper_timer
            IslandIconType.HOTSPOT -> R.drawable.ic_hyper_hotspot
            IslandIconType.CAST -> R.drawable.ic_hyper_cast
            IslandIconType.HEADPHONES -> R.drawable.ic_hyper_headphones
            IslandIconType.DND -> R.drawable.ic_hyper_dnd
            IslandIconType.SILENT -> R.drawable.ic_hyper_silent
            IslandIconType.VIBRATE -> R.drawable.ic_hyper_vibrate
            IslandIconType.SOUND -> R.drawable.ic_hyper_sound
            IslandIconType.MUSIC -> R.drawable.ic_hyper_music
        }
        Icon(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    } else {
        val vector: ImageVector = when (type) {
            IslandIconType.TORCH -> Icons.Default.FlashlightOn
            IslandIconType.CALL -> Icons.Default.PhoneInTalk
            IslandIconType.DOWNLOAD -> Icons.Default.Download
            IslandIconType.CLIPBOARD -> Icons.Default.ContentPaste
            IslandIconType.CHARGING,
            IslandIconType.CHARGING_SUPER,
            IslandIconType.CHARGING_TURBO -> Icons.Default.Bolt
            IslandIconType.NAVIGATION -> Icons.Default.NearMe
            IslandIconType.TIMER -> Icons.Default.Timer
            IslandIconType.HOTSPOT -> Icons.Default.WifiTethering
            IslandIconType.CAST -> Icons.Default.Cast
            IslandIconType.HEADPHONES -> Icons.Default.Headphones
            IslandIconType.DND -> Icons.Default.DoNotDisturbOn
            IslandIconType.SILENT -> Icons.Default.NotificationsOff
            IslandIconType.VIBRATE -> Icons.Default.Vibration
            IslandIconType.SOUND -> Icons.Default.Notifications
            IslandIconType.MUSIC -> Icons.Default.MusicNote
        }
        Icon(
            imageVector = vector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier
        )
    }
}
