package com.google.android.exoplayer2.source.sabr.manifest;

import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SegmentList;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SegmentTemplate;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SegmentTimelineElement;
import com.google.android.exoplayer2.source.sabr.manifest.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.youtubeapi.formatbuilders.utils.ITagUtils;
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils;

import java.util.ArrayList;
import java.util.List;

public class SabrManifestParser {
    private int mId;
    private static final String NULL_INDEX_RANGE = "0-0";
    private static final String NULL_CONTENT_LENGTH = "0";
    private static final int MAX_DURATION_SEC = 48 * 60 * 60;
    private MediaItemFormatInfo mFormatInfo;

    public SabrManifest parse(@NonNull MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
        return parseSabrManifest(formatInfo);
    }

    private SabrManifest parseSabrManifest(MediaItemFormatInfo formatInfo) {
        long availabilityStartTime = formatInfo.getStartTimeMs();
        long durationMs = getDurationMs(formatInfo);
        long minBufferTimeMs = C.TIME_UNSET;
        long timeShiftBufferDepthMs = C.TIME_UNSET;
        long suggestedPresentationDelayMs = C.TIME_UNSET;
        long publishTimeMs = formatInfo.getStartTimeMs();

        List<Period> periods = new ArrayList<>();
        long nextPeriodStartMs = 0;

        Pair<Period, Long> periodWithDurationMs = parsePeriod(formatInfo, nextPeriodStartMs);
        if (periodWithDurationMs != null) {
            Period period = periodWithDurationMs.first;
            periods.add(period);
        }

        return new SabrManifest(
                availabilityStartTime,
                durationMs,
                minBufferTimeMs,
                timeShiftBufferDepthMs,
                suggestedPresentationDelayMs,
                publishTimeMs,
                periods);
    }

    private static long getDurationMs(MediaItemFormatInfo formatInfo) {
        long lenSeconds = Helpers.parseLong(formatInfo.getLengthSeconds());
        return lenSeconds != -1 ? lenSeconds * 1_000 : C.TIME_UNSET;
    }

    private Pair<Period, Long> parsePeriod(MediaItemFormatInfo formatInfo, long nextPeriodStartMs) {
        String id = formatInfo.getVideoId();
        long startMs = formatInfo.getStartTimeMs();
        long durationMs = getDurationMs(formatInfo);
        List<AdaptationSet> adaptationSets = new ArrayList<>();

        for (MediaFormat format : formatInfo.getAdaptiveFormats()) {
            adaptationSets.add(parseAdaptationSet(format));
        }

        return Pair.create(new Period(id, startMs, adaptationSets), durationMs);
    }

    private AdaptationSet parseAdaptationSet(MediaFormat format) {
        int id = mId++;
        int contentType = C.TRACK_TYPE_UNKNOWN;
        String mimeType = MediaFormatUtils.extractMimeType(format);
        String codecs = MediaFormatUtils.extractCodecs(format);
        int width = format.getWidth();
        int height = format.getHeight();
        float frameRate = Helpers.parseFloat(format.getFps(), Format.NO_VALUE);
        int audioChannels = Format.NO_VALUE;
        int audioSamplingRate = Helpers.parseInt(ITagUtils.getAudioRateByTag(format.getITag()), Format.NO_VALUE);
        int bandwidth = Helpers.parseInt(format.getBitrate(), Format.NO_VALUE);
        String language = format.getLanguage();
        String label = null;
        String drmSchemeType = null;
        ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
        List<RepresentationInfo> representationInfos = new ArrayList<>();

        String baseUrl = format.getUrl();

        SegmentBase segmentBase = null;

        if (MediaFormatUtils.isLiveMedia(format)) {
            segmentBase = parseSegmentTemplate(format);
        } else if (format.getSegmentUrlList() != null) {
            segmentBase = parseSegmentList(format);
        } else if (format.getIndex() != null &&
                !format.getIndex().equals(NULL_INDEX_RANGE)) { // json format fix: index is null
            segmentBase = parseSegmentBase(format);
        }

        segmentBase = segmentBase != null ? segmentBase : new SingleSegmentBase();

        RepresentationInfo representationInfo =
                parseRepresentation(
                        id,
                        bandwidth,
                        baseUrl,
                        mimeType,
                        codecs,
                        width,
                        height,
                        frameRate,
                        audioChannels,
                        audioSamplingRate,
                        language,
                        segmentBase);
        representationInfos.add(representationInfo);

        // Build the representations.
        List<Representation> representations = new ArrayList<>(representationInfos.size());
        for (int i = 0; i < representationInfos.size(); i++) {
            representations.add(
                    buildRepresentation(
                            representationInfos.get(i),
                            label,
                            drmSchemeType,
                            drmSchemeDatas));
        }

        return new AdaptationSet(id, contentType, representations);
    }

