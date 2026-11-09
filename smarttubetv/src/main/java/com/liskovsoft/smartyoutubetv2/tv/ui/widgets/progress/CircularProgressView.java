package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.progress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Modern indeterminate circular progress (Material 3 style).<br/>
 * A rounded arc that grows and shrinks while rotating (no background track),
 * with nothing drawn behind it (no grey track or background disc).<br/>
 * Pure Canvas implementation: works on every API level and costs nothing while hidden.
 */
public class CircularProgressView extends View {
    private static final int DEFAULT_SIZE_DP = 64;
    private static final float STROKE_DP = 3f;
    private static final float ARC_INSET_DP = 14;

    private static final long CYCLE_MS = 1333;    // one grow + shrink of the arc
    private static final long ROTATION_MS = 2222; // one full turn of the whole spinner
    private static final float MIN_SWEEP = 18f;
    private static final float MAX_SWEEP = 270f;
    private static final long FADE_IN_MS = 220;

    private final Paint mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArcBounds = new RectF();
    private final float mDensity;
    private long mStartTimeMs = -1;
    /**
     * API 25+: animated on the RenderThread, so the spinner keeps turning while the main thread
     * is busy (e.g. a video is being loaded). Null on older systems, where onDraw animates it.
     */
    private final AnimatedVectorDrawable mAnimatedDrawable;

    public CircularProgressView(Context context) {
        this(context, null);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDensity = context.getResources().getDisplayMetrics().density;
        float stroke = STROKE_DP * mDensity;

        mArcPaint.setStyle(Paint.Style.STROKE);
        mArcPaint.setStrokeCap(Paint.Cap.ROUND);
        mArcPaint.setStrokeWidth(stroke);
        mArcPaint.setColor(resolveArcColor(context));


        mAnimatedDrawable = Build.VERSION.SDK_INT >= 25 ? createAnimatedDrawable(context) : null;
        if (mAnimatedDrawable != null) {
            mAnimatedDrawable.setCallback(this);
        }

        setFocusable(false);
        setClickable(false);
    }

    @Nullable
    private static AnimatedVectorDrawable createAnimatedDrawable(Context context) {
        try {
            Drawable drawable = context.getDrawable(R.drawable.circular_progress_avd);
            return drawable instanceof AnimatedVectorDrawable ? (AnimatedVectorDrawable) drawable.mutate() : null;
        } catch (Exception e) {
            return null; // fall back to the Canvas animation
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mAnimatedDrawable || super.verifyDrawable(who);
    }

    private void updateAnimation() {
        if (mAnimatedDrawable == null) {
            return;
        }

        if (isAttachedToWindow() && isShown()) {
            // Restart so every appearance gets the fade-in
            mAnimatedDrawable.stop();
            mAnimatedDrawable.start();
        } else {
            mAnimatedDrawable.stop();
        }
    }

    /**
     * SmartTube's brand red on every system (R.color.progress_arc, also used by the API 25+ drawable).
     */
    private static int resolveArcColor(Context context) {
        return context.getResources().getColor(R.color.progress_arc);
    }

    public void setArcColor(int color) {
        mArcPaint.setColor(color);
        invalidate();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false; // no offscreen layer needed for the fade
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(DEFAULT_SIZE_DP * mDensity);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = Math.min(w, h);
        float inset = Math.min(ARC_INSET_DP * mDensity, size / 4f);
        float left = (w - size) / 2f + inset;
        float top = (h - size) / 2f + inset;
        mArcBounds.set(left, top, left + size - inset * 2, top + size - inset * 2);

        if (mAnimatedDrawable != null) {
            int drawableSize = (int) size;
            int dl = (w - drawableSize) / 2;
            int dt = (h - drawableSize) / 2;
            mAnimatedDrawable.setBounds(dl, dt, dl + drawableSize, dt + drawableSize);
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mAnimatedDrawable != null) {
            updateAnimation();
        } else if (visibility == VISIBLE && isShown()) {
            restart();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mAnimatedDrawable != null) {
            updateAnimation();
        } else {
            restart();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAnimatedDrawable != null) {
            mAnimatedDrawable.stop();
        }
    }

    private void restart() {
        mStartTimeMs = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mAnimatedDrawable != null) {
            // Recorded once into the display list; the RenderThread animates it from there
            mAnimatedDrawable.draw(canvas);
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (mStartTimeMs < 0) {
            mStartTimeMs = now;
        }
        long elapsed = now - mStartTimeMs;

        // Soft fade + scale in so the spinner doesn't pop abruptly over the video
        float appear = Math.min(1f, elapsed / (float) FADE_IN_MS);
        appear = 1f - (1f - appear) * (1f - appear); // ease-out
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        int saveCount = canvas.save();
        float scale = 0.85f + 0.15f * appear;
        canvas.scale(scale, scale, cx, cy);

        mArcPaint.setAlpha(Math.round(255 * appear));

        // Arc geometry: head leads during first half of the cycle, tail catches up in the second half
        long cycle = elapsed / CYCLE_MS;
        float t = (elapsed % CYCLE_MS) / (float) CYCLE_MS;
        float delta = MAX_SWEEP - MIN_SWEEP;
        float base = (cycle * delta) % 360f;
        float rotation = (elapsed % ROTATION_MS) * 360f / ROTATION_MS;

        float start;
        float sweep;
        if (t < 0.5f) {
            sweep = MIN_SWEEP + delta * ease(t * 2f);
            start = 0;
        } else {
            float p = ease((t - 0.5f) * 2f);
            sweep = MAX_SWEEP - delta * p;
            start = delta * p;
        }

        canvas.drawArc(mArcBounds, rotation + base + start - 90f, sweep, false, mArcPaint);
        canvas.restoreToCount(saveCount);

        if (isShown()) {
            if (Build.VERSION.SDK_INT >= 16) {
                postInvalidateOnAnimation();
            } else {
                postInvalidateDelayed(16);
            }
        }
    }

    /** Cubic ease-in-out — close to Material's fast-out-slow-in feel. */
    private static float ease(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }
}
