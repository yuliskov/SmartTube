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

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.FrameLayout;

import androidx.leanback.R;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Renders a {@link DetailsOverviewRow} to display an overview of an item. Typically this row will
 * be the first row in a fragment such as the
 * {@link androidx.leanback.app.DetailsFragment}. The View created by the
 * FullWidthDetailsOverviewRowPresenter is made in three parts: logo view on the left, action list view on
 * the top and a customizable detailed description view on the right.
 *
 * <p>The detailed description is rendered using a {@link Presenter} passed in
 * {@link #FullWidthDetailsOverviewRowPresenter(Presenter)}. Typically this will be an instance of
 * {@link AbstractDetailsDescriptionPresenter}. The application can access the detailed description
 * ViewHolder from {@link ViewHolder#getDetailsDescriptionViewHolder()}.
 * </p>
 *
 * <p>The logo view is rendered using a customizable {@link DetailsOverviewLogoPresenter} passed in
 * {@link #FullWidthDetailsOverviewRowPresenter(Presenter, DetailsOverviewLogoPresenter)}. The application
 * can access the logo ViewHolder from {@link ViewHolder#getLogoViewHolder()}.
 * </p>
 *
 * <p>
 * To support activity shared element transition, call {@link #setListener(Listener)} with
 * {@link FullWidthDetailsOverviewSharedElementHelper} during Activity's onCreate(). Application is free to
 * create its own "shared element helper" class using the Listener for image binding.
 * Call {@link #setParticipatingEntranceTransition(boolean)} with false
 * </p>
 *
 * <p>
 * The view has three states: {@link #STATE_HALF} {@link #STATE_FULL} and {@link #STATE_SMALL}. See
 * {@link androidx.leanback.app.DetailsFragment} where it switches states based on
 * selected row position.
 * </p>
 */
public class FullWidthDetailsOverviewRowPresenter extends RowPresenter {

    static final String TAG = "FullWidthDetailsRP";
    static final boolean DEBUG = false;

    private static Rect sTmpRect = new Rect();
    static final Handler sHandler = new Handler();

    /**
     * This is the default state corresponding to layout file.  The view takes full width
     * of screen and covers bottom half of the screen.
     */
    public static final int STATE_HALF = 0;
    /**
     * This is the state when the view covers full width and height of screen.
     */
    public static final int STATE_FULL = 1;
    /**
     * This is the state where the view shrinks to a small banner.
     */
    public static final int STATE_SMALL = 2;

    /**
     * This is the alignment mode that the logo and description align to the starting edge of the
     * overview view.
     */
    public static final int ALIGN_MODE_START = 0;
    /**
     * This is the alignment mode that the ending edge of logo and the starting edge of description
     * align to the middle of the overview view. Note that this might not be the exact horizontal
     * center of the overview view.
     */
    public static final int ALIGN_MODE_MIDDLE = 1;

    /**
     * Listeners for events on ViewHolder.
     */
    public static abstract class Listener {

        /**
         * {@link FullWidthDetailsOverviewRowPresenter#notifyOnBindLogo(ViewHolder)} is called.
         * @param vh  The ViewHolder that has bound logo view.
         */
        public void onBindLogo(ViewHolder vh) {
        }

    }

    class ActionsItemBridgeAdapter extends ItemBridgeAdapter {
        FullWidthDetailsOverviewRowPresenter.ViewHolder mViewHolder;

        ActionsItemBridgeAdapter(FullWidthDetailsOverviewRowPresenter.ViewHolder viewHolder) {
            mViewHolder = viewHolder;
        }

        @Override
        public void onBind(final ItemBridgeAdapter.ViewHolder ibvh) {
            if (mViewHolder.getOnItemViewClickedListener() != null
                    || mActionClickedListener != null) {
                ibvh.getPresenter().setOnClickListener(
                        ibvh.getViewHolder(), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (mViewHolder.getOnItemViewClickedListener() != null) {
                                    mViewHolder.getOnItemViewClickedListener().onItemClicked(
                                            ibvh.getViewHolder(), ibvh.getItem(),
                                            mViewHolder, mViewHolder.getRow());
                                }
                                if (mActionClickedListener != null) {
                                    mActionClickedListener.onActionClicked((Action) ibvh.getItem());
                                }
                            }
                        });
            }
        }
        @Override
        public void onUnbind(final ItemBridgeAdapter.ViewHolder ibvh) {
            if (mViewHolder.getOnItemViewClickedListener() != null
                    || mActionClickedListener != null) {
                ibvh.getPresenter().setOnClickListener(ibvh.getViewHolder(), null);
            }
        }
        @Override
        public void onAttachedToWindow(ItemBridgeAdapter.ViewHolder viewHolder) {
            // Remove first to ensure we don't add ourselves more than once.
            viewHolder.itemView.removeOnLayoutChangeListener(mViewHolder.mLayoutChangeListener);
            viewHolder.itemView.addOnLayoutChangeListener(mViewHolder.mLayoutChangeListener);
        }
        @Override
        public void onDetachedFromWindow(ItemBridgeAdapter.ViewHolder viewHolder) {
            viewHolder.itemView.removeOnLayoutChangeListener(mViewHolder.mLayoutChangeListener);
            mViewHolder.checkFirstAndLastPosition(false);
        }
    }

    /**
     * A ViewHolder for the DetailsOverviewRow.
     */
    public class ViewHolder extends RowPresenter.ViewHolder {

        protected final DetailsOverviewRow.Listener mRowListener = createRowListener();

        protected DetailsOverviewRow.Listener createRowListener() {
            return new DetailsOverviewRowListener();
        }

        public class DetailsOverviewRowListener extends DetailsOverviewRow.Listener {
            @Override
            public void onImageDrawableChanged(DetailsOverviewRow row) {
                sHandler.removeCallbacks(mUpdateDrawableCallback);
                sHandler.post(mUpdateDrawableCallback);
            }

            @Override
            public void onItemChanged(DetailsOverviewRow row) {
                if (mDetailsDescriptionViewHolder != null) {
                    mDetailsPresenter.onUnbindViewHolder(mDetailsDescriptionViewHolder);
                }
                mDetailsPresenter.onBindViewHolder(mDetailsDescriptionViewHolder, row.getItem());
            }

            @Override
            public void onActionsAdapterChanged(DetailsOverviewRow row) {
                bindActions(row.getActionsAdapter());
            }
        };

        final ViewGroup mOverviewRoot;
        final FrameLayout mOverviewFrame;
        final ViewGroup mDetailsDescriptionFrame;
        final HorizontalGridView mActionsRow;
        final Presenter.ViewHolder mDetailsDescriptionViewHolder;
        final DetailsOverviewLogoPresenter.ViewHolder mDetailsLogoViewHolder;
        int mNumItems;
        ItemBridgeAdapter mActionBridgeAdapter;
        int mState = STATE_HALF;

        final Runnable mUpdateDrawableCallback = new Runnable() {
            @Override
            public void run() {
                Row row = getRow();
                if (row == null) {
                    return;
                }
                mDetailsOverviewLogoPresenter.onBindViewHolder(mDetailsLogoViewHolder, row);
            }
        };

        void bindActions(ObjectAdapter adapter) {
            mActionBridgeAdapter.setAdapter(adapter);
            mActionsRow.setAdapter(mActionBridgeAdapter);
            mNumItems = mActionBridgeAdapter.getItemCount();

        }

        void onBind() {
            DetailsOverviewRow row = (DetailsOverviewRow) getRow();
            bindActions(row.getActionsAdapter());
            row.addListener(mRowListener);
        }

        void onUnbind() {
            DetailsOverviewRow row = (DetailsOverviewRow) getRow();
            row.removeListener(mRowListener);
            sHandler.removeCallbacks(mUpdateDrawableCallback);
        }

        final View.OnLayoutChangeListener mLayoutChangeListener =
                new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                    int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (DEBUG) Log.v(TAG, "onLayoutChange " + v);
                checkFirstAndLastPosition(false);
            }
        };

        final OnChildSelectedListener mChildSelectedListener = new OnChildSelectedListener() {
            @Override
            public void onChildSelected(ViewGroup parent, View view, int position, long id) {
                dispatchItemSelection(view);
            }
        };

        void dispatchItemSelection(View view) {
            if (!isSelected()) {
                return;
            }
            ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder) (view != null
                    ? mActionsRow.getChildViewHolder(view)
                    : mActionsRow.findViewHolderForPosition(mActionsRow.getSelectedPosition()));
            if (ibvh == null) {
                if (getOnItemViewSelectedListener() != null) {
                    getOnItemViewSelectedListener().onItemSelected(null, null,
                            ViewHolder.this, getRow());
                }
            } else {
                if (getOnItemViewSelectedListener() != null) {
                    getOnItemViewSelectedListener().onItemSelected(ibvh.getViewHolder(), ibvh.getItem(),
                            ViewHolder.this, getRow());
                }
            }
        };

        final RecyclerView.OnScrollListener mScrollListener =
                new RecyclerView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                checkFirstAndLastPosition(true);
            }
        };

        private int getViewCenter(View view) {
            return (view.getRight() - view.getLeft()) / 2;
        }

        void checkFirstAndLastPosition(boolean fromScroll) {
            RecyclerView.ViewHolder viewHolder;

            viewHolder = mActionsRow.findViewHolderForPosition(mNumItems - 1);
            boolean showRight = (viewHolder == null
                    || viewHolder.itemView.getRight() > mActionsRow.getWidth());

            viewHolder = mActionsRow.findViewHolderForPosition(0);
            boolean showLeft = (viewHolder == null || viewHolder.itemView.getLeft() < 0);

            if (DEBUG) {
                Log.v(TAG, "checkFirstAndLast fromScroll " + fromScroll
                        + " showRight " + showRight + " showLeft " + showLeft);
            }

        }

        /**
         * Constructor for the ViewHolder.
         *
         * @param rootView The root View that this view holder will be attached
         *        to.
         */
        public ViewHolder(View rootView, Presenter detailsPresenter,
                DetailsOverviewLogoPresenter logoPresenter) {
            super(rootView);
            mOverviewRoot = (ViewGroup) rootView.findViewById(R.id.details_root);
            mOverviewFrame = (FrameLayout) rootView.findViewById(R.id.details_frame);
            mDetailsDescriptionFrame =
                    (ViewGroup) rootView.findViewById(R.id.details_overview_description);
            mActionsRow =
                    (HorizontalGridView) mOverviewFrame.findViewById(R.id.details_overview_actions);
            mActionsRow.setHasOverlappingRendering(false);
            mActionsRow.setOnScrollListener(mScrollListener);
            mActionsRow.setAdapter(mActionBridgeAdapter);
            mActionsRow.setOnChildSelectedListener(mChildSelectedListener);

            final int fadeLength = rootView.getResources().getDimensionPixelSize(
                    R.dimen.lb_details_overview_actions_fade_size);
            mActionsRow.setFadingRightEdgeLength(fadeLength);
            mActionsRow.setFadingLeftEdgeLength(fadeLength);
            mDetailsDescriptionViewHolder =
                    detailsPresenter.onCreateViewHolder(mDetailsDescriptionFrame);
            mDetailsDescriptionFrame.addView(mDetailsDescriptionViewHolder.view);
            mDetailsLogoViewHolder = (DetailsOverviewLogoPresenter.ViewHolder)
                    logoPresenter.onCreateViewHolder(mOverviewRoot);
            mOverviewRoot.addView(mDetailsLogoViewHolder.view);
        }

        /**
         * Returns the rectangle area with a color background.
         */
        public final ViewGroup getOverviewView() {
            return mOverviewFrame;
        }

        /**
         * Returns the ViewHolder for logo.
         */
        public final DetailsOverviewLogoPresenter.ViewHolder getLogoViewHolder() {
            return mDetailsLogoViewHolder;
        }

        /**
         * Returns the ViewHolder for DetailsDescription.
         */
        public final Presenter.ViewHolder getDetailsDescriptionViewHolder() {
            return mDetailsDescriptionViewHolder;
        }

        /**
         * Returns the root view for inserting details description.
         */
        public final ViewGroup getDetailsDescriptionFrame() {
            return mDetailsDescriptionFrame;
        }

        /**
         * Returns the view of actions row.
         */
        public final ViewGroup getActionsRow() {
            return mActionsRow;
        }

        /**
         * Returns current state of the ViewHolder set by
         * {@link FullWidthDetailsOverviewRowPresenter#setState(ViewHolder, int)}.
         */
        public final int getState() {
            return mState;
        }
    }

    protected int mInitialState = STATE_HALF;

    final Presenter mDetailsPresenter;
    final DetailsOverviewLogoPresenter mDetailsOverviewLogoPresenter;
    OnActionClickedListener mActionClickedListener;

    private int mBackgroundColor = Color.TRANSPARENT;
    private int mActionsBackgroundColor = Color.TRANSPARENT;
    private boolean mBackgroundColorSet;
    private boolean mActionsBackgroundColorSet;

    private Listener mListener;
    private boolean mParticipatingEntranceTransition;

    private int mAlignmentMode;

    /**
     * Constructor for a FullWidthDetailsOverviewRowPresenter.
     *
     * @param detailsPresenter The {@link Presenter} used to render the detailed
     *        description of the row.
     */
    public FullWidthDetailsOverviewRowPresenter(Presenter detailsPresenter) {
        this(detailsPresenter, new DetailsOverviewLogoPresenter());
    }

    /**
     * Constructor for a FullWidthDetailsOverviewRowPresenter.
     *
     * @param detailsPresenter The {@link Presenter} used to render the detailed
     *        description of the row.
     * @param logoPresenter  The {@link Presenter} used to render the logo view.
     */
    public FullWidthDetailsOverviewRowPresenter(Presenter detailsPresenter,
            DetailsOverviewLogoPresenter logoPresenter) {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);
        mDetailsPresenter = detailsPresenter;
        mDetailsOverviewLogoPresenter = logoPresenter;
    }

    /**
     * Sets the listener for Action click events.
     */
    public void setOnActionClickedListener(OnActionClickedListener listener) {
        mActionClickedListener = listener;
    }

    /**
     * Returns the listener for Action click events.
     */
    public OnActionClickedListener getOnActionClickedListener() {
        return mActionClickedListener;
    }

    /**
     * Sets the background color.  If not set, a default from the theme will be used.
     */
    public final void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackgroundColorSet = true;
    }

    /**
     * Returns the background color.  If {@link #setBackgroundColor(int)}, transparent
     * is returned.
     */
    public final int getBackgroundColor() {
        return mBackgroundColor;
    }

    /**
     * Sets the background color for Action Bar.  If not set, a default from the theme will be
     * used.
     */
    public final void setActionsBackgroundColor(int color) {
        mActionsBackgroundColor = color;
        mActionsBackgroundColorSet = true;
    }

    /**
     * Returns the background color of actions.  If {@link #setActionsBackgroundColor(int)}
     * is not called,  transparent is returned.
     */
    public final int getActionsBackgroundColor() {
        return mActionsBackgroundColor;
    }

    /**
     * Returns true if the overview should be part of shared element transition.
     */
    public final boolean isParticipatingEntranceTransition() {
        return mParticipatingEntranceTransition;
    }

    /**
     * Sets if the overview should be part of shared element transition.
     */
    public final void setParticipatingEntranceTransition(boolean participating) {
        mParticipatingEntranceTransition = participating;
    }

    /**
     * Change the initial state used to create ViewHolder.
     */
    public final void setInitialState(int state) {
        mInitialState = state;
    }

    /**
     * Returns the initial state used to create ViewHolder.
     */
    public final int getInitialState() {
        return mInitialState;
    }

    /**
     * Set alignment mode of Description.
     *
     * @param alignmentMode  One of {@link #ALIGN_MODE_MIDDLE} or {@link #ALIGN_MODE_START}
     */
    public final void setAlignmentMode(int alignmentMode) {
        mAlignmentMode = alignmentMode;
    }

    /**
     * Returns alignment mode of Description.
     *
     * @return  One of {@link #ALIGN_MODE_MIDDLE} or {@link #ALIGN_MODE_START}.
     */
    public final int getAlignmentMode() {
        return mAlignmentMode;
    }

    @Override
    protected boolean isClippingChildren() {
        return true;
    }

    /**
     * Set listener for details overview presenter. Must be called before creating
     * ViewHolder.
     */
    public final void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Get resource id to inflate the layout.  The layout must match {@link #STATE_HALF}
     */
    protected int getLayoutResourceId() {
        return R.layout.lb_fullwidth_details_overview;
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(getLayoutResourceId(), parent, false);
        final ViewHolder vh = new ViewHolder(v, mDetailsPresenter, mDetailsOverviewLogoPresenter);
        mDetailsOverviewLogoPresenter.setContext(vh.mDetailsLogoViewHolder, vh, this);
        setState(vh, mInitialState);

        vh.mActionBridgeAdapter = new ActionsItemBridgeAdapter(vh);
        final View overview = vh.mOverviewFrame;
        if (mBackgroundColorSet) {
            overview.setBackgroundColor(mBackgroundColor);
        }
        if (mActionsBackgroundColorSet) {
            overview.findViewById(R.id.details_overview_actions_background)
                    .setBackgroundColor(mActionsBackgroundColor);
        }
        RoundedRectHelper.setClipToRoundedOutline(overview, true);

        if (!getSelectEffectEnabled()) {
            vh.mOverviewFrame.setForeground(null);
        }

        vh.mActionsRow.setOnUnhandledKeyListener(new BaseGridView.OnUnhandledKeyListener() {
            @Override
            public boolean onUnhandledKey(KeyEvent event) {
                if (vh.getOnKeyListener() != null) {
                    if (vh.getOnKeyListener().onKey(vh.view, event.getKeyCode(), event)) {
                        return true;
                    }
                }
                return false;
            }
        });
        return vh;
    }

    private static int getNonNegativeWidth(Drawable drawable) {
        final int width = (drawable == null) ? 0 : drawable.getIntrinsicWidth();
        return (width > 0 ? width : 0);
    }

    private static int getNonNegativeHeight(Drawable drawable) {
        final int height = (drawable == null) ? 0 : drawable.getIntrinsicHeight();
        return (height > 0 ? height : 0);
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        DetailsOverviewRow row = (DetailsOverviewRow) item;
        ViewHolder vh = (ViewHolder) holder;

        mDetailsOverviewLogoPresenter.onBindViewHolder(vh.mDetailsLogoViewHolder, row);
        mDetailsPresenter.onBindViewHolder(vh.mDetailsDescriptionViewHolder, row.getItem());
        vh.onBind();
    }

    @Override
    protected void onUnbindRowViewHolder(RowPresenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        vh.onUnbind();
        mDetailsPresenter.onUnbindViewHolder(vh.mDetailsDescriptionViewHolder);
        mDetailsOverviewLogoPresenter.onUnbindViewHolder(vh.mDetailsLogoViewHolder);
        super.onUnbindRowViewHolder(holder);
    }

    @Override
    public final boolean isUsingDefaultSelectEffect() {
        return false;
    }

    @Override
    protected void onSelectLevelChanged(RowPresenter.ViewHolder holder) {
        super.onSelectLevelChanged(holder);
        if (getSelectEffectEnabled()) {
            ViewHolder vh = (ViewHolder) holder;
            int dimmedColor = vh.mColorDimmer.getPaint().getColor();
            ((ColorDrawable) vh.mOverviewFrame.getForeground().mutate()).setColor(dimmedColor);
        }
    }

    @Override
    protected void onRowViewAttachedToWindow(RowPresenter.ViewHolder vh) {
        super.onRowViewAttachedToWindow(vh);
        ViewHolder viewHolder = (ViewHolder) vh;
        mDetailsPresenter.onViewAttachedToWindow(viewHolder.mDetailsDescriptionViewHolder);
        mDetailsOverviewLogoPresenter.onViewAttachedToWindow(viewHolder.mDetailsLogoViewHolder);
    }

    @Override
    protected void onRowViewDetachedFromWindow(RowPresenter.ViewHolder vh) {
        super.onRowViewDetachedFromWindow(vh);
        ViewHolder viewHolder = (ViewHolder) vh;
        mDetailsPresenter.onViewDetachedFromWindow(viewHolder.mDetailsDescriptionViewHolder);
        mDetailsOverviewLogoPresenter.onViewDetachedFromWindow(viewHolder.mDetailsLogoViewHolder);
    }

    /**
     * Called by {@link DetailsOverviewLogoPresenter} to notify logo was bound to view.
     * Application should not directly call this method.
     * @param viewHolder  The row ViewHolder that has logo bound to view.
     */
    public final void notifyOnBindLogo(ViewHolder viewHolder) {
        onLayoutOverviewFrame(viewHolder, viewHolder.getState(), true);
        onLayoutLogo(viewHolder, viewHolder.getState(), true);
        if (mListener != null) {
            mListener.onBindLogo(viewHolder);
        }
    }

    /**
     * Layout logo position based on current state.  Subclass may override.
     * The method is called when a logo is bound to view or state changes.
     * @param viewHolder  The row ViewHolder that contains the logo.
     * @param oldState    The old state,  can be same as current viewHolder.getState()
     * @param logoChanged Whether logo was changed.
     */
    protected void onLayoutLogo(ViewHolder viewHolder, int oldState, boolean logoChanged) {
        View v = viewHolder.getLogoViewHolder().view;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                v.getLayoutParams();
        switch (mAlignmentMode) {
            case ALIGN_MODE_START:
            default:
                lp.setMarginStart(v.getResources().getDimensionPixelSize(
                        R.dimen.lb_details_v2_logo_margin_start));
                break;
            case ALIGN_MODE_MIDDLE:
                lp.setMarginStart(v.getResources().getDimensionPixelSize(R.dimen.lb_details_v2_left)
                        - lp.width);
                break;
        }

        switch (viewHolder.getState()) {
        case STATE_FULL:
        default:
            lp.topMargin =
                    v.getResources().getDimensionPixelSize(R.dimen.lb_details_v2_blank_height)
                    - lp.height / 2;
            break;
        case STATE_HALF:
            lp.topMargin = v.getResources().getDimensionPixelSize(
                    R.dimen.lb_details_v2_blank_height) + v.getResources()
                    .getDimensionPixelSize(R.dimen.lb_details_v2_actions_height) + v
                    .getResources().getDimensionPixelSize(
                    R.dimen.lb_details_v2_description_margin_top);
            break;
        case STATE_SMALL:
            lp.topMargin = 0;
            break;
        }
        v.setLayoutParams(lp);
    }

    /**
     * Layout overview frame based on current state.  Subclass may override.
     * The method is called when a logo is bound to view or state changes.
     * @param viewHolder  The row ViewHolder that contains the logo.
     * @param oldState    The old state,  can be same as current viewHolder.getState()
     * @param logoChanged Whether logo was changed.
     */
    protected void onLayoutOverviewFrame(ViewHolder viewHolder, int oldState, boolean logoChanged) {
        boolean wasBanner = oldState == STATE_SMALL;
        boolean isBanner = viewHolder.getState() == STATE_SMALL;
        if (wasBanner != isBanner || logoChanged) {
            Resources res = viewHolder.view.getResources();

            int frameMarginStart;
            int descriptionMarginStart = 0;
            int logoWidth = 0;
            if (mDetailsOverviewLogoPresenter.isBoundToImage(viewHolder.getLogoViewHolder(),
                    (DetailsOverviewRow) viewHolder.getRow())) {
                logoWidth = viewHolder.getLogoViewHolder().view.getLayoutParams().width;
            }
            switch (mAlignmentMode) {
                case ALIGN_MODE_START:
                default:
                    if (isBanner) {
                        frameMarginStart = res.getDimensionPixelSize(
                                R.dimen.lb_details_v2_logo_margin_start);
                        descriptionMarginStart = logoWidth;
                    } else {
                        frameMarginStart = 0;
                        descriptionMarginStart = logoWidth + res.getDimensionPixelSize(
                                R.dimen.lb_details_v2_logo_margin_start);
                    }
                    break;
                case ALIGN_MODE_MIDDLE:
                    if (isBanner) {
                        frameMarginStart = res.getDimensionPixelSize(R.dimen.lb_details_v2_left)
                                - logoWidth;
                        descriptionMarginStart = logoWidth;
                    } else {
                        frameMarginStart = 0;
                        descriptionMarginStart = res.getDimensionPixelSize(
                                R.dimen.lb_details_v2_left);
                    }
                    break;
            }
            MarginLayoutParams lpFrame =
                    (MarginLayoutParams) viewHolder.getOverviewView().getLayoutParams();
            lpFrame.topMargin = isBanner ? 0
                    : res.getDimensionPixelSize(R.dimen.lb_details_v2_blank_height);
            lpFrame.leftMargin = lpFrame.rightMargin = frameMarginStart;
            viewHolder.getOverviewView().setLayoutParams(lpFrame);

            View description = viewHolder.getDetailsDescriptionFrame();
            MarginLayoutParams lpDesc = (MarginLayoutParams) description.getLayoutParams();
            lpDesc.setMarginStart(descriptionMarginStart);
            description.setLayoutParams(lpDesc);

            View action = viewHolder.getActionsRow();
            MarginLayoutParams lpActions = (MarginLayoutParams) action.getLayoutParams();
            lpActions.setMarginStart(descriptionMarginStart);
            lpActions.height =
                    isBanner ? 0 : res.getDimensionPixelSize(R.dimen.lb_details_v2_actions_height);
            action.setLayoutParams(lpActions);
        }
    }

    /**
     * Switch state of a ViewHolder.
     * @param viewHolder   The ViewHolder to change state.
     * @param state        New state, can be {@link #STATE_FULL}, {@link #STATE_HALF}
     *                     or {@link #STATE_SMALL}.
     */
    public final void setState(ViewHolder viewHolder, int state) {
        if (viewHolder.getState() != state) {
            int oldState = viewHolder.getState();
            viewHolder.mState = state;
            onStateChanged(viewHolder, oldState);
        }
    }

    /**
     * Called when {@link ViewHolder#getState()} changes.  Subclass may override.
     * The default implementation calls {@link #onLayoutLogo(ViewHolder, int, boolean)} and
     * {@link #onLayoutOverviewFrame(ViewHolder, int, boolean)}.
     * @param viewHolder   The ViewHolder which state changed.
     * @param oldState     The old state.
     */
    protected void onStateChanged(ViewHolder viewHolder, int oldState) {
        onLayoutOverviewFrame(viewHolder, oldState, false);
        onLayoutLogo(viewHolder, oldState, false);
    }

    @Override
    public void setEntranceTransitionState(RowPresenter.ViewHolder holder,
            boolean afterEntrance) {
        super.setEntranceTransitionState(holder, afterEntrance);
        if (mParticipatingEntranceTransition) {
            holder.view.setVisibility(afterEntrance? View.VISIBLE : View.INVISIBLE);
        }
    }
}
