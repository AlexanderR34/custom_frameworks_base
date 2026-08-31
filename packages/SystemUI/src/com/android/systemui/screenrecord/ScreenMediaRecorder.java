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

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

import static com.android.systemui.screenrecord.ScreenRecordingAudioSource.INTERNAL;
import static com.android.systemui.screenrecord.ScreenRecordingAudioSource.MIC;
import static com.android.systemui.screenrecord.ScreenRecordingAudioSource.MIC_AND_INTERNAL;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.media.projection.IMediaProjection;
import android.media.projection.IMediaProjectionManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.projection.StopReason;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import com.android.internal.R;
import com.android.systemui.mediaprojection.MediaProjectionCaptureTarget;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Recording screen and mic/internal audio
 */
public class ScreenMediaRecorder extends MediaProjection.Callback {
    private static final int TOTAL_NUM_TRACKS = 2; // True Stereo (Left + Right)
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int VIDEO_FRAME_RATE_TO_RESOLUTION_RATIO = 6;
    private static final int LOW_VIDEO_FRAME_RATE_TO_RESOLUTION_RATIO = 2;
    private static final int LOW_VIDEO_FRAME_RATE = 25;
    private static final int AUDIO_BIT_RATE = 256000; // 256 kbps High-Fidelity AAC Stereo
    private static final int AUDIO_SAMPLE_RATE = 48000; // 48 kHz Standard
    private static final int MAX_DURATION_MS = 60 * 60 * 1000;
    private static final long MAX_FILESIZE_BYTES = 5000000000L;
    private static final long MAX_FILESIZE_BYTES_LONGER = 16106100000L; // 15 GiB
    private static final String TAG = "ScreenMediaRecorder";

    private File mTempVideoFile;
    private File mTempAudioFile;
    private MediaProjection mMediaProjection;
    private Surface mInputSurface;
    private VirtualDisplay mVirtualDisplay;
    private MediaRecorder mMediaRecorder;
    private int mUid;
    private ScreenInternalAudioRecorder mAudio;
    private ScreenRecordingAudioSource mAudioSource;
    private Long mStartTimeMillis = 0L;
    private final MediaProjectionCaptureTarget mCaptureRegion;
    private final Handler mHandler;
    private final int mDisplayId;
    private int mMaxRefreshRate;
    private String mAvcProfileLevel;

    private boolean mLowQuality;
    private boolean mLongerDuration;
    private boolean mHEVC;
    private int mVideoQuality = 0;
    private int mResolution = 0;
    private int mFrameRate = 0;
    private int mTimeLimit = 0;
    private int mFileSizeLimit = 0;

    private Context mContext;
    ScreenMediaRecorderListener mListener;

    public ScreenMediaRecorder(
            Context context,
            Handler handler,
            int uid,
            ScreenRecordingAudioSource audioSource,
            MediaProjectionCaptureTarget captureRegion,
            int displayId,
            ScreenMediaRecorderListener listener) {
        this(context, handler, uid, audioSource, captureRegion,
                displayId, listener, false, false, false);
    }

    public ScreenMediaRecorder(
            Context context,
            Handler handler,
            int uid,
            ScreenRecordingAudioSource audioSource,
            MediaProjectionCaptureTarget captureRegion,
            int displayId,
            ScreenMediaRecorderListener listener,
            boolean lowQuality,
            boolean longerDuration,
            boolean hevc) {
        mContext = context;
        mHandler = handler;
        mUid = uid;
        mCaptureRegion = captureRegion;
        mListener = listener;
        mAudioSource = audioSource;
        mDisplayId = displayId;
        mLowQuality = lowQuality;
        mLongerDuration = longerDuration;
        mHEVC = hevc;
        mMaxRefreshRate = mContext.getResources().getInteger(
                com.android.systemui.res.R.integer.config_screenRecorderMaxFramerate);
        mAvcProfileLevel = mContext.getResources().getString(
                com.android.systemui.res.R.string.config_screenRecorderAVCProfileLevel);
    }

    public void setLowQuality(boolean low) {
        mLowQuality = low;
    }

    public void setLongerDuration(boolean longer) {
        mLongerDuration = longer;
    }

    public void setHEVC(boolean hevc) {
        mHEVC = hevc;
    }

    public void setVideoQuality(int quality) {
        mVideoQuality = quality;
    }

    public void setResolution(int resolution) {
        mResolution = resolution;
    }

    public void setFrameRate(int frameRate) {
        mFrameRate = frameRate;
    }

    public void setTimeLimit(int timeLimit) {
        mTimeLimit = timeLimit;
    }

