package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ActionHelpers {
    public static int getIconHighlightColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.playbackControlsIconHighlightColor,
                outValue, true)) {
            return outValue.data;
        }
        return ContextCompat.getColor(context, R.color.lb_playback_icon_highlight_no_theme);
    }

    public static int getIconGrayedOutColor(Context context) {
        return ContextCompat.getColor(context, R.color.gray);
    }

    public static BitmapDrawable createDrawable(Context context, BitmapDrawable bitmapDrawable, int bitmapColor) {
        return bitmapDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                createBitmap(bitmapDrawable.getBitmap(), bitmapColor));
    }

    public static Bitmap createBitmap(Bitmap bitmap, int color) {
        Bitmap dst = bitmap.copy(bitmap.getConfig(), true);
        Canvas canvas = new Canvas(dst);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return dst;
    }

    public static Drawable getStyledDrawable(Context context, int index) {
        TypedValue outValue = new TypedValue();
        if (!context.getTheme().resolveAttribute(
                R.attr.playbackControlsActionIcons, outValue, false)) {
            return null;
        }
        TypedArray array = context.getTheme().obtainStyledAttributes(outValue.data,
                R.styleable.lbPlaybackControlsActionIcons);
        Drawable drawable = array.getDrawable(index);
        array.recycle();
        return drawable;
    }
}
