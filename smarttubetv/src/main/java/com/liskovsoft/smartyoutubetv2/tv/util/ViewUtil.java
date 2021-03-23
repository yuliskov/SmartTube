package com.liskovsoft.smartyoutubetv2.tv.util;

import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.widget.TextView;
import androidx.leanback.widget.FocusHighlight;

public class ViewUtil {
    public static final int ZOOM_FACTOR = FocusHighlight.ZOOM_FACTOR_SMALL;
    public static final boolean USE_ITEM_FOCUS_DIMMER = false;
    public static final boolean USE_ROW_FOCUS_DIMMER = false;

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
}
