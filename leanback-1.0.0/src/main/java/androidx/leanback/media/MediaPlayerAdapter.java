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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.view.SurfaceHolder;

import androidx.leanback.R;

import java.io.IOException;

/**
 * This implementation extends the {@link PlayerAdapter} with a {@link MediaPlayer}.
 */
public class MediaPlayerAdapter extends PlayerAdapter {

    Context mContext;
    final MediaPlayer mPlayer = new MediaPlayer();
    SurfaceHolderGlueHost mSurfaceHolderGlueHost;
    final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            getCallback().onCurrentPositionChanged(MediaPlayerAdapter.this);
            mHandler.postDelayed(this, getProgressUpdatingInterval());
        }
    };;
    final Handler mHandler = new Handler();
    boolean mInitialized = false; // true when the MediaPlayer is prepared/initialized
    Uri mMediaSourceUri = null;
    boolean mHasDisplay;
    long mBufferedProgress;

    MediaPlayer.OnPreparedListener mOnPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            mInitialized = true;
            notifyBufferingStartEnd();
            if (mSurfaceHolderGlueHost == null || mHasDisplay) {
                getCallback().onPreparedStateChanged(MediaPlayerAdapter.this);
            }
        }
    };

    final MediaPlayer.OnCompletionListener mOnCompletionListener =
            new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            getCallback().onPlayStateChanged(MediaPlayerAdapter.this);
            getCallback().onPlayCompleted(MediaPlayerAdapter.this);
        }
    };

    final MediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener =
            new MediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mBufferedProgress = getDuration() * percent / 100;
            getCallback().onBufferedPositionChanged(MediaPlayerAdapter.this);
        }
    };

    final MediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
            getCallback().onVideoSizeChanged(MediaPlayerAdapter.this, width, height);
        }
    };

    final MediaPlayer.OnErrorListener mOnErrorListener =
            new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    getCallback().onError(MediaPlayerAdapter.this, what,
                            mContext.getString(R.string.lb_media_player_error, what, extra));
                    return MediaPlayerAdapter.this.onError(what, extra);
                }
            };

    final MediaPlayer.OnSeekCompleteListener mOnSeekCompleteListener =
            new MediaPlayer.OnSeekCompleteListener() {
                @Override
                public void onSeekComplete(MediaPlayer mp) {
                    MediaPlayerAdapter.this.onSeekComplete();
                }
            };

    final MediaPlayer.OnInfoListener mOnInfoListener = new MediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            boolean handled = false;
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    mBufferingStart = true;
                    notifyBufferingStartEnd();
                    handled = true;
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    mBufferingStart = false;
                    notifyBufferingStartEnd();
                    handled = true;
                    break;
            }
            boolean thisHandled = MediaPlayerAdapter.this.onInfo(what, extra);
            return handled || thisHandled;
        }
    };

    boolean mBufferingStart;

    void notifyBufferingStartEnd() {
        getCallback().onBufferingStateChanged(MediaPlayerAdapter.this,
                mBufferingStart || !mInitialized);
    }

    /**
     * Constructor.
     */
    public MediaPlayerAdapter(Context context) {
        mContext = context;
    }

    @Override
    public void onAttachedToHost(PlaybackGlueHost host) {
        if (host instanceof SurfaceHolderGlueHost) {
            mSurfaceHolderGlueHost = ((SurfaceHolderGlueHost) host);
            mSurfaceHolderGlueHost.setSurfaceHolderCallback(new VideoPlayerSurfaceHolderCallback());
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
            notifyBufferingStartEnd();
            if (mHasDisplay) {
                getCallback().onPreparedStateChanged(MediaPlayerAdapter.this);
            }
        }
    }

    /**
     * Release internal MediaPlayer. Should not use the object after call release().
     */
    public void release() {
        changeToUnitialized();
        mHasDisplay = false;
        mPlayer.release();
    }

    @Override
    public void onDetachedFromHost() {
        if (mSurfaceHolderGlueHost != null) {
            mSurfaceHolderGlueHost.setSurfaceHolderCallback(null);
            mSurfaceHolderGlueHost = null;
        }
        reset();
        release();
    }

    /**
     * Called to indicate an error.
     *
     * @param what    the type of error that has occurred:
     * <ul>
     * <li>{@link MediaPlayer#MEDIA_ERROR_UNKNOWN}
     * <li>{@link MediaPlayer#MEDIA_ERROR_SERVER_DIED}
     * </ul>
     * @param extra an extra code, specific to the error. Typically
     * implementation dependent.
     * <ul>
     * <li>{@link MediaPlayer#MEDIA_ERROR_IO}
     * <li>{@link MediaPlayer#MEDIA_ERROR_MALFORMED}
     * <li>{@link MediaPlayer#MEDIA_ERROR_UNSUPPORTED}
     * <li>{@link MediaPlayer#MEDIA_ERROR_TIMED_OUT}
     * <li><code>MEDIA_ERROR_SYSTEM (-2147483648)</code> - low-level system error.
     * </ul>
     * @return True if the method handled the error, false if it didn't.
     * Returning false, will cause the {@link PlayerAdapter.Callback#onPlayCompleted(PlayerAdapter)}
     * being called.
     */
    protected boolean onError(int what, int extra) {
        return false;
    }

    /**
     * Called to indicate the completion of a seek operation.
     */
    protected void onSeekComplete() {
    }

    /**
     * Called to indicate an info or a warning.
     *
     * @param what    the type of info or warning.
     * <ul>
     * <li>{@link MediaPlayer#MEDIA_INFO_UNKNOWN}
     * <li>{@link MediaPlayer#MEDIA_INFO_VIDEO_TRACK_LAGGING}
     * <li>{@link MediaPlayer#MEDIA_INFO_VIDEO_RENDERING_START}
     * <li>{@link MediaPlayer#MEDIA_INFO_BUFFERING_START}
     * <li>{@link MediaPlayer#MEDIA_INFO_BUFFERING_END}
     * <li><code>MEDIA_INFO_NETWORK_BANDWIDTH (703)</code> -
     *     bandwidth information is available (as <code>extra</code> kbps)
     * <li>{@link MediaPlayer#MEDIA_INFO_BAD_INTERLEAVING}
     * <li>{@link MediaPlayer#MEDIA_INFO_NOT_SEEKABLE}
     * <li>{@link MediaPlayer#MEDIA_INFO_METADATA_UPDATE}
     * <li>{@link MediaPlayer#MEDIA_INFO_UNSUPPORTED_SUBTITLE}
     * <li>{@link MediaPlayer#MEDIA_INFO_SUBTITLE_TIMED_OUT}
     * </ul>
     * @param extra an extra code, specific to the info. Typically
     * implementation dependent.
     * @return True if the method handled the info, false if it didn't.
     * Returning false, will cause the info to be discarded.
     */
    protected boolean onInfo(int what, int extra) {
        return false;
    }

    /**
     * @see MediaPlayer#setDisplay(SurfaceHolder)
     */
    void setDisplay(SurfaceHolder surfaceHolder) {
        boolean hadDisplay = mHasDisplay;
        mHasDisplay = surfaceHolder != null;
        if (hadDisplay == mHasDisplay) {
            return;
        }
        mPlayer.setDisplay(surfaceHolder);
        if (mHasDisplay) {
            if (mInitialized) {
                getCallback().onPreparedStateChanged(MediaPlayerAdapter.this);
            }
        } else {
            if (mInitialized) {
                getCallback().onPreparedStateChanged(MediaPlayerAdapter.this);
            }
        }

    }

    @Override
    public void setProgressUpdatingEnabled(final boolean enabled) {
        mHandler.removeCallbacks(mRunnable);
        if (!enabled) {
            return;
        }
        mHandler.postDelayed(mRunnable, getProgressUpdatingInterval());
    }

    /**
     * Return updating interval of progress UI in milliseconds. Subclass may override.
     * @return Update interval of progress UI in milliseconds.
     */
    public int getProgressUpdatingInterval() {
        return 16;
    }

    @Override
    public boolean isPlaying() {
        return mInitialized && mPlayer.isPlaying();
    }

    @Override
    public long getDuration() {
        return mInitialized ? mPlayer.getDuration() : -1;
    }

    @Override
    public long getCurrentPosition() {
        return mInitialized ? mPlayer.getCurrentPosition() : -1;
    }

    @Override
    public void play() {
        if (!mInitialized || mPlayer.isPlaying()) {
            return;
        }
        mPlayer.start();
        getCallback().onPlayStateChanged(MediaPlayerAdapter.this);
        getCallback().onCurrentPositionChanged(MediaPlayerAdapter.this);
    }

    @Override
    public void pause() {
        if (isPlaying()) {
            mPlayer.pause();
            getCallback().onPlayStateChanged(MediaPlayerAdapter.this);
        }
    }

    @Override
    public void seekTo(long newPosition) {
        if (!mInitialized) {
            return;
        }
        mPlayer.seekTo((int) newPosition);
    }

    @Override
    public long getBufferedPosition() {
        return mBufferedProgress;
    }

    /**
     * Sets the media source of the player witha given URI.
     *
     * @return Returns <code>true</code> if uri represents a new media; <code>false</code>
     * otherwise.
     * @see MediaPlayer#setDataSource(String)
     */
    public boolean setDataSource(Uri uri) {
        if (mMediaSourceUri != null ? mMediaSourceUri.equals(uri) : uri == null) {
            return false;
        }
        mMediaSourceUri = uri;
        prepareMediaForPlaying();
        return true;
    }

    private void prepareMediaForPlaying() {
        reset();
        try {
            if (mMediaSourceUri != null) {
                mPlayer.setDataSource(mContext, mMediaSourceUri);
            } else {
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mPlayer.setOnPreparedListener(mOnPreparedListener);
        mPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener);
        mPlayer.setOnErrorListener(mOnErrorListener);
        mPlayer.setOnSeekCompleteListener(mOnSeekCompleteListener);
        mPlayer.setOnCompletionListener(mOnCompletionListener);
        mPlayer.setOnInfoListener(mOnInfoListener);
        mPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        notifyBufferingStartEnd();
        mPlayer.prepareAsync();
        getCallback().onPlayStateChanged(MediaPlayerAdapter.this);
    }

    /**
     * Return the MediaPlayer associated with the MediaPlayerAdapter. App can use the instance
     * to config DRM or control volumes, etc. Warning: App should not use the following seven
     * listeners as they are controlled by MediaPlayerAdapter. If that's the case, app should write
     * its own PlayerAdapter class.
     * {@link MediaPlayer#setOnPreparedListener}
     * {@link MediaPlayer#setOnVideoSizeChangedListener}
     * {@link MediaPlayer#setOnErrorListener}
     * {@link MediaPlayer#setOnSeekCompleteListener}
     * {@link MediaPlayer#setOnCompletionListener}
     * {@link MediaPlayer#setOnInfoListener}
     * {@link MediaPlayer#setOnBufferingUpdateListener}
     *
     * @return The MediaPlayer associated with the MediaPlayerAdapter.
     */
    public final MediaPlayer getMediaPlayer() {
        return mPlayer;
    }

    /**
     * @return True if MediaPlayer OnPreparedListener is invoked and got a SurfaceHolder if
     * {@link PlaybackGlueHost} provides SurfaceHolder.
     */
    @Override
    public boolean isPrepared() {
        return mInitialized && (mSurfaceHolderGlueHost == null || mHasDisplay);
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
