/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * This class allows a customized widget class to implement {@link TitleViewAdapter.Provider}
 * and expose {@link TitleViewAdapter} methods to containing fragment (e.g. BrowseFragment or
 * DetailsFragment).
 * The title view must have a search orb view ({@link #getSearchAffordanceView()} aligned to start
 * and can typically have a branding Drawable and or title text aligned to end.  The branding part
 * is fully open to customization: not necessary to be a drawable or text.
 */
public abstract class TitleViewAdapter {

    /**
     * Interface to be implemented by a customized widget class to implement
     * {@link TitleViewAdapter}.
     */
    public interface Provider {
        /**
         * Returns {@link TitleViewAdapter} to be implemented by the customized widget class.
         * @return {@link TitleViewAdapter} to be implemented by the customized widget class.
         */
        TitleViewAdapter getTitleViewAdapter();
    }

    public static final int BRANDING_VIEW_VISIBLE = 0x02;
    public static final int SEARCH_VIEW_VISIBLE = 0x04;
    public static final int FULL_VIEW_VISIBLE = BRANDING_VIEW_VISIBLE | SEARCH_VIEW_VISIBLE;

    /**
     * Sets the title text.
     * @param titleText The text to set as title.
     */
    public void setTitle(CharSequence titleText) {
    }

    /**
     * Returns the title text.
     * @return The title text.
     */
    public CharSequence getTitle() {
        return null;
    }

    /**
     * Sets the badge drawable.
     * If non-null, the drawable is displayed instead of the title text.
     * @param drawable The badge drawable to set on title view.
     */
    public void setBadgeDrawable(Drawable drawable) {
    }

    /**
     * Returns the badge drawable.
     * @return The badge drawable.
     */
    public Drawable getBadgeDrawable() {
        return null;
    }

    /**
     *  Returns the view for the search affordance.
     *  @return The view for search affordance.
     */
    public abstract View getSearchAffordanceView();

    /**
     * Sets a click listener for the search affordance view.
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
        View view = getSearchAffordanceView();
        if (view != null) {
            view.setOnClickListener(listener);
        }
    }

    /**
     * Sets the {@link androidx.leanback.widget.SearchOrbView.Colors} used to draw the
     * search affordance.
     *
     * @param colors Colors used to draw search affordance.
     */
    public void setSearchAffordanceColors(SearchOrbView.Colors colors) {
    }

    /**
     * Returns the {@link androidx.leanback.widget.SearchOrbView.Colors} used to draw the
     * search affordance.
     *
     * @return Colors used to draw search affordance.
     */
    public SearchOrbView.Colors getSearchAffordanceColors() {
        return null;
    }

    /**
     * Enables or disables any view animations.  This method is called to save CPU cycle for example
     * stop search view breathing animation when containing fragment is paused.
     * @param enable True to enable animation, false otherwise.
     */
    public void setAnimationEnabled(boolean enable) {
    }

    /**
     * Based on the flag, it updates the visibility of the individual components -
     * Branding views (badge drawable and/or title) and search affordance view.
     *
     * @param flags integer representing the visibility of TitleView components.
     * @see #BRANDING_VIEW_VISIBLE
     * @see #SEARCH_VIEW_VISIBLE
     * @see #FULL_VIEW_VISIBLE
     */
    public void updateComponentsVisibility(int flags) {
    }
}
