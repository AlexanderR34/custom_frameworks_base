/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.screenshot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioSystem
import android.media.MediaActionSound
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import com.android.internal.R
import com.android.systemui.dagger.SysUISingleton
import java.io.File
import javax.inject.Inject

/** Provides a [MediaPlayer] that reproduces the screenshot sound. */
interface ScreenshotSoundProvider {

    /**
     * Creates a new [MediaPlayer] that reproduces the screenshot sound. This should be called from
     * a background thread, as it might take time.
     */
    fun getScreenshotSound(): MediaPlayer?

    fun getForcedShutterSound(): MediaActionSound
}

@SysUISingleton
class ScreenshotSoundProviderImpl @Inject constructor(private val context: Context) :
    ScreenshotSoundProvider {

    override fun getScreenshotSound(): MediaPlayer? {
        val soundUri = resolveScreenshotSoundUri() ?: return null
        return try {
            MediaPlayer.create(
                context,
                soundUri,
                /* holder = */ null,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioSystem.newAudioSessionId(),
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveScreenshotSoundUri(): Uri? {
        // Check custom_screenshot_sound in Settings.System
        try {
            val customPath = Settings.System.getString(context.contentResolver, "custom_screenshot_sound")
            if (!customPath.isNullOrEmpty()) {
                val file = File(customPath)
                if (file.exists()) {
                    return Uri.fromFile(file)
                }
            }
        } catch (ignored: Exception) {}

        // Check ui_sounds_theme in Settings.System
        val theme = try {
            Settings.System.getInt(context.contentResolver, "ui_sounds_theme", 0)
        } catch (ignored: Exception) {
            0
        }

        val themeCandidates = when (theme) {
            1 -> listOf(
                "/system/media/audio/ui/poco/screenshot.ogg",
                "/system/media/audio/ui/poco/camera_click.ogg",
                "/product/media/audio/ui/camera_click.ogg"
            )
            2 -> listOf(
                "/system/media/audio/ui/samsung/Screen_Capture.ogg",
                "/system/media/audio/ui/samsung/camera_click.ogg",
                "/product/media/audio/ui/camera_click.ogg"
            )
            3 -> listOf(
                "/system/media/audio/ui/ios/screenshot.ogg",
                "/system/media/audio/ui/ios/camera_click.ogg",
                "/product/media/audio/ui/camera_click.ogg"
            )
            else -> listOf(
                "/product/media/audio/ui/camera_click.ogg",
                "/system/media/audio/ui/camera_click.ogg"
            )
        }

        for (candidate in themeCandidates) {
            val file = File(candidate)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        val configPath = try {
            context.resources.getString(R.string.config_cameraShutterSound)
        } catch (e: Exception) {
            null
        }
        if (!configPath.isNullOrEmpty()) {
            val file = File(configPath)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
        }

        return null
    }

    override fun getForcedShutterSound(): MediaActionSound {
        return MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    }
}
