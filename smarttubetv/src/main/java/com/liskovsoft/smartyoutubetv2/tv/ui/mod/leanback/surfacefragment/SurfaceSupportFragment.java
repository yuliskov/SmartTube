package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.surfacefragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import androidx.leanback.app.PlaybackSupportFragment;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.ResizeMode;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;

/**
 * Subclass of {@link PlaybackSupportFragment} that is responsible for providing a {@link SurfaceView}
 * and rendering video.
 */
public class SurfaceSupportFragment extends PlaybackSupportFragment {
    SurfaceWrapper mVideoSurfaceWrapper;
    AspectRatioFrameLayout mVideoSurfaceRoot;
    private int mBackgroundResId;
    private float mAspectRatio;
    private float mVideoAspectRatio;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        mVideoSurfaceWrapper = PlayerTweaksData.instance(getContext()).isTextureViewEnabled() ?
                new TextureViewWrapper(getContext(), root) : new SurfaceViewWrapper(getContext(), root);
        mVideoSurfaceRoot = root.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.surface_root);
        mVideoSurfaceRoot.addView(mVideoSurfaceWrapper.getSurfaceView(), 0);
        setBackgroundType(PlaybackSupportFragment.BG_LIGHT);
        return root;
    }

    /**
     * Adds {@link SurfaceHolder.Callback} to {@link SurfaceView}.
     */
    public void setSurfaceHolderCallback(SurfaceHolder.Callback callback) {
        if (mVideoSurfaceWrapper != null) {
            mVideoSurfaceWrapper.setSurfaceHolderCallback(callback);
        }
    }

    @Override
    protected void onVideoSizeChanged(int width, int height) {
        mVideoAspectRatio = ((float) width) / height;
        mVideoSurfaceRoot.setAspectRatio(mAspectRatio == 0 ? mVideoAspectRatio : mAspectRatio);
    }

    /**
     * Returns the surface view.
     */
    public View getSurfaceView() {
        return mVideoSurfaceWrapper.getSurfaceView();
    }

    @Override
    public void onDestroyView() {
        mVideoSurfaceWrapper = null;
        super.onDestroyView();
    }

    /**
     * Sets the {@link ResizeMode}.
     *
     * @param resizeMode The {@link ResizeMode}.
     */
    public void setResizeMode(@ResizeMode int resizeMode) {
        mVideoSurfaceRoot.setResizeMode(resizeMode);
    }

    /** Returns the {@link ResizeMode}. */
    public @ResizeMode int getResizeMode() {
        return mVideoSurfaceRoot.getResizeMode();
    }

    public void setAspectRatio(float aspectRatio) {
        mAspectRatio = aspectRatio;
        mVideoSurfaceRoot.setAspectRatio(mAspectRatio == 0 ? mVideoAspectRatio : mAspectRatio);
    }

    /**
     * Setup player's background used when controls are showed.
     * @param resId background
     */
    public void setBackgroundResource(int resId) {
        if (resId <= 0 || mBackgroundResId == resId) {
            return;
        }

        View backgroundView = (View) Helpers.getField(this, "mBackgroundView");

        if (backgroundView != null) {
            backgroundView.setBackgroundResource(resId);
            mBackgroundResId = resId;
        }
    }
}
