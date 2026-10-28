package com.liskovsoft.smartyoutubetv2.common.vot;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.mylogger.Log;

public class TranslationAudioPlayer implements Player.EventListener {
    private static final String TAG = TranslationAudioPlayer.class.getSimpleName();

    public interface OnReadyListener {
        void onReady();
    }

    private final Context mContext;
    private SimpleExoPlayer mPlayer;
    @Nullable
    private OnReadyListener mOnReadyListener;
    private boolean mReadyFired;

    public TranslationAudioPlayer(Context context) {
        mContext = context.getApplicationContext();
    }

    public void play(String url, long startPositionMs, float volume, float speed) {
        release();
        mReadyFired = false;
        String userAgent = Util.getUserAgent(mContext, "SmartTubeVOT");
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(mContext, userAgent);
        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse(url));

        mPlayer = ExoPlayerFactory.newSimpleInstance(mContext);
        mPlayer.addListener(this);
        mPlayer.setVolume(volume);
        if (speed > 0f && speed != 1f) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1f));
        }
        mPlayer.prepare(mediaSource);
        mPlayer.setPlayWhenReady(true);
        if (startPositionMs > 0) {
            mPlayer.seekTo(startPositionMs);
        }
    }

    public void setOnReadyListener(@Nullable OnReadyListener listener) {
        mOnReadyListener = listener;
    }

    public void setPlaybackSpeed(float speed) {
        if (mPlayer == null) {
            return;
        }
        float s = speed > 0f ? speed : 1f;
        mPlayer.setPlaybackParameters(new PlaybackParameters(s, 1f));
    }

    public boolean isReady() {
        return mPlayer != null && mPlayer.getPlaybackState() == Player.STATE_READY;
    }

    public void seekTo(long positionMs) {
        if (mPlayer != null) {
            mPlayer.seekTo(positionMs);
        }
    }

    public long getPositionMs() {
        return mPlayer != null ? mPlayer.getCurrentPosition() : 0;
    }

    public void pause() {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(false);
        }
    }

    public void resume() {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(true);
        }
    }

    public void release() {
        if (mPlayer != null) {
            mPlayer.removeListener(this);
            mPlayer.release();
            mPlayer = null;
        }
        mReadyFired = false;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (playbackState == Player.STATE_READY && !mReadyFired) {
            mReadyFired = true;
            if (mOnReadyListener != null) {
                mOnReadyListener.onReady();
            }
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, "Translation player error: %s", error.getMessage());
    }
}
