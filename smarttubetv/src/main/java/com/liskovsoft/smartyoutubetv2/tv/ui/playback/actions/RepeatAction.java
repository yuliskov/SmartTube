package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying two repeat states: none and all.
 */
public class RepeatAction extends MultiAction {
    public static final int INDEX_NONE = 0;
    
    public static final int INDEX_ONE = 1;

    public static final int INDEX_ALL = 2;

    public static final int INDEX_PAUSE = 3;

    /**
     * Constructor
     * @param context Context used for loading resources.
     */
    public RepeatAction(Context context) {
        this(context, ActionHelpers.getIconHighlightColor(context));
    }

    /**
     * Constructor
     * @param context Context used for loading resources
     * @param selectionColor Color to display the repeat-all icon.
     */
    public RepeatAction(Context context, int selectionColor) {
        super(R.id.lb_control_repeat);
        Drawable[] drawables = new Drawable[4];
        BitmapDrawable repeatNoneDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.action_repeat_none);
        BitmapDrawable repeatAllDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.action_repeat_all);
        BitmapDrawable repeatOneDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.action_repeat_one);
        //BitmapDrawable repeatDrawable = (BitmapDrawable) ActionHelpers.getStyledDrawable(context,
        //        R.styleable.lbPlaybackControlsActionIcons_repeat);
        //BitmapDrawable repeatOneDrawable = (BitmapDrawable) ActionHelpers.getStyledDrawable(context,
        //        R.styleable.lbPlaybackControlsActionIcons_repeat_one);
        drawables[INDEX_NONE] = repeatNoneDrawable;
        drawables[INDEX_ONE] = repeatOneDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(repeatOneDrawable.getBitmap(), selectionColor));
        drawables[INDEX_ALL] = repeatAllDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(repeatAllDrawable.getBitmap(), selectionColor));
        drawables[INDEX_PAUSE] = repeatNoneDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(repeatNoneDrawable.getBitmap(), selectionColor));
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        labels[INDEX_NONE] = context.getString(R.string.lb_playback_controls_repeat_none);
        labels[INDEX_ONE] = context.getString(R.string.lb_playback_controls_repeat_one);
        labels[INDEX_ALL] = context.getString(R.string.lb_playback_controls_repeat_all);
        labels[INDEX_PAUSE] = context.getString(R.string.lb_playback_controls_repeat_none);
        setLabels(labels);
    }
}
