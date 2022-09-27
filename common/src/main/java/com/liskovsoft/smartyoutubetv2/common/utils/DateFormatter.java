package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateFormatter {
    public static String getCurrentDateTimeShort(Context context) {
        return getDateTimeShort(context, true, true, System.currentTimeMillis());
    }

    public static String getCurrentTimeShort(Context context) {
        return getDateTimeShort(context, false, true, System.currentTimeMillis());
    }

    public static String getCurrentDateShort(Context context) {
        return getDateTimeShort(context, true, false, System.currentTimeMillis());
    }

    public static String formatTimeShort(Context context, long currentTimeMs) {
        return getDateTimeShort(context, false, true, currentTimeMs);
    }

    private static String getDateTimeShort(Context context, boolean showDate, boolean showTime, long currentTimeMs) {
        String datePattern = "EEE d MMM";

        // details: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
        String timePattern = GeneralData.instance(context).getTimeMode() == GeneralData.TIME_MODE_24 ? "H:mm" : "h:mm a";

        SimpleDateFormat serverFormat = new SimpleDateFormat(
                String.format("%s%s",
                        showDate ? datePattern + " " : "",
                        showTime ? timePattern : ""),
                Locale.getDefault()
        );
        String currentTime = serverFormat.format(new Date(currentTimeMs));

        return String.format("%1$s", currentTime);
    }
}
