/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityManager;
import androidx.annotation.RestrictTo;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewConfigurationCompat;

import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * Event handler used used to emulate the behavior of {@link View#setTooltipText(CharSequence)}
 * prior to API level 26.
 *
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class TooltipCompatHandler implements View.OnLongClickListener, View.OnHoverListener,
        View.OnAttachStateChangeListener, View.OnFocusChangeListener {
    private static final String TAG = "TooltipCompatHandler";

    private static final long LONG_CLICK_HIDE_TIMEOUT_MS = 2500;
    private static final long HOVER_HIDE_TIMEOUT_MS = 15000;
    private static final long HOVER_HIDE_TIMEOUT_SHORT_MS = 3000;

    private final View mAnchor;
    private final CharSequence mTooltipText;
    private final int mHoverSlop;

    private final Runnable mShowRunnable = new Runnable() {
        @Override
        public void run() {
            show(false /* not from touch*/);
        }
    };
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private int mAnchorX;
    private int mAnchorY;

    private TooltipPopup mPopup;
    private boolean mFromTouch;

    // The handler currently scheduled to show a tooltip, triggered by a hover
    // (there can be only one).
    private static TooltipCompatHandler sPendingHandler;

    // The handler currently showing a tooltip (there can be only one).
    private static TooltipCompatHandler sActiveHandler;

    /**
     * Set the tooltip text for the view.
     *
     * @param view        view to set the tooltip on
     * @param tooltipText the tooltip text
     */
    public static void setTooltipText(View view, CharSequence tooltipText) {
        // The code below is not attempting to update the tooltip text
        // for a pending or currently active tooltip, because it may lead
        // to updating the wrong tooltip in in some rare cases (e.g. when
        // action menu item views are recycled). Instead, the tooltip is
        // canceled/hidden. This might still be the wrong tooltip,
        // but hiding a wrong tooltip is less disruptive UX.
        if (view == null) {
            return;
        }

        if (sPendingHandler != null && sPendingHandler.mAnchor == view) {
            setPendingHandler(null);
        }

        boolean sameAnchor = sActiveHandler != null && sActiveHandler.mAnchor == view;

        if (sameAnchor) {
            sActiveHandler.hide();
        }

        // MODIFIED: listener already added in ControlBarPresenter
        //view.setOnLongClickListener(null);
        //view.setLongClickable(false);

        // Invisible controls bar can react on mouse pointer
        //view.setOnHoverListener(null);

        view.setOnFocusChangeListener(null);

        if (!TextUtils.isEmpty(tooltipText)) {
            TooltipCompatHandler handler = new TooltipCompatHandler(view, tooltipText);

            // Circle through the button states
            if (sameAnchor) {
                handler.show(false);
            }
        }
    }

    private TooltipCompatHandler(View anchor, CharSequence tooltipText) {
        mAnchor = anchor;
        mTooltipText = tooltipText;
        mHoverSlop = ViewConfigurationCompat.getScaledHoverSlop(
                ViewConfiguration.get(mAnchor.getContext()));
        clearAnchorPos();

        // MODIFIED: listener already added in ControlBarPresenter
        //mAnchor.setOnLongClickListener(this);

        // Invisible controls bar can react on mouse pointer
        //mAnchor.setOnHoverListener(this);

        mAnchor.setOnFocusChangeListener(this);
    }

    @Override
    public boolean onLongClick(View v) {
        mAnchorX = v.getWidth() / 2;
        mAnchorY = v.getHeight() / 2;
        show(true /* from touch */);
        return true;
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        if (mPopup != null && mFromTouch) {
            return false;
        }
        AccessibilityManager manager = (AccessibilityManager)
                mAnchor.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager.isEnabled() && manager.isTouchExplorationEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_HOVER_MOVE:
                if (mAnchor.isEnabled() && mPopup == null && updateAnchorPos(event)) {
                    setPendingHandler(this);
                }
                break;
            case MotionEvent.ACTION_HOVER_EXIT:
                clearAnchorPos();
                hide();
                break;
        }

        return false;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            // Wait till probable animation complete (button is moving)
            setPendingHandler(this);
        } else {
            hide();
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        // no-op.
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        hide();
    }

    void show(boolean fromTouch) {
        if (!ViewCompat.isAttachedToWindow(mAnchor)) {
            return;
        }
        setPendingHandler(null);
        if (sActiveHandler != null) {
            sActiveHandler.hide();
        }
        sActiveHandler = this;

        mFromTouch = fromTouch;
        mPopup = new TooltipPopup(mAnchor.getContext());
        mPopup.show(mAnchor, mAnchorX, mAnchorY, mFromTouch, mTooltipText);
        // Only listen for attach state change while the popup is being shown.
        mAnchor.addOnAttachStateChangeListener(this);

        final long timeout;
        if (mFromTouch) {
            timeout = LONG_CLICK_HIDE_TIMEOUT_MS;
        } else if ((ViewCompat.getWindowSystemUiVisibility(mAnchor)
                & SYSTEM_UI_FLAG_LOW_PROFILE) == SYSTEM_UI_FLAG_LOW_PROFILE) {
            timeout = HOVER_HIDE_TIMEOUT_SHORT_MS - ViewConfiguration.getLongPressTimeout();
        } else {
            timeout = HOVER_HIDE_TIMEOUT_MS - ViewConfiguration.getLongPressTimeout();
        }
        mAnchor.removeCallbacks(mHideRunnable);
        mAnchor.postDelayed(mHideRunnable, timeout);
    }

    void hide() {
        if (sActiveHandler == this) {
            sActiveHandler = null;
            if (mPopup != null) {
                mPopup.hide();
                mPopup = null;
                clearAnchorPos();
                mAnchor.removeOnAttachStateChangeListener(this);
            } else {
                Log.e(TAG, "sActiveHandler.mPopup == null");
            }
        }
        if (sPendingHandler == this) {
            setPendingHandler(null);
        }
        mAnchor.removeCallbacks(mHideRunnable);
    }

    private static void setPendingHandler(TooltipCompatHandler handler) {
        if (sPendingHandler != null) {
            sPendingHandler.cancelPendingShow();
        }
        sPendingHandler = handler;
        if (sPendingHandler != null) {
            sPendingHandler.scheduleShow();
        }
    }

    private void scheduleShow() {
        mAnchor.postDelayed(mShowRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelPendingShow() {
        mAnchor.removeCallbacks(mShowRunnable);
    }

    /**
     * Update the anchor position if it significantly (that is by at least mHoverSlope)
     * different from the previously stored position. Ignoring insignificant changes
     * filters out the jitter which is typical for such input sources as stylus.
     *
     * @return True if the position has been updated.
     */
    private boolean updateAnchorPos(MotionEvent event) {
        final int newAnchorX = (int) event.getX();
        final int newAnchorY = (int) event.getY();
        if (Math.abs(newAnchorX - mAnchorX) <= mHoverSlop
                && Math.abs(newAnchorY - mAnchorY) <= mHoverSlop) {
            return false;
        }
        mAnchorX = newAnchorX;
        mAnchorY = newAnchorY;
        return true;
    }

    /**
     *  Clear the anchor position to ensure that the next change is considered significant.
     */
    private void clearAnchorPos() {
        mAnchorX = Integer.MAX_VALUE;
        mAnchorY = Integer.MAX_VALUE;
    }
}
