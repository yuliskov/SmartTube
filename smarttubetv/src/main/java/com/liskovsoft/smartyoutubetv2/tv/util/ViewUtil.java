package com.liskovsoft.smartyoutubetv2.tv.util;

import android.os.Build.VERSION;
import android.text.BidiFormatter;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview.MarqueeTextView;

public class ViewUtil {
    /**
     * Focused card zoom factor
     */
    public static final int FOCUS_ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    /**
     * Dim focused card?
     */
    public static final boolean USE_FOCUS_DIMMER = false;
    /**
     * Dim other rows in {@link RowPresenter}
     */
    public static final boolean SELECT_EFFECT_ENABLED = false;
    /**
     * Scroll continue threshold
     */
    public static final int GRID_SCROLL_CONTINUE_NUM = 10;
    public static final int ROW_SCROLL_CONTINUE_NUM = 4;

    /**
     * Checks whether text is truncated (e.g. has ... at the end)
     */
    public static boolean isTruncated(TextView textView) {
        Layout layout = textView.getLayout();
        if (layout != null) {
            int lines = layout.getLineCount();
            if (lines > 0) {
                int ellipsisCount = layout.getEllipsisCount(lines - 1);
                if (ellipsisCount > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void disableMarquee(TextView... textViews) {
        if (textViews == null || textViews.length == 0) {
            return;
        }

        for (TextView textView : textViews) {
            textView.setEllipsize(TruncateAt.END);
            textView.setHorizontallyScrolling(false);
        }
    }

    /**
     * <a href="https://stackoverflow.com/questions/3332924/textview-marquee-not-working">More info</a>
     */
    public static void enableMarquee(TextView... textViews) {
        if (textViews == null || textViews.length == 0) {
            return;
        }

        if (VERSION.SDK_INT > 17) {
            if (BidiFormatter.getInstance().isRtlContext()) {
                // TODO: fix marquee on rtl languages
                return;
            }
        }

        for (TextView textView : textViews) {
            if (ViewUtil.isTruncated(textView)) { // multiline scroll fix
                textView.setEllipsize(TruncateAt.MARQUEE);
                textView.setMarqueeRepeatLimit(-1);
                textView.setHorizontallyScrolling(true);

                // App dialog title fix.
                textView.setSelected(true);
            }
        }
    }

    public static void setTextScrollSpeed(TextView textView, float speed) {
        if (textView instanceof MarqueeTextView) {
            ((MarqueeTextView) textView).setMarqueeSpeedFactor(speed);
        }
    }

    public static void enableView(View view, boolean enabled) {
        if (view != null) {
            view.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    public static void setDimensions(View view, int width, int height) {
        if (view != null) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();

            if (lp != null) {
                if (width > 0) {
                    lp.width = width;
                }
                if (height > 0) {
                    lp.height = height;
                }
                view.setLayoutParams(lp);
            }
        }
    }
}
