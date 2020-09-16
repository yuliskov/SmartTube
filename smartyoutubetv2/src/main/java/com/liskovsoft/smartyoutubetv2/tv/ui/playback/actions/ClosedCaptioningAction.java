package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying a CC (Closed Captioning) icon.
 */
public class ClosedCaptioningAction extends Action {
    public ClosedCaptioningAction(Context context) {
        super(R.id.lb_control_closed_captioning);
        BitmapDrawable uncoloredDrawable = (BitmapDrawable) ActionHelpers.getStyledDrawable(context,
                R.styleable.lbPlaybackControlsActionIcons_closed_captioning);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.lb_playback_controls_closed_captioning_enable));
    }
}
