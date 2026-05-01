package com.liskovsoft.smartyoutubetv2.common.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses YouTube's relative date strings (e.g. "2 years ago", "vor 3 Tagen")
 * into Unix milliseconds.
 * 
 * Supports multiple languages:
 * - English: "X year(s) ago", "X month(s) ago", "X week(s) ago", "X day(s) ago",
 *           "X hour(s) ago", "X minute(s) ago"
 * - German: "vor X Jahr(en)", "vor X Monat(en)", "vor X Woche(n)", "vor X Tag(en)",
 *           "vor X Stunde(n)", "vor X Minute(n)"
 * - French: "il y a X an(s)", "il y a X mois", "il y a X semaine(s)", 
 *           "il y a X jour(s)", "il y a X heure(s)", "il y a X minute(s)"
 * - Spanish: "hace X año(s)", "hace X mes(es)", "hace X semana(s)", "hace X día(s)",
 *           "hace X hora(s)", "hace X minuto(s)"
 * 
 * Add new languages by extending the parseLang() methods.
 */
public final class RelativePublishedTime {
    private RelativePublishedTime() {}

    private static final long MS_SECOND = 1_000;
    private static final long MS_MINUTE = 60 * MS_SECOND;
    private static final long MS_HOUR = 60 * MS_MINUTE;
    private static final long MS_DAY = 24 * MS_HOUR;
    private static final long MS_WEEK = 7 * MS_DAY;
    private static final long MS_MONTH = 30 * MS_DAY;
    private static final long MS_YEAR = 365 * MS_DAY;

    /**
     * Converts a relative date string to Unix milliseconds.
     * Returns 0 if parsing fails.
     */
    public static long publishedTimeTextToUnixMs(CharSequence text) {
        if (text == null || text.length() == 0) {
            return 0;
        }
        String s = text.toString();

        long result = parse(s, "en");
        if (result > 0) return result;
        result = parse(s, "de");
        if (result > 0) return result;
        result = parse(s, "fr");
        if (result > 0) return result;
        result = parse(s, "es");
        return result;
    }

    private static long parse(String s, String lang) {
        // English: "2 years ago", "3 months ago"
        if (lang.equals("en")) {
            Pattern p = Pattern.compile(
                "(?:(\\d+)\\s+year[s]?|(\\d+)\\s+month[s]?|(\\d+)\\s+week[s]?|(\\d+)\\s+day[s]?|(\\d+)\\s+hour[s]?|(\\d+)\\s+minute[s]?)\\s+ago",
                Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(s);
            if (m.find()) {
                for (int i = 1; i <= m.groupCount(); i++) {
                    String numStr = m.group(i);
                    if (numStr != null) {
                        return Integer.parseInt(numStr) * unitMsEn(i);
                    }
                }
            }
        }
        // German: "vor 2 Jahren", "vor 3 Monaten"
        if (lang.equals("de")) {
            Pattern p = Pattern.compile(
                "vor\\s+(\\d+)\\s+(Jahr[e]?|Monat[e]?|Woche[n]?|Tag[e]?|Stunde[n]?|Minute[n]?)",
                Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(s);
            if (m.find()) {
                int num = Integer.parseInt(m.group(1));
                String unit = m.group(2).toLowerCase();
                if (unit.startsWith("jahr")) return num * MS_YEAR;
                if (unit.startsWith("monat")) return num * MS_MONTH;
                if (unit.startsWith("woche")) return num * MS_WEEK;
                if (unit.startsWith("tag")) return num * MS_DAY;
                if (unit.startsWith("stunde")) return num * MS_HOUR;
                if (unit.startsWith("minute")) return num * MS_MINUTE;
            }
        }
        // French: "il y a 2 ans", "il y a 3 mois"
        if (lang.equals("fr")) {
            Pattern p = Pattern.compile(
                "il\\s+y\\s+a\\s+(\\d+)\\s+(an[s]?|mois|semaine[s]?|jour[s]?|heure[s]?|minute[s]?)",
                Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(s);
            if (m.find()) {
                int num = Integer.parseInt(m.group(1));
                String unit = m.group(2).toLowerCase();
                if (unit.startsWith("an")) return num * MS_YEAR;
                if (unit.equals("mois")) return num * MS_MONTH;
                if (unit.startsWith("semaine")) return num * MS_WEEK;
                if (unit.startsWith("jour")) return num * MS_DAY;
                if (unit.startsWith("heure")) return num * MS_HOUR;
                if (unit.startsWith("minute")) return num * MS_MINUTE;
            }
        }
        // Spanish: "hace 2 años", "hace 3 meses"
        if (lang.equals("es")) {
            Pattern p = Pattern.compile(
                "hace\\s+(\\d+)\\s+(año[s]?|mes(es)?|semana[s]?|día[s]?|hora[s]?|minuto[s]?)",
                Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(s);
            if (m.find()) {
                int num = Integer.parseInt(m.group(1));
                String unit = m.group(2).toLowerCase();
                if (unit.startsWith("año")) return num * MS_YEAR;
                if (unit.startsWith("mes")) return num * MS_MONTH;
                if (unit.startsWith("semana")) return num * MS_WEEK;
                if (unit.startsWith("día")) return num * MS_DAY;
                if (unit.startsWith("hora")) return num * MS_HOUR;
                if (unit.startsWith("minuto")) return num * MS_MINUTE;
            }
        }
        return 0;
    }

    private static long unitMsEn(int groupIndex) {
        // Groups: 1=year, 2=month, 3=week, 4=day, 5=hour, 6=minute
        switch (groupIndex) {
            case 1: return MS_YEAR;
            case 2: return MS_MONTH;
            case 3: return MS_WEEK;
            case 4: return MS_DAY;
            case 5: return MS_HOUR;
            case 6: return MS_MINUTE;
            default: return 0;
        }
    }
}
