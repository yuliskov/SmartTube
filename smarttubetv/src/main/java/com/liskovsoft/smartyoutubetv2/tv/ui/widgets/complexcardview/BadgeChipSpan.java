package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;

/**
 * YouTube TV metadata badge ("4K", "Auto-dubbed") drawn as a rounded grey chip.<br/>
 * Badges arrive wrapped in {@link YouTubeHelper#BADGE_START}/{@link YouTubeHelper#BADGE_END}.
 */
public class BadgeChipSpan extends ReplacementSpan {
    private static final int CHIP_COLOR = 0xFF3A3A3A;
    private static final int CHIP_TEXT_COLOR = 0xFFE8E8E8;
    private final float mPadding;
    private final float mRadius;
    private final RectF mRect = new RectF();

    private BadgeChipSpan(float density) {
        mPadding = 6 * density;
        mRadius = 4 * density;
    }

    public static @Nullable CharSequence apply(@Nullable CharSequence text, float density) {
        if (text == null) {
            return null;
        }

        SpannableStringBuilder result = new SpannableStringBuilder(text);
        int start;
        while ((start = indexOf(result, YouTubeHelper.BADGE_START, 0)) != -1) {
            int end = indexOf(result, YouTubeHelper.BADGE_END, start);
            if (end == -1) {
                result.delete(start, start + 1);
                continue;
            }
            // drop the markers, chip the label between them
            result.delete(end, end + 1);
            result.delete(start, start + 1);
            if (end - 1 > start) {
                result.setSpan(new BadgeChipSpan(density), start, end - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        return result;
    }

    private static int indexOf(CharSequence text, char ch, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
        if (fm != null) {
            paint.getFontMetricsInt(fm);
        }
        return Math.round(paint.measureText(text, start, end) + mPadding * 2);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        int color = paint.getColor();

        mRect.set(x, y + fm.ascent, x + paint.measureText(text, start, end) + mPadding * 2, y + fm.descent);
        paint.setColor(CHIP_COLOR);
        canvas.drawRoundRect(mRect, mRadius, mRadius, paint);

        paint.setColor(CHIP_TEXT_COLOR);
        canvas.drawText(text, start, end, x + mPadding, y, paint);
        paint.setColor(color);
    }
}
