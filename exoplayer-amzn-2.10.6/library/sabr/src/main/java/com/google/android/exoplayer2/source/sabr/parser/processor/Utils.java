package com.google.android.exoplayer2.source.sabr.parser.processor;

public class Utils {
    public static int ticksToMs(long timeTicks, int timescale) {
        if (timeTicks == -1 || timescale == -1) {
            return -1;
        }

        return (int) Math.ceil(((double) timeTicks / timescale) * 1_000);
    }
}
