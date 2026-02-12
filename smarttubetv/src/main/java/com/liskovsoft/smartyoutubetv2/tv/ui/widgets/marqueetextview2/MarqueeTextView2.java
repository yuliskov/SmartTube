package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.marqueetextview2;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.os.Build;
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
public class MarqueeTextView2 extends TextView {
    /**
     * Unit: PX
     */
    private static final int DEFAULT_SPACE = 100;

    /**
     * Unit: DP
     */
    private static final float DEFAULT_SPEED = 0.5f;
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

    private float mLeftX = 0f;

    /**
     * Minimum spacing distance between head and tail when scrolling text
     */
    private int mSpace = DEFAULT_SPACE;

    /**
     * Text scrolling speed
     */
    private float mSpeed = DEFAULT_SPEED * 2;

    public MarqueeTextView2(Context context) {
        super(context);
        init(null);
    }

    public MarqueeTextView2(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.MarqueeTextView2);
            mSpace = typedArray.getDimensionPixelSize(
                    R.styleable.MarqueeTextView2_space,
                    DEFAULT_SPACE
            );
            float speedDp = typedArray.getFloat(
                    R.styleable.MarqueeTextView2_speed,
                    DEFAULT_SPEED
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
        mTextView.setMaxLines(1);
        setMaxLines(1);

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
        super.setText(text, type);
        // When executing the parent constructor, if AttributeSet contains text,
        // setText will be called first, and mTextView is not initialized yet
        if (mTextView != null) {
            mTextView.setText(text);
            requestLayout();
        }
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        // When executing the parent constructor, if AttributeSet contains textSize,
        // setTextSize will be called first, and mTextView is not initialized yet
        if (mTextView != null) {
            mTextView.setTextSize(size);
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mTextView.measure(View.MeasureSpec.UNSPECIFIED, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mTextView.layout(left, top, left + mTextView.getMeasuredWidth(), bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            onDrawRTL(canvas);
            return;
        }

        if (mTextView.getMeasuredWidth() <= getWidth()) {
            // When text width is smaller than view width, do not scroll
            mTextView.draw(canvas);
        } else {
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
    }

    private void onDrawRTL(Canvas canvas) {
        if (mTextView.getMeasuredWidth() <= getWidth()) {
            // When text width is smaller than view width, do not scroll
            int save = canvas.save();
            canvas.translate((getWidth() - mTextView.getMeasuredWidth()), 0f);
            mTextView.draw(canvas);
            canvas.restoreToCount(save);
        } else {
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
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    private void startScroll() {
        updateFps();
        Choreographer.getInstance().postFrameCallback(mFrameCallback);
    }

    public void pauseScroll() {
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
    }

    private void stopScroll() {
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
}
