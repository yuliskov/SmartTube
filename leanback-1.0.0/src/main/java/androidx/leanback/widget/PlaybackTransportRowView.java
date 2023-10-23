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

package androidx.leanback.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * View for PlaybackTransportRowPresenter that has a custom focusSearch.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class PlaybackTransportRowView extends LinearLayout {

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public interface OnUnhandledKeyListener {
        /**
         * Returns true if the key event should be consumed.
         */
        boolean onUnhandledKey(KeyEvent event);
    }

    private OnUnhandledKeyListener mOnUnhandledKeyListener;

    public PlaybackTransportRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PlaybackTransportRowView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    void setOnUnhandledKeyListener(OnUnhandledKeyListener listener) {
        mOnUnhandledKeyListener = listener;
    }

    OnUnhandledKeyListener getOnUnhandledKeyListener() {
        return mOnUnhandledKeyListener;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) {
            return true;
        }
        return mOnUnhandledKeyListener != null && mOnUnhandledKeyListener.onUnhandledKey(event);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        final View focused = findFocus();
        if (focused != null && focused.requestFocus(direction, previouslyFocusedRect)) {
            return true;
        }
        View progress = findViewById(R.id.playback_progress);
        if (progress != null && progress.isFocusable()) {
            if (progress.requestFocus(direction, previouslyFocusedRect)) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        // when focusSearch vertically, return the next immediate focusable child
        if (focused != null) {
            if (direction == View.FOCUS_UP) {
                int index = indexOfChild(getFocusedChild());
                for (index = index - 1; index >= 0; index--) {
                    View view = getChildAt(index);
                    if (view.hasFocusable()) {
                        return view;
                    }
                }
            } else if (direction == View.FOCUS_DOWN) {
                int index = indexOfChild(getFocusedChild());
                for (index = index + 1; index < getChildCount(); index++) {
                    View view = getChildAt(index);
                    if (view.hasFocusable()) {
                        return view;
                    }
                }
            } else if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                if (getFocusedChild() instanceof ViewGroup) {
                    return FocusFinder.getInstance().findNextFocus(
                            (ViewGroup) getFocusedChild(), focused, direction);
                }
            }
        }
        return super.focusSearch(focused, direction);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