    private SegmentTemplate parseSegmentTemplate(MediaFormat format) {
        int unitsPerSecond = 1_000_000;

        // Present on live streams only.
        int segmentDurationUs = mFormatInfo.getSegmentDurationUs();

        if (segmentDurationUs <= 0) {
            // Inaccurate. Present on past (!) live streams.
            segmentDurationUs = Integer.parseInt(format.getTargetDurationSec()) * 1_000_000;
        }

        int lengthSeconds = Integer.parseInt(mFormatInfo.getLengthSeconds());

        if (mFormatInfo.isLive() || lengthSeconds <= 0) {
            // For premiere streams (length > 0) or regular streams (length == 0) set window that exceeds normal limits - 48hrs
            lengthSeconds = MAX_DURATION_SEC;
        }

        // To make long streams (12hrs) seekable we should decrease size of the segment a bit
        //long segmentDurationUnits = (long) targetDurationSec * unitsPerSecond * 9999 / 10000;
        int segmentDurationUnits = (int)(segmentDurationUs * (long) unitsPerSecond / 1_000_000);
        // Increase count a bit to compensate previous tweak
        //long segmentCount = (long) lengthSeconds / targetDurationSec * 10000 / 9999;
        //int segmentCount = (int)(lengthSeconds * (long) unitsPerSecond / segmentDurationUnits);
        int segmentCount = (int) Math.ceil(lengthSeconds * (double) unitsPerSecond / segmentDurationUnits);
        // Increase offset a bit to compensate previous tweaks
        // Streams to check:
        // https://www.youtube.com/watch?v=drdemkJpgao
        long offsetUnits = (long) segmentDurationUnits * mFormatInfo.getStartSegmentNum();

        long timescale = unitsPerSecond;
        long presentationTimeOffset = offsetUnits;
        long duration = segmentDurationUnits;
        long startNumber = mFormatInfo.getStartSegmentNum();
        long endNumber = C.INDEX_UNSET;
        UrlTemplate mediaTemplate = UrlTemplate.compile(format.getUrl() + "&sq=$Number$");
        UrlTemplate initializationTemplate = UrlTemplate.compile(format.getOtfInitUrl()); // ?

        RangedUri initialization = parseRangedUrl(format.getSourceUrl(), format.getInit());

        List<SegmentTimelineElement> timeline = parseSegmentTimeline(offsetUnits, segmentDurationUnits, segmentCount);

        return new SegmentTemplate(
                initialization,
                timescale,
                presentationTimeOffset,
                startNumber,
                endNumber,
                duration,
                timeline,
                initializationTemplate,
                mediaTemplate);
    }

    private SegmentList parseSegmentList(MediaFormat format) {
        long timescale = 1;
        long presentationTimeOffset = 0;
        long duration = C.TIME_UNSET;
        long startNumber = 1;

        RangedUri initialization = parseRangedUrl(format.getSourceUrl(), format.getInit());

        List<SegmentTimelineElement> timeline = parseSegmentTimeline(format);

        List<RangedUri> segments = parseSegmentUrl(format);

        return new SegmentList(initialization, timescale, presentationTimeOffset,
                startNumber, duration, timeline, segments);
    }

    private RangedUri parseRangedUrl(String urlText, String rangeText) {
        long rangeStart = 0;
        long rangeLength = C.LENGTH_UNSET;
        if (rangeText != null) {
            String[] rangeTextArray = rangeText.split("-");
            rangeStart = Long.parseLong(rangeTextArray[0]);
            if (rangeTextArray.length == 2) {
                rangeLength = Long.parseLong(rangeTextArray[1]) - rangeStart + 1;
            }
        }

        return new RangedUri(urlText, rangeStart, rangeLength);
    }

    private List<SegmentTimelineElement> parseSegmentTimeline(MediaFormat format) {
        List<SegmentTimelineElement> timeline = new ArrayList<>();

        if (format.getGlobalSegmentList() == null) {
            return timeline;
        }

        // From writeGlobalSegmentList
        long elapsedTime = 0;

        // SegmentURL tag
        for (String segment : format.getGlobalSegmentList()) {
            long duration = Helpers.parseLong(segment, C.TIME_UNSET);
            int count = 1;
            for (int i = 0; i < count; i++) {
                timeline.add(new SegmentTimelineElement(elapsedTime, duration));
                elapsedTime += duration;
            }
        }

        return timeline;
    }

