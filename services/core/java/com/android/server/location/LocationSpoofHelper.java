/*
 * Copyright (C) 2026 Project Diva
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

package com.android.server.location;

import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

/**
 * Helper utility to manage per-app location spoofing and isolation.
 */
public final class LocationSpoofHelper {

    private static final String TAG = "LocationSpoofHelper";
    public static final String SETTING_FAKE_LOC_ENABLED_PREFIX = "fake_loc_enabled_";
    public static final String SETTING_FAKE_LOC_COORDS_PREFIX = "fake_loc_coords_";
    public static final String SETTING_SPOOF_PKG_PREFIX = "location_spoof_pkg_";
    public static final String SETTING_SPOOF_COORDS_PREFIX = "location_spoof_coords_";

    private LocationSpoofHelper() {}

    /**
     * Checks if location spoofing or fake permissions isolation is active for the given package and UID.
     */
    public static boolean isSpoofEnabledForPackage(@Nullable Context context,
            @Nullable String packageName, int uid, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return false;
        }

        // Never spoof or isolate system server itself
        if ("android".equals(packageName) || "com.android.systemui".equals(packageName)) {
            return false;
        }

        try {
            // 1. Check explicit Secure Settings toggle (fake_loc_enabled_ or location_spoof_pkg_)
            int fakeLocEnabled = Settings.Secure.getIntForUser(
                    context.getContentResolver(),
                    SETTING_FAKE_LOC_ENABLED_PREFIX + packageName,
                    -1,
                    userId);
            if (fakeLocEnabled == 1) {
                return true;
            } else if (fakeLocEnabled == 0) {
                return false;
            }

            int spoofPkg = Settings.Secure.getIntForUser(
                    context.getContentResolver(),
                    SETTING_SPOOF_PKG_PREFIX + packageName,
                    0,
                    userId);
            if (spoofPkg == 1) {
                return true;
            }

            // 2. Check AppOpsManager MODE_IGNORED (Fake permissions isolation)
            if (uid > 0) {
                AppOpsManager aom = context.getSystemService(AppOpsManager.class);
                if (aom != null) {
                    int fineMode = aom.checkOpNoThrow(AppOpsManager.OP_FINE_LOCATION, uid, packageName);
                    int coarseMode = aom.checkOpNoThrow(AppOpsManager.OP_COARSE_LOCATION, uid, packageName);
                    if (fineMode == AppOpsManager.MODE_IGNORED || coarseMode == AppOpsManager.MODE_IGNORED) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking spoof/isolation status for " + packageName, e);
        }
        return false;
    }

    public static boolean isSpoofEnabledForPackage(@Nullable Context context,
            @Nullable String packageName, int userId) {
        return isSpoofEnabledForPackage(context, packageName, -1, userId);
    }

    /**
     * Obtains the configured spoofed location for a package, or null if no coords or spoof inactive.
     * When spoof is enabled but no coordinates are set, returns null (silencing GPS updates).
     */
    @Nullable
    public static Location getSpoofLocation(@Nullable Context context,
            @Nullable String packageName, int uid, @Nullable String provider, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }

        if (!isSpoofEnabledForPackage(context, packageName, uid, userId)) {
            return null;
        }

        try {
            // Check both fake_loc_coords_ and location_spoof_coords_
            String coords = Settings.Secure.getStringForUser(
                    context.getContentResolver(),
                    SETTING_FAKE_LOC_COORDS_PREFIX + packageName,
                    userId);
            if (TextUtils.isEmpty(coords)) {
                coords = Settings.Secure.getStringForUser(
                        context.getContentResolver(),
                        SETTING_SPOOF_COORDS_PREFIX + packageName,
                        userId);
            }

            if (TextUtils.isEmpty(coords)) {
                // Spoofing/Isolation active but no coordinates defined: simulate searching/no satellite lock
                return null;
            }

            String[] parts = coords.split(",");
            if (parts.length < 2) {
                return null;
            }

            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            double alt = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 25.0;
            float acc = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 6.5f;

            Location spoof = new Location(
                    !TextUtils.isEmpty(provider) ? provider : LocationManager.GPS_PROVIDER);
            spoof.setLatitude(lat);
            spoof.setLongitude(lng);
            spoof.setAltitude(alt);
            spoof.setSpeed(0.0f);
            spoof.setBearing(0.0f);
            spoof.setAccuracy(acc > 0 ? acc : 6.5f);
            spoof.setTime(System.currentTimeMillis());
            spoof.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            // Ensure isFromMockProvider returns false to pass security/anti-mock checks of 3rd party apps
            spoof.setMock(false);

            return spoof;
        } catch (Exception e) {
            Log.e(TAG, "Error generating spoofed location for " + packageName, e);
            return null;
        }
    }

    @Nullable
    public static Location getSpoofLocation(@Nullable Context context,
            @Nullable String packageName, @Nullable String provider, int userId) {
        return getSpoofLocation(context, packageName, -1, provider, userId);
    }
}
