package com.google.android.exoplayer2.source.sabr.parser.adapter;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Track;
import com.google.android.exoplayer2.source.sabr.parser.core.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.List;

public class SabrFragmentedMp4Adapter2 extends FragmentedMp4Extractor {
    private static final String TAG = SabrFragmentedMp4Adapter2.class.getSimpleName();
    private final SabrExtractorInput extractorInput;

    public SabrFragmentedMp4Adapter2(SabrStream sabrStream) {
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter2(int flags, SabrStream sabrStream) {
        super(flags);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter2(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter2(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter2(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            List<Format> closedCaptionFormats,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats);
        this.extractorInput = new SabrExtractorInput(sabrStream);
    }

    public SabrFragmentedMp4Adapter2(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            List<Format> closedCaptionFormats,
            @Nullable TrackOutput additionalEmsgTrackOutput,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats, additionalEmsgTrackOutput);
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
                Log.e(TAG, "Mp4Adapter: disposing, result=%s", result);
                extractorInput.dispose();
            }
        }

        return result;
    }
}
