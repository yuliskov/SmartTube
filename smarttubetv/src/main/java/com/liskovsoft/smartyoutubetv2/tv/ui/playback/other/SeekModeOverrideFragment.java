package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.os.Bundle;
import androidx.leanback.widget.PlaybackSeekDataProvider;
import androidx.leanback.widget.PlaybackSeekUi;
import com.liskovsoft.sharedutils.helpers.Helpers;

/**
 * Disables this behavior when seeking: <em>Show or hide other rows other than PlaybackRow.</em>
 */
public class SeekModeOverrideFragment extends VideoEventsOverrideFragment {
    private PlaybackSeekUi.Client mSeekUiClient2;

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

    final PlaybackSeekUi.Client mChainedClient2 = new PlaybackSeekUi.Client() {
        @Override
        public boolean isSeekEnabled() {
            return mSeekUiClient2 == null ? false : mSeekUiClient2.isSeekEnabled();
        }

        @Override
        public void onSeekStarted() {
            if (mSeekUiClient2 != null) {
                mSeekUiClient2.onSeekStarted();
            }
            // Show or hide other rows other than PlaybackRow.
            //setSeekMode(true);
        }

        @Override
        public PlaybackSeekDataProvider getPlaybackSeekDataProvider() {
            return mSeekUiClient2 == null ? null : mSeekUiClient2.getPlaybackSeekDataProvider();
        }

        @Override
        public void onSeekPositionChanged(long pos) {
            if (mSeekUiClient2 != null) {
                mSeekUiClient2.onSeekPositionChanged(pos);
            }
        }

        @Override
        public void onSeekFinished(boolean cancelled) {
            if (mSeekUiClient2 != null) {
                mSeekUiClient2.onSeekFinished(cancelled);
            }
            // Show or hide other rows other than PlaybackRow.
            //setSeekMode(false);
        }
    };
}
