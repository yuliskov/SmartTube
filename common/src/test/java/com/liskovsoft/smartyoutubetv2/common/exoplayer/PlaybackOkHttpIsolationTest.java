package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.sharedutils.okhttp.interceptors.UnzippingInterceptor;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

public class PlaybackOkHttpIsolationTest {
    private static final String BODY_LOGGER = "okhttp3.logging.HttpLoggingInterceptor";
    private static final String PROFILER =
            "com.localebro.okhttpprofiler.OkHttpProfilerInterceptor";
    private static final String UNZIPPING =
            "com.liskovsoft.sharedutils.okhttp.interceptors.UnzippingInterceptor";

    @Test
    public void mediaClientRemovesFullBodyDebugInterceptorsButKeepsTransportInterceptors()
            throws Exception {
        OkHttpClient globalClient = new OkHttpClient.Builder()
                .addInterceptor(newInterceptor(PROFILER))
                .addInterceptor(newInterceptor(BODY_LOGGER))
                .addInterceptor(new UnzippingInterceptor())
                .build();

        // This fixture establishes the production defect: debug builds add both unsafe
        // full-body interceptors to the shared client used before playback isolation.
        assertTrue(hasInterceptor(globalClient, PROFILER));
        assertTrue(hasInterceptor(globalClient, BODY_LOGGER));
        assertTrue(hasInterceptor(globalClient, UNZIPPING));

        OkHttpClient playbackClient =
                PlaybackOkHttpClient.withoutUnsafeDebugInterceptors(globalClient);

        assertFalse(hasInterceptor(playbackClient, PROFILER));
        assertFalse(hasInterceptor(playbackClient, BODY_LOGGER));
        assertTrue(hasInterceptor(playbackClient, UNZIPPING));
        assertSame(globalClient.connectionPool(), playbackClient.connectionPool());
        assertSame(globalClient.dispatcher(), playbackClient.dispatcher());
        assertSame(globalClient.dns(), playbackClient.dns());
    }

    @Test
    public void mediaClientIsReusedWhenItHasNoUnsafeDebugInterceptors() {
        OkHttpClient globalClient = new OkHttpClient.Builder()
                .addInterceptor(new UnzippingInterceptor())
                .build();

        assertSame(globalClient,
                PlaybackOkHttpClient.withoutUnsafeDebugInterceptors(globalClient));
    }

    private static Interceptor newInterceptor(String className) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singletonField = unsafeClass.getDeclaredField("theUnsafe");
        singletonField.setAccessible(true);
        Object unsafe = singletonField.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return (Interceptor) allocateInstance.invoke(unsafe, Class.forName(className));
    }

    private static boolean hasInterceptor(OkHttpClient client, String className) {
        for (Interceptor interceptor : client.interceptors()) {
            if (className.equals(interceptor.getClass().getName())) {
                return true;
            }
        }
        return false;
    }
}
