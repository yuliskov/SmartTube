package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ThumbsUpAction extends ThumbsAction {
    public ThumbsUpAction(Context context) {
        super(R.id.action_thumbs_up, context, R.drawable.lb_ic_thumb_up, R.drawable.lb_ic_thumb_up_outline);
    }
}
