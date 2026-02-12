/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.leanback.widget.picker;

import android.content.res.Resources;
import android.os.Build;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * Utility class that provides Date/Time related constants as well as locale-specific calendar for
 * both {@link DatePicker} and {@link TimePicker}.
 */
class PickerUtility {

    // Whether the API version supports the use of {@link DateFormat#getBestDateTimePattern()}
    static final boolean SUPPORTS_BEST_DATE_TIME_PATTERN =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2;
    public static class DateConstant {
        public final Locale locale;
        public final String[] months;
        public final String[] days;

        DateConstant(Locale locale, Resources resources) {
            this.locale = locale;
            DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
            months = symbols.getShortMonths();
            Calendar calendar = Calendar.getInstance(locale);
            days = createStringIntArrays(calendar.getMinimum(Calendar.DAY_OF_MONTH),
                    calendar.getMaximum(Calendar.DAY_OF_MONTH), "%02d");
        }
    }

    public static class TimeConstant {
        public final Locale locale;
        public final String[] hours12;
        public final String[] hours24;
        public final String[] minutes;
        public final String[] ampm;

        TimeConstant(Locale locale, Resources resources) {
            this.locale = locale;
            DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
            hours12 = createStringIntArrays(1, 12, "%02d");
            hours24 = createStringIntArrays(0, 23, "%02d");
            minutes = createStringIntArrays(0, 59, "%02d");
            ampm = symbols.getAmPmStrings();
        }
    }

    public static DateConstant getDateConstantInstance(Locale locale, Resources resources) {
        return new DateConstant(locale, resources);
    }

    public static TimeConstant getTimeConstantInstance(Locale locale, Resources resources) {
        return new TimeConstant(locale, resources);
    }


    public static String[] createStringIntArrays(int firstNumber, int lastNumber, String format) {
        String[] array = new String[lastNumber - firstNumber + 1];
        for (int i = firstNumber; i <= lastNumber; i++) {
            if (format != null) {
                array[i - firstNumber] = String.format(format, i);
            } else {
                array[i - firstNumber] = String.valueOf(i);
            }
        }
        return array;
    }

    public static Calendar getCalendarForLocale(Calendar oldCalendar, Locale locale) {
        if (oldCalendar == null) {
            return Calendar.getInstance(locale);
        } else {
            final long currentTimeMillis = oldCalendar.getTimeInMillis();
            Calendar newCalendar = Calendar.getInstance(locale);
            newCalendar.setTimeInMillis(currentTimeMillis);
            return newCalendar;
        }
    }

    private PickerUtility() {
    }
}
