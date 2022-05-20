package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * An action for displaying subscribe states.
 */
public class SubscribeAction extends MultiAction {
    public static final int INDEX_OFF = 0;
    
    public static final int INDEX_ON = 1;

    public SubscribeAction(Context context) {
        this(context, ActionHelpers.getIconHighlightColor(context));
    }

    public SubscribeAction(Context context, int highlightColor) {
        super(R.id.action_subscribe);
        Drawable[] drawables = new Drawable[2];
        BitmapDrawable offDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.action_subscribe);
        drawables[INDEX_OFF] = offDrawable;
        drawables[INDEX_ON] = offDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(offDrawable.getBitmap(), highlightColor));
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.unsubscribed_from_channel);
        labels[INDEX_ON] = context.getString(R.string.subscribed_to_channel);
        setLabels(labels);
    }
}
