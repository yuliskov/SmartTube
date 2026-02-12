/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;
import androidx.leanback.R;

/**
 * RowHeaderView is a header text view.
 */
public final class RowHeaderView extends TextView {

    public RowHeaderView(Context context) {
        this(context, null);
    }

    public RowHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.rowHeaderStyle);
    }

    public RowHeaderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * See
     * {@link TextViewCompat#setCustomSelectionActionModeCallback(TextView, ActionMode.Callback)}
     */
    @Override
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        super.setCustomSelectionActionModeCallback(TextViewCompat
                .wrapCustomSelectionActionModeCallback(this, actionModeCallback));
    }

}
