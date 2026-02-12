/*******************************************************************************
 * Copyright 2016 stfalcon.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package com.stfalcon.chatkit.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateFormatter {
    private DateFormatter() {
        throw new AssertionError();
    }

    public static String format(Date date, Template template) {
        return format(date, template.get());
    }

    public static String format(Date date, String format) {
        if (date == null) return "";
        return new SimpleDateFormat(format, Locale.getDefault())
                .format(date);
    }

    public static boolean isSameDay(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return isSameDay(cal1, cal2);
    }

    public static boolean isSameDay(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }
        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR));
    }

    public static boolean isSameYear(Date date1, Date date2) {
        if (date1 == null || date2 == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        return isSameYear(cal1, cal2);
    }

    public static boolean isSameYear(Calendar cal1, Calendar cal2) {
        if (cal1 == null || cal2 == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }
        return (cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR));
    }

    public static boolean isToday(Calendar calendar) {
        return isSameDay(calendar, Calendar.getInstance());
    }

    public static boolean isToday(Date date) {
        return isSameDay(date, Calendar.getInstance().getTime());
    }

    public static boolean isYesterday(Calendar calendar) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        return isSameDay(calendar, yesterday);
    }

    public static boolean isYesterday(Date date) {
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DAY_OF_MONTH, -1);
        return isSameDay(date, yesterday.getTime());
    }

    public static boolean isCurrentYear(Date date) {
        return isSameYear(date, Calendar.getInstance().getTime());
    }

    public static boolean isCurrentYear(Calendar calendar) {
        return isSameYear(calendar, Calendar.getInstance());
    }

    /**
     * Interface used to format dates before they were displayed (e.g. dialogs time, messages date headers etc.).
     */
    public interface Formatter {

        /**
         * Formats an string representation of the date object.
         *
         * @param date The date that should be formatted.
         * @return Formatted text.
         */
        String format(Date date);
    }

    public enum Template {
        STRING_DAY_MONTH_YEAR("d MMMM yyyy"),
        STRING_DAY_MONTH("d MMMM"),
        TIME("HH:mm");

        private String template;

        Template(String template) {
            this.template = template;
        }

        public String get() {
            return template;
        }
    }
}
