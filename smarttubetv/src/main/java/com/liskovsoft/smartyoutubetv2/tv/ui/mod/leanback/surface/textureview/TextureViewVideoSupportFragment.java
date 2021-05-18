/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.surface.textureview;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.app.PlaybackSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Subclass of {@link PlaybackSupportFragment} that is responsible for providing a {@link SurfaceView}
 * and rendering video.
 */
public class TextureViewVideoSupportFragment extends PlaybackSupportFragment implements TextureView.SurfaceTextureListener {
    static final int SURFACE_NOT_CREATED = 0;
    static final int SURFACE_CREATED = 1;

    TextureView mVideoSurface;
    SurfaceHolder.Callback mMediaPlaybackCallback;

    int mState = SURFACE_NOT_CREATED;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        mVideoSurface = (TextureView) LayoutInflater.from(getContext()).inflate(
                R.layout.lb_video_texture, root, false);
        root.addView(mVideoSurface, 0);
        mVideoSurface.setSurfaceTextureListener(this);
        setBackgroundType(PlaybackSupportFragment.BG_LIGHT);
        return root;
    }

    /**
     * Adds {@link SurfaceHolder.Callback} to {@link SurfaceView}.
     */
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        mMediaPlaybackCallback = callback;

        if (callback != null) {
            if (mState == SURFACE_CREATED) {
                mMediaPlaybackCallback.surfaceCreated(new TextureViewSurfaceHolder(new Surface(mVideoSurface.getSurfaceTexture())));
            }
        }
    }

    @Override
    protected void onVideoSizeChanged(int width, int height) {
        int screenWidth = getView().getWidth();
        int screenHeight = getView().getHeight();

        ViewGroup.LayoutParams p = mVideoSurface.getLayoutParams();
        if (screenWidth * height > width * screenHeight) {
            // fit in screen height
            p.height = screenHeight;
            p.width = screenHeight * width / height;
        } else {
            // fit in screen width
            p.width = screenWidth;
            p.height = screenWidth * height / width;
        }
        mVideoSurface.setLayoutParams(p);
    }

    /**
     * Returns the surface view.
     */
    public View getSurfaceView() {
        return mVideoSurface;
    }

    @Override
    public void onDestroyView() {
        mVideoSurface = null;
        mState = SURFACE_NOT_CREATED;
        super.onDestroyView();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (mMediaPlaybackCallback != null) {
            mMediaPlaybackCallback.surfaceCreated(new TextureViewSurfaceHolder(new Surface(surface)));
        }
        mState = SURFACE_CREATED;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (mMediaPlaybackCallback != null) {
            mMediaPlaybackCallback.surfaceChanged(new TextureViewSurfaceHolder(new Surface(surface)), 4, width, height);
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (mMediaPlaybackCallback != null) {
            mMediaPlaybackCallback.surfaceDestroyed(new TextureViewSurfaceHolder(new Surface(surface)));
        }
        mState = SURFACE_NOT_CREATED;

        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
