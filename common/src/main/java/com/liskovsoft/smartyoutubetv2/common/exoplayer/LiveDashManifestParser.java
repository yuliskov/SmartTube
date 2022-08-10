package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.dash.DashSegmentIndex;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.Representation.MultiSegmentRepresentation;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentList;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentTimelineElement;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryString;
import com.liskovsoft.sharedutils.querystringparser.UrlQueryStringFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Supported ExoPlayer versions: 2.10.6
 */
@SuppressWarnings("unchecked")
public class LiveDashManifestParser extends DashManifestParser {
    private static final String TAG = LiveDashManifestParser.class.getSimpleName();
    private static final long MAX_STREAM_LENGTH_MS = 8 * 60 * 60 * 1_000; // 8 hours
    private DashManifest mOldManifest;
    private long mOldSegmentNum;

    @Override
    public DashManifest parse(Uri uri, InputStream inputStream) throws IOException {
        DashManifest manifest = super.parse(uri, inputStream);

        appendManifest(manifest);

        return mOldManifest;
    }

    private void appendManifest(DashManifest newManifest) {
        if (newManifest == null) {
            return;
        }

        // Optimize ram usage on short streams (< 2 hours)
        if (getFirstSegmentNum(newManifest) == 0) { // Short stream. No need to do something special.
            mOldManifest = newManifest;
            // Below line will be needed later (> 2 hours), when the stream no longer starts from 0 segment
            mOldSegmentNum = getLastSegmentNum(newManifest);
            return;
        }

        // Even 4+ hours streams could have different length.
        // So, we should take into account last segment num instead of first one.
        long newSegmentNum = getLastSegmentNum(newManifest);

        if (mOldManifest == null) {
            //recreateMissingSegments(newManifest);

            //newManifest.availabilityStartTimeMs = -1;
            Period newPeriod = newManifest.getPeriod(0);
            // TODO: modified
            //newPeriod.startMs = 0;
            Helpers.setField(newPeriod, "startMs", 0);
            mOldSegmentNum = newSegmentNum;

            for (int i = 0; i < newPeriod.adaptationSets.size(); i++) {
                for (int j = 0; j < newPeriod.adaptationSets.get(i).representations.size(); j++) {
                    MultiSegmentRepresentation representation = (MultiSegmentRepresentation) newPeriod.adaptationSets.get(i).representations.get(j);
                    //representation.presentationTimeOffsetUs = 0;

                    // TODO: modified
                    //SegmentList newSegmentList = (SegmentList) representation.segmentBase;
                    SegmentList newSegmentList = (SegmentList) Helpers.getField(representation, "segmentBase");
                    // TODO: modified
                    //newSegmentList.presentationTimeOffset = 0;
                    Helpers.setField(newSegmentList, "presentationTimeOffset", 0);
                    // TODO: modified
                    //newSegmentList.startNumber = 0;
                    Helpers.setField(newSegmentList, "startNumber", 0);
                }
            }

            mOldManifest = newManifest;

            return;
        }

        //long oldSegmentNum = getFirstSegmentNum(mManifest);

        Period oldPeriod = mOldManifest.getPeriod(0);
        Period newPeriod = newManifest.getPeriod(0);

        for (int i = 0; i < oldPeriod.adaptationSets.size(); i++) {
            for (int j = 0; j < oldPeriod.adaptationSets.get(i).representations.size(); j++) {
                appendRepresentation(
                        oldPeriod.adaptationSets.get(i).representations.get(j),
                        newPeriod.adaptationSets.get(i).representations.get(j),
                        newSegmentNum - mOldSegmentNum
                );
            }
        }

        mOldSegmentNum = newSegmentNum;

        //mManifest.timeShiftBufferDepthMs += (newSegmentNum - oldSegmentNum) * 5_000;
    }

