package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class BugReportAction extends Action {
    public BugReportAction(Context context) {
        super(R.id.action_bug_report);
        Drawable uncoloredDrawable = ContextCompat.getDrawable(context, R.drawable.action_bug_report);

        setIcon(uncoloredDrawable);
        setLabel1(context.getString(
                R.string.action_bug_report));
    }
}
