package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** A live-only, focusable action that seeks to the current live edge. */
public final class GoLiveAction extends PaddingAction {
    public GoLiveAction(Context context) {
        super(R.id.action_go_live);
        setIcon(ContextCompat.getDrawable(context, R.drawable.icon_live));
        setLabel1(context.getString(R.string.action_go_live));
        setPadding(3);
    }
}
