package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.widget;

import androidx.leanback.widget.Action;

/**
 * Interface for receiving notification when an {@link Action} is long clicked.
 */
public interface OnActionLongClickedListener {
    /**
     * Callback fired when the host fragment receives an action.
     */
    boolean onActionLongClicked(Action action);
}
