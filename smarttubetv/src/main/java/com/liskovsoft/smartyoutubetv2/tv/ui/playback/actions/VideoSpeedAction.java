package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class VideoSpeedAction extends TwoStateAction {
    public VideoSpeedAction(Context context) {
        super(context, R.id.action_video_speed, R.drawable.action_video_speed);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.action_video_speed);
        labels[INDEX_ON] = context.getString(R.string.action_video_speed);
        setLabels(labels);
    }
}
