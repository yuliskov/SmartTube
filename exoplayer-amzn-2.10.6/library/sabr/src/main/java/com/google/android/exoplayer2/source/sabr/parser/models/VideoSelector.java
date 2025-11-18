package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

public class VideoSelector extends FormatSelector {
    public VideoSelector(String displayName, boolean discardMedia) {
        super(displayName, discardMedia);
    }

    public VideoSelector(String displayName, boolean discardMedia, FormatId... formatIds) {
        super(displayName, discardMedia, formatIds);
    }

    @Override
    public String getMimePrefix() {
        return "video";
    }
}
