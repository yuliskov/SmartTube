package com.google.android.exoplayer2.source.sabr.manifest;

import android.text.TextUtils;
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
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientInfo;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientName;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.formatbuilders.mpdbuilder.MediaFormatComparator;
import com.liskovsoft.youtubeapi.formatbuilders.utils.ITagUtils;
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SabrManifestParser {
    private static final String TAG = SabrManifestParser.class.getSimpleName();
    private int mId;
    private static final String NULL_INDEX_RANGE = "0-0";
    private static final String NULL_CONTENT_LENGTH = "0";
    private static final int MAX_DURATION_SEC = 48 * 60 * 60;
    private MediaItemFormatInfo mFormatInfo;
    private Set<MediaFormat> mMP4Videos;
    private Set<MediaFormat> mWEBMVideos;
    private Map<String, Set<MediaFormat>> mMP4Audios;
    private Map<String, Set<MediaFormat>> mWEBMAudios;
    private List<MediaSubtitle> mSubs;

    public SabrManifest parse(@NonNull MediaItemFormatInfo formatInfo) {
        mFormatInfo = formatInfo;
        MediaFormatComparator comp = new MediaFormatComparator();
        mMP4Videos = new TreeSet<>(comp);
        mWEBMVideos = new TreeSet<>(comp);
        mMP4Audios = new HashMap<>();
        mWEBMAudios = new HashMap<>();
        mSubs = new ArrayList<>();
        return parseSabrManifest(formatInfo);
    }

    private SabrManifest parseSabrManifest(MediaItemFormatInfo formatInfo) {
        long availabilityStartTime = C.TIME_UNSET;
        long durationMs = getDurationMs(formatInfo);
        long minBufferTimeMs = 1500; // "PT1.500S"
        long timeShiftBufferDepthMs = C.TIME_UNSET;
        long suggestedPresentationDelayMs = C.TIME_UNSET;
        long publishTimeMs = C.TIME_UNSET;
        boolean dynamic = false;
        long minUpdateTimeMs = C.TIME_UNSET; // 3155690800000L, "P100Y" no refresh (there is no dash url)

        List<Period> periods = new ArrayList<>();

        Pair<Period, Long> periodWithDurationMs = parsePeriod(formatInfo);
        if (periodWithDurationMs != null) {
            Period period = periodWithDurationMs.first;
            periods.add(period);
        }

        return new SabrManifest(
                availabilityStartTime,
                durationMs,
                minBufferTimeMs,
                dynamic,
                minUpdateTimeMs,
                timeShiftBufferDepthMs,
                suggestedPresentationDelayMs,
                publishTimeMs,
                periods,
                formatInfo.getServerAbrStreamingUrl(),
                formatInfo.getVideoPlaybackUstreamerConfig(),
                formatInfo.getPoToken(),
                formatInfo.getVideoId(),
                createClientInfo(formatInfo));
    }

    private static long getDurationMs(MediaItemFormatInfo formatInfo) {
        long lenSeconds = Helpers.parseLong(formatInfo.getLengthSeconds());
        return lenSeconds > 0 ? lenSeconds * 1_000 : C.TIME_UNSET;
    }

    private Pair<Period, Long> parsePeriod(MediaItemFormatInfo formatInfo) {
        String id = formatInfo.getVideoId();
        long startMs = 0; // Should add real start time or make it unset?
        long durationMs = getDurationMs(formatInfo);
        List<AdaptationSet> adaptationSets = new ArrayList<>();

        for (MediaFormat format : formatInfo.getAdaptiveFormats()) {
            append(format);
        }

        if (formatInfo.getSubtitles() != null) {
            append(formatInfo.getSubtitles());
        }

        // MXPlayer fix: write high quality formats first
        if (!mMP4Videos.isEmpty()) {
            adaptationSets.add(parseAdaptationSet(mMP4Videos));
        }
        if (!mWEBMVideos.isEmpty()) {
            adaptationSets.add(parseAdaptationSet(mWEBMVideos));
        }

        for (Set<MediaFormat> formats : mMP4Audios.values()) {
            adaptationSets.add(parseAdaptationSet(formats));
        }

        for (Set<MediaFormat> formats : mWEBMAudios.values()) {
            adaptationSets.add(parseAdaptationSet(formats));
        }

        for (MediaSubtitle subtitle : mSubs) {
            adaptationSets.add(parseAdaptationSet(Collections.singletonList(subtitle)));
        }

        return Pair.create(new Period(id, startMs, adaptationSets), durationMs);
    }

    private AdaptationSet parseAdaptationSet(Set<MediaFormat> formats) {
        int id = mId++;
        int contentType = C.TRACK_TYPE_UNKNOWN;
        String label = null;
        String drmSchemeType = null;
        ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
        List<RepresentationInfo> representationInfos = new ArrayList<>();

        for (MediaFormat format : formats) {
            RepresentationInfo representationInfo = parseRepresentation(format);
            if (contentType == C.TRACK_TYPE_UNKNOWN) {
                contentType = getContentType(representationInfo.format);
            }
            representationInfos.add(representationInfo);
        }

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

    private AdaptationSet parseAdaptationSet(List<MediaSubtitle> formats) {
        int id = mId++;
        int contentType = C.TRACK_TYPE_UNKNOWN;
        String label = null;
        String drmSchemeType = null;
        ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();
        List<RepresentationInfo> representationInfos = new ArrayList<>();

        for (MediaSubtitle format : formats) {
            RepresentationInfo representationInfo = parseRepresentation(format);
            if (contentType == C.TRACK_TYPE_UNKNOWN) {
                contentType = getContentType(representationInfo.format);
            }
            representationInfos.add(representationInfo);
        }

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
            segmentDurationUs = format.getTargetDurationSec() * 1_000_000;
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
        //UrlTemplate initializationTemplate = UrlTemplate.compile(format.getOtfInitUrl()); // ?
        UrlTemplate initializationTemplate = null; // ?

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

    private RepresentationInfo parseRepresentation(MediaFormat mediaFormat) {
        int roleFlags = C.ROLE_FLAG_MAIN;
        int selectionFlags = C.SELECTION_FLAG_DEFAULT;
        String id = mediaFormat.isDrc() ? mediaFormat.getITag() + "-drc" : mediaFormat.getITag();
        int bandwidth = Helpers.parseInt(mediaFormat.getBitrate(), Format.NO_VALUE);
        String mimeType = MediaFormatUtils.extractMimeType(mediaFormat);
        String codecs = MediaFormatUtils.extractCodecs(mediaFormat);
        int width = mediaFormat.getWidth();
        int height = mediaFormat.getHeight();
        float frameRate = Helpers.parseFloat(mediaFormat.getFps(), Format.NO_VALUE);
        int audioChannels = Format.NO_VALUE;
        int audioSamplingRate = Helpers.parseInt(ITagUtils.getAudioRateByTag(mediaFormat.getITag()), Format.NO_VALUE);
        String language = mediaFormat.getLanguage();
        String baseUrl = mediaFormat.getUrl();
        String label = null;
        String drmSchemeType = null;
        ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();

        Format format =
                buildFormat(
                        id,
                        mimeType,
                        width,
                        height,
                        frameRate,
                        audioChannels,
                        audioSamplingRate,
                        bandwidth,
                        language,
                        roleFlags,
                        selectionFlags,
                        codecs);

        SegmentBase segmentBase = null;

        if (MediaFormatUtils.isLiveMedia(mediaFormat)) {
            segmentBase = parseSegmentTemplate(mediaFormat);
        } else if (mediaFormat.getSegmentUrlList() != null) {
            segmentBase = parseSegmentList(mediaFormat);
        } else if (mediaFormat.getIndex() != null &&
                !mediaFormat.getIndex().equals(NULL_INDEX_RANGE)) { // json mediaFormat fix: index is null
            segmentBase = parseSegmentBase(mediaFormat);
        }

        segmentBase = segmentBase != null ? segmentBase : new SingleSegmentBase();

        return new RepresentationInfo(format, baseUrl, segmentBase, drmSchemeType, drmSchemeDatas, Representation.REVISION_ID_DEFAULT);
    }

    private RepresentationInfo parseRepresentation(MediaSubtitle sub) {
        int roleFlags = C.ROLE_FLAG_SUBTITLE;
        int selectionFlags = 0;
        String id = String.valueOf(mId++);
        int bandwidth = 268;
        String mimeType = sub.getMimeType();
        String codecs = sub.getCodecs();
        int width = Format.NO_VALUE;
        int height = Format.NO_VALUE;
        float frameRate = Format.NO_VALUE;
        int audioChannels = Format.NO_VALUE;
        int audioSamplingRate = Format.NO_VALUE;
        String language = sub.getName() == null ? sub.getLanguageCode() : sub.getName();
        String baseUrl = sub.getBaseUrl();
        String label = null;
        String drmSchemeType = null;
        ArrayList<SchemeData> drmSchemeDatas = new ArrayList<>();

        Format format =
                buildFormat(
                        id,
                        mimeType,
                        width,
                        height,
                        frameRate,
                        audioChannels,
                        audioSamplingRate,
                        bandwidth,
                        language,
                        roleFlags,
                        selectionFlags,
                        codecs);

        SegmentBase segmentBase = new SingleSegmentBase();

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
            @C.RoleFlags int roleFlags,
            @C.SelectionFlags int selectionFlags,
            String codecs) {
        String sampleMimeType = getSampleMimeType(containerMimeType, codecs);
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

    private void append(List<MediaSubtitle> subs) {
        mSubs.addAll(subs);
    }

    private void append(MediaSubtitle sub) {
        mSubs.add(sub);
    }

    private void append(MediaFormat mediaItem) {
        if (!MediaFormatUtils.checkMediaUrl(mediaItem)) {
            Log.e(TAG, "Media item doesn't contain required url field!");
            return;
        }

        // NOTE: FORMAT_STREAM_TYPE_OTF not supported
        if (!MediaFormatUtils.isDash(mediaItem)) {
            return;
        }

        //fixOTF(mediaItem);

        Set<MediaFormat> placeholder = null;
        String mimeType = MediaFormatUtils.extractMimeType(mediaItem);
        if (mimeType != null) {
            switch (mimeType) {
                case MediaFormatUtils.MIME_MP4_VIDEO:
                    placeholder = mMP4Videos;
                    break;
                case MediaFormatUtils.MIME_WEBM_VIDEO:
                    placeholder = mWEBMVideos;
                    break;
                case MediaFormatUtils.MIME_MP4_AUDIO:
                    placeholder = getMP4Audios(mediaItem.getLanguage());
                    break;
                case MediaFormatUtils.MIME_WEBM_AUDIO:
                    placeholder = getWEBMAudios(mediaItem.getLanguage());
                    break;
            }
        }

        if (placeholder != null) {
            placeholder.add(mediaItem); // NOTE: reverse order
        }
    }

    private Set<MediaFormat> getMP4Audios(String language) {
        return getFormats(mMP4Audios, language);
    }

    private Set<MediaFormat> getWEBMAudios(String language) {
        return getFormats(mWEBMAudios, language);
    }

    private static Set<MediaFormat> getFormats(Map<String, Set<MediaFormat>> formatMap, String language) {
        if (language == null) {
            language = "default";
        }

        Set<MediaFormat> mediaFormats = formatMap.get(language);

        if (mediaFormats == null) {
            mediaFormats = new TreeSet<>(new MediaFormatComparator());
            formatMap.put(language, mediaFormats);
        }

        return mediaFormats;
    }

    protected int getContentType(Format format) {
        String sampleMimeType = format.sampleMimeType;
        if (TextUtils.isEmpty(sampleMimeType)) {
            return C.TRACK_TYPE_UNKNOWN;
        } else if (MimeTypes.isVideo(sampleMimeType)) {
            return C.TRACK_TYPE_VIDEO;
        } else if (MimeTypes.isAudio(sampleMimeType)) {
            return C.TRACK_TYPE_AUDIO;
        } else if (mimeTypeIsRawText(sampleMimeType)) {
            return C.TRACK_TYPE_TEXT;
        }
        return C.TRACK_TYPE_UNKNOWN;
    }

    private ClientInfo createClientInfo(MediaItemFormatInfo formatInfo) {
        MediaItemFormatInfo.ClientInfo clientInfo = formatInfo.getClientInfo();
        ClientName clientName = ClientName.WEB;

        if (Helpers.startsWith(clientInfo.getName(), "TV")) {
            clientName = ClientName.TVHTML5;
        } else if (Helpers.startsWith(clientInfo.getName(), "MWEB")) {
            clientName = ClientName.MWEB;
        } else if (Helpers.startsWith(clientInfo.getName(), "ANDROID")) {
            clientName = ClientName.ANDROID;
        } else if (Helpers.startsWith(clientInfo.getName(), "IOS")) {
            clientName = ClientName.IOS;
        }

        return ClientInfo.newBuilder()
                .setClientName(clientName)
                .setClientVersion(clientInfo.getVersion())
                .build();
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
