package com.google.android.exoplayer2.source.sabr.parser.adapter;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.sabr.parser.core.SabrStream;

import java.io.IOException;

public class SabrMatroskaAdapter extends MatroskaExtractor {
    private final SabrStream sabrStream;

    public SabrMatroskaAdapter(SabrStream sabrStream) {
        this.sabrStream = sabrStream;
    }

    public SabrMatroskaAdapter(int flags, SabrStream sabrStream) {
        super(flags);
        this.sabrStream = sabrStream;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        return super.read(input, seekPosition);
    }
}
