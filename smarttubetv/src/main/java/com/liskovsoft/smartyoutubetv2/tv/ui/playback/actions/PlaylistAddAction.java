package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class PlaylistAddAction extends MultiAction {
    public static final int INDEX_OFF = 0;

    public static final int INDEX_ON = 1;

    public PlaylistAddAction(Context context) {
        this(context, ActionHelpers.getIconHighlightColor(context));
    }

    public PlaylistAddAction(Context context, int highlightColor) {
        super(R.id.action_playlist_add);
        Drawable[] drawables = new Drawable[2];
        BitmapDrawable offDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, R.drawable.action_playlist_add);
        drawables[INDEX_OFF] = offDrawable;
        drawables[INDEX_ON] = offDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(offDrawable.getBitmap(), highlightColor));
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.action_playlist_add);
        labels[INDEX_ON] = context.getString(R.string.action_playlist_add);
        setLabels(labels);
    }
}
