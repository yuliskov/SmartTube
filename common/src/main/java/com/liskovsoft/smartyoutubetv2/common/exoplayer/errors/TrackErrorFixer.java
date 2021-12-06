package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import java.util.Set;

public class TrackErrorFixer {
    private static final long BLACKLIST_INTERVAL_MS = 1_000;
    private final TrackSelectorManager mTrackSelectorManager;
    private long mSelectionTimeMs;

    public TrackErrorFixer(TrackSelectorManager trackSelectorManager) {
        mTrackSelectorManager = trackSelectorManager;
    }

    /**
     * Blacklisting audio track for certain live streams.<br/>
     * Last segment of such streams produce 404 error.<br/>
     * See DrLupo streams, for example.
     */
    public boolean fixError(Exception e) {
        if (!(e instanceof InvalidResponseCodeException)) {
            return false;
        }

        if (((InvalidResponseCodeException) e).responseCode != 404) {
            return false;
        }

        if (System.currentTimeMillis() - mSelectionTimeMs < BLACKLIST_INTERVAL_MS) {
            return false;
        }

        Set<MediaTrack> audioTracks = mTrackSelectorManager.getAudioTracks();
        MediaTrack tmpTrack = null;

        for (MediaTrack track : audioTracks) {
            if (!track.isSelected) {
                tmpTrack = track;
                break;
            }
        }

        if (tmpTrack != null) {
            mTrackSelectorManager.selectTrack(tmpTrack);
            mSelectionTimeMs = System.currentTimeMillis();
            return true;
        }

        return false;
    }
}
