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
package androidx.leanback.app;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.R;
import androidx.leanback.widget.SearchOrbView;
import androidx.leanback.widget.TitleHelper;
import androidx.leanback.widget.TitleViewAdapter;

/**
 * Fragment class for managing search and branding using a view that implements
 * {@link TitleViewAdapter.Provider}.
 */
public class BrandedSupportFragment extends Fragment {

    // BUNDLE attribute for title is showing
    private static final String TITLE_SHOW = "titleShow";

    private boolean mShowingTitle = true;
    private CharSequence mTitle;
    private Drawable mBadgeDrawable;
    private View mTitleView;
    private TitleViewAdapter mTitleViewAdapter;
    private SearchOrbView.Colors mSearchAffordanceColors;
    private boolean mSearchAffordanceColorSet;
    private View.OnClickListener mExternalOnSearchClickedListener;
    private TitleHelper mTitleHelper;

    /**
     * Called by {@link #installTitleView(LayoutInflater, ViewGroup, Bundle)} to inflate
     * title view.  Default implementation uses layout file lb_browse_title.
     * Subclass may override and use its own layout, the layout must have a descendant with id
     * browse_title_group that implements {@link TitleViewAdapter.Provider}. Subclass may return
     * null if no title is needed.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate
     *                           any views in the fragment,
     * @param parent             Parent of title view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     *                           from a previous saved state as given here.
     * @return Title view which must have a descendant with id browse_title_group that implements
     *         {@link TitleViewAdapter.Provider}, or null for no title view.
     */
    public View onInflateTitleView(LayoutInflater inflater, ViewGroup parent,
                                Bundle savedInstanceState) {
        TypedValue typedValue = new TypedValue();
        boolean found = parent.getContext().getTheme().resolveAttribute(
                R.attr.browseTitleViewLayout, typedValue, true);
        return inflater.inflate(found ? typedValue.resourceId : R.layout.lb_browse_title,
                parent, false);
    }

    /**
     * Inflate title view and add to parent.  This method should be called in
     * {@link Fragment#onCreateView(LayoutInflater, ViewGroup, Bundle)}.
     * @param inflater The LayoutInflater object that can be used to inflate
     * any views in the fragment,
     * @param parent Parent of title view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    public void installTitleView(LayoutInflater inflater, ViewGroup parent,
                            Bundle savedInstanceState) {
        View titleLayoutRoot = onInflateTitleView(inflater, parent, savedInstanceState);
        if (titleLayoutRoot != null) {
            parent.addView(titleLayoutRoot);
            setTitleView(titleLayoutRoot.findViewById(R.id.browse_title_group));
        } else {
            setTitleView(null);
        }
    }

    /**
     * Sets the view that implemented {@link TitleViewAdapter}.
     * @param titleView The view that implemented {@link TitleViewAdapter.Provider}.
     */
    public void setTitleView(View titleView) {
        mTitleView = titleView;
        if (mTitleView == null) {
            mTitleViewAdapter = null;
            mTitleHelper = null;
        } else {
            mTitleViewAdapter = ((TitleViewAdapter.Provider) mTitleView).getTitleViewAdapter();
            mTitleViewAdapter.setTitle(mTitle);
            mTitleViewAdapter.setBadgeDrawable(mBadgeDrawable);
            if (mSearchAffordanceColorSet) {
                mTitleViewAdapter.setSearchAffordanceColors(mSearchAffordanceColors);
            }
            if (mExternalOnSearchClickedListener != null) {
                setOnSearchClickedListener(mExternalOnSearchClickedListener);
            }
            if (getView() instanceof ViewGroup) {
                mTitleHelper = new TitleHelper((ViewGroup) getView(), mTitleView);
            }
        }
    }

    /**
     * Returns the view that implements {@link TitleViewAdapter.Provider}.
     * @return The view that implements {@link TitleViewAdapter.Provider}.
     */
    public View getTitleView() {
        return mTitleView;
    }

    /**
     * Returns the {@link TitleViewAdapter} implemented by title view.
     * @return The {@link TitleViewAdapter} implemented by title view.
     */
    public TitleViewAdapter getTitleViewAdapter() {
        return mTitleViewAdapter;
    }

    /**
     * Returns the {@link TitleHelper}.
     */
    TitleHelper getTitleHelper() {
        return mTitleHelper;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(TITLE_SHOW, mShowingTitle);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            mShowingTitle = savedInstanceState.getBoolean(TITLE_SHOW);
        }
        if (mTitleView != null && view instanceof ViewGroup) {
            mTitleHelper = new TitleHelper((ViewGroup) view, mTitleView);
            mTitleHelper.showTitle(mShowingTitle);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mTitleHelper = null;
    }

