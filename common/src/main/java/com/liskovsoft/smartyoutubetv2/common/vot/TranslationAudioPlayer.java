package com.liskovsoft.smartyoutubetv2.common.vot;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TranslationAudioPlayer implements Player.EventListener {
    private static final String TAG = TranslationAudioPlayer.class.getSimpleName();

    public interface OnReadyListener {
        void onReady();
        void onError(String message);
    }

    private final Context mContext;
    private SimpleExoPlayer mPlayer;
    @Nullable
    private OnReadyListener mOnReadyListener;
    private boolean mReadyFired;
    private volatile double mRms;
    private volatile long mRmsAt;

    private final TeeAudioProcessor.AudioBufferSink mLevelSink = new TeeAudioProcessor.AudioBufferSink() {
        private volatile int mEncoding;

        @Override public void flush(int sampleRateHz, int channelCount, int encoding) {
            mEncoding = encoding;
            mRms = 0;
            mRmsAt = 0;
        }

        @Override public void handleBuffer(ByteBuffer buffer) {
            if (mEncoding != C.ENCODING_PCM_16BIT) return;
            ByteBuffer samples = buffer.order(ByteOrder.LITTLE_ENDIAN);
            int count = Math.min(samples.remaining() / 2, 4096);
            if (count == 0) return;
            double sum = 0;
            for (int i = 0; i < count; i++) {
                double value = samples.getShort() / 32768d;
                sum += value * value;
            }
            mRms = Math.sqrt(sum / count);
            mRmsAt = SystemClock.elapsedRealtime();
        }
    };

    public TranslationAudioPlayer(Context context) {
        mContext = context.getApplicationContext();
    }

    public void play(String url, long startPositionMs, float volume, float speed) {
        release();
        mReadyFired = false;
        String userAgent = Util.getUserAgent(mContext, "SmartTubeVOT");
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(mContext,
                new OkHttpDataSourceFactory(OkHttpManager.instance().getClient(), userAgent));
        ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(Uri.parse(url));

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(mContext) {
            @Override protected AudioProcessor[] buildAudioProcessors() {
                return new AudioProcessor[]{new TeeAudioProcessor(mLevelSink)};
            }
        };
        mPlayer = ExoPlayerFactory.newSimpleInstance(mContext, renderers, new DefaultTrackSelector());
        mPlayer.addListener(this);
        mPlayer.setVolume(volume);
        if (speed > 0f && speed != 1f) {
            mPlayer.setPlaybackParameters(new PlaybackParameters(speed, 1f));
        }
        mPlayer.setPlayWhenReady(false);
        mPlayer.prepare(mediaSource);
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

    public void setVolume(float volume) {
        if (mPlayer != null) mPlayer.setVolume(volume);
    }

    public boolean isReady() {
        return mPlayer != null && mPlayer.getPlaybackState() == Player.STATE_READY;
    }

    public boolean canSync() {
        return mPlayer != null && mPlayer.getPlaybackState() != Player.STATE_IDLE
                && mPlayer.getPlaybackState() != Player.STATE_ENDED;
    }

    public double getSpeechRms() {
        return SystemClock.elapsedRealtime() - mRmsAt < 300 ? mRms : 0;
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
        mRms = 0;
        mRmsAt = 0;
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
        if (mOnReadyListener != null) {
            mOnReadyListener.onError(error.getMessage());
        }
    }
}
