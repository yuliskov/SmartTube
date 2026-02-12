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
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ControlButtonPresenterSelector;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackControlsRowPresenter;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SparseArrayObjectAdapter;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * A helper class for managing a {@link PlaybackControlsRow}
 * and {@link PlaybackGlueHost} that implements a
 * recommended approach to handling standard playback control actions such as play/pause,
 * fast forward/rewind at progressive speed levels, and skip to next/previous. This helper class
 * is a glue layer in that manages the configuration of and interaction between the
 * leanback UI components by defining a functional interface to the media player.
 *
 * <p>You can instantiate a concrete subclass such as MediaPlayerGlue or you must
 * subclass this abstract helper.  To create a subclass you must implement all of the
 * abstract methods and the subclass must invoke {@link #onMetadataChanged()} and
 * {@link #onStateChanged()} appropriately.
 * </p>
 *
 * <p>To use an instance of the glue layer, first construct an instance.  Constructor parameters
 * inform the glue what speed levels are supported for fast forward/rewind.
 * </p>
 *
 * <p>You may override {@link #onCreateControlsRowAndPresenter()} which will create a
 * {@link PlaybackControlsRow} and a {@link PlaybackControlsRowPresenter}. You may call
 * {@link #setControlsRow(PlaybackControlsRow)} and
 * {@link #setPlaybackRowPresenter(PlaybackRowPresenter)} to customize your own row and presenter.
 * </p>
 *
 * <p>The helper sets a {@link SparseArrayObjectAdapter}
 * on the controls row as the primary actions adapter, and adds actions to it. You can provide
 * additional actions by overriding {@link #onCreatePrimaryActions}. This helper does not
 * deal in secondary actions so those you may add separately.
 * </p>
 *
 * <p>Provide a click listener on your fragment and if an action is clicked, call
 * {@link #onActionClicked}.
 * </p>
 *
 * <p>This helper implements a key event handler. If you pass a
 * {@link PlaybackGlueHost}, it will configure its
 * fragment to intercept all key events.  Otherwise, you should set the glue object as key event
 * handler to the ViewHolder when bound by your row presenter; see
 * {@link RowPresenter.ViewHolder#setOnKeyListener(android.view.View.OnKeyListener)}.
 * </p>
 *
 * <p>To update the controls row progress during playback, override {@link #enableProgressUpdating}
 * to manage the lifecycle of a periodic callback to {@link #updateProgress()}.
 * {@link #getUpdatePeriod()} provides a recommended update period.
 * </p>
 *
 */
public abstract class PlaybackControlGlue extends PlaybackGlue
        implements OnActionClickedListener, View.OnKeyListener {
    /**
     * The adapter key for the first custom control on the left side
     * of the predefined primary controls.
     */
    public static final int ACTION_CUSTOM_LEFT_FIRST = 0x1;

    /**
     * The adapter key for the skip to previous control.
     */
    public static final int ACTION_SKIP_TO_PREVIOUS = 0x10;

    /**
     * The adapter key for the rewind control.
     */
    public static final int ACTION_REWIND = 0x20;

    /**
     * The adapter key for the play/pause control.
     */
    public static final int ACTION_PLAY_PAUSE = 0x40;

    /**
     * The adapter key for the fast forward control.
     */
    public static final int ACTION_FAST_FORWARD = 0x80;

    /**
     * The adapter key for the skip to next control.
     */
    public static final int ACTION_SKIP_TO_NEXT = 0x100;

    /**
     * The adapter key for the first custom control on the right side
     * of the predefined primary controls.
     */
    public static final int ACTION_CUSTOM_RIGHT_FIRST = 0x1000;

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

    static final String TAG = "PlaybackControlGlue";
    static final boolean DEBUG = false;

    static final int MSG_UPDATE_PLAYBACK_STATE = 100;
    private static final int UPDATE_PLAYBACK_STATE_DELAY_MS = 2000;
    private static final int NUMBER_OF_SEEK_SPEEDS = PLAYBACK_SPEED_FAST_L4
            - PLAYBACK_SPEED_FAST_L0 + 1;

    private final int[] mFastForwardSpeeds;
    private final int[] mRewindSpeeds;
    private PlaybackControlsRow mControlsRow;
    private PlaybackRowPresenter mControlsRowPresenter;
    private PlaybackControlsRow.PlayPauseAction mPlayPauseAction;
    private PlaybackControlsRow.SkipNextAction mSkipNextAction;
    private PlaybackControlsRow.SkipPreviousAction mSkipPreviousAction;
    private PlaybackControlsRow.FastForwardAction mFastForwardAction;
    private PlaybackControlsRow.RewindAction mRewindAction;
    private int mPlaybackSpeed = PLAYBACK_SPEED_NORMAL;
    private boolean mFadeWhenPlaying = true;

    static class UpdatePlaybackStateHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_PLAYBACK_STATE) {
                PlaybackControlGlue glue = ((WeakReference<PlaybackControlGlue>) msg.obj).get();
                if (glue != null) {
                    glue.updatePlaybackState();
                }
            }
        }
    }

    static final Handler sHandler = new UpdatePlaybackStateHandler();

    final WeakReference<PlaybackControlGlue> mGlueWeakReference =  new WeakReference(this);

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param seekSpeeds Array of seek speeds for fast forward and rewind.
     */
    public PlaybackControlGlue(Context context, int[] seekSpeeds) {
        this(context, seekSpeeds, seekSpeeds);
    }

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param fastForwardSpeeds Array of seek speeds for fast forward.
     * @param rewindSpeeds Array of seek speeds for rewind.
     */
    public PlaybackControlGlue(Context context,
                               int[] fastForwardSpeeds,
                               int[] rewindSpeeds) {
        super(context);
        if (fastForwardSpeeds.length == 0 || fastForwardSpeeds.length > NUMBER_OF_SEEK_SPEEDS) {
            throw new IllegalStateException("invalid fastForwardSpeeds array size");
        }
        mFastForwardSpeeds = fastForwardSpeeds;
        if (rewindSpeeds.length == 0 || rewindSpeeds.length > NUMBER_OF_SEEK_SPEEDS) {
            throw new IllegalStateException("invalid rewindSpeeds array size");
        }
        mRewindSpeeds = rewindSpeeds;
    }

    @Override
    protected void onAttachedToHost(PlaybackGlueHost host) {
        super.onAttachedToHost(host);
        host.setOnKeyInterceptListener(this);
        host.setOnActionClickedListener(this);
        if (getControlsRow() == null || getPlaybackRowPresenter() == null) {
            onCreateControlsRowAndPresenter();
        }
        host.setPlaybackRowPresenter(getPlaybackRowPresenter());
        host.setPlaybackRow(getControlsRow());
    }

    @Override
    protected void onHostStart() {
        enableProgressUpdating(true);
    }

    @Override
    protected void onHostStop() {
        enableProgressUpdating(false);
    }

    @Override
    protected void onDetachedFromHost() {
        enableProgressUpdating(false);
        super.onDetachedFromHost();
    }

    /**
     * Instantiating a {@link PlaybackControlsRow} and corresponding
     * {@link PlaybackControlsRowPresenter}. Subclass may override.
     */
    protected void onCreateControlsRowAndPresenter() {
        if (getControlsRow() == null) {
            PlaybackControlsRow controlsRow = new PlaybackControlsRow(this);
            setControlsRow(controlsRow);
        }
        if (getPlaybackRowPresenter() == null) {
            final AbstractDetailsDescriptionPresenter detailsPresenter =
                    new AbstractDetailsDescriptionPresenter() {
                        @Override
                        protected void onBindDescription(ViewHolder
                                viewHolder, Object object) {
                            PlaybackControlGlue glue = (PlaybackControlGlue) object;
                            if (glue.hasValidMedia()) {
                                viewHolder.getTitle().setText(glue.getMediaTitle());
                                viewHolder.getSubtitle().setText(glue.getMediaSubtitle());
                            } else {
                                viewHolder.getTitle().setText("");
                                viewHolder.getSubtitle().setText("");
                            }
                        }
                    };

            setPlaybackRowPresenter(new PlaybackControlsRowPresenter(detailsPresenter) {
                @Override
                protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                    super.onBindRowViewHolder(vh, item);
                    vh.setOnKeyListener(PlaybackControlGlue.this);
                }
                @Override
                protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                    super.onUnbindRowViewHolder(vh);
                    vh.setOnKeyListener(null);
                }
            });
        }
    }

    /**
     * Returns the fast forward speeds.
     */
    public int[] getFastForwardSpeeds() {
        return mFastForwardSpeeds;
    }

    /**
     * Returns the rewind speeds.
     */
    public int[] getRewindSpeeds() {
        return mRewindSpeeds;
    }

    /**
     * Sets the controls to fade after a timeout when media is playing.
     */
    public void setFadingEnabled(boolean enable) {
        mFadeWhenPlaying = enable;
        if (!mFadeWhenPlaying && getHost() != null) {
            getHost().setControlsOverlayAutoHideEnabled(false);
        }
    }

    /**
     * Returns true if controls are set to fade when media is playing.
     */
    public boolean isFadingEnabled() {
        return mFadeWhenPlaying;
    }

    /**
     * Sets the controls row to be managed by the glue layer.
     * The primary actions and playback state related aspects of the row
     * are updated by the glue.
     */
    public void setControlsRow(PlaybackControlsRow controlsRow) {
        mControlsRow = controlsRow;
        mControlsRow.setPrimaryActionsAdapter(
                createPrimaryActionsAdapter(new ControlButtonPresenterSelector()));
        // Add secondary actions
        ArrayObjectAdapter secondaryActions = new ArrayObjectAdapter(
                new ControlButtonPresenterSelector());
        onCreateSecondaryActions(secondaryActions);
        getControlsRow().setSecondaryActionsAdapter(secondaryActions);
        updateControlsRow();
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    protected SparseArrayObjectAdapter createPrimaryActionsAdapter(
            PresenterSelector presenterSelector) {
        SparseArrayObjectAdapter adapter = new SparseArrayObjectAdapter(presenterSelector);
        onCreatePrimaryActions(adapter);
        return adapter;
    }

    /**
     * Sets the controls row Presenter to be managed by the glue layer.
     * @deprecated PlaybackControlGlue supports any PlaybackRowPresenter, use
     * {@link #setPlaybackRowPresenter(PlaybackRowPresenter)}.
     */
    @Deprecated
    public void setControlsRowPresenter(PlaybackControlsRowPresenter presenter) {
        mControlsRowPresenter = presenter;
    }

    /**
     * Returns the playback controls row managed by the glue layer.
     */
    public PlaybackControlsRow getControlsRow() {
        return mControlsRow;
    }

    /**
     * Returns the playback controls row Presenter managed by the glue layer.
     * @deprecated PlaybackControlGlue supports any PlaybackRowPresenter, use
     * {@link #getPlaybackRowPresenter()}.
     */
    @Deprecated
    public PlaybackControlsRowPresenter getControlsRowPresenter() {
        return mControlsRowPresenter instanceof PlaybackControlsRowPresenter
                ? (PlaybackControlsRowPresenter) mControlsRowPresenter : null;
    }

    /**
     * Sets the controls row Presenter to be passed to {@link PlaybackGlueHost} in
     * {@link #onAttachedToHost(PlaybackGlueHost)}.
     */
    public void setPlaybackRowPresenter(PlaybackRowPresenter presenter) {
        mControlsRowPresenter = presenter;
    }

    /**
     * Returns the playback row Presenter to be passed to {@link PlaybackGlueHost} in
     * {@link #onAttachedToHost(PlaybackGlueHost)}.
     */
    public PlaybackRowPresenter getPlaybackRowPresenter() {
        return mControlsRowPresenter;
    }

    /**
     * Override this to start/stop a runnable to call {@link #updateProgress} at
     * an interval such as {@link #getUpdatePeriod}.
     */
    public void enableProgressUpdating(boolean enable) {
    }

    /**
     * Returns the time period in milliseconds that should be used
     * to update the progress.  See {@link #updateProgress()}.
     */
    public int getUpdatePeriod() {
        // TODO: calculate a better update period based on total duration and screen size
        return 500;
    }

    /**
     * Updates the progress bar based on the current media playback position.
     */
    public void updateProgress() {
        int position = getCurrentPosition();
        if (DEBUG) Log.v(TAG, "updateProgress " + position);
        if (mControlsRow != null) {
            mControlsRow.setCurrentTime(position);
        }
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
                    mPlaybackSpeed = PLAYBACK_SPEED_NORMAL;
                    play(mPlaybackSpeed);
                    updatePlaybackStatusAfterUserAction();
                    return keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE;
                }
                return false;
        }
        final SparseArrayObjectAdapter primaryActionsAdapter = (SparseArrayObjectAdapter)
                mControlsRow.getPrimaryActionsAdapter();
        Action action = mControlsRow.getActionForKeyCode(primaryActionsAdapter, keyCode);

        if (action != null) {
            if (action == primaryActionsAdapter.lookup(ACTION_PLAY_PAUSE)
                    || action == primaryActionsAdapter.lookup(ACTION_REWIND)
                    || action == primaryActionsAdapter.lookup(ACTION_FAST_FORWARD)
                    || action == primaryActionsAdapter.lookup(ACTION_SKIP_TO_PREVIOUS)
                    || action == primaryActionsAdapter.lookup(ACTION_SKIP_TO_NEXT)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    dispatchAction(action, event);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Called when the given action is invoked, either by click or keyevent.
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
                mPlaybackSpeed = PLAYBACK_SPEED_PAUSED;
                pause();
            } else if (canPlay && mPlaybackSpeed != PLAYBACK_SPEED_NORMAL) {
                mPlaybackSpeed = PLAYBACK_SPEED_NORMAL;
                play(mPlaybackSpeed);
            }
            updatePlaybackStatusAfterUserAction();
            handled = true;
        } else if (action == mSkipNextAction) {
            next();
            handled = true;
        } else if (action == mSkipPreviousAction) {
            previous();
            handled = true;
        } else if (action == mFastForwardAction) {
            if (mPlaybackSpeed < getMaxForwardSpeedId()) {
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
                play(mPlaybackSpeed);
                updatePlaybackStatusAfterUserAction();
            }
            handled = true;
        } else if (action == mRewindAction) {
            if (mPlaybackSpeed > -getMaxRewindSpeedId()) {
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
                play(mPlaybackSpeed);
                updatePlaybackStatusAfterUserAction();
            }
            handled = true;
        }
        return handled;
    }

    private int getMaxForwardSpeedId() {
        return PLAYBACK_SPEED_FAST_L0 + (mFastForwardSpeeds.length - 1);
    }

    private int getMaxRewindSpeedId() {
        return PLAYBACK_SPEED_FAST_L0 + (mRewindSpeeds.length - 1);
    }

    private void updateControlsRow() {
        updateRowMetadata();
        updateControlButtons();
        sHandler.removeMessages(MSG_UPDATE_PLAYBACK_STATE, mGlueWeakReference);
        updatePlaybackState();
    }

    private void updatePlaybackStatusAfterUserAction() {
        updatePlaybackState(mPlaybackSpeed);
        // Sync playback state after a delay
        sHandler.removeMessages(MSG_UPDATE_PLAYBACK_STATE, mGlueWeakReference);
        sHandler.sendMessageDelayed(sHandler.obtainMessage(MSG_UPDATE_PLAYBACK_STATE,
                mGlueWeakReference), UPDATE_PLAYBACK_STATE_DELAY_MS);
    }

    /**
     * Start playback at the given speed.
     *
     * @param speed The desired playback speed.  For normal playback this will be
     *              {@link #PLAYBACK_SPEED_NORMAL}; higher positive values for fast forward,
     *              and negative values for rewind.
     */
    public void play(int speed) {
    }

    @Override
    public final void play() {
        play(PLAYBACK_SPEED_NORMAL);
    }

    private void updateRowMetadata() {
        if (mControlsRow == null) {
            return;
        }

        if (DEBUG) Log.v(TAG, "updateRowMetadata");

        if (!hasValidMedia()) {
            mControlsRow.setImageDrawable(null);
            mControlsRow.setTotalTime(0);
            mControlsRow.setCurrentTime(0);
        } else {
            mControlsRow.setImageDrawable(getMediaArt());
            mControlsRow.setTotalTime(getMediaDuration());
            mControlsRow.setCurrentTime(getCurrentPosition());
        }

        if (getHost() != null) {
            getHost().notifyPlaybackRowChanged();
        }
    }

    void updatePlaybackState() {
        if (hasValidMedia()) {
            mPlaybackSpeed = getCurrentSpeedId();
            updatePlaybackState(mPlaybackSpeed);
        }
    }

    void updateControlButtons() {
        final SparseArrayObjectAdapter primaryActionsAdapter = (SparseArrayObjectAdapter)
                getControlsRow().getPrimaryActionsAdapter();
        final long actions = getSupportedActions();
        if ((actions & ACTION_SKIP_TO_PREVIOUS) != 0 && mSkipPreviousAction == null) {
            mSkipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(getContext());
            primaryActionsAdapter.set(ACTION_SKIP_TO_PREVIOUS, mSkipPreviousAction);
        } else if ((actions & ACTION_SKIP_TO_PREVIOUS) == 0 && mSkipPreviousAction != null) {
            primaryActionsAdapter.clear(ACTION_SKIP_TO_PREVIOUS);
            mSkipPreviousAction = null;
        }
        if ((actions & ACTION_REWIND) != 0 && mRewindAction == null) {
            mRewindAction = new PlaybackControlsRow.RewindAction(getContext(),
                    mRewindSpeeds.length);
            primaryActionsAdapter.set(ACTION_REWIND, mRewindAction);
        } else if ((actions & ACTION_REWIND) == 0 && mRewindAction != null) {
            primaryActionsAdapter.clear(ACTION_REWIND);
            mRewindAction = null;
        }
        if ((actions & ACTION_PLAY_PAUSE) != 0 && mPlayPauseAction == null) {
            mPlayPauseAction = new PlaybackControlsRow.PlayPauseAction(getContext());
            primaryActionsAdapter.set(ACTION_PLAY_PAUSE, mPlayPauseAction);
        } else if ((actions & ACTION_PLAY_PAUSE) == 0 && mPlayPauseAction != null) {
            primaryActionsAdapter.clear(ACTION_PLAY_PAUSE);
            mPlayPauseAction = null;
        }
        if ((actions & ACTION_FAST_FORWARD) != 0 && mFastForwardAction == null) {
            mFastForwardAction = new PlaybackControlsRow.FastForwardAction(getContext(),
                    mFastForwardSpeeds.length);
            primaryActionsAdapter.set(ACTION_FAST_FORWARD, mFastForwardAction);
        } else if ((actions & ACTION_FAST_FORWARD) == 0 && mFastForwardAction != null) {
            primaryActionsAdapter.clear(ACTION_FAST_FORWARD);
            mFastForwardAction = null;
        }
        if ((actions & ACTION_SKIP_TO_NEXT) != 0 && mSkipNextAction == null) {
            mSkipNextAction = new PlaybackControlsRow.SkipNextAction(getContext());
            primaryActionsAdapter.set(ACTION_SKIP_TO_NEXT, mSkipNextAction);
        } else if ((actions & ACTION_SKIP_TO_NEXT) == 0 && mSkipNextAction != null) {
            primaryActionsAdapter.clear(ACTION_SKIP_TO_NEXT);
            mSkipNextAction = null;
        }
    }

    private void updatePlaybackState(int playbackSpeed) {
        if (mControlsRow == null) {
            return;
        }

        final SparseArrayObjectAdapter primaryActionsAdapter = (SparseArrayObjectAdapter)
                getControlsRow().getPrimaryActionsAdapter();

        if (mFastForwardAction != null) {
            int index = 0;
            if (playbackSpeed >= PLAYBACK_SPEED_FAST_L0) {
                index = playbackSpeed - PLAYBACK_SPEED_FAST_L0 + 1;
            }
            if (mFastForwardAction.getIndex() != index) {
                mFastForwardAction.setIndex(index);
                notifyItemChanged(primaryActionsAdapter, mFastForwardAction);
            }
        }
        if (mRewindAction != null) {
            int index = 0;
            if (playbackSpeed <= -PLAYBACK_SPEED_FAST_L0) {
                index = -playbackSpeed - PLAYBACK_SPEED_FAST_L0 + 1;
            }
            if (mRewindAction.getIndex() != index) {
                mRewindAction.setIndex(index);
                notifyItemChanged(primaryActionsAdapter, mRewindAction);
            }
        }

        if (playbackSpeed == PLAYBACK_SPEED_PAUSED) {
            updateProgress();
            enableProgressUpdating(false);
        } else {
            enableProgressUpdating(true);
        }

        if (mFadeWhenPlaying && getHost() != null) {
            getHost().setControlsOverlayAutoHideEnabled(playbackSpeed == PLAYBACK_SPEED_NORMAL);
        }

        if (mPlayPauseAction != null) {
            int index = playbackSpeed == PLAYBACK_SPEED_PAUSED
                    ? PlaybackControlsRow.PlayPauseAction.INDEX_PLAY
                    : PlaybackControlsRow.PlayPauseAction.INDEX_PAUSE;
            if (mPlayPauseAction.getIndex() != index) {
                mPlayPauseAction.setIndex(index);
                notifyItemChanged(primaryActionsAdapter, mPlayPauseAction);
            }
        }
        List<PlayerCallback> callbacks = getPlayerCallbacks();
        if (callbacks != null) {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onPlayStateChanged(this);
            }
        }
    }

    private static void notifyItemChanged(SparseArrayObjectAdapter adapter, Object object) {
        int index = adapter.indexOf(object);
        if (index >= 0) {
            adapter.notifyArrayItemRangeChanged(index, 1);
        }
    }

    private static String getSpeedString(int speed) {
        switch (speed) {
            case PLAYBACK_SPEED_INVALID:
                return "PLAYBACK_SPEED_INVALID";
            case PLAYBACK_SPEED_PAUSED:
                return "PLAYBACK_SPEED_PAUSED";
            case PLAYBACK_SPEED_NORMAL:
                return "PLAYBACK_SPEED_NORMAL";
            case PLAYBACK_SPEED_FAST_L0:
                return "PLAYBACK_SPEED_FAST_L0";
            case PLAYBACK_SPEED_FAST_L1:
                return "PLAYBACK_SPEED_FAST_L1";
            case PLAYBACK_SPEED_FAST_L2:
                return "PLAYBACK_SPEED_FAST_L2";
            case PLAYBACK_SPEED_FAST_L3:
                return "PLAYBACK_SPEED_FAST_L3";
            case PLAYBACK_SPEED_FAST_L4:
                return "PLAYBACK_SPEED_FAST_L4";
            case -PLAYBACK_SPEED_FAST_L0:
                return "-PLAYBACK_SPEED_FAST_L0";
            case -PLAYBACK_SPEED_FAST_L1:
                return "-PLAYBACK_SPEED_FAST_L1";
            case -PLAYBACK_SPEED_FAST_L2:
                return "-PLAYBACK_SPEED_FAST_L2";
            case -PLAYBACK_SPEED_FAST_L3:
                return "-PLAYBACK_SPEED_FAST_L3";
            case -PLAYBACK_SPEED_FAST_L4:
                return "-PLAYBACK_SPEED_FAST_L4";
        }
        return null;
    }

    /**
     * Returns true if there is a valid media item.
     */
    public abstract boolean hasValidMedia();

    /**
     * Returns true if media is currently playing.
     */
    public abstract boolean isMediaPlaying();

    @Override
    public boolean isPlaying() {
        return isMediaPlaying();
    }

    /**
     * Returns the title of the media item.
     */
    public abstract CharSequence getMediaTitle();

    /**
     * Returns the subtitle of the media item.
     */
    public abstract CharSequence getMediaSubtitle();

    /**
     * Returns the duration of the media item in milliseconds.
     */
    public abstract int getMediaDuration();

    /**
     * Returns a bitmap of the art for the media item.
     */
    public abstract Drawable getMediaArt();

    /**
     * Returns a bitmask of actions supported by the media player.
     */
    public abstract long getSupportedActions();

    /**
     * Returns the current playback speed.  When playing normally,
     * {@link #PLAYBACK_SPEED_NORMAL} should be returned.
     */
    public abstract int getCurrentSpeedId();

    /**
     * Returns the current position of the media item in milliseconds.
     */
    public abstract int getCurrentPosition();

    /**
     * May be overridden to add primary actions to the adapter.
     *
     * @param primaryActionsAdapter The adapter to add primary {@link Action}s.
     */
    protected void onCreatePrimaryActions(SparseArrayObjectAdapter primaryActionsAdapter) {
    }

    /**
     * May be overridden to add secondary actions to the adapter.
     *
     * @param secondaryActionsAdapter The adapter you need to add the {@link Action}s to.
     */
    protected void onCreateSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
    }

    /**
     * Must be called appropriately by a subclass when the playback state has changed.
     * It updates the playback state displayed on the media player.
     */
    protected void onStateChanged() {
        if (DEBUG) Log.v(TAG, "onStateChanged");
        // If a pending control button update is present, delay
        // the update until the state settles.
        if (!hasValidMedia()) {
            return;
        }
        if (sHandler.hasMessages(MSG_UPDATE_PLAYBACK_STATE, mGlueWeakReference)) {
            sHandler.removeMessages(MSG_UPDATE_PLAYBACK_STATE, mGlueWeakReference);
            if (getCurrentSpeedId() != mPlaybackSpeed) {
                if (DEBUG) Log.v(TAG, "Status expectation mismatch, delaying update");
                sHandler.sendMessageDelayed(sHandler.obtainMessage(MSG_UPDATE_PLAYBACK_STATE,
                        mGlueWeakReference), UPDATE_PLAYBACK_STATE_DELAY_MS);
            } else {
                if (DEBUG) Log.v(TAG, "Update state matches expectation");
                updatePlaybackState();
            }
        } else {
            updatePlaybackState();
        }
    }

    /**
     * Must be called appropriately by a subclass when the metadata state has changed.
     */
    protected void onMetadataChanged() {
        if (DEBUG) Log.v(TAG, "onMetadataChanged");
        updateRowMetadata();
    }
}
