package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class VideoSpeedAction extends ThumbsAction {
    public VideoSpeedAction(Context context) {
        super(context, R.id.action_video_speed, R.drawable.action_video_speed);

        setLabel1(context.getString(R.string.action_video_speed));
    }
}
