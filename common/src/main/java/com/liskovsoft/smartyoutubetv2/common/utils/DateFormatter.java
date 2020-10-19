package com.liskovsoft.smartyoutubetv2.common.utils;
;
import android.content.Context;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateFormatter {
    public static String getCurrentDateShort(Context context) {
        // details: https://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
        String pattern = DateFormat.is24HourFormat(context) ? "EEE d MMM H:mm" : "EEE d MMM h:mm a";

        SimpleDateFormat serverFormat = new SimpleDateFormat(pattern, Locale.getDefault());
        String currentTime = serverFormat.format(new Date());

        return String.format("%1$s", currentTime);
    }
}
