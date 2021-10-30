package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.content.Intent;

public class TvQuickActions {
    private final static String PACKAGE = "dev.vodik7.tvquickactions";

    public static void sendStopAFR(Context context) {
        Intent intent = new Intent();
        intent.setPackage(PACKAGE);
        intent.setAction(PACKAGE + ".STOP_AFR");
        context.sendBroadcast(intent);
    }

    public static void sendStartAFR(Context context, int height, float fps) {
        Intent intent = new Intent();
        intent.setPackage(PACKAGE);
        intent.setAction(PACKAGE + ".START_AFR");
        intent.putExtra("fps", fps);
        intent.putExtra("height", height);
        context.sendBroadcast(intent);
    }
}