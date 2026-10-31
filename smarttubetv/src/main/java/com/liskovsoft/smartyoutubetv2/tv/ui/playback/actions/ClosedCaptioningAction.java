package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying a CC (Closed Captioning) icon.
 */
public class ClosedCaptioningAction extends TwoStateAction {
    public ClosedCaptioningAction(Context context) {
        super(context, R.id.lb_control_closed_captioning, R.drawable.lb_ic_cc);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.lb_playback_controls_closed_captioning_enable);
        labels[INDEX_ON] = context.getString(R.string.lb_playback_controls_closed_captioning_disable);
        setLabels(labels);
    }
}
