package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

public class SurfaceViewWrapper implements SurfaceWrapper {
    private int mState = SURFACE_NOT_CREATED;
    private final SurfaceView mVideoSurface;
    private SurfaceHolder.Callback mMediaPlaybackCallback;

    public SurfaceViewWrapper(Context context, ViewGroup root) {
        mVideoSurface = (SurfaceView) LayoutInflater.from(context).inflate(
                androidx.leanback.R.layout.lb_video_surface, root, false);
        // PIP flickering fix
        // https://github.com/google/ExoPlayer/issues/8611
        //mVideoSurface.getHolder().setFixedSize(1, 1);
        mVideoSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mMediaPlaybackCallback != null) {
                    mMediaPlaybackCallback.surfaceCreated(holder);
                }
                mState = SURFACE_CREATED;
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (mMediaPlaybackCallback != null) {
                    mMediaPlaybackCallback.surfaceChanged(holder, format, width, height);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mMediaPlaybackCallback != null) {
                    mMediaPlaybackCallback.surfaceDestroyed(holder);
                }
                mState = SURFACE_NOT_CREATED;
            }
        });
    }

    /**
     * Adds {@link SurfaceHolder.Callback} to {@link SurfaceView}.
     */
    @Override
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        mMediaPlaybackCallback = callback;

        if (callback != null) {
            if (mState == SURFACE_CREATED) {
                mMediaPlaybackCallback.surfaceCreated(mVideoSurface.getHolder());
            }
        }
    }

    @Override
    public View getSurfaceView() {
        return mVideoSurface;
    }
}
