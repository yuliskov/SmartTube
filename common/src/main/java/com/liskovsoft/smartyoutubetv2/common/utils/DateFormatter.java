package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import com.liskovsoft.sharedutils.locale.LocaleUtility;

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

    public static String formatTimeShort(Context context, long currentTimeMs) {
        return getDateTimeShort(context, false, true, currentTimeMs);
    }

    private static String getDateTimeShort(Context context, boolean showDate, boolean showTime, long currentTimeMs) {
        String datePattern = "EEE d MMM";

        // details: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
        String timePattern = is24HourLocale(context) ? "H:mm" : "h:mm a";

        SimpleDateFormat serverFormat = new SimpleDateFormat(
                String.format("%s%s",
                        showDate ? datePattern + " " : "",
                        showTime ? timePattern : ""),
                Locale.getDefault()
        );
        String currentTime = serverFormat.format(new Date(currentTimeMs));

        return String.format("%1$s", currentTime);
    }

    private static boolean is24HourLocale(Context context) {
        Locale locale = LocaleUtility.getCurrentLocale(context);
        // Fix weird locale (e.g. ru_US)
        Locale fixedLocale = new Locale(locale.getLanguage());

        java.text.DateFormat natural =
                java.text.DateFormat.getTimeInstance(
                        java.text.DateFormat.LONG, fixedLocale);

        if (natural instanceof SimpleDateFormat) {
            SimpleDateFormat sdf = (SimpleDateFormat) natural;
            String pattern = sdf.toPattern();
            if (pattern.indexOf('H') >= 0) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }
}
