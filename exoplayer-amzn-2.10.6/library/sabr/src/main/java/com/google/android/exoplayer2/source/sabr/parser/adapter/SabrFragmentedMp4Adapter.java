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
import com.google.android.exoplayer2.util.TimestampAdjuster;

import java.io.IOException;
import java.util.List;

public class SabrFragmentedMp4Adapter extends FragmentedMp4Extractor {
    private final SabrStream sabrStream;

    public SabrFragmentedMp4Adapter(SabrStream sabrStream) {
        this.sabrStream = sabrStream;
    }

    public SabrFragmentedMp4Adapter(int flags, SabrStream sabrStream) {
        super(flags);
        this.sabrStream = sabrStream;
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster);
        this.sabrStream = sabrStream;
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData);
        this.sabrStream = sabrStream;
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            List<Format> closedCaptionFormats,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats);
        this.sabrStream = sabrStream;
    }

    public SabrFragmentedMp4Adapter(
            int flags,
            @Nullable TimestampAdjuster timestampAdjuster,
            @Nullable Track sideloadedTrack,
            @Nullable DrmInitData sideloadedDrmInitData,
            List<Format> closedCaptionFormats,
            @Nullable TrackOutput additionalEmsgTrackOutput,
            SabrStream sabrStream) {
        super(flags, timestampAdjuster, sideloadedTrack, sideloadedDrmInitData, closedCaptionFormats, additionalEmsgTrackOutput);
        this.sabrStream = sabrStream;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition)
            throws IOException, InterruptedException {
        return super.read(input, seekPosition);
    }
}
