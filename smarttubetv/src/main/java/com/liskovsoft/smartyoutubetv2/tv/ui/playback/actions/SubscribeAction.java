package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying subscribe states.
 */
public class SubscribeAction extends TwoStateAction {
    public SubscribeAction(Context context) {
        super(context, R.id.action_subscribe, R.drawable.action_subscribe);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.unsubscribed_from_channel);
        labels[INDEX_ON] = context.getString(R.string.subscribed_to_channel);
        setLabels(labels);
    }
}
