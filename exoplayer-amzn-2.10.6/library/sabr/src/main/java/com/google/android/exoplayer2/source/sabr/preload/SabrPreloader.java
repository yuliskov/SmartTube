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
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Warms up a SABR video before it's opened (e.g. the next Shorts).<br/>
 * Makes the same first requests the player makes (init + first segment of each track) with a manifest
 * of its own, and records the responses into {@link SabrPreloadStore}. When the video is opened,
 * {@link SabrReplayDataSource} answers these requests from memory, so playback starts without the network.<br/>
 * The responses are parsed by a real {@link SabrStream} between the requests, so every request carries
 * the same state (buffered ranges, playback cookie) the player would send. The player parses the same
 * responses later and ends up with the same state, so its next requests continue the server session.
 */
public final class SabrPreloader {
    private static final String TAG = SabrPreloader.class.getSimpleName();
    private static final int READ_BUFFER_SIZE = 64 * 1024;
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
     * @param videoFormat format of this manifest the player will most likely select for the video
     * @param audioFormat format of this manifest the player will most likely select for the audio
     * @return whether the video has been warmed up
     */
    public static boolean preload(SabrManifest manifest, DataSource upstream, Format videoFormat, Format audioFormat, AtomicBoolean canceled) {
        if (manifest == null || manifest.dynamic || videoFormat == null || audioFormat == null) {
            return false; // live streams have nothing to warm up
        }

        int videoItag = Helpers.parseInt(videoFormat.id);
        int audioItag = Helpers.parseInt(audioFormat.id);

        // Same as the DefaultSabrChunkSource constructor of each track
        SabrStream videoStream = manifest.getSabrStream(C.TRACK_TYPE_VIDEO);
        videoStream.setFormatSelector(new FormatSelector("selected_video", false, videoFormat));
        SabrStream audioStream = manifest.getSabrStream(C.TRACK_TYPE_AUDIO);
        audioStream.setFormatSelector(new FormatSelector("selected_audio", false, audioFormat));

        if (SabrPreloadStore.contains(videoStream.getUrl())) {
            return true; // already warmed up
        }

        if (SabrPreloadStore.isPlayerSession(videoStream.getUrl())) {
            return false; // the player streams it already
        }

        Map<Integer, ArrayDeque<SabrPreloadStore.Response>> responses = new HashMap<>();
        responses.put(C.TRACK_TYPE_VIDEO, new ArrayDeque<>());
        responses.put(C.TRACK_TYPE_AUDIO, new ArrayDeque<>());
        String requestUrl = null;
        boolean completed = false;

        // NOTE: once a request is sent, the server session is started. Whatever is received must be handed to the player,
        // so it continues this session. Otherwise the player starts a second session with the same player response and
        // the server may stop sending media after the first minute (ATTESTATION_PENDING).
        try {
            // The order the player loads the chunks: init chunks first, then the first segments
            int[][] steps = {
                    {C.TRACK_TYPE_VIDEO, 1},
                    {C.TRACK_TYPE_AUDIO, 1},
                    {C.TRACK_TYPE_AUDIO, 0},
                    {C.TRACK_TYPE_VIDEO, 0}
            };

            for (int[] step : steps) {
                // Stop if the user has skipped it or the player has opened it meanwhile (it's using the network then)
                if (canceled.get() || SabrPreloadStore.isPlayerSession(videoStream.getUrl())) {
                    break;
                }

                int trackType = step[0];
                boolean isInit = step[1] == 1;
                SabrStream stream = trackType == C.TRACK_TYPE_VIDEO ? videoStream : audioStream;
                int iTag = trackType == C.TRACK_TYPE_VIDEO ? videoItag : audioItag;

                if (!isInit) {
                    stream.reset(iTag); // the first media chunk is a seek to the start (see DefaultSabrChunkSource#newMediaChunk)
                }

                requestUrl = manifest.getRequestUrl(trackType);
                VideoPlaybackAbrRequest request = isInit
                        ? manifest.createVideoPlaybackAbrRequest(trackType, true)
                        : manifest.createVideoPlaybackAbrRequest(trackType, false, 0);

                byte[] data = load(upstream, requestUrl, request.toByteArray());

                String error = consume(stream, data);

                if (error != null) {
                    // The player gets this response from the network itself and handles it as usual
                    Log.d(TAG, "Stop %s: %s (track %s, init %s)", manifest.getVideoId(), error, trackType, isInit);
                    break;
                }

                responses.get(trackType).add(new SabrPreloadStore.Response(
                        stream.getFormatSelector().getSelectedFormatId(), request.getClientAbrState().getPlayerTimeMs(), data));
            }

            completed = !canceled.get() && responses.get(C.TRACK_TYPE_VIDEO).size() + responses.get(C.TRACK_TYPE_AUDIO).size() == 4;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "Preload %s error: %s", manifest.getVideoId(), e.getMessage());
        } finally {
            int count = responses.get(C.TRACK_TYPE_VIDEO).size() + responses.get(C.TRACK_TYPE_AUDIO).size();

            if (count > 0) {
                SabrPreloadStore.put(requestUrl, responses);
                Log.d(TAG, "Preloaded %s: video itag %s, audio itag %s, responses %s/4", manifest.getVideoId(), videoItag, audioItag, count);
            }
        }

        return completed;
    }

    private static byte[] load(DataSource upstream, String url, byte[] body) throws IOException {
        DataSpec dataSpec = new DataSpec(
                Uri.parse(url),
                DataSpec.HTTP_METHOD_POST,
                body,
                0, 0, C.LENGTH_UNSET,
                null,
                0,
                SABR_HEADERS);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            upstream.open(dataSpec);
            byte[] buffer = new byte[READ_BUFFER_SIZE];
            int read;

            while ((read = upstream.read(buffer, 0, buffer.length)) != C.RESULT_END_OF_INPUT) {
                output.write(buffer, 0, read);
            }
        } finally {
            upstream.close();
        }

        return output.toByteArray();
    }

    /**
     * Parse the response like the player does, to update the stream state for the next request.
     * @return null or the reason the response can't be used (e.g. backoff or player response reload requested)
     */
    @Nullable
    private static String consume(SabrStream stream, byte[] data) throws IOException, InterruptedException {
        ByteArrayDataSource dataSource = new ByteArrayDataSource(data);
        dataSource.open(new DataSpec(Uri.EMPTY));
        ExtractorInput input = new DefaultExtractorInput(dataSource, 0, data.length);
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
