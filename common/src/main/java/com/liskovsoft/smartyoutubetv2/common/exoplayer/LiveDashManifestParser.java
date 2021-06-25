package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.net.Uri;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.RangedUri;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.Representation.MultiSegmentRepresentation;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentList;
import com.google.android.exoplayer2.source.dash.manifest.SegmentBase.SegmentTimelineElement;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Supported ExoPlayer versions: 2.10.6
 */
@SuppressWarnings("unchecked")
public class LiveDashManifestParser extends DashManifestParser {
    private DashManifest mManifest;
    private long mOldSegmentNum;

    @Override
    public DashManifest parse(Uri uri, InputStream inputStream) throws IOException {
        DashManifest manifest = super.parse(uri, inputStream);

        updateManifest(manifest);

        return mManifest;
    }

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

        // TODO: modified
        //SegmentList oldSegmentList = (SegmentList) oldRepresentation.segmentBase;
        SegmentList oldSegmentList = (SegmentList) Helpers.getField(oldRepresentation, "segmentBase");
        // TODO: modified
        //SegmentList newSegmentList = (SegmentList) newRepresentation.segmentBase;
        SegmentList newSegmentList = (SegmentList) Helpers.getField(newRepresentation, "segmentBase");

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
}
