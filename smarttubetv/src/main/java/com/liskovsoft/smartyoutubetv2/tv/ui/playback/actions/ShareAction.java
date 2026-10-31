package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for sharing a video link.
 */
public class ShareAction extends Action {
    public ShareAction(Context context) {
        super(R.id.action_share);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_share);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.share_link));
    }
}
