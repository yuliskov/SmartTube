/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.RestrictTo;
import androidx.leanback.widget.GuidedActionAdapter.EditListener;

import java.util.ArrayList;

/**
 * Internal implementation manages a group of GuidedActionAdapters, control the next action after
 * editing finished, maintain the Ime open/close status.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class GuidedActionAdapterGroup {

    private static final String TAG_EDIT = "EditableAction";
    private static final boolean DEBUG_EDIT = false;

    ArrayList<Pair<GuidedActionAdapter, GuidedActionAdapter>> mAdapters =
            new ArrayList<Pair<GuidedActionAdapter, GuidedActionAdapter>>();
    private boolean mImeOpened;
    private EditListener mEditListener;

    public void addAdpter(GuidedActionAdapter adapter1, GuidedActionAdapter adapter2) {
        mAdapters.add(new Pair<GuidedActionAdapter, GuidedActionAdapter>(adapter1, adapter2));
        if (adapter1 != null) {
            adapter1.mGroup = this;
        }
        if (adapter2 != null) {
            adapter2.mGroup = this;
        }
    }

    public GuidedActionAdapter getNextAdapter(GuidedActionAdapter adapter) {
        for (int i = 0; i < mAdapters.size(); i++) {
            Pair<GuidedActionAdapter, GuidedActionAdapter> pair = mAdapters.get(i);
            if (pair.first == adapter) {
                return pair.second;
            }
        }
        return null;
    }

    public void setEditListener(EditListener listener) {
        mEditListener = listener;
    }

    boolean focusToNextAction(GuidedActionAdapter adapter, GuidedAction action, long nextActionId) {
        // for ACTION_ID_NEXT, we first find out the matching index in Actions list.
        int index = 0;
        if (nextActionId == GuidedAction.ACTION_ID_NEXT) {
            index = adapter.indexOf(action);
            if (index < 0) {
                return false;
            }
            // start from next, if reach end, will go next Adapter below
            index++;
        }

        do {
            int size = adapter.getCount();
            if (nextActionId == GuidedAction.ACTION_ID_NEXT) {
                while (index < size && !adapter.getItem(index).isFocusable()) {
                    index++;
                }
            } else {
                while (index < size && adapter.getItem(index).getId() != nextActionId) {
                    index++;
                }
            }
            if (index < size) {
                GuidedActionsStylist.ViewHolder vh =
                        (GuidedActionsStylist.ViewHolder) adapter.getGuidedActionsStylist()
                                .getActionsGridView().findViewHolderForPosition(index);
                if (vh != null) {
                    if (vh.getAction().hasTextEditable()) {
                        if (DEBUG_EDIT) Log.v(TAG_EDIT, "openIme of next Action");
                        // open Ime on next action.
                        openIme(adapter, vh);
                    } else {
                        if (DEBUG_EDIT) Log.v(TAG_EDIT, "closeIme and focus to next Action");
                        // close IME and focus to next (not editable) action
                        closeIme(vh.itemView);
                        vh.itemView.requestFocus();
                    }
                    return true;
                }
                return false;
            }
            // search from index 0 of next Adapter
            adapter = getNextAdapter(adapter);
            if (adapter == null) {
                break;
            }
            index = 0;
        } while (true);
        return false;
    }

    public void openIme(GuidedActionAdapter adapter, GuidedActionsStylist.ViewHolder avh) {
        adapter.getGuidedActionsStylist().setEditingMode(avh, true);
        View v = avh.getEditingView();
        if (v == null || !avh.isInEditingText()) {
            return;
        }
        InputMethodManager mgr = (InputMethodManager)
                v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        // Make the TextView focusable during editing, avoid the TextView gets accessibility focus
        // before editing started. see also GuidedActionEditText where setFocusable(false).
        v.setFocusable(true);
        v.requestFocus();
        mgr.showSoftInput(v, 0);
        if (!mImeOpened) {
            mImeOpened = true;
            mEditListener.onImeOpen();
        }
    }

    public void closeIme(View v) {
        if (mImeOpened) {
            mImeOpened = false;
            InputMethodManager mgr = (InputMethodManager)
                    v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            mgr.hideSoftInputFromWindow(v.getWindowToken(), 0);
            mEditListener.onImeClose();
        }
    }

    public void fillAndStay(GuidedActionAdapter adapter, TextView v) {
        GuidedActionsStylist.ViewHolder avh = adapter.findSubChildViewHolder(v);
        updateTextIntoAction(avh, v);
        mEditListener.onGuidedActionEditCanceled(avh.getAction());
        adapter.getGuidedActionsStylist().setEditingMode(avh, false);
        closeIme(v);
        avh.itemView.requestFocus();
    }

    public void fillAndGoNext(GuidedActionAdapter adapter, TextView v) {
        boolean handled = false;
        GuidedActionsStylist.ViewHolder avh = adapter.findSubChildViewHolder(v);
        updateTextIntoAction(avh, v);
        adapter.performOnActionClick(avh);
        long nextActionId = mEditListener.onGuidedActionEditedAndProceed(avh.getAction());
        adapter.getGuidedActionsStylist().setEditingMode(avh, false);
        if (nextActionId != GuidedAction.ACTION_ID_CURRENT
                && nextActionId != avh.getAction().getId()) {
            handled = focusToNextAction(adapter, avh.getAction(), nextActionId);
        }
        if (!handled) {
            if (DEBUG_EDIT) Log.v(TAG_EDIT, "closeIme no next action");
            handled = true;
            closeIme(v);
            avh.itemView.requestFocus();
        }
    }

    private void updateTextIntoAction(GuidedActionsStylist.ViewHolder avh, TextView v) {
        GuidedAction action = avh.getAction();
        if (v == avh.getDescriptionView()) {
            if (action.getEditDescription() != null) {
                action.setEditDescription(v.getText());
            } else {
                action.setDescription(v.getText());
            }
        } else if (v == avh.getTitleView()) {
            if (action.getEditTitle() != null) {
                action.setEditTitle(v.getText());
            } else {
                action.setTitle(v.getText());
            }
        }
    }

}
