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
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;

/**
 * Helper utility to manage per-app location spoofing and isolation.
 */
public final class LocationSpoofHelper {

    private static final String TAG = "LocationSpoofHelper";
    public static final String SETTING_SPOOF_PKG_PREFIX = "location_spoof_pkg_";
    public static final String SETTING_SPOOF_COORDS_PREFIX = "location_spoof_coords_";
    public static final String SETTING_SPOOF_GLOBAL_ENABLED = "location_spoof_global_enabled";

    private LocationSpoofHelper() {}

    /**
     * Checks if location spoofing is active for the given package.
     */
    public static boolean isSpoofEnabledForPackage(@Nullable Context context,
            @Nullable String packageName, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return false;
        }

        // Never spoof system server itself
        if ("android".equals(packageName) || "com.android.systemui".equals(packageName)) {
            return false;
        }

        try {
            return Settings.Secure.getIntForUser(
                    context.getContentResolver(),
                    SETTING_SPOOF_PKG_PREFIX + packageName,
                    0,
                    userId) == 1;
        } catch (Exception e) {
            Log.e(TAG, "Error checking spoof status for " + packageName, e);
            return false;
        }
    }

    /**
     * Obtains the configured spoofed location for a package, or null if no coords or spoof inactive.
     * When spoof is enabled but no coordinates are set, returns null (silencing GPS updates).
     */
    @Nullable
    public static Location getSpoofLocation(@Nullable Context context,
            @Nullable String packageName, @Nullable String provider, int userId) {
        if (context == null || TextUtils.isEmpty(packageName)) {
            return null;
        }

        if (!isSpoofEnabledForPackage(context, packageName, userId)) {
            return null;
        }

        try {
            String coords = Settings.Secure.getStringForUser(
                    context.getContentResolver(),
                    SETTING_SPOOF_COORDS_PREFIX + packageName,
                    userId);

            if (TextUtils.isEmpty(coords)) {
                // Spoofing active but no coordinates defined: simulate searching/no satellite lock
                return null;
            }

            String[] parts = coords.split(",");
            if (parts.length < 2) {
                return null;
            }

            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            double alt = parts.length > 2 ? Double.parseDouble(parts[2].trim()) : 15.0;
            float acc = parts.length > 3 ? Float.parseFloat(parts[3].trim()) : 3.5f;

            Location spoof = new Location(
                    !TextUtils.isEmpty(provider) ? provider : LocationManager.GPS_PROVIDER);
            spoof.setLatitude(lat);
            spoof.setLongitude(lng);
            spoof.setAltitude(alt);
            spoof.setSpeed(0.0f);
            spoof.setBearing(0.0f);
            spoof.setAccuracy(acc > 0 ? acc : 3.5f);
            spoof.setTime(System.currentTimeMillis());
            spoof.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

            // Ensure isFromMockProvider returns false to pass security/anti-mock checks
            spoof.setMock(false);

            return spoof;
        } catch (Exception e) {
            Log.e(TAG, "Error generating spoofed location for " + packageName, e);
            return null;
        }
    }
}
