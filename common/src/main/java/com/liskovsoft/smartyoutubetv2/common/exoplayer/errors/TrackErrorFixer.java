package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;

import java.util.Set;

public class TrackErrorFixer {
    private final TrackSelectorManager mTrackSelectorManager;

    public TrackErrorFixer(TrackSelectorManager trackSelectorManager) {
        mTrackSelectorManager = trackSelectorManager;
    }

    public boolean fixError(ExoPlaybackException error) {
        if (error.type != ExoPlaybackException.TYPE_SOURCE) {
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
            return true;
        }

        return false;
    }
}
