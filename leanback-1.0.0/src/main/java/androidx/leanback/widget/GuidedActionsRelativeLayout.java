package androidx.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.leanback.R;

/**
 * Relative layout implementation that assign subactions list topMargin based on a percentage
 * given by "guidedStepKeyline" theme attribute when the topMargin is set to a negative value.
 */
class GuidedActionsRelativeLayout extends RelativeLayout {

    interface InterceptKeyEventListener {
        public boolean onInterceptKeyEvent(KeyEvent event);
    }

    private float mKeyLinePercent;
    private boolean mInOverride = false;
    private InterceptKeyEventListener mInterceptKeyEventListener;

    public GuidedActionsRelativeLayout(Context context) {
        this(context, null);
    }

    public GuidedActionsRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuidedActionsRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mKeyLinePercent = GuidanceStylingRelativeLayout.getKeyLinePercent(context);
    }

    private void init() {
        TypedArray ta = getContext().getTheme().obtainStyledAttributes(
                R.styleable.LeanbackGuidedStepTheme);
        mKeyLinePercent = ta.getFloat(R.styleable.LeanbackGuidedStepTheme_guidedStepKeyline,
                40);
        ta.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightSize > 0) {
            View view = findViewById(R.id.guidedactions_sub_list);
            if (view != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                        view.getLayoutParams();
                if (lp.topMargin < 0 && !mInOverride) {
                    mInOverride = true;
                }
                if (mInOverride) {
                    lp.topMargin = (int) (mKeyLinePercent * heightSize / 100);
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mInOverride = false;
    }

    public void setInterceptKeyEventListener(InterceptKeyEventListener l) {
        mInterceptKeyEventListener = l;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mInterceptKeyEventListener != null) {
            if (mInterceptKeyEventListener.onInterceptKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
