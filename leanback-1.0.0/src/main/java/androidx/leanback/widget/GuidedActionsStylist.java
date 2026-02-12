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
import static androidx.leanback.widget.GuidedAction.EDITING_ACTIVATOR_VIEW;
import static androidx.leanback.widget.GuidedAction.EDITING_DESCRIPTION;
import static androidx.leanback.widget.GuidedAction.EDITING_NONE;
import static androidx.leanback.widget.GuidedAction.EDITING_TITLE;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Build.VERSION;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.content.ContextCompat;
import androidx.leanback.R;
import androidx.leanback.transition.TransitionEpicenterCallback;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.leanback.widget.GuidedActionAdapter.EditListener;
import androidx.leanback.widget.picker.DatePicker;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * GuidedActionsStylist is used within a {@link androidx.leanback.app.GuidedStepFragment}
 * to supply the right-side panel where users can take actions. It consists of a container for the
 * list of actions, and a stationary selector view that indicates visually the location of focus.
 * GuidedActionsStylist has two different layouts: default is for normal actions including text,
 * radio, checkbox, DatePicker, etc, the other when {@link #setAsButtonActions()} is called is
 * recommended for button actions such as "yes", "no".
 * <p>
 * Many aspects of the base GuidedActionsStylist can be customized through theming; see the
 * theme attributes below. Note that these attributes are not set on individual elements in layout
 * XML, but instead would be set in a custom theme. See
 * <a href="http://developer.android.com/guide/topics/ui/themes.html">Styles and Themes</a>
 * for more information.
 * <p>
 * If these hooks are insufficient, this class may also be subclassed. Subclasses may wish to
 * override the {@link #onProvideLayoutId} method to change the layout used to display the
 * list container and selector; override {@link #onProvideItemLayoutId(int)} and
 * {@link #getItemViewType(GuidedAction)} method to change the layout used to display each action.
 * <p>
 * To support a "click to activate" view similar to DatePicker, app needs:
 * <li> Override {@link #onProvideItemLayoutId(int)} and {@link #getItemViewType(GuidedAction)},
 * provides a layout id for the action.
 * <li> The layout must include a widget with id "guidedactions_activator_item", the widget is
 * toggled edit mode by {@link View#setActivated(boolean)}.
 * <li> Override {@link #onBindActivatorView(ViewHolder, GuidedAction)} to populate values into View.
 * <li> Override {@link #onUpdateActivatorView(ViewHolder, GuidedAction)} to update action.
 * <p>
 * Note: If an alternate list layout is provided, the following view IDs must be supplied:
 * <ul>
 * <li>{@link androidx.leanback.R.id#guidedactions_list}</li>
 * </ul><p>
 * These view IDs must be present in order for the stylist to function. The list ID must correspond
 * to a {@link VerticalGridView} or subclass.
 * <p>
 * If an alternate item layout is provided, the following view IDs should be used to refer to base
 * elements:
 * <ul>
 * <li>{@link androidx.leanback.R.id#guidedactions_item_content}</li>
 * <li>{@link androidx.leanback.R.id#guidedactions_item_title}</li>
 * <li>{@link androidx.leanback.R.id#guidedactions_item_description}</li>
 * <li>{@link androidx.leanback.R.id#guidedactions_item_icon}</li>
 * <li>{@link androidx.leanback.R.id#guidedactions_item_checkmark}</li>
 * <li>{@link androidx.leanback.R.id#guidedactions_item_chevron}</li>
 * </ul><p>
 * These view IDs are allowed to be missing, in which case the corresponding views in {@link
 * GuidedActionsStylist.ViewHolder} will be null.
 * <p>
 * In order to support editable actions, the view associated with guidedactions_item_title should
 * be a subclass of {@link android.widget.EditText}, and should satisfy the {@link
 * ImeKeyMonitor} interface and {@link GuidedActionAutofillSupport} interface.
 *
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedStepImeAppearingAnimation
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedStepImeDisappearingAnimation
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionsSelectorDrawable
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionsListStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedSubActionsListStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedButtonActionsListStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemContainerStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemCheckmarkStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemIconStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemContentStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemTitleStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemDescriptionStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionItemChevronStyle
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionPressedAnimation
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionUnpressedAnimation
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionEnabledChevronAlpha
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionDisabledChevronAlpha
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionTitleMinLines
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionTitleMaxLines
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionDescriptionMinLines
 * @attr ref androidx.leanback.R.styleable#LeanbackGuidedStepTheme_guidedActionVerticalPadding
 * @see android.R.attr#listChoiceIndicatorSingle
 * @see android.R.attr#listChoiceIndicatorMultiple
 * @see androidx.leanback.app.GuidedStepFragment
 * @see GuidedAction
 */
public class GuidedActionsStylist implements FragmentAnimationProvider {

    /**
     * Default viewType that associated with default layout Id for the action item.
     * @see #getItemViewType(GuidedAction)
     * @see #onProvideItemLayoutId(int)
     * @see #onCreateViewHolder(ViewGroup, int)
     */
    public static final int VIEW_TYPE_DEFAULT = 0;

    /**
     * ViewType for DatePicker.
     */
    public static final int VIEW_TYPE_DATE_PICKER = 1;

    final static ItemAlignmentFacet sGuidedActionItemAlignFacet;

    static {
        sGuidedActionItemAlignFacet = new ItemAlignmentFacet();
        ItemAlignmentFacet.ItemAlignmentDef alignedDef = new ItemAlignmentFacet.ItemAlignmentDef();
        alignedDef.setItemAlignmentViewId(R.id.guidedactions_item_title);
        alignedDef.setAlignedToTextViewBaseline(true);
        alignedDef.setItemAlignmentOffset(0);
        alignedDef.setItemAlignmentOffsetWithPadding(true);
        alignedDef.setItemAlignmentOffsetPercent(0);
        sGuidedActionItemAlignFacet.setAlignmentDefs(new ItemAlignmentFacet.ItemAlignmentDef[]{alignedDef});
    }

    /**
     * ViewHolder caches information about the action item layouts' subviews. Subclasses of {@link
     * GuidedActionsStylist} may also wish to subclass this in order to add fields.
     * @see GuidedAction
     */
    public static class ViewHolder extends RecyclerView.ViewHolder implements FacetProvider {

        GuidedAction mAction;
        private View mContentView;
        TextView mTitleView;
        TextView mDescriptionView;
        View mActivatorView;
        ImageView mIconView;
        ImageView mCheckmarkView;
        ImageView mChevronView;
        int mEditingMode = EDITING_NONE;
        private final boolean mIsSubAction;
        Animator mPressAnimator;

        final AccessibilityDelegate mDelegate = new AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
                super.onInitializeAccessibilityEvent(host, event);
                event.setChecked(mAction != null && mAction.isChecked());
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCheckable(
                        mAction != null && mAction.getCheckSetId() != GuidedAction.NO_CHECK_SET);
                info.setChecked(mAction != null && mAction.isChecked());
            }
        };

        /**
         * Constructs an ViewHolder and caches the relevant subviews.
         */
        public ViewHolder(View v) {
            this(v, false);
        }

        /**
         * Constructs an ViewHolder for sub action and caches the relevant subviews.
         */
        public ViewHolder(View v, boolean isSubAction) {
            super(v);

            mContentView = v.findViewById(R.id.guidedactions_item_content);
            mTitleView = (TextView) v.findViewById(R.id.guidedactions_item_title);
            mActivatorView = v.findViewById(R.id.guidedactions_activator_item);
            mDescriptionView = (TextView) v.findViewById(R.id.guidedactions_item_description);
            mIconView = (ImageView) v.findViewById(R.id.guidedactions_item_icon);
            mCheckmarkView = (ImageView) v.findViewById(R.id.guidedactions_item_checkmark);
            mChevronView = (ImageView) v.findViewById(R.id.guidedactions_item_chevron);
            mIsSubAction = isSubAction;

            v.setAccessibilityDelegate(mDelegate);
        }

        /**
         * Returns the content view within this view holder's view, where title and description are
         * shown.
         */
        public View getContentView() {
            return mContentView;
        }

        /**
         * Returns the title view within this view holder's view.
         */
        public TextView getTitleView() {
            return mTitleView;
        }

        /**
         * Convenience method to return an editable version of the title, if possible,
         * or null if the title view isn't an EditText.
         */
        public EditText getEditableTitleView() {
            return (mTitleView instanceof EditText) ? (EditText)mTitleView : null;
        }

        /**
         * Returns the description view within this view holder's view.
         */
        public TextView getDescriptionView() {
            return mDescriptionView;
        }

        /**
         * Convenience method to return an editable version of the description, if possible,
         * or null if the description view isn't an EditText.
         */
        public EditText getEditableDescriptionView() {
            return (mDescriptionView instanceof EditText) ? (EditText)mDescriptionView : null;
        }

        /**
         * Returns the icon view within this view holder's view.
         */
        public ImageView getIconView() {
            return mIconView;
        }

        /**
         * Returns the checkmark view within this view holder's view.
         */
        public ImageView getCheckmarkView() {
            return mCheckmarkView;
        }

        /**
         * Returns the chevron view within this view holder's view.
         */
        public ImageView getChevronView() {
            return mChevronView;
        }

        /**
         * Returns true if in editing title, description, or activator View, false otherwise.
         */
        public boolean isInEditing() {
            return mEditingMode != EDITING_NONE;
        }

        /**
         * Returns true if in editing title, description, so IME would be open.
         * @return True if in editing title, description, so IME would be open, false otherwise.
         */
        public boolean isInEditingText() {
            return mEditingMode == EDITING_TITLE || mEditingMode == EDITING_DESCRIPTION;
        }

        /**
         * Returns true if the TextView is in editing title, false otherwise.
         */
        public boolean isInEditingTitle() {
            return mEditingMode == EDITING_TITLE;
        }

        /**
         * Returns true if the TextView is in editing description, false otherwise.
         */
        public boolean isInEditingDescription() {
            return mEditingMode == EDITING_DESCRIPTION;
        }

        /**
         * Returns true if is in editing activator view with id guidedactions_activator_item, false
         * otherwise.
         */
        public boolean isInEditingActivatorView() {
            return mEditingMode == EDITING_ACTIVATOR_VIEW;
        }

        /**
         * @return Current editing title view or description view or activator view or null if not
         * in editing.
         */
        public View getEditingView() {
            switch(mEditingMode) {
            case EDITING_TITLE:
                return mTitleView;
            case EDITING_DESCRIPTION:
                return mDescriptionView;
            case EDITING_ACTIVATOR_VIEW:
                return mActivatorView;
            case EDITING_NONE:
            default:
                return null;
            }
        }

        /**
         * @return True if bound action is inside {@link GuidedAction#getSubActions()}, false
         * otherwise.
         */
        public boolean isSubAction() {
            return mIsSubAction;
        }

        /**
         * @return Currently bound action.
         */
        public GuidedAction getAction() {
            return mAction;
        }

        void setActivated(boolean activated) {
            mActivatorView.setActivated(activated);
            if (itemView instanceof GuidedActionItemContainer) {
                ((GuidedActionItemContainer) itemView).setFocusOutAllowed(!activated);
            }
        }

        @Override
        public Object getFacet(Class<?> facetClass) {
            if (facetClass == ItemAlignmentFacet.class) {
                return sGuidedActionItemAlignFacet;
            }
            return null;
        }

        void press(boolean pressed) {
            if (mPressAnimator != null) {
                mPressAnimator.cancel();
                mPressAnimator = null;
            }
            final int themeAttrId = pressed ? R.attr.guidedActionPressedAnimation :
                    R.attr.guidedActionUnpressedAnimation;
            Context ctx = itemView.getContext();
            TypedValue typedValue = new TypedValue();
            if (ctx.getTheme().resolveAttribute(themeAttrId, typedValue, true)) {
                mPressAnimator = AnimatorInflater.loadAnimator(ctx, typedValue.resourceId);
                mPressAnimator.setTarget(itemView);
                mPressAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mPressAnimator = null;
                    }
                });
                mPressAnimator.start();
            }
        }
    }

    private static final String TAG = "GuidedActionsStylist";

    ViewGroup mMainView;
    private VerticalGridView mActionsGridView;
    VerticalGridView mSubActionsGridView;
    private View mSubActionsBackground;
    private View mBgView;
    private View mContentView;
    private boolean mButtonActions;

    // Cached values from resources
    private float mEnabledTextAlpha;
    private float mDisabledTextAlpha;
    private float mEnabledDescriptionAlpha;
    private float mDisabledDescriptionAlpha;
    private float mEnabledChevronAlpha;
    private float mDisabledChevronAlpha;
    private int mTitleMinLines;
    private int mTitleMaxLines;
    private int mDescriptionMinLines;
    private int mVerticalPadding;
    private int mDisplayHeight;

    private EditListener mEditListener;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    GuidedAction mExpandedAction = null;
    Object mExpandTransition;
    private boolean mBackToCollapseSubActions = true;
    private boolean mBackToCollapseActivatorView = true;

    private float mKeyLinePercent;

    /**
     * Creates a view appropriate for displaying a list of GuidedActions, using the provided
     * inflater and container.
     * <p>
     * <i>Note: Does not actually add the created view to the container; the caller should do
     * this.</i>
     * @param inflater The layout inflater to be used when constructing the view.
     * @param container The view group to be passed in the call to
     * <code>LayoutInflater.inflate</code>.
     * @return The view to be added to the caller's view hierarchy.
     */
    public View onCreateView(LayoutInflater inflater, final ViewGroup container) {
        TypedArray ta = inflater.getContext().getTheme().obtainStyledAttributes(
                R.styleable.LeanbackGuidedStepTheme);
        float keylinePercent = ta.getFloat(R.styleable.LeanbackGuidedStepTheme_guidedStepKeyline,
                40);
        mMainView = (ViewGroup) inflater.inflate(onProvideLayoutId(), container, false);
        mContentView = mMainView.findViewById(mButtonActions ? R.id.guidedactions_content2 :
                R.id.guidedactions_content);
        mBgView = mMainView.findViewById(mButtonActions ? R.id.guidedactions_list_background2 :
                R.id.guidedactions_list_background);
        if (mMainView instanceof VerticalGridView) {
            mActionsGridView = (VerticalGridView) mMainView;
        } else {
            mActionsGridView = (VerticalGridView) mMainView.findViewById(mButtonActions
                    ? R.id.guidedactions_list2 : R.id.guidedactions_list);
            if (mActionsGridView == null) {
                throw new IllegalStateException("No ListView exists.");
            }
            mActionsGridView.setWindowAlignmentOffsetPercent(keylinePercent);
            mActionsGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
            if (!mButtonActions) {
                mSubActionsGridView = (VerticalGridView) mMainView.findViewById(
                        R.id.guidedactions_sub_list);
                mSubActionsBackground = mMainView.findViewById(
                        R.id.guidedactions_sub_list_background);
            }
        }
        mActionsGridView.setFocusable(false);
        mActionsGridView.setFocusableInTouchMode(false);

        // Cache widths, chevron alpha values, max and min text lines, etc
        Context ctx = mMainView.getContext();
        TypedValue val = new TypedValue();
        mEnabledChevronAlpha = getFloat(ctx, val, R.attr.guidedActionEnabledChevronAlpha);
        mDisabledChevronAlpha = getFloat(ctx, val, R.attr.guidedActionDisabledChevronAlpha);
        mTitleMinLines = getInteger(ctx, val, R.attr.guidedActionTitleMinLines);
        mTitleMaxLines = getInteger(ctx, val, R.attr.guidedActionTitleMaxLines);
        mDescriptionMinLines = getInteger(ctx, val, R.attr.guidedActionDescriptionMinLines);
        mVerticalPadding = getDimension(ctx, val, R.attr.guidedActionVerticalPadding);
        mDisplayHeight = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getHeight();

        mEnabledTextAlpha = getFloatValue(ctx.getResources(), val, R.dimen
                .lb_guidedactions_item_unselected_text_alpha);
        mDisabledTextAlpha = getFloatValue(ctx.getResources(), val, R.dimen
                .lb_guidedactions_item_disabled_text_alpha);
        mEnabledDescriptionAlpha = getFloatValue(ctx.getResources(), val, R.dimen
                .lb_guidedactions_item_unselected_description_text_alpha);
        mDisabledDescriptionAlpha = getFloatValue(ctx.getResources(), val, R.dimen
                .lb_guidedactions_item_disabled_description_text_alpha);

        mKeyLinePercent = GuidanceStylingRelativeLayout.getKeyLinePercent(ctx);
        if (mContentView instanceof GuidedActionsRelativeLayout) {
            ((GuidedActionsRelativeLayout) mContentView).setInterceptKeyEventListener(
                    new GuidedActionsRelativeLayout.InterceptKeyEventListener() {
                        @Override
                        public boolean onInterceptKeyEvent(KeyEvent event) {
                            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                                    && event.getAction() == KeyEvent.ACTION_UP
                                    && mExpandedAction != null) {
                                if ((mExpandedAction.hasSubActions()
                                        && isBackKeyToCollapseSubActions())
                                        || (mExpandedAction.hasEditableActivatorView()
                                        && isBackKeyToCollapseActivatorView())) {
                                    collapseAction(true);
                                    return true;
                                }
                            }
                            return false;
                        }
                    }
            );
        }
        return mMainView;
    }

    /**
     * Choose the layout resource for button actions in {@link #onProvideLayoutId()}.
     */
    public void setAsButtonActions() {
        if (mMainView != null) {
            throw new IllegalStateException("setAsButtonActions() must be called before creating "
                    + "views");
        }
        mButtonActions = true;
    }

    /**
     * Returns true if it is button actions list, false for normal actions list.
     * @return True if it is button actions list, false for normal actions list.
     */
    public boolean isButtonActions() {
        return mButtonActions;
    }

    /**
     * Called when destroy the View created by GuidedActionsStylist.
     */
    public void onDestroyView() {
        mExpandedAction = null;
        mExpandTransition = null;
        mActionsGridView = null;
        mSubActionsGridView = null;
        mSubActionsBackground = null;
        mContentView = null;
        mBgView = null;
        mMainView = null;
    }

    /**
     * Returns the VerticalGridView that displays the list of GuidedActions.
     * @return The VerticalGridView for this presenter.
     */
    public VerticalGridView getActionsGridView() {
        return mActionsGridView;
    }

    /**
     * Returns the VerticalGridView that displays the sub actions list of an expanded action.
     * @return The VerticalGridView that displays the sub actions list of an expanded action.
     */
    public VerticalGridView getSubActionsGridView() {
        return mSubActionsGridView;
    }

    /**
     * Provides the resource ID of the layout defining the host view for the list of guided actions.
     * Subclasses may override to provide their own customized layouts. The base implementation
     * returns {@link androidx.leanback.R.layout#lb_guidedactions} or
     * {@link androidx.leanback.R.layout#lb_guidedbuttonactions} if
     * {@link #isButtonActions()} is true. If overridden, the substituted layout should contain
     * matching IDs for any views that should be managed by the base class; this can be achieved by
     * starting with a copy of the base layout file.
     *
     * @return The resource ID of the layout to be inflated to define the host view for the list of
     *         GuidedActions.
     */
    public int onProvideLayoutId() {
        return mButtonActions ? R.layout.lb_guidedbuttonactions : R.layout.lb_guidedactions;
    }

    /**
     * Return view type of action, each different type can have differently associated layout Id.
     * Default implementation returns {@link #VIEW_TYPE_DEFAULT}.
     * @param action  The action object.
     * @return View type that used in {@link #onProvideItemLayoutId(int)}.
     */
    public int getItemViewType(GuidedAction action) {
        if (action instanceof GuidedDatePickerAction) {
            return VIEW_TYPE_DATE_PICKER;
        }
        return VIEW_TYPE_DEFAULT;
    }

    /**
     * Provides the resource ID of the layout defining the view for an individual guided actions.
     * Subclasses may override to provide their own customized layouts. The base implementation
     * returns {@link androidx.leanback.R.layout#lb_guidedactions_item}. If overridden,
     * the substituted layout should contain matching IDs for any views that should be managed by
     * the base class; this can be achieved by starting with a copy of the base layout file. Note
     * that in order for the item to support editing, the title view should both subclass {@link
     * android.widget.EditText} and implement {@link ImeKeyMonitor},
     * {@link GuidedActionAutofillSupport}; see {@link
     * GuidedActionEditText}.  To support different types of Layouts, override {@link
     * #onProvideItemLayoutId(int)}.
     * @return The resource ID of the layout to be inflated to define the view to display an
     * individual GuidedAction.
     */
    public int onProvideItemLayoutId() {
        return R.layout.lb_guidedactions_item;
    }

    /**
     * Provides the resource ID of the layout defining the view for an individual guided actions.
     * Subclasses may override to provide their own customized layouts. The base implementation
     * supports:
     * <li>{@link androidx.leanback.R.layout#lb_guidedactions_item}
     * <li>{{@link androidx.leanback.R.layout#lb_guidedactions_datepicker_item}. If
     * overridden, the substituted layout should contain matching IDs for any views that should be
     * managed by the base class; this can be achieved by starting with a copy of the base layout
     * file. Note that in order for the item to support editing, the title view should both subclass
     * {@link android.widget.EditText} and implement {@link ImeKeyMonitor}; see
     * {@link GuidedActionEditText}.
     *
     * @param viewType View type returned by {@link #getItemViewType(GuidedAction)}
     * @return The resource ID of the layout to be inflated to define the view to display an
     *         individual GuidedAction.
     */
    public int onProvideItemLayoutId(int viewType) {
        if (viewType == VIEW_TYPE_DEFAULT) {
            return onProvideItemLayoutId();
        } else if (viewType == VIEW_TYPE_DATE_PICKER) {
            return R.layout.lb_guidedactions_datepicker_item;
        } else {
            throw new RuntimeException("ViewType " + viewType
                    + " not supported in GuidedActionsStylist");
        }
    }

    /**
     * Constructs a {@link ViewHolder} capable of representing {@link GuidedAction}s. Subclasses
     * may choose to return a subclass of ViewHolder.  To support different view types, override
     * {@link #onCreateViewHolder(ViewGroup, int)}
     * <p>
     * <i>Note: Should not actually add the created view to the parent; the caller will do
     * this.</i>
     * @param parent The view group to be used as the parent of the new view.
     * @return The view to be added to the caller's view hierarchy.
     */
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(onProvideItemLayoutId(), parent, false);
        return new ViewHolder(v, parent == mSubActionsGridView);
    }

    /**
     * Constructs a {@link ViewHolder} capable of representing {@link GuidedAction}s. Subclasses
     * may choose to return a subclass of ViewHolder.
     * <p>
     * <i>Note: Should not actually add the created view to the parent; the caller will do
     * this.</i>
     * @param parent The view group to be used as the parent of the new view.
     * @param viewType The viewType returned by {@link #getItemViewType(GuidedAction)}
     * @return The view to be added to the caller's view hierarchy.
     */
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DEFAULT) {
            return onCreateViewHolder(parent);
        }
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(onProvideItemLayoutId(viewType), parent, false);
        return new ViewHolder(v, parent == mSubActionsGridView);
    }

    /**
     * Binds a {@link ViewHolder} to a particular {@link GuidedAction}.
     * @param vh The view holder to be associated with the given action.
     * @param action The guided action to be displayed by the view holder's view.
     * @return The view to be added to the caller's view hierarchy.
     */
    public void onBindViewHolder(ViewHolder vh, GuidedAction action) {
        vh.mAction = action;
        if (vh.mTitleView != null) {
            vh.mTitleView.setInputType(action.getInputType());
            vh.mTitleView.setText(action.getTitle());
            vh.mTitleView.setAlpha(action.isEnabled() ? mEnabledTextAlpha : mDisabledTextAlpha);
            vh.mTitleView.setFocusable(false);
            vh.mTitleView.setClickable(false);
            vh.mTitleView.setLongClickable(false);
            if (Build.VERSION.SDK_INT >= 28) {
                if (action.isEditable()) {
                    vh.mTitleView.setAutofillHints(action.getAutofillHints());
                } else {
                    vh.mTitleView.setAutofillHints((String[]) null);
                }
            } else if (VERSION.SDK_INT >= 26) {
                // disable autofill below P as dpad/keyboard is not supported
                vh.mTitleView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
        if (vh.mDescriptionView != null) {
            vh.mDescriptionView.setInputType(action.getDescriptionInputType());
            vh.mDescriptionView.setText(action.getDescription());
            vh.mDescriptionView.setVisibility(TextUtils.isEmpty(action.getDescription())
                    ? View.GONE : View.VISIBLE);
            vh.mDescriptionView.setAlpha(action.isEnabled() ? mEnabledDescriptionAlpha :
                mDisabledDescriptionAlpha);
            vh.mDescriptionView.setFocusable(false);
            vh.mDescriptionView.setClickable(false);
            vh.mDescriptionView.setLongClickable(false);
            if (Build.VERSION.SDK_INT >= 28) {
                if (action.isDescriptionEditable()) {
                    vh.mDescriptionView.setAutofillHints(action.getAutofillHints());
                } else {
                    vh.mDescriptionView.setAutofillHints((String[]) null);
                }
            } else if (VERSION.SDK_INT >= 26) {
                // disable autofill below P as dpad/keyboard is not supported
                vh.mTitleView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
        }
        // Clients might want the check mark view to be gone entirely, in which case, ignore it.
        if (vh.mCheckmarkView != null) {
            onBindCheckMarkView(vh, action);
        }
        setIcon(vh.mIconView, action);

        if (action.hasMultilineDescription()) {
            if (vh.mTitleView != null) {
                setMaxLines(vh.mTitleView, mTitleMaxLines);
                vh.mTitleView.setInputType(
                        vh.mTitleView.getInputType() | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                if (vh.mDescriptionView != null) {
                    vh.mDescriptionView.setInputType(vh.mDescriptionView.getInputType()
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    vh.mDescriptionView.setMaxHeight(getDescriptionMaxHeight(
                            vh.itemView.getContext(), vh.mTitleView));
                }
            }
        } else {
            if (vh.mTitleView != null) {
                setMaxLines(vh.mTitleView, mTitleMinLines);
            }
            if (vh.mDescriptionView != null) {
                setMaxLines(vh.mDescriptionView, mDescriptionMinLines);
            }
        }
        if (vh.mActivatorView != null) {
            onBindActivatorView(vh, action);
        }
        setEditingMode(vh, false /*editing*/, false /*withTransition*/);
        if (action.isFocusable()) {
            vh.itemView.setFocusable(true);
            ((ViewGroup) vh.itemView).setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        } else {
            vh.itemView.setFocusable(false);
            ((ViewGroup) vh.itemView).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        setupImeOptions(vh, action);

        updateChevronAndVisibility(vh);
    }

    /**
     * Switches action to edit mode and pops up the keyboard.
     */
    public void openInEditMode(GuidedAction action) {
        final GuidedActionAdapter guidedActionAdapter =
                (GuidedActionAdapter) getActionsGridView().getAdapter();
        int actionIndex = guidedActionAdapter.getActions().indexOf(action);
        if (actionIndex < 0 || !action.isEditable()) {
            return;
        }

        getActionsGridView().setSelectedPosition(actionIndex, new ViewHolderTask() {
            @Override
            public void run(RecyclerView.ViewHolder viewHolder) {
                ViewHolder vh = (ViewHolder) viewHolder;
                guidedActionAdapter.mGroup.openIme(guidedActionAdapter, vh);
            }
        });
    }

    private static void setMaxLines(TextView view, int maxLines) {
        // setSingleLine must be called before setMaxLines because it resets maximum to
        // Integer.MAX_VALUE.
        if (maxLines == 1) {
            view.setSingleLine(true);
        } else {
            view.setSingleLine(false);
            view.setMaxLines(maxLines);
        }
    }

    /**
     * Called by {@link #onBindViewHolder(ViewHolder, GuidedAction)} to setup IME options.  Default
     * implementation assigns {@link EditorInfo#IME_ACTION_DONE}.  Subclass may override.
     * @param vh The view holder to be associated with the given action.
     * @param action The guided action to be displayed by the view holder's view.
     */
    protected void setupImeOptions(ViewHolder vh, GuidedAction action) {
        setupNextImeOptions(vh.getEditableTitleView());
        setupNextImeOptions(vh.getEditableDescriptionView());
    }

    private void setupNextImeOptions(EditText edit) {
        if (edit != null) {
            edit.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        }
    }

    /**
     * @deprecated This method is for internal library use only and should not
     *             be called directly.
     */
    @Deprecated
    public void setEditingMode(ViewHolder vh, GuidedAction action, boolean editing) {
        if (editing != vh.isInEditing() && isInExpandTransition()) {
            onEditingModeChange(vh, action, editing);
        }
    }

    void setEditingMode(ViewHolder vh, boolean editing) {
        setEditingMode(vh, editing, true /*withTransition*/);
    }

    void setEditingMode(ViewHolder vh, boolean editing, boolean withTransition) {
        if (editing != vh.isInEditing() && !isInExpandTransition()) {
            onEditingModeChange(vh, editing, withTransition);
        }
    }

    /**
     * @deprecated Use {@link #onEditingModeChange(ViewHolder, boolean, boolean)}.
     */
    @Deprecated
    protected void onEditingModeChange(ViewHolder vh, GuidedAction action, boolean editing) {
    }

    /**
     * Called when editing mode of an ViewHolder is changed.  Subclass must call
     * <code>super.onEditingModeChange(vh,editing,withTransition)</code>.
     *
     * @param vh                ViewHolder to change editing mode.
     * @param editing           True to enable editing, false to stop editing
     * @param withTransition    True to run expand transiiton, false otherwise.
     */
    @CallSuper
    protected void onEditingModeChange(ViewHolder vh, boolean editing, boolean withTransition) {
        GuidedAction action = vh.getAction();
        TextView titleView = vh.getTitleView();
        TextView descriptionView = vh.getDescriptionView();
        if (editing) {
            CharSequence editTitle = action.getEditTitle();
            if (titleView != null && editTitle != null) {
                titleView.setText(editTitle);
            }
            CharSequence editDescription = action.getEditDescription();
            if (descriptionView != null && editDescription != null) {
                descriptionView.setText(editDescription);
            }
            if (action.isDescriptionEditable()) {
                if (descriptionView != null) {
                    descriptionView.setVisibility(View.VISIBLE);
                    descriptionView.setInputType(action.getDescriptionEditInputType());
                }
                vh.mEditingMode = EDITING_DESCRIPTION;
            } else if (action.isEditable()){
                if (titleView != null) {
                    titleView.setInputType(action.getEditInputType());
                }
                vh.mEditingMode = EDITING_TITLE;
            } else if (vh.mActivatorView != null) {
                onEditActivatorView(vh, editing, withTransition);
                vh.mEditingMode = EDITING_ACTIVATOR_VIEW;
            }
        } else {
            if (titleView != null) {
                titleView.setText(action.getTitle());
            }
            if (descriptionView != null) {
                descriptionView.setText(action.getDescription());
            }
            if (vh.mEditingMode == EDITING_DESCRIPTION) {
                if (descriptionView != null) {
                    descriptionView.setVisibility(TextUtils.isEmpty(action.getDescription())
                            ? View.GONE : View.VISIBLE);
                    descriptionView.setInputType(action.getDescriptionInputType());
                }
            } else if (vh.mEditingMode == EDITING_TITLE) {
                if (titleView != null) {
                    titleView.setInputType(action.getInputType());
                }
            } else if (vh.mEditingMode == EDITING_ACTIVATOR_VIEW) {
                if (vh.mActivatorView != null) {
                    onEditActivatorView(vh, editing, withTransition);
                }
            }
            vh.mEditingMode = EDITING_NONE;
        }
        // call deprecated method for backward compatible
        onEditingModeChange(vh, action, editing);
    }

    /**
     * Animates the view holder's view (or subviews thereof) when the action has had its focus
     * state changed.
     * @param vh The view holder associated with the relevant action.
     * @param focused True if the action has become focused, false if it has lost focus.
     */
    public void onAnimateItemFocused(ViewHolder vh, boolean focused) {
        // No animations for this, currently, because the animation is done on
        // mSelectorView
    }

    /**
     * Animates the view holder's view (or subviews thereof) when the action has had its press
     * state changed.
     * @param vh The view holder associated with the relevant action.
     * @param pressed True if the action has been pressed, false if it has been unpressed.
     */
    public void onAnimateItemPressed(ViewHolder vh, boolean pressed) {
        vh.press(pressed);
    }

    /**
     * Resets the view holder's view to unpressed state.
     * @param vh The view holder associated with the relevant action.
     */
    public void onAnimateItemPressedCancelled(ViewHolder vh) {
        vh.press(false);
    }

    /**
     * Animates the view holder's view (or subviews thereof) when the action has had its check state
     * changed. Default implementation calls setChecked() if {@link ViewHolder#getCheckmarkView()}
     * is instance of {@link Checkable}.
     *
     * @param vh The view holder associated with the relevant action.
     * @param checked True if the action has become checked, false if it has become unchecked.
     * @see #onBindCheckMarkView(ViewHolder, GuidedAction)
     */
    public void onAnimateItemChecked(ViewHolder vh, boolean checked) {
        if (vh.mCheckmarkView instanceof Checkable) {
            ((Checkable) vh.mCheckmarkView).setChecked(checked);
        }
    }

    /**
     * Sets states of check mark view, called by {@link #onBindViewHolder(ViewHolder, GuidedAction)}
     * when action's checkset Id is other than {@link GuidedAction#NO_CHECK_SET}. Default
     * implementation assigns drawable loaded from theme attribute
     * {@link android.R.attr#listChoiceIndicatorMultiple} for checkbox or
     * {@link android.R.attr#listChoiceIndicatorSingle} for radio button. Subclass rarely needs
     * override the method, instead app can provide its own drawable that supports transition
     * animations, change theme attributes {@link android.R.attr#listChoiceIndicatorMultiple} and
     * {@link android.R.attr#listChoiceIndicatorSingle} in {androidx.leanback.R.
     * styleable#LeanbackGuidedStepTheme}.
     *
     * @param vh The view holder associated with the relevant action.
     * @param action The GuidedAction object to bind to.
     * @see #onAnimateItemChecked(ViewHolder, boolean)
     */
    public void onBindCheckMarkView(ViewHolder vh, GuidedAction action) {
        if (action.getCheckSetId() != GuidedAction.NO_CHECK_SET) {
            vh.mCheckmarkView.setVisibility(View.VISIBLE);
            int attrId = action.getCheckSetId() == GuidedAction.CHECKBOX_CHECK_SET_ID
                    ? android.R.attr.listChoiceIndicatorMultiple
                    : android.R.attr.listChoiceIndicatorSingle;
            final Context context = vh.mCheckmarkView.getContext();
            Drawable drawable = null;
            TypedValue typedValue = new TypedValue();
            if (context.getTheme().resolveAttribute(attrId, typedValue, true)) {
                drawable = ContextCompat.getDrawable(context, typedValue.resourceId);
            }
            vh.mCheckmarkView.setImageDrawable(drawable);
            if (vh.mCheckmarkView instanceof Checkable) {
                ((Checkable) vh.mCheckmarkView).setChecked(action.isChecked());
            }
        } else {
            vh.mCheckmarkView.setVisibility(View.GONE);
        }
    }

    /**
     * Performs binding activator view value to action.  Default implementation supports
     * GuidedDatePickerAction, subclass may override to add support of other views.
     * @param vh ViewHolder of activator view.
     * @param action GuidedAction to bind.
     */
    public void onBindActivatorView(ViewHolder vh, GuidedAction action) {
        if (action instanceof GuidedDatePickerAction) {
            GuidedDatePickerAction dateAction = (GuidedDatePickerAction) action;
            DatePicker dateView = (DatePicker) vh.mActivatorView;
            dateView.setDatePickerFormat(dateAction.getDatePickerFormat());
            if (dateAction.getMinDate() != Long.MIN_VALUE) {
                dateView.setMinDate(dateAction.getMinDate());
            }
            if (dateAction.getMaxDate() != Long.MAX_VALUE) {
                dateView.setMaxDate(dateAction.getMaxDate());
            }
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(dateAction.getDate());
            dateView.updateDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH), false);
        }
    }

    /**
     * Performs updating GuidedAction from activator view.  Default implementation supports
     * GuidedDatePickerAction, subclass may override to add support of other views.
     * @param vh ViewHolder of activator view.
     * @param action GuidedAction to update.
     * @return True if value has been updated, false otherwise.
     */
    public boolean onUpdateActivatorView(ViewHolder vh, GuidedAction action) {
        if (action instanceof GuidedDatePickerAction) {
            GuidedDatePickerAction dateAction = (GuidedDatePickerAction) action;
            DatePicker dateView = (DatePicker) vh.mActivatorView;
            if (dateAction.getDate() != dateView.getDate()) {
                dateAction.setDate(dateView.getDate());
                return true;
            }
        }
        return false;
    }

    /**
     * Sets listener for reporting view being edited.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void setEditListener(EditListener listener) {
        mEditListener = listener;
    }

    void onEditActivatorView(final ViewHolder vh, boolean editing, final boolean withTransition) {
        if (editing) {
            startExpanded(vh, withTransition);
            vh.itemView.setFocusable(false);
            vh.mActivatorView.requestFocus();
            vh.mActivatorView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isInExpandTransition()) {
                        ((GuidedActionAdapter) getActionsGridView().getAdapter())
                                .performOnActionClick(vh);
                    }
                }
            });
        } else {
            if (onUpdateActivatorView(vh, vh.getAction())) {
                if (mEditListener != null) {
                    mEditListener.onGuidedActionEditedAndProceed(vh.getAction());
                }
            }
            vh.itemView.setFocusable(true);
            vh.itemView.requestFocus();
            startExpanded(null, withTransition);
            vh.mActivatorView.setOnClickListener(null);
            vh.mActivatorView.setClickable(false);
        }
    }

    /**
     * Sets states of chevron view, called by {@link #onBindViewHolder(ViewHolder, GuidedAction)}.
     * Subclass may override.
     *
     * @param vh The view holder associated with the relevant action.
     * @param action The GuidedAction object to bind to.
     */
    public void onBindChevronView(ViewHolder vh, GuidedAction action) {
        final boolean hasNext = action.hasNext();
        final boolean hasSubActions = action.hasSubActions();
        if (hasNext || hasSubActions) {
            vh.mChevronView.setVisibility(View.VISIBLE);
            vh.mChevronView.setAlpha(action.isEnabled() ? mEnabledChevronAlpha :
                    mDisabledChevronAlpha);
            if (hasNext) {
                float r = mMainView != null
                        && mMainView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? 180f : 0f;
                vh.mChevronView.setRotation(r);
            } else if (action == mExpandedAction) {
                vh.mChevronView.setRotation(270);
            } else {
                vh.mChevronView.setRotation(90);
            }
        } else {
            vh.mChevronView.setVisibility(View.GONE);

        }
    }

    /**
     * Expands or collapse the sub actions list view with transition animation
     * @param avh When not null, fill sub actions list of this ViewHolder into sub actions list and
     * hide the other items in main list.  When null, collapse the sub actions list.
     * @deprecated use {@link #expandAction(GuidedAction, boolean)} and
     * {@link #collapseAction(boolean)}
     */
    @Deprecated
    public void setExpandedViewHolder(ViewHolder avh) {
        expandAction(avh == null ? null : avh.getAction(), isExpandTransitionSupported());
    }

    /**
     * Returns true if it is running an expanding or collapsing transition, false otherwise.
     * @return True if it is running an expanding or collapsing transition, false otherwise.
     */
    public boolean isInExpandTransition() {
        return mExpandTransition != null;
    }

    /**
     * Returns if expand/collapse animation is supported.  When this method returns true,
     * {@link #startExpandedTransition(ViewHolder)} will be used.  When this method returns false,
     * {@link #onUpdateExpandedViewHolder(ViewHolder)} will be called.
     * @return True if it is running an expanding or collapsing transition, false otherwise.
     */
    public boolean isExpandTransitionSupported() {
        return VERSION.SDK_INT >= 21;
    }

    /**
     * Start transition to expand or collapse GuidedActionStylist.
     * @param avh When not null, the GuidedActionStylist expands the sub actions of avh.  When null
     * the GuidedActionStylist will collapse sub actions.
     * @deprecated use {@link #expandAction(GuidedAction, boolean)} and
     * {@link #collapseAction(boolean)}
     */
    @Deprecated
    public void startExpandedTransition(ViewHolder avh) {
        expandAction(avh == null ? null : avh.getAction(), isExpandTransitionSupported());
    }

    /**
     * Enable or disable using BACK key to collapse sub actions list. Default is enabled.
     *
     * @param backToCollapse True to enable using BACK key to collapse sub actions list, false
     *                       to disable.
     * @see GuidedAction#hasSubActions
     * @see GuidedAction#getSubActions
     */
    public final void setBackKeyToCollapseSubActions(boolean backToCollapse) {
        mBackToCollapseSubActions = backToCollapse;
    }

    /**
     * @return True if using BACK key to collapse sub actions list, false otherwise. Default value
     * is true.
     *
     * @see GuidedAction#hasSubActions
     * @see GuidedAction#getSubActions
     */
    public final boolean isBackKeyToCollapseSubActions() {
        return mBackToCollapseSubActions;
    }

    /**
     * Enable or disable using BACK key to collapse {@link GuidedAction} with editable activator
     * view. Default is enabled.
     *
     * @param backToCollapse True to enable using BACK key to collapse {@link GuidedAction} with
     *                       editable activator view.
     * @see GuidedAction#hasEditableActivatorView
     */
    public final void setBackKeyToCollapseActivatorView(boolean backToCollapse) {
        mBackToCollapseActivatorView = backToCollapse;
    }

    /**
     * @return True if using BACK key to collapse {@link GuidedAction} with editable activator
     * view, false otherwise. Default value is true.
     *
     * @see GuidedAction#hasEditableActivatorView
     */
    public final boolean isBackKeyToCollapseActivatorView() {
        return mBackToCollapseActivatorView;
    }

    /**
     * Expand an action. Do nothing if it is in animation or there is action expanded.
     *
     * @param action         Action to expand.
     * @param withTransition True to run transition animation, false otherwsie.
     */
    public void expandAction(GuidedAction action, final boolean withTransition) {
        if (isInExpandTransition() || mExpandedAction != null) {
            return;
        }
        int actionPosition =
                ((GuidedActionAdapter) getActionsGridView().getAdapter()).indexOf(action);
        if (actionPosition < 0) {
            return;
        }
        boolean runTransition = isExpandTransitionSupported() && withTransition;
        if (!runTransition) {
            getActionsGridView().setSelectedPosition(actionPosition,
                    new ViewHolderTask() {
                        @Override
                        public void run(RecyclerView.ViewHolder vh) {
                            GuidedActionsStylist.ViewHolder avh =
                                    (GuidedActionsStylist.ViewHolder)vh;
                            if (avh.getAction().hasEditableActivatorView()) {
                                setEditingMode(avh, true /*editing*/, false /*withTransition*/);
                            } else {
                                onUpdateExpandedViewHolder(avh);
                            }
                        }
                    });
            if (action.hasSubActions()) {
                onUpdateSubActionsGridView(action, true);
            }
        } else {
            getActionsGridView().setSelectedPosition(actionPosition,
                    new ViewHolderTask() {
                        @Override
                        public void run(RecyclerView.ViewHolder vh) {
                            GuidedActionsStylist.ViewHolder avh =
                                    (GuidedActionsStylist.ViewHolder)vh;
                            if (avh.getAction().hasEditableActivatorView()) {
                                setEditingMode(avh, true /*editing*/, true /*withTransition*/);
                            } else {
                                startExpanded(avh, true);
                            }
                        }
                    });
        }

    }

    /**
     * Collapse expanded action. Do nothing if it is in animation or there is no action expanded.
     *
     * @param withTransition True to run transition animation, false otherwsie.
     */
    public void collapseAction(boolean withTransition) {
        if (isInExpandTransition() || mExpandedAction == null) {
            return;
        }
        boolean runTransition = isExpandTransitionSupported() && withTransition;
        int actionPosition =
                ((GuidedActionAdapter) getActionsGridView().getAdapter()).indexOf(mExpandedAction);
        if (actionPosition < 0) {
            return;
        }
        if (mExpandedAction.hasEditableActivatorView()) {
            setEditingMode(
                    ((ViewHolder) getActionsGridView().findViewHolderForPosition(actionPosition)),
                    false /*editing*/,
                    runTransition);
        } else {
            startExpanded(null, runTransition);
        }
    }

    int getKeyLine() {
        return (int) (mKeyLinePercent * mActionsGridView.getHeight() / 100);
    }

    /**
     * Internal method with assumption we already scroll to the new ViewHolder or is currently
     * expanded.
     */
    void startExpanded(ViewHolder avh, final boolean withTransition) {
        ViewHolder focusAvh = null; // expand / collapse view holder
        final int count = mActionsGridView.getChildCount();
        for (int i = 0; i < count; i++) {
            ViewHolder vh = (ViewHolder) mActionsGridView
                    .getChildViewHolder(mActionsGridView.getChildAt(i));
            if (avh == null && vh.itemView.getVisibility() == View.VISIBLE) {
                // going to collapse this one.
                focusAvh = vh;
                break;
            } else if (avh != null && vh.getAction() == avh.getAction()) {
                // going to expand this one.
                focusAvh = vh;
                break;
            }
        }
        if (focusAvh == null) {
            // huh?
            return;
        }
        boolean isExpand = avh != null;
        boolean isSubActionTransition = focusAvh.getAction().hasSubActions();
        if (withTransition) {
            Object set = TransitionHelper.createTransitionSet(false);
            float slideDistance = isSubActionTransition ? focusAvh.itemView.getHeight()
                    : focusAvh.itemView.getHeight() * 0.5f;
            Object slideAndFade = TransitionHelper.createFadeAndShortSlide(
                    Gravity.TOP | Gravity.BOTTOM,
                    slideDistance);
            TransitionHelper.setEpicenterCallback(slideAndFade, new TransitionEpicenterCallback() {
                Rect mRect = new Rect();
                @Override
                public Rect onGetEpicenter(Object transition) {
                    int centerY = getKeyLine();
                    int centerX = 0;
                    mRect.set(centerX, centerY, centerX, centerY);
                    return mRect;
                }
            });
            Object changeFocusItemTransform = TransitionHelper.createChangeTransform();
            Object changeFocusItemBounds = TransitionHelper.createChangeBounds(false);
            Object fade = TransitionHelper.createFadeTransition(TransitionHelper.FADE_IN
                    | TransitionHelper.FADE_OUT);
            Object changeGridBounds = TransitionHelper.createChangeBounds(false);
            if (avh == null) {
                TransitionHelper.setStartDelay(slideAndFade, 150);
                TransitionHelper.setStartDelay(changeFocusItemTransform, 100);
                TransitionHelper.setStartDelay(changeFocusItemBounds, 100);
                TransitionHelper.setStartDelay(changeGridBounds, 100);
            } else {
                TransitionHelper.setStartDelay(fade, 100);
                TransitionHelper.setStartDelay(changeGridBounds, 50);
                TransitionHelper.setStartDelay(changeFocusItemTransform, 50);
                TransitionHelper.setStartDelay(changeFocusItemBounds, 50);
            }
            for (int i = 0; i < count; i++) {
                ViewHolder vh = (ViewHolder) mActionsGridView
                        .getChildViewHolder(mActionsGridView.getChildAt(i));
                if (vh == focusAvh) {
                    // going to expand/collapse this one.
                    if (isSubActionTransition) {
                        TransitionHelper.include(changeFocusItemTransform, vh.itemView);
                        TransitionHelper.include(changeFocusItemBounds, vh.itemView);
                    }
                } else {
                    // going to slide this item to top / bottom.
                    TransitionHelper.include(slideAndFade, vh.itemView);
                    TransitionHelper.exclude(fade, vh.itemView, true);
                }
            }
            TransitionHelper.include(changeGridBounds, mSubActionsGridView);
            TransitionHelper.include(changeGridBounds, mSubActionsBackground);
            TransitionHelper.addTransition(set, slideAndFade);
            // note that we don't run ChangeBounds for activating view due to the rounding problem
            // of multiple level views ChangeBounds animation causing vertical jittering.
            if (isSubActionTransition) {
                TransitionHelper.addTransition(set, changeFocusItemTransform);
                TransitionHelper.addTransition(set, changeFocusItemBounds);
            }
            TransitionHelper.addTransition(set, fade);
            TransitionHelper.addTransition(set, changeGridBounds);
            mExpandTransition = set;
            TransitionHelper.addTransitionListener(mExpandTransition, new TransitionListener() {
                @Override
                public void onTransitionEnd(Object transition) {
                    mExpandTransition = null;
                }
            });
            if (isExpand && isSubActionTransition) {
                // To expand sub actions, move original position of sub actions to bottom of item
                int startY = avh.itemView.getBottom();
                mSubActionsGridView.offsetTopAndBottom(startY - mSubActionsGridView.getTop());
                mSubActionsBackground.offsetTopAndBottom(startY - mSubActionsBackground.getTop());
            }
            TransitionHelper.beginDelayedTransition(mMainView, mExpandTransition);
        }
        onUpdateExpandedViewHolder(avh);
        if (isSubActionTransition) {
            onUpdateSubActionsGridView(focusAvh.getAction(), isExpand);
        }
    }

    /**
     * @return True if sub actions list is expanded.
     */
    public boolean isSubActionsExpanded() {
        return mExpandedAction != null && mExpandedAction.hasSubActions();
    }

    /**
     * @return True if there is {@link #getExpandedAction()} is not null, false otherwise.
     */
    public boolean isExpanded() {
        return mExpandedAction != null;
    }

    /**
     * @return Current expanded GuidedAction or null if not expanded.
     */
    public GuidedAction getExpandedAction() {
        return mExpandedAction;
    }

    /**
     * Expand or collapse GuidedActionStylist.
     * @param avh When not null, the GuidedActionStylist expands the sub actions of avh.  When null
     * the GuidedActionStylist will collapse sub actions.
     */
    public void onUpdateExpandedViewHolder(ViewHolder avh) {

        // Note about setting the prune child flag back & forth here: without this, the actions that
        // go off the screen from the top or bottom become invisible forever. This is because once
        // an action is expanded, it takes more space which in turn kicks out some other actions
        // off of the screen. Once, this action is collapsed (after the second click) and the
        // visibility flag is set back to true for all existing actions,
        // the off-the-screen actions are pruned from the view, thus
        // could not be accessed, had we not disabled pruning prior to this.
        if (avh == null) {
            mExpandedAction = null;
            mActionsGridView.setPruneChild(true);
        } else if (avh.getAction() != mExpandedAction) {
            mExpandedAction = avh.getAction();
            mActionsGridView.setPruneChild(false);
        }
        // In expanding mode, notifyItemChange on expanded item will reset the translationY by
        // the default ItemAnimator.  So disable ItemAnimation in expanding mode.
        mActionsGridView.setAnimateChildLayout(false);
        final int count = mActionsGridView.getChildCount();
        for (int i = 0; i < count; i++) {
            ViewHolder vh = (ViewHolder) mActionsGridView
                    .getChildViewHolder(mActionsGridView.getChildAt(i));
            updateChevronAndVisibility(vh);
        }
    }

    void onUpdateSubActionsGridView(GuidedAction action, boolean expand) {
        if (mSubActionsGridView != null) {
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) mSubActionsGridView.getLayoutParams();
            GuidedActionAdapter adapter = (GuidedActionAdapter) mSubActionsGridView.getAdapter();
            if (expand) {
                // set to negative value so GuidedActionRelativeLayout will override with
                // keyLine percentage.
                lp.topMargin = -2;
                lp.height = ViewGroup.MarginLayoutParams.MATCH_PARENT;
                mSubActionsGridView.setLayoutParams(lp);
                mSubActionsGridView.setVisibility(View.VISIBLE);
                mSubActionsBackground.setVisibility(View.VISIBLE);
                mSubActionsGridView.requestFocus();
                adapter.setActions(action.getSubActions());
            } else {
                // set to explicit value, which will disable the keyLine percentage calculation
                // in GuidedRelativeLayout.
                int actionPosition = ((GuidedActionAdapter) mActionsGridView.getAdapter())
                        .indexOf(action);
                lp.topMargin = mActionsGridView.getLayoutManager()
                        .findViewByPosition(actionPosition).getBottom();
                lp.height = 0;
                mSubActionsGridView.setVisibility(View.INVISIBLE);
                mSubActionsBackground.setVisibility(View.INVISIBLE);
                mSubActionsGridView.setLayoutParams(lp);
                adapter.setActions(Collections.<GuidedAction>emptyList());
                mActionsGridView.requestFocus();
            }
        }
    }

    private void updateChevronAndVisibility(ViewHolder vh) {
        if (!vh.isSubAction()) {
            if (mExpandedAction == null) {
                vh.itemView.setVisibility(View.VISIBLE);
                vh.itemView.setTranslationY(0);
                if (vh.mActivatorView != null) {
                    vh.setActivated(false);
                }
            } else if (vh.getAction() == mExpandedAction) {
                vh.itemView.setVisibility(View.VISIBLE);
                if (vh.getAction().hasSubActions()) {
                    vh.itemView.setTranslationY(getKeyLine() - vh.itemView.getBottom());
                } else if (vh.mActivatorView != null) {
                    vh.itemView.setTranslationY(0);
                    vh.setActivated(true);
                }
            } else {
                vh.itemView.setVisibility(View.INVISIBLE);
                vh.itemView.setTranslationY(0);
            }
        }
        if (vh.mChevronView != null) {
            onBindChevronView(vh, vh.getAction());
        }
    }

    /*
     * ==========================================
     * FragmentAnimationProvider overrides
     * ==========================================
     */

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

    /*
     * ==========================================
     * Private methods
     * ==========================================
     */

    private static float getFloat(Context ctx, TypedValue typedValue, int attrId) {
        ctx.getTheme().resolveAttribute(attrId, typedValue, true);
        return typedValue.getFloat();
    }

    private static float getFloatValue(Resources resources, TypedValue typedValue, int resId) {
        resources.getValue(resId, typedValue, true);
        return typedValue.getFloat();
    }

    private static int getInteger(Context ctx, TypedValue typedValue, int attrId) {
        ctx.getTheme().resolveAttribute(attrId, typedValue, true);
        return ctx.getResources().getInteger(typedValue.resourceId);
    }

    private static int getDimension(Context ctx, TypedValue typedValue, int attrId) {
        ctx.getTheme().resolveAttribute(attrId, typedValue, true);
        return ctx.getResources().getDimensionPixelSize(typedValue.resourceId);
    }

    private boolean setIcon(final ImageView iconView, GuidedAction action) {
        Drawable icon = null;
        if (iconView != null) {
            icon = action.getIcon();
            if (icon != null) {
                // setImageDrawable resets the drawable's level unless we set the view level first.
                iconView.setImageLevel(icon.getLevel());
                iconView.setImageDrawable(icon);
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }
        return icon != null;
    }

    /**
     * @return the max height in pixels the description can be such that the
     *         action nicely takes up the entire screen.
     */
    private int getDescriptionMaxHeight(Context context, TextView title) {
        // The 2 multiplier on the title height calculation is a
        // conservative estimate for font padding which can not be
        // calculated at this stage since the view hasn't been rendered yet.
        return (int)(mDisplayHeight - 2*mVerticalPadding - 2*mTitleMaxLines*title.getLineHeight());
    }

}
