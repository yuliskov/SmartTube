package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ThumbsDownAction extends ThumbsAction {
    public ThumbsDownAction(Context context) {
        super(R.id.action_thumbs_down, context, R.drawable.lb_ic_thumb_down, R.drawable.lb_ic_thumb_down_outline);
    }
}
