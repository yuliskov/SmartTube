package com.liskovsoft.smartyoutubetv2.tv.ui.modern.common;

import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.core.view.animation.PathInterpolatorCompat;

/**
 * Motion tokens of the modern interface. See docs/modern-ui/tokens.md
 */
public final class ModernTheme {
    /** Rail open/close, opacity changes, focus indicator. */
    public static final int ANIM_FAST_MS = 150;
    /** Row and column scrolling. */
    public static final int ANIM_SCROLL_MS = 300;
    /** Entries fading in/out. */
    public static final int ANIM_FADE_MS = 300;
    /** Thumbnail fade-out when the inline preview starts. */
    public static final int ANIM_PREVIEW_FADE_MS = 1500;

    private ModernTheme() {
    }

    public static Interpolator fastInterpolator() {
        return new LinearInterpolator();
    }

    public static Interpolator scrollInterpolator() {
        return PathInterpolatorCompat.create(0.26f, 0.86f, 0.44f, 0.985f);
    }

    public static Interpolator fadeInterpolator() {
        return PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f);
    }

    public static Interpolator previewFadeInterpolator() {
        return PathInterpolatorCompat.create(0.05f, 0f, 0f, 1f);
    }
}
