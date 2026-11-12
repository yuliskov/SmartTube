package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.sabr.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.sabr.manifest.Representation;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectionPredictor;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifestParser;
import com.google.android.exoplayer2.source.sabr.preload.SabrPreloader;
import com.google.android.exoplayer2.upstream.DataSource;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Warms up the next SABR Shorts (see {@link ShortsPreloader}): its first SABR responses are recorded,
 * so the player doesn't wait for the network when the Shorts is opened.<br/>
 * See {@link SabrPreloader}.
 */
public final class ShortsSabrPreloader {
    private static final String TAG = ShortsSabrPreloader.class.getSimpleName();
    private static DataSource.Factory sUpstreamFactory;

    private ShortsSabrPreloader() {
    }

    /**
     * The same network stack the player uses for SABR (without the replay layer)
     */
    public static synchronized void setUpstreamFactory(DataSource.Factory upstreamFactory) {
        sUpstreamFactory = upstreamFactory;
    }

    /**
     * Blocking, call from a background thread.
     * @param videoItag itag of the video track that is playing now (the player will most likely pick the same quality)
     * @param audioItag itag of the audio track that is playing now
     * @param canceled stops the warm-up (e.g. the user has skipped the video)
     * @return whether the video has been warmed up
     */
    public static boolean preload(Context context, MediaItemFormatInfo formatInfo, @Nullable String videoItag, @Nullable String audioItag,
                                  AtomicBoolean canceled) {
        if (context == null || formatInfo == null || formatInfo.isLive() || canceled.get()) {
            return false;
        }

        Context appContext = context.getApplicationContext();
        DataSource.Factory upstreamFactory;

        synchronized (ShortsSabrPreloader.class) {
            upstreamFactory = sUpstreamFactory;
        }

        if (upstreamFactory == null) {
            return false;
        }

        String videoId = formatInfo.getVideoId();

        try {
            // A manifest of its own. The player creates a new one when the video is opened.
            SabrManifest manifest = new SabrManifestParser().parse(formatInfo);

            // The format the player will select for this video. Fallback: the quality of the previous one.
            // NOTE: a wrong guess is safe. The player loads the mismatched track from the network.
            Format videoFormat = selectFormat(appContext, manifest, C.TRACK_TYPE_VIDEO, TrackSelectorManager.RENDERER_INDEX_VIDEO, videoItag);
            Format audioFormat = selectFormat(appContext, manifest, C.TRACK_TYPE_AUDIO, TrackSelectorManager.RENDERER_INDEX_AUDIO, audioItag);

            if (videoFormat == null || audioFormat == null) {
                Log.d(TAG, "Skip %s: can't predict the formats", videoId);
                return false;
            }

            return SabrPreloader.preload(manifest, upstreamFactory, videoFormat, audioFormat, canceled);
        } catch (Exception e) { // malformed format info, parser or network stack failure
            Log.e(TAG, "SABR preload %s error: %s", videoId, e.getMessage());
            return false;
        }
    }

    @Nullable
    private static Format selectFormat(Context appContext, SabrManifest manifest, int trackType, int rendererIndex, @Nullable String fallbackItag) {
        List<List<Format>> formats = getFormats(manifest, trackType);
        Format predicted = TrackSelectionPredictor.predict(appContext, rendererIndex, formats);

        return predicted != null ? predicted : findFormat(formats, fallbackItag);
    }

    @Nullable
    private static Format findFormat(List<List<Format>> formatGroups, @Nullable String itag) {
        if (itag == null) {
            return null;
        }

        for (List<Format> group : formatGroups) {
            for (Format format : group) {
                if (itag.equals(format.id)) {
                    return format;
                }
            }
        }

        return null;
    }

    private static List<List<Format>> getFormats(SabrManifest manifest, int trackType) {
        List<List<Format>> result = new ArrayList<>();

        if (manifest == null || manifest.getPeriodCount() == 0) {
            return result;
        }

        for (AdaptationSet adaptationSet : manifest.getPeriod(0).adaptationSets) {
            if (adaptationSet.type != trackType) {
                continue;
            }

            List<Format> group = new ArrayList<>();

            for (Representation representation : adaptationSet.representations) {
                group.add(representation.format);
            }

            result.add(group);
        }

        return result;
    }
}
