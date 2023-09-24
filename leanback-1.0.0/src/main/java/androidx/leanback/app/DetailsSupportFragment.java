// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from DetailsFragment.java.  DO NOT MODIFY. */

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

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.CallSuper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.R;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.leanback.util.StateMachine.Event;
import androidx.leanback.util.StateMachine.State;
import androidx.leanback.widget.BaseOnItemViewClickedListener;
import androidx.leanback.widget.BaseOnItemViewSelectedListener;
import androidx.leanback.widget.BrowseFrameLayout;
import androidx.leanback.widget.DetailsParallax;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter;
import androidx.leanback.widget.ItemAlignmentFacet;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.VerticalGridView;

import java.lang.ref.WeakReference;

/**
 * A fragment for creating Leanback details screens.
 *
 * <p>
 * A DetailsSupportFragment renders the elements of its {@link ObjectAdapter} as a set
 * of rows in a vertical list.The Adapter's {@link PresenterSelector} must maintain subclasses
 * of {@link RowPresenter}.
 * </p>
 *
 * When {@link FullWidthDetailsOverviewRowPresenter} is found in adapter,  DetailsSupportFragment will
 * setup default behavior of the DetailsOverviewRow:
 * <li>
 * The alignment of FullWidthDetailsOverviewRowPresenter is setup in
 * {@link #setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter)}.
 * </li>
 * <li>
 * The view status switching of FullWidthDetailsOverviewRowPresenter is done in
 * {@link #onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter,
 * FullWidthDetailsOverviewRowPresenter.ViewHolder, int, int, int)}.
 * </li>
 *
 * <p>
 * The recommended activity themes to use with a DetailsSupportFragment are
 * <li>
 * {@link androidx.leanback.R.style#Theme_Leanback_Details} with activity
 * shared element transition for {@link FullWidthDetailsOverviewRowPresenter}.
 * </li>
 * <li>
 * {@link androidx.leanback.R.style#Theme_Leanback_Details_NoSharedElementTransition}
 * if shared element transition is not needed, for example if first row is not rendered by
 * {@link FullWidthDetailsOverviewRowPresenter}.
 * </li>
 * </p>
 *
 * <p>
 * DetailsSupportFragment can use {@link DetailsSupportFragmentBackgroundController} to add a parallax drawable
 * background and embedded video playing fragment.
 * </p>
 */
public class DetailsSupportFragment extends BaseSupportFragment {
    static final String TAG = "DetailsSupportFragment";
    static final boolean DEBUG = false;

    final State STATE_SET_ENTRANCE_START_STATE = new State("STATE_SET_ENTRANCE_START_STATE") {
        @Override
        public void run() {
            mRowsSupportFragment.setEntranceTransitionState(false);
        }
    };

    final State STATE_ENTER_TRANSITION_INIT = new State("STATE_ENTER_TRANSIITON_INIT");

    void switchToVideoBeforeVideoSupportFragmentCreated() {
        // if the video fragment is not ready: immediately fade out covering drawable,
        // hide title and mark mPendingFocusOnVideo and set focus on it later.
        mDetailsBackgroundController.switchToVideoBeforeCreate();
        showTitle(false);
        mPendingFocusOnVideo = true;
        slideOutGridView();
    }

    final State STATE_SWITCH_TO_VIDEO_IN_ON_CREATE = new State("STATE_SWITCH_TO_VIDEO_IN_ON_CREATE",
            false, false) {
        @Override
        public void run() {
            switchToVideoBeforeVideoSupportFragmentCreated();
        }
    };

    final State STATE_ENTER_TRANSITION_CANCEL = new State("STATE_ENTER_TRANSITION_CANCEL",
            false, false) {
        @Override
        public void run() {
            if (mWaitEnterTransitionTimeout != null) {
                mWaitEnterTransitionTimeout.mRef.clear();
            }
            // clear the activity enter/sharedElement transition, return transitions are kept.
            // keep the return transitions and clear enter transition
            if (getActivity() != null) {
                Window window = getActivity().getWindow();
                Object returnTransition = TransitionHelper.getReturnTransition(window);
                Object sharedReturnTransition = TransitionHelper
                        .getSharedElementReturnTransition(window);
                TransitionHelper.setEnterTransition(window, null);
                TransitionHelper.setSharedElementEnterTransition(window, null);
                TransitionHelper.setReturnTransition(window, returnTransition);
                TransitionHelper.setSharedElementReturnTransition(window, sharedReturnTransition);
            }
        }
    };

