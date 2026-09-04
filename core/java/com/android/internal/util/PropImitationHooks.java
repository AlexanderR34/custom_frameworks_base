/*
 * Copyright (C) 2022-2024 Paranoid Android
 *           (C) 2023 ArrowOS
 *           (C) 2023 The LibreMobileOS Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util;

import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Binder;
import android.os.Environment;
import android.os.Process;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.util.custom.KeyProviderManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @hide
 */
public class PropImitationHooks {

    private static final String TAG = "PropImitationHooks";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final Boolean sDisableGmsProps = SystemProperties.getBoolean(
            "persist.sys.pihooks.disable.gms_props", false);

    private static final Boolean sDisableKeyAttestationBlock = SystemProperties.getBoolean(
            "persist.sys.pihooks.disable.gms_key_attestation_block", false);
    private static final String DATA_FILE = "gms_certified_props.json";

    private static final String PACKAGE_ARCORE = "com.google.ar.core";
    private static final String PACKAGE_FINSKY = "com.android.vending";
    private static final String PACKAGE_GMS = "com.google.android.gms";
    private static final String PROCESS_GMS_UNSTABLE = PACKAGE_GMS + ".unstable";
    private static final String PACKAGE_NETFLIX = "com.netflix.mediaclient";
    private static final String PACKAGE_GPHOTOS = "com.google.android.apps.photos";

    private static final ComponentName GMS_ADD_ACCOUNT_ACTIVITY = ComponentName.unflattenFromString(
            "com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity");

    private static final String FEATURE_NEXUS_PRELOAD =
            "com.google.android.apps.photos.NEXUS_PRELOAD";

    private static final Map<String, String> sPixelOneProps = Map.of(
        "PRODUCT", "sailfish",
        "DEVICE", "sailfish",
        "MANUFACTURER", "Google",
        "BRAND", "google",
        "MODEL", "Pixel",
        "FINGERPRINT", "google/sailfish/sailfish:10/QP1A.191005.007.A3/5972272:user/release-keys"
    );

    private static final Set<String> sPixelFeatures = Set.of(
        "PIXEL_2017_EXPERIENCE",
        "PIXEL_2017_PRELOAD",
        "PIXEL_2018_EXPERIENCE",
        "PIXEL_2018_PRELOAD",
        "PIXEL_2019_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_EXPERIENCE",
        "PIXEL_2019_MIDYEAR_PRELOAD",
        "PIXEL_2019_PRELOAD",
        "PIXEL_2020_EXPERIENCE",
        "PIXEL_2020_MIDYEAR_EXPERIENCE",
        "PIXEL_2021_MIDYEAR_EXPERIENCE"
    );

    private static final Set<String> sTensorFeatures = Set.of(
        "PIXEL_2021_EXPERIENCE",
        "PIXEL_2022_EXPERIENCE",
        "PIXEL_2022_MIDYEAR_EXPERIENCE",
        "PIXEL_2023_EXPERIENCE",
        "PIXEL_2023_MIDYEAR_EXPERIENCE",
        "PIXEL_2024_EXPERIENCE",
        "PIXEL_2024_MIDYEAR_EXPERIENCE",
        "PIXEL_2025_EXPERIENCE",
        "PIXEL_2025_MIDYEAR_EXPERIENCE"
    );

    private static volatile List<String> sCertifiedProps = new ArrayList<>();
    private static volatile String sStockFp, sNetflixModel;

    private static volatile String sProcessName;
    private static volatile boolean sIsGms, sIsFinsky, sIsPhotos;

    public static void setProps(Context context) {
        final String packageName = context.getPackageName();
        final String processName = Application.getProcessName();

        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(processName)) {
            Log.e(TAG, "Null package or process name");
            return;
        }

        final Resources res = context.getResources();
        if (res == null) {
            Log.e(TAG, "Null resources");
            return;
        }

        sStockFp = res.getString(R.string.config_stockFingerprint);
        sNetflixModel = res.getString(R.string.config_netflixSpoofModel);

        sProcessName = processName;
        sIsGms = packageName.equals(PACKAGE_GMS) && processName.equals(PROCESS_GMS_UNSTABLE);
        sIsFinsky = packageName.equals(PACKAGE_FINSKY);
        sIsPhotos = packageName.equals(PACKAGE_GPHOTOS);

