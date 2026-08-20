package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class NightlightAction extends TwoStateAction {
    public NightlightAction(Context context) {
        super(context, R.id.action_nightlight, R.drawable.action_nightlight);

        String label = context.getString(R.string.nightlight);
        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = label;
        labels[INDEX_ON] = label;
        setLabels(labels);
    }
}
