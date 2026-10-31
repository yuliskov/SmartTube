package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class SearchAction extends Action {
    public SearchAction(Context context) {
        super(R.id.action_search);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_search);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.action_search));
    }
}