    private static void appendRepresentation(Representation oldRepresentation, Representation newRepresentation, long segmentNumShift) {
        if (segmentNumShift <= 0) {
            return;
        }

        MultiSegmentRepresentation oldMultiRepresentation = (MultiSegmentRepresentation) oldRepresentation;
        MultiSegmentRepresentation newMultiRepresentation = (MultiSegmentRepresentation) newRepresentation;

        // TODO: modified
        //SegmentList oldSegmentList = (SegmentList) oldRepresentation.segmentBase;
        SegmentList oldSegmentList = (SegmentList) Helpers.getField(oldMultiRepresentation, "segmentBase");
        // TODO: modified
        //SegmentList newSegmentList = (SegmentList) newRepresentation.segmentBase;
        SegmentList newSegmentList = (SegmentList) Helpers.getField(newMultiRepresentation, "segmentBase");

        // TODO: modified
        //List<RangedUri> oldMediaSegments = oldSegmentList.mediaSegments;
        List<RangedUri> oldMediaSegments = (List<RangedUri>) Helpers.getField(oldSegmentList, "mediaSegments");
        // TODO: modified
        //List<RangedUri> newMediaSegments = newSegmentList.mediaSegments;
        List<RangedUri> newMediaSegments = (List<RangedUri>) Helpers.getField(newSegmentList, "mediaSegments");

        oldMediaSegments.addAll(
                newMediaSegments.subList(newMediaSegments.size() - (int) segmentNumShift, newMediaSegments.size()));

        // TODO: modified
        //List<SegmentTimelineElement> oldSegmentTimeline = oldSegmentList.segmentTimeline;
        List<SegmentTimelineElement> oldSegmentTimeline = (List<SegmentTimelineElement>) Helpers.getField(oldSegmentList, "segmentTimeline");

        // segmentTimeline is the same for all segments
        if (oldMediaSegments.size() != oldSegmentTimeline.size()) {
            SegmentTimelineElement lastTimeline = oldSegmentTimeline.get(oldSegmentTimeline.size() - 1);
            // TODO: modified
            //long lastTimelineDuration = lastTimeline.duration;
            long lastTimelineDuration = (Long) Helpers.getField(lastTimeline, "duration");
            // TODO: modified
            //long lastTimelineStartTime = lastTimeline.startTime;
            long lastTimelineStartTime = (Long) Helpers.getField(lastTimeline, "startTime");

            for (int i = 1; i <= segmentNumShift; i++) {
                oldSegmentTimeline.add(new SegmentTimelineElement(lastTimelineStartTime + (lastTimelineDuration * i), lastTimelineDuration));
            }

            //oldSegmentTimeline.addAll(
            //        newSegmentList.segmentTimeline.subList(newSegmentList.segmentTimeline.size() - (int) segmentNumShift - 1, newSegmentList.segmentTimeline.size()));
        }
    }

    private static void recreateMissingSegments(DashManifest manifest) {
        if (manifest == null) {
            return;
        }

        //if (getFirstSegmentNum(manifest) > MAX_SEGMENTS_COUNT) {
        //    // Skip long live streams (performance fix)
        //    return;
        //}

        long minUpdatePeriodMs = (long) Helpers.getField(manifest, "minUpdatePeriodMs");
        long timeShiftBufferDepthMs = (long) Helpers.getField(manifest, "timeShiftBufferDepthMs");
        long firstSegmentNum = getFirstSegmentNum(manifest);
        long lastSegmentNum = getLastSegmentNum(manifest);
        long maxSegmentsCount = MAX_STREAM_LENGTH_MS / minUpdatePeriodMs;
        long segmentCount = Math.min(firstSegmentNum, maxSegmentsCount - (lastSegmentNum - firstSegmentNum - 1));
        Helpers.setField(manifest, "timeShiftBufferDepthMs", timeShiftBufferDepthMs + (segmentCount * minUpdatePeriodMs));

        Period oldPeriod = manifest.getPeriod(0);

        for (int i = 0; i < oldPeriod.adaptationSets.size(); i++) {
            for (int j = 0; j < oldPeriod.adaptationSets.get(i).representations.size(); j++) {
                Representation oldRepresentation = oldPeriod.adaptationSets.get(i).representations.get(j);
                recreateRepresentation(oldRepresentation, segmentCount);

                long presentationTimeOffsetUs = oldRepresentation.presentationTimeOffsetUs;
                Helpers.setField(oldRepresentation, "presentationTimeOffsetUs", presentationTimeOffsetUs - (segmentCount * minUpdatePeriodMs * 1000));
            }
        }
    }

