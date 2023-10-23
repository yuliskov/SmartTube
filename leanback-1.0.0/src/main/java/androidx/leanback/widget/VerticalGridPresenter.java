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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.leanback.R;
import androidx.leanback.system.Settings;
import androidx.leanback.transition.TransitionHelper;

/**
 * A presenter that renders objects in a {@link VerticalGridView}.
 */
public class VerticalGridPresenter extends Presenter {
    private static final String TAG = "GridPresenter";
    private static final boolean DEBUG = false;

    class VerticalGridItemBridgeAdapter extends ItemBridgeAdapter {
        @Override
        protected void onCreate(ItemBridgeAdapter.ViewHolder viewHolder) {
            if (viewHolder.itemView instanceof ViewGroup) {
                TransitionHelper.setTransitionGroup((ViewGroup) viewHolder.itemView,
                        true);
            }
            if (mShadowOverlayHelper != null) {
                mShadowOverlayHelper.onViewCreated(viewHolder.itemView);
            }
        }

        @Override
        public void onBind(final ItemBridgeAdapter.ViewHolder itemViewHolder) {
            // Only when having an OnItemClickListener, we attach the OnClickListener.
            if (getOnItemViewClickedListener() != null) {
                final View itemView = itemViewHolder.mHolder.view;
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (getOnItemViewClickedListener() != null) {
                            // Row is always null
                            getOnItemViewClickedListener().onItemClicked(
                                    itemViewHolder.mHolder, itemViewHolder.mItem, null, null);
                        }
                    }
                });
            }
        }

        @Override
        public void onUnbind(ItemBridgeAdapter.ViewHolder viewHolder) {
            if (getOnItemViewClickedListener() != null) {
                viewHolder.mHolder.view.setOnClickListener(null);
            }
        }

        @Override
        public void onAttachedToWindow(ItemBridgeAdapter.ViewHolder viewHolder) {
            viewHolder.itemView.setActivated(true);
        }
    }

    /**
     * ViewHolder for the VerticalGridPresenter.
     */
    public static class ViewHolder extends Presenter.ViewHolder {
        ItemBridgeAdapter mItemBridgeAdapter;
        final VerticalGridView mGridView;
        boolean mInitialized;

        public ViewHolder(VerticalGridView view) {
            super(view);
            mGridView = view;
        }

        public VerticalGridView getGridView() {
            return mGridView;
        }
    }

    private int mNumColumns = -1;
    private int mFocusZoomFactor;
    private boolean mUseFocusDimmer;
    private boolean mShadowEnabled = true;
    private boolean mKeepChildForeground = true;
    private OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;
    private boolean mRoundedCornersEnabled = true;
    ShadowOverlayHelper mShadowOverlayHelper;
    private ItemBridgeAdapter.Wrapper mShadowOverlayWrapper;

    /**
     * Constructs a VerticalGridPresenter with defaults.
     * Uses {@link FocusHighlight#ZOOM_FACTOR_MEDIUM} for focus zooming and
     * enabled dimming on focus.
     */
    public VerticalGridPresenter() {
        this(FocusHighlight.ZOOM_FACTOR_LARGE);
    }

    /**
     * Constructs a VerticalGridPresenter with the given parameters.
     *
     * @param focusZoomFactor Controls the zoom factor used when an item view is focused. One of
     *         {@link FocusHighlight#ZOOM_FACTOR_NONE},
     *         {@link FocusHighlight#ZOOM_FACTOR_SMALL},
     *         {@link FocusHighlight#ZOOM_FACTOR_XSMALL},
     *         {@link FocusHighlight#ZOOM_FACTOR_MEDIUM},
     *         {@link FocusHighlight#ZOOM_FACTOR_LARGE}
     * enabled dimming on focus.
     */
    public VerticalGridPresenter(int focusZoomFactor) {
        this(focusZoomFactor, true);
    }

    /**
     * Constructs a VerticalGridPresenter with the given parameters.
     *
     * @param focusZoomFactor Controls the zoom factor used when an item view is focused. One of
     *         {@link FocusHighlight#ZOOM_FACTOR_NONE},
     *         {@link FocusHighlight#ZOOM_FACTOR_SMALL},
     *         {@link FocusHighlight#ZOOM_FACTOR_XSMALL},
     *         {@link FocusHighlight#ZOOM_FACTOR_MEDIUM},
     *         {@link FocusHighlight#ZOOM_FACTOR_LARGE}
     * @param useFocusDimmer determines if the FocusHighlighter will use the dimmer
     */
    public VerticalGridPresenter(int focusZoomFactor, boolean useFocusDimmer) {
        mFocusZoomFactor = focusZoomFactor;
        mUseFocusDimmer = useFocusDimmer;
    }

    /**
     * Sets the number of columns in the vertical grid.
     */
    public void setNumberOfColumns(int numColumns) {
        if (numColumns < 0) {
            throw new IllegalArgumentException("Invalid number of columns");
        }
        if (mNumColumns != numColumns) {
            mNumColumns = numColumns;
        }
    }

    /**
     * Returns the number of columns in the vertical grid.
     */
    public int getNumberOfColumns() {
        return mNumColumns;
    }

    /**
     * Enable or disable child shadow.
     * This is not only for enable/disable default shadow implementation but also subclass must
     * respect this flag.
     */
    public final void setShadowEnabled(boolean enabled) {
        mShadowEnabled = enabled;
    }

    /**
     * Returns true if child shadow is enabled.
     * This is not only for enable/disable default shadow implementation but also subclass must
     * respect this flag.
     */
    public final boolean getShadowEnabled() {
        return mShadowEnabled;
    }

    /**
     * Default implementation returns true if SDK version >= 21, shadow (either static or z-order
     * based) will be applied to each individual child of {@link VerticalGridView}.
     * Subclass may return false to disable default implementation of shadow and provide its own.
     */
    public boolean isUsingDefaultShadow() {
        return ShadowOverlayHelper.supportsShadow();
    }

    /**
     * Enables or disabled rounded corners on children of this row.
     * Supported on Android SDK >= L.
     */
    public final void enableChildRoundedCorners(boolean enable) {
        mRoundedCornersEnabled = enable;
    }

    /**
     * Returns true if rounded corners are enabled for children of this row.
     */
    public final boolean areChildRoundedCornersEnabled() {
        return mRoundedCornersEnabled;
    }

    /**
     * Returns true if SDK >= L, where Z shadow is enabled so that Z order is enabled
     * on each child of vertical grid.   If subclass returns false in isUsingDefaultShadow()
     * and does not use Z-shadow on SDK >= L, it should override isUsingZOrder() return false.
     */
    public boolean isUsingZOrder(Context context) {
        return !Settings.getInstance(context).preferStaticShadows();
    }

    final boolean needsDefaultShadow() {
        return isUsingDefaultShadow() && getShadowEnabled();
    }

    /**
     * Returns the zoom factor used for focus highlighting.
     */
    public final int getFocusZoomFactor() {
        return mFocusZoomFactor;
    }

    /**
     * Returns true if the focus dimmer is used for focus highlighting; false otherwise.
     */
    public final boolean isFocusDimmerUsed() {
        return mUseFocusDimmer;
    }

    @Override
    public final ViewHolder onCreateViewHolder(ViewGroup parent) {
        ViewHolder vh = createGridViewHolder(parent);
        vh.mInitialized = false;
        vh.mItemBridgeAdapter = new VerticalGridItemBridgeAdapter();
        initializeGridViewHolder(vh);
        if (!vh.mInitialized) {
            throw new RuntimeException("super.initializeGridViewHolder() must be called");
        }
        return vh;
    }

    /**
     * Subclass may override this to inflate a different layout.
     */
    protected ViewHolder createGridViewHolder(ViewGroup parent) {
        View root = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.lb_vertical_grid, parent, false);
        return new ViewHolder((VerticalGridView) root.findViewById(R.id.browse_grid));
    }

    /**
     * Called after a {@link VerticalGridPresenter.ViewHolder} is created.
     * Subclasses may override this method and start by calling
     * super.initializeGridViewHolder(ViewHolder).
     *
     * @param vh The ViewHolder to initialize for the vertical grid.
     */
    protected void initializeGridViewHolder(ViewHolder vh) {
        if (mNumColumns == -1) {
            throw new IllegalStateException("Number of columns must be set");
        }
        if (DEBUG) Log.v(TAG, "mNumColumns " + mNumColumns);
        vh.getGridView().setNumColumns(mNumColumns);
        vh.mInitialized = true;

        Context context = vh.mGridView.getContext();
        if (mShadowOverlayHelper == null) {
            mShadowOverlayHelper = new ShadowOverlayHelper.Builder()
                    .needsOverlay(mUseFocusDimmer)
                    .needsShadow(needsDefaultShadow())
                    .needsRoundedCorner(areChildRoundedCornersEnabled())
                    .preferZOrder(isUsingZOrder(context))
                    .keepForegroundDrawable(mKeepChildForeground)
                    .options(createShadowOverlayOptions())
                    .build(context);
            if (mShadowOverlayHelper.needsWrapper()) {
                mShadowOverlayWrapper = new ItemBridgeAdapterShadowOverlayWrapper(
                        mShadowOverlayHelper);
            }
        }
        vh.mItemBridgeAdapter.setWrapper(mShadowOverlayWrapper);
        mShadowOverlayHelper.prepareParentForShadow(vh.mGridView);
        vh.getGridView().setFocusDrawingOrderEnabled(mShadowOverlayHelper.getShadowType()
                != ShadowOverlayHelper.SHADOW_DYNAMIC);
        FocusHighlightHelper.setupBrowseItemFocusHighlight(vh.mItemBridgeAdapter,
                mFocusZoomFactor, mUseFocusDimmer);

        final ViewHolder gridViewHolder = vh;
        vh.getGridView().setOnChildSelectedListener(new OnChildSelectedListener() {
            @Override
            public void onChildSelected(ViewGroup parent, View view, int position, long id) {
                selectChildView(gridViewHolder, view);
            }
        });
    }

    /**
     * Set if keeps foreground of child of this grid, the foreground will not
     * be used for overlay color.  Default value is true.
     *
     * @param keep   True if keep foreground of child of this grid.
     */
    public final void setKeepChildForeground(boolean keep) {
        mKeepChildForeground = keep;
    }

    /**
     * Returns true if keeps foreground of child of this grid, the foreground will not
     * be used for overlay color.  Default value is true.
     *
     * @return   True if keeps foreground of child of this grid.
     */
    public final boolean getKeepChildForeground() {
        return mKeepChildForeground;
    }

    /**
     * Create ShadowOverlayHelper Options.  Subclass may override.
     * e.g.
     * <code>
     * return new ShadowOverlayHelper.Options().roundedCornerRadius(10);
     * </code>
     *
     * @return   The options to be used for shadow, overlay and rounded corner.
     */
    protected ShadowOverlayHelper.Options createShadowOverlayOptions() {
        return ShadowOverlayHelper.Options.DEFAULT;
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        if (DEBUG) Log.v(TAG, "onBindViewHolder " + item);
        ViewHolder vh = (ViewHolder) viewHolder;
        vh.mItemBridgeAdapter.setAdapter((ObjectAdapter) item);
        vh.getGridView().setAdapter(vh.mItemBridgeAdapter);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        if (DEBUG) Log.v(TAG, "onUnbindViewHolder");
        ViewHolder vh = (ViewHolder) viewHolder;
        vh.mItemBridgeAdapter.setAdapter(null);
        vh.getGridView().setAdapter(null);
    }

    /**
     * Sets the item selected listener.
     * Since this is a grid the row parameter is always null.
     */
    public final void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;
    }

    /**
     * Returns the item selected listener.
     */
    public final OnItemViewSelectedListener getOnItemViewSelectedListener() {
        return mOnItemViewSelectedListener;
    }

    /**
     * Sets the item clicked listener.
     * OnItemViewClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general, developer should choose one of the listeners but not both.
     */
    public final void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
    }

    /**
     * Returns the item clicked listener.
     */
    public final OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    void selectChildView(ViewHolder vh, View view) {
        if (getOnItemViewSelectedListener() != null) {
            ItemBridgeAdapter.ViewHolder ibh = (view == null) ? null :
                    (ItemBridgeAdapter.ViewHolder) vh.getGridView().getChildViewHolder(view);
            if (ibh == null) {
                getOnItemViewSelectedListener().onItemSelected(null, null, null, null);
            } else {
                getOnItemViewSelectedListener().onItemSelected(ibh.mHolder, ibh.mItem, null, null);
            }
        }
    }

    /**
     * Changes the visibility of views.  The entrance transition will be run against the views that
     * change visibilities.  This method is called by the fragment, it should not be called
     * directly by the application.
     *
     * @param holder         The ViewHolder for the vertical grid.
     * @param afterEntrance  true if children of vertical grid participating in entrance transition
     *                       should be set to visible, false otherwise.
     */
    public void setEntranceTransitionState(VerticalGridPresenter.ViewHolder holder,
            boolean afterEntrance) {
        holder.mGridView.setChildrenVisibility(afterEntrance? View.VISIBLE : View.INVISIBLE);
    }
}
