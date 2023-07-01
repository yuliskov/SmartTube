package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying a PIP icon.
 */
public class ScreenOffAction extends TwoStateAction {
    public ScreenOffAction(Context context) {
        super(context, R.id.action_screen_off, R.drawable.action_screen_off);

        String label = context.getString(R.string.action_screen_off);
        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = label;
        labels[INDEX_ON] = label;
        setLabels(labels);
    }
}
