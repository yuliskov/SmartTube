package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class ThumbsAction extends MultiAction {
    /**
     * Action index for the solid thumb icon.
     */
    public static final int INDEX_ON = 0;

    /**
     * Action index for the outline thumb icon.
     */
    public static final int INDEX_OFF = 1;

    private ThumbsAction mBoundAction;

    public ThumbsAction(Context context, int actionId, int solidIconResId) {
        this(context, actionId, solidIconResId, ActionHelpers.getIconHighlightColor(context));
    }

    /**
     * Constructor
     * @param context Context used for loading resources.
     */
    public ThumbsAction(Context context, int actionId, int solidIconResId, int highlightColor) {
        super(actionId);
        Drawable[] drawables = new Drawable[2];

        BitmapDrawable solidDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, solidIconResId);
        drawables[INDEX_ON] = solidDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(solidDrawable.getBitmap(), highlightColor));
        drawables[INDEX_OFF] = solidDrawable;
        setDrawables(drawables);

        String[] labels = new String[drawables.length];
        // Note, labels denote the action taken when clicked
        String simpleName = Helpers.getSimpleClassName(getClass().getSimpleName());
        labels[INDEX_OFF] = simpleName + " Off";
        labels[INDEX_ON] = simpleName + " On";
        setLabels(labels);

        setIndex(INDEX_OFF); // default state
    }

    @Override
    public void setIndex(int index) {
        super.setIndex(index);

        if (index == INDEX_ON && mBoundAction != null) {
            mBoundAction.setIndex(INDEX_OFF);
        }
    }

    public ThumbsAction getBoundAction() {
        return mBoundAction;
    }

    public void setBoundAction(ThumbsAction boundAction) {
        mBoundAction = boundAction;
    }
}
