package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for sharing a video link.
 */
public class SeekIntervalAction extends Action {
    public SeekIntervalAction(Context context) {
        super(R.id.action_seek_interval);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_seek_interval);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.seek_interval));
    }
}
