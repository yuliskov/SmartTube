package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.EventStream;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.ProgramInformation;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentList;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentTemplate;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentTimelineElement;
import com.google.android.exoplayer2.source.dash.manifest.UrlTemplate;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

public class LiveManifestParser extends DashManifestParser {
    private long mPresentationTimeOffsetMs;
    private long mStartNumber;

    @Override
    protected DashManifest parseMediaPresentationDescription(XmlPullParser xpp, String baseUrl) throws XmlPullParserException, IOException {
        return super.parseMediaPresentationDescription(xpp, baseUrl);
    }

    @Override
    protected DashManifest buildMediaPresentationDescription(long availabilityStartTime, long durationMs, long minBufferTimeMs, boolean dynamic,
                                                             long minUpdateTimeMs, long timeShiftBufferDepthMs, long suggestedPresentationDelayMs,
                                                             long publishTimeMs, ProgramInformation programInformation, UtcTimingElement utcTiming,
                                                             Uri location, List<Period> periods) {
        return super.buildMediaPresentationDescription(availabilityStartTime, durationMs, minBufferTimeMs, dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, suggestedPresentationDelayMs, publishTimeMs, programInformation, utcTiming, location, periods);
    }

    @Override
    protected SegmentList parseSegmentList(XmlPullParser xpp, SegmentList parent) throws XmlPullParserException, IOException {
        return super.parseSegmentList(xpp, parent);
    }

    //@Override
    //protected SegmentList buildSegmentList(RangedUri initialization, long timescale, long presentationTimeOffset, long startNumber, long duration, List<SegmentTimelineElement> timeline, List<RangedUri> segments) {
    //    if (presentationTimeOffset > 0) {
    //        mPresentationTimeOffsetMs = presentationTimeOffset;
    //    }
    //
    //    if (startNumber > 0) {
    //        mStartNumber = startNumber;
    //    }
    //
    //    return super.buildSegmentList(initialization, timescale, 0, 0, duration, timeline, segments);
    //}
    //
    //@Override
    //protected SegmentTemplate buildSegmentTemplate(RangedUri initialization, long timescale, long presentationTimeOffset, long startNumber,
    //                                               long endNumber, long duration, List<SegmentTimelineElement> timeline,
    //                                               UrlTemplate initializationTemplate, UrlTemplate mediaTemplate) {
    //    return super.buildSegmentTemplate(initialization, timescale, 0, 0, endNumber, duration, timeline, initializationTemplate, mediaTemplate);
    //}

    @Override
    protected Period buildPeriod(String id, long startMs, List<AdaptationSet> adaptationSets, List<EventStream> eventStreams) {
        return super.buildPeriod(id, 0, adaptationSets, eventStreams);
    }
}
