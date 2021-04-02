package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.surfacefragment;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.tmp.surface.textureview.TextureViewSurfaceHolder;

public class TextureViewWrapper implements SurfaceWrapper {
    private int mState = SURFACE_NOT_CREATED;
    private final TextureView mVideoSurface;
    private SurfaceHolder.Callback mMediaPlaybackCallback;

    public TextureViewWrapper(Context context, ViewGroup root) {
        mVideoSurface = (TextureView) LayoutInflater.from(context).inflate(
                R.layout.lb_video_texture, root, false);
        mVideoSurface.setSurfaceTextureListener(new SurfaceTextureListener() {
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
        });
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
    public View getSurfaceView() {
        return mVideoSurface;
    }
}
