package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.progress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Modern indeterminate circular progress (Material 3 style).<br/>
 * A rounded arc that grows and shrinks while rotating (no background track),
 * drawn on a soft translucent disc so it stays readable on top of any video frame.<br/>
 * Pure Canvas implementation: works on every API level and costs nothing while hidden.
 */
public class CircularProgressView extends View {
    private static final int DEFAULT_SIZE_DP = 64;
    private static final float STROKE_DP = 4.5f;
    private static final float ARC_INSET_DP = 14;

    private static final long CYCLE_MS = 1333;    // one grow + shrink of the arc
    private static final long ROTATION_MS = 2222; // one full turn of the whole spinner
    private static final float MIN_SWEEP = 18f;
    private static final float MAX_SWEEP = 270f;
    private static final long FADE_IN_MS = 220;
    private static final int YOUTUBE_RED = 0xFFFF0000;

    private final Paint mArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDiscPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mArcBounds = new RectF();
    private final float mDensity;
    private long mStartTimeMs = -1;

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

        mDiscPaint.setStyle(Paint.Style.FILL);
        mDiscPaint.setColor(Color.argb(115, 0, 0, 0));

        setFocusable(false);
        setClickable(false);
    }

    /**
     * Android 12+ : the system's dynamic (Material You / wallpaper) accent color.<br/>
     * Older systems or devices without dynamic color: YouTube red.
     */
    private static int resolveArcColor(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                // Tone 300: vivid enough to pop, light enough to read on the dark video overlay
                return context.getResources().getColor(android.R.color.system_accent1_300, context.getTheme());
            } catch (Exception e) {
                // Some TV/vendor builds strip the dynamic palette
            }
        }
        return YOUTUBE_RED;
    }

    public void setArcColor(int color) {
        mArcPaint.setColor(color);
        invalidate();
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
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && isShown()) {
            restart();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        restart();
    }

    private void restart() {
        mStartTimeMs = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

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

        int alpha = Math.round(255 * appear);
        mDiscPaint.setAlpha(Math.round(115 * appear));
        mArcPaint.setAlpha(alpha);

        float radius = Math.min(getWidth(), getHeight()) / 2f;
        canvas.drawCircle(cx, cy, radius, mDiscPaint);

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
