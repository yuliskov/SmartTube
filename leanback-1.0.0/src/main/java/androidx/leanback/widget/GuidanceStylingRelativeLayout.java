package androidx.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.leanback.R;

/**
 * Relative layout implementation that lays out child views based on provided keyline percent(
 * distance of TitleView baseline from the top).
 *
 * Repositioning child views in PreDraw callback in {@link GuidanceStylist} was interfering with
 * fragment transition. To avoid that, we do that in the onLayout pass.
 */
class GuidanceStylingRelativeLayout extends RelativeLayout {
    private float mTitleKeylinePercent;

    public GuidanceStylingRelativeLayout(Context context) {
        this(context, null);
    }

    public GuidanceStylingRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuidanceStylingRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTitleKeylinePercent = getKeyLinePercent(context);
    }

    public static float getKeyLinePercent(Context context) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(
                R.styleable.LeanbackGuidedStepTheme);
        float percent = ta.getFloat(R.styleable.LeanbackGuidedStepTheme_guidedStepKeyline, 40);
        ta.recycle();
        return percent;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        View mTitleView = getRootView().findViewById(R.id.guidance_title);
        View mBreadcrumbView = getRootView().findViewById(R.id.guidance_breadcrumb);
        View mDescriptionView = getRootView().findViewById(
                R.id.guidance_description);
        ImageView mIconView = getRootView().findViewById(R.id.guidance_icon);
        int mTitleKeylinePixels = (int) (getMeasuredHeight() * mTitleKeylinePercent / 100);

        if (mTitleView != null && mTitleView.getParent() == this) {
            int titleViewBaseline = mTitleView.getBaseline();
            int mBreadcrumbViewHeight = mBreadcrumbView.getMeasuredHeight();
            int guidanceTextContainerTop = mTitleKeylinePixels
                    - titleViewBaseline - mBreadcrumbViewHeight - mTitleView.getPaddingTop();
            int offset = guidanceTextContainerTop - mBreadcrumbView.getTop();

            if (mBreadcrumbView != null && mBreadcrumbView.getParent() == this) {
                mBreadcrumbView.offsetTopAndBottom(offset);
            }

            mTitleView.offsetTopAndBottom(offset);

            if (mDescriptionView != null && mDescriptionView.getParent() == this) {
                mDescriptionView.offsetTopAndBottom(offset);
            }
        }

        if (mIconView != null && mIconView.getParent() == this) {
            Drawable drawable = mIconView.getDrawable();
            if (drawable != null) {
                mIconView.offsetTopAndBottom(
                        mTitleKeylinePixels - mIconView.getMeasuredHeight() / 2);
            }
        }
    }
}
