package com.google.android.exoplayer2.source.sabr.parser.adapter;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.source.sabr.parser.core.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.LimitedExtractorInput;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;

import java.io.IOException;

public class SabrMatroskaAdapter extends MatroskaExtractor {
    private static final String TAG = SabrMatroskaAdapter.class.getSimpleName();
    private final SabrStream sabrStream;
    private final LimitedExtractorInput limitedExtractorInput;

    public SabrMatroskaAdapter(SabrStream sabrStream) {
        this.sabrStream = sabrStream;
        this.limitedExtractorInput = new LimitedExtractorInput();
    }

    public SabrMatroskaAdapter(int flags, SabrStream sabrStream) {
        super(flags);
        this.sabrStream = sabrStream;
        this.limitedExtractorInput = new LimitedExtractorInput();
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        //Log.e(TAG, "Start SABR read: readNum=" + readNum++);

        int result = Extractor.RESULT_END_OF_INPUT;

        while (true) {
            SabrPart sabrPart = sabrStream.parse(input);

            if (sabrPart == null) {
                break;
            }

            // Debug
            //if (sabrPart instanceof MediaSegmentDataSabrPart) {
            //    MediaSegmentDataSabrPart data = (MediaSegmentDataSabrPart) sabrPart;
            //    Log.e(TAG, "Consumed contentLength: " + data.contentLength);
            //    data.data.skipFully(data.contentLength);
            //    continue;
            //}

            int nestedResult = Extractor.RESULT_CONTINUE;

            if (sabrPart instanceof MediaSegmentDataSabrPart) {
                MediaSegmentDataSabrPart data = (MediaSegmentDataSabrPart) sabrPart;
                limitedExtractorInput.init(data.data, data.contentLength); // adds input and length
                while (nestedResult == Extractor.RESULT_CONTINUE) {
                    long positionBefore = limitedExtractorInput.getPosition();
                    nestedResult = super.read(limitedExtractorInput, seekPosition);

                    // break if no progress
                    if (nestedResult == Extractor.RESULT_CONTINUE &&
                            limitedExtractorInput.getPosition() == positionBefore) {
                        break;
                    }
                }
                limitedExtractorInput.dispose(); // clears input and length
            }

            if (nestedResult == Extractor.RESULT_SEEK) {
                result = nestedResult;
                break;
            }
        }

        return result;
    }
}
