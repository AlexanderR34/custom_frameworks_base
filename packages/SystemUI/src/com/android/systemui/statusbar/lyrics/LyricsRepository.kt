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

package com.android.systemui.statusbar.lyrics

import android.util.Log
import android.util.LruCache
import com.android.systemui.dagger.SysUISingleton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "LyricsRepository"
private const val USER_AGENT = "Android-SystemUI-Lyrics/1.0"
private const val BASE_URL = "https://lrclib.net/api"
private const val CONNECT_TIMEOUT_MS = 4000
private const val READ_TIMEOUT_MS = 5000

@SysUISingleton
class LyricsRepository @Inject constructor() {

    private val cache = LruCache<String, LyricsData>(50)

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        durationSeconds: Long = 0L
    ): LyricsData? = withContext(Dispatchers.IO) {
        val cleanTrack = cleanTrackTitle(trackName)
        val cleanArtist = cleanArtistName(artistName)
        val cacheKey = "${cleanTrack.lowercase()}|${cleanArtist.lowercase()}"

        cache.get(cacheKey)?.let {
            return@withContext it
        }

        var lyrics = queryLrclibGet(cleanTrack, cleanArtist, durationSeconds)
        if (lyrics == null) {
            lyrics = queryLrclibSearch("$cleanTrack $cleanArtist")
        }

        if (lyrics != null) {
            cache.put(cacheKey, lyrics)
        }
        return@withContext lyrics
    }

    private fun queryLrclibGet(
        track: String,
        artist: String,
        durationSeconds: Long
    ): LyricsData? {
        try {
            val queryBuilder = StringBuilder("$BASE_URL/get?")
            queryBuilder.append("track_name=").append(URLEncoder.encode(track, "UTF-8"))
            if (artist.isNotBlank()) {
                queryBuilder.append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            }
            if (durationSeconds > 0) {
                queryBuilder.append("&duration=").append(durationSeconds)
            }

            val response = executeHttpRequest(queryBuilder.toString()) ?: return null
            val json = JSONObject(response)
            val syncedLyrics = json.optString("syncedLyrics", "")
            if (syncedLyrics.isNotBlank()) {
                return LrcParser.parse(syncedLyrics, track, artist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get lyrics from lrclib /get: ${e.message}")
        }
        return null
    }

    private fun queryLrclibSearch(query: String): LyricsData? {
        try {
            val url = "$BASE_URL/search?q=" + URLEncoder.encode(query, "UTF-8")
            val response = executeHttpRequest(url) ?: return null
            val array = JSONArray(response)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val syncedLyrics = item.optString("syncedLyrics", "")
                if (syncedLyrics.isNotBlank()) {
                    val track = item.optString("trackName", query)
                    val artist = item.optString("artistName", "")
                    val parsed = LrcParser.parse(syncedLyrics, track, artist)
                    if (parsed != null && parsed.lines.isNotEmpty()) {
                        return parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to search lyrics from lrclib /search: ${e.message}")
        }
        return null
    }

    private fun executeHttpRequest(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append('\n')
                }
                reader.close()
                sb.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Http request failed for $urlString: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Clean track title from feat., remasters, etc. for better search accuracy */
    private fun cleanTrackTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*\\(feat\\..*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[feat\\..*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(ft\\..*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[ft\\..*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(remaster.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[remaster.*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(official.*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[official.*?\\]"), "")
            .replace(Regex("(?i)\\s*-\\s*remaster.*?$"), "")
            .trim()
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("(?i)\\s*feat\\..*?$"), "")
            .replace(Regex("(?i)\\s*ft\\..*?$"), "")
            .trim()
    }
}
