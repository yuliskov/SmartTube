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

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.RestrictTo;
import com.liskovsoft.smartyoutubetv2.tv.R;
//import androidx.appcompat.R;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

/**
 * A popup window displaying a text message aligned to a specified view.
 *
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
class TooltipPopup {
    private static final String TAG = "TooltipPopup";

    private final Context mContext;

    private final View mContentView;
    private final TextView mMessageView;

    private final WindowManager.LayoutParams mLayoutParams = new WindowManager.LayoutParams();
    private final Rect mTmpDisplayFrame = new Rect();
    private final int[] mTmpAnchorPos = new int[2];
    private final int[] mTmpAppPos = new int[2];

    TooltipPopup(Context context) {
        mContext = context;

        mContentView = LayoutInflater.from(mContext).inflate(R.layout.abc_tooltip, null);
        mMessageView = (TextView) mContentView.findViewById(R.id.message);

        mLayoutParams.setTitle(getClass().getSimpleName());
        mLayoutParams.packageName = mContext.getPackageName();
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL;
        mLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mLayoutParams.format = PixelFormat.TRANSLUCENT;
        mLayoutParams.windowAnimations = R.style.Animation_AppCompat_Tooltip;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    }

    void show(View anchorView, int anchorX, int anchorY, boolean fromTouch,
            CharSequence tooltipText) {
        if (isShowing()) {
            hide();
        }

        mMessageView.setText(tooltipText);

        computePosition(anchorView, anchorX, anchorY, fromTouch, mLayoutParams);

        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.addView(mContentView, mLayoutParams);
    }

    void hide() {
        if (!isShowing()) {
            return;
        }

        WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        wm.removeView(mContentView);
    }

    boolean isShowing() {
        return mContentView.getParent() != null;
    }

    private void computePosition(View anchorView, int anchorX, int anchorY, boolean fromTouch,
            WindowManager.LayoutParams outParams) {
        outParams.token = anchorView.getApplicationWindowToken();
        final int tooltipPreciseAnchorThreshold = mContext.getResources().getDimensionPixelOffset(
                R.dimen.tooltip_precise_anchor_threshold);

        final int offsetX;
        if (anchorView.getWidth() >= tooltipPreciseAnchorThreshold) {
            // Wide view. Align the tooltip horizontally to the precise X position.
            offsetX = anchorX;
        } else {
            // Otherwise anchor the tooltip to the view center.
            offsetX = anchorView.getWidth() / 2;  // Center on the view horizontally.
        }

        final int offsetBelow;
        final int offsetAbove;
        if (anchorView.getHeight() >= tooltipPreciseAnchorThreshold) {
            // Tall view. Align the tooltip vertically to the precise Y position.
            final int offsetExtra = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.tooltip_precise_anchor_extra_offset);
            offsetBelow = anchorY + offsetExtra;
            offsetAbove = anchorY - offsetExtra;
        } else {
            // Otherwise anchor the tooltip to the view center.
            offsetBelow = anchorView.getHeight();  // Place below the view in most cases.
            offsetAbove = 0;  // Place above the view if the tooltip does not fit below.
        }

        outParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;

        final int tooltipOffset = mContext.getResources().getDimensionPixelOffset(
                fromTouch ? R.dimen.tooltip_y_offset_touch : R.dimen.tooltip_y_offset_non_touch);

        final View appView = getAppRootView(anchorView);
        if (appView == null) {
            Log.e(TAG, "Cannot find app view");
            return;
        }
        appView.getWindowVisibleDisplayFrame(mTmpDisplayFrame);
        if (mTmpDisplayFrame.left < 0 && mTmpDisplayFrame.top < 0) {
            // No meaningful display frame, the anchor view is probably in a subpanel
            // (such as a popup window). Use the screen frame as a reasonable approximation.
            final Resources res = mContext.getResources();
            final int statusBarHeight;
            int resourceId = res.getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId != 0) {
                statusBarHeight = res.getDimensionPixelSize(resourceId);
            } else {
                statusBarHeight = 0;
            }
            final DisplayMetrics metrics = res.getDisplayMetrics();
            mTmpDisplayFrame.set(0, statusBarHeight, metrics.widthPixels, metrics.heightPixels);
        }
        appView.getLocationOnScreen(mTmpAppPos);

        anchorView.getLocationOnScreen(mTmpAnchorPos);
        mTmpAnchorPos[0] -= mTmpAppPos[0];
        mTmpAnchorPos[1] -= mTmpAppPos[1];
        // mTmpAnchorPos is now relative to the main app window.

        outParams.x = mTmpAnchorPos[0] + offsetX - appView.getWidth() / 2;

        final int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        mContentView.measure(spec, spec);
        final int tooltipHeight = mContentView.getMeasuredHeight();

        final int yAbove = mTmpAnchorPos[1] + offsetAbove - tooltipOffset - tooltipHeight;
        final int yBelow = mTmpAnchorPos[1] + offsetBelow + tooltipOffset;
        if (fromTouch) {
            if (yAbove >= 0) {
                outParams.y = yAbove;
            } else {
                outParams.y = yBelow;
            }
        } else {
            if (yBelow + tooltipHeight <= mTmpDisplayFrame.height()) {
                outParams.y = yBelow;
            } else {
                outParams.y = yAbove;
            }
        }
    }

    private static View getAppRootView(View anchorView) {
        View rootView = anchorView.getRootView();
        ViewGroup.LayoutParams lp = rootView.getLayoutParams();
        if (lp instanceof WindowManager.LayoutParams
                && (((WindowManager.LayoutParams) lp).type
                    == WindowManager.LayoutParams.TYPE_APPLICATION)) {
            // This covers regular app windows and Dialog windows.
            return rootView;
        }
        // For non-application window types (such as popup windows) try to find the main app window
        // through the context.
        Context context = anchorView.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return ((Activity) context).getWindow().getDecorView();
            } else {
                context = ((ContextWrapper) context).getBaseContext();
            }
        }
        // Main app window not found, fall back to the anchor's root view. There is no guarantee
        // that the tooltip position will be computed correctly.
        return rootView;
    }
}
