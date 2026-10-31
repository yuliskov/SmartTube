package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying a channel icon.
 */
public class VideoZoomAction extends Action {
    public VideoZoomAction(Context context) {
        super(R.id.action_video_zoom);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_video_zoom);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.video_aspect));
    }
}
