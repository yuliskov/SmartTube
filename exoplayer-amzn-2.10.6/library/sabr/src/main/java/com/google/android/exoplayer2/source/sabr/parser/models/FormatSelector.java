package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatId;

import java.util.ArrayList;
import java.util.List;

public class FormatSelector {
    public final String displayName;
    public final List<FormatId> formatIds = new ArrayList<>();
    public final boolean discardMedia;

    public FormatSelector(String displayName, boolean discardMedia) {
        this.displayName = displayName;
        this.discardMedia = discardMedia;
    }

    public String getMimePrefix() {
        return null;
    }

    public boolean match(FormatId formatId, String mimeType) {
        return formatIds.contains(formatId)
                || (formatIds.isEmpty() && getMimePrefix() != null && mimeType != null && mimeType.toLowerCase().startsWith(getMimePrefix()));
    }

    public boolean isDiscardMedia() {
        return discardMedia;
    }
}