    public void setFileSizeLimit(int fileSizeLimit) {
        mFileSizeLimit = fileSizeLimit;
    }

    private void prepare() throws IOException, RemoteException, RuntimeException {
        //Setup media projection
        IBinder b = ServiceManager.getService(MEDIA_PROJECTION_SERVICE);
        IMediaProjectionManager mediaService =
                IMediaProjectionManager.Stub.asInterface(b);
        IMediaProjection proj =
                mediaService.createProjection(
                        mUid,
                        mContext.getPackageName(),
                        MediaProjectionManager.TYPE_SCREEN_CAPTURE,
                        false,
                        mDisplayId);
        IMediaProjection projection = IMediaProjection.Stub.asInterface(proj.asBinder());
        if (mCaptureRegion != null) {
            projection.setLaunchCookie(mCaptureRegion.getLaunchCookie());
            projection.setTaskId(mCaptureRegion.getTaskId());
        }
        mMediaProjection = new MediaProjection(mContext, projection);
        mMediaProjection.registerCallback(this, mHandler);

        File cacheDir = mContext.getCacheDir();
        cacheDir.mkdirs();
        mTempVideoFile = File.createTempFile("temp", ".mp4", cacheDir);

        // Set up media recorder
        mMediaRecorder = new MediaRecorder();

        // Set up audio source
        if (mAudioSource == MIC) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);


        // Set up video
        DisplayMetrics metrics = new DisplayMetrics();
        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display display = dm.getDisplay(mDisplayId);
        display.getRealMetrics(metrics);

        int nativeWidth = metrics.widthPixels;
        int nativeHeight = metrics.heightPixels;
        boolean isPortrait = nativeHeight >= nativeWidth;
        int maxDim = Math.max(nativeWidth, nativeHeight);
        int minDim = Math.min(nativeWidth, nativeHeight);
        float aspectRatio = (float) minDim / (float) maxDim;

        int reqWidth = nativeWidth;
        int reqHeight = nativeHeight;

        if (mResolution == 1) { // 1440p (2K / QHD)
            int shortSide = 1440;
            int longSide = Math.round(shortSide / aspectRatio);
            reqWidth = isPortrait ? shortSide : longSide;
            reqHeight = isPortrait ? longSide : shortSide;
        } else if (mResolution == 2) { // 1220p (1.5K)
            int shortSide = 1220;
            int longSide = Math.round(shortSide / aspectRatio);
            reqWidth = isPortrait ? shortSide : longSide;
            reqHeight = isPortrait ? longSide : shortSide;
        } else if (mResolution == 3) { // 1080p (FHD)
            int shortSide = 1080;
            int longSide = Math.round(shortSide / aspectRatio);
            reqWidth = isPortrait ? shortSide : longSide;
            reqHeight = isPortrait ? longSide : shortSide;
        } else if (mResolution == 4) { // 720p (HD)
            int shortSide = 720;
            int longSide = Math.round(shortSide / aspectRatio);
            reqWidth = isPortrait ? shortSide : longSide;
            reqHeight = isPortrait ? longSide : shortSide;
        } else if (mResolution == 5) { // 480p (SD)
            int shortSide = 480;
            int longSide = Math.round(shortSide / aspectRatio);
            reqWidth = isPortrait ? shortSide : longSide;
            reqHeight = isPortrait ? longSide : shortSide;
        }

        // Enforce 16-pixel block alignment required by hardware video encoders
        reqWidth = (reqWidth / 16) * 16;
        reqHeight = (reqHeight / 16) * 16;

        int refreshRate;
        if (mFrameRate == 1) { // 60 FPS
            refreshRate = 60;
        } else if (mFrameRate == 2) { // 30 FPS
            refreshRate = 30;
        } else if (mFrameRate == 3) { // 90 FPS
            refreshRate = 90;
        } else if (mFrameRate == 4) { // 120 FPS
            refreshRate = 120;
        } else { // 0: Auto / Native display refresh rate
            refreshRate = mLowQuality ? LOW_VIDEO_FRAME_RATE : (int) display.getRefreshRate();
            if (mMaxRefreshRate != 0 && refreshRate > mMaxRefreshRate) refreshRate = mMaxRefreshRate;
        }

        int[] dimens = getSupportedSize(reqWidth, reqHeight, refreshRate);
        int width = dimens[0];
        int height = dimens[1];
        if (mFrameRate == 0) {
            refreshRate = dimens[2];
        }

