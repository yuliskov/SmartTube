package com.liskovsoft.smartyoutubetv2.common.vot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.liskovsoft.sharedutils.okhttp.OkHttpManager;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Small client for the unofficial Yandex Browser video translation endpoint. */
public final class VotClient {
    public static final class AuthRequiredException extends IOException {
        public AuthRequiredException() { super("Yandex account required"); }
    }
    private static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(String path, int status) {
            super("Translation service " + path + ": HTTP " + status);
            this.status = status;
        }
    }

    private static final String HOST = "https://api.browser.yandex.ru";
    private static final String VERSION = "26.8.3.1002";
    private static final int CHUNK_SIZE = 5_295_308;
    private static final String SIGNING_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 YaBrowser/26.8.0.0 Safari/537.36";
    private static final MediaType PROTOBUF = MediaType.parse("application/x-protobuf");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private OkHttpClient mHttp;

    public interface ProgressListener {
        void onSourceAudioUpload();
    }
    private String mUuid;
    private String mSessionKey;
    private volatile Call mCall;
    private volatile Call mSourceCall;
    private volatile Thread mWorker;
    private volatile boolean mCancelled;

    /** Uploads source audio only when requested by the translation service. */
    public String translate(String videoId, long durationMs, VotAudioSource source,
                            String sourceLanguage, String targetLanguage, boolean preferLively,
                            ProgressListener progress) throws Exception {
        mWorker = Thread.currentThread();
        try {
            return translateInternal(videoId, durationMs, source, sourceLanguage,
                    targetLanguage, preferLively, progress);
        } finally {
            mWorker = null;
        }
    }

    public void cancel() {
        mCancelled = true;
        Call call = mCall;
        if (call != null) call.cancel();
        Call sourceCall = mSourceCall;
        if (sourceCall != null) sourceCall.cancel();
        Thread worker = mWorker;
        if (worker != null) worker.interrupt();
    }

    private OkHttpClient http() {
        if (mHttp == null) {
            mHttp = OkHttpManager.instance().getClient().newBuilder()
                    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(90, TimeUnit.SECONDS).build();
        }
        return mHttp;
    }

    private String translateInternal(String videoId, long durationMs, VotAudioSource source,
                                     String sourceLanguage, String targetLanguage, boolean preferLively,
                                     ProgressListener progress) throws Exception {
        String url = "https://www.youtube.com/watch?v=" + videoId;
        createSession();
        boolean lively = preferLively;
        boolean uploaded = false;
        boolean firstRequest = true;
        for (int attempt = 0; attempt < 36; attempt++) {
            if (mCancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            Reply reply;
            try {
                reply = request(url, durationMs / 1000d, firstRequest,
                        sourceLanguage, targetLanguage, lively);
            } catch (HttpFailure failure) {
                if (!lively || failure.status != 400) throw failure;
                lively = false;
                firstRequest = true;
                uploaded = false;
                continue;
            }
            firstRequest = false;
            if (reply.isComplete()) {
                return reply.audioUrl;
            }
            if (reply.shouldUseStandardVoice(lively)) {
                lively = false;
                firstRequest = true;
                uploaded = false;
                continue;
            }
            if (reply.status == 6 && !uploaded) {
                if (source == null || reply.translationId == null || reply.translationId.isEmpty()) {
                    throw new IOException("Source audio is unavailable for upload");
                }
                if (progress != null) progress.onSourceAudioUpload();
                upload(url, reply.translationId, source);
                uploaded = true;
                continue;
            }
            if (reply.status != 2 && reply.status != 3 && reply.status != 5 && reply.status != 6) {
                if (reply.status == 7) throw new AuthRequiredException();
                throw new IOException("Translation unavailable (status " + reply.status + ")");
            }
            Thread.sleep(Math.min(15, Math.max(3, reply.waitSeconds)) * 1000L);
        }
        throw new IOException("Translation timed out");
    }

    private Reply request(String url, double duration, boolean first,
                          String sourceLanguage, String targetLanguage, boolean lively) throws Exception {
        return parse(send("/video-translation/translate",
                encodeTranslationRequest(url, duration, first, sourceLanguage, targetLanguage, lively),
                false, true), false);
    }

    static byte[] encodeTranslationRequest(String url, double duration, boolean first,
                                           String sourceLanguage, String targetLanguage, boolean lively) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, 3, url);
        if (first) writeVarintField(out, 5, 1);
        writeVarint(out, 6 * 8 + 1);
        byte[] durationBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putDouble(duration).array();
        out.write(durationBytes, 0, durationBytes.length);
        writeVarintField(out, 7, 1);
        writeString(out, 8, sourceLanguage);
        writeString(out, 14, targetLanguage);
        writeVarintField(out, 15, 1);
        writeVarintField(out, 16, 2);
        if (lively) writeVarintField(out, 18, 1);
        return out.toByteArray();
    }

    private void createSession() throws Exception {
        mUuid = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, 1, mUuid);
        writeString(out, 2, "video-translation");
        mSessionKey = parse(send("/session/create", out.toByteArray(), false, false), true).sessionKey;
        if (mSessionKey == null || mSessionKey.isEmpty()) throw new IOException("Translation session unavailable");
    }

    private void upload(String url, String translationId, VotAudioSource source) throws Exception {
        Request request = new Request.Builder().url(source.url).header("User-Agent", USER_AGENT).get().build();
        Call call = http().newCall(request);
        mSourceCall = call;
        if (mCancelled) call.cancel();
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Could not download source audio: HTTP " + response.code());
            }
            long length = response.body().contentLength();
            if (length <= 0) length = source.size;
            if (length <= 0) throw new IOException("Source audio size is unknown");
            long parts = (length + CHUNK_SIZE - 1) / CHUNK_SIZE;
            if (parts > Integer.MAX_VALUE) throw new IOException("Source audio is too large");
            String fileId = "random-web_abr-" + UUID.randomUUID();
            try (InputStream stream = response.body().byteStream()) {
                for (int index = 0; index < parts; index++) {
                    if (mCancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    int count = (int) Math.min(CHUNK_SIZE, length - (long) index * CHUNK_SIZE);
                    byte[] body = encodeAudioChunk(stream, count, index, parts, translationId, url, fileId);
                    Reply reply = parse(send("/video-translation/audio", body, true, true), false);
                    if (reply.uploadStatus != 1 && reply.uploadStatus != 2) {
                        throw new IOException("Service rejected source audio (status " + reply.uploadStatus + ")");
                    }
                }
            }
        } finally {
            mSourceCall = null;
        }
    }

    /** Writes nested protobuf lengths first, then streams audio into one body buffer. */
    static byte[] encodeAudioChunk(InputStream stream, int count, int index, long parts,
                                   String translationId, String url, String fileId) throws IOException {
        byte[] fileIdBytes = fileId.getBytes(UTF8);
        int partialSize = 1 + varintSize(index) + 1 + varintSize(count) + count;
        int chunkSize = 1 + varintSize(partialSize) + partialSize
                + 1 + varintSize(parts) + 1 + varintSize(fileIdBytes.length) + fileIdBytes.length + 2;
        ByteArrayOutputStream body = new ByteArrayOutputStream(count + 256);
        writeString(body, 1, translationId);
        writeString(body, 2, url);
        writeVarint(body, 4 * 8 + 2);
        writeVarint(body, chunkSize);
        writeVarint(body, 1 * 8 + 2);
        writeVarint(body, partialSize);
        writeVarintField(body, 1, index);
        writeVarint(body, 2 * 8 + 2);
        writeVarint(body, count);
        byte[] buffer = new byte[8_192];
        for (int remaining = count; remaining > 0;) {
            int read = stream.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) throw new IOException("Source audio ended during upload");
            body.write(buffer, 0, read);
            remaining -= read;
        }
        writeVarintField(body, 2, parts);
        writeString(body, 3, fileId);
        writeVarintField(body, 4, 1);
        return body.toByteArray();
    }

    private static int varintSize(long value) {
        int size = 1;
        while ((value & ~0x7fL) != 0) { value >>>= 7; size++; }
        return size;
    }

    private byte[] send(String path, byte[] body, boolean put, boolean session) throws Exception {
        if (mCancelled) throw new InterruptedException();
        Request.Builder builder = new Request.Builder().url(HOST + path)
                .header("Accept", "application/x-protobuf")
                .header("User-Agent", USER_AGENT)
                .header("Vtrans-Signature", sign(body));
        if (session) {
            String token = mUuid + ":" + path + ":" + VERSION;
            builder.header("Sec-Vtrans-Sk", mSessionKey)
                    .header("Sec-Vtrans-Token", sign(token.getBytes(UTF8)) + ":" + token);
        }
        RequestBody payload = RequestBody.create(PROTOBUF, body);
        Request req = (put ? builder.put(payload) : builder.post(payload)).build();
        Call call = http().newCall(req);
        mCall = call;
        if (mCancelled) call.cancel();
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new HttpFailure(path, response.code());
            }
            return response.body().bytes();
        } finally {
            mCall = null;
        }
    }

    private static String sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_KEY.getBytes(UTF8), "HmacSHA256"));
        StringBuilder signature = new StringBuilder();
        for (byte b : mac.doFinal(body)) signature.append(String.format("%02x", b & 0xff));
        return signature.toString();
    }

    static Reply parse(byte[] bytes, boolean session) throws IOException {
        Reply reply = new Reply();
        int[] offset = {0};
        while (offset[0] < bytes.length) {
            long tag = readVarint(bytes, offset);
            int field = (int) (tag >> 3), wire = (int) (tag & 7);
            if (wire == 0) {
                long value = readVarint(bytes, offset);
                if (field == 4) reply.status = (int) value;
                if (field == 5) reply.waitSeconds = (int) value;
                if (field == 1) reply.uploadStatus = (int) value;
            } else if (wire == 2) {
                long length = readVarint(bytes, offset);
                if (length < 0 || length > bytes.length - offset[0]) throw new IOException("Invalid VOT response");
                if (field == 1) {
                    String value = new String(bytes, offset[0], (int) length, UTF8);
                    if (session) reply.sessionKey = value; else reply.audioUrl = value;
                }
                if (field == 7) reply.translationId = new String(bytes, offset[0], (int) length, UTF8);
                if (field == 9) reply.message = new String(bytes, offset[0], (int) length, UTF8);
                offset[0] += (int) length;
            } else if (wire == 1 && bytes.length - offset[0] >= 8) {
                offset[0] += 8;
            } else if (wire == 5 && bytes.length - offset[0] >= 4) {
                offset[0] += 4;
            } else {
                throw new IOException("Invalid VOT response field");
            }
        }
        return reply;
    }

    private static long readVarint(byte[] bytes, int[] offset) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64 && offset[0] < bytes.length; shift += 7) {
            int b = bytes[offset[0]++] & 0xff;
            value |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("Invalid VOT varint");
    }

    private static void writeString(ByteArrayOutputStream out, int field, String value) {
        writeBytes(out, field, value.getBytes(UTF8));
    }

    private static void writeBytes(ByteArrayOutputStream out, int field, byte[] bytes) {
        writeVarint(out, field * 8 + 2);
        writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void writeVarintField(ByteArrayOutputStream out, int field, long value) {
        writeVarint(out, field * 8);
        writeVarint(out, value);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7fL) != 0) {
            out.write(((int) value & 0x7f) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }

    static final class Reply {
        int status;
        int waitSeconds;
        int uploadStatus;
        String audioUrl;
        String translationId;
        String sessionKey;
        String message;

        boolean isComplete() {
            return audioUrl != null && (status == 1 || status == 5 && waitSeconds < 1);
        }

        boolean shouldUseStandardVoice(boolean lively) {
            // The API sends this literal phrase in Russian regardless of the app locale.
            return lively && (status == 7 || message != null
                    && message.toLowerCase(Locale.ROOT).contains("обычная озвучка"));
        }
    }
}
