package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class PlaylistAddAction extends Action {
    public PlaylistAddAction(Context context) {
        super(R.id.action_playlist_add);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_playlist_add);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.action_playlist_add));
    }
}
