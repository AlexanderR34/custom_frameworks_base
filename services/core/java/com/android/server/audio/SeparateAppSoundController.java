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

package com.android.server.audio;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Controller for Separate App Sound (One UI style per-app independent audio routing).
 * Routes specific applications' audio playback exclusively to a designated physical
 * audio device (e.g., built-in speaker, Bluetooth A2DP/LE Audio) via native UID device affinities
 * and dynamic stream refreshes.
 */
public class SeparateAppSoundController {
    private static final String TAG = "AS.SeparateAppSound";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int MAX_ROUTED_APPS = 5;

    private final Context mContext;
    private final Handler mHandler;
    private final AudioManager mAudioManager;
    private final AudioDeviceBroker mDeviceBroker;
    private final SettingsObserver mSettingsObserver;

    private boolean mEnabled = false;
    private String mTargetPackages = "";
    private int mTargetDeviceType = AudioDeviceInfo.TYPE_UNKNOWN;
    private String mTargetDeviceAddress = "";

    private final List<Integer> mCurrentRoutedUids = new ArrayList<>();
    private int mCurrentInternalDevice = AudioSystem.DEVICE_NONE;
    private String mCurrentRoutedAddress = "";

    public SeparateAppSoundController(@NonNull Context context, @NonNull AudioDeviceBroker broker, @NonNull Looper looper) {
        mContext = Objects.requireNonNull(context);
        mDeviceBroker = Objects.requireNonNull(broker);
        mHandler = new Handler(looper);
        mAudioManager = mContext.getSystemService(AudioManager.class);
        mSettingsObserver = new SettingsObserver(mHandler);
    }

    /**
     * Called when AudioService reaches system ready phase.
     */
    public void onSystemReady() {
        registerSettingsObserver();
        registerReceivers();
        loadSettingsAndApply();
    }

