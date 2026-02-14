package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat;

import android.content.Context;
import android.util.AttributeSet;

/**
 * MarqueeTextView used in browse pip title
 */
public class TitleMarqueeTextViewCompat extends MarqueeTextViewCompat {
    public TitleMarqueeTextViewCompat(Context context) {
        super(context);
    }

    public TitleMarqueeTextViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TitleMarqueeTextViewCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Make marquee work even when the view isn't focused (e.g. browse pip title)
     */
    @Override
    public boolean isFocused() {
        return true;
    }
}
