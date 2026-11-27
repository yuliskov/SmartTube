package com.google.android.exoplayer2.ui;

/**
 * Marks {@link com.google.android.exoplayer2.text.Cue} text built from merged original + translated
 * subtitles so the UI layer can keep translation-line spans even when embedded WebVTT/TTML styling
 * is disabled for normal cues.
 */
public final class DualSubtitleCueMarkers {
    /** android.text.Annotation key on the translated line range */
    public static final String ANNOTATION_KEY = "smarttube-dual";
    /** android.text.Annotation value on the translated line range */
    public static final String ANNOTATION_VALUE = "translation";

    /** Translation line text size relative to the user's primary subtitle size. */
    public static final float TRANSLATION_RELATIVE_SIZE = 1.0f;
    /** ARGB for translated line (light green). */
    public static final int TRANSLATION_COLOR = 0xFF88FFAA;
    /** Vertical gap between original and translated line, as a fraction of primary text size. */
    public static final float TRANSLATION_LINE_GAP_FRACTION = 0.08f;

    private DualSubtitleCueMarkers() {
    }
}