        /* Set Certified Properties for GMSCore
         * Set Stock Fingerprint for ARCore
         * Set custom model for Netflix
         * Set Pixel XL for Google Photos
         * Set Game Spoofing props for configured apps
         */
        if (sIsGms || sIsFinsky) {
            if (!android.os.Process.isIsolated()) {
                setPlayIntegrityProps(context);
            } else {
                dlog("Not setting Play Integrity props in isolated process");
            }
        } else if (!sStockFp.isEmpty() && packageName.equals(PACKAGE_ARCORE)) {
            dlog("Setting stock fingerprint for: " + packageName);
            setPropValue("FINGERPRINT", sStockFp);
        } else if (sIsPhotos) {
            boolean spoofPhotos = true;
            if (!android.os.Process.isIsolated()) {
                try {
                    spoofPhotos = Settings.Secure.getInt(
                            context.getContentResolver(), Settings.Secure.SPOOF_PIF_PHOTOS, 1) == 1;
                } catch (Exception ignored) {}
            }
            if (spoofPhotos) {
                dlog("Spoofing Pixel 1 for Google Photos");
                sPixelOneProps.forEach((PropImitationHooks::setPropValue));
            }
        } else if (!sNetflixModel.isEmpty() && packageName.equals(PACKAGE_NETFLIX)) {
            dlog("Setting model to " + sNetflixModel + " for Netflix");
            setPropValue("MODEL", sNetflixModel);
        } else {
            setGameProps(context, packageName);
        }
    }

    private static void setGameProps(Context context, String packageName) {
        if (android.os.Process.isIsolated()) {
            dlog("Skipping setGameProps in isolated process");
            return;
        }
        try {
            String config = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.SPOOF_GAMEPROPS_CONFIG);
            if (TextUtils.isEmpty(config)) {
                return;
            }
            JSONObject json = new JSONObject(config);
            if (!json.optBoolean("enabled", false)) {
                return;
            }
            JSONObject games = json.optJSONObject("games");
            if (games == null || !games.has(packageName)) {
                return;
            }
            JSONObject props = games.getJSONObject(packageName);
            Log.i(TAG, "Spoofing game props for package: " + packageName);
            Iterator<String> keys = props.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = props.getString(key);
                setPropValue(key, value);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to spoof game props for " + packageName, e);
        }
    }

    private static void setPropValue(String key, String value) {
        try {
            dlog("Setting prop " + key + " to " + value.toString());
            Class clazz = Build.class;
            String fieldName = key;
            if (key.startsWith("VERSION.")) {
                clazz = Build.VERSION.class;
                fieldName = key.substring(8);
            } else {
                try {
                    Build.class.getDeclaredField(key);
                } catch (NoSuchFieldException e) {
                    try {
                        Build.VERSION.class.getDeclaredField(key);
                        clazz = Build.VERSION.class;
                        fieldName = key;
                    } catch (NoSuchFieldException ignored) {
                        // Custom or system property key
                    }
                }
            }
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                // Cast the value to int if it's an integer field, otherwise string.
                field.set(null, field.getType().equals(Integer.TYPE) ? Integer.parseInt(value) : value);
                field.setAccessible(false);
            } catch (NoSuchFieldException ignored) {
            }

            setSystemPropertyOverride(key, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static void setSystemPropertyOverride(String key, String value) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
            return;
        }
        if (key.startsWith("ro.")) {
            SystemProperties.setOverrideProperty(key, value);
            return;
        }
        switch (key) {
            case "MODEL":
                SystemProperties.setOverrideProperty("ro.product.model", value);
                SystemProperties.setOverrideProperty("ro.product.system.model", value);
                SystemProperties.setOverrideProperty("ro.product.vendor.model", value);
                SystemProperties.setOverrideProperty("ro.product.odm.model", value);
                SystemProperties.setOverrideProperty("ro.product.product.model", value);
                SystemProperties.setOverrideProperty("ro.product.system_ext.model", value);
                break;
            case "MANUFACTURER":
                SystemProperties.setOverrideProperty("ro.product.manufacturer", value);
                SystemProperties.setOverrideProperty("ro.product.system.manufacturer", value);
                SystemProperties.setOverrideProperty("ro.product.vendor.manufacturer", value);
                SystemProperties.setOverrideProperty("ro.product.odm.manufacturer", value);
                SystemProperties.setOverrideProperty("ro.product.product.manufacturer", value);
                SystemProperties.setOverrideProperty("ro.product.system_ext.manufacturer", value);
                break;
            case "BRAND":
                SystemProperties.setOverrideProperty("ro.product.brand", value);
                SystemProperties.setOverrideProperty("ro.product.system.brand", value);
                SystemProperties.setOverrideProperty("ro.product.vendor.brand", value);
                SystemProperties.setOverrideProperty("ro.product.odm.brand", value);
                SystemProperties.setOverrideProperty("ro.product.product.brand", value);
                SystemProperties.setOverrideProperty("ro.product.system_ext.brand", value);
                break;
            case "DEVICE":
                SystemProperties.setOverrideProperty("ro.product.device", value);
                SystemProperties.setOverrideProperty("ro.product.system.device", value);
                SystemProperties.setOverrideProperty("ro.product.vendor.device", value);
                SystemProperties.setOverrideProperty("ro.product.odm.device", value);
                SystemProperties.setOverrideProperty("ro.product.product.device", value);
                SystemProperties.setOverrideProperty("ro.product.system_ext.device", value);
                break;
            case "PRODUCT":
                SystemProperties.setOverrideProperty("ro.product.name", value);
                SystemProperties.setOverrideProperty("ro.product.system.name", value);
                SystemProperties.setOverrideProperty("ro.product.vendor.name", value);
                SystemProperties.setOverrideProperty("ro.product.odm.name", value);
                SystemProperties.setOverrideProperty("ro.product.product.name", value);
                SystemProperties.setOverrideProperty("ro.product.system_ext.name", value);
                break;
            case "BOARD":
                SystemProperties.setOverrideProperty("ro.product.board", value);
                SystemProperties.setOverrideProperty("ro.board.platform", value);
                break;
            case "HARDWARE":
                SystemProperties.setOverrideProperty("ro.hardware", value);
                break;
            case "FINGERPRINT":
                SystemProperties.setOverrideProperty("ro.build.fingerprint", value);
                SystemProperties.setOverrideProperty("ro.system.build.fingerprint", value);
                SystemProperties.setOverrideProperty("ro.vendor.build.fingerprint", value);
                SystemProperties.setOverrideProperty("ro.odm.build.fingerprint", value);
                SystemProperties.setOverrideProperty("ro.product.build.fingerprint", value);
                SystemProperties.setOverrideProperty("ro.system_ext.build.fingerprint", value);
                break;
            case "ID":
                SystemProperties.setOverrideProperty("ro.build.id", value);
                break;
            case "TAGS":
                SystemProperties.setOverrideProperty("ro.build.tags", value);
                break;
            case "TYPE":
                SystemProperties.setOverrideProperty("ro.build.type", value);
                break;
            case "RELEASE":
            case "VERSION.RELEASE":
                SystemProperties.setOverrideProperty("ro.build.version.release", value);
                break;
            case "SECURITY_PATCH":
            case "VERSION.SECURITY_PATCH":
                SystemProperties.setOverrideProperty("ro.build.version.security_patch", value);
                break;
            case "SDK_INT":
            case "VERSION.SDK_INT":
                SystemProperties.setOverrideProperty("ro.build.version.sdk", value);
                break;
            case "INCREMENTAL":
            case "VERSION.INCREMENTAL":
                SystemProperties.setOverrideProperty("ro.build.version.incremental", value);
                break;
            default:
                break;
        }
    }

    private static void setPlayIntegrityProps(Context context) {
        if (sDisableGmsProps) {
            dlog("GMS prop imitation is disabled by user");
            return;
        }

        // Guard: isolated processes cannot access content providers (Settings.*).
        if (android.os.Process.isIsolated()) {
            dlog("Skipping setPlayIntegrityProps in isolated process");
            return;
        }

        String savedProps = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.SPOOF_PIF_CONFIG);
        if (savedProps == null || TextUtils.isEmpty(savedProps)) {
            savedProps = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.PIF_DATA);
        }
        if (savedProps == null || TextUtils.isEmpty(savedProps)) {
            savedProps = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.FETCHED_PIF);
        }

        if (savedProps == null || TextUtils.isEmpty(savedProps)) {
            dlog("Parsing props locally - fetched pif / user provided pif unavailable");
            sCertifiedProps = Arrays.asList(context.getResources().getStringArray(R.array.config_certifiedBuildProperties));
        } else {
            dlog("Parsing props fetched / provided by user");
            try {
                JSONObject parsedProps = new JSONObject(savedProps);
                Iterator<String> keys = parsedProps.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = parsedProps.getString(key);
                    sCertifiedProps.add(key + ":" + value);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON data", e);
                dlog("Parsing props locally as fallback");
                sCertifiedProps = Arrays.asList(context.getResources().getStringArray(R.array.config_certifiedBuildProperties));
            }
        }

        if (sCertifiedProps.isEmpty()) {
            dlog("Certified props are not set");
            return;
        }

        final boolean was = isGmsAddAccountActivityOnTop();
        final TaskStackListener taskStackListener = new TaskStackListener() {
            @Override
            public void onTaskStackChanged() {
                final boolean is = isGmsAddAccountActivityOnTop();
                if (is ^ was) {
                    dlog("GmsAddAccountActivityOnTop is:" + is + " was:" + was +
                            ", killing myself!"); // process will restart automatically later
                    Process.killProcess(Process.myPid());
                }
            }
        };

        if (!was) {
            dlog("Spoofing build for GMS / Finsky");
            setCertifiedProps();
        } else {
            dlog("Skip spoofing build for GMS / Finsky, because GmsAddAccountActivityOnTop");
        }

        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register task stack listener!", e);
        }
    }

    private static void setCertifiedProps() {
        for (String entry : sCertifiedProps) {
            // Each entry must be of the format FIELD:value
            final String[] fieldAndProp = entry.split(":", 2);
            if (fieldAndProp.length != 2) {
                Log.e(TAG, "Invalid entry in certified props: " + entry);
                continue;
            }
            setPropValue(fieldAndProp[0], fieldAndProp[1]);
        }
    }

    private static String readFromFile(File file) {
        StringBuilder content = new StringBuilder();

        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from file", e);
            }
        }
        return content.toString();
    }

    private static boolean isGmsAddAccountActivityOnTop() {
        try {
            final ActivityTaskManager.RootTaskInfo focusedTask =
                    ActivityTaskManager.getService().getFocusedRootTaskInfo();
            return focusedTask != null && focusedTask.topActivity != null
                    && focusedTask.topActivity.equals(GMS_ADD_ACCOUNT_ACTIVITY);
        } catch (Exception e) {
            Log.e(TAG, "Unable to get top activity!", e);
        }
        return false;
    }

    public static boolean shouldBypassTaskPermission(Context context) {
        if (sDisableGmsProps) {
            return false;
        }

        // GMS doesn't have MANAGE_ACTIVITY_TASKS permission
        final int callingUid = Binder.getCallingUid();
        final int gmsUid;
        try {
            gmsUid = context.getPackageManager().getApplicationInfo(PACKAGE_GMS, 0).uid;
            dlog("shouldBypassTaskPermission: gmsUid:" + gmsUid + " callingUid:" + callingUid);
        } catch (Exception e) {
            Log.e(TAG, "shouldBypassTaskPermission: unable to get gms uid", e);
            return false;
        }
        return gmsUid == callingUid;
    }

    private static boolean isCallerPlayIntegrity() {
        return Arrays.stream(Thread.currentThread().getStackTrace())
                .map(StackTraceElement::getClassName)
                .anyMatch(name -> name.toLowerCase(Locale.US).contains("droidguard"));
    }

    public static void onEngineGetCertificateChain() {
        if (sDisableKeyAttestationBlock) {
            dlog("Key attestation blocking is disabled by user");
            return;
        }

        // If a keybox is found, don't block key attestation
        if (KeyProviderManager.isKeyboxAvailable()) {
            dlog("Key attestation blocking is disabled because a keybox is defined to spoof");
            return;
        }

        // Check stack for Play Integrity
        if (isCallerPlayIntegrity()) {
            dlog("Blocked key attestation for play integrity");
            throw new UnsupportedOperationException();
        }
    }

    public static boolean hasSystemFeature(String name, boolean has) {
        if (sIsPhotos) {
            if (has && (sPixelFeatures.stream().anyMatch(name::contains)
                    || sTensorFeatures.stream().anyMatch(name::contains))) {
                dlog("Blocked system feature " + name + " for Google Photos");
                has = false;
            } else if (!has && name.equalsIgnoreCase(FEATURE_NEXUS_PRELOAD)) {
                dlog("Enabled system feature " + name + " for Google Photos");
                has = true;
            }
        }
        return has;
    }

    public static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, "[" + sProcessName + "] " + msg);
    }
}
