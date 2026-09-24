package com.liskovsoft.smartyoutubetv2.common.vot;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import okio.Buffer;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

public class VotClientTest {
    @Test
    public void sendsOAuthTokenOnlyToTranslationEndpoint() throws Exception {
        String token = "y0_abcdefghijklmnopqrstuvwxyz1234567890";
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            boolean translate = chain.request().url().encodedPath().endsWith("/translate");
            if (translate) assertEquals("OAuth " + token, chain.request().header("Authorization"));
            else assertNull(chain.request().header("Authorization"));
            byte[] body = translate ? new byte[]{32, 1, 10, 1, 'u'} : new byte[]{10, 1, 's'};
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(null, body)).build();
        }).build();
        VotClient client = new VotClient(token);
        Field field = VotClient.class.getDeclaredField("mHttp");
        field.setAccessible(true);
        field.set(client, http);

        assertEquals("u", client.translate("video", 1_000, null, "en", "ru", true, null));
    }

    @Test
    public void waitsForPartialAudioToFinish() throws IOException {
        VotClient.Reply partial = VotClient.parse(new byte[]{32, 5, 40, 12, 10, 1, 'u'}, false);
        VotClient.Reply complete = VotClient.parse(new byte[]{32, 5, 40, 0, 10, 1, 'u'}, false);

        assertFalse(partial.isComplete());
        assertTrue(complete.isComplete());
        assertEquals("u", complete.audioUrl);
    }

    @Test
    public void authRequiredFallsBackToStandardVoice() throws IOException {
        byte[] hint = "Доступна обычная озвучка".getBytes(StandardCharsets.UTF_8);
        byte[] response = new byte[4 + hint.length];
        response[0] = 32; // status
        response[1] = 7;  // session required
        response[2] = 74; // message
        response[3] = (byte) hint.length;
        System.arraycopy(hint, 0, response, 4, hint.length);

        VotClient.Reply reply = VotClient.parse(response, false);
        assertTrue(reply.shouldUseStandardVoice(true));
        assertFalse(reply.shouldUseStandardVoice(false));
        assertEquals(7, reply.status);

        response[1] = 2;
        assertTrue(VotClient.parse(response, false).shouldUseStandardVoice(true));
    }

    @Test
    public void standardVoiceRetryStartsNewRequestAfterLiveVoiceRequiresAccount() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            byte[] body;
            if (chain.request().url().encodedPath().endsWith("/session/create")) {
                assertNull(chain.request().header("Authorization"));
                body = new byte[]{10, 1, 's'};
            } else {
                Buffer payload = new Buffer();
                chain.request().body().writeTo(payload);
                assertEquals(1, VotClient.parse(payload.readByteArray(), false).waitSeconds);
                int attempt = requests.getAndIncrement();
                if (attempt == 0) assertEquals("OAuth y0_abcdefghijklmnopqrstuvwxyz1234567890",
                        chain.request().header("Authorization"));
                else assertNull(chain.request().header("Authorization"));
                body = attempt == 0
                        ? new byte[]{32, 7} : new byte[]{32, 1, 10, 1, 'u'};
            }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK")
                    .body(ResponseBody.create(null, body)).build();
        }).build();
        VotClient client = new VotClient("y0_abcdefghijklmnopqrstuvwxyz1234567890");
        Field field = VotClient.class.getDeclaredField("mHttp");
        field.setAccessible(true);
        field.set(client, http);

        assertEquals("u", client.translate("video", 1_000, null, "en", "ru", true, null));
        assertEquals(2, requests.get());
    }

    @Test(expected = IOException.class)
    public void rejectsTruncatedResponse() throws IOException {
        VotClient.parse(new byte[]{10, 5, 'x'}, false);
    }

    @Test(expected = InterruptedException.class)
    public void cancelledRequestDoesNotReachNetwork() throws Exception {
        VotClient client = new VotClient();
        client.cancel();
        client.translate("video", 1_000, null, "en", "ru", true, null);
    }

    @Test
    public void translationRequestUsesSelectedTargetLanguage() {
        byte[] body = VotClient.encodeTranslationRequest("url", 5, true, "en", "kk", false);
        boolean found = false;
        for (int i = 0; i < body.length - 3; i++) {
            if (body[i] == 114 && body[i + 1] == 2 && body[i + 2] == 'k' && body[i + 3] == 'k') {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void audioChunkMatchesNestedProtobufLayout() throws IOException {
        byte[] body = VotClient.encodeAudioChunk(
                new ByteArrayInputStream(new byte[]{'a', 'b'}), 2, 0, 1, "t", "u", "f");
        assertArrayEquals(new byte[]{
                10, 1, 't', 18, 1, 'u', 34, 15,
                10, 6, 8, 0, 18, 2, 'a', 'b', 16, 1, 26, 1, 'f', 32, 1
        }, body);
    }

    @Test
    public void audioChunkEncodesLengthsBeyondOneByte() throws IOException {
        byte[] audio = new byte[130];
        for (int i = 0; i < audio.length; i++) audio[i] = (byte) i;
        byte[] body = VotClient.encodeAudioChunk(
                new ByteArrayInputStream(audio), audio.length, 1, 2, "t", "u", "f");

        assertEquals((byte) 34, body[6]); // nested chunk field
        assertEquals((byte) 0x91, body[7]);
        assertEquals((byte) 0x01, body[8]); // nested chunk length: 145
        assertEquals((byte) 10, body[9]); // partial chunk field
        assertEquals((byte) 0x87, body[10]);
        assertEquals((byte) 0x01, body[11]); // partial chunk length: 135
        assertArrayEquals(audio, java.util.Arrays.copyOfRange(body, 17, 147));
    }

    @Test(expected = IOException.class)
    public void audioChunkRejectsTruncatedSource() throws IOException {
        VotClient.encodeAudioChunk(new ByteArrayInputStream(new byte[]{1}),
                2, 0, 1, "t", "u", "f");
    }
}
