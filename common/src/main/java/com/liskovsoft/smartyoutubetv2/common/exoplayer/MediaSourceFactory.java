package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.ProgramInformation;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class MediaSourceFactory {
    private static final String TAG = MediaSourceFactory.class.getSimpleName();
    private static MediaSourceFactory sInstance;
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private final Factory mMediaDataSourceFactory;
    private final Context mContext;
    private static final List<String> EXO_HEADERS = Arrays.asList("Origin", "Referer", "User-Agent", "Accept-Language", "Accept", "X-Client-Data");
    private static final String SAMSUNG_SMART_TV_UA =
            "Mozilla/5.0 (Linux; Tizen 2.3; SmartHub; SMART-TV; SmartTV; U; Maple2012) AppleWebKit/538.1+ (KHTML, like Gecko) TV Safari/538.1+";
    private static final Uri DASH_MANIFEST_URI = Uri.parse("https://example.com/test.mpd");
    private static final String DASH_MANIFEST_EXTENSION = "mpd";
    private static final String HLS_PLAYLIST_EXTENSION = "m3u8";
    private Handler mMainHandler;
    private MediaSourceEventListener mEventLogger;

    private MediaSourceFactory(Context context) {
        mContext = context;
        mMediaDataSourceFactory = buildDataSourceFactory(false);
    }

    public static MediaSourceFactory instance(Context context) {
        if (sInstance == null) {
            sInstance = new MediaSourceFactory(context.getApplicationContext());
        }

        return sInstance;
    }

    public MediaSource fromDashManifest(InputStream dashManifest) {
        return buildMPDMediaSource(DASH_MANIFEST_URI, dashManifest);
    }

    public MediaSource fromDashManifest(Uri dashManifest) {
        return buildMediaSource(dashManifest, DASH_MANIFEST_EXTENSION);
    }

    public MediaSource fromHlsPlaylist(Uri hlsPlaylist) {
        return buildMediaSource(hlsPlaylist, HLS_PLAYLIST_EXTENSION);
    }

    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return buildDataSourceFactory(mContext, useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return buildHttpDataSourceFactory(mContext, useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);
        switch (type) {
            case C.TYPE_SS:
                SsMediaSource ssSource =
                        new SsMediaSource.Factory(
                                new DefaultSsChunkSource.Factory(mMediaDataSourceFactory),
                                buildDataSourceFactory(false)
                        )
                                .createMediaSource(uri);
                if (mEventLogger != null) {
                    ssSource.addEventListener(mMainHandler, mEventLogger);
                }
                return ssSource;
            case C.TYPE_DASH:
                DashMediaSource dashSource =
                        new DashMediaSource.Factory(
                                new DefaultDashChunkSource.Factory(mMediaDataSourceFactory),
                                buildDataSourceFactory(false)
                        )
                                .createMediaSource(uri);
                if (mEventLogger != null) {
                    dashSource.addEventListener(mMainHandler, mEventLogger);
                }
                return dashSource;
            case C.TYPE_HLS:
                HlsMediaSource hlsSource = new HlsMediaSource.Factory(mMediaDataSourceFactory).createMediaSource(uri);
                if (mEventLogger != null) {
                    hlsSource.addEventListener(mMainHandler, mEventLogger);
                }
                return hlsSource;
            case C.TYPE_OTHER:
                ExtractorMediaSource extractorSource = new ExtractorMediaSource.Factory(mMediaDataSourceFactory)
                        .setExtractorsFactory(new DefaultExtractorsFactory())
                        .createMediaSource(uri);
                if (mEventLogger != null) {
                    extractorSource.addEventListener(mMainHandler, mEventLogger);
                }
                return extractorSource;
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    private MediaSource buildMPDMediaSource(Uri uri, InputStream mpdContent) {
        // Are you using FrameworkSampleSource or ExtractorSampleSource when you build your player?
        DashMediaSource dashSource = new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mMediaDataSourceFactory),
                null
        )
                .createMediaSource(getManifest(uri, mpdContent));
        if (mEventLogger != null) {
            dashSource.addEventListener(mMainHandler, mEventLogger);
        }
        return dashSource;
    }

    private MediaSource buildMPDMediaSource(Uri uri, String mpdContent) {
        if (mpdContent == null || mpdContent.isEmpty()) {
            Log.e(TAG, "Can't build media source. MpdContent is null or empty. " + mpdContent);
            return null;
        }

        // Are you using FrameworkSampleSource or ExtractorSampleSource when you build your player?
        DashMediaSource dashSource = new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(mMediaDataSourceFactory),
                null
        )
                .createMediaSource(getManifest(uri, mpdContent));
        if (mEventLogger != null) {
            dashSource.addEventListener(mMainHandler, mEventLogger);
        }
        return dashSource;
    }

    private DashManifest getManifest(Uri uri, InputStream mpdContent) {
        DashManifestParser parser = new StaticDashManifestParser();
        DashManifest result;
        try {
            result = parser.parse(uri, mpdContent);
        } catch (IOException e) {
            throw new IllegalStateException("Malformed mpd file:\n" + mpdContent, e);
        }
        return result;
    }

    private DashManifest getManifest(Uri uri, String mpdContent) {
        DashManifestParser parser = new StaticDashManifestParser();
        DashManifest result;
        try {
            result = parser.parse(uri, FileHelpers.toStream(mpdContent));
        } catch (IOException e) {
            throw new IllegalStateException("Malformed mpd file:\n" + mpdContent, e);
        }
        return result;
    }

    private static DataSource.Factory buildDataSourceFactory(Context context, DefaultBandwidthMeter bandwidthMeter) {
        return new DefaultDataSourceFactory(context, bandwidthMeter, buildHttpDataSourceFactory(context, bandwidthMeter));
    }

    //public static HttpDataSource.Factory buildHttpDataSourceFactory(Context context, DefaultBandwidthMeter bandwidthMeter) {
    //    OkHttpDataSourceFactory dataSourceFactory = new OkHttpDataSourceFactory(OkHttpHelpers.getOkHttpClient(), USER_AGENT_MANAGER.getUA(),
    //            bandwidthMeter);
    //    addCommonHeaders(context, dataSourceFactory);
    //    return dataSourceFactory;
    //}

    private static HttpDataSource.Factory buildHttpDataSourceFactory(Context context, DefaultBandwidthMeter bandwidthMeter) {
        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(SAMSUNG_SMART_TV_UA, bandwidthMeter);
        //addCommonHeaders(context, dataSourceFactory); // cause troubles for some users
        return dataSourceFactory;
    }

    //private static void addCommonHeaders(Context context, BaseFactory dataSourceFactory) {
    //    HeaderManager headerManager = new HeaderManager(context);
    //    HashMap<String, String> headers = headerManager.getHeaders();
    //
    //    // NOTE: "Accept-Encoding" should set to "identity" or not present
    //
    //    for (String header : headers.keySet()) {
    //        if (EXO_HEADERS.contains(header)) {
    //            dataSourceFactory.getDefaultRequestProperties().set(header, headers.get(header));
    //        }
    //    }
    //}

    private static class StaticDashManifestParser extends DashManifestParser {
        @Override
        protected DashManifest buildMediaPresentationDescription(
                long availabilityStartTime,
                long durationMs,
                long minBufferTimeMs,
                boolean dynamic,
                long minUpdateTimeMs,
                long timeShiftBufferDepthMs,
                long suggestedPresentationDelayMs,
                long publishTimeMs,
                ProgramInformation programInformation,
                UtcTimingElement utcTiming,
                Uri location,
                List<Period> periods) {
            return new DashManifest(
                    availabilityStartTime,
                    durationMs,
                    minBufferTimeMs,
                    false,
                    minUpdateTimeMs,
                    timeShiftBufferDepthMs,
                    suggestedPresentationDelayMs,
                    publishTimeMs,
                    programInformation,
                    utcTiming,
                    location,
                    periods);
        }
    }
}
