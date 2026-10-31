package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod;

import android.os.Bundle;
import android.os.Handler;
import androidx.leanback.widget.PlaybackSeekDataProvider;
import androidx.leanback.widget.PlaybackSeekUi;
import com.liskovsoft.sharedutils.helpers.Helpers;

/**
 * Disables this behavior when seeking: <em>Show or hide other rows other than PlaybackRow.</em>
 */
public class SeekModePlaybackFragment extends EventsOverridePlaybackFragment {
    private static final String TAG = SeekModePlaybackFragment.class.getSimpleName();
    private static final int START_FADE_OUT = 1;
    private PlaybackSeekUi.Client mSeekUiClient2;
    private boolean mInSeek;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Object chainedClient = Helpers.getField(this, "mChainedClient");

        if (chainedClient != null) {
            Helpers.setField(this, "mChainedClient", mChainedClient2);
        }
    }

    /**
     * Interface to be implemented by UI widget to support PlaybackSeekUi.
     */
    @Override
    public void setPlaybackSeekUiClient(PlaybackSeekUi.Client client) {
        mSeekUiClient2 = client;
    }

    private final PlaybackSeekUi.Client mChainedClient2 = new PlaybackSeekUi.Client() {
        @Override
        public boolean isSeekEnabled() {
            return mSeekUiClient2 == null ? false : mSeekUiClient2.isSeekEnabled();
        }

        @Override
        public void onSeekStarted() {
            if (mSeekUiClient2 != null) {
                mSeekUiClient2.onSeekStarted();
            }
            setSeekMode(true);
        }

        @Override
        public PlaybackSeekDataProvider getPlaybackSeekDataProvider() {
            return mSeekUiClient2 == null ? null : mSeekUiClient2.getPlaybackSeekDataProvider();
        }

        @Override
        public void onSeekPositionChanged(long positionMs) {
            if (mSeekUiClient2 != null) {
                mSeekUiClient2.onSeekPositionChanged(positionMs);
            }
            SeekModePlaybackFragment.this.onSeekPositionChanged(positionMs);
        }

        @Override
        public void onSeekFinished(boolean cancelled) {
            if (mSeekUiClient2 != null) {
                mSeekUiClient2.onSeekFinished(cancelled);
            }
            setSeekMode(false);
        }
    };

    protected void onSeekPositionChanged(long positionMs) {}

    /**
     * NOTE: MOD version. Removed part: hiding rows.<br/>
     * Show or hide other rows other than PlaybackRow.
     * @param inSeek True to make other rows visible, false to make other rows invisible.
     */
    private void setSeekMode(boolean inSeek) {
        if (mInSeek == inSeek) {
            return;
        }
        mInSeek = inSeek;
        if (mInSeek) {
            stopFadeTimer();
            // Show UI while seeking with FastForward/Rewind keys
            showControlsOverlay(false);
        }
    }

    private void stopFadeTimer() {
        Object handler = Helpers.getField(this, "mHandler");
        if (handler != null) {
            ((Handler)handler).removeMessages(START_FADE_OUT);
        }
    }
}
