package com.liskovsoft.smartyoutubetv2.tv.util;

import android.text.Layout;
import android.widget.TextView;

public class ViewUtil {
    /**
     * Checks whether text is truncated (e.g. has ... at the end)
     */
    public static boolean isEllipsized(TextView textView) {
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
}
