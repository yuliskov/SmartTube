package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Circular frame that draws the focus ring outside of its bounds (parent must not clip children).
 */
public class ModernRingFrame extends FrameLayout {
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mRingInset;
    private OnFocusChangeListener mExtraFocusListener;

    public ModernRingFrame(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStrokeWidth(getResources().getDimension(R.dimen.modern_focus_ring_width));
        mRingPaint.setColor(ContextCompat.getColor(context, R.color.modern_focus_ring));
        mRingInset = getResources().getDimension(R.dimen.modern_focus_ring_inset);
        setWillNotDraw(false);

        if (Build.VERSION.SDK_INT >= 21) {
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            setClipToOutline(true);
        }
    }

    public void setExtraFocusListener(OnFocusChangeListener listener) {
        mExtraFocusListener = listener;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (getParent() instanceof View) {
            ((View) getParent()).invalidate();
        }
        if (mExtraFocusListener != null) {
            mExtraFocusListener.onFocusChange(this, gainFocus);
        }
    }

    /**
     * Drawn by the parent since the frame itself clips to its circle.
     */
    public void drawRing(Canvas canvas) {
        if (!isFocused()) {
            return;
        }

        float half = mRingPaint.getStrokeWidth() / 2;
        float radius = getWidth() / 2f + mRingInset - half;
        canvas.drawCircle(getLeft() + getWidth() / 2f, getTop() + getHeight() / 2f, radius, mRingPaint);
    }
}
