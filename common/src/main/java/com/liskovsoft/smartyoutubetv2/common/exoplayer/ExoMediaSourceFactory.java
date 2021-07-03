package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
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
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.app.AppConstants;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class ExoMediaSourceFactory {
    private static final String TAG = ExoMediaSourceFactory.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ExoMediaSourceFactory sInstance;
    @SuppressLint("StaticFieldLeak")
    @SuppressWarnings("deprecation")
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private final Factory mMediaDataSourceFactory;
    private final Context mContext;
    //private static final List<String> EXO_HEADERS = Arrays.asList("Origin", "Referer", "User-Agent", "Accept-Language", "Accept", "X-Client-Data");
    private static final Uri DASH_MANIFEST_URI = Uri.parse("https://example.com/test.mpd");
    private static final String DASH_MANIFEST_EXTENSION = "mpd";
    private static final String HLS_PLAYLIST_EXTENSION = "m3u8";
    private static final boolean USE_BANDWIDTH_METER = false;
    private Handler mMainHandler;
    private MediaSourceEventListener mEventLogger;

    private ExoMediaSourceFactory(Context context) {
        mContext = context;
        mMediaDataSourceFactory = buildDataSourceFactory(USE_BANDWIDTH_METER);

        //if (BuildConfig.DEBUG) {
        //    mMainHandler = new Handler(Looper.getMainLooper());
        //    mEventLogger = new DefaultMediaSourceEventListener() {
        //    };
        //}
    }

    public static ExoMediaSourceFactory instance(Context context) {
        if (sInstance == null) {
            sInstance = new ExoMediaSourceFactory(context.getApplicationContext());
        }

        return sInstance;
    }

    //private void prepareMediaForPlaying(Uri mediaSourceUri) {
    //    String userAgent = Util.getUserAgent(getActivity(), "VideoPlayerGlue");
    //    MediaSource mediaSource =
    //            new ExtractorMediaSource(
    //                    mediaSourceUri,
    //                    new DefaultDataSourceFactory(getActivity(), userAgent),
    //                    new DefaultExtractorsFactory(),
    //                    null,
    //                    null);
    //
    //    mPlayer.prepare(mediaSource);
    //}

    public MediaSource fromDashManifest(InputStream dashManifest) {
        return buildMPDMediaSource(DASH_MANIFEST_URI, dashManifest);
    }

    public MediaSource fromDashManifestUrl(String dashManifestUrl) {
        return buildMediaSource(Uri.parse(dashManifestUrl), DASH_MANIFEST_EXTENSION);
    }

    public MediaSource fromHlsPlaylist(String hlsPlaylist) {
        return buildMediaSource(Uri.parse(hlsPlaylist), HLS_PLAYLIST_EXTENSION);
    }

    public MediaSource fromUrlList(List<String> urlList) {
        MediaSource[] mediaSources = new MediaSource[urlList.size()];

        for (int i = 0; i < urlList.size(); i++) {
            mediaSources[i] = buildMediaSource(Uri.parse(urlList.get(i)), null);
        }

        return mediaSources.length == 1 ? mediaSources[0] : new ConcatenatingMediaSource(mediaSources); // or playlist
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
        return buildHttpDataSourceFactory(useBandwidthMeter ? BANDWIDTH_METER : null);
    }

    @SuppressWarnings("deprecation")
    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);
        switch (type) {
            case C.TYPE_SS:
                SsMediaSource ssSource =
                        new SsMediaSource.Factory(
                                new DefaultSsChunkSource.Factory(mMediaDataSourceFactory),
                                buildDataSourceFactory(USE_BANDWIDTH_METER)
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
                                buildDataSourceFactory(USE_BANDWIDTH_METER)
                        )
                                .setManifestParser(new LiveDashManifestParser()) // Don't make static! Need state reset for each live source.
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
        return new DefaultDataSourceFactory(context, bandwidthMeter, buildHttpDataSourceFactory(bandwidthMeter));
    }

    /**
     * Use OkHttp for networking
     */
    //public static HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
    //    // OkHttpHelpers.getOkHttpClient()
    //    // RetrofitHelper.createOkHttpClient()
    //    OkHttpDataSourceFactory dataSourceFactory = new OkHttpDataSourceFactory(RetrofitHelper.createOkHttpClient(), AppConstants.APP_USER_AGENT,
    //            bandwidthMeter);
    //    //addCommonHeaders(dataSourceFactory);
    //    return dataSourceFactory;
    //}

    /**
     * Use built-in component for networking
     */
    private static HttpDataSource.Factory buildHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        //DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(
        //        AppConstants.APP_USER_AGENT, bandwidthMeter);

        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(
                AppConstants.APP_USER_AGENT, bandwidthMeter, DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, true);

        addCommonHeaders(dataSourceFactory); // cause troubles for some users
        //if (YouTubeSignInManager.mAuthorizationHeaderCached != null) {
        //    dataSourceFactory.getDefaultRequestProperties().set("Authorization", YouTubeSignInManager.mAuthorizationHeaderCached);
        //}

        return dataSourceFactory;
    }

    private static void addCommonHeaders(BaseFactory dataSourceFactory) {
        //HeaderManager headerManager = new HeaderManager(context);
        //HashMap<String, String> headers = headerManager.getHeaders();

        // NOTE: "Accept-Encoding" should set to "identity" or not present

        //for (String header : headers.keySet()) {
        //    if (EXO_HEADERS.contains(header)) {
        //        dataSourceFactory.getDefaultRequestProperties().set(header, headers.get(header));
        //    }
        //}

        // Emulate browser request
        //dataSourceFactory.getDefaultRequestProperties().set("accept", "*/*");
        //dataSourceFactory.getDefaultRequestProperties().set("accept-encoding", "identity"); // Next won't work: gzip, deflate, br
        //dataSourceFactory.getDefaultRequestProperties().set("accept-language", "en-US,en;q=0.9");
        //dataSourceFactory.getDefaultRequestProperties().set("dnt", "1");
        //dataSourceFactory.getDefaultRequestProperties().set("origin", "https://www.youtube.com");
        //dataSourceFactory.getDefaultRequestProperties().set("referer", "https://www.youtube.com/");
        //dataSourceFactory.getDefaultRequestProperties().set("sec-fetch-dest", "empty");
        //dataSourceFactory.getDefaultRequestProperties().set("sec-fetch-mode", "cors");
        //dataSourceFactory.getDefaultRequestProperties().set("sec-fetch-site", "cross-site");

        // WARN: Won't work on Exo 2.10!!!
        // Compress response (WARN: gzip, deflate or br aren't supported in dash urls)
        // dataSourceFactory.getDefaultRequestProperties().set("Accept-Encoding", AppConstants.ACCEPT_ENCODING);
    }

    // EXO: 2.10 - 2.12
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

    // EXO: 2.13
    //private static class StaticDashManifestParser extends DashManifestParser {
    //    @Override
    //    protected DashManifest buildMediaPresentationDescription(
    //            long availabilityStartTime,
    //            long durationMs,
    //            long minBufferTimeMs,
    //            boolean dynamic,
    //            long minUpdateTimeMs,
    //            long timeShiftBufferDepthMs,
    //            long suggestedPresentationDelayMs,
    //            long publishTimeMs,
    //            @Nullable ProgramInformation programInformation,
    //            @Nullable UtcTimingElement utcTiming,
    //            @Nullable ServiceDescriptionElement serviceDescription,
    //            @Nullable Uri location,
    //            List<Period> periods) {
    //        return new DashManifest(
    //                availabilityStartTime,
    //                durationMs,
    //                minBufferTimeMs,
    //                false,
    //                minUpdateTimeMs,
    //                timeShiftBufferDepthMs,
    //                suggestedPresentationDelayMs,
    //                publishTimeMs,
    //                programInformation,
    //                utcTiming,
    //                serviceDescription,
    //                location,
    //                periods);
    //    }
    //}
}
