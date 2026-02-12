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

package androidx.leanback.media;

import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_FAST_FORWARD;
import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_PLAY_PAUSE;
import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_REPEAT;
import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_REWIND;
import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_SHUFFLE;
import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_SKIP_TO_NEXT;
import static androidx.leanback.media.PlaybackBaseControlGlue.ACTION_SKIP_TO_PREVIOUS;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.leanback.widget.PlaybackControlsRow;

/**
 * A helper class for implementing a adapter layer for {@link MediaControllerCompat}.
 */
public class MediaControllerAdapter extends PlayerAdapter {

    private static final String TAG = "MediaControllerAdapter";
    private static final boolean DEBUG = false;

    private MediaControllerCompat mController;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    Handler mHandler = new Handler();

    // Runnable object to update current media's playing position.
    private final Runnable mPositionUpdaterRunnable = new Runnable() {
        @Override
        public void run() {
            getCallback().onCurrentPositionChanged(MediaControllerAdapter.this);
            mHandler.postDelayed(this, getUpdatePeriod());
        }
    };

    // Update period to post runnable.
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    int getUpdatePeriod() {
        return 16;
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean mIsBuffering = false;

    MediaControllerCompat.Callback mMediaControllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    if (mIsBuffering && state.getState() != PlaybackStateCompat.STATE_BUFFERING) {
                        getCallback().onBufferingStateChanged(MediaControllerAdapter.this, false);
                        getCallback().onBufferedPositionChanged(MediaControllerAdapter.this);
                        mIsBuffering = false;
                    }
                    if (state.getState() == PlaybackStateCompat.STATE_NONE) {
                        // The STATE_NONE playback state will only occurs when initialize the player
                        // at first time.
                        if (DEBUG) {
                            Log.d(TAG, "Playback state is none");
                        }
                    } else if (state.getState() == PlaybackStateCompat.STATE_STOPPED) {
                        // STATE_STOPPED is associated with onPlayCompleted() callback.
                        // STATE_STOPPED playback state will only occurs when the last item in
                        // play list is finished. And repeat mode is not enabled.
                        getCallback().onPlayCompleted(MediaControllerAdapter.this);
                    } else if (state.getState() == PlaybackStateCompat.STATE_PAUSED) {
                        getCallback().onPlayStateChanged(MediaControllerAdapter.this);
                        getCallback().onCurrentPositionChanged(MediaControllerAdapter.this);
                    } else if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                        getCallback().onPlayStateChanged(MediaControllerAdapter.this);
                        getCallback().onCurrentPositionChanged(MediaControllerAdapter.this);
                    } else if (state.getState() == PlaybackStateCompat.STATE_BUFFERING) {
                        mIsBuffering = true;
                        getCallback().onBufferingStateChanged(MediaControllerAdapter.this, true);
                        getCallback().onBufferedPositionChanged(MediaControllerAdapter.this);
                    } else if (state.getState() == PlaybackStateCompat.STATE_ERROR) {
                        CharSequence errorMessage = state.getErrorMessage();
                        if (errorMessage == null) {
                            getCallback().onError(MediaControllerAdapter.this, state.getErrorCode(),
                                    "");
                        } else {
                            getCallback().onError(MediaControllerAdapter.this, state.getErrorCode(),
                                    state.getErrorMessage().toString());
                        }
                    } else if (state.getState() == PlaybackStateCompat.STATE_FAST_FORWARDING) {
                        getCallback().onPlayStateChanged(MediaControllerAdapter.this);
                        getCallback().onCurrentPositionChanged(MediaControllerAdapter.this);
                    } else if (state.getState() == PlaybackStateCompat.STATE_REWINDING) {
                        getCallback().onPlayStateChanged(MediaControllerAdapter.this);
                        getCallback().onCurrentPositionChanged(MediaControllerAdapter.this);
                    }
                }

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    getCallback().onMetadataChanged(MediaControllerAdapter.this);
                }
            };

    /**
     * Constructor for the adapter using {@link MediaControllerCompat}.
     *
     * @param controller Object of MediaControllerCompat..
     */
    public MediaControllerAdapter(MediaControllerCompat controller) {
        if (controller == null) {
            throw new NullPointerException("Object of MediaControllerCompat is null");
        }
        mController = controller;
    }

    /**
     * Return the object of {@link MediaControllerCompat} from this class.
     *
     * @return Media Controller Compat object owned by this class.
     */
    public MediaControllerCompat getMediaController() {
        return mController;
    }

    @Override
    public void play() {
        mController.getTransportControls().play();
    }

    @Override
    public void pause() {
        mController.getTransportControls().pause();
    }

    @Override
    public void seekTo(long positionInMs) {
        mController.getTransportControls().seekTo(positionInMs);
    }

    @Override
    public void next() {
        mController.getTransportControls().skipToNext();
    }

    @Override
    public void previous() {
        mController.getTransportControls().skipToPrevious();
    }

    @Override
    public void fastForward() {
        mController.getTransportControls().fastForward();
    }

    @Override
    public void rewind() {
        mController.getTransportControls().rewind();
    }

    @Override
    public void setRepeatAction(int repeatActionIndex) {
        int repeatMode = mapRepeatActionToRepeatMode(repeatActionIndex);
        mController.getTransportControls().setRepeatMode(repeatMode);
    }

    @Override
    public void setShuffleAction(int shuffleActionIndex) {
        int shuffleMode = mapShuffleActionToShuffleMode(shuffleActionIndex);
        mController.getTransportControls().setShuffleMode(shuffleMode);
    }

    @Override
    public boolean isPlaying() {
        if (mController.getPlaybackState() == null) {
            return false;
        }
        return mController.getPlaybackState().getState()
                == PlaybackStateCompat.STATE_PLAYING
                || mController.getPlaybackState().getState()
                == PlaybackStateCompat.STATE_FAST_FORWARDING
                || mController.getPlaybackState().getState() == PlaybackStateCompat.STATE_REWINDING;
    }

    @Override
    public long getCurrentPosition() {
        if (mController.getPlaybackState() == null) {
            return 0;
        }
        return mController.getPlaybackState().getPosition();
    }

    @Override
    public long getBufferedPosition() {
        if (mController.getPlaybackState() == null) {
            return 0;
        }
        return mController.getPlaybackState().getBufferedPosition();
    }

    /**
     * Get current media's title.
     *
     * @return Title of current media.
     */
    public CharSequence getMediaTitle() {
        if (mController.getMetadata() == null) {
            return "";
        }
        return mController.getMetadata().getDescription().getTitle();
    }

    /**
     * Get current media's subtitle.
     *
     * @return Subtitle of current media.
     */
    public CharSequence getMediaSubtitle() {
        if (mController.getMetadata() == null) {
            return "";
        }
        return mController.getMetadata().getDescription().getSubtitle();
    }

    /**
     * Get current media's drawable art.
     *
     * @return Drawable art of current media.
     */
    public Drawable getMediaArt(Context context) {
        if (mController.getMetadata() == null) {
            return null;
        }
        Bitmap bitmap = mController.getMetadata().getDescription().getIconBitmap();
        return bitmap == null ? null : new BitmapDrawable(context.getResources(), bitmap);
    }

    @Override
    public long getDuration() {
        if (mController.getMetadata() == null) {
            return 0;
        }
        return (int) mController.getMetadata().getLong(
                MediaMetadataCompat.METADATA_KEY_DURATION);
    }

    @Override
    public void onAttachedToHost(PlaybackGlueHost host) {
        mController.registerCallback(mMediaControllerCallback);
    }

    @Override
    public void onDetachedFromHost() {
        mController.unregisterCallback(mMediaControllerCallback);
    }

    @Override
    public void setProgressUpdatingEnabled(boolean enabled) {
        mHandler.removeCallbacks(mPositionUpdaterRunnable);
        if (!enabled) {
            return;
        }
        mHandler.postDelayed(mPositionUpdaterRunnable, getUpdatePeriod());
    }

    @Override
    public long getSupportedActions() {
        long supportedActions = 0;
        if (mController.getPlaybackState() == null) {
            return supportedActions;
        }
        long actionsFromController = mController.getPlaybackState().getActions();
        // Translation.
        if ((actionsFromController & PlaybackStateCompat.ACTION_PLAY_PAUSE) != 0) {
            supportedActions |= ACTION_PLAY_PAUSE;
        }
        if ((actionsFromController & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0) {
            supportedActions |= ACTION_SKIP_TO_NEXT;
        }
        if ((actionsFromController & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0) {
            supportedActions |= ACTION_SKIP_TO_PREVIOUS;
        }
        if ((actionsFromController & PlaybackStateCompat.ACTION_FAST_FORWARD) != 0) {
            supportedActions |= ACTION_FAST_FORWARD;
        }
        if ((actionsFromController & PlaybackStateCompat.ACTION_REWIND) != 0) {
            supportedActions |= ACTION_REWIND;
        }
        if ((actionsFromController & PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0) {
            supportedActions |= ACTION_REPEAT;
        }
        if ((actionsFromController & PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0) {
            supportedActions |= ACTION_SHUFFLE;
        }
        return supportedActions;
    }

    /**
     * This function will translate the index of RepeatAction in PlaybackControlsRow to
     * the repeat mode which is defined by PlaybackStateCompat.
     *
     * @param repeatActionIndex Index of RepeatAction in PlaybackControlsRow.
     * @return Repeat Mode in playback state.
     */
    private int mapRepeatActionToRepeatMode(int repeatActionIndex) {
        switch (repeatActionIndex) {
            case PlaybackControlsRow.RepeatAction.INDEX_NONE:
                return PlaybackStateCompat.REPEAT_MODE_NONE;
            case PlaybackControlsRow.RepeatAction.INDEX_ALL:
                return PlaybackStateCompat.REPEAT_MODE_ALL;
            case PlaybackControlsRow.RepeatAction.INDEX_ONE:
                return PlaybackStateCompat.REPEAT_MODE_ONE;
        }
        return -1;
    }

    /**
     * This function will translate the index of RepeatAction in PlaybackControlsRow to
     * the repeat mode which is defined by PlaybackStateCompat.
     *
     * @param shuffleActionIndex Index of RepeatAction in PlaybackControlsRow.
     * @return Repeat Mode in playback state.
     */
    private int mapShuffleActionToShuffleMode(int shuffleActionIndex) {
        switch (shuffleActionIndex) {
            case PlaybackControlsRow.ShuffleAction.INDEX_OFF:
                return PlaybackStateCompat.SHUFFLE_MODE_NONE;
            case PlaybackControlsRow.ShuffleAction.INDEX_ON:
                return PlaybackStateCompat.SHUFFLE_MODE_ALL;
        }
        return -1;
    }
}

