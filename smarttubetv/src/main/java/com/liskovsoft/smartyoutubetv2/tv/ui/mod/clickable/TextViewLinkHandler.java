package com.liskovsoft.smartyoutubetv2.tv.ui.mod.clickable;

import android.text.Layout;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Usage:
 * <pre>
 *     textView.setMovementMethod(new TextViewLinkHandler() {
 *         public void onLinkClick(String url) {
 *             Toast.makeText(textView.getContext(), url, Toast.LENGTH_SHORT).show();
 *         }
 *     });
 * </pre>
 */
public abstract class TextViewLinkHandler extends LinkMovementMethod {
    public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(widget, buffer, event);
        }

        int x = (int) event.getX();
        int y = (int) event.getY();

        x -= widget.getTotalPaddingLeft();
        y -= widget.getTotalPaddingTop();

        x += widget.getScrollX();
        y += widget.getScrollY();

        Layout layout = widget.getLayout();
        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);

        URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
        if (link.length != 0) {
            onLinkClick(link[0].getURL());
        }
        return true;
    }

    abstract public void onLinkClick(String url);
}