        int vidBitRate;
        if (mHEVC) {
            if (mVideoQuality == 1) { // Medium
                vidBitRate = 5000000; // 5 Mbps (~110 MB for 3 mins)
            } else if (mVideoQuality == 2) { // Low
                vidBitRate = 2500000; // 2.5 Mbps (~55 MB for 3 mins)
            } else { // High / Default (0)
                vidBitRate = mLowQuality ? 3500000 : 10000000; // 10 Mbps (~220 MB for 3 mins)
            }
        } else {
            if (mVideoQuality == 1) { // Medium
                vidBitRate = 8000000; // 8 Mbps (~170 MB for 3 mins)
            } else if (mVideoQuality == 2) { // Low
                vidBitRate = 4000000; // 4 Mbps (~85 MB for 3 mins)
            } else { // High / Default (0)
                vidBitRate = mLowQuality ? 5000000 : 14000000; // 14 Mbps (~300 MB for 3 mins)
            }
        }

        int maxDurationMs = 0;
        if (mTimeLimit == 1) {
            maxDurationMs = 5 * 60 * 1000;
        } else if (mTimeLimit == 2) {
            maxDurationMs = 10 * 60 * 1000;
        } else if (mTimeLimit == 3) {
            maxDurationMs = 30 * 60 * 1000;
        } else if (mTimeLimit == 4) {
            maxDurationMs = 60 * 60 * 1000;
        } else {
            maxDurationMs = mLongerDuration ? 0 : MAX_DURATION_MS;
        }

        long maxFilesize = MAX_FILESIZE_BYTES;
        if (mFileSizeLimit == 1) {
            maxFilesize = 10L * 1024 * 1024;
        } else if (mFileSizeLimit == 2) {
            maxFilesize = 100L * 1024 * 1024;
        } else if (mFileSizeLimit == 3) {
            maxFilesize = 500L * 1024 * 1024;
        } else if (mFileSizeLimit == 4) {
            maxFilesize = 1000L * 1024 * 1024;
        } else if (mFileSizeLimit == 5) {
            maxFilesize = 15L * 1024 * 1024 * 1024;
        } else {
            maxFilesize = mLongerDuration ? MAX_FILESIZE_BYTES_LONGER : MAX_FILESIZE_BYTES;
        }

