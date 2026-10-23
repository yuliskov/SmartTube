package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying start mix states.
 */
public class StartMixAction extends TwoStateAction {
    public StartMixAction(Context context) {
        super(context, R.id.action_start_mix, R.drawable.action_start_mix);

        String[] labels = new String[2];
        labels[INDEX_OFF] = context.getString(R.string.start_mix);
        labels[INDEX_ON] = context.getString(R.string.start_mix);
        setLabels(labels);
    }
}
