package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview;

import android.content.Context;
import android.util.AttributeSet;

/**
 * MarqueeTextView used in browse pip title
 */
public class TitleMarqueeTextView extends MarqueeTextView {
    public TitleMarqueeTextView(Context context) {
        super(context);
    }

    public TitleMarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TitleMarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
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
