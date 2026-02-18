package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat;

import android.content.Context;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * MarqueeTextView used in browse pip title
 */
public class TitleMarqueeTextViewCompat extends MarqueeTextViewCompat {
    private final Runnable mUpdateMarquee = super::updateMarquee;

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

    @Override
    public void setMaxLines(int maxLines) {
        super.setMaxLines(1);
    }

    @Override
    public void setEllipsize(TruncateAt where) {
        super.setEllipsize(TruncateAt.MARQUEE);
    }

    @Override
    protected void updateMarquee() {
        Utils.postDelayed(mUpdateMarquee, 1_000);
    }
}
