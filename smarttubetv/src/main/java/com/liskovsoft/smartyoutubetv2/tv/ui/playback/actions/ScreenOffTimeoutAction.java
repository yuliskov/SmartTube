package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for enable/disable sponsored content.
 */
public class ScreenOffTimeoutAction extends TwoStateAction {
    public ScreenOffTimeoutAction(Context context) {
        super(context, R.id.action_screen_off_timeout, R.drawable.action_screen_timeout_on);

        String label = context.getString(R.string.action_screen_off);
        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = label;
        labels[INDEX_ON] = label;
        setLabels(labels);
    }
}
