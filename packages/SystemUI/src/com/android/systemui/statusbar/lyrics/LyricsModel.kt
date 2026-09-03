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

import java.util.regex.Pattern

/** Represents a single line of time-synchronized lyrics. */
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

/** Represents a full set of synchronized lyrics for a track. */
data class LyricsData(
    val trackName: String,
    val artistName: String,
    val lines: List<LyricLine>
) {
    /**
     * Finds the index of the active lyric line for the given playback position in milliseconds.
     * Returns -1 if playback has not yet reached the first lyric line.
     */
    fun findCurrentIndex(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var result = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= positionMs) {
                result = i
            } else {
                break
            }
        }
        return result
    }
}

/** Parser for LRC synchronized lyrics format. */
object LrcParser {
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

    fun parse(lrcContent: String?, trackName: String = "", artistName: String = ""): LyricsData? {
        if (lrcContent.isNullOrBlank()) return null

        val resultLines = mutableListOf<LyricLine>()
        val lines = lrcContent.lines()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("[ti:") || line.startsWith("[ar:") ||
                line.startsWith("[al:") || line.startsWith("[by:") || line.startsWith("[re:") ||
                line.startsWith("[ve:") || line.startsWith("[length:")) {
                continue
            }

            val matcher = TIME_TAG_PATTERN.matcher(line)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3)
                val ms = when {
                    msStr == null -> 0L
                    msStr.length == 1 -> (msStr.toLongOrNull() ?: 0L) * 100
                    msStr.length == 2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    else -> msStr.take(3).toLongOrNull() ?: 0L
                }
                timestamps.add((min * 60 + sec) * 1000 + ms)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val lyricText = line.substring(lastMatchEnd).trim()
                if (lyricText.isNotEmpty()) {
                    for (ts in timestamps) {
                        resultLines.add(LyricLine(ts, lyricText))
                    }
                }
            }
        }

        if (resultLines.isEmpty()) return null
        resultLines.sortBy { it.timestampMs }

        return LyricsData(
            trackName = trackName,
            artistName = artistName,
            lines = resultLines
        )
    }
}
