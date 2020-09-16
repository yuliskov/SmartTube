package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying two repeat states: none and all.
 */
public class RepeatAction extends MultiAction {
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

    /**
     * Action index for the repeat-none icon.
     */
    public static final int INDEX_NONE = 0;

    /**
     * Action index for the repeat-all icon.
     */
    public static final int INDEX_ALL = 1;

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
     * @param repeatAllColor Color to display the repeat-all icon.
     */
    public RepeatAction(Context context, int repeatAllColor) {
        super(R.id.lb_control_repeat);
        Drawable[] drawables = new Drawable[2];
        BitmapDrawable repeatDrawable = (BitmapDrawable) ActionHelpers.getStyledDrawable(context,
                R.styleable.lbPlaybackControlsActionIcons_repeat);
        drawables[INDEX_NONE] = repeatDrawable;
        drawables[INDEX_ALL] = repeatDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(repeatDrawable.getBitmap(), repeatAllColor));
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        labels[INDEX_NONE] = context.getString(R.string.lb_playback_controls_repeat_none);
        labels[INDEX_ALL] = context.getString(R.string.lb_playback_controls_repeat_all);
        setLabels(labels);
    }
}
