package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextviewcompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Build;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.tv.R;

@SuppressLint("AppCompatCustomView")
class MarqueeTextViewCompatBase extends TextView {
    /**
     * Unit: PX
     */
    private static final int DEFAULT_SPACE = 100;

    /**
     * Unit: DP
     */
    private static final float ORIGINAL_SPEED = 0.5f; // Don't change. The original marquee speed.
    private static final float DEFAULT_SPEED = ORIGINAL_SPEED * 2;
    private static final float BASE_FPS = 60f;

    private int mFps = 60;

    /*
     * Create an internal TextView to ensure the actual text occupies the same space
     * as displayed in TextView. Calculating text length using paint may not match
     * the actual width when rendered in TextView, which can cause the text to be
     * truncated by a few pixels and stop scrolling.
     */
    private TextView mTextView;

    private final FrameCallback mFrameCallback = new FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                mLeftX += BASE_FPS / mFps * mSpeed;
            } else {
                mLeftX -= BASE_FPS / mFps * mSpeed;
            }

            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    protected float mLeftX = 0f;

    /**
     * Minimum spacing distance between head and tail when scrolling text
     */
    protected int mSpace = DEFAULT_SPACE;

    /**
     * Text scrolling speed
     */
    private float mSpeed = ORIGINAL_SPEED * 2;

    public MarqueeTextViewCompatBase(Context context) {
        super(context);
        init(null);
    }

    public MarqueeTextViewCompatBase(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.MarqueeTextViewCompatBase);
            mSpace = typedArray.getDimensionPixelSize(
                    R.styleable.MarqueeTextViewCompatBase_space,
                    DEFAULT_SPACE
            );
            float speedDp = typedArray.getFloat(
                    R.styleable.MarqueeTextViewCompatBase_speed, DEFAULT_SPEED
            );
            mSpeed = dpToPx(speedDp, getContext());
            typedArray.recycle();
        } else {
            mSpeed = dpToPx(DEFAULT_SPEED, getContext());
        }

        mTextView = new TextView(getContext(), attrs);
        mTextView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        mTextView.setMaxLines(getMaxLines());
        mTextView.setTextAlignment(getTextAlignment());
        super.setEllipsize(TruncateAt.END);

        mTextView.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(
                    View v,
                    int left,
                    int top,
                    int right,
                    int bottom,
                    int oldLeft,
                    int oldTop,
                    int oldRight,
                    int oldBottom
            ) {
                restartScroll();
            }
        });
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        // When executing the parent constructor, if AttributeSet contains text,
        // setText will be called first, and mTextView is not initialized yet
        if (mTextView != null) {
            mTextView.setText(text);
        }

        super.setText(text, type);

        // When executing the parent constructor, if AttributeSet contains text,
        // setText will be called first, and mTextView is not initialized yet
        //if (mTextView != null) {
        //    mTextView.setText(text);
        //    requestLayout();
        //}
    }

    @Override
    public void setTextSize(int unit, float size) {
        // When executing the parent constructor, if AttributeSet contains textSize,
        // setTextSize will be called first, and mTextView is not initialized yet
        if (mTextView != null) {
            mTextView.setTextSize(size);
        }

        super.setTextSize(unit, size);

        // When executing the parent constructor, if AttributeSet contains textSize,
        // setTextSize will be called first, and mTextView is not initialized yet
        //if (mTextView != null) {
        //    mTextView.setTextSize(size);
        //    requestLayout();
        //}
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mTextView.measure(isStaticMode() ? widthMeasureSpec : View.MeasureSpec.UNSPECIFIED, heightMeasureSpec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        mTextView.layout(left, top, left + mTextView.getMeasuredWidth(), bottom);
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isTextFullyVisible()) {
            // When text width is smaller than view width, do not scroll
            super.onDraw(canvas);
        } else if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            onDrawRTL(canvas);
        } else {
            onDrawLTR(canvas);
        }
    }

    private void onDrawLTR(Canvas canvas) {
        // When left shift exceeds actual text length + space, reset mLeftX to create loop scrolling
        if (mLeftX < -mTextView.getMeasuredWidth() - mSpace) {
            mLeftX += (mTextView.getMeasuredWidth() + mSpace);
        }

        int save = canvas.save();
        canvas.translate(mLeftX, 0f);
        mTextView.draw(canvas);
        canvas.restoreToCount(save);

        // When text is fully displayed, draw second copy on the right side of visible area
        // to create continuous scrolling effect
        if (mLeftX + (mTextView.getMeasuredWidth() - getWidth()) < 0) {
            int save2 = canvas.save();
            canvas.translate(mTextView.getMeasuredWidth() + mLeftX + mSpace, 0f);
            mTextView.draw(canvas);
            canvas.restoreToCount(save2);
        }
    }

    private void onDrawRTL(Canvas canvas) {
        // When right shift exceeds actual text length + space, reset mLeftX to create loop scrolling
        if (mLeftX > mTextView.getMeasuredWidth() + mSpace) {
            mLeftX -= (mTextView.getMeasuredWidth() + mSpace);
        }

        int save = canvas.save();
        canvas.translate(-(mTextView.getMeasuredWidth() - getWidth()) + mLeftX, 0f);
        mTextView.draw(canvas);
        canvas.restoreToCount(save);

        // When text is fully displayed, draw second copy on the left side of visible area
        // to create continuous scrolling effect
        if (mLeftX - (mTextView.getMeasuredWidth() - getWidth()) > 0) {
            int save2 = canvas.save();
            canvas.translate(
                    -mTextView.getMeasuredWidth() - mSpace + mLeftX - (mTextView.getMeasuredWidth() - getWidth()),
                    0f
            );
            mTextView.draw(canvas);
            canvas.restoreToCount(save2);
        }
    }

    private void updateFps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mFps = (int) getContext().getDisplay().getRefreshRate();
        } else {
            WindowManager windowManager =
                    (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            mFps = (int) windowManager.getDefaultDisplay().getRefreshRate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        super.onDetachedFromWindow();
    }


    @Override
    public void setBackgroundColor(int color) {
        mTextView.setBackgroundColor(color);
        super.setBackgroundColor(color);
    }

    @Override
    public void setTextColor(int color) {
        mTextView.setTextColor(color);
        super.setTextColor(color);
    }

    @Override
    public void setTextDirection(int textDirection) {
        mTextView.setTextDirection(textDirection);
        super.setTextDirection(textDirection);
    }

    @Override
    public void setTextAlignment(int textAlignment) {
        mTextView.setTextAlignment(textAlignment);
        super.setTextAlignment(textAlignment);
    }

    @Override
    public void setGravity(int gravity) {
        mTextView.setGravity(gravity);
        super.setGravity(gravity);
    }

    @Override
    public void setEllipsize(TruncateAt where) {
        // NOP
    }

    @Override
    public void setMarqueeRepeatLimit(int marqueeLimit) {
        // NOP
    }

    @Override
    public void setHorizontallyScrolling(boolean whether) {
        // NOP
    }

    public void setMarqueeSpeedFactor(float factor) {
        mSpeed = dpToPx(ORIGINAL_SPEED * factor, getContext());
    }

    protected void startScroll() {
        updateFps();
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    private void pauseScroll() {
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    protected void stopScroll() {
        mLeftX = 0f;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    private void restartScroll() {
        stopScroll();
        startScroll();
    }

    // Convert px value to sp value
    private float dpToPx(float dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return dp * density;
    }

    private boolean isStaticMode() {
        return isTextFullyVisible() || !(isFocused() || isSelected());
    }

    protected boolean isTextFullyVisible() {
        Layout layout = getLayout();
        if (layout == null) return false;

        int lines = layout.getLineCount();
        for (int i = 0; i < lines; i++) {
            if (layout.getEllipsisCount(i) > 0) {
                return false;
            }
        }
        return true;
    }
}
