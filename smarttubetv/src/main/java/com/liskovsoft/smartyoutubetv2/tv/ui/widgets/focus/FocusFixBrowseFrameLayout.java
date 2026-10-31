package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.focus;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.leanback.widget.BrowseFrameLayout;

public class FocusFixBrowseFrameLayout extends BrowseFrameLayout {
    public FocusFixBrowseFrameLayout(Context context) {
        super(context);
    }

    public FocusFixBrowseFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusFixBrowseFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        return focused;
    }
}
