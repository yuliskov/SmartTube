package com.google.android.exoplayer2.source.sabr.parser.adapter;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.sabr.parser.core.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;

import java.io.IOException;

public class SabrMatroskaAdapter2 extends MatroskaExtractor {
    private static final String TAG = SabrMatroskaAdapter2.class.getSimpleName();
    private final SabrExtractorInput extractorInput;

    public SabrMatroskaAdapter2(SabrStream sabrStream) {
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrMatroskaAdapter2(int flags, SabrStream sabrStream) {
        super(flags);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        int result;

        try {
            extractorInput.init(input);
            result = super.read(extractorInput, seekPosition);
        } finally {
            extractorInput.dispose();
        }

        return result;
    }
}
