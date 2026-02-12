/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.leanback.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

/**
 * A helper class for implementing a glue layer for {@link MediaControllerCompat}.
 * @deprecated Use {@link MediaControllerAdapter} with {@link PlaybackTransportControlGlue} or
 *             {@link PlaybackBannerControlGlue}.
 */
@Deprecated
public abstract class MediaControllerGlue extends PlaybackControlGlue {
    static final String TAG = "MediaControllerGlue";
    static final boolean DEBUG = false;

    MediaControllerCompat mMediaController;

    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            if (DEBUG) Log.v(TAG, "onMetadataChanged");
            MediaControllerGlue.this.onMetadataChanged();
        }
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            if (DEBUG) Log.v(TAG, "onPlaybackStateChanged");
            onStateChanged();
        }
        @Override
        public void onSessionDestroyed() {
            if (DEBUG) Log.v(TAG, "onSessionDestroyed");
            mMediaController = null;
        }
        @Override
        public void onSessionEvent(String event, Bundle extras) {
            if (DEBUG) Log.v(TAG, "onSessionEvent");
        }
    };

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param fastForwardSpeeds Array of seek speeds for fast forward.
     * @param rewindSpeeds Array of seek speeds for rewind.
     */
    public MediaControllerGlue(Context context,
                               int[] fastForwardSpeeds,
                               int[] rewindSpeeds) {
        super(context, fastForwardSpeeds, rewindSpeeds);
    }

    /**
     * Attaches to the given media controller.
     */
    public void attachToMediaController(MediaControllerCompat mediaController) {
        if (mediaController != mMediaController) {
            if (DEBUG) Log.v(TAG, "New media controller " + mediaController);
            detach();
            mMediaController = mediaController;
            if (mMediaController != null) {
                mMediaController.registerCallback(mCallback);
            }
            onMetadataChanged();
            onStateChanged();
        }
    }

    /**
     * Detaches from the media controller.  Must be called when the object is no longer
     * needed.
     */
    public void detach() {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mCallback);
        }
        mMediaController = null;
    }

    /**
     * Returns the media controller currently attached.
     */
    public final MediaControllerCompat getMediaController() {
        return mMediaController;
    }

    @Override
    public boolean hasValidMedia() {
        return mMediaController != null && mMediaController.getMetadata() != null;
    }

    @Override
    public boolean isMediaPlaying() {
        return mMediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING;
    }

    @Override
    public int getCurrentSpeedId() {
        int speed = (int) mMediaController.getPlaybackState().getPlaybackSpeed();
        if (speed == 0) {
            return PLAYBACK_SPEED_PAUSED;
        } else if (speed == 1) {
            return PLAYBACK_SPEED_NORMAL;
        } else if (speed > 0) {
            int[] seekSpeeds = getFastForwardSpeeds();
            for (int index = 0; index < seekSpeeds.length; index++) {
                if (speed == seekSpeeds[index]) {
                    return PLAYBACK_SPEED_FAST_L0 + index;
                }
            }
        } else {
            int[] seekSpeeds = getRewindSpeeds();
            for (int index = 0; index < seekSpeeds.length; index++) {
                if (-speed == seekSpeeds[index]) {
                    return -PLAYBACK_SPEED_FAST_L0 - index;
                }
            }
        }
        Log.w(TAG, "Couldn't find index for speed " + speed);
        return PLAYBACK_SPEED_INVALID;
    }

    @Override
    public CharSequence getMediaTitle() {
        return mMediaController.getMetadata().getDescription().getTitle();
    }

    @Override
    public CharSequence getMediaSubtitle() {
        return mMediaController.getMetadata().getDescription().getSubtitle();
    }

    @Override
    public int getMediaDuration() {
        return (int) mMediaController.getMetadata().getLong(
                MediaMetadataCompat.METADATA_KEY_DURATION);
    }

    @Override
    public int getCurrentPosition() {
        return (int) mMediaController.getPlaybackState().getPosition();
    }

    @Override
    public Drawable getMediaArt() {
        Bitmap bitmap = mMediaController.getMetadata().getDescription().getIconBitmap();
        return bitmap == null ? null : new BitmapDrawable(getContext().getResources(), bitmap);
    }

    @Override
    public long getSupportedActions() {
        long result = 0;
        long actions = mMediaController.getPlaybackState().getActions();
        if ((actions & PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0) {
            result |= ACTION_PLAY_PAUSE;
        }
        if ((actions & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            result |= ACTION_SKIP_TO_NEXT;
        }
        if ((actions & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            result |= ACTION_SKIP_TO_PREVIOUS;
        }
        if ((actions & PlaybackStateCompat.ACTION_FAST_FORWARD) != 0) {
            result |= ACTION_FAST_FORWARD;
        }
        if ((actions & PlaybackStateCompat.ACTION_REWIND) != 0) {
            result |= ACTION_REWIND;
        }
        return result;
    }

    @Override
    public void play(int speed) {
        if (DEBUG) Log.v(TAG, "startPlayback speed " + speed);
        if (speed == PLAYBACK_SPEED_NORMAL) {
            mMediaController.getTransportControls().play();
        } else if (speed > 0) {
            mMediaController.getTransportControls().fastForward();
        } else {
            mMediaController.getTransportControls().rewind();
        }
    }

    @Override
    public void pause() {
        if (DEBUG) Log.v(TAG, "pausePlayback");
        mMediaController.getTransportControls().pause();
    }

    @Override
    public void next() {
        if (DEBUG) Log.v(TAG, "skipToNext");
        mMediaController.getTransportControls().skipToNext();
    }

    @Override
    public void previous() {
        if (DEBUG) Log.v(TAG, "skipToPrevious");
        mMediaController.getTransportControls().skipToPrevious();
    }
}