    final State STATE_ENTER_TRANSITION_COMPLETE = new State("STATE_ENTER_TRANSIITON_COMPLETE",
            true, false);

    final State STATE_ENTER_TRANSITION_ADDLISTENER = new State("STATE_ENTER_TRANSITION_PENDING") {
        @Override
        public void run() {
            Object transition = TransitionHelper.getEnterTransition(getActivity().getWindow());
            TransitionHelper.addTransitionListener(transition, mEnterTransitionListener);
        }
    };

    final State STATE_ENTER_TRANSITION_PENDING = new State("STATE_ENTER_TRANSITION_PENDING") {
        @Override
        public void run() {
            if (mWaitEnterTransitionTimeout == null) {
                new WaitEnterTransitionTimeout(DetailsSupportFragment.this);
            }
        }
    };

    /**
     * Start this task when first DetailsOverviewRow is created, if there is no entrance transition
     * started, it will clear PF_ENTRANCE_TRANSITION_PENDING.
     */
    static class WaitEnterTransitionTimeout implements Runnable {
        static final long WAIT_ENTERTRANSITION_START = 200;

        final WeakReference<DetailsSupportFragment> mRef;

        WaitEnterTransitionTimeout(DetailsSupportFragment f) {
            mRef = new WeakReference<>(f);
            f.getView().postDelayed(this, WAIT_ENTERTRANSITION_START);
        }

        @Override
        public void run() {
            DetailsSupportFragment f = mRef.get();
            if (f != null) {
                f.mStateMachine.fireEvent(f.EVT_ENTER_TRANSIITON_DONE);
            }
        }
    }

    final State STATE_ON_SAFE_START = new State("STATE_ON_SAFE_START") {
        @Override
        public void run() {
            onSafeStart();
        }
    };

    final Event EVT_ONSTART = new Event("onStart");

    final Event EVT_NO_ENTER_TRANSITION = new Event("EVT_NO_ENTER_TRANSITION");

    final Event EVT_DETAILS_ROW_LOADED = new Event("onFirstRowLoaded");

    final Event EVT_ENTER_TRANSIITON_DONE = new Event("onEnterTransitionDone");

    final Event EVT_SWITCH_TO_VIDEO = new Event("switchToVideo");

    @Override
    void createStateMachineStates() {
        super.createStateMachineStates();
        mStateMachine.addState(STATE_SET_ENTRANCE_START_STATE);
        mStateMachine.addState(STATE_ON_SAFE_START);
        mStateMachine.addState(STATE_SWITCH_TO_VIDEO_IN_ON_CREATE);
        mStateMachine.addState(STATE_ENTER_TRANSITION_INIT);
        mStateMachine.addState(STATE_ENTER_TRANSITION_ADDLISTENER);
        mStateMachine.addState(STATE_ENTER_TRANSITION_CANCEL);
        mStateMachine.addState(STATE_ENTER_TRANSITION_PENDING);
        mStateMachine.addState(STATE_ENTER_TRANSITION_COMPLETE);
    }

