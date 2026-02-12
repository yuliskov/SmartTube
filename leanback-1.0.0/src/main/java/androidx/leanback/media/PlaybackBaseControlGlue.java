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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ControlButtonPresenterSelector;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowPresenter;
import androidx.leanback.widget.Presenter;

import java.util.List;

/**
 * A base abstract class for managing a {@link PlaybackControlsRow} being displayed in
 * {@link PlaybackGlueHost}. It supports standard playback control actions play/pause and
 * skip next/previous. This helper class is a glue layer that manages interaction between the
 * leanback UI components {@link PlaybackControlsRow} {@link PlaybackRowPresenter}
 * and a functional {@link PlayerAdapter} which represents the underlying
 * media player.
 *
 * <p>The app must pass a {@link PlayerAdapter} in constructor for a specific
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
 * @param <T> Type of {@link PlayerAdapter} passed in constructor.
 */
public abstract class PlaybackBaseControlGlue<T extends PlayerAdapter> extends PlaybackGlue
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
     * The adapter key for the repeat control.
     */
    public static final int ACTION_REPEAT = 0x200;

    /**
     * The adapter key for the shuffle control.
     */
    public static final int ACTION_SHUFFLE = 0x400;

    /**
     * The adapter key for the first custom control on the right side
     * of the predefined primary controls.
     */
    public static final int ACTION_CUSTOM_RIGHT_FIRST = 0x1000;

    static final String TAG = "PlaybackTransportGlue";
    static final boolean DEBUG = false;

    final T mPlayerAdapter;
    PlaybackControlsRow mControlsRow;
    PlaybackRowPresenter mControlsRowPresenter;
    PlaybackControlsRow.PlayPauseAction mPlayPauseAction;
    boolean mIsPlaying = false;
    boolean mFadeWhenPlaying = true;

    CharSequence mSubtitle;
    CharSequence mTitle;
    Drawable mCover;

    PlaybackGlueHost.PlayerCallback mPlayerCallback;
    boolean mBuffering = false;
    int mVideoWidth = 0;
    int mVideoHeight = 0;
    boolean mErrorSet = false;
    int mErrorCode;
    String mErrorMessage;

    final PlayerAdapter.Callback mAdapterCallback = new PlayerAdapter
            .Callback() {

        @Override
        public void onPlayStateChanged(PlayerAdapter wrapper) {
            if (DEBUG) Log.v(TAG, "onPlayStateChanged");
            PlaybackBaseControlGlue.this.onPlayStateChanged();
        }

        @Override
        public void onCurrentPositionChanged(PlayerAdapter wrapper) {
            if (DEBUG) Log.v(TAG, "onCurrentPositionChanged");
            PlaybackBaseControlGlue.this.onUpdateProgress();
        }

        @Override
        public void onBufferedPositionChanged(PlayerAdapter wrapper) {
            if (DEBUG) Log.v(TAG, "onBufferedPositionChanged");
            PlaybackBaseControlGlue.this.onUpdateBufferedProgress();
        }

        @Override
        public void onDurationChanged(PlayerAdapter wrapper) {
            if (DEBUG) Log.v(TAG, "onDurationChanged");
            PlaybackBaseControlGlue.this.onUpdateDuration();
        }

        @Override
        public void onPlayCompleted(PlayerAdapter wrapper) {
            if (DEBUG) Log.v(TAG, "onPlayCompleted");
            PlaybackBaseControlGlue.this.onPlayCompleted();
        }

        @Override
        public void onPreparedStateChanged(PlayerAdapter wrapper) {
            if (DEBUG) Log.v(TAG, "onPreparedStateChanged");
            PlaybackBaseControlGlue.this.onPreparedStateChanged();
        }

        @Override
        public void onVideoSizeChanged(PlayerAdapter wrapper, int width, int height) {
            mVideoWidth = width;
            mVideoHeight = height;
            if (mPlayerCallback != null) {
                mPlayerCallback.onVideoSizeChanged(width, height);
            }
        }

        @Override
        public void onError(PlayerAdapter wrapper, int errorCode, String errorMessage) {
            mErrorSet = true;
            mErrorCode = errorCode;
            mErrorMessage = errorMessage;
            if (mPlayerCallback != null) {
                mPlayerCallback.onError(errorCode, errorMessage);
            }
        }

        @Override
        public void onBufferingStateChanged(PlayerAdapter wrapper, boolean start) {
            mBuffering = start;
            if (mPlayerCallback != null) {
                mPlayerCallback.onBufferingStateChanged(start);
            }
        }

        @Override
        public void onMetadataChanged(PlayerAdapter wrapper) {
            PlaybackBaseControlGlue.this.onMetadataChanged();
        }
    };

    /**
     * Constructor for the glue.
     *
     * @param context
     * @param impl Implementation to underlying media player.
     */
    public PlaybackBaseControlGlue(Context context, T impl) {
        super(context);
        mPlayerAdapter = impl;
        mPlayerAdapter.setCallback(mAdapterCallback);
    }

    public final T getPlayerAdapter() {
        return mPlayerAdapter;
    }

    @Override
    protected void onAttachedToHost(PlaybackGlueHost host) {
        super.onAttachedToHost(host);
        host.setOnKeyInterceptListener(this);
        host.setOnActionClickedListener(this);
        onCreateDefaultControlsRow();
        onCreateDefaultRowPresenter();
        host.setPlaybackRowPresenter(getPlaybackRowPresenter());
        host.setPlaybackRow(getControlsRow());

        mPlayerCallback = host.getPlayerCallback();
        onAttachHostCallback();
        mPlayerAdapter.onAttachedToHost(host);
    }

    void onAttachHostCallback() {
        if (mPlayerCallback != null) {
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                mPlayerCallback.onVideoSizeChanged(mVideoWidth, mVideoHeight);
            }
            if (mErrorSet) {
                mPlayerCallback.onError(mErrorCode, mErrorMessage);
            }
            mPlayerCallback.onBufferingStateChanged(mBuffering);
        }
    }

    void onDetachHostCallback() {
        mErrorSet = false;
        mErrorCode = 0;
        mErrorMessage = null;
        if (mPlayerCallback != null) {
            mPlayerCallback.onBufferingStateChanged(false);
        }
    }

    @Override
    protected void onHostStart() {
        mPlayerAdapter.setProgressUpdatingEnabled(true);
    }

    @Override
    protected void onHostStop() {
        mPlayerAdapter.setProgressUpdatingEnabled(false);
    }

    @Override
    protected void onDetachedFromHost() {
        onDetachHostCallback();
        mPlayerCallback = null;
        mPlayerAdapter.onDetachedFromHost();
        mPlayerAdapter.setProgressUpdatingEnabled(false);
        super.onDetachedFromHost();
    }

    void onCreateDefaultControlsRow() {
        if (mControlsRow == null) {
            PlaybackControlsRow controlsRow = new PlaybackControlsRow(this);
            setControlsRow(controlsRow);
        }
    }

    void onCreateDefaultRowPresenter() {
        if (mControlsRowPresenter == null) {
            setPlaybackRowPresenter(onCreateRowPresenter());
        }
    }

    protected abstract PlaybackRowPresenter onCreateRowPresenter();

    /**
     * Sets the controls to auto hide after a timeout when media is playing.
     * @param enable True to enable auto hide after a timeout when media is playing.
     * @see PlaybackGlueHost#setControlsOverlayAutoHideEnabled(boolean)
     */
    public void setControlsOverlayAutoHideEnabled(boolean enable) {
        mFadeWhenPlaying = enable;
        if (!mFadeWhenPlaying && getHost() != null) {
            getHost().setControlsOverlayAutoHideEnabled(false);
        }
    }

    /**
     * Returns true if the controls auto hides after a timeout when media is playing.
     * @see PlaybackGlueHost#isControlsOverlayAutoHideEnabled()
     */
    public boolean isControlsOverlayAutoHideEnabled() {
        return mFadeWhenPlaying;
    }

    /**
     * Sets the controls row to be managed by the glue layer. If
     * {@link PlaybackControlsRow#getPrimaryActionsAdapter()} is not provided, a default
     * {@link ArrayObjectAdapter} will be created and initialized in
     * {@link #onCreatePrimaryActions(ArrayObjectAdapter)}. If
     * {@link PlaybackControlsRow#getSecondaryActionsAdapter()} is not provided, a default
     * {@link ArrayObjectAdapter} will be created and initialized in
     * {@link #onCreateSecondaryActions(ArrayObjectAdapter)}.
     * The primary actions and playback state related aspects of the row
     * are updated by the glue.
     */
    public void setControlsRow(PlaybackControlsRow controlsRow) {
        mControlsRow = controlsRow;
        mControlsRow.setCurrentPosition(-1);
        mControlsRow.setDuration(-1);
        mControlsRow.setBufferedPosition(-1);
        if (mControlsRow.getPrimaryActionsAdapter() == null) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(
                    new ControlButtonPresenterSelector());
            onCreatePrimaryActions(adapter);
            mControlsRow.setPrimaryActionsAdapter(adapter);
        }
        // Add secondary actions
        if (mControlsRow.getSecondaryActionsAdapter() == null) {
            ArrayObjectAdapter secondaryActions = new ArrayObjectAdapter(
                    new ControlButtonPresenterSelector());
            onCreateSecondaryActions(secondaryActions);
            getControlsRow().setSecondaryActionsAdapter(secondaryActions);
        }
        updateControlsRow();
    }

    /**
     * Sets the controls row Presenter to be managed by the glue layer.
     */
    public void setPlaybackRowPresenter(PlaybackRowPresenter presenter) {
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
     */
    public PlaybackRowPresenter getPlaybackRowPresenter() {
        return mControlsRowPresenter;
    }

    /**
     * Handles action clicks.  A subclass may override this add support for additional actions.
     */
    @Override
    public abstract void onActionClicked(Action action);

    /**
     * Handles key events and returns true if handled.  A subclass may override this to provide
     * additional support.
     */
    @Override
    public abstract boolean onKey(View v, int keyCode, KeyEvent event);

    private void updateControlsRow() {
        onMetadataChanged();
    }

    @Override
    public final boolean isPlaying() {
        return mPlayerAdapter.isPlaying();
    }

    @Override
    public void play() {
        mPlayerAdapter.play();
    }

    @Override
    public void pause() {
        mPlayerAdapter.pause();
    }

    @Override
    public void next() {
        mPlayerAdapter.next();
    }

    @Override
    public void previous() {
        mPlayerAdapter.previous();
    }

    protected static void notifyItemChanged(ArrayObjectAdapter adapter, Object object) {
        int index = adapter.indexOf(object);
        if (index >= 0) {
            adapter.notifyArrayItemRangeChanged(index, 1);
        }
    }

    /**
     * May be overridden to add primary actions to the adapter. Default implementation add
     * {@link PlaybackControlsRow.PlayPauseAction}.
     *
     * @param primaryActionsAdapter The adapter to add primary {@link Action}s.
     */
    protected void onCreatePrimaryActions(ArrayObjectAdapter primaryActionsAdapter) {
    }

    /**
     * May be overridden to add secondary actions to the adapter.
     *
     * @param secondaryActionsAdapter The adapter you need to add the {@link Action}s to.
     */
    protected void onCreateSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
    }

    @CallSuper
    protected void onUpdateProgress() {
        if (mControlsRow != null) {
            mControlsRow.setCurrentPosition(mPlayerAdapter.isPrepared()
                    ? getCurrentPosition() : -1);
        }
    }

    @CallSuper
    protected void onUpdateBufferedProgress() {
        if (mControlsRow != null) {
            mControlsRow.setBufferedPosition(mPlayerAdapter.getBufferedPosition());
        }
    }

    @CallSuper
    protected void onUpdateDuration() {
        if (mControlsRow != null) {
            mControlsRow.setDuration(
                    mPlayerAdapter.isPrepared() ? mPlayerAdapter.getDuration() : -1);
        }
    }

    /**
     * @return The duration of the media item in milliseconds.
     */
    public final long getDuration() {
        return mPlayerAdapter.getDuration();
    }

    /**
     * @return The current position of the media item in milliseconds.
     */
    public long getCurrentPosition() {
        return mPlayerAdapter.getCurrentPosition();
    }

    /**
     * @return The current buffered position of the media item in milliseconds.
     */
    public final long getBufferedPosition() {
        return mPlayerAdapter.getBufferedPosition();
    }

    @Override
    public final boolean isPrepared() {
        return mPlayerAdapter.isPrepared();
    }

    /**
     * Event when ready state for play changes.
     */
    @CallSuper
    protected void onPreparedStateChanged() {
        onUpdateDuration();
        List<PlayerCallback> callbacks = getPlayerCallbacks();
        if (callbacks != null) {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onPreparedStateChanged(this);
            }
        }
    }

    /**
     * Sets the drawable representing cover image. The drawable will be rendered by default
     * description presenter in
     * {@link PlaybackTransportRowPresenter#setDescriptionPresenter(Presenter)}.
     * @param cover The drawable representing cover image.
     */
    public void setArt(Drawable cover) {
        if (mCover == cover) {
            return;
        }
        this.mCover = cover;
        mControlsRow.setImageDrawable(mCover);
        if (getHost() != null) {
            getHost().notifyPlaybackRowChanged();
        }
    }

    /**
     * @return The drawable representing cover image.
     */
    public Drawable getArt() {
        return mCover;
    }

    /**
     * Sets the media subtitle. The subtitle will be rendered by default description presenter
     * {@link PlaybackTransportRowPresenter#setDescriptionPresenter(Presenter)}.
     * @param subtitle Subtitle to set.
     */
    public void setSubtitle(CharSequence subtitle) {
        if (TextUtils.equals(subtitle, mSubtitle)) {
            return;
        }
        mSubtitle = subtitle;
        if (getHost() != null) {
            getHost().notifyPlaybackRowChanged();
        }
    }

    /**
     * Return The media subtitle.
     */
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    /**
     * Sets the media title. The title will be rendered by default description presenter
     * {@link PlaybackTransportRowPresenter#setDescriptionPresenter(Presenter)}.
     */
    public void setTitle(CharSequence title) {
        if (TextUtils.equals(title, mTitle)) {
            return;
        }
        mTitle = title;
        if (getHost() != null) {
            getHost().notifyPlaybackRowChanged();
        }
    }

    /**
     * Returns the title of the media item.
     */
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Event when metadata changed
     */
    protected void onMetadataChanged() {
        if (mControlsRow == null) {
            return;
        }

        if (DEBUG) Log.v(TAG, "updateRowMetadata");

        mControlsRow.setImageDrawable(getArt());
        mControlsRow.setDuration(getDuration());
        mControlsRow.setCurrentPosition(getCurrentPosition());

        if (getHost() != null) {
            getHost().notifyPlaybackRowChanged();
        }
    }

    /**
     * Event when play state changed.
     */
    @CallSuper
    protected void onPlayStateChanged() {
        List<PlayerCallback> callbacks = getPlayerCallbacks();
        if (callbacks != null) {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onPlayStateChanged(this);
            }
        }
    }

    /**
     * Event when play finishes, subclass may handling repeat mode here.
     */
    @CallSuper
    protected void onPlayCompleted() {
        List<PlayerCallback> callbacks = getPlayerCallbacks();
        if (callbacks != null) {
            for (int i = 0, size = callbacks.size(); i < size; i++) {
                callbacks.get(i).onPlayCompleted(this);
            }
        }
    }

    /**
     * Seek media to a new position.
     * @param position New position.
     */
    public final void seekTo(long position) {
        mPlayerAdapter.seekTo(position);
    }

    /**
     * Returns a bitmask of actions supported by the media player.
     */
    public long getSupportedActions() {
        return mPlayerAdapter.getSupportedActions();
    }
}
