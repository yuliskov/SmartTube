package com.google.android.exoplayer2.source.sabr.parser.models;

import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FormatSelector {
    public final String displayName;
    public final List<FormatId> formatIds = new ArrayList<>();
    public final boolean discardMedia;

    public FormatSelector(String displayName, boolean discardMedia) {
        this(displayName, discardMedia, (FormatId[]) null);
    }

    public FormatSelector(String displayName, boolean discardMedia, FormatId... formatIds) {
        this.displayName = displayName;
        this.discardMedia = discardMedia;

        if (formatIds != null) {
            this.formatIds.addAll(Arrays.asList(formatIds));
        }
    }

    public String getMimePrefix() {
        return null;
    }

    public boolean match(FormatId formatId, String mimeType) {
        return formatIds.contains(formatId)
                || (formatIds.isEmpty() && getMimePrefix() != null && mimeType != null && mimeType.toLowerCase().startsWith(getMimePrefix()))
                || Helpers.findFirst(formatIds, fmt -> fmt.hasItag() && formatId.hasItag() && fmt.getItag() == formatId.getItag()) != null;
    }

    public boolean isDiscardMedia() {
        return discardMedia;
    }
}
