package com.google.android.exoplayer2.source.sabr.parser.processor;

import com.google.android.exoplayer2.extractor.ExtractorInput;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class Utils {
    public static int ticksToMs(long timeTicks, int timescale) {
        if (timeTicks == -1 || timescale == -1) {
            return -1;
        }

        return (int) Math.ceil(((double) timeTicks / timescale) * 1_000);
    }

    public static byte[] readAllBytes(ByteArrayInputStream is) {
        int streamLength = is.available();
        byte[] result = new byte[streamLength];

        is.read(result, 0, streamLength);

        return result;
    }

    public static byte[] readExactBytes(ExtractorInput input, int length) throws IOException, InterruptedException {
        byte[] result = new byte[length];
        input.readFully(result, 0, length);
        return result;
    }

    public static long toLong(int value) {
        return Integer.toUnsignedLong(value);
    }

    public static String updateQuery(String baseUrl, String key, Object value) {
        if (baseUrl == null || key == null || value == null) return baseUrl;
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + key + "=" + value;
    }
}
