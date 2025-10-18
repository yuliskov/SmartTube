package com.google.android.exoplayer2.source.sabr.parser.models;

public class AudioSelector extends FormatSelector {
    public AudioSelector(String displayName, boolean discardMedia) {
        super(displayName, discardMedia);
    }

    @Override
    public String getMimePrefix() {
        return "audio";
    }
}
