package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

import java.util.List;

public class CaptionSelector extends FormatSelector {
    public CaptionSelector(String displayName, boolean discardMedia) {
        super(displayName, discardMedia);
    }

    public CaptionSelector(String displayName, boolean discardMedia, FormatId... formatIds) {
        super(displayName, discardMedia, formatIds);
    }

    public CaptionSelector(String displayName, boolean discardMedia, Format... selectedFormats) {
        super(displayName, discardMedia, selectedFormats);
    }

    @Override
    public String getMimePrefix() {
        return "text";
    }
}