    @Override
    void createStateMachineTransitions() {
        super.createStateMachineTransitions();
        /**
         * Part 1: Processing enter transitions after fragment.onCreate
         */
        mStateMachine.addTransition(STATE_START, STATE_ENTER_TRANSITION_INIT, EVT_ON_CREATE);
        // if transition is not supported, skip to complete
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_INIT, STATE_ENTER_TRANSITION_COMPLETE,
                COND_TRANSITION_NOT_SUPPORTED);
        // if transition is not set on Activity, skip to complete
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_INIT, STATE_ENTER_TRANSITION_COMPLETE,
                EVT_NO_ENTER_TRANSITION);
        // if switchToVideo is called before EVT_ON_CREATEVIEW, clear enter transition and skip to
        // complete.
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_INIT, STATE_ENTER_TRANSITION_CANCEL,
                EVT_SWITCH_TO_VIDEO);
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_CANCEL, STATE_ENTER_TRANSITION_COMPLETE);
        // once after onCreateView, we cannot skip the enter transition, add a listener and wait
        // it to finish
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_INIT, STATE_ENTER_TRANSITION_ADDLISTENER,
                EVT_ON_CREATEVIEW);
        // when enter transition finishes, go to complete, however this might never happen if
        // the activity is not giving transition options in startActivity, there is no API to query
        // if this activity is started in a enter transition mode. So we rely on a timer below:
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_ADDLISTENER,
                STATE_ENTER_TRANSITION_COMPLETE, EVT_ENTER_TRANSIITON_DONE);
        // we are expecting app to start delayed enter transition shortly after details row is
        // loaded, so create a timer and wait for enter transition start.
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_ADDLISTENER,
                STATE_ENTER_TRANSITION_PENDING, EVT_DETAILS_ROW_LOADED);
        // if enter transition not started in the timer, skip to DONE, this can be also true when
        // startActivity is not giving transition option.
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_PENDING, STATE_ENTER_TRANSITION_COMPLETE,
                EVT_ENTER_TRANSIITON_DONE);

        /**
         * Part 2: modification to the entrance transition defined in BaseSupportFragment
         */
        // Must finish enter transition before perform entrance transition.
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_COMPLETE, STATE_ENTRANCE_PERFORM);
        // Calling switch to video would hide immediately and skip entrance transition
        mStateMachine.addTransition(STATE_ENTRANCE_INIT, STATE_SWITCH_TO_VIDEO_IN_ON_CREATE,
                EVT_SWITCH_TO_VIDEO);
        mStateMachine.addTransition(STATE_SWITCH_TO_VIDEO_IN_ON_CREATE, STATE_ENTRANCE_COMPLETE);
        // if the entrance transition is skipped to complete by COND_TRANSITION_NOT_SUPPORTED, we
        // still need to do the switchToVideo.
        mStateMachine.addTransition(STATE_ENTRANCE_COMPLETE, STATE_SWITCH_TO_VIDEO_IN_ON_CREATE,
                EVT_SWITCH_TO_VIDEO);

        // for once the view is created in onStart and prepareEntranceTransition was called, we
        // could setEntranceStartState:
        mStateMachine.addTransition(STATE_ENTRANCE_ON_PREPARED,
                STATE_SET_ENTRANCE_START_STATE, EVT_ONSTART);

        /**
         * Part 3: onSafeStart()
         */
        // for onSafeStart: the condition is onStart called, entrance transition complete
        mStateMachine.addTransition(STATE_START, STATE_ON_SAFE_START, EVT_ONSTART);
        mStateMachine.addTransition(STATE_ENTRANCE_COMPLETE, STATE_ON_SAFE_START);
        mStateMachine.addTransition(STATE_ENTER_TRANSITION_COMPLETE, STATE_ON_SAFE_START);
    }

    private class SetSelectionRunnable implements Runnable {
        int mPosition;
        boolean mSmooth = true;

        SetSelectionRunnable() {
        }

        @Override
        public void run() {
            if (mRowsSupportFragment == null) {
                return;
            }
            mRowsSupportFragment.setSelectedPosition(mPosition, mSmooth);
        }
    }

    TransitionListener mEnterTransitionListener = new TransitionListener() {
        @Override
        public void onTransitionStart(Object transition) {
            if (mWaitEnterTransitionTimeout != null) {
                // cancel task of WaitEnterTransitionTimeout, we will clearPendingEnterTransition
                // when transition finishes.
                mWaitEnterTransitionTimeout.mRef.clear();
            }
        }

        @Override
        public void onTransitionCancel(Object transition) {
            mStateMachine.fireEvent(EVT_ENTER_TRANSIITON_DONE);
        }

        @Override
        public void onTransitionEnd(Object transition) {
            mStateMachine.fireEvent(EVT_ENTER_TRANSIITON_DONE);
        }
    };

    TransitionListener mReturnTransitionListener = new TransitionListener() {
        @Override
        public void onTransitionStart(Object transition) {
            onReturnTransitionStart();
        }
    };

    BrowseFrameLayout mRootView;
    View mBackgroundView;
    Drawable mBackgroundDrawable;
    Fragment mVideoSupportFragment;
    DetailsParallax mDetailsParallax;
    RowsSupportFragment mRowsSupportFragment;
    ObjectAdapter mAdapter;
    int mContainerListAlignTop;
    BaseOnItemViewSelectedListener mExternalOnItemViewSelectedListener;
    BaseOnItemViewClickedListener mOnItemViewClickedListener;
    DetailsSupportFragmentBackgroundController mDetailsBackgroundController;

    // A temporarily flag when switchToVideo() is called in onCreate(), if mPendingFocusOnVideo is
    // true, we will focus to VideoSupportFragment immediately after video fragment's view is created.
    boolean mPendingFocusOnVideo = false;

    WaitEnterTransitionTimeout mWaitEnterTransitionTimeout;

    Object mSceneAfterEntranceTransition;

    final SetSelectionRunnable mSetSelectionRunnable = new SetSelectionRunnable();

    final BaseOnItemViewSelectedListener<Object> mOnItemViewSelectedListener =
            new BaseOnItemViewSelectedListener<Object>() {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Object row) {
            int position = mRowsSupportFragment.getVerticalGridView().getSelectedPosition();
            int subposition = mRowsSupportFragment.getVerticalGridView().getSelectedSubPosition();
            if (DEBUG) Log.v(TAG, "row selected position " + position
                    + " subposition " + subposition);
            onRowSelected(position, subposition);
            if (mExternalOnItemViewSelectedListener != null) {
                mExternalOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                        rowViewHolder, row);
            }
        }
    };

    /**
     * Sets the list of rows for the fragment.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        Presenter[] presenters = adapter.getPresenterSelector().getPresenters();
        if (presenters != null) {
            for (int i = 0; i < presenters.length; i++) {
                setupPresenter(presenters[i]);
            }
        } else {
            Log.e(TAG, "PresenterSelector.getPresenters() not implemented");
        }
        if (mRowsSupportFragment != null) {
            mRowsSupportFragment.setAdapter(adapter);
        }
    }

    /**
     * Returns the list of rows.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(BaseOnItemViewSelectedListener listener) {
        mExternalOnItemViewSelectedListener = listener;
    }

    /**
     * Sets an item clicked listener.
     */
    public void setOnItemViewClickedListener(BaseOnItemViewClickedListener listener) {
        if (mOnItemViewClickedListener != listener) {
            mOnItemViewClickedListener = listener;
            if (mRowsSupportFragment != null) {
                mRowsSupportFragment.setOnItemViewClickedListener(listener);
            }
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public BaseOnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContainerListAlignTop =
            getResources().getDimensionPixelSize(R.dimen.lb_details_rows_align_top);

        FragmentActivity activity = getActivity();
        if (activity != null) {
            Object transition = TransitionHelper.getEnterTransition(activity.getWindow());
            if (transition == null) {
                mStateMachine.fireEvent(EVT_NO_ENTER_TRANSITION);
            }
            transition = TransitionHelper.getReturnTransition(activity.getWindow());
            if (transition != null) {
                TransitionHelper.addTransitionListener(transition, mReturnTransitionListener);
            }
        } else {
            mStateMachine.fireEvent(EVT_NO_ENTER_TRANSITION);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = (BrowseFrameLayout) inflater.inflate(
                R.layout.lb_details_fragment, container, false);
        mBackgroundView = mRootView.findViewById(R.id.details_background_view);
        if (mBackgroundView != null) {
            mBackgroundView.setBackground(mBackgroundDrawable);
        }
        mRowsSupportFragment = (RowsSupportFragment) getChildFragmentManager().findFragmentById(
                R.id.details_rows_dock);
        if (mRowsSupportFragment == null) {
            mRowsSupportFragment = new RowsSupportFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.details_rows_dock, mRowsSupportFragment).commit();
        }
        installTitleView(inflater, mRootView, savedInstanceState);
        mRowsSupportFragment.setAdapter(mAdapter);
        mRowsSupportFragment.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
        mRowsSupportFragment.setOnItemViewClickedListener(mOnItemViewClickedListener);

        mSceneAfterEntranceTransition = TransitionHelper.createScene(mRootView, new Runnable() {
            @Override
            public void run() {
                mRowsSupportFragment.setEntranceTransitionState(true);
            }
        });

        setupDpadNavigation();

        if (Build.VERSION.SDK_INT >= 21) {
            // Setup adapter listener to work with ParallaxTransition (>= API 21).
            mRowsSupportFragment.setExternalAdapterListener(new ItemBridgeAdapter.AdapterListener() {
                @Override
                public void onCreate(ItemBridgeAdapter.ViewHolder vh) {
                    if (mDetailsParallax != null && vh.getViewHolder()
                            instanceof FullWidthDetailsOverviewRowPresenter.ViewHolder) {
                        FullWidthDetailsOverviewRowPresenter.ViewHolder rowVh =
                                (FullWidthDetailsOverviewRowPresenter.ViewHolder)
                                        vh.getViewHolder();
                        rowVh.getOverviewView().setTag(R.id.lb_parallax_source,
                                mDetailsParallax);
                    }
                }
            });
        }
        return mRootView;
    }

    /**
     * @deprecated override {@link #onInflateTitleView(LayoutInflater,ViewGroup,Bundle)} instead.
     */
    @Deprecated
    protected View inflateTitle(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        return super.onInflateTitleView(inflater, parent, savedInstanceState);
    }

    @Override
    public View onInflateTitleView(LayoutInflater inflater, ViewGroup parent,
                                   Bundle savedInstanceState) {
        return inflateTitle(inflater, parent, savedInstanceState);
    }

    void setVerticalGridViewLayout(VerticalGridView listview) {
        // align the top edge of item to a fixed position
        listview.setItemAlignmentOffset(-mContainerListAlignTop);
        listview.setItemAlignmentOffsetPercent(VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
        listview.setWindowAlignmentOffset(0);
        listview.setWindowAlignmentOffsetPercent(VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
        listview.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
    }

    /**
     * Called to setup each Presenter of Adapter passed in {@link #setAdapter(ObjectAdapter)}.Note
     * that setup should only change the Presenter behavior that is meaningful in DetailsSupportFragment.
     * For example how a row is aligned in details Fragment.   The default implementation invokes
     * {@link #setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter)}
     *
     */
    protected void setupPresenter(Presenter rowPresenter) {
        if (rowPresenter instanceof FullWidthDetailsOverviewRowPresenter) {
            setupDetailsOverviewRowPresenter((FullWidthDetailsOverviewRowPresenter) rowPresenter);
        }
    }

    /**
     * Called to setup {@link FullWidthDetailsOverviewRowPresenter}.  The default implementation
     * adds two alignment positions({@link ItemAlignmentFacet}) for ViewHolder of
     * FullWidthDetailsOverviewRowPresenter to align in fragment.
     */
    protected void setupDetailsOverviewRowPresenter(FullWidthDetailsOverviewRowPresenter presenter) {
        ItemAlignmentFacet facet = new ItemAlignmentFacet();
        // by default align details_frame to half window height
        ItemAlignmentFacet.ItemAlignmentDef alignDef1 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef1.setItemAlignmentViewId(R.id.details_frame);
        alignDef1.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_actions));
        alignDef1.setItemAlignmentOffsetPercent(0);
        // when description is selected, align details_frame to top edge
        ItemAlignmentFacet.ItemAlignmentDef alignDef2 = new ItemAlignmentFacet.ItemAlignmentDef();
        alignDef2.setItemAlignmentViewId(R.id.details_frame);
        alignDef2.setItemAlignmentFocusViewId(R.id.details_overview_description);
        alignDef2.setItemAlignmentOffset(- getResources()
                .getDimensionPixelSize(R.dimen.lb_details_v2_align_pos_for_description));
        alignDef2.setItemAlignmentOffsetPercent(0);
        ItemAlignmentFacet.ItemAlignmentDef[] defs =
                new ItemAlignmentFacet.ItemAlignmentDef[] {alignDef1, alignDef2};
        facet.setAlignmentDefs(defs);
        presenter.setFacet(ItemAlignmentFacet.class, facet);
    }

    VerticalGridView getVerticalGridView() {
        return mRowsSupportFragment == null ? null : mRowsSupportFragment.getVerticalGridView();
    }

    /**
     * Gets embedded RowsSupportFragment showing multiple rows for DetailsSupportFragment.  If view of
     * DetailsSupportFragment is not created, the method returns null.
     * @return Embedded RowsSupportFragment showing multiple rows for DetailsSupportFragment.
     */
    public RowsSupportFragment getRowsSupportFragment() {
        return mRowsSupportFragment;
    }

    /**
     * Setup dimensions that are only meaningful when the child Fragments are inside
     * DetailsSupportFragment.
     */
    private void setupChildFragmentLayout() {
        setVerticalGridViewLayout(mRowsSupportFragment.getVerticalGridView());
    }

    /**
     * Sets the selected row position with smooth animation.
     */
    public void setSelectedPosition(int position) {
        setSelectedPosition(position, true);
    }

    /**
     * Sets the selected row position.
     */
    public void setSelectedPosition(int position, boolean smooth) {
        mSetSelectionRunnable.mPosition = position;
        mSetSelectionRunnable.mSmooth = smooth;
        if (getView() != null && getView().getHandler() != null) {
            getView().getHandler().post(mSetSelectionRunnable);
        }
    }

    void switchToVideo() {
        if (mVideoSupportFragment != null && mVideoSupportFragment.getView() != null) {
            mVideoSupportFragment.getView().requestFocus();
        } else {
            mStateMachine.fireEvent(EVT_SWITCH_TO_VIDEO);
        }
    }

    void switchToRows() {
        mPendingFocusOnVideo = false;
        VerticalGridView verticalGridView = getVerticalGridView();
        if (verticalGridView != null && verticalGridView.getChildCount() > 0) {
            verticalGridView.requestFocus();
        }
    }

    /**
     * This method asks DetailsSupportFragmentBackgroundController to add a fragment for rendering video.
     * In case the fragment is already there, it will return the existing one. The method must be
     * called after calling super.onCreate(). App usually does not call this method directly.
     *
     * @return Fragment the added or restored fragment responsible for rendering video.
     * @see DetailsSupportFragmentBackgroundController#onCreateVideoSupportFragment()
     */
    final Fragment findOrCreateVideoSupportFragment() {
        if (mVideoSupportFragment != null) {
            return mVideoSupportFragment;
        }
        Fragment fragment = getChildFragmentManager()
                .findFragmentById(R.id.video_surface_container);
        if (fragment == null && mDetailsBackgroundController != null) {
            FragmentTransaction ft2 = getChildFragmentManager().beginTransaction();
            ft2.add(androidx.leanback.R.id.video_surface_container,
                    fragment = mDetailsBackgroundController.onCreateVideoSupportFragment());
            ft2.commit();
            if (mPendingFocusOnVideo) {
                // wait next cycle for Fragment view created so we can focus on it.
                // This is a bit hack eventually we will do commitNow() which get view immediately.
                getView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (getView() != null) {
                            switchToVideo();
                        }
                        mPendingFocusOnVideo = false;
                    }
                });
            }
        }
        mVideoSupportFragment = fragment;
        return mVideoSupportFragment;
    }

    void onRowSelected(int selectedPosition, int selectedSubPosition) {
        ObjectAdapter adapter = getAdapter();
        if (( mRowsSupportFragment != null && mRowsSupportFragment.getView() != null
                && mRowsSupportFragment.getView().hasFocus() && !mPendingFocusOnVideo)
                && (adapter == null || adapter.size() == 0
                || (getVerticalGridView().getSelectedPosition() == 0
                && getVerticalGridView().getSelectedSubPosition() == 0))) {
            showTitle(true);
        } else {
            showTitle(false);
        }
        if (adapter != null && adapter.size() > selectedPosition) {
            final VerticalGridView gridView = getVerticalGridView();
            final int count = gridView.getChildCount();
            if (count > 0) {
                mStateMachine.fireEvent(EVT_DETAILS_ROW_LOADED);
            }
            for (int i = 0; i < count; i++) {
                ItemBridgeAdapter.ViewHolder bridgeViewHolder = (ItemBridgeAdapter.ViewHolder)
                        gridView.getChildViewHolder(gridView.getChildAt(i));
                RowPresenter rowPresenter = (RowPresenter) bridgeViewHolder.getPresenter();
                onSetRowStatus(rowPresenter,
                        rowPresenter.getRowViewHolder(bridgeViewHolder.getViewHolder()),
                        bridgeViewHolder.getAdapterPosition(),
                        selectedPosition, selectedSubPosition);
            }
        }
    }

    /**
     * Called when onStart and enter transition (postponed/none postponed) and entrance transition
     * are all finished.
     */
    @CallSuper
    void onSafeStart() {
        if (mDetailsBackgroundController != null) {
            mDetailsBackgroundController.onStart();
        }
    }

    @CallSuper
    void onReturnTransitionStart() {
        if (mDetailsBackgroundController != null) {
            // first disable parallax effect that auto-start PlaybackGlue.
            boolean isVideoVisible = mDetailsBackgroundController.disableVideoParallax();
            // if video is not visible we can safely remove VideoSupportFragment,
            // otherwise let video playing during return transition.
            if (!isVideoVisible && mVideoSupportFragment != null) {
                FragmentTransaction ft2 = getChildFragmentManager().beginTransaction();
                ft2.remove(mVideoSupportFragment);
                ft2.commit();
                mVideoSupportFragment = null;
            }
        }
    }

    @Override
    public void onStop() {
        if (mDetailsBackgroundController != null) {
            mDetailsBackgroundController.onStop();
        }
        super.onStop();
    }

    /**
     * Called on every visible row to change view status when current selected row position
     * or selected sub position changed.  Subclass may override.   The default
     * implementation calls {@link #onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter,
     * FullWidthDetailsOverviewRowPresenter.ViewHolder, int, int, int)} if presenter is
     * instance of {@link FullWidthDetailsOverviewRowPresenter}.
     *
     * @param presenter   The presenter used to create row ViewHolder.
     * @param viewHolder  The visible (attached) row ViewHolder, note that it may or may not
     *                    be selected.
     * @param adapterPosition  The adapter position of viewHolder inside adapter.
     * @param selectedPosition The adapter position of currently selected row.
     * @param selectedSubPosition The sub position within currently selected row.  This is used
     *                            When a row has multiple alignment positions.
     */
    protected void onSetRowStatus(RowPresenter presenter, RowPresenter.ViewHolder viewHolder, int
            adapterPosition, int selectedPosition, int selectedSubPosition) {
        if (presenter instanceof FullWidthDetailsOverviewRowPresenter) {
            onSetDetailsOverviewRowStatus((FullWidthDetailsOverviewRowPresenter) presenter,
                    (FullWidthDetailsOverviewRowPresenter.ViewHolder) viewHolder,
                    adapterPosition, selectedPosition, selectedSubPosition);
        }
    }

    /**
     * Called to change DetailsOverviewRow view status when current selected row position
     * or selected sub position changed.  Subclass may override.   The default
     * implementation switches between three states based on the positions:
     * {@link FullWidthDetailsOverviewRowPresenter#STATE_HALF},
     * {@link FullWidthDetailsOverviewRowPresenter#STATE_FULL} and
     * {@link FullWidthDetailsOverviewRowPresenter#STATE_SMALL}.
     *
     * @param presenter   The presenter used to create row ViewHolder.
     * @param viewHolder  The visible (attached) row ViewHolder, note that it may or may not
     *                    be selected.
     * @param adapterPosition  The adapter position of viewHolder inside adapter.
     * @param selectedPosition The adapter position of currently selected row.
     * @param selectedSubPosition The sub position within currently selected row.  This is used
     *                            When a row has multiple alignment positions.
     */
    protected void onSetDetailsOverviewRowStatus(FullWidthDetailsOverviewRowPresenter presenter,
            FullWidthDetailsOverviewRowPresenter.ViewHolder viewHolder, int adapterPosition,
            int selectedPosition, int selectedSubPosition) {
        if (selectedPosition > adapterPosition) {
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_HALF);
        } else if (selectedPosition == adapterPosition && selectedSubPosition == 1) {
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_HALF);
        } else if (selectedPosition == adapterPosition && selectedSubPosition == 0){
            presenter.setState(viewHolder, FullWidthDetailsOverviewRowPresenter.STATE_FULL);
        } else {
            presenter.setState(viewHolder,
                    FullWidthDetailsOverviewRowPresenter.STATE_SMALL);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        setupChildFragmentLayout();
        mStateMachine.fireEvent(EVT_ONSTART);
        if (mDetailsParallax != null) {
            mDetailsParallax.setRecyclerView(mRowsSupportFragment.getVerticalGridView());
        }
        if (mPendingFocusOnVideo) {
            slideOutGridView();
        } else if (!getView().hasFocus()) {
            mRowsSupportFragment.getVerticalGridView().requestFocus();
        }
    }

    @Override
    protected Object createEntranceTransition() {
        return TransitionHelper.loadTransition(getContext(),
                R.transition.lb_details_enter_transition);
    }

    @Override
    protected void runEntranceTransition(Object entranceTransition) {
        TransitionHelper.runTransition(mSceneAfterEntranceTransition, entranceTransition);
    }

    @Override
    protected void onEntranceTransitionEnd() {
        mRowsSupportFragment.onTransitionEnd();
    }

    @Override
    protected void onEntranceTransitionPrepare() {
        mRowsSupportFragment.onTransitionPrepare();
    }

    @Override
    protected void onEntranceTransitionStart() {
        mRowsSupportFragment.onTransitionStart();
    }

    /**
     * Returns the {@link DetailsParallax} instance used by
     * {@link DetailsSupportFragmentBackgroundController} to configure parallax effect of background and
     * control embedded video playback. App usually does not use this method directly.
     * App may use this method for other custom parallax tasks.
     *
     * @return The DetailsParallax instance attached to the DetailsSupportFragment.
     */
    public DetailsParallax getParallax() {
        if (mDetailsParallax == null) {
            mDetailsParallax = new DetailsParallax();
            if (mRowsSupportFragment != null && mRowsSupportFragment.getView() != null) {
                mDetailsParallax.setRecyclerView(mRowsSupportFragment.getVerticalGridView());
            }
        }
        return mDetailsParallax;
    }

    /**
     * Set background drawable shown below foreground rows UI and above
     * {@link #findOrCreateVideoSupportFragment()}.
     *
     * @see DetailsSupportFragmentBackgroundController
     */
    void setBackgroundDrawable(Drawable drawable) {
        if (mBackgroundView != null) {
            mBackgroundView.setBackground(drawable);
        }
        mBackgroundDrawable = drawable;
    }

    /**
     * This method does the following
     * <ul>
     * <li>sets up focus search handling logic in the root view to enable transitioning between
     * half screen/full screen/no video mode.</li>
     *
     * <li>Sets up the key listener in the root view to intercept events like UP/DOWN and
     * transition to appropriate mode like half/full screen video.</li>
     * </ul>
     */
    void setupDpadNavigation() {
        mRootView.setOnChildFocusListener(new BrowseFrameLayout.OnChildFocusListener() {

            @Override
            public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
                return false;
            }

            @Override
            public void onRequestChildFocus(View child, View focused) {
                if (child != mRootView.getFocusedChild()) {
                    if (child.getId() == R.id.details_fragment_root) {
                        if (!mPendingFocusOnVideo) {
                            slideInGridView();
                            showTitle(true);
                        }
                    } else if (child.getId() == R.id.video_surface_container) {
                        slideOutGridView();
                        showTitle(false);
                    } else {
                        showTitle(true);
                    }
                }
            }
        });
        mRootView.setOnFocusSearchListener(new BrowseFrameLayout.OnFocusSearchListener() {
            @Override
            public View onFocusSearch(View focused, int direction) {
                if (mRowsSupportFragment.getVerticalGridView() != null
                        && mRowsSupportFragment.getVerticalGridView().hasFocus()) {
                    if (direction == View.FOCUS_UP) {
                        if (mDetailsBackgroundController != null
                                && mDetailsBackgroundController.canNavigateToVideoSupportFragment()
                                && mVideoSupportFragment != null && mVideoSupportFragment.getView() != null) {
                            return mVideoSupportFragment.getView();
                        } else if (getTitleView() != null && getTitleView().hasFocusable()) {
                            return getTitleView();
                        }
                    }
                } else if (getTitleView() != null && getTitleView().hasFocus()) {
                    if (direction == View.FOCUS_DOWN) {
                        if (mRowsSupportFragment.getVerticalGridView() != null) {
                            return mRowsSupportFragment.getVerticalGridView();
                        }
                    }
                }
                return focused;
            }
        });

        // If we press BACK on remote while in full screen video mode, we should
        // transition back to half screen video playback mode.
        mRootView.setOnDispatchKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // This is used to check if we are in full screen video mode. This is somewhat
                // hacky and relies on the behavior of the video helper class to update the
                // focusability of the video surface view.
                if (mVideoSupportFragment != null && mVideoSupportFragment.getView() != null
                        && mVideoSupportFragment.getView().hasFocus()) {
                    if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        if (getVerticalGridView().getChildCount() > 0) {
                            getVerticalGridView().requestFocus();
                            return true;
                        }
                    }
                }

                return false;
            }
        });
    }

    /**
     * Slides vertical grid view (displaying media item details) out of the screen from below.
     */
    void slideOutGridView() {
        if (getVerticalGridView() != null) {
            getVerticalGridView().animateOut();
        }
    }

    void slideInGridView() {
        if (getVerticalGridView() != null) {
            getVerticalGridView().animateIn();
        }
    }
}
