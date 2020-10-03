package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorUtil;

public abstract class MediaTrack {
    public Format format;
    public int groupIndex = -1;
    public int trackIndex = -1;
    public boolean isSelected;

    public int rendererIndex;

    public MediaTrack(int rendererIndex) {
        this.rendererIndex = rendererIndex;
    }

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

    public static boolean codecEquals(String codecs1, String codecs2) {
        if (codecs1 == null || codecs2 == null) {
            return false;
        }

        return Helpers.equals(TrackSelectorUtil.codecNameShort(codecs1), TrackSelectorUtil.codecNameShort(codecs2));
    }
}