        if (!mHEVC) {
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            try {
                mMediaRecorder.setVideoEncodingProfileLevel(
                        MediaCodecInfo.CodecProfileLevel.AVCProfileMain,
                        mLowQuality ? MediaCodecInfo.CodecProfileLevel.AVCLevel32
                        : MediaCodecInfo.CodecProfileLevel.AVCLevel51);
            } catch (Exception ignored) {}
        } else {
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);
            try {
                mMediaRecorder.setVideoEncodingProfileLevel(
                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
                        mLowQuality ? MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31
                        : MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51);
            } catch (Exception ignored) {}
        }
        mMediaRecorder.setVideoSize(width, height);
        mMediaRecorder.setVideoFrameRate(refreshRate);
        mMediaRecorder.setVideoEncodingBitRate(vidBitRate);
        mMediaRecorder.setMaxDuration(maxDurationMs);
        mMediaRecorder.setMaxFileSize(maxFilesize);

        // Set up audio
        if (mAudioSource == MIC) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setAudioChannels(TOTAL_NUM_TRACKS);
            mMediaRecorder.setAudioEncodingBitRate(AUDIO_BIT_RATE);
            mMediaRecorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE);
        }

        mMediaRecorder.setOutputFile(mTempVideoFile);
        mMediaRecorder.prepare();
        // Create surface
        mInputSurface = mMediaRecorder.getSurface();

        VirtualDisplayConfig.Builder vdBuilder = new VirtualDisplayConfig.Builder(
                "Recording Display",
                width,
                height,
                metrics.densityDpi)
                .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC)
                .setSurface(mInputSurface);

        if (refreshRate > 0) {
            vdBuilder.setRequestedRefreshRate(refreshRate);
        }

        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                vdBuilder,
                new VirtualDisplay.Callback() {
                    @Override
                    public void onStopped() {
                        onStop();
                    }
                },
                mHandler);

        mMediaRecorder.setOnInfoListener((mr, what, extra) -> mListener.onInfo(mr, what, extra));
        if (mAudioSource == INTERNAL ||
                mAudioSource == MIC_AND_INTERNAL) {
            mTempAudioFile = File.createTempFile("temp", ".aac",
                    mContext.getCacheDir());
            mAudio = new ScreenInternalAudioRecorder(mTempAudioFile.getAbsolutePath(),
                    mMediaProjection, mAudioSource == MIC_AND_INTERNAL);
        }

    }

    /**
     * Match human-readable AVC level name to its constant value.
     */
    private int getAvcProfileLevelCodeByName(final String levelName) {
        switch (levelName) {
            case "3": return MediaCodecInfo.CodecProfileLevel.AVCLevel3;
            case "3.1": return MediaCodecInfo.CodecProfileLevel.AVCLevel31;
            case "3.2": return MediaCodecInfo.CodecProfileLevel.AVCLevel32;
            case "4": return MediaCodecInfo.CodecProfileLevel.AVCLevel4;
            case "4.1": return MediaCodecInfo.CodecProfileLevel.AVCLevel41;
            default:
            case "4.2": return MediaCodecInfo.CodecProfileLevel.AVCLevel42;
        }
    }

    /**
     * Find the highest supported screen resolution and refresh rate for the given dimensions on
     * this device, up to actual size and given rate.
     * If possible this will return the same values as given, but values may be smaller on some
     * devices.
     *
     * @param screenWidth Actual pixel width of screen
     * @param screenHeight Actual pixel height of screen
     * @param refreshRate Desired refresh rate
     * @return array with supported width, height, and refresh rate
     */
    private int[] getSupportedSize(final int screenWidth, final int screenHeight, int refreshRate) {
        int alignedWidth = (screenWidth / 16) * 16;
        int alignedHeight = (screenHeight / 16) * 16;

        // If the user manually selected resolution or frame rate, trust their selection
        // Hardware encoders with Level 5.1 support up to 4K @ 120 FPS natively
        if (mResolution != 0 || mFrameRate != 0) {
            Log.d(TAG, "Using requested manual parameters: " + alignedWidth + "x" + alignedHeight + " @" + refreshRate + "fps");
            return new int[]{alignedWidth, alignedHeight, refreshRate};
        }

        try {
            String videoType = mHEVC ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC;
            MediaCodec decoder = MediaCodec.createDecoderByType(videoType);
            MediaCodecInfo.VideoCapabilities vc = decoder.getCodecInfo()
                    .getCapabilitiesForType(videoType).getVideoCapabilities();
            decoder.release();

            int minDim = Math.min(alignedWidth, alignedHeight);
            int maxDim = Math.max(alignedWidth, alignedHeight);

            if (vc.isSizeSupported(minDim, maxDim) || vc.isSizeSupported(maxDim, minDim)) {
                return new int[]{alignedWidth, alignedHeight, refreshRate};
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking codec capabilities", e);
        }

        return new int[]{alignedWidth, alignedHeight, refreshRate};
    }

    /**
    * Start screen recording
    */
    public void start() throws IOException, RemoteException, RuntimeException {
        Log.d(TAG, "start recording");
        prepare();
        mMediaRecorder.start();
        mStartTimeMillis = System.currentTimeMillis();
        mListener.onStarted();
        recordInternalAudio();
    }

    /**
     * End screen recording, throws an exception if stopping recording failed
     */
    public void end(@StopReason int stopReason) throws IOException {
        Closer closer = new Closer();

        // MediaRecorder might throw RuntimeException if stopped immediately after starting
        // We should remove the recording in this case as it will be invalid
        closer.register(mMediaRecorder::stop);
        closer.register(mMediaRecorder::release);
        closer.register(mInputSurface::release);
        closer.register(mVirtualDisplay::release);
        closer.register(() -> {
            if (stopReason == StopReason.STOP_UNKNOWN) {
                // Attempt to call MediaProjection#stop() even if it might have already been called.
                // If projection has already been stopped, then nothing will happen. Else, stop
                // will be logged as a manually requested stop from host app.
                mMediaProjection.stop();
            } else {
                // In any other case, the stop reason is related to the recorder, so pass it on here
                mMediaProjection.stop(stopReason);
            }
        });
        closer.register(this::stopInternalAudioRecording);

        closer.close();

        mMediaRecorder = null;
        mMediaProjection = null;

        Log.d(TAG, "end recording");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "The system notified about stopping the projection");
        mListener.onStopped(mContext.getUserId(), StopReason.STOP_UNKNOWN);
    }

    private void stopInternalAudioRecording() {
        if (mAudioSource == INTERNAL || mAudioSource == MIC_AND_INTERNAL) {
            mAudio.end();
            mAudio = null;
        }
    }

    private  void recordInternalAudio() throws IllegalStateException {
        if (mAudioSource == INTERNAL || mAudioSource == MIC_AND_INTERNAL) {
            mAudio.start();
        }
    }

    /**
     * Store recorded video
     */
    public SavedRecording save() throws IOException, IllegalStateException {
        String saveDate = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String fileName = mStartTimeMillis > 0L
                ? String.format("screen-%s-%d.mp4", saveDate, mStartTimeMillis)
                : String.format("screen-%s.mp4", saveDate);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis());
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());

        ContentResolver resolver = mContext.getContentResolver();
        Uri collectionUri = MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = resolver.insert(collectionUri, values);

        Log.d(TAG, itemUri.toString());
        if (mAudioSource == MIC_AND_INTERNAL || mAudioSource == INTERNAL) {
            try {
                Log.d(TAG, "muxing recording");
                File file = File.createTempFile("temp", ".mp4",
                        mContext.getCacheDir());
                ScreenRecordingMuxer muxer = new ScreenRecordingMuxer(
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                        file.getAbsolutePath(),
                        mTempVideoFile.getAbsolutePath(),
                        mTempAudioFile.getAbsolutePath());
                muxer.mux();
                mTempVideoFile.delete();
                mTempVideoFile = file;
            } catch (IOException e) {
                Log.e(TAG, "muxing recording " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Add to the mediastore
        OutputStream os = resolver.openOutputStream(itemUri, "w");
        Files.copy(mTempVideoFile.toPath(), os);
        os.close();
        if (mTempAudioFile != null) mTempAudioFile.delete();
        SavedRecording recording = new SavedRecording(
                itemUri, mTempVideoFile, getRequiredThumbnailSize());
        mTempVideoFile.delete();
        return recording;
    }

    /**
     * Returns the required {@code Size} of the thumbnail.
     */
    private Size getRequiredThumbnailSize() {
        int thumbnailIconHeight = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.notification_big_picture_max_height);
        int thumbnailIconWidth = mContext.getResources().getDimensionPixelSize(
                com.android.internal.R.dimen.notification_big_picture_max_width);
        return new Size(thumbnailIconWidth, thumbnailIconHeight);
    }

    /**
     * Release the resources without saving the data
     */
    public void release() {
        if (mTempVideoFile != null) {
            mTempVideoFile.delete();
        }
        if (mTempAudioFile != null) {
            mTempAudioFile.delete();
        }
    }

    /**
    * Object representing the recording
    */
    public class SavedRecording {

        private Uri mUri;
        private Icon mThumbnailIcon;

        public SavedRecording(Uri uri, File file, Size thumbnailSize) {
            mUri = uri;
            try {
                Bitmap thumbnailBitmap = ThumbnailUtils.createVideoThumbnail(
                        file, thumbnailSize, null);
                mThumbnailIcon = Icon.createWithBitmap(thumbnailBitmap);
            } catch (IOException e) {
                Log.e(TAG, "Error creating thumbnail", e);
            }
        }

        public Uri getUri() {
            return mUri;
        }

        public @Nullable Icon getThumbnail() {
            return mThumbnailIcon;
        }
    }

    public interface ScreenMediaRecorderListener {

        /**
         * Called when the recording actually starts
         */
        void onStarted();

        /**
         * Called to indicate an info or a warning during recording.
         * See {@link MediaRecorder.OnInfoListener} for the full description.
         */
        void onInfo(MediaRecorder mr, int what, int extra);

        /**
         * Called when the recording stopped by the system.
         * For example, this might happen when doing partial screen sharing of an app
         * and the app that is being captured is closed.
         */
        void onStopped(int userId, @StopReason int stopReason);
    }

    /**
     * Allows to register multiple {@link Closeable} objects and close them all by calling
     * {@link Closer#close}. If there is an exception thrown during closing of one
     * of the registered closeables it will continue trying closing the rest closeables.
     * If there are one or more exceptions thrown they will be re-thrown at the end.
     * In case of multiple exceptions only the first one will be thrown and all the rest
     * will be printed.
     */
    private static class Closer implements Closeable {
        private final List<Closeable> mCloseables = new ArrayList<>();

        void register(Closeable closeable) {
            mCloseables.add(closeable);
        }

        @Override
        public void close() throws IOException {
            Throwable throwable = null;

            for (int i = 0; i < mCloseables.size(); i++) {
                Closeable closeable = mCloseables.get(i);

                try {
                    closeable.close();
                } catch (Throwable e) {
                    if (throwable == null) {
                        throwable = e;
                    } else {
                        e.printStackTrace();
                    }
                }
            }

            if (throwable != null) {
                if (throwable instanceof IOException) {
                    throw (IOException) throwable;
                }

                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                }

                throw (Error) throwable;
            }
        }
    }
}
