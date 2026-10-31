package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ThumbsDownAction extends TwoStateAction {
    public ThumbsDownAction(Context context) {
        super(context, R.id.action_thumbs_down, R.drawable.lb_ic_thumb_down, false);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.action_dislike);
        labels[INDEX_ON] = context.getString(R.string.action_dislike);
        setLabels(labels);
    }
}
