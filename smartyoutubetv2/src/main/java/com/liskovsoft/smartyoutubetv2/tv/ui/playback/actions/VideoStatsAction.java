package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class VideoStatsAction extends ThumbsAction {
    public VideoStatsAction(Context context) {
        super(context, R.id.action_video_stats, R.drawable.action_video_stats);
    }
}
