package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;

public abstract class MediaTrack {
    public MediaTrack(int rendererIndex) {
        this.rendererIndex = rendererIndex;
    }

    public Format format;
    public int groupIndex = -1;
    public int trackIndex = -1;
    public boolean isSelected;

    public int rendererIndex;

    public abstract int compare(MediaTrack track2);

    public static MediaTrack forRendererIndex(int rendererIndex) {
        switch (rendererIndex) {
            case TrackSelectorManager.RENDERER_INDEX_VIDEO:
                return new VideoTrack(rendererIndex);
            case TrackSelectorManager.RENDERER_INDEX_AUDIO:
                return new AudioTrack(rendererIndex);
            case TrackSelectorManager.RENDERER_INDEX_SUBTITLE:
                return new SubtitleTrack(rendererIndex);
        }

        return null;
    }
}
