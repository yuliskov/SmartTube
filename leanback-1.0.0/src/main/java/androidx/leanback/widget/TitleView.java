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

import static androidx.leanback.widget.TitleViewAdapter.BRANDING_VIEW_VISIBLE;
import static androidx.leanback.widget.TitleViewAdapter.FULL_VIEW_VISIBLE;
import static androidx.leanback.widget.TitleViewAdapter.SEARCH_VIEW_VISIBLE;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.leanback.R;

/**
 * Title view for a leanback fragment.
 */
public class TitleView extends FrameLayout implements TitleViewAdapter.Provider {

    private ImageView mBadgeView;
    private TextView mTextView;
    private SearchOrbView mSearchOrbView;
    private int flags = FULL_VIEW_VISIBLE;
    private boolean mHasSearchListener = false;

    private final TitleViewAdapter mTitleViewAdapter = new TitleViewAdapter() {
        @Override
        public View getSearchAffordanceView() {
            return TitleView.this.getSearchAffordanceView();
        }

        @Override
        public void setOnSearchClickedListener(View.OnClickListener listener) {
            TitleView.this.setOnSearchClickedListener(listener);
        }

        @Override
        public void setAnimationEnabled(boolean enable) {
            TitleView.this.enableAnimation(enable);
        }

        @Override
        public Drawable getBadgeDrawable() {
            return TitleView.this.getBadgeDrawable();
        }

        @Override
        public SearchOrbView.Colors getSearchAffordanceColors() {
            return TitleView.this.getSearchAffordanceColors();
        }

        @Override
        public CharSequence getTitle() {
            return TitleView.this.getTitle();
        }

        @Override
        public void setBadgeDrawable(Drawable drawable) {
            TitleView.this.setBadgeDrawable(drawable);
        }

        @Override
        public void setSearchAffordanceColors(SearchOrbView.Colors colors) {
            TitleView.this.setSearchAffordanceColors(colors);
        }

        @Override
        public void setTitle(CharSequence titleText) {
            TitleView.this.setTitle(titleText);
        }

        @Override
        public void updateComponentsVisibility(int flags) {
            TitleView.this.updateComponentsVisibility(flags);
        }
    };

    public TitleView(Context context) {
        this(context, null);
    }

    public TitleView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.browseTitleViewStyle);
    }

    public TitleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = LayoutInflater.from(context);
        View rootView = inflater.inflate(R.layout.lb_title_view, this);

        mBadgeView = (ImageView) rootView.findViewById(R.id.title_badge);
        mTextView = (TextView) rootView.findViewById(R.id.title_text);
        mSearchOrbView = (SearchOrbView) rootView.findViewById(R.id.title_orb);

        setClipToPadding(false);
        setClipChildren(false);
    }

    /**
     * Sets the title text.
     */
    public void setTitle(CharSequence titleText) {
        mTextView.setText(titleText);
        updateBadgeVisibility();
    }

    /**
     * Returns the title text.
     */
    public CharSequence getTitle() {
        return mTextView.getText();
    }

    /**
     * Sets the badge drawable.
     * If non-null, the drawable is displayed instead of the title text.
     */
    public void setBadgeDrawable(Drawable drawable) {
        mBadgeView.setImageDrawable(drawable);
        updateBadgeVisibility();
    }

    /**
     * Returns the badge drawable.
     */
    public Drawable getBadgeDrawable() {
        return mBadgeView.getDrawable();
    }

    /**
     * Sets the listener to be called when the search affordance is clicked.
     */
    public void setOnSearchClickedListener(View.OnClickListener listener) {
        mHasSearchListener = listener != null;
        mSearchOrbView.setOnOrbClickedListener(listener);
        updateSearchOrbViewVisiblity();
    }

    /**
     *  Returns the view for the search affordance.
     */
    public View getSearchAffordanceView() {
        return mSearchOrbView;
    }

    /**
     * Sets the {@link SearchOrbView.Colors} used to draw the search affordance.
     */
    public void setSearchAffordanceColors(SearchOrbView.Colors colors) {
        mSearchOrbView.setOrbColors(colors);
    }

    /**
     * Returns the {@link SearchOrbView.Colors} used to draw the search affordance.
     */
    public SearchOrbView.Colors getSearchAffordanceColors() {
        return mSearchOrbView.getOrbColors();
    }

    /**
     * Enables or disables any view animations.
     */
    public void enableAnimation(boolean enable) {
        mSearchOrbView.enableOrbColorAnimation(enable && mSearchOrbView.hasFocus());
    }

    /**
     * Based on the flag, it updates the visibility of the individual components -
     * BadgeView, TextView and SearchView.
     *
     * @param flags integer representing the visibility of TitleView components.
     * @see TitleViewAdapter#SEARCH_VIEW_VISIBLE
     * @see TitleViewAdapter#BRANDING_VIEW_VISIBLE
     * @see TitleViewAdapter#FULL_VIEW_VISIBLE
     */
    public void updateComponentsVisibility(int flags) {
        this.flags = flags;

        if ((flags & BRANDING_VIEW_VISIBLE) == BRANDING_VIEW_VISIBLE) {
            updateBadgeVisibility();
        } else {
            mBadgeView.setVisibility(View.GONE);
            mTextView.setVisibility(View.GONE);
        }
        updateSearchOrbViewVisiblity();
    }

    private void updateSearchOrbViewVisiblity() {
        int visibility = mHasSearchListener && (flags & SEARCH_VIEW_VISIBLE) == SEARCH_VIEW_VISIBLE
                ? View.VISIBLE : View.INVISIBLE;
        mSearchOrbView.setVisibility(visibility);
    }

    private void updateBadgeVisibility() {
        Drawable drawable = mBadgeView.getDrawable();
        if (drawable != null) {
            mBadgeView.setVisibility(View.VISIBLE);
            mTextView.setVisibility(View.GONE);
        } else {
            mBadgeView.setVisibility(View.GONE);
            mTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public TitleViewAdapter getTitleViewAdapter() {
        return mTitleViewAdapter;
    }
}
