package com.liskovsoft.smartyoutubetv2.common.vot;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.VotData;

import org.json.JSONObject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.schedulers.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VotClient {
    private static final String TAG = VotClient.class.getSimpleName();
    private static final int MAX_POLL_ATTEMPTS = 60;

    private final VotHttp mHttp = new VotHttp();
    private final VotData mVotData;
    @Nullable
    private VotSession mSession;

    public VotClient(Context context) {
        mVotData = VotData.instance(context);
    }

    public String translateToRussian(String youtubeUrl, long durationSec) throws IOException, VotException {
        VotProgress last = observeTranslation(youtubeUrl, durationSec)
                .blockingLast();
        if (last == null) {
            throw new VotException("Translation cancelled");
        }
        if (last.type == VotProgress.TYPE_READY && last.audioUrl != null) {
            return last.audioUrl;
        }
        if (last.type == VotProgress.TYPE_FAILED) {
            throw new VotException(last.message != null ? last.message : "Translation failed");
        }
        throw new VotException("Translation timeout");
    }

    public Observable<VotProgress> observeTranslation(String youtubeUrl, long durationSec) {
        return Observable.<VotProgress>create(emitter -> pollTranslation(emitter, youtubeUrl, durationSec, true))
                .subscribeOn(Schedulers.io());
    }

    private void pollTranslation(ObservableEmitter<VotProgress> emitter, String youtubeUrl, long durationSec,
                                 boolean allowAudioFallback) {
        try {
            VotTranslationResponse response = requestTranslation(youtubeUrl, durationSec, false);
            if (!processResponse(emitter, youtubeUrl, durationSec, allowAudioFallback, response)) {
                return;
            }

            int waitSec = Math.max(3, response.remainingTimeSec > 0 ? response.remainingTimeSec : 5);
            for (int i = 0; i < MAX_POLL_ATTEMPTS && !emitter.isDisposed(); i++) {
                sleep(waitSec);
                if (emitter.isDisposed()) {
                    return;
                }
                response = requestTranslation(youtubeUrl, durationSec, true);
                if (!processResponse(emitter, youtubeUrl, durationSec, allowAudioFallback, response)) {
                    return;
                }
                waitSec = Math.max(3, response.remainingTimeSec > 0 ? response.remainingTimeSec : waitSec);
            }
            if (!emitter.isDisposed()) {
                emitter.onNext(VotProgress.failed("Translation timeout"));
                emitter.onComplete();
            }
        } catch (IOException e) {
            if (!emitter.isDisposed()) {
                emitter.onError(e);
            }
        } catch (VotException e) {
            if (!emitter.isDisposed()) {
                emitter.onNext(VotProgress.failed(e.getMessage()));
                emitter.onComplete();
            }
        }
    }

    /** @return false if polling should stop (ready, failed, or disposed) */
    private boolean processResponse(ObservableEmitter<VotProgress> emitter, String youtubeUrl, long durationSec,
                                    boolean allowAudioFallback, VotTranslationResponse response)
            throws IOException, VotException {
        if (emitter.isDisposed()) {
            return false;
        }

        if (response.status == VotTranslationResponse.STATUS_SESSION_REQUIRED) {
            emitter.onNext(VotProgress.failed("auth required"));
            emitter.onComplete();
            return false;
        }

        if (response.status == VotTranslationResponse.STATUS_AUDIO_REQUESTED && allowAudioFallback) {
            handleAudioRequested(youtubeUrl, durationSec, response.translationId);
            pollTranslation(emitter, youtubeUrl, durationSec, false);
            return false;
        }

        if (response.isReady() && response.url != null && !response.url.isEmpty()) {
            emitter.onNext(VotProgress.ready(response.url));
            emitter.onComplete();
            return false;
        }

        if (response.status == VotTranslationResponse.STATUS_FAILED) {
            String msg = response.message != null ? response.message : "Translation failed";
            emitter.onNext(VotProgress.failed(msg));
            emitter.onComplete();
            return false;
        }

        if (response.isWaiting() || response.status == VotTranslationResponse.STATUS_AUDIO_REQUESTED) {
            emitter.onNext(VotProgress.waiting(response.remainingTimeSec, response.status));
            return true;
        }

        emitter.onNext(VotProgress.failed("Unexpected translation status: " + response.status));
        emitter.onComplete();
        return false;
    }

    private VotTranslationResponse requestTranslation(String youtubeUrl, double durationSec, boolean subsequent)
            throws IOException {
        boolean useLively = mVotData.isLivelyVoiceEnabled();
        byte[] body = VotProtobuf.encodeTranslationRequest(
                youtubeUrl,
                durationSec,
                VotConfig.REQUEST_LANG,
                VotConfig.RESPONSE_LANG,
                !subsequent,
                useLively
        );

        Map<String, String> headers = buildTranslateHeaders(body);
        byte[] raw = mHttp.postProtobuf("/video-translation/translate", body, headers);

        if (raw == null || raw.length == 0) {
            throw new IOException("Empty translation response");
        }
        return VotProtobuf.decodeTranslationResponse(raw);
    }

    private Map<String, String> buildTranslateHeaders(byte[] body) {
        Map<String, String> headers;
        if (mSession != null) {
            headers = VotHeaders.sessionTranslate(mSession, body, "/video-translation/translate");
        } else {
            headers = VotHeaders.simpleTranslate(body);
        }
        if (mVotData.isLivelyVoiceEnabled()) {
            headers = VotHeaders.merge(headers, VotHeaders.oauthHeader(mVotData.getOAuthToken()));
        }
        return headers;
    }

    private void handleAudioRequested(String youtubeUrl, long durationSec, @Nullable String translationId)
            throws IOException, VotException {
        if (translationId == null || translationId.isEmpty()) {
            VotTranslationResponse r = requestTranslation(youtubeUrl, durationSec, false);
            translationId = r.translationId;
        }
        if (translationId == null || translationId.isEmpty()) {
            throw new VotException("Missing translationId for audio upload");
        }

        ensureSession();
        requestFailAudio(youtubeUrl);
        uploadEmptyAudio(youtubeUrl, translationId);
    }

    private void requestFailAudio(String youtubeUrl) throws IOException, VotException {
        String json = "{\"video_url\":\"" + youtubeUrl.replace("\"", "\\\"") + "\"}";
        byte[] raw = mHttp.putJson("/video-translation/fail-audio-js", json,
                VotHeaders.simpleTranslate(json.getBytes(StandardCharsets.UTF_8)));
        if (raw == null) {
            throw new VotException("fail-audio-js: empty response");
        }
        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(text);
            if (obj.optInt("status", 0) != 1) {
                throw new VotException("fail-audio-js failed");
            }
        } catch (VotException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "fail-audio-js parse error: %s", e.getMessage());
        }
    }

    private void uploadEmptyAudio(String youtubeUrl, String translationId) throws IOException {
        byte[] body = VotProtobuf.encodeTranslationAudioRequest(
                youtubeUrl, translationId, VotConfig.FAKE_AUDIO_FILE_ID);
        mHttp.putProtobuf("/video-translation/audio", body,
                VotHeaders.sessionTranslate(mSession, body, "/video-translation/audio"));
    }

    private void ensureSession() throws IOException {
        if (mSession != null && System.currentTimeMillis() - mSession.createdAtMs < mSession.expiresSec * 1000L) {
            return;
        }
        String uuid = VotSignature.randomToken();
        byte[] body = VotProtobuf.encodeSessionRequest(uuid, "video-translation");
        byte[] raw = mHttp.postProtobuf("/session/create", body, VotHeaders.simpleTranslate(body));
        if (raw == null) {
            throw new IOException("Empty session response");
        }
        VotSession decoded = VotProtobuf.decodeSessionResponse(raw);
        decoded.uuid = uuid;
        decoded.createdAtMs = System.currentTimeMillis();
        mSession = decoded;
    }

    private void sleep(int sec) throws VotException {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VotException("Interrupted");
        }
    }
}
