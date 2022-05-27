package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying a CC (Closed Captioning) icon.
 */
public class ClosedCaptioningAction extends MultiAction {
    public static final int INDEX_OFF = 0;

    public static final int INDEX_ON = 1;

    public ClosedCaptioningAction(Context context) {
        this(context, ActionHelpers.getIconHighlightColor(context));
    }

    public ClosedCaptioningAction(Context context, int highlightColor) {
        super(R.id.lb_control_closed_captioning);
        Drawable[] drawables = new Drawable[2];
        BitmapDrawable offDrawable = (BitmapDrawable) ActionHelpers.getStyledDrawable(context,
                R.styleable.lbPlaybackControlsActionIcons_closed_captioning);
        drawables[INDEX_OFF] = offDrawable;
        drawables[INDEX_ON] = offDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(offDrawable.getBitmap(), highlightColor));
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.lb_playback_controls_closed_captioning_enable);
        labels[INDEX_ON] = context.getString(R.string.lb_playback_controls_closed_captioning_enable);
        setLabels(labels);
    }
}
