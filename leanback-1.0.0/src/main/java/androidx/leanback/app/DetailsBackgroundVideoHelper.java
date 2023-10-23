/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.leanback.app;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;

import androidx.leanback.media.PlaybackGlue;
import androidx.leanback.widget.DetailsParallax;
import androidx.leanback.widget.Parallax;
import androidx.leanback.widget.ParallaxEffect;
import androidx.leanback.widget.ParallaxTarget;

/**
 * Helper class responsible for controlling video playback in {@link DetailsFragment}. This
 * takes {@link DetailsParallax}, {@link PlaybackGlue} and a drawable as input.
 * Video is played when {@link DetailsParallax#getOverviewRowTop()} moved bellow top edge of screen.
 * Video is stopped when {@link DetailsParallax#getOverviewRowTop()} reaches or scrolls above top
 * edge of screen. The drawable will change alpha to 0 when video is ready to play.
 * App does not directly use this class.
 * @see DetailsFragmentBackgroundController
 * @see DetailsSupportFragmentBackgroundController
 */
final class DetailsBackgroundVideoHelper {
    private static final long BACKGROUND_CROSS_FADE_DURATION = 500;
    // Temporarily add CROSSFADE_DELAY waiting for video surface ready.
    // We will remove this delay once PlaybackGlue have a callback for videoRenderingReady event.
    private static final long CROSSFADE_DELAY = 1000;

    /**
     * Different states {@link DetailsFragment} can be in.
     */
    static final int INITIAL = 0;
    static final int PLAY_VIDEO = 1;
    static final int NO_VIDEO = 2;

    private final DetailsParallax mDetailsParallax;
    private ParallaxEffect mParallaxEffect;

    private int mCurrentState = INITIAL;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    ValueAnimator mBackgroundAnimator;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    Drawable mBackgroundDrawable;
    private PlaybackGlue mPlaybackGlue;
    private boolean mBackgroundDrawableVisible;

    /**
     * Constructor to setup a Helper for controlling video playback in DetailsFragment.
     * @param playbackGlue The PlaybackGlue used to control underlying player.
     * @param detailsParallax The DetailsParallax to add special parallax effect to control video
     *                        start/stop. Video is played when
     *                        {@link DetailsParallax#getOverviewRowTop()} moved bellow top edge of
     *                        screen. Video is stopped when
     *                        {@link DetailsParallax#getOverviewRowTop()} reaches or scrolls above
     *                        top edge of screen.
     * @param backgroundDrawable The drawable will change alpha to 0 when video is ready to play.
     */
    DetailsBackgroundVideoHelper(
            PlaybackGlue playbackGlue,
            DetailsParallax detailsParallax,
            Drawable backgroundDrawable) {
        this.mPlaybackGlue = playbackGlue;
        this.mDetailsParallax = detailsParallax;
        this.mBackgroundDrawable = backgroundDrawable;
        mBackgroundDrawableVisible = true;
        mBackgroundDrawable.setAlpha(255);
        startParallax();
    }

    void startParallax() {
        if (mParallaxEffect != null) {
            return;
        }
        Parallax.IntProperty frameTop = mDetailsParallax.getOverviewRowTop();
        final float maxFrameTop = 1f;
        final float minFrameTop = 0f;
        mParallaxEffect = mDetailsParallax
                .addEffect(frameTop.atFraction(maxFrameTop), frameTop.atFraction(minFrameTop))
                .target(new ParallaxTarget() {
                    @Override
                    public void update(float fraction) {
                        if (fraction == maxFrameTop) {
                            updateState(NO_VIDEO);
                        } else {
                            updateState(PLAY_VIDEO);
                        }
                    }
                });
        // In case the VideoHelper is created after RecyclerView is created: perform initial
        // parallax effect.
        mDetailsParallax.updateValues();
    }

    void stopParallax() {
        mDetailsParallax.removeEffect(mParallaxEffect);
    }

    boolean isVideoVisible() {
        return mCurrentState == PLAY_VIDEO;
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void updateState(int state) {
        if (state == mCurrentState) {
            return;
        }
        mCurrentState = state;
        applyState();
    }

    private void applyState() {
        switch (mCurrentState) {
            case PLAY_VIDEO:
                if (mPlaybackGlue != null) {
                    if (mPlaybackGlue.isPrepared()) {
                        internalStartPlayback();
                    } else {
                        mPlaybackGlue.addPlayerCallback(mControlStateCallback);
                    }
                } else {
                    crossFadeBackgroundToVideo(false);
                }
                break;
            case NO_VIDEO:
                crossFadeBackgroundToVideo(false);
                if (mPlaybackGlue != null) {
                    mPlaybackGlue.removePlayerCallback(mControlStateCallback);
                    mPlaybackGlue.pause();
                }
                break;
        }
    }

    void setPlaybackGlue(PlaybackGlue playbackGlue) {
        if (mPlaybackGlue != null) {
            mPlaybackGlue.removePlayerCallback(mControlStateCallback);
        }
        mPlaybackGlue = playbackGlue;
        applyState();
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void internalStartPlayback() {
        if (mPlaybackGlue != null) {
            mPlaybackGlue.play();
        }
        mDetailsParallax.getRecyclerView().postDelayed(new Runnable() {
            @Override
            public void run() {
                crossFadeBackgroundToVideo(true);
            }
        }, CROSSFADE_DELAY);
    }

    void crossFadeBackgroundToVideo(boolean crossFadeToVideo) {
        crossFadeBackgroundToVideo(crossFadeToVideo, false);
    }

    void crossFadeBackgroundToVideo(boolean crossFadeToVideo, boolean immediate) {
        final boolean newVisible = !crossFadeToVideo;
        if (mBackgroundDrawableVisible == newVisible) {
            if (immediate) {
                if (mBackgroundAnimator != null) {
                    mBackgroundAnimator.cancel();
                    mBackgroundAnimator = null;
                }
                if (mBackgroundDrawable != null) {
                    mBackgroundDrawable.setAlpha(crossFadeToVideo ? 0 : 255);
                    return;
                }
            }
            return;
        }
        mBackgroundDrawableVisible = newVisible;
        if (mBackgroundAnimator != null) {
            mBackgroundAnimator.cancel();
            mBackgroundAnimator = null;
        }

        float startAlpha = crossFadeToVideo ? 1f : 0f;
        float endAlpha = crossFadeToVideo ? 0f : 1f;

        if (mBackgroundDrawable == null) {
            return;
        }
        if (immediate) {
            mBackgroundDrawable.setAlpha(crossFadeToVideo ? 0 : 255);
            return;
        }
        mBackgroundAnimator = ValueAnimator.ofFloat(startAlpha, endAlpha);
        mBackgroundAnimator.setDuration(BACKGROUND_CROSS_FADE_DURATION);
        mBackgroundAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mBackgroundDrawable.setAlpha(
                        (int) ((Float) (valueAnimator.getAnimatedValue()) * 255));
            }
        });

        mBackgroundAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mBackgroundAnimator = null;
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });

        mBackgroundAnimator.start();
    }

    private class PlaybackControlStateCallback extends PlaybackGlue.PlayerCallback {
        PlaybackControlStateCallback() {
        }

        @Override
        public void onPreparedStateChanged(PlaybackGlue glue) {
            if (glue.isPrepared()) {
                internalStartPlayback();
            }
        }
    }

    PlaybackControlStateCallback mControlStateCallback = new PlaybackControlStateCallback();
}
