package com.google.android.exoplayer2.source.sabr.parser.processor;

import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;

public class ProcessMediaHeaderResult {
    public final SabrPart sabrPart;

    public ProcessMediaHeaderResult() {
        this(null);
    }

    public ProcessMediaHeaderResult(SabrPart sabrPart) {
        this.sabrPart = sabrPart;
    }
}
