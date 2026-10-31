package com.stfalcon.chatkit.commons.widgets;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Alter multiline TextView behavior to wrap content exactly.<br/>
 * <a href="https://stackoverflow.com/questions/7439748/why-is-wrap-content-in-multiple-line-textview-filling-parent">Original discussion 1</a><br/>
 * <a href="https://stackoverflow.com/questions/10913384/how-to-make-textview-wrap-its-multiline-content-exactly">Original discussion 2</a><br/>
 */
public class WrapWidthTextView extends AppCompatTextView {
    public WrapWidthTextView(@NonNull Context context) {
        super(context);
    }

    public WrapWidthTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WrapWidthTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        Layout layout = getLayout();
        if (layout != null) {
            int width = (int) Math.ceil(getMaxLineWidth(layout))
                    + getCompoundPaddingLeft() + getCompoundPaddingRight();
            int height = getMeasuredHeight();
            setMeasuredDimension(width, height);
        }
    }

    private float getMaxLineWidth(Layout layout) {
        float max_width = 0.0f;
        int lines = layout.getLineCount();
        for (int i = 0; i < lines; i++) {
            if (layout.getLineWidth(i) > max_width) {
                max_width = layout.getLineWidth(i);
            }
        }
        return max_width;
    }
}
