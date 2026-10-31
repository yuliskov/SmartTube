package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import com.liskovsoft.sharedutils.helpers.Helpers;

@SuppressLint("AppCompatCustomView")
public class MarqueeTextView extends TextView implements View.OnLayoutChangeListener {
    private float mDefaultMarqueeSpeed;
    private float mMarqueeSpeedFactor = 2.0f;

    public MarqueeTextView(Context context) {
        super(context);
    }

    public MarqueeTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MarqueeTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);

        addOnLayoutChangeListener(this);
    }

    private void updateMarqueeSpeed() {
        Object marquee = Helpers.getField(this, "mMarquee");

        if (marquee != null) {
            if (mDefaultMarqueeSpeed == 0) {
                Object defaultMarqueeSpeed = Helpers.getField(marquee, getSpeedFieldName());
                if (defaultMarqueeSpeed != null) {
                    mDefaultMarqueeSpeed = (float) defaultMarqueeSpeed;
                }
            }

            if (mMarqueeSpeedFactor > 0) {
                Helpers.setField(marquee, getSpeedFieldName(), mDefaultMarqueeSpeed * mMarqueeSpeedFactor);
            }
        }
    }

    private String getSpeedFieldName() {
        String result;

        if (VERSION.SDK_INT > 27) {
            result = "mPixelsPerMs";
        } else if (VERSION.SDK_INT > 21) {
            result = "mPixelsPerSecond";
        } else {
            result = "mScrollUnit";
        }

        return result;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        updateMarqueeSpeed();
    }

    public void setMarqueeSpeedFactor(float factor) {
        if (factor > 0) {
            mMarqueeSpeedFactor = factor;
        }
    }

    public float getMarqueeSpeedFactor() {
        return mMarqueeSpeedFactor;
    }
}
