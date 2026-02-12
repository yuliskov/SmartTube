/*
 * Copyright 2018 The Android Open Source Project
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

package com.liskovsoft.smartyoutubetv2.tv.ui.mod.clickable;

import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

/**
 * Support copy of https://cs.chromium.org/chromium/src/android_webview/java/src/org/chromium
 * /android_webview/FindAddress.java
 *
 * @hide
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
class FindAddress {
    private static class ZipRange {
        int mLow;
        int mHigh;
        int mException1;
        int mException2;

        ZipRange(int low, int high, int exception1, int exception2) {
            mLow = low;
            mHigh = high;
            mException1 = exception1;
            mException2 = exception2;
        }

        boolean matches(String zipCode) {
            int prefix = Integer.parseInt(zipCode.substring(0, 2));
            return (mLow <= prefix && prefix <= mHigh) || prefix == mException1
                    || prefix == mException2;
        }
    }

    // Addresses consist of at least this many words, not including state and zip code.
    private static final int MIN_ADDRESS_WORDS = 4;

    // Adddresses consist of at most this many words, not including state and zip code.
    private static final int MAX_ADDRESS_WORDS = 14;

    // Addresses consist of at most this many lines.
    private static final int MAX_ADDRESS_LINES = 5;

    // No words in an address are longer than this many characters.
    private static final int kMaxAddressNameWordLength = 25;

    // Location name should be in the first MAX_LOCATION_NAME_DISTANCE words
    private static final int MAX_LOCATION_NAME_DISTANCE = 5;

    private static final ZipRange[] sStateZipCodeRanges = {
            new ZipRange(99, 99, -1, -1), // AK Alaska.
            new ZipRange(35, 36, -1, -1), // AL Alabama.
            new ZipRange(71, 72, -1, -1), // AR Arkansas.
            new ZipRange(96, 96, -1, -1), // AS American Samoa.
            new ZipRange(85, 86, -1, -1), // AZ Arizona.
            new ZipRange(90, 96, -1, -1), // CA California.
            new ZipRange(80, 81, -1, -1), // CO Colorado.
            new ZipRange(6, 6, -1, -1), // CT Connecticut.
            new ZipRange(20, 20, -1, -1), // DC District of Columbia.
            new ZipRange(19, 19, -1, -1), // DE Delaware.
            new ZipRange(32, 34, -1, -1), // FL Florida.
            new ZipRange(96, 96, -1, -1), // FM Federated States of Micronesia.
            new ZipRange(30, 31, -1, -1), // GA Georgia.
            new ZipRange(96, 96, -1, -1), // GU Guam.
            new ZipRange(96, 96, -1, -1), // HI Hawaii.
            new ZipRange(50, 52, -1, -1), // IA Iowa.
            new ZipRange(83, 83, -1, -1), // ID Idaho.
            new ZipRange(60, 62, -1, -1), // IL Illinois.
            new ZipRange(46, 47, -1, -1), // IN Indiana.
            new ZipRange(66, 67, 73, -1), // KS Kansas.
            new ZipRange(40, 42, -1, -1), // KY Kentucky.
            new ZipRange(70, 71, -1, -1), // LA Louisiana.
            new ZipRange(1, 2, -1, -1), // MA Massachusetts.
            new ZipRange(20, 21, -1, -1), // MD Maryland.
            new ZipRange(3, 4, -1, -1), // ME Maine.
            new ZipRange(96, 96, -1, -1), // MH Marshall Islands.
            new ZipRange(48, 49, -1, -1), // MI Michigan.
            new ZipRange(55, 56, -1, -1), // MN Minnesota.
            new ZipRange(63, 65, -1, -1), // MO Missouri.
            new ZipRange(96, 96, -1, -1), // MP Northern Mariana Islands.
            new ZipRange(38, 39, -1, -1), // MS Mississippi.
            new ZipRange(55, 56, -1, -1), // MT Montana.
            new ZipRange(27, 28, -1, -1), // NC North Carolina.
            new ZipRange(58, 58, -1, -1), // ND North Dakota.
            new ZipRange(68, 69, -1, -1), // NE Nebraska.
            new ZipRange(3, 4, -1, -1), // NH New Hampshire.
            new ZipRange(7, 8, -1, -1), // NJ New Jersey.
            new ZipRange(87, 88, 86, -1), // NM New Mexico.
            new ZipRange(88, 89, 96, -1), // NV Nevada.
            new ZipRange(10, 14, 0, 6), // NY New York.
            new ZipRange(43, 45, -1, -1), // OH Ohio.
            new ZipRange(73, 74, -1, -1), // OK Oklahoma.
            new ZipRange(97, 97, -1, -1), // OR Oregon.
            new ZipRange(15, 19, -1, -1), // PA Pennsylvania.
            new ZipRange(6, 6, 0, 9), // PR Puerto Rico.
            new ZipRange(96, 96, -1, -1), // PW Palau.
            new ZipRange(2, 2, -1, -1), // RI Rhode Island.
            new ZipRange(29, 29, -1, -1), // SC South Carolina.
            new ZipRange(57, 57, -1, -1), // SD South Dakota.
            new ZipRange(37, 38, -1, -1), // TN Tennessee.
            new ZipRange(75, 79, 87, 88), // TX Texas.
            new ZipRange(84, 84, -1, -1), // UT Utah.
            new ZipRange(22, 24, 20, -1), // VA Virginia.
            new ZipRange(6, 9, -1, -1), // VI Virgin Islands.
            new ZipRange(5, 5, -1, -1), // VT Vermont.
            new ZipRange(98, 99, -1, -1), // WA Washington.
            new ZipRange(53, 54, -1, -1), // WI Wisconsin.
            new ZipRange(24, 26, -1, -1), // WV West Virginia.
            new ZipRange(82, 83, -1, -1) // WY Wyoming.
    };

    // Newlines
    private static final String NL = "\n\u000B\u000C\r\u0085\u2028\u2029";

    // Space characters
    private static final String SP = "\u0009\u0020\u00A0\u1680\u2000\u2001"
            + "\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u202F"
            + "\u205F\u3000";

    // Whitespace
    private static final String WS = SP + NL;

    // Characters that are considered word delimiters.
    private static final String WORD_DELIM = ",*\u2022" + WS;

    // Lookahead for word end.
    private static final String WORD_END = "(?=[" + WORD_DELIM + "]|$)";

    // Address words are a sequence of non-delimiter characters.
    private static final Pattern sWordRe =
            Pattern.compile("[^" + WORD_DELIM + "]+" + WORD_END, Pattern.CASE_INSENSITIVE);

    // Characters that are considered suffix delimiters for house numbers.
    private static final String HOUSE_POST_DELIM = ",\"'" + WS;

    // Lookahead for house end.
    private static final String HOUSE_END = "(?=[" + HOUSE_POST_DELIM + "]|$)";

    // Characters that are considered prefix delimiters for house numbers.
    private static final String HOUSE_PRE_DELIM = ":" + HOUSE_POST_DELIM;

    // A house number component is "one" or a number, optionally
    // followed by a single alphabetic character, or
    private static final String HOUSE_COMPONENT = "(?:one|[0-9]+([a-z](?=[^a-z]|$)|st|nd|rd|th)?)";

    // House numbers are a repetition of |HOUSE_COMPONENT|, separated by -, and followed by
    // a delimiter character.
    private static final Pattern sHouseNumberRe =
            Pattern.compile(HOUSE_COMPONENT + "(?:-" + HOUSE_COMPONENT + ")*" + HOUSE_END,
                    Pattern.CASE_INSENSITIVE);

    // XXX: do we want to accept whitespace other than 0x20 in state names?
    private static final Pattern sStateRe = Pattern.compile("(?:"
                    + "(ak|alaska)|"
                    + "(al|alabama)|"
                    + "(ar|arkansas)|"
                    + "(as|american[" + SP + "]+samoa)|"
                    + "(az|arizona)|"
                    + "(ca|california)|"
                    + "(co|colorado)|"
                    + "(ct|connecticut)|"
                    + "(dc|district[" + SP + "]+of[" + SP + "]+columbia)|"
                    + "(de|delaware)|"
                    + "(fl|florida)|"
                    + "(fm|federated[" + SP + "]+states[" + SP + "]+of[" + SP + "]+micronesia)|"
                    + "(ga|georgia)|"
                    + "(gu|guam)|"
                    + "(hi|hawaii)|"
                    + "(ia|iowa)|"
                    + "(id|idaho)|"
                    + "(il|illinois)|"
                    + "(in|indiana)|"
                    + "(ks|kansas)|"
                    + "(ky|kentucky)|"
                    + "(la|louisiana)|"
                    + "(ma|massachusetts)|"
                    + "(md|maryland)|"
                    + "(me|maine)|"
                    + "(mh|marshall[" + SP + "]+islands)|"
                    + "(mi|michigan)|"
                    + "(mn|minnesota)|"
                    + "(mo|missouri)|"
                    + "(mp|northern[" + SP + "]+mariana[" + SP + "]+islands)|"
                    + "(ms|mississippi)|"
                    + "(mt|montana)|"
                    + "(nc|north[" + SP + "]+carolina)|"
                    + "(nd|north[" + SP + "]+dakota)|"
                    + "(ne|nebraska)|"
                    + "(nh|new[" + SP + "]+hampshire)|"
                    + "(nj|new[" + SP + "]+jersey)|"
                    + "(nm|new[" + SP + "]+mexico)|"
                    + "(nv|nevada)|"
                    + "(ny|new[" + SP + "]+york)|"
                    + "(oh|ohio)|"
                    + "(ok|oklahoma)|"
                    + "(or|oregon)|"
                    + "(pa|pennsylvania)|"
                    + "(pr|puerto[" + SP + "]+rico)|"
                    + "(pw|palau)|"
                    + "(ri|rhode[" + SP + "]+island)|"
                    + "(sc|south[" + SP + "]+carolina)|"
                    + "(sd|south[" + SP + "]+dakota)|"
                    + "(tn|tennessee)|"
                    + "(tx|texas)|"
                    + "(ut|utah)|"
                    + "(va|virginia)|"
                    + "(vi|virgin[" + SP + "]+islands)|"
                    + "(vt|vermont)|"
                    + "(wa|washington)|"
                    + "(wi|wisconsin)|"
                    + "(wv|west[" + SP + "]+virginia)|"
                    + "(wy|wyoming)"
                    + ")" + WORD_END,
            Pattern.CASE_INSENSITIVE);

    private static final Pattern sLocationNameRe = Pattern.compile("(?:"
                    + "alley|annex|arcade|ave[.]?|avenue|alameda|bayou|"
                    + "beach|bend|bluffs?|bottom|boulevard|branch|bridge|"
                    + "brooks?|burgs?|bypass|broadway|camino|camp|canyon|"
                    + "cape|causeway|centers?|circles?|cliffs?|club|common|"
                    + "corners?|course|courts?|coves?|creek|crescent|crest|"
                    + "crossing|crossroad|curve|circulo|dale|dam|divide|"
                    + "drives?|estates?|expressway|extensions?|falls?|ferry|"
                    + "fields?|flats?|fords?|forest|forges?|forks?|fort|"
                    + "freeway|gardens?|gateway|glens?|greens?|groves?|"
                    + "harbors?|haven|heights|highway|hills?|hollow|inlet|"
                    + "islands?|isle|junctions?|keys?|knolls?|lakes?|land|"
                    + "landing|lane|lights?|loaf|locks?|lodge|loop|mall|"
                    + "manors?|meadows?|mews|mills?|mission|motorway|mount|"
                    + "mountains?|neck|orchard|oval|overpass|parks?|"
                    + "parkways?|pass|passage|path|pike|pines?|plains?|"
                    + "plaza|points?|ports?|prairie|privada|radial|ramp|"
                    + "ranch|rapids?|rd[.]?|rest|ridges?|river|roads?|route|"
                    + "row|rue|run|shoals?|shores?|skyway|springs?|spurs?|"
                    + "squares?|station|stravenue|stream|st[.]?|streets?|"
                    + "summit|speedway|terrace|throughway|trace|track|"
                    + "trafficway|trail|tunnel|turnpike|underpass|unions?|"
                    + "valleys?|viaduct|views?|villages?|ville|vista|walks?|"
                    + "wall|ways?|wells?|xing|xrd)" + WORD_END,
            Pattern.CASE_INSENSITIVE);

    private static final Pattern sSuffixedNumberRe =
            Pattern.compile("([0-9]+)(st|nd|rd|th)", Pattern.CASE_INSENSITIVE);

    private static final Pattern sZipCodeRe =
            Pattern.compile("(?:[0-9]{5}(?:-[0-9]{4})?)" + WORD_END, Pattern.CASE_INSENSITIVE);

    private static boolean checkHouseNumber(String houseNumber) {
        // Make sure that there are at most 5 digits.
        int digitCount = 0;
        for (int i = 0; i < houseNumber.length(); ++i) {
            if (Character.isDigit(houseNumber.charAt(i))) ++digitCount;
        }
        if (digitCount > 5) return false;

        // Make sure that any ordinals are valid.
        Matcher suffixMatcher = sSuffixedNumberRe.matcher(houseNumber);
        while (suffixMatcher.find()) {
            int num = Integer.parseInt(suffixMatcher.group(1));
            if (num == 0) {
                return false; // 0th is invalid.
            }
            String suffix = suffixMatcher.group(2).toLowerCase(Locale.getDefault());
            switch (num % 10) {
                case 1:
                    return suffix.equals(num % 100 == 11 ? "th" : "st");
                case 2:
                    return suffix.equals(num % 100 == 12 ? "th" : "nd");
                case 3:
                    return suffix.equals(num % 100 == 13 ? "th" : "rd");
                default:
                    return suffix.equals("th");
            }
        }
        return true;
    }

    /**
     * Attempt to match a house number beginnning at position offset
     * in content.  The house number must be followed by a word
     * delimiter or the end of the string, and if offset is non-zero,
     * then it must also be preceded by a word delimiter.
     *
     * @return a MatchResult if a valid house number was found.
     */
    @VisibleForTesting
    public static MatchResult matchHouseNumber(String content, int offset) {
        if (offset > 0 && HOUSE_PRE_DELIM.indexOf(content.charAt(offset - 1)) == -1) return null;
        Matcher matcher = sHouseNumberRe.matcher(content).region(offset, content.length());
        if (matcher.lookingAt()) {
            MatchResult matchResult = matcher.toMatchResult();
            if (checkHouseNumber(matchResult.group(0))) return matchResult;
        }
        return null;
    }

    /**
     * Attempt to match a US state beginnning at position offset in
     * content.  The matching state must be followed by a word
     * delimiter or the end of the string, and if offset is non-zero,
     * then it must also be preceded by a word delimiter.
     *
     * @return a MatchResult if a valid US state (or two letter code)
     * was found.
     */
    @VisibleForTesting
    public static MatchResult matchState(String content, int offset) {
        if (offset > 0 && WORD_DELIM.indexOf(content.charAt(offset - 1)) == -1) return null;
        Matcher stateMatcher = sStateRe.matcher(content).region(offset, content.length());
        return stateMatcher.lookingAt() ? stateMatcher.toMatchResult() : null;
    }

    /**
     * Test whether zipCode matches the U.S. zip code format (ddddd or
     * ddddd-dddd) and is within the expected range, given that
     * stateMatch is a match of sStateRe.
     *
     * @return true if zipCode is a valid zip code, is legal for the
     * matched state, and is followed by a word delimiter or the end
     * of the string.
     */
    private static boolean isValidZipCode(String zipCode, MatchResult stateMatch) {
        if (stateMatch == null) return false;
        // Work out the index of the state, based on which group matched.
        int stateIndex = stateMatch.groupCount();
        while (stateIndex > 0) {
            if (stateMatch.group(stateIndex--) != null) break;
        }
        return sZipCodeRe.matcher(zipCode).matches()
                && sStateZipCodeRanges[stateIndex].matches(zipCode);
    }

    /**
     * Test whether zipCode matches the U.S. zip code format (ddddd or
     * ddddd-dddd) and is within the expected range, given that
     * state holds a string that will match sStateRe.
     *
     * @return true if zipCode is a valid zip code, is legal for the
     * given state, and is followed by a word delimiter or the end
     * of the string.
     */
    @VisibleForTesting
    public static boolean isValidZipCode(String zipCode, String state) {
        return isValidZipCode(zipCode, matchState(state, 0));
    }

    /**
     * Test whether zipCode matches the U.S. zip code format (ddddd or ddddd-dddd).
     *
     * @return true if zipCode is a valid zip code followed by a word
     * delimiter or the end of the string.
     */
    @VisibleForTesting
    public static boolean isValidZipCode(String zipCode) {
        return sZipCodeRe.matcher(zipCode).matches();
    }

    /**
     * Test whether location is one of the valid locations.
     *
     * @return true if location starts with a valid location name
     * followed by a word delimiter or the end of the string.
     */
    @VisibleForTesting
    public static boolean isValidLocationName(String location) {
        return sLocationNameRe.matcher(location).matches();
    }

    /**
     * Attempt to match a complete address in content, starting with
     * houseNumberMatch.
     *
     * @param content          The string to search.
     * @param houseNumberMatch A matching house number to start extending.
     * @return +ve: the end of the match
     * +ve: the position to restart searching for house numbers, negated.
     */
    private static int attemptMatch(String content, MatchResult houseNumberMatch) {
        int restartPos = -1;
        int nonZipMatch = -1;
        int it = houseNumberMatch.end();
        int numLines = 1;
        boolean consecutiveHouseNumbers = true;
        boolean foundLocationName = false;
        int wordCount = 1;
        String lastWord = "";

        Matcher matcher = sWordRe.matcher(content);

        for (; it < content.length(); lastWord = matcher.group(0), it = matcher.end()) {
            if (!matcher.find(it)) {
                // No more words in the input sequence.
                return -content.length();
            }
            if (matcher.end() - matcher.start() > kMaxAddressNameWordLength) {
                // Word is too long to be part of an address. Fail.
                return -matcher.end();
            }

            // Count the number of newlines we just consumed.
            while (it < matcher.start()) {
                if (NL.indexOf(content.charAt(it++)) != -1) ++numLines;
            }

            // Consumed too many lines. Fail.
            if (numLines > MAX_ADDRESS_LINES) break;

            // Consumed too many words. Fail.
            if (++wordCount > MAX_ADDRESS_WORDS) break;

            if (matchHouseNumber(content, it) != null) {
                if (consecutiveHouseNumbers && numLines > 1) {
                    // Last line ended with a number, and this this line starts with one.
                    // Restart at this number.
                    return -it;
                }
                // Remember the position of this match as the restart position.
                if (restartPos == -1) restartPos = it;
                continue;
            }

            consecutiveHouseNumbers = false;

            if (isValidLocationName(matcher.group(0))) {
                foundLocationName = true;
                continue;
            }

            if (wordCount == MAX_LOCATION_NAME_DISTANCE && !foundLocationName) {
                // Didn't find a location name in time. Fail.
                it = matcher.end();
                break;
            }

            if (foundLocationName && wordCount > MIN_ADDRESS_WORDS) {
                // We can now attempt to match a state.
                MatchResult stateMatch = matchState(content, it);
                if (stateMatch != null) {
                    if (lastWord.equals("et") && stateMatch.group(0).equals("al")) {
                        // Reject "et al" as a false postitive.
                        it = stateMatch.end();
                        break;
                    }

                    // At this point we've matched a state; try to match a zip code after it.
                    Matcher zipMatcher = sWordRe.matcher(content);
                    if (zipMatcher.find(stateMatch.end())) {
                        if (isValidZipCode(zipMatcher.group(0), stateMatch)) {
                            return zipMatcher.end();
                        }
                    } else {
                        // The content ends with a state but no zip
                        // code. This is a legal match according to the
                        // documentation. N.B. This is equivalent to the
                        // original c++ implementation, which only allowed
                        // the zip code to be optional at the end of the
                        // string, which presumably is a bug.  We tried
                        // relaxing this to work in other places but it
                        // caused too many false positives.
                        nonZipMatch = stateMatch.end();
                    }
                }
            }
        }

        if (nonZipMatch > 0) return nonZipMatch;

        return -(restartPos > 0 ? restartPos : it);
    }

    /**
     * Return the first matching address in content.
     *
     * @param content The string to search.
     * @return The first valid address, or null if no address was matched.
     */
    static String findAddress(String content) {
        Matcher houseNumberMatcher = sHouseNumberRe.matcher(content);
        int start = 0;
        while (houseNumberMatcher.find(start)) {
            if (checkHouseNumber(houseNumberMatcher.group(0))) {
                start = houseNumberMatcher.start();
                int end = attemptMatch(content, houseNumberMatcher);
                if (end > 0) {
                    return content.substring(start, end);
                }
                start = -end;
            } else {
                start = houseNumberMatcher.end();
            }
        }
        return null;
    }

    private FindAddress() {
    }
}