    private void registerSettingsObserver() {
        final Uri enabledUri = Settings.Secure.getUriFor(Settings.Secure.SEPARATE_APP_SOUND_ENABLED);
        final Uri packageUri = Settings.Secure.getUriFor(Settings.Secure.SEPARATE_APP_SOUND_PACKAGE);
        final Uri devTypeUri = Settings.Secure.getUriFor(Settings.Secure.SEPARATE_APP_SOUND_TARGET_DEVICE_TYPE);
        final Uri devAddrUri = Settings.Secure.getUriFor(Settings.Secure.SEPARATE_APP_SOUND_TARGET_DEVICE_ADDRESS);

        mContext.getContentResolver().registerContentObserver(enabledUri, false, mSettingsObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(packageUri, false, mSettingsObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(devTypeUri, false, mSettingsObserver, UserHandle.USER_ALL);
        mContext.getContentResolver().registerContentObserver(devAddrUri, false, mSettingsObserver, UserHandle.USER_ALL);
    }

    private void registerReceivers() {
        IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        mContext.registerReceiverForAllUsers(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                        || BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        handleBluetoothDisconnection(device);
                    } else if (state == BluetoothProfile.STATE_CONNECTED) {
                        mHandler.post(SeparateAppSoundController.this::loadSettingsAndApply);
                    }
                } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                        handleAllBtDisconnected();
                    }
                }
            }
        }, btFilter, null, mHandler);

        IntentFilter pkgFilter = new IntentFilter(Intent.ACTION_PACKAGE_REMOVED);
        pkgFilter.addDataScheme("package");
        mContext.registerReceiverForAllUsers(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getData() != null) {
                    String pkg = intent.getData().getSchemeSpecificPart();
                    if (!TextUtils.isEmpty(pkg) && mTargetPackages != null && mTargetPackages.contains(pkg)) {
                        Log.i(TAG, "Target package uninstalled: " + pkg + ". Re-applying audio routing.");
                        mHandler.post(SeparateAppSoundController.this::loadSettingsAndApply);
                    }
                }
            }
        }, pkgFilter, null, mHandler);

        mAudioManager.registerAudioDeviceCallback(new android.media.AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                mHandler.post(SeparateAppSoundController.this::loadSettingsAndApply);
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                for (AudioDeviceInfo dev : removedDevices) {
                    if (isCurrentDeviceMatched(dev)) {
                        Log.w(TAG, "Active routed device physically removed: " + dev.getProductName());
                        clearCurrentRoutingInternal();
                        break;
                    }
                }
            }
        }, mHandler);
    }

    private synchronized void loadSettingsAndApply() {
        int currentUserId = ActivityManager.getCurrentUser();
        mEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SEPARATE_APP_SOUND_ENABLED, 0, currentUserId) == 1;
        mTargetPackages = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.SEPARATE_APP_SOUND_PACKAGE, currentUserId);
        mTargetDeviceType = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.SEPARATE_APP_SOUND_TARGET_DEVICE_TYPE, AudioDeviceInfo.TYPE_UNKNOWN, currentUserId);
        mTargetDeviceAddress = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                Settings.Secure.SEPARATE_APP_SOUND_TARGET_DEVICE_ADDRESS, currentUserId);

        if (mTargetDeviceAddress == null) {
            mTargetDeviceAddress = "";
        }

        if (!mEnabled || TextUtils.isEmpty(mTargetPackages) || mTargetDeviceType == AudioDeviceInfo.TYPE_UNKNOWN) {
            clearCurrentRoutingInternal();
            return;
        }

        applyRoutingInternal(currentUserId);
    }

    private synchronized void applyRoutingInternal(int userId) {
        String[] pkgList = mTargetPackages.split("[,;]");
        List<Integer> targetUids = new ArrayList<>();
        for (String pkg : pkgList) {
            String trimmed = pkg.trim();
            if (TextUtils.isEmpty(trimmed)) continue;
            int uid = resolveUidForPackage(trimmed, userId);
            if (uid != Process.INVALID_UID && !targetUids.contains(uid)) {
                targetUids.add(uid);
                if (targetUids.size() >= MAX_ROUTED_APPS) break;
            }
        }

        if (targetUids.isEmpty()) {
            Log.e(TAG, "Cannot resolve target UIDs for packages: " + mTargetPackages);
            clearCurrentRoutingInternal();
            return;
        }

        AudioDeviceInfo matchedDevice = findConnectedAudioDevice(mTargetDeviceType, mTargetDeviceAddress);
        if (matchedDevice == null) {
            Log.w(TAG, "Target device not currently attached to audio server. Clearing route.");
            clearCurrentRoutingInternal();
            return;
        }

        int internalDeviceType = AudioDeviceInfo.convertDeviceTypeToInternalDevice(matchedDevice.getType());
        String address = matchedDevice.getAddress() != null ? matchedDevice.getAddress() : "";

        if (mCurrentRoutedUids.equals(targetUids)
                && mCurrentInternalDevice == internalDeviceType
                && TextUtils.equals(mCurrentRoutedAddress, address)) {
            return;
        }

        clearCurrentRoutingInternal();

        try {
            AudioDeviceAttributes attributes = new AudioDeviceAttributes(
                    AudioDeviceAttributes.ROLE_OUTPUT,
                    internalDeviceType,
                    address
            );

            for (int uid : targetUids) {
                int status = AudioSystem.setUidDeviceAffinities(uid, new int[]{internalDeviceType}, new String[]{address});
                Log.i(TAG, "AudioSystem.setUidDeviceAffinities UID=" + uid
                        + " to dev=0x" + Integer.toHexString(internalDeviceType) + " result=" + status);
            }

            // Forzar a AudioFlinger/AudioPolicy a reevaluar y mover las pistas que ya están en reproducción
            mAudioManager.setParameters("restarting=false");

            mCurrentRoutedUids.clear();
            mCurrentRoutedUids.addAll(targetUids);
            mCurrentInternalDevice = internalDeviceType;
            mCurrentRoutedAddress = address;

            Log.i(TAG, "Applied separate app sound for UIDs: " + targetUids + " to device "
                    + matchedDevice.getProductName() + " (type=0x" + Integer.toHexString(internalDeviceType) + ")");
        } catch (Exception e) {
            Log.e(TAG, "Exception applying Separate App Sound routing", e);
        }
    }

    private synchronized void clearCurrentRoutingInternal() {
        if (!mCurrentRoutedUids.isEmpty()) {
            Log.i(TAG, "Clearing preferred routes for UIDs: " + mCurrentRoutedUids);
            for (int uid : mCurrentRoutedUids) {
                try {
                    AudioSystem.removeUidDeviceAffinities(uid);
                } catch (Exception e) {
                    Log.e(TAG, "Error clearing routing for uid " + uid, e);
                }
            }
            mAudioManager.setParameters("restarting=false");
            mCurrentRoutedUids.clear();
            mCurrentInternalDevice = AudioSystem.DEVICE_NONE;
            mCurrentRoutedAddress = "";
        }
    }

    private void handleBluetoothDisconnection(@Nullable BluetoothDevice device) {
        if (device == null) return;
        String disconnectedAddress = device.getAddress();
        if (mCurrentInternalDevice != AudioSystem.DEVICE_NONE && !TextUtils.isEmpty(mCurrentRoutedAddress)) {
            if (TextUtils.equals(mCurrentRoutedAddress, disconnectedAddress)) {
                Log.w(TAG, "Bluetooth device disconnected: " + disconnectedAddress + ". Fallback to default route.");
                clearCurrentRoutingInternal();
            }
        }
    }

    private void handleAllBtDisconnected() {
        if (mCurrentInternalDevice != AudioSystem.DEVICE_NONE
                && (AudioSystem.DEVICE_OUT_ALL_A2DP_SET.contains(mCurrentInternalDevice)
                || AudioSystem.DEVICE_OUT_ALL_BLE_SET.contains(mCurrentInternalDevice))) {
            Log.w(TAG, "All BT down, resetting separate sound route");
            clearCurrentRoutingInternal();
        }
    }

    private boolean isCurrentDeviceMatched(@NonNull AudioDeviceInfo dev) {
        if (mCurrentInternalDevice == AudioSystem.DEVICE_NONE) return false;
        int internalDev = AudioDeviceInfo.convertDeviceTypeToInternalDevice(dev.getType());
        if (internalDev != mCurrentInternalDevice) return false;
        if (!TextUtils.isEmpty(mCurrentRoutedAddress)) {
            return TextUtils.equals(dev.getAddress(), mCurrentRoutedAddress);
        }
        return true;
    }

    private int resolveUidForPackage(@NonNull String packageName, int userId) {
        try {
            return mContext.getPackageManager().getPackageUidAsUser(packageName,
                    PackageManager.PackageInfoFlags.of(0), userId);
        } catch (PackageManager.NameNotFoundException e) {
            return Process.INVALID_UID;
        }
    }

    @Nullable
    private AudioDeviceInfo findConnectedAudioDevice(int type, @NonNull String address) {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        if (devices == null) return null;

        // 1. If non-empty address is specified (e.g. for Bluetooth MAC), match by address first
        if (!TextUtils.isEmpty(address)) {
            for (AudioDeviceInfo device : devices) {
                if (TextUtils.equals(device.getAddress(), address)) {
                    return device;
                }
            }
        }

        // 2. Match exact type
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == type) {
                if (TextUtils.isEmpty(address) || TextUtils.equals(device.getAddress(), address)) {
                    return device;
                }
            }
        }

        // 3. If target type is Bluetooth, fallback to any connected Bluetooth audio output
        if (isBluetoothType(type)) {
            for (AudioDeviceInfo device : devices) {
                if (isBluetoothType(device.getType())) {
                    return device;
                }
            }
        }

        return null;
    }

    private static boolean isBluetoothType(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || type == AudioDeviceInfo.TYPE_BLE_BROADCAST
                || type == AudioDeviceInfo.TYPE_HEARING_AID;
    }

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            loadSettingsAndApply();
        }
    }
}
