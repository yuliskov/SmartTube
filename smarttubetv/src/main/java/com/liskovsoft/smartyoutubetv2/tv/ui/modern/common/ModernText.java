package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import java.util.Locale;

/**
 * Text conventions of the modern interface: labels in sentence case, never all caps.
 */
public final class ModernText {
    private ModernText() {
    }

    /**
     * "LIVE" becomes "Live". Mixed case text ("Watch later", "iPhone") is kept as is.
     */
    public static String sentenceCase(CharSequence text) {
        return sentenceCase(text, Locale.getDefault());
    }

    public static String sentenceCase(CharSequence text, Locale locale) {
        if (text == null) {
            return null;
        }

        String value = text.toString();
        int letters = 0;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                if (Character.isLowerCase(c)) {
                    return value; // already mixed or lower case
                }
                letters++;
            }
        }

        if (letters < 2) {
            return value;
        }

        String lower = value.toLowerCase(locale);

        for (int i = 0; i < lower.length(); i++) {
            if (Character.isLetter(lower.charAt(i))) {
                return lower.substring(0, i) + lower.substring(i, i + 1).toUpperCase(locale) + lower.substring(i + 1);
            }
        }

        return lower;
    }
}
