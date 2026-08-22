package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.leanback.app.PlaybackSupportFragment;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.ResizeMode;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * Subclass of {@link PlaybackSupportFragment} that is responsible for providing a {@link SurfaceView}
 * and rendering video.
 */
public class SurfacePlaybackFragment extends PlaybackSupportFragment {
    private SurfaceWrapper mVideoSurfaceWrapper;
    private AspectRatioFrameLayout mVideoSurfaceRoot;
    private View mNightlightOverlay;
    private SubtitleView mLeanbackSubtitles;
    private int mSubtitlesPadding;
    private int mBackgroundResId;
    private float mAspectRatio;
    private float mPixelRatio = 1.0f;
    private float mVideoAspectRatio;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) super.onCreateView(inflater, container, savedInstanceState);
        if (root == null) {
            throw new IllegalStateException("Can't create root of SurfacePlaybackFragment");
        }
        mVideoSurfaceWrapper = createSurfaceWrapper(root);
        mVideoSurfaceRoot = root.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.surface_root);
        mVideoSurfaceRoot.addView(mVideoSurfaceWrapper.getSurfaceView(), 0);
        mVideoSurfaceRoot.setAspectRatioListener((targetAspectRatio, naturalAspectRatio, aspectRatioMismatch) -> scaleIfNeeded());
        mLeanbackSubtitles = root.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.leanback_subtitles);
        mSubtitlesPadding = mLeanbackSubtitles.getPaddingLeft();
        setBackgroundType(PlaybackSupportFragment.BG_LIGHT);
        applyNightlight();
        return root;
    }

    private SurfaceWrapper createSurfaceWrapper(ViewGroup root) {
        // The Nightlight ColorMatrix only reaches the video when it lives in the view tree,
        // i.e. a TextureView. A SurfaceView is a separate hardware layer below the window that
        // view-level filters can't touch. So while a warmth is set use TextureView; with warmth
        // off the stock SurfaceView (HDR passthrough / tunneling) is kept.
        // Exception: tunneled playback only works on a SurfaceView, and devices that have it
        // enabled usually need it for smooth playback — there the tint falls back to an overlay.
        PlayerTweaksData tweaks = PlayerTweaksData.instance(getContext());
        if (tweaks.isTextureViewEnabled()
                || (tweaks.isNightlightEnabled() && !tweaks.isTunneledPlaybackEnabled())
                || PlayerData.instance(getContext()).getRotationAngle() != 0) {
            return new TextureViewWrapper(getContext(), root);
        }
        return new SurfaceViewWrapper(getContext(), root);
    }

    /**
     * Warm-tint just the video via a hardware-layer ColorMatrix on the video container.
     * Skipped when "Apply to UI" is on — the activity's decor layer already covers the video then.
     */
    protected void applyNightlight() {
        if (mVideoSurfaceRoot == null) {
            return;
        }

        PlayerTweaksData tweaks = PlayerTweaksData.instance(getContext());
        boolean tintVideo = tweaks.isNightlightActive() && !tweaks.isNightlightOnUi();
        boolean surfaceLocked = tweaks.isTunneledPlaybackEnabled(); // tunneling only works on a SurfaceView

        // Warmth enabled mid-playback over a SurfaceView: a ColorMatrix can't reach a SurfaceView.
        // The swap restarts the engine, and this method can be invoked from inside a player callback
        // (onTrackChanged), so defer it — releasing the player while its own dispatch is still on the
        // stack lets the released controller's loop push stale state into the new engine.
        if (tintVideo && !surfaceLocked && !(mVideoSurfaceWrapper instanceof TextureViewWrapper)) {
            mVideoSurfaceRoot.post(this::ensureTextureView);
        }

        // When the surface must stay a SurfaceView, tint with a translucent overlay instead:
        // works on any device and keeps tunneling/HDR passthrough intact.
        boolean useOverlay = tintVideo && surfaceLocked && !(mVideoSurfaceWrapper instanceof TextureViewWrapper);

        Paint paint = tintVideo && !useOverlay ? Utils.kelvinToPaint(tweaks.getNightlightWarmth()) : null;
        mVideoSurfaceRoot.setLayerType(paint != null ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE, paint);
        applyNightlightOverlay(useOverlay ? Utils.kelvinToOverlayColor(tweaks.getNightlightWarmth()) : 0);
    }

    private void applyNightlightOverlay(int color) {
        if (color == 0) {
            if (mNightlightOverlay != null) {
                mVideoSurfaceRoot.removeView(mNightlightOverlay);
                mNightlightOverlay = null;
            }
            return;
        }

        if (mNightlightOverlay == null) {
            mNightlightOverlay = new View(getContext());
            mVideoSurfaceRoot.addView(mNightlightOverlay,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        mNightlightOverlay.setBackgroundColor(color);
    }

    /**
     * Swap a plain SurfaceView for a TextureView (needed for view-level transforms/tints, since a
     * SurfaceView is a separate hardware layer). No-op if already a TextureView. Restarts the engine.
     */
    private void ensureTextureView() {
        if (mVideoSurfaceWrapper == null || mVideoSurfaceWrapper instanceof TextureViewWrapper || getView() == null) {
            return;
        }

        mVideoSurfaceRoot.removeView(mVideoSurfaceWrapper.getSurfaceView());
        mVideoSurfaceWrapper = new TextureViewWrapper(getContext(), (ViewGroup) getView());
        mVideoSurfaceRoot.addView(mVideoSurfaceWrapper.getSurfaceView(), 0);

        ((PlayerEngine) this).restartEngine();
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
        mVideoSurfaceRoot.setAspectRatio(calculateAspectRatio());
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

    /** Returns the {@link ResizeMode}. */
    protected @ResizeMode int getResize() {
        return mVideoSurfaceRoot.getResizeMode();
    }

    /**
     * Sets the {@link ResizeMode}.
     *
     * @param resizeMode The {@link ResizeMode}.
     */
    protected void setResize(@ResizeMode int resizeMode) {
        mVideoSurfaceRoot.setResizeMode(resizeMode);
    }

    protected void setZoom(int percents) {
        mVideoSurfaceRoot.setZoom(percents);
    }

    protected void setAspect(float aspectRatio) {
        mAspectRatio = aspectRatio;
        mVideoSurfaceRoot.setAspectRatio(calculateAspectRatio());
    }

    protected void setRotation(int angle) {
        if (Helpers.floatEquals(mVideoSurfaceRoot.getRotation(), angle) || mVideoSurfaceWrapper == null) {
            return;
        }

        ensureTextureView();
        mVideoSurfaceRoot.setRotation(angle);
    }

    protected void setFlipEnabled(boolean enabled) {
        float scaleX = enabled ? -1f : 1f;

        if (Helpers.floatEquals(mVideoSurfaceRoot.getScaleX(), scaleX) || mVideoSurfaceWrapper == null) {
            return;
        }

        ensureTextureView();
        mVideoSurfaceRoot.setScaleX(scaleX);
    }

    private void scaleIfNeeded() {
        if (!(mVideoSurfaceWrapper instanceof TextureViewWrapper)) {
            return;
        }

        if (mVideoSurfaceRoot.getWidth() == 0 || mVideoSurfaceRoot.getHeight() == 0) {
            return;
        }

        float angle = mVideoSurfaceRoot.getRotation();

        int width, height;

        if (Helpers.floatEquals(angle, 90) || Helpers.floatEquals(angle, 270)) {
            float ratio = mVideoSurfaceRoot.getWidth() / ((float) mVideoSurfaceRoot.getHeight());

            width = mVideoSurfaceRoot.getHeight();
            height = (int) (mVideoSurfaceRoot.getHeight() / ratio);
        } else {
            width = mVideoSurfaceRoot.getWidth();
            height = mVideoSurfaceRoot.getHeight();
        }

        // https://stackoverflow.com/questions/52196362/how-resize-textureview-to-fullscreen-when-rotation-90
        View textureView = mVideoSurfaceWrapper.getSurfaceView();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) textureView.getLayoutParams();
        params.width = width;
        params.height = height;
        params.gravity = Gravity.CENTER;
        textureView.setLayoutParams(params);
    }

    protected void setPixelRatio(float pixelRatio) {
        mPixelRatio = pixelRatio;
    }

    /**
     * Setup player's background used when controls are showed.
     * @param resId background
     */
    protected void setBackgroundResource(int resId) {
        if (resId <= 0 || mBackgroundResId == resId) {
            return;
        }

        View backgroundView = (View) Helpers.getField(this, "mBackgroundView");

        if (backgroundView != null) {
            backgroundView.setBackgroundResource(resId);
            mBackgroundResId = resId;
        }
    }

    protected void setGravity(int gravity) {
        ViewUtil.setGravity(mVideoSurfaceRoot, gravity);

        scaleSubtitles(gravity);
    }

    private void scaleSubtitles(int gravity) {
        if ((gravity & Gravity.START) == Gravity.START) {
            scaleSubtitles(scaleSubsWidth(), scaleSubsPadding(), Gravity.START);
        } else if ((gravity & Gravity.END) == Gravity.END) {
            scaleSubtitles(scaleSubsWidth(), scaleSubsPadding(), Gravity.END);
        } else if ((gravity & Gravity.CENTER) == Gravity.CENTER) {
            scaleSubtitles(ViewGroup.LayoutParams.MATCH_PARENT, mSubtitlesPadding, Gravity.CENTER);
        }
    }

    private void scaleSubtitles(int width, int padding, int gravity) {
        ViewUtil.setWidth(mLeanbackSubtitles, width);
        ViewUtil.setPadding(mLeanbackSubtitles, padding);
        ViewUtil.setGravity(mLeanbackSubtitles, gravity);
    }

    private int scaleSubsWidth() {
        View parent = (View) mLeanbackSubtitles.getParent();
        return parent.getWidth() / 100 * calculateZoom();
    }

    private int scaleSubsPadding() {
        return mSubtitlesPadding / 100 * calculateZoom();
    }

    private int calculateZoom() {
        View parent = (View) mLeanbackSubtitles.getParent();
        int widthRatio = mVideoSurfaceRoot.getWidth() * 100 / parent.getWidth();
        return mVideoSurfaceRoot.getZoom() * widthRatio / 100;
    }

    private float calculateAspectRatio() {
        return (mAspectRatio == 0 ? mVideoAspectRatio : mAspectRatio) * mPixelRatio;
    }
}
