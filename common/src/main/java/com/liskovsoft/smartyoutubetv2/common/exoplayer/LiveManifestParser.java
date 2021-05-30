package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.EventStream;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.ProgramInformation;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.Representation.MultiSegmentRepresentation;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentList;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentTemplate;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentTimelineElement;
import com.google.android.exoplayer2.source.dash.manifest.UrlTemplate;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class LiveManifestParser extends DashManifestParser {
    private DashManifest mManifest;
    private long mOldSegmentNum;

    @Override
    public DashManifest parse(Uri uri, InputStream inputStream) throws IOException {
        DashManifest manifest = super.parse(uri, inputStream);

        updateManifest(manifest);

        return mManifest;
    }

    //@Override
    //protected DashManifest parseMediaPresentationDescription(XmlPullParser xpp, String baseUrl) throws XmlPullParserException, IOException {
    //    return super.parseMediaPresentationDescription(xpp, baseUrl);
    //}
    //
    //@Override
    //protected DashManifest buildMediaPresentationDescription(long availabilityStartTime, long durationMs, long minBufferTimeMs, boolean dynamic,
    //                                                         long minUpdateTimeMs, long timeShiftBufferDepthMs, long suggestedPresentationDelayMs,
    //                                                         long publishTimeMs, ProgramInformation programInformation, UtcTimingElement utcTiming,
    //                                                         Uri location, List<Period> periods) {
    //    return super.buildMediaPresentationDescription(availabilityStartTime, durationMs, minBufferTimeMs, dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, suggestedPresentationDelayMs, publishTimeMs, programInformation, utcTiming, location, periods);
    //}
    //
    //@Override
    //protected SegmentList parseSegmentList(XmlPullParser xpp, SegmentList parent) throws XmlPullParserException, IOException {
    //    return super.parseSegmentList(xpp, parent);
    //}
    //
    //@Override
    //protected SegmentList buildSegmentList(RangedUri initialization, long timescale, long presentationTimeOffset, long startNumber, long duration, List<SegmentTimelineElement> timeline, List<RangedUri> segments) {
    //    return super.buildSegmentList(initialization, timescale, presentationTimeOffset, startNumber, duration, timeline, segments);
    //}
    //
    //@Override
    //protected SegmentTemplate buildSegmentTemplate(RangedUri initialization, long timescale, long presentationTimeOffset, long startNumber,
    //                                               long endNumber, long duration, List<SegmentTimelineElement> timeline,
    //                                               UrlTemplate initializationTemplate, UrlTemplate mediaTemplate) {
    //    return super.buildSegmentTemplate(initialization, timescale, presentationTimeOffset, startNumber, endNumber, duration, timeline, initializationTemplate, mediaTemplate);
    //}
    //
    //@Override
    //protected Period buildPeriod(String id, long startMs, List<AdaptationSet> adaptationSets, List<EventStream> eventStreams) {
    //    return super.buildPeriod(id, 0, adaptationSets, eventStreams);
    //}

    private static long getFirstSegmentNum(DashManifest manifest) {
        return manifest.getPeriod(0).adaptationSets.get(0).representations.get(0).getIndex().getFirstSegmentNum();
    }

    private void updateManifest(DashManifest newManifest) {
        if (newManifest == null) {
            return;
        }

        long newSegmentNum = getFirstSegmentNum(newManifest);

        if (newSegmentNum == 0) { // Short stream. No need to do something special.
            mManifest = newManifest;
            return;
        }

        if (mManifest == null) {
            //newManifest.availabilityStartTimeMs = -1;
            Period newPeriod = newManifest.getPeriod(0);
            newPeriod.startMs = 0;
            mOldSegmentNum = newSegmentNum;

            for (int i = 0; i < newPeriod.adaptationSets.size(); i++) {
                for (int j = 0; j < newPeriod.adaptationSets.get(i).representations.size(); j++) {
                    MultiSegmentRepresentation representation = (MultiSegmentRepresentation) newPeriod.adaptationSets.get(i).representations.get(j);
                    //representation.presentationTimeOffsetUs = 0;
                    SegmentList newSegmentList = (SegmentList) representation.segmentBase;
                    newSegmentList.presentationTimeOffset = 0;
                    newSegmentList.startNumber = 0;
                }
            }

            mManifest = newManifest;

            return;
        }

        //long oldSegmentNum = getFirstSegmentNum(mManifest);

        Period oldPeriod = mManifest.getPeriod(0);
        Period newPeriod = newManifest.getPeriod(0);

        for (int i = 0; i < oldPeriod.adaptationSets.size(); i++) {
            for (int j = 0; j < oldPeriod.adaptationSets.get(i).representations.size(); j++) {
                updateRepresentation(
                        oldPeriod.adaptationSets.get(i).representations.get(j),
                        newPeriod.adaptationSets.get(i).representations.get(j),
                        newSegmentNum - mOldSegmentNum
                );
            }
        }

        mOldSegmentNum = newSegmentNum;

        //mManifest.timeShiftBufferDepthMs += (newSegmentNum - oldSegmentNum) * 5_000;
    }

    private static void updateRepresentation(Representation representation1, Representation representation2, long segmentNumShift) {
        if (segmentNumShift <= 0) {
            return;
        }

        MultiSegmentRepresentation oldRepresentation = (MultiSegmentRepresentation) representation1;
        MultiSegmentRepresentation newRepresentation = (MultiSegmentRepresentation) representation2;

        SegmentList oldSegmentList = (SegmentList) oldRepresentation.segmentBase;
        SegmentList newSegmentList = (SegmentList) newRepresentation.segmentBase;

        oldSegmentList.mediaSegments.addAll(
                newSegmentList.mediaSegments.subList(newSegmentList.mediaSegments.size() - (int) segmentNumShift, newSegmentList.mediaSegments.size()));

        // segmentTimeline is the same for all segments
        if (oldSegmentList.mediaSegments.size() != oldSegmentList.segmentTimeline.size()) {
            SegmentTimelineElement lastTimeline = oldSegmentList.segmentTimeline.get(oldSegmentList.segmentTimeline.size() - 1);

            for (int i = 1; i <= segmentNumShift; i++) {
                oldSegmentList.segmentTimeline.add(new SegmentTimelineElement(lastTimeline.startTime + (lastTimeline.duration * i), lastTimeline.duration));
            }

            //oldSegmentList.segmentTimeline.addAll(
            //        newSegmentList.segmentTimeline.subList(newSegmentList.segmentTimeline.size() - (int) segmentNumShift - 1, newSegmentList.segmentTimeline.size()));
        }
    }
}
