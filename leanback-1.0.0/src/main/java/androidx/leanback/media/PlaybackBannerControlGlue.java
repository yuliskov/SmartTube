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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackControlsRowPresenter;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.RowPresenter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A helper class for managing a {@link PlaybackControlsRow} being displayed in
 * {@link PlaybackGlueHost}. It supports standard playback control actions play/pause and
 * skip next/previous. This helper class is a glue layer that manages interaction between the
 * leanback UI components {@link PlaybackControlsRow} {@link PlaybackControlsRowPresenter}
 * and a functional {@link PlayerAdapter} which represents the underlying
 * media player.
 *
 * <p>Apps must pass a {@link PlayerAdapter} in the constructor for a specific
 * implementation e.g. a {@link MediaPlayerAdapter}.
 * </p>
 *
 * <p>The glue has two action bars: primary action bars and secondary action bars. Apps
 * can provide additional actions by overriding {@link #onCreatePrimaryActions} and / or
 * {@link #onCreateSecondaryActions} and respond to actions by overriding
 * {@link #onActionClicked(Action)}.
 * </p>
 *
 * <p>The subclass is responsible for implementing the "repeat mode" in
 * {@link #onPlayCompleted()}.
 * </p>
 *
 * Sample Code:
 * <pre><code>
 * public class MyVideoFragment extends VideoFragment {
 *     &#64;Override
 *     public void onCreate(Bundle savedInstanceState) {
 *         super.onCreate(savedInstanceState);
 *         PlaybackBannerControlGlue<MediaPlayerAdapter> playerGlue =
 *                 new PlaybackBannerControlGlue(getActivity(),
 *                         new MediaPlayerAdapter(getActivity()));
 *         playerGlue.setHost(new VideoFragmentGlueHost(this));
 *         playerGlue.setSubtitle("Leanback artist");
 *         playerGlue.setTitle("Leanback team at work");
 *         String uriPath = "android.resource://com.example.android.leanback/raw/video";
 *         playerGlue.getPlayerAdapter().setDataSource(Uri.parse(uriPath));
 *         playerGlue.playWhenPrepared();
 *     }
 * }
 * </code></pre>
 * @param <T> Type of {@link PlayerAdapter} passed in constructor.
 */
public class PlaybackBannerControlGlue<T extends PlayerAdapter>
        extends PlaybackBaseControlGlue<T> {

    /** @hide */
    @IntDef(
            flag = true,
            value = {
            ACTION_CUSTOM_LEFT_FIRST,
            ACTION_SKIP_TO_PREVIOUS,
            ACTION_REWIND,
            ACTION_PLAY_PAUSE,
            ACTION_FAST_FORWARD,
            ACTION_SKIP_TO_NEXT,
            ACTION_CUSTOM_RIGHT_FIRST
    })
    @RestrictTo(LIBRARY_GROUP)
    @Retention(RetentionPolicy.SOURCE)
    public @interface ACTION_ {}

    /**
     * The adapter key for the first custom control on the left side
     * of the predefined primary controls.
     */
    public static final int ACTION_CUSTOM_LEFT_FIRST =
            PlaybackBaseControlGlue.ACTION_CUSTOM_LEFT_FIRST;

    /**
     * The adapter key for the skip to previous control.
     */
    public static final int ACTION_SKIP_TO_PREVIOUS =
            PlaybackBaseControlGlue.ACTION_SKIP_TO_PREVIOUS;

    /**
     * The adapter key for the rewind control.
     */
    public static final int ACTION_REWIND = PlaybackBaseControlGlue.ACTION_REWIND;

    /**
     * The adapter key for the play/pause control.
     */
    public static final int ACTION_PLAY_PAUSE = PlaybackBaseControlGlue.ACTION_PLAY_PAUSE;

    /**
     * The adapter key for the fast forward control.
     */
    public static final int ACTION_FAST_FORWARD = PlaybackBaseControlGlue.ACTION_FAST_FORWARD;

    /**
     * The adapter key for the skip to next control.
     */
    public static final int ACTION_SKIP_TO_NEXT = PlaybackBaseControlGlue.ACTION_SKIP_TO_NEXT;

    /**
     * The adapter key for the first custom control on the right side
     * of the predefined primary controls.
     */
    public static final int ACTION_CUSTOM_RIGHT_FIRST =
            PlaybackBaseControlGlue.ACTION_CUSTOM_RIGHT_FIRST;


    /** @hide */
    @IntDef({
                    PLAYBACK_SPEED_INVALID,
                    PLAYBACK_SPEED_PAUSED,
                    PLAYBACK_SPEED_NORMAL,
                    PLAYBACK_SPEED_FAST_L0,
                    PLAYBACK_SPEED_FAST_L1,
                    PLAYBACK_SPEED_FAST_L2,
                    PLAYBACK_SPEED_FAST_L3,
                    PLAYBACK_SPEED_FAST_L4
    })
    @RestrictTo(LIBRARY_GROUP)
    @Retention(RetentionPolicy.SOURCE)
    private @interface SPEED {}

    /**
     * Invalid playback speed.
     */
    public static final int PLAYBACK_SPEED_INVALID = -1;

    /**
     * Speed representing playback state that is paused.
     */
    public static final int PLAYBACK_SPEED_PAUSED = 0;

    /**
     * Speed representing playback state that is playing normally.
     */
    public static final int PLAYBACK_SPEED_NORMAL = 1;

    /**
     * The initial (level 0) fast forward playback speed.
     * The negative of this value is for rewind at the same speed.
     */
    public static final int PLAYBACK_SPEED_FAST_L0 = 10;

    /**
     * The level 1 fast forward playback speed.
     * The negative of this value is for rewind at the same speed.
     */
    public static final int PLAYBACK_SPEED_FAST_L1 = 11;

    /**
     * The level 2 fast forward playback speed.
     * The negative of this value is for rewind at the same speed.
     */
    public static final int PLAYBACK_SPEED_FAST_L2 = 12;

    /**
     * The level 3 fast forward playback speed.
     * The negative of this value is for rewind at the same speed.
     */
    public static final int PLAYBACK_SPEED_FAST_L3 = 13;

    /**
     * The level 4 fast forward playback speed.
     * The negative of this value is for rewind at the same speed.
     */
    public static final int PLAYBACK_SPEED_FAST_L4 = 14;

    private static final String TAG = PlaybackBannerControlGlue.class.getSimpleName();
    private static final int NUMBER_OF_SEEK_SPEEDS = PLAYBACK_SPEED_FAST_L4
            - PLAYBACK_SPEED_FAST_L0 + 1;

    private final int[] mFastForwardSpeeds;
    private final int[] mRewindSpeeds;
    private PlaybackControlsRow.SkipNextAction mSkipNextAction;
    private PlaybackControlsRow.SkipPreviousAction mSkipPreviousAction;
    private PlaybackControlsRow.FastForwardAction mFastForwardAction;
    private PlaybackControlsRow.RewindAction mRewindAction;

    @SPEED
    private int mPlaybackSpeed = PLAYBACK_SPEED_PAUSED;
    private long mStartTime;
    private long mStartPosition = 0;

    // Flag for is customized FastForward/ Rewind Action supported.
    // If customized actions are not supported, the adapter can still use default behavior through
    // setting ACTION_REWIND and ACTION_FAST_FORWARD as supported actions.
    private boolean mIsCustomizedFastForwardSupported;
    private boolean mIsCustomizedRewindSupported;

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param seekSpeeds The array of seek speeds for fast forward and rewind. The maximum length of
     *                   the array is defined as NUMBER_OF_SEEK_SPEEDS.
     * @param impl Implementation to underlying media player.
     */
    public PlaybackBannerControlGlue(Context context,
            int[] seekSpeeds,
            T impl) {
        this(context, seekSpeeds, seekSpeeds, impl);
    }

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param fastForwardSpeeds The array of seek speeds for fast forward. The maximum length of
     *                   the array is defined as NUMBER_OF_SEEK_SPEEDS.
     * @param rewindSpeeds The array of seek speeds for rewind. The maximum length of
     *                   the array is defined as NUMBER_OF_SEEK_SPEEDS.
     * @param impl Implementation to underlying media player.
     */
    public PlaybackBannerControlGlue(Context context,
                                    int[] fastForwardSpeeds,
                                    int[] rewindSpeeds,
                                    T impl) {
        super(context, impl);

        if (fastForwardSpeeds.length == 0 || fastForwardSpeeds.length > NUMBER_OF_SEEK_SPEEDS) {
            throw new IllegalArgumentException("invalid fastForwardSpeeds array size");
        }
        mFastForwardSpeeds = fastForwardSpeeds;

        if (rewindSpeeds.length == 0 || rewindSpeeds.length > NUMBER_OF_SEEK_SPEEDS) {
            throw new IllegalArgumentException("invalid rewindSpeeds array size");
        }
        mRewindSpeeds = rewindSpeeds;
        if ((mPlayerAdapter.getSupportedActions() & ACTION_FAST_FORWARD) != 0) {
            mIsCustomizedFastForwardSupported = true;
        }
        if ((mPlayerAdapter.getSupportedActions() & ACTION_REWIND) != 0) {
            mIsCustomizedRewindSupported = true;
        }
    }

    @Override
    public void setControlsRow(PlaybackControlsRow controlsRow) {
        super.setControlsRow(controlsRow);
        onUpdatePlaybackState();
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter primaryActionsAdapter) {
        final long supportedActions = getSupportedActions();
        if ((supportedActions & ACTION_SKIP_TO_PREVIOUS) != 0 && mSkipPreviousAction == null) {
            primaryActionsAdapter.add(mSkipPreviousAction =
                    new PlaybackControlsRow.SkipPreviousAction(getContext()));
        } else if ((supportedActions & ACTION_SKIP_TO_PREVIOUS) == 0
                && mSkipPreviousAction != null) {
            primaryActionsAdapter.remove(mSkipPreviousAction);
            mSkipPreviousAction = null;
        }
        if ((supportedActions & ACTION_REWIND) != 0 && mRewindAction == null) {
            primaryActionsAdapter.add(mRewindAction =
                    new PlaybackControlsRow.RewindAction(getContext(), mRewindSpeeds.length));
        } else if ((supportedActions & ACTION_REWIND) == 0 && mRewindAction != null) {
            primaryActionsAdapter.remove(mRewindAction);
            mRewindAction = null;
        }
        if ((supportedActions & ACTION_PLAY_PAUSE) != 0 && mPlayPauseAction == null) {
            mPlayPauseAction = new PlaybackControlsRow.PlayPauseAction(getContext());
            primaryActionsAdapter.add(mPlayPauseAction =
                    new PlaybackControlsRow.PlayPauseAction(getContext()));
        } else if ((supportedActions & ACTION_PLAY_PAUSE) == 0 && mPlayPauseAction != null) {
            primaryActionsAdapter.remove(mPlayPauseAction);
            mPlayPauseAction = null;
        }
        if ((supportedActions & ACTION_FAST_FORWARD) != 0 && mFastForwardAction == null) {
            mFastForwardAction = new PlaybackControlsRow.FastForwardAction(getContext(),
                    mFastForwardSpeeds.length);
            primaryActionsAdapter.add(mFastForwardAction =
                    new PlaybackControlsRow.FastForwardAction(getContext(),
                            mFastForwardSpeeds.length));
        } else if ((supportedActions & ACTION_FAST_FORWARD) == 0 && mFastForwardAction != null) {
            primaryActionsAdapter.remove(mFastForwardAction);
            mFastForwardAction = null;
        }
        if ((supportedActions & ACTION_SKIP_TO_NEXT) != 0 && mSkipNextAction == null) {
            primaryActionsAdapter.add(mSkipNextAction =
                    new PlaybackControlsRow.SkipNextAction(getContext()));
        } else if ((supportedActions & ACTION_SKIP_TO_NEXT) == 0 && mSkipNextAction != null) {
            primaryActionsAdapter.remove(mSkipNextAction);
            mSkipNextAction = null;
        }
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        final AbstractDetailsDescriptionPresenter detailsPresenter =
                new AbstractDetailsDescriptionPresenter() {
                    @Override
                    protected void onBindDescription(ViewHolder
                            viewHolder, Object object) {
                        PlaybackBannerControlGlue glue = (PlaybackBannerControlGlue) object;
                        viewHolder.getTitle().setText(glue.getTitle());
                        viewHolder.getSubtitle().setText(glue.getSubtitle());
                    }
                };

        PlaybackControlsRowPresenter rowPresenter =
                new PlaybackControlsRowPresenter(detailsPresenter) {
            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(PlaybackBannerControlGlue.this);
            }
            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }
        };

        return rowPresenter;
    }

    /**
     * Handles action clicks.  A subclass may override this add support for additional actions.
     */
    @Override
    public void onActionClicked(Action action) {
        dispatchAction(action, null);
    }

    /**
     * Handles key events and returns true if handled.  A subclass may override this to provide
     * additional support.
     */
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                boolean abortSeek = mPlaybackSpeed >= PLAYBACK_SPEED_FAST_L0
                        || mPlaybackSpeed <= -PLAYBACK_SPEED_FAST_L0;
                if (abortSeek) {
                    play();
                    onUpdatePlaybackStatusAfterUserAction();
                    return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE;
                }
                return false;
        }

        final ObjectAdapter primaryActionsAdapter = mControlsRow.getPrimaryActionsAdapter();
        Action action = mControlsRow.getActionForKeyCode(primaryActionsAdapter, keyCode);
        if (action == null) {
            action = mControlsRow.getActionForKeyCode(mControlsRow.getSecondaryActionsAdapter(),
                    keyCode);
        }

        if (action != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                dispatchAction(action, event);
            }
            return true;
        }
        return false;
    }

    void onUpdatePlaybackStatusAfterUserAction() {
        updatePlaybackState(mIsPlaying);
    }

    // Helper function to increment mPlaybackSpeed when necessary. The mPlaybackSpeed will control
    // the UI of fast forward button in control row.
    private void incrementFastForwardPlaybackSpeed() {
        switch (mPlaybackSpeed) {
            case PLAYBACK_SPEED_FAST_L0:
            case PLAYBACK_SPEED_FAST_L1:
            case PLAYBACK_SPEED_FAST_L2:
            case PLAYBACK_SPEED_FAST_L3:
                mPlaybackSpeed++;
                break;
            default:
                mPlaybackSpeed = PLAYBACK_SPEED_FAST_L0;
                break;
        }
    }

    // Helper function to decrement mPlaybackSpeed when necessary. The mPlaybackSpeed will control
    // the UI of rewind button in control row.
    private void decrementRewindPlaybackSpeed() {
        switch (mPlaybackSpeed) {
            case -PLAYBACK_SPEED_FAST_L0:
            case -PLAYBACK_SPEED_FAST_L1:
            case -PLAYBACK_SPEED_FAST_L2:
            case -PLAYBACK_SPEED_FAST_L3:
                mPlaybackSpeed--;
                break;
            default:
                mPlaybackSpeed = -PLAYBACK_SPEED_FAST_L0;
                break;
        }
    }

    /**
     * Called when the given action is invoked, either by click or key event.
     */
    boolean dispatchAction(Action action, KeyEvent keyEvent) {
        boolean handled = false;
        if (action == mPlayPauseAction) {
            boolean canPlay = keyEvent == null
                    || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY;
            boolean canPause = keyEvent == null
                    || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    || keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE;
            //            PLAY_PAUSE    PLAY      PAUSE
            // playing    paused                  paused
            // paused     playing       playing
            // ff/rw      playing       playing   paused
            if (canPause
                    && (canPlay ? mPlaybackSpeed == PLAYBACK_SPEED_NORMAL :
                    mPlaybackSpeed != PLAYBACK_SPEED_PAUSED)) {
                pause();
            } else if (canPlay && mPlaybackSpeed != PLAYBACK_SPEED_NORMAL) {
                play();
            }
            onUpdatePlaybackStatusAfterUserAction();
            handled = true;
        } else if (action == mSkipNextAction) {
            next();
            handled = true;
        } else if (action == mSkipPreviousAction) {
            previous();
            handled = true;
        } else if (action == mFastForwardAction) {
            if (mPlayerAdapter.isPrepared() && mPlaybackSpeed < getMaxForwardSpeedId()) {
                // When the customized fast forward action is available, it will be executed
                // when fast forward button is pressed. If current media item is not playing, the UI
                // will be updated to PLAYING status.
                if (mIsCustomizedFastForwardSupported) {
                    // Change UI to Playing status.
                    mIsPlaying = true;
                    // Execute customized fast forward action.
                    mPlayerAdapter.fastForward();
                } else {
                    // When the customized fast forward action is not supported, the fakePause
                    // operation is needed to stop the media item but still indicating the media
                    // item is playing from the UI perspective
                    // Also the fakePause() method must be called before
                    // incrementFastForwardPlaybackSpeed() method to make sure fake fast forward
                    // computation is accurate.
                    fakePause();
                }
                // Change mPlaybackSpeed to control the UI.
                incrementFastForwardPlaybackSpeed();
                onUpdatePlaybackStatusAfterUserAction();
            }
            handled = true;
        } else if (action == mRewindAction) {
            if (mPlayerAdapter.isPrepared() && mPlaybackSpeed > -getMaxRewindSpeedId()) {
                if (mIsCustomizedFastForwardSupported) {
                    mIsPlaying = true;
                    mPlayerAdapter.rewind();
                } else {
                    fakePause();
                }
                decrementRewindPlaybackSpeed();
                onUpdatePlaybackStatusAfterUserAction();
            }
            handled = true;
        }
        return handled;
    }

    @Override
    protected void onPlayStateChanged() {
        if (DEBUG) Log.v(TAG, "onStateChanged");

        onUpdatePlaybackState();
        super.onPlayStateChanged();
    }

    @Override
    protected void onPlayCompleted() {
        super.onPlayCompleted();
        mIsPlaying = false;
        mPlaybackSpeed = PLAYBACK_SPEED_PAUSED;
        mStartPosition = getCurrentPosition();
        mStartTime = System.currentTimeMillis();
        onUpdatePlaybackState();
    }

    void onUpdatePlaybackState() {
        updatePlaybackState(mIsPlaying);
    }

    private void updatePlaybackState(boolean isPlaying) {
        if (mControlsRow == null) {
            return;
        }

        if (!isPlaying) {
            onUpdateProgress();
            mPlayerAdapter.setProgressUpdatingEnabled(false);
        } else {
            mPlayerAdapter.setProgressUpdatingEnabled(true);
        }

        if (mFadeWhenPlaying && getHost() != null) {
            getHost().setControlsOverlayAutoHideEnabled(isPlaying);
        }


        final ArrayObjectAdapter primaryActionsAdapter =
                (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
        if (mPlayPauseAction != null) {
            int index = !isPlaying
                    ? PlaybackControlsRow.PlayPauseAction.INDEX_PLAY
                    : PlaybackControlsRow.PlayPauseAction.INDEX_PAUSE;
            if (mPlayPauseAction.getIndex() != index) {
                mPlayPauseAction.setIndex(index);
                notifyItemChanged(primaryActionsAdapter, mPlayPauseAction);
            }
        }

        if (mFastForwardAction != null) {
            int index = 0;
            if (mPlaybackSpeed >= PLAYBACK_SPEED_FAST_L0) {
                index = mPlaybackSpeed - PLAYBACK_SPEED_FAST_L0 + 1;
            }
            if (mFastForwardAction.getIndex() != index) {
                mFastForwardAction.setIndex(index);
                notifyItemChanged(primaryActionsAdapter, mFastForwardAction);
            }
        }
        if (mRewindAction != null) {
            int index = 0;
            if (mPlaybackSpeed <= -PLAYBACK_SPEED_FAST_L0) {
                index = -mPlaybackSpeed - PLAYBACK_SPEED_FAST_L0 + 1;
            }
            if (mRewindAction.getIndex() != index) {
                mRewindAction.setIndex(index);
                notifyItemChanged(primaryActionsAdapter, mRewindAction);
            }
        }
    }

    /**
     * Returns the fast forward speeds.
     */
    @NonNull
    public int[] getFastForwardSpeeds() {
        return mFastForwardSpeeds;
    }

    /**
     * Returns the rewind speeds.
     */
    @NonNull
    public int[] getRewindSpeeds() {
        return mRewindSpeeds;
    }

    private int getMaxForwardSpeedId() {
        return PLAYBACK_SPEED_FAST_L0 + (mFastForwardSpeeds.length - 1);
    }

    private int getMaxRewindSpeedId() {
        return PLAYBACK_SPEED_FAST_L0 + (mRewindSpeeds.length - 1);
    }

    /**
     * Gets current position of the player. If the player is playing/paused, this
     * method returns current position from {@link PlayerAdapter}. Otherwise, if the player is
     * fastforwarding/rewinding, the method fake-pauses the {@link PlayerAdapter} and returns its
     * own calculated position.
     * @return Current position of the player.
     */
    @Override
    public long getCurrentPosition() {
        int speed;
        if (mPlaybackSpeed == PlaybackControlGlue.PLAYBACK_SPEED_PAUSED
                || mPlaybackSpeed == PlaybackControlGlue.PLAYBACK_SPEED_NORMAL) {
            // If the adapter is playing/paused, using the position from adapter instead.
            return mPlayerAdapter.getCurrentPosition();
        } else if (mPlaybackSpeed >= PlaybackControlGlue.PLAYBACK_SPEED_FAST_L0) {
            // If fast forward operation is supported in this scenario, current player position
            // can be get from mPlayerAdapter.getCurrentPosition() directly
            if (mIsCustomizedFastForwardSupported) {
                return mPlayerAdapter.getCurrentPosition();
            }
            int index = mPlaybackSpeed - PlaybackControlGlue.PLAYBACK_SPEED_FAST_L0;
            speed = getFastForwardSpeeds()[index];
        } else if (mPlaybackSpeed <= -PlaybackControlGlue.PLAYBACK_SPEED_FAST_L0) {
            // If fast rewind is supported in this scenario, current player position
            // can be get from mPlayerAdapter.getCurrentPosition() directly
            if (mIsCustomizedRewindSupported) {
                return mPlayerAdapter.getCurrentPosition();
            }
            int index = -mPlaybackSpeed - PlaybackControlGlue.PLAYBACK_SPEED_FAST_L0;
            speed = -getRewindSpeeds()[index];
        } else {
            return -1;
        }

        long position = mStartPosition + (System.currentTimeMillis() - mStartTime) * speed;
        if (position > getDuration()) {
            mPlaybackSpeed = PLAYBACK_SPEED_PAUSED;
            position = getDuration();
            mPlayerAdapter.seekTo(position);
            mStartPosition = 0;
            pause();
        } else if (position < 0) {
            mPlaybackSpeed = PLAYBACK_SPEED_PAUSED;
            position = 0;
            mPlayerAdapter.seekTo(position);
            mStartPosition = 0;
            pause();
        }
        return position;
    }


    @Override
    public void play() {
        if (!mPlayerAdapter.isPrepared()) {
            return;
        }

        // Solves the situation that a player pause at the end and click play button. At this case
        // the player will restart from the beginning.
        if (mPlaybackSpeed == PLAYBACK_SPEED_PAUSED
                && mPlayerAdapter.getCurrentPosition() >= mPlayerAdapter.getDuration()) {
            mStartPosition = 0;
        } else {
            mStartPosition = getCurrentPosition();
        }

        mStartTime = System.currentTimeMillis();
        mIsPlaying = true;
        mPlaybackSpeed = PLAYBACK_SPEED_NORMAL;
        mPlayerAdapter.seekTo(mStartPosition);
        super.play();

        onUpdatePlaybackState();
    }

    @Override
    public void pause() {
        mIsPlaying = false;
        mPlaybackSpeed = PLAYBACK_SPEED_PAUSED;
        mStartPosition = getCurrentPosition();
        mStartTime = System.currentTimeMillis();
        super.pause();

        onUpdatePlaybackState();
    }

    /**
     * Control row shows PLAY, but the media is actually paused when the player is
     * fastforwarding/rewinding.
     */
    private void fakePause() {
        mIsPlaying = true;
        mStartPosition = getCurrentPosition();
        mStartTime = System.currentTimeMillis();
        super.pause();

        onUpdatePlaybackState();
    }
}
