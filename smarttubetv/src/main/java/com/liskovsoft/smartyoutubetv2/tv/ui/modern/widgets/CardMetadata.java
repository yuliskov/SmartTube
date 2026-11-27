package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a card subtitle ("label • author • views • age") into the tile lines:<br/>
 * line 1 = author, line 2 = remaining info, label = leading quality/status mark.
 */
public final class CardMetadata {
    private static final String DELIM = Video.TERTIARY_TEXT_DELIM;
    public final String label;
    public final String line1;
    public final String line2;

    private CardMetadata(String label, String line1, String line2) {
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
    }

    public static CardMetadata parse(CharSequence secondTitle) {
        if (secondTitle == null || secondTitle.length() == 0) {
            return new CardMetadata(null, null, null);
        }

        List<String> parts = new ArrayList<>();

        for (String part : secondTitle.toString().split(DELIM)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !"LIVE".equals(trimmed)) {
                parts.add(trimmed);
            }
        }

        if (parts.isEmpty()) {
            return new CardMetadata(null, null, null);
        }

        String label = null;

        if (parts.size() >= 4) {
            label = parts.remove(0);
        }

        if (startsWithDigit(parts.get(0))) {
            return new CardMetadata(label, null, join(parts, 0));
        }

        return new CardMetadata(label, parts.get(0), parts.size() > 1 ? join(parts, 1) : null);
    }

    private static boolean startsWithDigit(String text) {
        return Character.isDigit(text.charAt(0));
    }

    private static String join(List<String> parts, int from) {
        StringBuilder result = new StringBuilder();

        for (int i = from; i < parts.size(); i++) {
            if (result.length() > 0) {
                result.append(' ').append(DELIM).append(' ');
            }
            result.append(parts.get(i));
        }

        return result.toString();
    }
}
