package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * MarqueeTextView used in browse section headers
 */
public class HeaderMarqueeTextViewCompat extends MarqueeTextViewCompat {
    public HeaderMarqueeTextViewCompat(Context context) {
        super(context);

        init();
    }

    public HeaderMarqueeTextViewCompat(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public HeaderMarqueeTextViewCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        ViewUtil.applyMarqueeRtlParams(this, true);
    }

    /**
     * Sidebar rail (collapsed) squeezes the label to a few px: hide it instead of showing an ellipsis.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setAlpha(w < 20 * getResources().getDisplayMetrics().density ? 0f : 1f);
        updateParentDivider();
    }

    /**
     * The row below Library draws a divider line as the 2nd layer of its background.
     * Like YouTube TV, show it only in the expanded sidebar (the collapsed rail shows just the gap).
     */
    public void updateParentDivider() {
        if (!(getParent() instanceof View)) {
            return;
        }
        Drawable bg = ((View) getParent()).getBackground();
        if (bg instanceof LayerDrawable && ((LayerDrawable) bg).getNumberOfLayers() > 1) {
            boolean collapsed = getWidth() > 0 && getAlpha() == 0f;
            ((LayerDrawable) bg.mutate()).getDrawable(1).setAlpha(collapsed ? 0 : 255);
            bg.invalidateSelf();
        }
    }
}
