package com.google.android.exoplayer2.source.sabr.preload;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.DefaultExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.parser.parts.MediaSegmentDataSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.RefreshPlayerResponseSabrPart;
import com.google.android.exoplayer2.source.sabr.parser.parts.SabrPart;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.VideoPlaybackAbrRequest;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Warms up a SABR video before it's opened (e.g. the next Shorts).<br/>
 * Makes the same first requests the player makes (init + first segment of each track, the tracks in parallel)
 * with a manifest of its own, and records the responses into {@link SabrPreloadStore}. When the video is opened,
 * {@link SabrReplayDataSource} answers these requests from memory, so playback starts without the network.<br/>
 * The responses are parsed by a real {@link SabrStream} between the requests, so every request carries
 * the same state (buffered ranges, playback cookie) the player would send. The player parses the same
 * responses later and ends up with the same state, so its next requests continue the server session.
 */
public final class SabrPreloader {
    private static final String TAG = SabrPreloader.class.getSimpleName();
    /**
     * Init chunk + first segment
     */
    private static final int REQUESTS_PER_TRACK = 2;
    /**
     * Rough length of the media a response carries. Used to estimate the response size by the bitrate.
     */
    private static final int ESTIMATED_RESPONSE_SEC = 6;
    /**
     * The responses are kept in memory. The start of a Shorts is ~1-2 MB, so anything much bigger is unexpected.
     */
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    private static final Map<String, String> SABR_HEADERS = new HashMap<>();

    static {
        // Same as DefaultSabrChunkSource
        SABR_HEADERS.put("Content-Type", "application/x-protobuf");
        SABR_HEADERS.put("Accept", "application/vnd.yt-ump");
    }

    private SabrPreloader() {
    }

    /**
     * Blocking. Call from a background thread.
     * @param manifest new manifest of the video (don't pass the one used by the player)
     * @param upstreamFactory network stack of the player (each track gets a data source of its own)
     * @param videoFormat format of this manifest the player will most likely select for the video
     * @param audioFormat format of this manifest the player will most likely select for the audio
     * @return whether the video has been warmed up
     */
    public static boolean preload(SabrManifest manifest, DataSource.Factory upstreamFactory, Format videoFormat, Format audioFormat,
                                  AtomicBoolean canceled) {
        if (manifest == null || manifest.dynamic || videoFormat == null || audioFormat == null) {
            return false; // live streams have nothing to warm up
        }

        // Same as the DefaultSabrChunkSource constructor of each track
        SabrStream videoStream = manifest.getSabrStream(C.TRACK_TYPE_VIDEO);
        videoStream.setFormatSelector(new FormatSelector("selected_video", false, videoFormat));
        SabrStream audioStream = manifest.getSabrStream(C.TRACK_TYPE_AUDIO);
        audioStream.setFormatSelector(new FormatSelector("selected_audio", false, audioFormat));

        // The url the player starts with. The server may move the session to another url later.
        String sessionUrl = videoStream.getUrl();

        if (SabrPreloadStore.contains(sessionUrl)) {
            return true; // already warmed up
        }

        if (SabrPreloadStore.isPlayerSession(sessionUrl)) {
            return false; // the player streams it already
        }

        // Stay within the memory limit. High bitrate formats (1440p60+) take up to 8 MB per response.
        int requestsPerTrack = getRequestsPerTrack(videoFormat, audioFormat);

        if (requestsPerTrack == 0) {
            Log.d(TAG, "Skip %s: not enough memory for video itag %s", manifest.getVideoId(), videoFormat.id);
            return false; // no request is sent, so the player starts the session itself as usual
        }

        // Stops both tracks: the user has skipped the video, the player has opened it or one of the tracks has failed
        AtomicBoolean stopped = new AtomicBoolean();
        TrackWarmUp video = new TrackWarmUp(manifest, C.TRACK_TYPE_VIDEO, videoStream, Helpers.parseInt(videoFormat.id),
                upstreamFactory.createDataSource(), sessionUrl, requestsPerTrack, canceled, stopped);
        TrackWarmUp audio = new TrackWarmUp(manifest, C.TRACK_TYPE_AUDIO, audioStream, Helpers.parseInt(audioFormat.id),
                upstreamFactory.createDataSource(), sessionUrl, requestsPerTrack, canceled, stopped);

        // The tracks are independent, the player loads them in parallel too. Halves the warm-up time.
        Thread audioThread = new Thread(audio, "SabrPreloadAudio");
        audioThread.start();
        video.run();
        joinUninterruptibly(audioThread, stopped);

        // NOTE: once a request is sent, the server session is started. Whatever is received must be handed to the player,
        // so it continues this session. Otherwise the player starts a second session with the same player response and
        // the server may stop sending media after the first minute (ATTESTATION_PENDING).
        int count = video.responses.size() + audio.responses.size();

        if (count > 0) {
            Map<Integer, ArrayDeque<SabrPreloadStore.Response>> responses = new HashMap<>();
            responses.put(C.TRACK_TYPE_VIDEO, video.responses);
            responses.put(C.TRACK_TYPE_AUDIO, audio.responses);
            SabrPreloadStore.put(sessionUrl, responses);
            Log.d(TAG, "Preloaded %s: video itag %s, audio itag %s, responses %s/%s",
                    manifest.getVideoId(), videoFormat.id, audioFormat.id, count, 2 * requestsPerTrack);
        }

        return count == 2 * requestsPerTrack;
    }

