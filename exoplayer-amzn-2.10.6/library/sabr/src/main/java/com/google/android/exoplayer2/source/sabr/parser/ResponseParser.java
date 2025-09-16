package com.google.android.exoplayer2.source.sabr.parser;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;

public class ResponseParser {
    private final ExtractorInput extractorInput;

    public ResponseParser(ExtractorInput extractorInput) {
        this.extractorInput = extractorInput;
    }

    public SabrPart parse() {
        return null;
    }
}
