package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

public class AudioSelector extends FormatSelector {
    public AudioSelector(String displayName, boolean discardMedia) {
        super(displayName, discardMedia);
    }

    public AudioSelector(String displayName, boolean discardMedia, FormatId... formatIds) {
        super(displayName, discardMedia, formatIds);
    }

    public AudioSelector(String displayName, boolean discardMedia, Format... selectedFormats) {
        super(displayName, discardMedia, selectedFormats);
    }

    @Override
    public String getMimePrefix() {
        return "audio";
    }
}
