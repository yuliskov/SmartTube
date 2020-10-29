package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateFormatter {
    public static String getCurrentDateShort(Context context) {
        // details: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
        String pattern = is24HourLocale() ? "EEE d MMM H:mm" : "EEE d MMM h:mm a";

        SimpleDateFormat serverFormat = new SimpleDateFormat(pattern, Locale.getDefault());
        String currentTime = serverFormat.format(new Date());

        return String.format("%1$s", currentTime);
    }

    private static boolean is24HourLocale() {
        String output = SimpleDateFormat.getTimeInstance().format(new Date());
        if (output.contains(" AM") || output.contains(" PM")) {
            return false;
        } else {
            return true;
        }
    }
}