    /**
     * Shows or hides the title view.
     * @param show True to show title view, false to hide title view.
     */
    public void showTitle(boolean show) {
        // TODO: handle interruptions?
        if (show == mShowingTitle) {
            return;
        }
        mShowingTitle = show;
        if (mTitleHelper != null) {
            mTitleHelper.showTitle(show);
        }
    }

    /**
     * Changes title view's components visibility and shows title.
     * @param flags Flags representing the visibility of components inside title view.
     * @see TitleViewAdapter#SEARCH_VIEW_VISIBLE
     * @see TitleViewAdapter#BRANDING_VIEW_VISIBLE
     * @see TitleViewAdapter#FULL_VIEW_VISIBLE
     * @see TitleViewAdapter#updateComponentsVisibility(int)
     */
    public void showTitle(int flags) {
        if (mTitleViewAdapter != null) {
            mTitleViewAdapter.updateComponentsVisibility(flags);
        }
        showTitle(true);
    }

    /**
     * Sets the drawable displayed in the fragment title.
     *
     * @param drawable The Drawable to display in the fragment title.
     */
    public void setBadgeDrawable(Drawable drawable) {
        if (mBadgeDrawable != drawable) {
            mBadgeDrawable = drawable;
            if (mTitleViewAdapter != null) {
                mTitleViewAdapter.setBadgeDrawable(drawable);
            }
        }
    }

    /**
     * Returns the badge drawable used in the fragment title.
     * @return The badge drawable used in the fragment title.
     */
    public Drawable getBadgeDrawable() {
        return mBadgeDrawable;
    }

    /**
     * Sets title text for the fragment.
     *
     * @param title The title text of the fragment.
     */
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (mTitleViewAdapter != null) {
            mTitleViewAdapter.setTitle(title);
        }
    }

    /**
     * Returns the title text for the fragment.
     * @return Title text for the fragment.
     */
    public CharSequence getTitle() {
        return mTitle;
    }

    /**
     * Sets a click listener for the search affordance.
     *
     * <p>The presence of a listener will change the visibility of the search
     * affordance in the fragment title. When set to non-null, the title will
     * contain an element that a user may click to begin a search.
     *
     * <p>The listener's {@link View.OnClickListener#onClick onClick} method
     * will be invoked when the user clicks on the search element.
     *
     * @param listener The listener to call when the search element is clicked.
     */
    public void setOnSearchClickedListener(View.OnClickListener listener) {
        mExternalOnSearchClickedListener = listener;
        if (mTitleViewAdapter != null) {
            mTitleViewAdapter.setOnSearchClickedListener(listener);
        }
    }

    /**
     * Sets the {@link androidx.leanback.widget.SearchOrbView.Colors} used to draw the
     * search affordance.
     *
     * @param colors Colors used to draw search affordance.
     */
    public void setSearchAffordanceColors(SearchOrbView.Colors colors) {
        mSearchAffordanceColors = colors;
        mSearchAffordanceColorSet = true;
        if (mTitleViewAdapter != null) {
            mTitleViewAdapter.setSearchAffordanceColors(mSearchAffordanceColors);
        }
    }

    /**
     * Returns the {@link androidx.leanback.widget.SearchOrbView.Colors}
     * used to draw the search affordance.
     */
    public SearchOrbView.Colors getSearchAffordanceColors() {
        if (mSearchAffordanceColorSet) {
            return mSearchAffordanceColors;
        }
        if (mTitleViewAdapter == null) {
            throw new IllegalStateException("Fragment views not yet created");
        }
        return mTitleViewAdapter.getSearchAffordanceColors();
    }

    /**
     * Sets the color used to draw the search affordance.
     * A default brighter color will be set by the framework.
     *
     * @param color The color to use for the search affordance.
     */
    public void setSearchAffordanceColor(int color) {
        setSearchAffordanceColors(new SearchOrbView.Colors(color));
    }

    /**
     * Returns the color used to draw the search affordance.
     */
    public int getSearchAffordanceColor() {
        return getSearchAffordanceColors().color;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mTitleViewAdapter != null) {
            showTitle(mShowingTitle);
            mTitleViewAdapter.setAnimationEnabled(true);
        }
    }

    @Override
    public void onPause() {
        if (mTitleViewAdapter != null) {
            mTitleViewAdapter.setAnimationEnabled(false);
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mTitleViewAdapter != null) {
            mTitleViewAdapter.setAnimationEnabled(true);
        }
    }

    /**
     * Returns true/false to indicate the visibility of TitleView.
     *
     * @return boolean to indicate whether or not it's showing the title.
     */
    public final boolean isShowingTitle() {
        return mShowingTitle;
    }

}
