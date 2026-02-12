package com.google.android.exoplayer2.source.sabr.parser.adapter;

import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

public class SabrMatroskaAdapter extends MatroskaExtractor {
    private static final String TAG = SabrMatroskaAdapter.class.getSimpleName();
    private final SabrExtractorInput extractorInput;

    public SabrMatroskaAdapter(SabrStream sabrStream) {
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrMatroskaAdapter(int flags, SabrStream sabrStream) {
        super(flags);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        int result = RESULT_END_OF_INPUT;

        try {
            extractorInput.init(input);
            result = super.read(extractorInput, seekPosition);
        } finally {
            if (result != RESULT_CONTINUE) {
                Log.e(TAG, "MatroskaAdapter: disposing, result=%s", result);
                extractorInput.dispose();
            }
        }

        return result;
    }
}
