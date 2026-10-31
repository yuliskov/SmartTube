package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for toggle between 0 and 90 degrees rotation.
 */
public class RotateAction extends TwoStateAction {
    public RotateAction(Context context) {
        super(context, R.id.action_rotate, R.drawable.action_rotate);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.video_rotate);
        labels[INDEX_ON] = context.getString(R.string.video_rotate);
        setLabels(labels);
    }
}
