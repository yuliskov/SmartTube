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

import android.view.View;
import android.view.ViewGroup;

/**
 * An abstract helper class that switches a view in its parent view using a
 * {@link PresenterSelector}.  A subclass should implement {@link #insertView(View)} to define
 * how to add the view in parent, and may optionally override {@link #onViewSelected(View)}.
 */
public abstract class PresenterSwitcher {

    private ViewGroup mParent;
    private PresenterSelector mPresenterSelector;
    private Presenter mCurrentPresenter;
    private Presenter.ViewHolder mCurrentViewHolder;

    /**
     * Initializes the switcher with a parent view to insert view into and a
     * {@link PresenterSelector} for choosing a {@link Presenter} for a given object.
     * This will destroy any existing views.
     */
    public void init(ViewGroup parent, PresenterSelector presenterSelector) {
        clear();
        mParent = parent;
        mPresenterSelector = presenterSelector;
    }

    /**
     * Selects a view based on the given object and shows that view.
     */
    public void select(Object object) {
        switchView(object);
        showView(true);
    }

    /**
     * Hides the view.
     */
    public void unselect() {
        showView(false);
    }

    /**
     * Returns the parent.
     */
    public final ViewGroup getParentViewGroup() {
        return mParent;
    }

    private void showView(boolean show) {
        if (mCurrentViewHolder != null) {
            showView(mCurrentViewHolder.view, show);
        }
    }

    private void switchView(Object object) {
        Presenter presenter = mPresenterSelector.getPresenter(object);
        if (presenter != mCurrentPresenter) {
            showView(false);
            clear();
            mCurrentPresenter = presenter;
            if (mCurrentPresenter == null) {
                return;
            }
            mCurrentViewHolder = mCurrentPresenter.onCreateViewHolder(mParent);
            insertView(mCurrentViewHolder.view);
        } else {
            if (mCurrentPresenter == null) {
                return;
            }
            mCurrentPresenter.onUnbindViewHolder(mCurrentViewHolder);
        }
        mCurrentPresenter.onBindViewHolder(mCurrentViewHolder, object);
        onViewSelected(mCurrentViewHolder.view);
    }

    protected abstract void insertView(View view);

    /**
     * Called when a view is bound to the object of {@link #select(Object)}.
     */
    protected void onViewSelected(View view) {
    }

    protected void showView(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Destroys created views.
     */
    public void clear() {
        if (mCurrentPresenter != null) {
            mCurrentPresenter.onUnbindViewHolder(mCurrentViewHolder);
            mParent.removeView(mCurrentViewHolder.view);
            mCurrentViewHolder = null;
            mCurrentPresenter = null;
        }
    }

}
