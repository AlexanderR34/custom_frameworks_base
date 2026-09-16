/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.wallpapers;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.widget.VideoView;

import com.android.systemui.res.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Activity that handles video selection from Google Photos, Gallery, ThemePicker, or file chooser,
 * renders a live full-screen preview, and applies it as the system video wallpaper.
 */
public class VideoWallpaperPickerActivity extends Activity {
    private static final String TAG = "VideoWallpaperPicker";
    private static final int REQUEST_PICK_VIDEO = 1001;

    private VideoView mVideoView;
    private Button mSetButton;
    private Uri mSelectedVideoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        mVideoView = new VideoView(this);
        FrameLayout.LayoutParams videoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        videoParams.gravity = Gravity.CENTER;
        root.addView(mVideoView, videoParams);

        mSetButton = new Button(this);
        mSetButton.setText(R.string.set_video_wallpaper);
        mSetButton.setAllCaps(false);
        mSetButton.setTextSize(16.0f);
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        btnParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        btnParams.setMargins(48, 0, 48, 72);
        mSetButton.setLayoutParams(btnParams);
        mSetButton.setOnClickListener(v -> applyVideoWallpaper());
        root.addView(mSetButton);

        setContentView(root);

        mVideoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0.0f, 0.0f);
            mVideoView.start();
        });

        Uri intentUri = getIntent().getData();
        if (intentUri == null && getIntent().hasExtra(Intent.EXTRA_STREAM)) {
            intentUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (intentUri == null && getIntent().getClipData() != null && getIntent().getClipData().getItemCount() > 0) {
            intentUri = getIntent().getClipData().getItemAt(0).getUri();
        }

        if (intentUri != null) {
            loadVideo(intentUri);
        } else {
            pickVideoFromGallery();
        }
    }

    private void pickVideoFromGallery() {
        Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickIntent.setType("video/*");
        pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(pickIntent, getString(R.string.select_video_wallpaper)), REQUEST_PICK_VIDEO);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch video picker", e);
            Toast.makeText(this, "No video picker available", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            loadVideo(data.getData());
        } else if (mSelectedVideoUri == null) {
            finish();
        }
    }

    private void loadVideo(Uri uri) {
        mSelectedVideoUri = uri;
        try {
            mVideoView.setVideoURI(uri);
        } catch (Exception e) {
            Log.e(TAG, "Error loading video URI in preview", e);
            Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyVideoWallpaper() {
        if (mSelectedVideoUri == null) {
            Toast.makeText(this, "No video selected", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File outFile = new File(getFilesDir(), VideoWallpaper.VIDEO_FILE_NAME);
            InputStream is = getContentResolver().openInputStream(mSelectedVideoUri);
            if (is != null) {
                FileOutputStream fos = new FileOutputStream(outFile);
                byte[] buffer = new byte[16384];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                fos.close();
                is.close();
            }

            WallpaperManager wm = WallpaperManager.getInstance(this);
            ComponentName component = new ComponentName(this, VideoWallpaper.class);
            wm.setWallpaperComponent(component);

            sendBroadcast(new Intent(VideoWallpaper.ACTION_VIDEO_CHANGED));
            Toast.makeText(this, R.string.video_wallpaper_saved, Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to set video wallpaper", e);
            Toast.makeText(this, "Failed to apply video wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null && !mVideoView.isPlaying()) {
            mVideoView.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null && mVideoView.isPlaying()) {
            mVideoView.pause();
        }
    }
}