    private static void recreateRepresentation(Representation oldRepresentation, long segmentCount) {
        MultiSegmentRepresentation oldMultiRepresentation = (MultiSegmentRepresentation) oldRepresentation;

        SegmentList oldSegmentList = (SegmentList) Helpers.getField(oldMultiRepresentation, "segmentBase");

        List<RangedUri> oldMediaSegments = (List<RangedUri>) Helpers.getField(oldSegmentList, "mediaSegments");

        RangedUri firstSegment = oldMediaSegments.get(0);
        RangedUri secondSegment = oldMediaSegments.get(1);
        long start = firstSegment.start;
        long length = firstSegment.length;
        String firstSegmentUri = (String) Helpers.getField(firstSegment, "referenceUri");
        String secondSegmentUri = (String) Helpers.getField(secondSegment, "referenceUri");

        UrlQueryString firstSegmentQuery = UrlQueryStringFactory.parse("/" + firstSegmentUri);
        UrlQueryString secondSegmentQuery = UrlQueryStringFactory.parse("/" + secondSegmentUri);
        long firstSegmentNum = Helpers.parseLong(firstSegmentQuery.get("sq"));
        long firstSegmentLimit = Helpers.parseLong(firstSegmentQuery.get("lmt"));
        long secondSegmentLimit = Helpers.parseLong(secondSegmentQuery.get("lmt"));
        long limitDiff = secondSegmentLimit - firstSegmentLimit;

        if (firstSegmentNum <= 0) {
            return;
        }

        long currentSegmentNum = firstSegmentNum - 1;
        long currentSegmentLimit = firstSegmentLimit - limitDiff;

        for (int i = 1; i <= segmentCount; i++) {
            oldMediaSegments.add(0, new RangedUri(String.format("sq/%s/lmt/%s", currentSegmentNum, currentSegmentLimit), start, length));
            currentSegmentNum--;
            currentSegmentLimit -= limitDiff;
        }

        List<SegmentTimelineElement> oldSegmentTimeline = (List<SegmentTimelineElement>) Helpers.getField(oldSegmentList, "segmentTimeline");

        // segmentTimeline is the same for all segments
        if (oldMediaSegments.size() != oldSegmentTimeline.size()) {
            SegmentTimelineElement lastTimeline = oldSegmentTimeline.get(oldSegmentTimeline.size() - 1);
            long lastTimelineDuration = (Long) Helpers.getField(lastTimeline, "duration");
            long lastTimelineStartTime = (Long) Helpers.getField(lastTimeline, "startTime");

            for (int i = 1; i <= segmentCount; i++) {
                oldSegmentTimeline.add(new SegmentTimelineElement(lastTimelineStartTime + (lastTimelineDuration * i), lastTimelineDuration));
            }
        }

        Log.d(TAG, "Recreate representation: done");
    }

    private static long getFirstSegmentNum(DashManifest manifest) {
        DashSegmentIndex dashSegmentIndex = manifest.getPeriod(0).adaptationSets.get(0).representations.get(0).getIndex();
        return dashSegmentIndex.getFirstSegmentNum();
    }

    private static long getLastSegmentNum(DashManifest manifest) {
        DashSegmentIndex dashSegmentIndex = manifest.getPeriod(0).adaptationSets.get(0).representations.get(0).getIndex();
        return dashSegmentIndex.getFirstSegmentNum() + dashSegmentIndex.getSegmentCount(DashSegmentIndex.INDEX_UNBOUNDED) - 1;
    }

    private static long getSegmentCount(DashManifest manifest) {
        return manifest.getPeriod(0).adaptationSets.get(0).representations.get(0).getIndex().getSegmentCount(C.TIME_UNSET);
    }
}
