/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.screenrecord;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Mixing audio and video tracks
 */
public class ScreenRecordingMuxer {
    // size of a memory page for cache coherency
    private static final int BUFFER_SIZE = 1024 * 4096;
    private String[] mFiles;
    private String mOutFile;
    private int mFormat;
    private ArrayMap<Pair<MediaExtractor, Integer>, Integer> mExtractorIndexToMuxerIndex
            = new ArrayMap<>();
    private ArrayList<MediaExtractor> mExtractors = new ArrayList<>();

    private static String TAG = "ScreenRecordingMuxer";

    private static class TrackInfo {
        final MediaExtractor extractor;
        final int extractorTrackIndex;
        final int muxerTrackIndex;
        boolean hasMoreSamples = true;
        long currentPresentationTimeUs = 0;

        TrackInfo(MediaExtractor extractor, int extractorTrackIndex, int muxerTrackIndex) {
            this.extractor = extractor;
            this.extractorTrackIndex = extractorTrackIndex;
            this.muxerTrackIndex = muxerTrackIndex;
        }
    }

    public ScreenRecordingMuxer(@MediaMuxer.Format int format, String outfileName,
            String... inputFileNames) {
        mFiles = inputFileNames;
        mOutFile = outfileName;
        mFormat = format;
        Log.d(TAG, "out: " + mOutFile + " , in: " + mFiles[0]);
    }

    /**
     * RUN IN THE BACKGROUND THREAD!
     */
    public void mux() throws IOException, IllegalStateException {
        MediaMuxer muxer = new MediaMuxer(mOutFile, mFormat);
        try {
            // Add extractors
            for (String file: mFiles) {
                MediaExtractor extractor = new MediaExtractor();
                try {
                    extractor.setDataSource(file);
                } catch (IOException e) {
                    Log.e(TAG, "error creating extractor: " + file, e);
                    continue;
                }
                Log.d(TAG, file + " track count: " + extractor.getTrackCount());
                mExtractors.add(extractor);
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    int muxId = muxer.addTrack(extractor.getTrackFormat(i));
                    Log.d(TAG, "created extractor format: " + extractor.getTrackFormat(i).toString());
                    mExtractorIndexToMuxerIndex.put(Pair.create(extractor, i), muxId);
                }
            }

            // This may throw IllegalStateException if no tracks were added above
            // Let the error propagate up so we can notify the user.
            muxer.start();

            List<TrackInfo> activeTracks = new ArrayList<>();
            for (Pair<MediaExtractor, Integer> pair: mExtractorIndexToMuxerIndex.keySet()) {
                MediaExtractor extractor = pair.first;
                int trackIdx = pair.second;
                extractor.selectTrack(trackIdx);
                int muxId = mExtractorIndexToMuxerIndex.get(pair);
                Log.d(TAG, "track format: " + extractor.getTrackFormat(trackIdx));
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                TrackInfo track = new TrackInfo(extractor, trackIdx, muxId);
                long sampleTime = extractor.getSampleTime();
                if (sampleTime >= 0) {
                    track.currentPresentationTimeUs = sampleTime;
                    track.hasMoreSamples = true;
                } else {
                    track.hasMoreSamples = false;
                }
                activeTracks.add(track);
            }

            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int offset = buffer.arrayOffset();

            // Interleaved muxing: always write the sample with the lowest presentation time
            // to produce a properly interleaved MP4 container compatible with web streaming
            // and social media upload pipelines.
            while (true) {
                TrackInfo nextTrack = null;
                long earliestTime = Long.MAX_VALUE;

                for (TrackInfo track : activeTracks) {
                    if (track.hasMoreSamples && track.currentPresentationTimeUs < earliestTime) {
                        earliestTime = track.currentPresentationTimeUs;
                        nextTrack = track;
                    }
                }

                if (nextTrack == null) {
                    // All tracks reached EOF
                    break;
                }

                buffer.clear();
                info.size = nextTrack.extractor.readSampleData(buffer, offset);
                if (info.size < 0) {
                    nextTrack.hasMoreSamples = false;
                } else {
                    info.offset = offset;
                    info.presentationTimeUs = nextTrack.extractor.getSampleTime();
                    info.flags = nextTrack.extractor.getSampleFlags();
                    muxer.writeSampleData(nextTrack.muxerTrackIndex, buffer, info);
                    nextTrack.extractor.advance();
                    long nextTime = nextTrack.extractor.getSampleTime();
                    if (nextTime < 0) {
                        nextTrack.hasMoreSamples = false;
                    } else {
                        nextTrack.currentPresentationTimeUs = nextTime;
                    }
                }
            }
        } finally {
            for (MediaExtractor extractor: mExtractors) {
                try {
                    extractor.release();
                } catch (Exception ignored) {}
            }
            try {
                muxer.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping muxer", e);
            }
            try {
                muxer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing muxer", e);
            }
        }
    }
}