    private List<SegmentTimelineElement> parseSegmentTimeline(long elapsedTime, long duration, int segmentCount) {
        List<SegmentTimelineElement> timeline = new ArrayList<>();

        // From writeLiveMediaSegmentList
        int count = 1 + segmentCount;
        for (int i = 0; i < count; i++) {
            timeline.add(new SegmentTimelineElement(elapsedTime, duration));
            elapsedTime += duration;
        }

        return timeline;
    }

    private List<RangedUri> parseSegmentUrl(MediaFormat format) {
        List<RangedUri> segments = new ArrayList<>();

        if (format.getSegmentUrlList() == null) {
            return segments;
        }

        // SegmentURL tag
        for (String url : format.getSegmentUrlList()) {
            segments.add(parseRangedUrl(url, null));
        }

        return segments;
    }

    private SingleSegmentBase parseSegmentBase(MediaFormat format) {
        long timescale = 1000;
        long presentationTimeOffset = 0;

        long indexStart = 0;
        long indexLength = 0;
        String indexRangeText = format.getIndex();
        if (indexRangeText != null) {
            String[] indexRange = indexRangeText.split("-");
            indexStart = Long.parseLong(indexRange[0]);
            indexLength = Long.parseLong(indexRange[1]) - indexStart + 1;
        }

        RangedUri initialization = parseRangedUrl(format.getSourceUrl(), format.getInit());


        return new SingleSegmentBase(initialization, timescale, presentationTimeOffset, indexStart,
                indexLength);
    }

    private RepresentationInfo parseRepresentation(
            int id,
            int bandwidth,
            String baseUrl,
            String mimeType,
            String codecs,
            int width,
            int height,
            float frameRate,
            int audioChannels,
            int audioSamplingRate,
            String language,
            SegmentBase segmentBase) {
        String drmSchemeType = null;
        ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();

        Format format =
                buildFormat(
                        String.valueOf(id),
                        mimeType,
                        width,
                        height,
                        frameRate,
                        audioChannels,
                        audioSamplingRate,
                        bandwidth,
                        language,
                        codecs);

        return new RepresentationInfo(format, baseUrl, segmentBase, drmSchemeType, drmSchemeDatas, Representation.REVISION_ID_DEFAULT);
    }

    protected Representation buildRepresentation(
            RepresentationInfo representationInfo,
            String label,
            String extraDrmSchemeType,
            ArrayList<SchemeData> extraDrmSchemeDatas) {
        Format format = representationInfo.format;
        if (label != null) {
            format = format.copyWithLabel(label);
        }
        String drmSchemeType = representationInfo.drmSchemeType != null
                ? representationInfo.drmSchemeType : extraDrmSchemeType;
        ArrayList<SchemeData> drmSchemeDatas = representationInfo.drmSchemeDatas;
        drmSchemeDatas.addAll(extraDrmSchemeDatas);
        if (!drmSchemeDatas.isEmpty()) {
            filterRedundantIncompleteSchemeDatas(drmSchemeDatas);
            DrmInitData drmInitData = new DrmInitData(drmSchemeType, drmSchemeDatas);
            format = format.copyWithDrmInitData(drmInitData);
        }
        return Representation.newInstance(
                representationInfo.revisionId,
                format,
                representationInfo.baseUrl,
                representationInfo.segmentBase);
    }

