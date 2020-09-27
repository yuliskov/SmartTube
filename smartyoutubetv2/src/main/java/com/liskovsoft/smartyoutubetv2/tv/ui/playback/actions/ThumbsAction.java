package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;

public abstract class ThumbsAction extends MultiAction {
    /**
     * Action index for the solid thumb icon.
     */
    public static final int INDEX_ON = 0;

    /**
     * Action index for the outline thumb icon.
     */
    public static final int INDEX_OFF = 1;

    private ThumbsAction mBoundAction;

    public ThumbsAction(int id, Context context, int solidIconResId, int outlineIconResId) {
        this(id, context, solidIconResId, outlineIconResId, ActionHelpers.getIconHighlightColor(context));
    }

    /**
     * Constructor
     * @param context Context used for loading resources.
     */
    public ThumbsAction(int id, Context context, int solidIconResId, int outlineIconResId, int highlightColor) {
        super(id);
        Drawable[] drawables = new Drawable[2];

        BitmapDrawable solidDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, solidIconResId);
        BitmapDrawable outlineDrawable = (BitmapDrawable) ContextCompat.getDrawable(context, outlineIconResId);
        drawables[INDEX_ON] = solidDrawable == null ? null
                : new BitmapDrawable(context.getResources(),
                ActionHelpers.createBitmap(solidDrawable.getBitmap(), highlightColor));
        drawables[INDEX_OFF] = solidDrawable;
        setDrawables(drawables);

        //String[] labels = new String[drawables.length];
        //// Note, labels denote the action taken when clicked
        //labels[INDEX_OUTLINE] = context.getString(R.string.action_thumbs_off);
        //labels[INDEX_SOLID] = context.getString(R.string.action_thumbs_on);
        //setLabels(labels);
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
