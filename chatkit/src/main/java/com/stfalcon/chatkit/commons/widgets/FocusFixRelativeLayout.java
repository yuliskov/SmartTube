package com.stfalcon.chatkit.commons.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
 */
public class FocusFixRelativeLayout extends RelativeLayout {
    public FocusFixRelativeLayout(Context context) {
        super(context);
    }

    public FocusFixRelativeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusFixRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressWarnings("NewApi")
    public FocusFixRelativeLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void clearFocus() {
        if (getParent() != null) {
            super.clearFocus();
        }
    }
}
