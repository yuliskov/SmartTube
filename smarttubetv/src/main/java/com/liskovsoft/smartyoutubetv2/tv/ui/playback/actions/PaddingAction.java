package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import androidx.leanback.widget.Action;

/**
 * An action for displaying smaller icon.
 */
public class PaddingAction extends Action {
    private int mPadding;

    public PaddingAction(long id) {
        super(id);
    }

    /**
     * Padding in px
     */
    public int getPadding() {
        return mPadding;
    }

    /**
     * Padding in px
     */
    public void setPadding(int padding) {
        mPadding = padding;
    }
}
