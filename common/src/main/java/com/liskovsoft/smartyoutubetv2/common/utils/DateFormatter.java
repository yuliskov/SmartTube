package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import com.liskovsoft.sharedutils.locale.LocaleUtility;
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

    public static boolean is24HourLocale(Context context) {
        Locale currentLocale = LocaleUtility.getCurrentLocale(context);

        // Fix weird locale like en_RO
        for (Locale locale : Locale.getAvailableLocales()) {
            if (locale.getCountry().equals(currentLocale.getCountry())) {
                currentLocale = locale;
                break;
            }
        }

        java.text.DateFormat natural =
                java.text.DateFormat.getTimeInstance(
                        java.text.DateFormat.LONG, currentLocale);

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
