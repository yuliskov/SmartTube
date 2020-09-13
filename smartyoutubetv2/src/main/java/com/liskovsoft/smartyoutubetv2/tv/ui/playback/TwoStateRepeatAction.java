package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

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
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying two repeat states: none and all.
 */
public class TwoStateRepeatAction extends MultiAction {
    /**
     * Action index for the repeat-none icon.
     * @deprecated Use {@link #INDEX_NONE}
     */
    @Deprecated
    public static final int NONE = 0;

    /**
     * Action index for the repeat-all icon.
     * @deprecated Use {@link #INDEX_ALL}
     */
    @Deprecated
    public static final int ALL = 1;

    ///**
    // * Action index for the repeat-one icon.
    // * @deprecated Use {@link #INDEX_ONE}
    // */
    //@Deprecated
    //public static final int ONE = 2;

    /**
     * Action index for the repeat-none icon.
     */
    public static final int INDEX_NONE = 0;

    /**
     * Action index for the repeat-all icon.
     */
    public static final int INDEX_ALL = 1;

    ///**
    // * Action index for the repeat-one icon.
    // */
    //public static final int INDEX_ONE = 2;

    /**
     * Constructor
     * @param context Context used for loading resources.
     */
    public TwoStateRepeatAction(Context context) {
        this(context, getIconHighlightColor(context));
    }

    /**
     * Constructor
     * @param context Context used for loading resources
     * @param highlightColor Color to display the repeat-all and repeat0one icons.
     */
    public TwoStateRepeatAction(Context context, int highlightColor) {
        this(context, highlightColor, highlightColor);
    }

    /**
     * Constructor
     * @param context Context used for loading resources
     * @param repeatAllColor Color to display the repeat-all icon.
     * @param repeatOneColor Color to display the repeat-one icon.
     */
    public TwoStateRepeatAction(Context context, int repeatAllColor, int repeatOneColor) {
        super(R.id.lb_control_repeat);
        Drawable[] drawables = new Drawable[2];
        BitmapDrawable repeatDrawable = (BitmapDrawable) getStyledDrawable(context,
                R.styleable.lbPlaybackControlsActionIcons_repeat);
        //BitmapDrawable repeatOneDrawable = (BitmapDrawable) getStyledDrawable(context,
        //        R.styleable.lbPlaybackControlsActionIcons_repeat_one);
        drawables[INDEX_NONE] = repeatDrawable;
        drawables[INDEX_ALL] = repeatDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                createBitmap(repeatDrawable.getBitmap(), repeatAllColor));
        //drawables[INDEX_ONE] = repeatOneDrawable == null ? null
        //        : new BitmapDrawable(context.getResources(),
        //        createBitmap(repeatOneDrawable.getBitmap(), repeatOneColor));
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        labels[INDEX_NONE] = context.getString(R.string.lb_playback_controls_repeat_all);
        labels[INDEX_ALL] = context.getString(R.string.lb_playback_controls_repeat_one);
        //labels[INDEX_ONE] = context.getString(R.string.lb_playback_controls_repeat_none);
        setLabels(labels);
    }

    private static int getIconHighlightColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.playbackControlsIconHighlightColor,
                outValue, true)) {
            return outValue.data;
        }
        return context.getResources().getColor(R.color.lb_playback_icon_highlight_no_theme);
    }

    private static Bitmap createBitmap(Bitmap bitmap, int color) {
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
