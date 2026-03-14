package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat;

import android.content.Context;
import android.util.AttributeSet;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * MarqueeTextView used in browse pip title
 */
public class TitleMarqueeTextViewCompat extends MarqueeTextViewCompat {
    private final Runnable mUpdateMarquee = super::updateMarquee;

    public TitleMarqueeTextViewCompat(Context context) {
        super(context);
        init();
    }

    public TitleMarqueeTextViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TitleMarqueeTextViewCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * Make marquee work even when the view isn't focused (e.g. browse pip title)
     */
    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    protected void updateMarquee() {
        Utils.postDelayed(mUpdateMarquee, 1_000);
    }

    private void init() {
        int maxWidth = getMaxWidth();

        if (maxWidth != -1) {
            float uiScale = MainUIData.instance(getContext()).getUIScale();

            if (!Helpers.floatEquals(uiScale, 1.0f)) {
                setMaxWidth((int) (maxWidth / uiScale / uiScale));
            }
        }
    }
}
