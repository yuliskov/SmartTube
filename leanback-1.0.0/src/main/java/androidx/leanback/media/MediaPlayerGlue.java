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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.RestrictTo;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;

import java.io.IOException;
import java.util.List;

/**
 * This glue extends the {@link androidx.leanback.media.PlaybackControlGlue} with a
 * {@link MediaPlayer} synchronization. It supports 7 actions:
 *
 * <ul>
 * <li>{@link androidx.leanback.widget.PlaybackControlsRow.FastForwardAction}</li>
 * <li>{@link androidx.leanback.widget.PlaybackControlsRow.RewindAction}</li>
 * <li>{@link  androidx.leanback.widget.PlaybackControlsRow.PlayPauseAction}</li>
 * <li>{@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction}</li>
 * <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsDownAction}</li>
 * <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsUpAction}</li>
 * </ul>
 *
 * @hide
 * @deprecated Use {@link MediaPlayerAdapter} with {@link PlaybackTransportControlGlue} or
 *             {@link PlaybackBannerControlGlue}.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Deprecated
public class MediaPlayerGlue extends PlaybackControlGlue implements
        OnItemViewSelectedListener {

    public static final int NO_REPEAT = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    public static final int FAST_FORWARD_REWIND_STEP = 10 * 1000; // in milliseconds
    public static final int FAST_FORWARD_REWIND_REPEAT_DELAY = 200; // in milliseconds
    private static final String TAG = "MediaPlayerGlue";
    protected final PlaybackControlsRow.ThumbsDownAction mThumbsDownAction;
    protected final PlaybackControlsRow.ThumbsUpAction mThumbsUpAction;
    MediaPlayer mPlayer = new MediaPlayer();
    private final PlaybackControlsRow.RepeatAction mRepeatAction;
    private Runnable mRunnable;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    Handler mHandler = new Handler();
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean mInitialized = false; // true when the MediaPlayer is prepared/initialized
    private Action mSelectedAction; // the action which is currently selected by the user
    private long mLastKeyDownEvent = 0L; // timestamp when the last DPAD_CENTER KEY_DOWN occurred
    private Uri mMediaSourceUri = null;
    private String mMediaSourcePath = null;
    private MediaPlayer.OnCompletionListener mOnCompletionListener;
    private String mArtist;
    private String mTitle;
    private Drawable mCover;

    /**
     * Sets the drawable representing cover image.
     */
    public void setCover(Drawable cover) {
        this.mCover = cover;
    }

    /**
     * Sets the artist name.
     */
    public void setArtist(String artist) {
        this.mArtist = artist;
    }

    /**
     * Sets the media title.
     */
    public void setTitle(String title) {
        this.mTitle = title;
    }

    /**
     * Sets the url for the video.
     */
    public void setVideoUrl(String videoUrl) {
        setMediaSource(videoUrl);
        onMetadataChanged();
    }

    /**
     * Constructor.
     */
    public MediaPlayerGlue(Context context) {
        this(context, new int[]{1}, new int[]{1});
    }

    /**
     * Constructor.
     */
    public MediaPlayerGlue(
            Context context, int[] fastForwardSpeeds, int[] rewindSpeeds) {
        super(context, fastForwardSpeeds, rewindSpeeds);

        // Instantiate secondary actions
        mRepeatAction = new PlaybackControlsRow.RepeatAction(getContext());
        mThumbsDownAction = new PlaybackControlsRow.ThumbsDownAction(getContext());
        mThumbsUpAction = new PlaybackControlsRow.ThumbsUpAction(getContext());
        mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_OUTLINE);
        mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_OUTLINE);
    }

    @Override
    protected void onAttachedToHost(PlaybackGlueHost host) {
        super.onAttachedToHost(host);
        if (host instanceof SurfaceHolderGlueHost) {
            ((SurfaceHolderGlueHost) host).setSurfaceHolderCallback(
                    new VideoPlayerSurfaceHolderCallback());
        }
    }

    /**
     * Will reset the {@link MediaPlayer} and the glue such that a new file can be played. You are
     * not required to call this method before playing the first file. However you have to call it
     * before playing a second one.
     */
    public void reset() {
        changeToUnitialized();
        mPlayer.reset();
    }

    void changeToUnitialized() {
        if (mInitialized) {
            mInitialized = false;
            List<PlayerCallback> callbacks = getPlayerCallbacks();
            if (callbacks != null) {
                for (PlayerCallback callback: callbacks) {
                    callback.onPreparedStateChanged(MediaPlayerGlue.this);
                }
            }
        }
    }

    /**
     * Release internal MediaPlayer. Should not use the object after call release().
     */
    public void release() {
        changeToUnitialized();
        mPlayer.release();
    }

    @Override
    protected void onDetachedFromHost() {
        if (getHost() instanceof SurfaceHolderGlueHost) {
            ((SurfaceHolderGlueHost) getHost()).setSurfaceHolderCallback(null);
        }
        reset();
        release();
        super.onDetachedFromHost();
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
        secondaryActionsAdapter.add(mRepeatAction);
        secondaryActionsAdapter.add(mThumbsDownAction);
        secondaryActionsAdapter.add(mThumbsUpAction);
    }

    /**
     * @see MediaPlayer#setDisplay(SurfaceHolder)
     */
    public void setDisplay(SurfaceHolder surfaceHolder) {
        mPlayer.setDisplay(surfaceHolder);
    }

    @Override
    public void enableProgressUpdating(final boolean enabled) {
        if (mRunnable != null) mHandler.removeCallbacks(mRunnable);
        if (!enabled) {
            return;
        }
        if (mRunnable == null) {
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    updateProgress();
                    mHandler.postDelayed(this, getUpdatePeriod());
                }
            };
        }
        mHandler.postDelayed(mRunnable, getUpdatePeriod());
    }

    @Override
    public void onActionClicked(Action action) {
        // If either 'Shuffle' or 'Repeat' has been clicked we need to make sure the actions index
        // is incremented and the UI updated such that we can display the new state.
        super.onActionClicked(action);
        if (action instanceof PlaybackControlsRow.RepeatAction) {
            ((PlaybackControlsRow.RepeatAction) action).nextIndex();
        } else if (action == mThumbsUpAction) {
            if (mThumbsUpAction.getIndex() == PlaybackControlsRow.ThumbsAction.INDEX_SOLID) {
                mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_OUTLINE);
            } else {
                mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_SOLID);
                mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_OUTLINE);
            }
        } else if (action == mThumbsDownAction) {
            if (mThumbsDownAction.getIndex() == PlaybackControlsRow.ThumbsAction.INDEX_SOLID) {
                mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_OUTLINE);
            } else {
                mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_SOLID);
                mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsAction.INDEX_OUTLINE);
            }
        }
        onMetadataChanged();
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        // This method is overridden in order to make implement fast forwarding and rewinding when
        // the user keeps the corresponding action pressed.
        // We only consume DPAD_CENTER Action_DOWN events on the Fast-Forward and Rewind action and
        // only if it has not been pressed in the last X milliseconds.
        boolean consume = mSelectedAction instanceof PlaybackControlsRow.RewindAction;
        consume = consume || mSelectedAction instanceof PlaybackControlsRow.FastForwardAction;
        consume = consume && mInitialized;
        consume = consume && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER;
        consume = consume && event.getAction() == KeyEvent.ACTION_DOWN;
        consume = consume && System
                .currentTimeMillis() - mLastKeyDownEvent > FAST_FORWARD_REWIND_REPEAT_DELAY;

        if (consume) {
            mLastKeyDownEvent = System.currentTimeMillis();
            int newPosition = getCurrentPosition() + FAST_FORWARD_REWIND_STEP;
            if (mSelectedAction instanceof PlaybackControlsRow.RewindAction) {
                newPosition = getCurrentPosition() - FAST_FORWARD_REWIND_STEP;
            }
            // Make sure the new calculated duration is in the range 0 >= X >= MediaDuration
            if (newPosition < 0) newPosition = 0;
            if (newPosition > getMediaDuration()) newPosition = getMediaDuration();
            seekTo(newPosition);
            return true;
        }

        return super.onKey(v, keyCode, event);
    }

    @Override
    public boolean hasValidMedia() {
        return mTitle != null && (mMediaSourcePath != null || mMediaSourceUri != null);
    }

    @Override
    public boolean isMediaPlaying() {
        return mInitialized && mPlayer.isPlaying();
    }

    @Override
    public boolean isPlaying() {
        return isMediaPlaying();
    }

    @Override
    public CharSequence getMediaTitle() {
        return mTitle != null ? mTitle : "N/a";
    }

    @Override
    public CharSequence getMediaSubtitle() {
        return mArtist != null ? mArtist : "N/a";
    }

    @Override
    public int getMediaDuration() {
        return mInitialized ? mPlayer.getDuration() : 0;
    }

    @Override
    public Drawable getMediaArt() {
        return mCover;
    }

    @Override
    public long getSupportedActions() {
        return PlaybackControlGlue.ACTION_PLAY_PAUSE
                | PlaybackControlGlue.ACTION_FAST_FORWARD
                | PlaybackControlGlue.ACTION_REWIND;
    }

    @Override
    public int getCurrentSpeedId() {
        // 0 = Pause, 1 = Normal Playback Speed
        return isMediaPlaying() ? 1 : 0;
    }

    @Override
    public int getCurrentPosition() {
        return mInitialized ? mPlayer.getCurrentPosition() : 0;
    }

    @Override
    public void play(int speed) {
        if (!mInitialized || mPlayer.isPlaying()) {
            return;
        }
        mPlayer.start();
        onMetadataChanged();
        onStateChanged();
        updateProgress();
    }

    @Override
    public void pause() {
        if (isMediaPlaying()) {
            mPlayer.pause();
            onStateChanged();
        }
    }

    /**
     * Sets the playback mode. It currently support no repeat, repeat once and infinite
     * loop mode.
     */
    public void setMode(int mode) {
        switch(mode) {
            case NO_REPEAT:
                mOnCompletionListener = null;
                break;
            case REPEAT_ONE:
                mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
                    public boolean mFirstRepeat;

                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        if (!mFirstRepeat) {
                            mFirstRepeat = true;
                            mediaPlayer.setOnCompletionListener(null);
                        }
                        play();
                    }
                };
                break;
            case REPEAT_ALL:
                mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        play();
                    }
                };
                break;
        }
    }

    /**
     * Called whenever the user presses fast-forward/rewind or when the user keeps the
     * corresponding action pressed.
     *
     * @param newPosition The new position of the media track in milliseconds.
     */
    protected void seekTo(int newPosition) {
        if (!mInitialized) {
            return;
        }
        mPlayer.seekTo(newPosition);
    }

    /**
     * Sets the media source of the player witha given URI.
     *
     * @return Returns <code>true</code> if uri represents a new media; <code>false</code>
     * otherwise.
     * @see MediaPlayer#setDataSource(String)
     */
    public boolean setMediaSource(Uri uri) {
        if (mMediaSourceUri != null ? mMediaSourceUri.equals(uri) : uri == null) {
            return false;
        }
        mMediaSourceUri = uri;
        mMediaSourcePath = null;
        prepareMediaForPlaying();
        return true;
    }

    /**
     * Sets the media source of the player with a String path URL.
     *
     * @return Returns <code>true</code> if path represents a new media; <code>false</code>
     * otherwise.
     * @see MediaPlayer#setDataSource(String)
     */
    public boolean setMediaSource(String path) {
        if (mMediaSourcePath != null ? mMediaSourcePath.equals(path) : path == null) {
            return false;
        }
        mMediaSourceUri = null;
        mMediaSourcePath = path;
        prepareMediaForPlaying();
        return true;
    }

    private void prepareMediaForPlaying() {
        reset();
        try {
            if (mMediaSourceUri != null) {
                mPlayer.setDataSource(getContext(), mMediaSourceUri);
            } else if (mMediaSourcePath != null) {
                mPlayer.setDataSource(mMediaSourcePath);
            } else {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mInitialized = true;
                List<PlayerCallback> callbacks = getPlayerCallbacks();
                if (callbacks != null) {
                    for (PlayerCallback callback: callbacks) {
                        callback.onPreparedStateChanged(MediaPlayerGlue.this);
                    }
                }
            }
        });

        if (mOnCompletionListener != null) {
            mPlayer.setOnCompletionListener(mOnCompletionListener);
        }

        mPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                if (getControlsRow() == null) {
                    return;
                }
                getControlsRow().setBufferedProgress((int) (mp.getDuration() * (percent / 100f)));
            }
        });
        mPlayer.prepareAsync();
        onStateChanged();
    }

    /**
     * This is a listener implementation for the {@link OnItemViewSelectedListener}.
     * This implementation is required in order to detect KEY_DOWN events
     * on the {@link androidx.leanback.widget.PlaybackControlsRow.FastForwardAction} and
     * {@link androidx.leanback.widget.PlaybackControlsRow.RewindAction}. Thus you
     * should <u>NOT</u> set another {@link OnItemViewSelectedListener} on your
     * Fragment. Instead, override this method and call its super (this)
     * implementation.
     *
     * @see OnItemViewSelectedListener#onItemSelected(
     *Presenter.ViewHolder, Object, RowPresenter.ViewHolder, Object)
     */
    @Override
    public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                               RowPresenter.ViewHolder rowViewHolder, Row row) {
        if (item instanceof Action) {
            mSelectedAction = (Action) item;
        } else {
            mSelectedAction = null;
        }
    }

    @Override
    public boolean isPrepared() {
        return mInitialized;
    }

    /**
     * Implements {@link SurfaceHolder.Callback} that can then be set on the
     * {@link PlaybackGlueHost}.
     */
    class VideoPlayerSurfaceHolderCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            setDisplay(surfaceHolder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            setDisplay(null);
        }
    }
}
