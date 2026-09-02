package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import java.util.Iterator;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

final class PlaybackOkHttpClient {
    private static final String BODY_LOGGER = "okhttp3.logging.HttpLoggingInterceptor";
    private static final String PROFILER =
            "com.localebro.okhttpprofiler.OkHttpProfilerInterceptor";

    private PlaybackOkHttpClient() {
    }

    static OkHttpClient withoutUnsafeDebugInterceptors(OkHttpClient client) {
        OkHttpClient.Builder builder = client.newBuilder();
        boolean removed = false;

        for (Iterator<Interceptor> iterator = builder.interceptors().iterator(); iterator.hasNext();) {
            String className = iterator.next().getClass().getName();
            if (PROFILER.equals(className) || BODY_LOGGER.equals(className)) {
                iterator.remove();
                removed = true;
            }
        }

        return removed ? builder.build() : client;
    }
}
