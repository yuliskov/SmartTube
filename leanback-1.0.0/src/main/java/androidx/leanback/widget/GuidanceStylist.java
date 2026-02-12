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

import android.animation.Animator;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.R;

import java.util.List;

/**
 * GuidanceStylist is used within a {@link androidx.leanback.app.GuidedStepFragment}
 * to display contextual information for the decision(s) required at that step.
 * <p>
 * Many aspects of the base GuidanceStylist can be customized through theming; see the theme
 * attributes below. Note that these attributes are not set on individual elements in layout
 * XML, but instead would be set in a custom theme. See
 * <a href="http://developer.android.com/guide/topics/ui/themes.html">Styles and Themes</a>
 * for more information.
 * <p>
 * If these hooks are insufficient, this class may also be subclassed. Subclasses
 * may wish to override the {@link #onProvideLayoutId} method to change the layout file used to
 * display the guidance; more complex layouts may be supported by also providing a subclass of
 * {@link GuidanceStylist.Guidance} with extra fields.
 * <p>
 * Note: If an alternate layout is provided, the following view IDs should be used to refer to base
 * elements:
 * <ul>
 * <li>{@link androidx.leanback.R.id#guidance_title}</li>
 * <li>{@link androidx.leanback.R.id#guidance_description}</li>
 * <li>{@link androidx.leanback.R.id#guidance_breadcrumb}</li>
 * <li>{@link androidx.leanback.R.id#guidance_icon}</li>
 * </ul><p>
 * View IDs are allowed to be missing, in which case the corresponding views will be null.
 *
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedStepImeAppearingAnimation
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedStepImeDisappearingAnimation
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidanceContainerStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidanceTitleStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidanceDescriptionStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidanceBreadcrumbStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidanceIconStyle
 * @see androidx.leanback.app.GuidedStepFragment
 * @see GuidanceStylist.Guidance
 */
public class GuidanceStylist implements FragmentAnimationProvider {

    /**
     * A data class representing contextual information for a {@link
     * androidx.leanback.app.GuidedStepFragment}. Guidance consists of a short title,
     * a longer description, a breadcrumb to help with global navigation (often indicating where
     * the back button will lead), and an optional icon.  All this information is intended to
     * provide users with the appropriate context to make the decision(s) required by the current
     * step.
     * <p>
     * Clients may provide a subclass of this if they wish to remember auxiliary data for use in
     * a customized GuidanceStylist.
     */
    public static class Guidance {
        private final String mTitle;
        private final String mDescription;
        private final String mBreadcrumb;
        private final Drawable mIconDrawable;

        /**
         * Constructs a Guidance object with the specified title, description, breadcrumb, and
         * icon drawable.
         * @param title The title for the current guided step.
         * @param description The description for the current guided step.
         * @param breadcrumb The breadcrumb for the current guided step.
         * @param icon The icon drawable representing the current guided step.
         */
        public Guidance(String title, String description, String breadcrumb, Drawable icon) {
            mBreadcrumb = breadcrumb;
            mTitle = title;
            mDescription = description;
            mIconDrawable = icon;
        }

        /**
         * Returns the title specified when this Guidance was constructed.
         * @return The title for this Guidance.
         */
        public String getTitle() {
            return mTitle;
        }

        /**
         * Returns the description specified when this Guidance was constructed.
         * @return The description for this Guidance.
         */
        public String getDescription() {
            return mDescription;
        }

        /**
         * Returns the breadcrumb specified when this Guidance was constructed.
         * @return The breadcrumb for this Guidance.
         */
        public String getBreadcrumb() {
            return mBreadcrumb;
        }

        /**
         * Returns the icon drawable specified when this Guidance was constructed.
         * @return The icon for this Guidance.
         */
        public Drawable getIconDrawable() {
            return mIconDrawable;
        }
    }

    private TextView mTitleView;
    private TextView mDescriptionView;
    private TextView mBreadcrumbView;
    private ImageView mIconView;
    private View mGuidanceContainer;

