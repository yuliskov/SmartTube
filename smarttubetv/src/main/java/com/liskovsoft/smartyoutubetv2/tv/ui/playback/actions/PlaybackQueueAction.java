package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying a PIP icon.
 */
public class PlaybackQueueAction extends Action {
    public PlaybackQueueAction(Context context) {
        super(R.id.action_playback_queue);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_playlist);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.action_playback_queue));
    }
}
