package com.liskovsoft.smartyoutubetv2.tv.ui.mod.widgets;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class SmoothMarqueeTextView extends AppCompatTextView {
    public SmoothMarqueeTextView(Context context) {
        super(context);
        init();
    }

    public SmoothMarqueeTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SmoothMarqueeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        Object marquee = Helpers.getField(this, "mMarquee");

        if (marquee != null) {
            
        }
    }
}
