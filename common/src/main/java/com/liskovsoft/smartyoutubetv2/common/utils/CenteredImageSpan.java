package com.liskovsoft.smartyoutubetv2.common.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

public class CenteredImageSpan extends ReplacementSpan {
    private static final float SCALE = 1.4f;
    private final Drawable drawable;
    private int height;

    public CenteredImageSpan(Drawable drawable) {
        this.drawable = drawable.mutate();
    }

    @Override
    public int getSize(Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {

        Paint.FontMetricsInt fontMetrics = paint.getFontMetricsInt();

        int textHeight = fontMetrics.descent - fontMetrics.ascent;
        int targetHeight = Math.round(textHeight * SCALE);

        int intrinsicW = drawable.getIntrinsicWidth();
        int intrinsicH = drawable.getIntrinsicHeight();

        float scale = (float) targetHeight / (float) intrinsicH;

        int width = (int) (intrinsicW * scale);
        height = targetHeight;

        drawable.setBounds(0, 0, width, height);

        // IMPORTANT: do NOT modify fm (keeps text layout stable)
        //if (fm != null) {
        //    fm.ascent = fontMetrics.ascent;
        //    fm.descent = fontMetrics.descent;
        //    fm.top = fontMetrics.top;
        //    fm.bottom = fontMetrics.bottom;
        //}

        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text,
                     int start, int end,
                     float x, int top, int y, int bottom, @NonNull Paint paint) {
        canvas.save();

        int transY = top + (bottom - top - height) / 2;

        canvas.translate(x, transY);
        drawable.draw(canvas);

        canvas.restore();
    }
}
