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

package com.android.systemui.statusbar.phone.island

import android.app.NotificationManager
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import com.android.systemui.res.R
import java.util.Locale
import kotlin.math.abs

/**
 * Controller for Transient Dynamic Island System Banners .
 * Observes system battery (90W HyperCharge Ultra), Bluetooth audio (LDAC Hi-Res),
 * Ringer Mode and DND events to display interactive island banners.
 */
class SystemIslandBannerController(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) {
    companion object {
        const val SETTING_ISLAND_MASTER = "status_bar_island_master"
        const val SETTING_ISLAND_CHARGING = "status_bar_island_charging"
        const val SETTING_ISLAND_BLUETOOTH = "status_bar_island_bluetooth_banner"
        const val SETTING_ISLAND_RINGER = "status_bar_island_ringer_banner"
    }

    private val banner = SystemIslandBanner(context)

    private var wasCharging = false
    private var lastRingerMode: Int? = null
    private var lastInterruptionFilter: Int? = null
    private var isAttached = false

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (ctx == null || intent == null) return
            val action = intent.action ?: return

            if (!isMasterEnabled()) return

            when (action) {
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_BATTERY_CHANGED -> {
                    handleBatteryEvent(intent)
                }
                Intent.ACTION_BATTERY_LOW -> {
                    handleBatteryLowEvent(intent)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    wasCharging = false
                }
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    handleBluetoothEvent(intent)
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    handleRingerModeEvent(intent)
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    handleDndEvent()
                }
            }
        }
    }

    private fun isMasterEnabled(): Boolean {
        return try {
            Settings.System.getIntForUser(
                context.contentResolver,
                SETTING_ISLAND_MASTER,
                1,
                UserHandle.USER_CURRENT
            ) == 1
        } catch (e: Exception) {
            true
        }
    }

    private fun isFeatureEnabled(key: String): Boolean {
        return try {
            Settings.System.getIntForUser(
                context.contentResolver,
                key,
                1,
                UserHandle.USER_CURRENT
            ) == 1
        } catch (e: Exception) {
            true
        }
    }

    private fun handleBatteryEvent(intent: Intent) {
        if (!isFeatureEnabled(SETTING_ISLAND_CHARGING)) return

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val isNewConnection = (intent.action == Intent.ACTION_POWER_CONNECTED) || (!wasCharging && isCharging)
        wasCharging = isCharging

        if (!isNewConnection || !isCharging) return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100 / scale) else level

        val watts = calculateChargingWatts(intent)

        mainHandler.post {
            val title = context.getString(R.string.island_charging_battery)
            val tintColor = Color.parseColor("#81C995")

            val subtitle = if (watts > 0.5) {
                val wFmt = if (watts >= 10.0) String.format(Locale.US, "%.0fW", watts) else String.format(Locale.US, "%.1fW", watts)
                "$batteryPct% • $wFmt"
            } else {
                "$batteryPct% • ${context.getString(R.string.island_charging_battery)}"
            }

            val badge = if (watts > 0.5) {
                if (watts >= 10.0) String.format(Locale.US, "%.0fW", watts) else String.format(Locale.US, "%.1fW", watts)
            } else null

            val icon = R.drawable.ic_hyper_charging

            banner.show(
                iconRes = icon,
                title = title,
                subtitle = subtitle,
                badge = badge,
                tintColor = tintColor,
                durationMs = 4000L,
            )
        }
    }

    private fun calculateChargingWatts(intent: Intent): Double {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        var voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (voltageMv <= 0) {
            val vFiles = listOf("/sys/class/power_supply/battery/voltage_now", "/sys/class/power_supply/mt6375-gauge/voltage_now")
            for (vf in vFiles) {
                try {
                    val f = java.io.File(vf)
                    if (f.exists() && f.canRead()) {
                        val vRaw = f.readText().trim().toLongOrNull() ?: 0L
                        if (vRaw > 100_000) { voltageMv = (vRaw / 1000).toInt(); break }
                        else if (vRaw > 1000) { voltageMv = vRaw.toInt(); break }
                    }
                } catch (_: Exception) {}
            }
        }
        val voltageVolts = if (voltageMv > 1000) voltageMv.toDouble() / 1000.0 else if (voltageMv > 0) voltageMv.toDouble() else 4.2

        if (bm != null) {
            val currentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (currentNow != 0 && currentNow != Int.MIN_VALUE) {
                val absCurrent = abs(currentNow).toDouble()
                val currentAmps = when {
                    absCurrent > 10_000 -> absCurrent / 1_000_000.0
                    absCurrent in 20.0..10_000.0 -> absCurrent / 1_000.0
                    else -> 0.0
                }
                if (currentAmps > 0.05) {
                    val w = currentAmps * voltageVolts
                    if (w in 0.5..120.0) return w
                }
            }
        }
        return 0.0
    }

    private var hasShownLowBatteryAlert = false

    private fun handleBatteryLowEvent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 20)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = if (scale > 0) (level * 100 / scale) else level
        if (hasShownLowBatteryAlert) return
        hasShownLowBatteryAlert = true
        mainHandler.post {
            banner.show(
                iconRes = R.drawable.battery_meter_progress_low,
                title = context.getString(R.string.island_battery_low),
                subtitle = String.format(Locale.getDefault(), context.getString(R.string.island_battery_low_sub), batteryPct),
                badge = "$batteryPct%",
                tintColor = Color.parseColor("#FF453A"),
                durationMs = 4500L,
            )
        }
    }

    private fun handleBluetoothEvent(intent: Intent) {
        if (!isFeatureEnabled(SETTING_ISLAND_BLUETOOTH)) return

        val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, -1)
        if (state == BluetoothA2dp.STATE_CONNECTED) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                ?: return

            val deviceName = try {
                device.alias ?: device.name ?: context.getString(R.string.island_headphones_connected)
            } catch (e: Exception) {
                context.getString(R.string.island_headphones_connected)
            }

            val battery = try {
                device.batteryLevel
            } catch (e: Exception) {
                -1
            }

            mainHandler.post {
                val subtitle = context.getString(R.string.island_headphones_hires)
                val badge = if (battery in 0..100) "$battery%" else null

                banner.show(
                    iconRes = R.drawable.ic_hyper_headphones,
                    title = deviceName,
                    subtitle = subtitle,
                    badge = badge,
                    tintColor = Color.parseColor("#78D9EC"),
                    durationMs = 3800L,
                )
            }
        }
    }

    private fun handleRingerModeEvent(intent: Intent) {
        if (!isFeatureEnabled(SETTING_ISLAND_RINGER)) return

        val ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
        if (ringerMode == -1) return

        if (lastRingerMode == null) {
            lastRingerMode = ringerMode
            return
        }

        if (lastRingerMode == ringerMode) return
        lastRingerMode = ringerMode

        mainHandler.post {
            when (ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> {
                    banner.show(
                        iconRes = R.drawable.ic_hyper_silent,
                        title = context.getString(R.string.island_mode_silent),
                        subtitle = context.getString(R.string.island_silent_desc),
                        tintColor = Color.parseColor("#FF5252"),
                        durationMs = 3000L,
                    )
                }
                AudioManager.RINGER_MODE_VIBRATE -> {
                    banner.show(
                        iconRes = R.drawable.ic_hyper_vibrate,
                        title = context.getString(R.string.island_mode_vibrate),
                        subtitle = context.getString(R.string.island_vibrate_desc),
                        tintColor = Color.parseColor("#FFB74D"),
                        durationMs = 3000L,
                    )
                }
                AudioManager.RINGER_MODE_NORMAL -> {
                    banner.show(
                        iconRes = R.drawable.ic_hyper_sound,
                        title = context.getString(R.string.island_mode_sound),
                        subtitle = context.getString(R.string.island_sound_desc),
                        tintColor = Color.parseColor("#81C784"),
                        durationMs = 3000L,
                    )
                }
            }
        }
    }

    private fun handleDndEvent() {
        if (!isFeatureEnabled(SETTING_ISLAND_RINGER)) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val filter = nm.currentInterruptionFilter

        if (lastInterruptionFilter == null) {
            lastInterruptionFilter = filter
            return
        }

        if (lastInterruptionFilter == filter) return
        lastInterruptionFilter = filter

        val isDnd = (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN)

        mainHandler.post {
            banner.show(
                iconRes = R.drawable.ic_hyper_dnd,
                title = context.getString(R.string.island_mode_dnd),
                subtitle = if (isDnd) context.getString(R.string.island_dnd_active_desc) else context.getString(R.string.island_dnd_inactive_desc),
                tintColor = if (isDnd) Color.parseColor("#B388FF") else Color.parseColor("#9E9E9E"),
                durationMs = 3000L,
            )
        }
    }

    fun attach() {
        if (isAttached) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        try {
            context.registerReceiver(systemReceiver, filter, Context.RECEIVER_EXPORTED)
            isAttached = true
        } catch (e: Exception) {
            try {
                context.registerReceiver(systemReceiver, filter)
                isAttached = true
            } catch (ignored: Exception) {}
        }
    }

    fun detach() {
        if (!isAttached) return
        try {
            context.unregisterReceiver(systemReceiver)
        } catch (ignored: Exception) {}
        banner.dismissWithAnimation()
        isAttached = false
    }
}