    /**
     * Creates an appropriately configured view for the given Guidance, using the provided
     * inflater and container.
     * <p>
     * <i>Note: Does not actually add the created view to the container; the caller should do
     * this.</i>
     * @param inflater The layout inflater to be used when constructing the view.
     * @param container The view group to be passed in the call to
     * <code>LayoutInflater.inflate</code>.
     * @param guidance The guidance data for the view.
     * @return The view to be added to the caller's view hierarchy.
     */
    public View onCreateView(
            final LayoutInflater inflater, ViewGroup container, Guidance guidance) {

        View guidanceView = inflater.inflate(onProvideLayoutId(), container, false);
        mTitleView = (TextView) guidanceView.findViewById(R.id.guidance_title);
        mBreadcrumbView = (TextView) guidanceView.findViewById(R.id.guidance_breadcrumb);
        mDescriptionView = (TextView) guidanceView.findViewById(R.id.guidance_description);
        mIconView = (ImageView) guidanceView.findViewById(R.id.guidance_icon);
        mGuidanceContainer = guidanceView.findViewById(R.id.guidance_container);

        // We allow any of the cached subviews to be null, so that subclasses can choose not to
        // display a particular piece of information.
        if (mTitleView != null) {
            mTitleView.setText(guidance.getTitle());
        }

        if (mBreadcrumbView != null) {
            mBreadcrumbView.setText(guidance.getBreadcrumb());
        }

        if (mDescriptionView != null) {
            mDescriptionView.setText(guidance.getDescription());
        }

        if (mIconView != null) {
            if (guidance.getIconDrawable() != null) {
                mIconView.setImageDrawable(guidance.getIconDrawable());
            } else {
                mIconView.setVisibility(View.GONE);
            }
        }

        if (mGuidanceContainer != null) {
            CharSequence contentDescription = mGuidanceContainer.getContentDescription();
            if (TextUtils.isEmpty(contentDescription)) {
                StringBuilder builder = new StringBuilder();
                if (!TextUtils.isEmpty(guidance.getBreadcrumb())) {
                    builder.append(guidance.getBreadcrumb()).append('\n');
                }
                if (!TextUtils.isEmpty(guidance.getTitle())) {
                    builder.append(guidance.getTitle()).append('\n');
                }
                if (!TextUtils.isEmpty(guidance.getDescription())) {
                    builder.append(guidance.getDescription()).append('\n');
                }
                mGuidanceContainer.setContentDescription(builder);
            }
        }

        return guidanceView;
    }

    /**
     * Called when destroy the View created by GuidanceStylist.
     */
    public void onDestroyView() {
        mBreadcrumbView = null;
        mDescriptionView = null;
        mIconView = null;
        mTitleView = null;
    }

    /**
     * Provides the resource ID of the layout defining the guidance view. Subclasses may override
     * to provide their own customized layouts. The base implementation returns
     * {@link androidx.leanback.R.layout#lb_guidance}. If overridden, the substituted
     * layout should contain matching IDs for any views that should be managed by the base class;
     * this can be achieved by starting with a copy of the base layout file.
     * @return The resource ID of the layout to be inflated to define the guidance view.
     */
    public int onProvideLayoutId() {
        return R.layout.lb_guidance;
    }

    /**
     * Returns the view displaying the title of the guidance.
     * @return The text view object for the title.
     */
    public TextView getTitleView() {
        return mTitleView;
    }

    /**
     * Returns the view displaying the description of the guidance.
     * @return The text view object for the description.
     */
    public TextView getDescriptionView() {
        return mDescriptionView;
    }

    /**
     * Returns the view displaying the breadcrumb of the guidance.
     * @return The text view object for the breadcrumb.
     */
    public TextView getBreadcrumbView() {
        return mBreadcrumbView;
    }

    /**
     * Returns the view displaying the icon of the guidance.
     * @return The image view object for the icon.
     */
    public ImageView getIconView() {
        return mIconView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onImeAppearing(@NonNull List<Animator> animators) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onImeDisappearing(@NonNull List<Animator> animators) {
    }

}
