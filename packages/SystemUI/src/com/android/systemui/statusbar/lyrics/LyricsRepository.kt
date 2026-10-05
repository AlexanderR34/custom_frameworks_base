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

    private val cache = LruCache<String, LyricsData>(100)

    private fun removeAccents(str: String): String {
        val nfd = java.text.Normalizer.normalize(str, java.text.Normalizer.Form.NFD)
        return java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+").matcher(nfd).replaceAll("").trim()
    }

    suspend fun fetchLyrics(
        trackName: String,
        artistName: String,
        durationSeconds: Long = 0L
    ): LyricsData? = withContext(Dispatchers.IO) {
        if (trackName.isBlank()) return@withContext null

        val (extractedTrack, extractedArtist) = resolveTrackAndArtist(trackName, artistName)
        val cleanTrack = cleanTitle(extractedTrack)
        val cleanArtist = cleanArtist(extractedArtist)
        val normTrack = removeAccents(cleanTrack)
        val normArtist = removeAccents(cleanArtist)

        val cacheKey = "${cleanTrack.lowercase()}|${cleanArtist.lowercase()}"
        cache.get(cacheKey)?.let {
            return@withContext it
        }

        var lyrics: LyricsData? = null

        // Attempt 1: Exact /get with clean track and artist
        if (cleanTrack.isNotBlank() && cleanArtist.isNotBlank()) {
            if (durationSeconds > 0) {
                lyrics = queryLrclibGet(cleanTrack, cleanArtist, durationSeconds)
            }
            if (lyrics == null) {
                lyrics = queryLrclibGet(cleanTrack, cleanArtist, 0L)
            }
        }

        // Attempt 2: Exact /get with normalized (unaccented) track and artist
        if (lyrics == null && normTrack.isNotBlank() && (normTrack != cleanTrack || normArtist != cleanArtist)) {
            if (durationSeconds > 0) {
                lyrics = queryLrclibGet(normTrack, normArtist, durationSeconds)
            }
            if (lyrics == null) {
                lyrics = queryLrclibGet(normTrack, normArtist, 0L)
            }
        }

        // Attempt 3: Search with normalized track and artist
        if (lyrics == null && normTrack.isNotBlank() && normArtist.isNotBlank()) {
            lyrics = queryLrclibSearch("$normTrack $normArtist", durationSeconds)
        }

        // Attempt 4: Search with clean track and artist
        if (lyrics == null && cleanTrack.isNotBlank() && cleanArtist.isNotBlank()) {
            lyrics = queryLrclibSearch("$cleanTrack $cleanArtist", durationSeconds)
        }

        // Attempt 5: If original raw title contained separator (e.g. "Artist - Track"), try inverted
        if (lyrics == null && trackName.contains(" - ")) {
            val parts = trackName.split(" - ", limit = 2)
            val invTrack = cleanTitle(parts[0])
            val invArtist = cleanArtist(parts[1])
            val invNormTrack = removeAccents(invTrack)
            val invNormArtist = removeAccents(invArtist)
            if (invTrack.isNotBlank()) {
                lyrics = queryLrclibGet(invTrack, invArtist, durationSeconds)
                    ?: queryLrclibGet(invNormTrack, invNormArtist, 0L)
                    ?: queryLrclibSearch("$invNormTrack $invNormArtist", durationSeconds)
            }
        }

        // Attempt 6: Search with normalized track name only
        if (lyrics == null && normTrack.isNotBlank()) {
            lyrics = queryLrclibSearch(normTrack, durationSeconds)
        }

        // Attempt 7: Search with clean track name only
        if (lyrics == null && cleanTrack.isNotBlank() && cleanTrack != normTrack) {
            lyrics = queryLrclibSearch(cleanTrack, durationSeconds)
        }

        // Attempt 8: Fallback search with raw track name
        if (lyrics == null && trackName.isNotBlank() && trackName != cleanTrack) {
            lyrics = queryLrclibSearch(trackName, durationSeconds)
        }

        if (lyrics != null) {
            cache.put(cacheKey, lyrics)
        }
        return@withContext lyrics
    }

    private fun resolveTrackAndArtist(rawTrack: String, rawArtist: String): Pair<String, String> {
        var track = rawTrack.trim()
        var artist = rawArtist.trim()

        // If artist is empty or is a generic browser/player name, check if track has "Artist - Song"
        val isGenericArtist = artist.isEmpty() ||
                artist.equals("YouTube", ignoreCase = true) ||
                artist.equals("Brave", ignoreCase = true) ||
                artist.equals("Chrome", ignoreCase = true) ||
                artist.equals("Browser", ignoreCase = true) ||
                artist.equals("Unknown", ignoreCase = true)

        if (isGenericArtist && track.contains(" - ")) {
            val parts = track.split(" - ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        } else if (isGenericArtist && track.contains(" : ")) {
            val parts = track.split(" : ", limit = 2)
            artist = parts[0].trim()
            track = parts[1].trim()
        }

        return Pair(track, artist)
    }

    private fun queryLrclibGet(
        track: String,
        artist: String,
        durationSeconds: Long
    ): LyricsData? {
        if (track.isBlank()) return null
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
                val parsed = LrcParser.parse(syncedLyrics, track, artist)
                if (parsed != null && parsed.lines.isNotEmpty()) return parsed
            }

            val plainLyrics = json.optString("plainLyrics", "")
            if (plainLyrics.isNotBlank()) {
                return parsePlainLyrics(plainLyrics, track, artist, durationSeconds)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get lyrics from lrclib /get: ${e.message}")
        }
        return null
    }

    private fun queryLrclibSearch(query: String, durationSeconds: Long = 0L): LyricsData? {
        if (query.isBlank()) return null
        try {
            val url = "$BASE_URL/search?q=" + URLEncoder.encode(query, "UTF-8")
            val response = executeHttpRequest(url) ?: return null
            val array = JSONArray(response)
            
            // First pass: look for synchronized lyrics
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

            // Second pass: fallback to plain lyrics if no synced available
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val plainLyrics = item.optString("plainLyrics", "")
                if (plainLyrics.isNotBlank()) {
                    val track = item.optString("trackName", query)
                    val artist = item.optString("artistName", "")
                    val parsed = parsePlainLyrics(plainLyrics, track, artist, durationSeconds)
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

    private fun parsePlainLyrics(
        plainText: String,
        trackName: String,
        artistName: String,
        durationSeconds: Long
    ): LyricsData? {
        val rawLines = plainText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) return null

        val totalDurationMs = if (durationSeconds > 0) durationSeconds * 1000L else rawLines.size * 3500L
        val timePerLineMs = maxOf(2000L, totalDurationMs / rawLines.size)

        val resultLines = rawLines.mapIndexed { index, text ->
            LyricLine(index * timePerLineMs, text)
        }

        return LyricsData(
            trackName = trackName,
            artistName = artistName,
            lines = resultLines
        )
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

    /** Clean track title from feat., remasters, official tags, etc. for search accuracy */
    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\s*[\\(\\[](official\\s*(music\\s*)?video|official\\s*audio|official\\s*hd\\s*video|video\\s*oficial|audio|lyrics?|lyric\\s*video|letra|visualizer|mv|4k|hd|hq|remaster.*?|live.*?|subtitulado.*?)[\\)\\]]"), "")
            .replace(Regex("(?i)\\s*\\(feat\\..*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[feat\\..*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(ft\\..*?\\)"), "")
            .replace(Regex("(?i)\\s*\\[ft\\..*?\\]"), "")
            .replace(Regex("(?i)\\s*\\(with .*?\\)"), "")
            .replace(Regex("(?i)\\s*-\\s*remaster.*?$"), "")
            .replace(Regex("(?i)\\s*-\\s*bonus track.*$"), "")
            .replace(Regex("(?i)\\s*\\|\\s*.*$"), "")
            .trim()
    }

    private fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("(?i)\\s*feat\\..*?$"), "")
            .replace(Regex("(?i)\\s*ft\\..*?$"), "")
            .split(",", "/", "&")[0]
            .trim()
    }
}
