package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying chat/comments.
 */
public class ChatAction extends TwoStateAction {
    public ChatAction(Context context) {
        super(context, R.id.action_chat, R.drawable.action_chat);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.open_chat);
        labels[INDEX_ON] = context.getString(R.string.open_chat);
        setLabels(labels);
    }
}
