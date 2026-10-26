package com.android.systemui.statusbar.phone.island.compose

import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.MediaController
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.systemui.res.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DynamicIslandMediaState(
    val hasActiveMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastUpdateTimeMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val packageName: String = "",
    val appIcon: Bitmap? = null
)

enum class TransientBannerType {
    NONE,
    CHARGING,
    BATTERY_LOW,
    SILENT_MODE,
    DND,
    CLIPBOARD,
    HEADPHONES,
    TORCH,
    HOTSPOT
}

data class TransientBannerState(
    val type: TransientBannerType = TransientBannerType.NONE,
    val title: String = "",
    val subtitle: String = "",
    val titleResId: Int = 0,
    val subtitleResId: Int = 0,
    val iconName: String = "",
    val tintColor: Long = 0xFF30D158,
    val targetWidthDp: Float = 220f
)

data class CopiedItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DynamicIslandBatteryState(
    val isCharging: Boolean = false,
    val level: Int = 100,
    val watts: Double = 0.0,
    val showBanner: Boolean = false
)

data class TorchState(
    val isOn: Boolean = false,
    val cameraId: String = "0"
)

data class ScreenRecordState(
    val isRecording: Boolean = false,
    val startTimeMs: Long = 0L,
    val stopIntent: PendingIntent? = null
)

data class CallState(
    val isActive: Boolean = false,
    val contactName: String = "",
    val startTimeMs: Long = 0L,
    val hangupIntent: PendingIntent? = null
)

data class DownloadState(
    val isDownloading: Boolean = false,
    val fileName: String = "",
    val progress: Int = 0,
    val cancelIntent: PendingIntent? = null,
    val appIcon: Bitmap? = null,
    val speedText: String = "",
    val totalSizeText: String = "",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val isCancelled: Boolean = false
)

data class HotspotState(
    val isEnabled: Boolean = false,
    val connectedDevices: Int = 0,
    val showIntroBanner: Boolean = false
)

data class NavigationState(
    val isActive: Boolean = false,
    val maneuver: String = "TURN_RIGHT",
    val instruction: String = "",
    val distanceText: String = "",
    val etaText: String = "",
    val appPackage: String = "com.google.android.apps.maps",
    val appIcon: Bitmap? = null,
    val openIntent: PendingIntent? = null
)

enum class TimerType {
    TIMER,
    STOPWATCH,
    ALARM
}

data class TimerState(
    val isActive: Boolean = false,
    val type: TimerType = TimerType.TIMER,
    val label: String = "Temporizador",
    val remainingTimeSeconds: Int = 0,
    val totalTimeSeconds: Int = 0,
    val elapsedTimeMs: Long = 0L,
    val isPaused: Boolean = false,
    val pauseResumeIntent: PendingIntent? = null,
    val cancelIntent: PendingIntent? = null,
    val alarmDismissIntent: PendingIntent? = null
)

data class CastState(
    val isCasting: Boolean = false,
    val deviceName: String = "",
    val castType: String = "Pantalla",
    val disconnectIntent: PendingIntent? = null
)

data class PrivacyState(
    val isCameraInUse: Boolean = false,
    val isMicInUse: Boolean = false,
    val isLocationInUse: Boolean = false,
    val appName: String = ""
) {
    val hasAnyPrivacyActive: Boolean get() = isCameraInUse || isMicInUse || isLocationInUse
}

enum class IslandActivityType {
    NONE,
    TRANSIENT_BANNER,
    CALL,
    NAVIGATION,
    TIMER,
    CAST,
    PRIVACY,
    TORCH,
    SCREEN_RECORD,
    DOWNLOAD,
    HOTSPOT,
    MEDIA
}

object DynamicIslandManager {
    val mediaState = MutableStateFlow(DynamicIslandMediaState())
    val batteryState = MutableStateFlow(DynamicIslandBatteryState())
    val transientBanner = MutableStateFlow(TransientBannerState())
    val torchState = MutableStateFlow(TorchState())
    val screenRecordState = MutableStateFlow(ScreenRecordState())
    val callState = MutableStateFlow(CallState())
    val downloadState = MutableStateFlow(DownloadState())
    val hotspotState = MutableStateFlow(HotspotState())
    val navigationState = MutableStateFlow(NavigationState())
    val timerState = MutableStateFlow(TimerState())
    val castState = MutableStateFlow(CastState())
    val privacyState = MutableStateFlow(PrivacyState())
    val clipboardHistory = MutableStateFlow<List<CopiedItem>>(emptyList())
    val isClipboardCardActive = MutableStateFlow(false)
    val manualSelectedActivity = MutableStateFlow<IslandActivityType?>(null)

    var onOpenMediaOutput: (() -> Unit)? = null

    fun openMediaOutput() {
        onOpenMediaOutput?.invoke()
    }

    val isExpanded = MutableStateFlow(false)
    val isOverlayActive = MutableStateFlow(false)

    private var bannerJob: Job? = null
    private var clipboardJob: Job? = null
    private var hotspotIntroJob: Job? = null
    private var inactivityJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoDismissBannerRunnable = Runnable {
        dismissTransientBanner()
    }
    private var isInitialized = false
    private var appContext: Context? = null

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        val appContext = context.applicationContext
        this.appContext = appContext

        try {
            val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.addPrimaryClipChangedListener {
                try {
                    val clip = cm.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0)?.coerceToText(appContext)?.toString() ?: ""
                        if (text.isNotBlank()) {
                            onClipboardCopied(text)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DynamicIslandManager", "Error en clipListener", e)
                }
            }
        } catch (_: Exception) {}

