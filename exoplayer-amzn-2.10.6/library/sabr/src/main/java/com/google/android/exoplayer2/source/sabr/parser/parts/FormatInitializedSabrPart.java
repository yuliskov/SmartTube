package com.google.android.exoplayer2.source.sabr.parser.parts;

import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;

public class FormatInitializedSabrPart implements SabrPart {
    public final FormatId formatId;
    public final FormatSelector formatSelector;

    public FormatInitializedSabrPart(FormatId formatId, FormatSelector formatSelector) {
        this.formatId = formatId;
        this.formatSelector = formatSelector;
    }
}