    /**
     * How many of the first requests fit into the free memory of the store.<br/>
     * The init response carries the start of the media too, so even one request per track gives a fast start.
     * @return {@link #REQUESTS_PER_TRACK}, 1 (init only) or 0 (don't warm up)
     */
    private static int getRequestsPerTrack(Format videoFormat, Format audioFormat) {
        // Unknown bitrate: can't estimate. The response size limit still applies.
        long bitrate = Math.max(videoFormat.bitrate, 0) + Math.max(audioFormat.bitrate, 0);
        long responsesBytes = bitrate / 8 * ESTIMATED_RESPONSE_SEC; // one response of each track
        long freeBytes = SabrPreloadStore.getFreeBytes();

        if (responsesBytes * REQUESTS_PER_TRACK <= freeBytes) {
            return REQUESTS_PER_TRACK;
        }

        return responsesBytes <= freeBytes ? 1 : 0;
    }

    /**
     * The first requests of one track, in the order the player sends them: the init chunk, then the first segment
     */
    private static final class TrackWarmUp implements Runnable {
        final ArrayDeque<SabrPreloadStore.Response> responses = new ArrayDeque<>();
        private final SabrManifest manifest;
        private final int trackType;
        private final SabrStream stream;
        private final int iTag;
        private final DataSource upstream;
        private final String sessionUrl;
        private final int requestCount;
        private final AtomicBoolean canceled;
        private final AtomicBoolean stopped;

        TrackWarmUp(SabrManifest manifest, int trackType, SabrStream stream, int iTag, DataSource upstream, String sessionUrl,
                    int requestCount, AtomicBoolean canceled, AtomicBoolean stopped) {
            this.manifest = manifest;
            this.trackType = trackType;
            this.stream = stream;
            this.iTag = iTag;
            this.upstream = upstream;
            this.sessionUrl = sessionUrl;
            this.requestCount = requestCount;
            this.canceled = canceled;
            this.stopped = stopped;
        }

        @Override
        public void run() {
            try {
                for (int i = 0; i < requestCount; i++) {
                    // Stop if the user has skipped it or the player has opened it meanwhile (it's using the network then)
                    if (canceled.get() || stopped.get() || SabrPreloadStore.isPlayerSession(sessionUrl)) {
                        break;
                    }

                    boolean isInit = i == 0;

                    if (!isInit) {
                        stream.reset(iTag); // the first media chunk is a seek to the start (see DefaultSabrChunkSource#newMediaChunk)
                    }

                    String requestUrl = manifest.getRequestUrl(trackType);
                    VideoPlaybackAbrRequest request = isInit
                            ? manifest.createVideoPlaybackAbrRequest(trackType, true)
                            : manifest.createVideoPlaybackAbrRequest(trackType, false, 0);

                    SabrResponseData data = load(upstream, requestUrl, request.toByteArray());

                    String error = consume(stream, data);

                    if (error != null) {
                        // The player gets this response from the network itself and handles it as usual
                        Log.d(TAG, "Stop %s: %s (track %s, init %s)", manifest.getVideoId(), error, trackType, isInit);
                        stopped.set(true);
                        break;
                    }

                    responses.add(new SabrPreloadStore.Response(
                            stream.getFormatSelector().getSelectedFormatId(), request.getClientAbrState().getPlayerTimeMs(), data));
                }
            } catch (InterruptedException e) {
                stopped.set(true);
                Thread.currentThread().interrupt();
            } catch (Exception | OutOfMemoryError e) { // network, malformed response, low memory device
                stopped.set(true);
                Log.e(TAG, "Preload %s error (track %s): %s", manifest.getVideoId(), trackType, e.getMessage());
            }
        }
    }

    /**
     * The responses of the thread are needed even if the caller is interrupted (they belong to a started server session)
     */
    private static void joinUninterruptibly(Thread thread, AtomicBoolean stopped) {
        boolean interrupted = false;

        while (true) {
            try {
                thread.join();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
                stopped.set(true); // finishes after the request it's doing
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static SabrResponseData load(DataSource upstream, String url, byte[] body) throws IOException {
        DataSpec dataSpec = new DataSpec(
                Uri.parse(url),
                DataSpec.HTTP_METHOD_POST,
                body,
                0, 0, C.LENGTH_UNSET,
                null,
                0,
                SABR_HEADERS);

        try {
            upstream.open(dataSpec);

            return SabrResponseData.readFrom(upstream, MAX_RESPONSE_BYTES);
        } finally {
            Util.closeQuietly(upstream); // don't hide the error of the request
        }
    }

    /**
     * Parse the response like the player does, to update the stream state for the next request.
     * @return null or the reason the response can't be used (e.g. backoff or player response reload requested)
     */
    @Nullable
    private static String consume(SabrStream stream, SabrResponseData data) throws IOException, InterruptedException {
        DataSource dataSource = data.createDataSource();
        dataSource.open(new DataSpec(Uri.EMPTY));
        ExtractorInput input = new DefaultExtractorInput(dataSource, 0, data.length());
        while (true) {
            SabrPart part = stream.parse(input);

            if (part == null) {
                break;
            }

            if (part instanceof RefreshPlayerResponseSabrPart) {
                return "player response reload requested";
            }

            if (part instanceof MediaSegmentDataSabrPart) {
                MediaSegmentDataSabrPart media = (MediaSegmentDataSabrPart) part;
                media.data.skipFully(media.contentLength);
            }
        }

        // NOTE: an empty segment response is fine (e.g. the init response already contained the whole short video).
        // The player gets the same response from the network.

        if (stream.getBackoffTimeMs() > 0) {
            return "backoff " + stream.getBackoffTimeMs() + " ms";
        }

        return null;
    }
}
