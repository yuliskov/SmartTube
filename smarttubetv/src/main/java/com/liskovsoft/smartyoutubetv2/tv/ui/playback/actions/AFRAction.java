package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;

public class AFRAction extends TwoStateAction {
    public AFRAction(Context context) {
        super(context, R.id.action_afr, R.drawable.action_afr);

        String label = context.getString(R.string.auto_frame_rate);
        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = label;
        labels[INDEX_ON] = label;
        setLabels(labels);
    }
}
