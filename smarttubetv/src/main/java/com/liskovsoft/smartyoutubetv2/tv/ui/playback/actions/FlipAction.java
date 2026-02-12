package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for toggling horizontal flip (between -1 and 1 scaleX)
 */
public class FlipAction extends TwoStateAction {
    public FlipAction(Context context) {
        super(context, R.id.action_flip, R.drawable.action_flip);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.video_flip);
        labels[INDEX_ON] = context.getString(R.string.video_flip);
        setLabels(labels);
    }
}
