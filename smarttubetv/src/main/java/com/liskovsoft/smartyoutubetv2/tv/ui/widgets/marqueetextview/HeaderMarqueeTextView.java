package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview;

import android.content.Context;
import android.util.AttributeSet;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * MarqueeTextView used in browse section headers
 */
public class HeaderMarqueeTextView extends MarqueeTextView {
    public HeaderMarqueeTextView(Context context) {
        super(context);

        init();
    }

    public HeaderMarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public HeaderMarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        ViewUtil.applyMarqueeRtlParams(this, true);
    }
}