        try {
            val cam = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cam?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    updateTorch(enabled, cameraId)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {}

        try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            am?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    if (addedDevices == null) return
                    for (device in addedDevices) {
                        val type = device.type
                        val isHeadphone = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                                type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                type == AudioDeviceInfo.TYPE_USB_HEADSET

                        if (isHeadphone) {
                            val rawName = device.productName?.toString()?.trim() ?: ""
                            val name = if (rawName.isNotBlank()) rawName else "Audífonos"
                            val isBluetooth = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                    type == AudioDeviceInfo.TYPE_BLE_HEADSET
                            val battery = if (isBluetooth) extractBluetoothBattery(null, null) else "Conectado"
                            onHeadphonesConnected(name, battery)
                            break
                        }
                    }
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    if (removedDevices == null) return
                    for (device in removedDevices) {
                        val type = device.type
                        val isHeadphone = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                                type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                type == AudioDeviceInfo.TYPE_USB_HEADSET

                        if (isHeadphone) {
                            val rawName = device.productName?.toString()?.trim() ?: ""
                            val name = if (rawName.isNotBlank()) rawName else "Audífonos"
                            onHeadphonesDisconnected(name)
                            break
                        }
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {}

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
                addAction("android.net.conn.TETHER_STATE_CHANGED")
            }
            appContext.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    if (intent == null) return
                    when (intent.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            val name = try {
                                device?.name?.ifBlank { "Audífonos Bluetooth" } ?: "Audífonos Bluetooth"
                            } catch (_: SecurityException) {
                                "Audífonos Bluetooth"
                            }
                            val batteryText = extractBluetoothBattery(device, intent)
                            onHeadphonesConnected(name, batteryText)
                        }
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            val name = try {
                                device?.name?.ifBlank { "Audífonos Bluetooth" } ?: "Audífonos Bluetooth"
                            } catch (_: SecurityException) {
                                "Audífonos Bluetooth"
                            }
                            onHeadphonesDisconnected(name)
                        }
                        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                            val state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE, BluetoothA2dp.STATE_DISCONNECTED)
                            val prevState = intent.getIntExtra(BluetoothA2dp.EXTRA_PREVIOUS_STATE, -1)
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            val name = try {
                                device?.name?.ifBlank { "Audífonos" } ?: "Audífonos"
                            } catch (_: SecurityException) {
                                "Audífonos"
                            }
                            if (state == BluetoothA2dp.STATE_CONNECTED) {
                                val batteryText = extractBluetoothBattery(device, intent)
                                onHeadphonesConnected(name, batteryText)
                            } else if (state == BluetoothA2dp.STATE_DISCONNECTED && (prevState == BluetoothA2dp.STATE_CONNECTED || prevState == BluetoothA2dp.STATE_CONNECTING)) {
                                onHeadphonesDisconnected(name)
                            }
                        }
                        AudioManager.ACTION_HEADSET_PLUG -> {
                            val state = intent.getIntExtra("state", -1)
                            if (state == 1) {
                                val hasMic = intent.getIntExtra("microphone", 0) == 1
                                val name = if (hasMic) "Audífonos con mic" else "Audífonos"
                                onHeadphonesConnected(name, "Conectado")
                            } else if (state == 0) {
                                onHeadphonesDisconnected("Audífonos")
                            }
                        }
                        "android.net.wifi.WIFI_AP_STATE_CHANGED" -> {
                            val state = intent.getIntExtra("wifi_state", 14)
                            when (state) {
                                13 -> {
                                    val cur = hotspotState.value.connectedDevices
                                    updateHotspot(isEnabled = true, connectedDevices = cur)
                                }
                                11, 10 -> {
                                    updateHotspot(isEnabled = false, connectedDevices = 0)
                                }
                            }
                        }
                    }
                }
            }, filter)
        } catch (_: Exception) {}

        try {
            val cc = com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.CastController::class.java)
            cc?.addCallback {
                try {
                    val devices = cc.castDevices
                    var activeDevice: com.android.systemui.statusbar.policy.CastDevice? = null
                    for (d in devices) {
                        if (d.isCasting) {
                            activeDevice = d
                            break
                        }
                    }
                    val isCasting = activeDevice != null
                    val name = activeDevice?.name?.trim()?.ifBlank { "Alex" } ?: ""
                    val desc = activeDevice?.description?.ifBlank { "Transmitiendo pantalla" } ?: "Transmitiendo pantalla"
                    updateCast(isCasting = isCasting, deviceName = name, castType = desc)
                } catch (e: Exception) {
                    Log.e("DynamicIslandManager", "Error in cast callback", e)
                }
            }
        } catch (_: Exception) {}
    }

    fun openClipboardCard(timeoutMs: Long = 0L) {
        bannerJob?.cancel()
        clipboardJob?.cancel()
        isClipboardCardActive.value = true
        isExpanded.value = true
        if (timeoutMs > 0L) {
            clipboardJob = managerScope.launch {
                delay(timeoutMs)
                closeClipboardCard()
            }
        }
    }

    fun closeClipboardCard() {
        clipboardJob?.cancel()
        bannerJob?.cancel()
        isClipboardCardActive.value = false
        isExpanded.value = false
        transientBanner.value = TransientBannerState(type = TransientBannerType.NONE)
    }

    fun getExpandedCardHeightDp(
        currentActivity: IslandActivityType,
        bannerType: TransientBannerType = TransientBannerType.NONE,
        isClipboardActive: Boolean = false
    ): Float {
        if (isClipboardActive || bannerType == TransientBannerType.CLIPBOARD) {
            return 220f
        }
        return when (bannerType) {
            TransientBannerType.CHARGING -> 140f
            TransientBannerType.BATTERY_LOW -> 155f
            TransientBannerType.SILENT_MODE, TransientBannerType.DND -> 146f
            TransientBannerType.HEADPHONES -> 138f
            else -> when (currentActivity) {
                IslandActivityType.CALL -> 148f
                IslandActivityType.NAVIGATION -> 138f
                IslandActivityType.TIMER -> 144f
                IslandActivityType.CAST -> 175f
                IslandActivityType.HOTSPOT -> 138f
                IslandActivityType.TORCH -> 134f
                IslandActivityType.SCREEN_RECORD -> 134f
                IslandActivityType.PRIVACY -> 140f
                IslandActivityType.DOWNLOAD -> 155f
                IslandActivityType.MEDIA -> 175f
                IslandActivityType.TRANSIENT_BANNER -> 146f
                IslandActivityType.NONE -> 140f
            }
        }
    }

    fun selectActivity(type: IslandActivityType) {
        manualSelectedActivity.value = type
    }

    fun isActivityActive(type: IslandActivityType): Boolean {
        return when (type) {
            IslandActivityType.CALL -> callState.value.isActive
            IslandActivityType.NAVIGATION -> navigationState.value.isActive
            IslandActivityType.TIMER -> timerState.value.isActive
            IslandActivityType.MEDIA -> mediaState.value.hasActiveMedia
            IslandActivityType.DOWNLOAD -> downloadState.value.isDownloading || downloadState.value.isCompleted || downloadState.value.isFailed || downloadState.value.isCancelled
            IslandActivityType.CAST -> castState.value.isCasting
            IslandActivityType.HOTSPOT -> hotspotState.value.isEnabled
            IslandActivityType.SCREEN_RECORD -> screenRecordState.value.isRecording
            IslandActivityType.TORCH -> torchState.value.isOn
            IslandActivityType.PRIVACY -> privacyState.value.hasAnyPrivacyActive
            IslandActivityType.TRANSIENT_BANNER -> isClipboardCardActive.value || transientBanner.value.type != TransientBannerType.NONE
            IslandActivityType.NONE -> false
        }
    }

    fun getActiveActivities(): List<IslandActivityType> {
        val list = mutableListOf<IslandActivityType>()
        if (callState.value.isActive) list.add(IslandActivityType.CALL)
        if (navigationState.value.isActive) list.add(IslandActivityType.NAVIGATION)
        if (timerState.value.isActive) list.add(IslandActivityType.TIMER)
        if (mediaState.value.hasActiveMedia) list.add(IslandActivityType.MEDIA)
        if (downloadState.value.isDownloading || downloadState.value.isCompleted || downloadState.value.isFailed || downloadState.value.isCancelled) list.add(IslandActivityType.DOWNLOAD)
        if (castState.value.isCasting) list.add(IslandActivityType.CAST)
        if (hotspotState.value.isEnabled) list.add(IslandActivityType.HOTSPOT)
        if (screenRecordState.value.isRecording) list.add(IslandActivityType.SCREEN_RECORD)
        if (torchState.value.isOn) list.add(IslandActivityType.TORCH)
        if (privacyState.value.hasAnyPrivacyActive) list.add(IslandActivityType.PRIVACY)
        return list
    }

    fun currentActivity(): IslandActivityType {
        if (transientBanner.value.type != TransientBannerType.NONE) return IslandActivityType.TRANSIENT_BANNER

        val manual = manualSelectedActivity.value
        if (manual != null && isActivityActive(manual)) {
            return manual
        }

        val activeList = getActiveActivities()
        if (activeList.isNotEmpty()) {
            val priorityOrder = ConfigManager.config.value.activityPriorities
            val sorted = activeList.sortedBy { act ->
                val idx = priorityOrder.indexOf(act.name)
                if (idx >= 0) idx else 999
            }
            return sorted.first()
        }

        if (isClipboardCardActive.value) return IslandActivityType.TRANSIENT_BANNER

        return IslandActivityType.NONE
    }

    fun hasAnyActiveActivity(): Boolean {
        return isClipboardCardActive.value ||
                mediaState.value.hasActiveMedia ||
                transientBanner.value.type != TransientBannerType.NONE ||
                torchState.value.isOn ||
                screenRecordState.value.isRecording ||
                callState.value.isActive ||
                navigationState.value.isActive ||
                timerState.value.isActive ||
                castState.value.isCasting ||
                privacyState.value.hasAnyPrivacyActive ||
                downloadState.value.isDownloading ||
                downloadState.value.isCompleted ||
                downloadState.value.isFailed ||
                downloadState.value.isCancelled ||
                hotspotState.value.isEnabled
    }

    fun updateNavigation(
        isActive: Boolean,
        maneuver: String = "TURN_RIGHT",
        instruction: String = "",
        distanceText: String = "",
        etaText: String = "",
        appPackage: String = "com.google.android.apps.maps",
        appIcon: Bitmap? = null,
        openIntent: PendingIntent? = null
    ) {
        navigationState.value = NavigationState(
            isActive = isActive,
            maneuver = maneuver,
            instruction = instruction,
            distanceText = distanceText,
            etaText = etaText,
            appPackage = appPackage,
            appIcon = appIcon,
            openIntent = openIntent
        )
        if (!isActive && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun updateTimer(
        isActive: Boolean,
        type: TimerType = TimerType.TIMER,
        label: String = "Temporizador",
        remainingTimeSeconds: Int = 0,
        totalTimeSeconds: Int = 0,
        elapsedTimeMs: Long = 0L,
        isPaused: Boolean = false,
        pauseResumeIntent: PendingIntent? = null,
        cancelIntent: PendingIntent? = null,
        alarmDismissIntent: PendingIntent? = null
    ) {
        timerState.value = TimerState(
            isActive = isActive,
            type = type,
            label = label,
            remainingTimeSeconds = remainingTimeSeconds,
            totalTimeSeconds = totalTimeSeconds,
            elapsedTimeMs = elapsedTimeMs,
            isPaused = isPaused,
            pauseResumeIntent = pauseResumeIntent,
            cancelIntent = cancelIntent,
            alarmDismissIntent = alarmDismissIntent
        )
        if (!isActive && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun updateCast(
        isCasting: Boolean,
        deviceName: String = "",
        castType: String = "Pantalla",
        disconnectIntent: PendingIntent? = null
    ) {
        castState.value = CastState(
            isCasting = isCasting,
            deviceName = deviceName,
            castType = castType,
            disconnectIntent = disconnectIntent
        )
        if (!isCasting && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun disconnectCast() {
        try {
            val cc = com.android.systemui.Dependency.get(com.android.systemui.statusbar.policy.CastController::class.java)
            for (d in cc.castDevices) {
                if (d.isCasting) {
                    cc.stopCasting(d, android.media.projection.StopReason.STOP_QS_TILE)
                }
            }
        } catch (_: Exception) {}
        try {
            appContext?.sendBroadcast(Intent("android.intent.action.DISCONNECT_CAST"))
        } catch (_: Exception) {}
        updateCast(isCasting = false)
    }

    fun updatePrivacy(
        isCameraInUse: Boolean = false,
        isMicInUse: Boolean = false,
        isLocationInUse: Boolean = false,
        appName: String = ""
    ) {
        privacyState.value = PrivacyState(
            isCameraInUse = isCameraInUse,
            isMicInUse = isMicInUse,
            isLocationInUse = isLocationInUse,
            appName = appName
        )
        if (!privacyState.value.hasAnyPrivacyActive && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun updatePrivacyOp(op: String, active: Boolean, pkgName: String?) {
        val cur = privacyState.value
        val name = pkgName ?: cur.appName
        when (op) {
            android.app.AppOpsManager.OPSTR_CAMERA -> {
                updatePrivacy(
                    isCameraInUse = active,
                    isMicInUse = cur.isMicInUse,
                    isLocationInUse = cur.isLocationInUse,
                    appName = if (active) name else cur.appName
                )
            }
            android.app.AppOpsManager.OPSTR_RECORD_AUDIO -> {
                updatePrivacy(
                    isCameraInUse = cur.isCameraInUse,
                    isMicInUse = active,
                    isLocationInUse = cur.isLocationInUse,
                    appName = if (active) name else cur.appName
                )
            }
            android.app.AppOpsManager.OPSTR_FINE_LOCATION,
            android.app.AppOpsManager.OPSTR_COARSE_LOCATION -> {
                updatePrivacy(
                    isCameraInUse = cur.isCameraInUse,
                    isMicInUse = cur.isMicInUse,
                    isLocationInUse = active,
                    appName = if (active) name else cur.appName
                )
            }
        }
    }

    fun collapseIsland() {
        isExpanded.value = false
    }

    fun updateTorch(isOn: Boolean, cameraId: String = "0") {
        val wasOn = torchState.value.isOn
        val isTurnedOff = !isOn && wasOn
        if (isOn) {
            isClipboardCardActive.value = false
            clipboardJob?.cancel()
        }
        torchState.value = TorchState(isOn = isOn, cameraId = cameraId)
        if (isTurnedOff) {
            if (isExpanded.value) {
                collapseIsland()
            }
            showTransientBanner(
                type = TransientBannerType.TORCH,
                title = "Linterna apagada",
                titleResId = R.string.island_torch_off,
                subtitle = "",
                iconName = "flashlight_off",
                tintColor = 0xFFA1A1A6,
                targetWidthDp = 225f,
                durationMs = 3500L
            )
        } else if (!isOn && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun toggleTorch(context: Context) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val id = torchState.value.cameraId.ifBlank { "0" }
            val newState = !torchState.value.isOn
            cm?.setTorchMode(id, newState)
            torchState.value = TorchState(isOn = newState, cameraId = id)
            if (!newState && !hasAnyActiveActivity()) {
                isExpanded.value = false
            }
        } catch (e: Exception) {
            Log.e("DynamicIslandManager", "Error al cambiar linterna", e)
        }
    }

    fun updateScreenRecording(
        isRecording: Boolean,
        startTimeMs: Long = System.currentTimeMillis(),
        stopIntent: PendingIntent? = null
    ) {
        screenRecordState.value = ScreenRecordState(
            isRecording = isRecording,
            startTimeMs = if (isRecording) (if (startTimeMs > 0) startTimeMs else System.currentTimeMillis()) else 0L,
            stopIntent = stopIntent
        )
        if (!isRecording && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun stopScreenRecording() {
        val intent = screenRecordState.value.stopIntent
        if (intent != null) {
            try {
                intent.send()
            } catch (e: Exception) {
                Log.e("DynamicIslandManager", "Error al enviar stopIntent de grabador", e)
            }
        }
        screenRecordState.value = ScreenRecordState(isRecording = false)
        if (!hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun updateCall(
        isActive: Boolean,
        contactName: String = "",
        startTimeMs: Long = System.currentTimeMillis(),
        hangupIntent: PendingIntent? = null
    ) {
        callState.value = CallState(
            isActive = isActive,
            contactName = contactName,
            startTimeMs = if (isActive) (if (startTimeMs > 0) startTimeMs else System.currentTimeMillis()) else 0L,
            hangupIntent = hangupIntent
        )
        if (!isActive && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun endCall() {
        val intent = callState.value.hangupIntent
        if (intent != null) {
            try {
                intent.send()
            } catch (e: Exception) {
                Log.e("DynamicIslandManager", "Error al colgar llamada", e)
            }
        }
        callState.value = CallState(isActive = false)
        if (!hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    private var downloadCompletionJob: Job? = null

    fun dismissDownload() {
        downloadCompletionJob?.cancel()
        downloadCompletionJob = null
        downloadState.value = DownloadState()
        if (!hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun updateDownload(
        isDownloading: Boolean,
        fileName: String = "",
        progress: Int = 0,
        cancelIntent: PendingIntent? = null,
        appIcon: Bitmap? = null,
        speedText: String = "",
        totalSizeText: String = "",
        isCompleted: Boolean = false,
        isFailed: Boolean = false,
        isCancelled: Boolean = false
    ) {
        downloadCompletionJob?.cancel()
        downloadCompletionJob = null

        if (!isDownloading && !isCompleted && !isFailed && !isCancelled) {
            downloadState.value = DownloadState()
            if (!hasAnyActiveActivity()) {
                isExpanded.value = false
            }
            return
        }

        val cleanSpeed = if (speedText.isNotBlank() && speedText != "0 B") {
            speedText
        } else if (downloadState.value.speedText != "0 B") {
            downloadState.value.speedText
        } else {
            ""
        }

        downloadState.value = DownloadState(
            isDownloading = isDownloading,
            fileName = fileName.ifBlank { downloadState.value.fileName },
            progress = progress.coerceIn(0, 100),
            cancelIntent = cancelIntent ?: downloadState.value.cancelIntent,
            appIcon = appIcon ?: downloadState.value.appIcon,
            speedText = cleanSpeed,
            totalSizeText = if (totalSizeText.isNotBlank()) totalSizeText else downloadState.value.totalSizeText,
            isCompleted = isCompleted,
            isFailed = isFailed,
            isCancelled = isCancelled
        )

        if (isCompleted || isFailed || isCancelled) {
            downloadCompletionJob = managerScope.launch {
                delay(3500L)
                downloadState.value = DownloadState()
                if (!hasAnyActiveActivity()) {
                    isExpanded.value = false
                }
            }
        } else if (!isDownloading && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun onDownloadCompleted(fileName: String = "", appIcon: Bitmap? = null) {
        downloadCompletionJob?.cancel()
        downloadState.value = DownloadState(
            isDownloading = false,
            fileName = fileName.ifBlank { downloadState.value.fileName },
            progress = 100,
            cancelIntent = null,
            appIcon = appIcon ?: downloadState.value.appIcon,
            speedText = "",
            totalSizeText = "",
            isCompleted = true,
            isFailed = false
        )
        downloadCompletionJob = managerScope.launch {
            delay(3500L)
            downloadState.value = DownloadState()
            if (!hasAnyActiveActivity()) {
                isExpanded.value = false
            }
        }
    }

    fun onDownloadFailed(fileName: String = "", appIcon: Bitmap? = null) {
        testDownloadJob?.cancel()
        downloadCompletionJob?.cancel()
        downloadState.value = DownloadState(
            isDownloading = false,
            fileName = fileName.ifBlank { downloadState.value.fileName },
            progress = downloadState.value.progress,
            cancelIntent = null,
            appIcon = appIcon ?: downloadState.value.appIcon,
            speedText = "",
            totalSizeText = "",
            isCompleted = false,
            isFailed = true,
            isCancelled = false
        )
        downloadCompletionJob = managerScope.launch {
            delay(3500L)
            downloadState.value = DownloadState(isDownloading = false, isCompleted = false, isFailed = false, isCancelled = false)
            if (!hasAnyActiveActivity()) {
                isExpanded.value = false
            }
        }
    }

    fun onDownloadCancelled(fileName: String = "", appIcon: Bitmap? = null) {
        testDownloadJob?.cancel()
        downloadCompletionJob?.cancel()
        downloadState.value = DownloadState(
            isDownloading = false,
            fileName = fileName.ifBlank { downloadState.value.fileName },
            progress = downloadState.value.progress,
            cancelIntent = null,
            appIcon = appIcon ?: downloadState.value.appIcon,
            speedText = "",
            totalSizeText = "",
            isCompleted = false,
            isFailed = false,
            isCancelled = true
        )
        downloadCompletionJob = managerScope.launch {
            delay(3500L)
            downloadState.value = DownloadState(isDownloading = false, isCompleted = false, isFailed = false, isCancelled = false)
            if (!hasAnyActiveActivity()) {
                isExpanded.value = false
            }
        }
    }

    fun cancelDownload() {
        val current = downloadState.value
        val intent = current.cancelIntent
        if (intent != null) {
            try {
                intent.send()
            } catch (e: Exception) {
                Log.e("DynamicIslandManager", "Error al cancelar descarga", e)
            }
        }
        onDownloadCancelled(
            fileName = current.fileName,
            appIcon = current.appIcon
        )
    }

    fun extractBluetoothBattery(device: BluetoothDevice?, intent: Intent?): String {
        val intentBattery = intent?.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1) ?: -1
        if (intentBattery in 0..100) {
            return "$intentBattery%"
        }
        try {
            val method = device?.javaClass?.getMethod("getBatteryLevel")
            val level = method?.invoke(device) as? Int ?: -1
            if (level in 0..100) {
                return "$level%"
            }
        } catch (_: Throwable) {}
        return "Conectado"
    }

    fun onHeadphonesConnected(name: String, batteryLevel: String = "") {
        val displaySubtitle = if (batteryLevel.isNotBlank()) batteryLevel else "Conectado"
        showTransientBanner(
            type = TransientBannerType.HEADPHONES,
            title = name,
            subtitle = displaySubtitle,
            subtitleResId = if (batteryLevel.isBlank()) R.string.connected else 0,
            iconName = "headphones",
            tintColor = 0xFF78D9EC,
            targetWidthDp = 270f,
            durationMs = 4000L
        )
    }

    fun onHeadphonesDisconnected(name: String = "") {
        val displayName = name.ifBlank { "Audífonos" }
        showTransientBanner(
            type = TransientBannerType.HEADPHONES,
            title = displayName,
            subtitle = "Desconectado",
            titleResId = 0,
            subtitleResId = R.string.island_disconnected,
            iconName = "headphones_off",
            tintColor = 0xFFA1A1A6,
            targetWidthDp = 250f,
            durationMs = 3200L
        )
    }

    fun updateHotspot(isEnabled: Boolean, connectedDevices: Int = 0) {
        val wasEnabled = hotspotState.value.isEnabled
        val isNewActivation = isEnabled && !wasEnabled
        val isTurnedOff = !isEnabled && wasEnabled

        if (isEnabled) {
            isClipboardCardActive.value = false
            clipboardJob?.cancel()
        }

        if (isNewActivation) {
            hotspotIntroJob?.cancel()
            hotspotState.value = HotspotState(isEnabled = true, connectedDevices = connectedDevices, showIntroBanner = true)
            hotspotIntroJob = managerScope.launch {
                delay(4500L)
                hotspotState.value = hotspotState.value.copy(showIntroBanner = false)
            }
        } else {
            val currentIntro = if (!isEnabled) false else hotspotState.value.showIntroBanner
            hotspotState.value = HotspotState(isEnabled = isEnabled, connectedDevices = connectedDevices, showIntroBanner = currentIntro)
        }
        if (isTurnedOff) {
            if (isExpanded.value) {
                collapseIsland()
            }
            showTransientBanner(
                type = TransientBannerType.HOTSPOT,
                title = "Zona Wi-Fi desactivada",
                titleResId = R.string.island_hotspot_off,
                subtitle = "",
                iconName = "hotspot_off",
                tintColor = 0xFFA1A1A6,
                targetWidthDp = 245f,
                durationMs = 3500L
            )
        } else if (!isEnabled && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    private val appIconCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    fun getAppIcon(packageName: String): Bitmap? {
        if (packageName.isBlank()) return null
        appIconCache[packageName]?.let { return it }
        val ctx = appContext ?: return null
        return try {
            val pm = ctx.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(appInfo)
            val bmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(b)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                b
            }
            appIconCache[packageName] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun updateMedia(
        title: String,
        artist: String,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        lastUpdateTimeMs: Long,
        playbackSpeed: Float = 1.0f,
        packageName: String
    ) {
        val resolvedAppIcon = getAppIcon(packageName) ?: mediaState.value.appIcon
        if (isPlaying) {

            inactivityJob?.cancel()
            inactivityJob = null
            mediaState.value = DynamicIslandMediaState(
                hasActiveMedia = true,
                title = title,
                artist = artist,
                albumArt = albumArt,
                isPlaying = true,
                positionMs = positionMs,
                durationMs = durationMs,
                lastUpdateTimeMs = lastUpdateTimeMs,
                playbackSpeed = playbackSpeed,
                packageName = packageName,
                appIcon = resolvedAppIcon
            )
        } else {

            val wasActive = mediaState.value.hasActiveMedia
            mediaState.value = mediaState.value.copy(
                hasActiveMedia = wasActive,
                title = title.ifBlank { mediaState.value.title },
                artist = artist.ifBlank { mediaState.value.artist },
                albumArt = albumArt ?: mediaState.value.albumArt,
                isPlaying = false,
                positionMs = positionMs,
                durationMs = durationMs,
                lastUpdateTimeMs = lastUpdateTimeMs,
                playbackSpeed = playbackSpeed,
                packageName = packageName.ifBlank { mediaState.value.packageName },
                appIcon = resolvedAppIcon ?: mediaState.value.appIcon
            )

            if (wasActive && inactivityJob == null) {
                inactivityJob = managerScope.launch {
                    delay(30_000L)
                    isExpanded.value = false
                    mediaState.value = mediaState.value.copy(hasActiveMedia = false)
                    inactivityJob = null
                }
            }
        }
    }

    fun showTransientBanner(
        type: TransientBannerType = TransientBannerType.NONE,
        title: String,
        subtitle: String = "",
        titleResId: Int = 0,
        subtitleResId: Int = 0,
        iconName: String = "",
        tintColor: Long = 0xFF38BDF8,
        targetWidthDp: Float = 220f,
        durationMs: Long = 3500L
    ) {
        bannerJob?.cancel()
        mainHandler.removeCallbacks(autoDismissBannerRunnable)
        transientBanner.value = TransientBannerState(
            type = type,
            title = title,
            subtitle = subtitle,
            titleResId = titleResId,
            subtitleResId = subtitleResId,
            iconName = iconName,
            tintColor = tintColor,
            targetWidthDp = targetWidthDp
        )
        mainHandler.postDelayed(autoDismissBannerRunnable, durationMs)
    }

    fun dismissTransientBanner() {
        mainHandler.removeCallbacks(autoDismissBannerRunnable)
        bannerJob?.cancel()
        clipboardJob?.cancel()
        isClipboardCardActive.value = false
        transientBanner.value = TransientBannerState(type = TransientBannerType.NONE)
        batteryState.value = batteryState.value.copy(showBanner = false)
        if (isExpanded.value && manualSelectedActivity.value == null) {
            isExpanded.value = false
        }
    }

    fun onRingerModeChanged(ringerMode: Int) {
        when (ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT -> {
                showTransientBanner(
                    type = TransientBannerType.SILENT_MODE,
                    title = "Silencio",
                    titleResId = R.string.silent,
                    subtitle = "",
                    iconName = "silent",
                    tintColor = 0xFFF28B82,
                    targetWidthDp = 180f,
                    durationMs = 3200L
                )
            }
            android.media.AudioManager.RINGER_MODE_VIBRATE -> {
                showTransientBanner(
                    type = TransientBannerType.SILENT_MODE,
                    title = "Vibración",
                    titleResId = R.string.vibrate,
                    subtitle = "",
                    iconName = "vibrate",
                    tintColor = 0xFFFCAD70,
                    targetWidthDp = 180f,
                    durationMs = 3200L
                )
            }
            android.media.AudioManager.RINGER_MODE_NORMAL -> {
                showTransientBanner(
                    type = TransientBannerType.SILENT_MODE,
                    title = "Sonido",
                    titleResId = R.string.sound,
                    subtitle = "",
                    iconName = "sound",
                    tintColor = 0xFF81C995,
                    targetWidthDp = 180f,
                    durationMs = 3200L
                )
            }
        }
    }

    fun onDndChanged(isDndEnabled: Boolean) {
        if (isDndEnabled) {
            showTransientBanner(
                type = TransientBannerType.DND,
                title = "No molestar",
                titleResId = R.string.mode_dnd,
                subtitle = "",
                iconName = "dnd_on",
                tintColor = 0xFFC58AF9,
                targetWidthDp = 310f,
                durationMs = 3200L
            )
        } else {
            showTransientBanner(
                type = TransientBannerType.DND,
                title = "No molestar desactivado",
                titleResId = R.string.dnd_off,
                subtitle = "",
                iconName = "dnd_off",
                tintColor = 0xFFA1A1A6,
                targetWidthDp = 310f,
                durationMs = 3200L
            )
        }
    }

    private var lastCopiedText = ""

    fun onClipboardCopied(text: String, forceShow: Boolean = false) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (!forceShow && clean == lastCopiedText) return
        lastCopiedText = clean

        val now = System.currentTimeMillis()
        val recentCutoff = now - (24 * 60 * 60 * 1000L)
        val newItem = CopiedItem(clean, now)
        val filtered = clipboardHistory.value.filter { it.timestamp >= recentCutoff && it.text != clean }
        clipboardHistory.value = (listOf(newItem) + filtered).take(30)

        val preview = if (clean.length > 28) clean.take(26) + "…" else clean

        if (isClipboardCardActive.value && isExpanded.value) {
            return
        }

        showTransientBanner(
            type = TransientBannerType.CLIPBOARD,
            title = "Copiado",
            titleResId = R.string.copied,
            subtitle = preview,
            iconName = "clipboard",
            tintColor = 0xFF8AB4F8,
            targetWidthDp = 240f,
            durationMs = 3000L
        )
    }

    fun copyToClipboard(context: Context, text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Copiado", text)
            cm?.setPrimaryClip(clip)
        } catch (_: Exception) {}
    }

    fun updateBattery(isCharging: Boolean, level: Int, watts: Double, isNewConnection: Boolean) {
        val previousState = batteryState.value
        val shouldTriggerBanner = isNewConnection || (!previousState.isCharging && isCharging)

        if (shouldTriggerBanner && isCharging) {
            batteryState.value = DynamicIslandBatteryState(
                isCharging = true,
                level = level,
                watts = watts,
                showBanner = true
            )

            val chargingClassRes = when {
                watts >= 67.0 -> R.string.charging_hypercharge
                watts >= 30.0 -> R.string.charging_turbo
                watts >= 15.0 -> R.string.charging_fast
                else -> R.string.charging_battery
            }
            val chargingClass = when {
                watts >= 67.0 -> "HyperCharge 90W Ultra"
                watts >= 33.0 -> "Carga ultra rápida"
                watts >= 15.0 -> "Carga rápida"
                else -> "Cargando"
            }
            val wattLabel = if (watts > 0) {
                if (watts >= 10.0) "${level}% • ${watts.toInt()}W"
                else String.format(java.util.Locale.US, "%d%% • %.1fW", level, watts)
            } else "$level%"

            showTransientBanner(
                type = TransientBannerType.CHARGING,
                title = chargingClass,
                titleResId = chargingClassRes,
                subtitle = wattLabel,
                iconName = "bolt",
                tintColor = 0xFF81C995,
                targetWidthDp = 270f,
                durationMs = 3800L
            )
        } else if (!isCharging) {
            val wasCharging = batteryState.value.isCharging
            batteryState.value = DynamicIslandBatteryState(
                isCharging = false,
                level = level,
                watts = 0.0,
                showBanner = false
            )
            if (wasCharging) {
                lastLowBatteryAlertLevel = -1
                if (isExpanded.value) {
                    collapseIsland()
                }
                showTransientBanner(
                    type = TransientBannerType.CHARGING,
                    title = "Cargador desconectado",
                    titleResId = R.string.island_charger_disconnected,
                    subtitle = "$level%",
                    iconName = "battery_unplugged",
                    tintColor = 0xFFA1A1A6,
                    targetWidthDp = 245f,
                    durationMs = 3500L
                )
            } else {
                checkLowBattery(level)
            }
        } else {
            lastLowBatteryAlertLevel = -1
            batteryState.value = batteryState.value.copy(
                level = level,
                watts = if (watts > 0) watts else batteryState.value.watts
            )
        }
    }

    private var lastLowBatteryAlertLevel = -1

    fun checkLowBattery(level: Int) {
        if (batteryState.value.isCharging) {
            lastLowBatteryAlertLevel = -1
            return
        }
        if (level > 25) {
            lastLowBatteryAlertLevel = -1
        } else if (level <= 20 && lastLowBatteryAlertLevel != 20 && (lastLowBatteryAlertLevel == -1 || lastLowBatteryAlertLevel > 20)) {
            lastLowBatteryAlertLevel = 20
            showTransientBanner(
                type = TransientBannerType.BATTERY_LOW,
                title = "Batería baja",
                titleResId = R.string.island_battery_low,
                subtitle = "$level%",
                iconName = "battery_alert",
                tintColor = 0xFFFF3B30,
                targetWidthDp = 240f,
                durationMs = 4500L
            )
        }
    }

    fun enableBatterySaver() {
        try {
            val pm = appContext?.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.setPowerSaveModeEnabled(true)
        } catch (_: Exception) {}
        dismissTransientBanner()
    }

    var activeMediaController: MediaController? = null

    fun play() {
        inactivityJob?.cancel()
        inactivityJob = null
        mediaState.value = mediaState.value.copy(isPlaying = true, hasActiveMedia = true)
        activeMediaController?.transportControls?.play()
    }

    fun pause() {
        mediaState.value = mediaState.value.copy(isPlaying = false)
        if (inactivityJob == null && mediaState.value.hasActiveMedia) {
            inactivityJob = managerScope.launch {
                delay(30_000L)
                isExpanded.value = false
                mediaState.value = mediaState.value.copy(hasActiveMedia = false)
                inactivityJob = null
            }
        }
        activeMediaController?.transportControls?.pause()
    }

    fun togglePlayPause() {
        if (mediaState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun skipToNext() {
        activeMediaController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeMediaController?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        activeMediaController?.transportControls?.seekTo(positionMs)
    }

    fun openMusicApp(context: android.content.Context) {

        isExpanded.value = false

        if (activeMediaController == null) {
            try {
                val msm = context.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
                val sessions = msm?.getActiveSessions(null)
                activeMediaController = sessions?.firstOrNull()
            } catch (e: Exception) {
                android.util.Log.e("DynamicIsland", "Error obteniendo sesiones activas en openMusicApp", e)
            }
        }

        val pkg = mediaState.value.packageName.ifBlank { activeMediaController?.packageName ?: "" }

        try {
            val sessionIntent = activeMediaController?.sessionActivity
            if (sessionIntent != null) {
                val options = android.app.ActivityOptions.makeBasic()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    options.setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                sessionIntent.send(context, 0, null, null, null, null, options.toBundle())
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("DynamicIsland", "Error abriendo app via sessionActivity", e)
        }

        if (pkg.isNotBlank()) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                if (launchIntent != null) {
                    val options = android.app.ActivityOptions.makeBasic()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        options.setPendingIntentBackgroundActivityStartMode(
                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        )
                    }
                    context.startActivity(launchIntent, options.toBundle())
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("DynamicIsland", "Error abriendo app via launchIntent", e)
            }
        }
    }

    fun testToggleScreenRecording() {
        val currentlyRecording = screenRecordState.value.isRecording
        updateScreenRecording(!currentlyRecording, startTimeMs = System.currentTimeMillis())
    }

    fun testToggleTorch() {
        val currentlyOn = torchState.value.isOn
        torchState.value = TorchState(isOn = !currentlyOn, cameraId = "0")
        if (currentlyOn && !hasAnyActiveActivity()) {
            isExpanded.value = false
        }
    }

    fun testToggleCall() {
        val currentlyActive = callState.value.isActive
        updateCall(
            isActive = !currentlyActive,
            contactName = if (!currentlyActive) "Mamá ❤️" else "",
            startTimeMs = System.currentTimeMillis()
        )
    }

    private var testDownloadJob: Job? = null
    fun testStartDownload() {
        testDownloadJob?.cancel()
        testDownloadJob = managerScope.launch {

            val sampleIcon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
                val canvas = android.graphics.Canvas(this)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#FF5722")
                }
                canvas.drawRoundRect(0f, 0f, 48f, 48f, 12f, 12f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 22f
                paint.textAlign = android.graphics.Paint.Align.CENTER
                canvas.drawText("🏎️", 24f, 32f, paint)
            }

            val steps = listOf(
                Pair(18, "4.2 MB/s"),
                Pair(35, "8.9 MB/s"),
                Pair(52, "12.4 MB/s"),
                Pair(76, "16.1 MB/s"),
                Pair(94, "14.8 MB/s")
            )
            for ((p, speed) in steps) {
                if (!downloadState.value.isDownloading && downloadState.value.isFailed) {
                    return@launch
                }
                updateDownload(
                    isDownloading = true,
                    fileName = "Hill Climb Racing 2",
                    progress = p,
                    appIcon = sampleIcon,
                    speedText = speed,
                    isCompleted = false,
                    isFailed = false
                )
                delay(900)
            }

            onDownloadCompleted(
                fileName = "Hill Climb Racing 2",
                appIcon = sampleIcon
            )
        }
    }

    private var testHeadphonesIndex = 0
    fun testHeadphonesSimulation() {
        val sampleHeadphones = listOf(
            Pair("realme Buds T310", "80%"),
            Pair("AirPods Pro", "95%"),
            Pair("Sony WH-1000XM5", "70%"),
            Pair("Audífonos Bluetooth", "Conectado")
        )
        val pair = sampleHeadphones[testHeadphonesIndex % sampleHeadphones.size]
        testHeadphonesIndex++
        onHeadphonesConnected(name = pair.first, batteryLevel = pair.second)
    }

    private var testClipCounter = 0
    fun testClipboardSimulation(context: Context? = null) {
        val sampleTexts = listOf(
            "POCO X7 Pro rodin HyperOS 2.0",
            "https://github.com/a17/rodin-kernel",
            "Código de autenticación: 849-204",
            "MediaTek Dimensity 8400 Ultra 1.5K 120Hz",
            "adb shell monkey -p com.rodin.dynamicisland 1"
        )
        val testText = sampleTexts[testClipCounter % sampleTexts.size]
        testClipCounter++
        if (context != null) {
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("test", testText)
                cm?.setPrimaryClip(clip)
            } catch (_: Exception) {}
        }
        onClipboardCopied(testText, forceShow = true)
    }

    private var testDndState = false
    fun testToggleDnd() {
        testDndState = !testDndState
        onDndChanged(testDndState)
    }

    fun setRingerMode(mode: Int) {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            am?.ringerMode = mode
        } catch (_: Exception) {}
        onRingerModeChanged(mode)
    }

    fun toggleSilentMode() {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val current = am?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        val nextMode = if (current == AudioManager.RINGER_MODE_SILENT) {
            AudioManager.RINGER_MODE_NORMAL
        } else {
            AudioManager.RINGER_MODE_SILENT
        }
        setRingerMode(nextMode)
    }

    fun toggleDndMode() {
        val nm = appContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        try {
            if (nm?.isNotificationPolicyAccessGranted == true) {
                val current = nm.currentInterruptionFilter
                val newFilter = if (current == NotificationManager.INTERRUPTION_FILTER_NONE ||
                    current == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                    current == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                } else {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                }
                nm.setInterruptionFilter(newFilter)
                onDndChanged(newFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
                return
            }
        } catch (_: Exception) {}
        testToggleDnd()
    }

    private var testRingerStep = 0
    fun testToggleSilent() {
        testRingerStep = (testRingerStep + 1) % 3
        when (testRingerStep) {
            0 -> onRingerModeChanged(android.media.AudioManager.RINGER_MODE_NORMAL)
            1 -> onRingerModeChanged(android.media.AudioManager.RINGER_MODE_SILENT)
            else -> onRingerModeChanged(android.media.AudioManager.RINGER_MODE_VIBRATE)
        }
    }

    fun testChargingSimulation(watts: Double = 90.0) {
        updateBattery(isCharging = true, level = 88, watts = watts, isNewConnection = true)
    }

    private var testHotspotDevices = 0
    fun testToggleHotspot() {
        if (!hotspotState.value.isEnabled) {
            testHotspotDevices = 0
            updateHotspot(isEnabled = true, connectedDevices = 0)
        } else if (testHotspotDevices == 0) {
            testHotspotDevices = 1
            updateHotspot(isEnabled = true, connectedDevices = 1)
        } else if (testHotspotDevices == 1) {
            testHotspotDevices = 3
            updateHotspot(isEnabled = true, connectedDevices = 3)
        } else {
            testHotspotDevices = 0
            updateHotspot(isEnabled = false, connectedDevices = 0)
        }
    }

    private var testNavJob: Job? = null
    fun testToggleNavigation() {
        if (!navigationState.value.isActive) {
            startLiveNavigationSimulation()
        } else {
            stopLiveNavigationSimulation()
        }
    }

    fun startLiveNavigationSimulation() {
        testNavJob?.cancel()
        testNavJob = managerScope.launch {
            val steps = listOf(
                Triple("250 m", "14 min", "Gira a la derecha en Av. Insurgentes Sur"),
                Triple("190 m", "14 min", "Gira a la derecha en Av. Insurgentes Sur"),
                Triple("110 m", "13 min", "Gira a la derecha en Av. Insurgentes Sur"),
                Triple("35 m", "13 min", "Gira a la derecha ahora"),
                Triple("1.8 km", "12 min", "Continúa recto por Paseo de la Reforma"),
                Triple("1.2 km", "10 min", "Continúa recto por Paseo de la Reforma"),
                Triple("450 m", "8 min", "Gira a la izquierda en Florencia"),
                Triple("50 m", "6 min", "Llegando a tu destino")
            )
            for (step in steps) {
                if (!isActive) break
                val maneuver = when {
                    step.third.contains("destino") -> "ARRIVAL"
                    step.third.contains("derecha") -> "TURN_RIGHT"
                    step.third.contains("izquierda") -> "TURN_LEFT"
                    else -> "STRAIGHT"
                }
                updateNavigation(
                    isActive = true,
                    maneuver = maneuver,
                    instruction = step.third,
                    distanceText = step.first,
                    etaText = step.second
                )
                delay(3000L)
            }
        }
    }

    fun stopLiveNavigationSimulation() {
        testNavJob?.cancel()
        testNavJob = null
        updateNavigation(isActive = false)
    }

    private var activeTimerJob: Job? = null
    private var testTimerMode = 0

    fun testToggleTimer() {
        if (!timerState.value.isActive) {
            testTimerMode = 0
            startLiveTimer(totalSeconds = 300, label = "Temporizador")
        } else if (testTimerMode == 0) {
            testTimerMode = 1
            startLiveStopwatch()
        } else if (testTimerMode == 1) {
            testTimerMode = 2
            activeTimerJob?.cancel()
            updateTimer(
                isActive = true,
                type = TimerType.ALARM,
                label = "Alarma matutina",
                remainingTimeSeconds = 0
            )
        } else {
            testTimerMode = 0
            cancelTimer()
        }
    }

    fun startLiveTimer(totalSeconds: Int = 300, label: String = "Temporizador") {
        activeTimerJob?.cancel()
        var remaining = totalSeconds
        updateTimer(
            isActive = true,
            type = TimerType.TIMER,
            label = label,
            remainingTimeSeconds = remaining,
            totalTimeSeconds = totalSeconds,
            isPaused = false
        )
        activeTimerJob = managerScope.launch {
            while (isActive && remaining > 0) {
                delay(1000L)
                if (!timerState.value.isPaused) {
                    remaining--
                    updateTimer(
                        isActive = true,
                        type = TimerType.TIMER,
                        label = label,
                        remainingTimeSeconds = remaining,
                        totalTimeSeconds = totalSeconds,
                        isPaused = false
                    )
                }
            }
            if (isActive && remaining <= 0) {
                updateTimer(
                    isActive = true,
                    type = TimerType.ALARM,
                    label = "¡Alarma!",
                    remainingTimeSeconds = 0,
                    totalTimeSeconds = totalSeconds,
                    isPaused = false
                )
            }
        }
    }

    fun startLiveStopwatch() {
        activeTimerJob?.cancel()
        var elapsed = 0L
        updateTimer(
            isActive = true,
            type = TimerType.STOPWATCH,
            label = "Cronómetro",
            elapsedTimeMs = 0L,
            isPaused = false
        )
        activeTimerJob = managerScope.launch {
            while (isActive) {
                delay(100L)
                if (!timerState.value.isPaused) {
                    elapsed += 100L
                    updateTimer(
                        isActive = true,
                        type = TimerType.STOPWATCH,
                        label = "Cronómetro",
                        elapsedTimeMs = elapsed,
                        isPaused = false
                    )
                }
            }
        }
    }

    fun toggleTimerPauseResume() {
        val current = timerState.value
        if (!current.isActive) return
        val newPaused = !current.isPaused
        updateTimer(
            isActive = true,
            type = current.type,
            label = current.label,
            remainingTimeSeconds = current.remainingTimeSeconds,
            totalTimeSeconds = current.totalTimeSeconds,
            elapsedTimeMs = current.elapsedTimeMs,
            isPaused = newPaused
        )
    }

    fun cancelTimer() {
        activeTimerJob?.cancel()
        activeTimerJob = null
        testTimerMode = 0
        updateTimer(isActive = false)
    }

    private var testCastState = false
    fun testToggleCast() {
        testCastState = !testCastState
        if (testCastState) {
            updateCast(
                isCasting = true,
                deviceName = "Samsung Smart TV 55\"",
                castType = "Pantalla"
            )
        } else {
            updateCast(isCasting = false)
        }
    }

    private var testPrivacyIndex = 0
    fun testToggleMediaPlayback(withAlbumArt: Boolean = true) {
        val current = mediaState.value
        if (current.hasActiveMedia && current.isPlaying && ((withAlbumArt && current.albumArt != null) || (!withAlbumArt && current.albumArt == null))) {
            updateMedia(
                title = current.title,
                artist = current.artist,
                albumArt = current.albumArt,
                isPlaying = false,
                positionMs = current.positionMs,
                durationMs = current.durationMs,
                lastUpdateTimeMs = android.os.SystemClock.elapsedRealtime(),
                playbackSpeed = 1f,
                packageName = current.packageName
            )
        } else {
            val art = if (withAlbumArt) {
                val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                val paint = android.graphics.Paint()
                paint.shader = android.graphics.LinearGradient(
                    0f, 0f, 128f, 128f,
                    android.graphics.Color.parseColor("#8A2387"),
                    android.graphics.Color.parseColor("#F27121"),
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, 128f, 128f, paint)
                bmp
            } else {
                null
            }
            val pkg = appContext?.packageName ?: "com.android.systemui"
            updateMedia(
                title = if (withAlbumArt) "Starboy" else "Pista de Audio",
                artist = if (withAlbumArt) "The Weeknd ft. Daft Punk" else "Reproduciendo en segundo plano",
                albumArt = art,
                isPlaying = true,
                positionMs = 64000L,
                durationMs = 230000L,
                lastUpdateTimeMs = android.os.SystemClock.elapsedRealtime(),
                playbackSpeed = 1f,
                packageName = pkg
            )
        }
    }

    fun testTogglePrivacy() {
        testPrivacyIndex = (testPrivacyIndex + 1) % 4
        when (testPrivacyIndex) {
            1 -> updatePrivacy(isCameraInUse = true, isMicInUse = false, isLocationInUse = false, appName = "")
            2 -> updatePrivacy(isCameraInUse = true, isMicInUse = true, isLocationInUse = false, appName = "WhatsApp")
            3 -> updatePrivacy(isCameraInUse = false, isMicInUse = false, isLocationInUse = true, appName = "Google Maps")
            0 -> updatePrivacy(isCameraInUse = false, isMicInUse = false, isLocationInUse = false, appName = "")
        }
    }
}


// Backward compatibility aliases
typealias RealIslandManager = DynamicIslandManager
typealias RealMediaState = DynamicIslandMediaState
typealias RealBatteryState = DynamicIslandBatteryState