    protected Format buildFormat(
            String id,
            String containerMimeType,
            int width,
            int height,
            float frameRate,
            int audioChannels,
            int audioSamplingRate,
            int bitrate,
            String language,
            String codecs) {
        String sampleMimeType = getSampleMimeType(containerMimeType, codecs);
        @C.SelectionFlags int selectionFlags = C.SELECTION_FLAG_DEFAULT;
        @C.RoleFlags int roleFlags = C.ROLE_FLAG_MAIN;
        if (sampleMimeType != null) {
            if (MimeTypes.isVideo(sampleMimeType)) {
                return Format.createVideoContainerFormat(
                        id,
                        /* label= */ null,
                        containerMimeType,
                        sampleMimeType,
                        codecs,
                        /* metadata= */ null,
                        bitrate,
                        width,
                        height,
                        frameRate,
                        /* initializationData= */ null,
                        selectionFlags,
                        roleFlags);
            } else if (MimeTypes.isAudio(sampleMimeType)) {
                return Format.createAudioContainerFormat(
                        id,
                        /* label= */ null,
                        containerMimeType,
                        sampleMimeType,
                        codecs,
                        /* metadata= */ null,
                        bitrate,
                        audioChannels,
                        audioSamplingRate,
                        /* initializationData= */ null,
                        selectionFlags,
                        roleFlags,
                        language);
            } else if (mimeTypeIsRawText(sampleMimeType)) {
                return Format.createTextContainerFormat(
                        id,
                        /* label= */ null,
                        containerMimeType,
                        sampleMimeType,
                        codecs,
                        bitrate,
                        selectionFlags,
                        roleFlags,
                        language,
                        Format.NO_VALUE);
            }
        }
        return Format.createContainerFormat(
                id,
                /* label= */ null,
                containerMimeType,
                sampleMimeType,
                codecs,
                bitrate,
                selectionFlags,
                roleFlags,
                language);
    }

    /**
     * Derives a sample mimeType from a container mimeType and codecs attribute.
     *
     * @param containerMimeType The mimeType of the container.
     * @param codecs The codecs attribute.
     * @return The derived sample mimeType, or null if it could not be derived.
     */
    private static String getSampleMimeType(String containerMimeType, String codecs) {
        if (MimeTypes.isAudio(containerMimeType)) {
            return MimeTypes.getAudioMediaMimeType(codecs);
        } else if (MimeTypes.isVideo(containerMimeType)) {
            return MimeTypes.getVideoMediaMimeType(codecs);
        } else if (mimeTypeIsRawText(containerMimeType)) {
            return containerMimeType;
        } else if (MimeTypes.APPLICATION_MP4.equals(containerMimeType)) {
            if (codecs != null) {
                if (codecs.startsWith("stpp")) {
                    return MimeTypes.APPLICATION_TTML;
                } else if (codecs.startsWith("wvtt")) {
                    return MimeTypes.APPLICATION_MP4VTT;
                }
            }
        } else if (MimeTypes.APPLICATION_RAWCC.equals(containerMimeType)) {
            if (codecs != null) {
                if (codecs.contains("cea708")) {
                    return MimeTypes.APPLICATION_CEA708;
                } else if (codecs.contains("eia608") || codecs.contains("cea608")) {
                    return MimeTypes.APPLICATION_CEA608;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Returns whether a mimeType is a text sample mimeType.
     *
     * @param mimeType The mimeType.
     * @return Whether the mimeType is a text sample mimeType.
     */
    private static boolean mimeTypeIsRawText(String mimeType) {
        return MimeTypes.isText(mimeType)
                || MimeTypes.APPLICATION_TTML.equals(mimeType)
                || MimeTypes.APPLICATION_MP4VTT.equals(mimeType)
                || MimeTypes.APPLICATION_CEA708.equals(mimeType)
                || MimeTypes.APPLICATION_CEA608.equals(mimeType);
    }

    /**
     * Removes unnecessary {@link SchemeData}s with null {@link SchemeData#data}.
     */
    private static void filterRedundantIncompleteSchemeDatas(ArrayList<SchemeData> schemeDatas) {
        for (int i = schemeDatas.size() - 1; i >= 0; i--) {
            SchemeData schemeData = schemeDatas.get(i);
            if (!schemeData.hasData()) {
                for (int j = 0; j < schemeDatas.size(); j++) {
                    if (schemeDatas.get(j).canReplace(schemeData)) {
                        // schemeData is incomplete, but there is another matching SchemeData which does contain
                        // data, so we remove the incomplete one.
                        schemeDatas.remove(i);
                        break;
                    }
                }
            }
        }
    }

    /** A parsed Representation element. */
    protected static final class RepresentationInfo {

        public final Format format;
        public final String baseUrl;
        public final SegmentBase segmentBase;
        public final String drmSchemeType;
        public final ArrayList<SchemeData> drmSchemeDatas;
        public final long revisionId;

        public RepresentationInfo(Format format, String baseUrl, SegmentBase segmentBase,
                                  String drmSchemeType, ArrayList<SchemeData> drmSchemeDatas,
                                  long revisionId) {
            this.format = format;
            this.baseUrl = baseUrl;
            this.segmentBase = segmentBase;
            this.drmSchemeType = drmSchemeType;
            this.drmSchemeDatas = drmSchemeDatas;
            this.revisionId = revisionId;
        }

    }
}
