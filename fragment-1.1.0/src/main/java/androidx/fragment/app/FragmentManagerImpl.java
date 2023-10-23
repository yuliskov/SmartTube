/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.fragment.app;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.core.util.DebugUtils;
import androidx.core.util.LogWriter;
import androidx.core.view.OneShotPreDrawListener;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Container for fragments associated with an activity.
 */
final class FragmentManagerImpl extends FragmentManager implements LayoutInflater.Factory2 {
    static boolean DEBUG = false;
    static final String TAG = "FragmentManager";

    static final String TARGET_REQUEST_CODE_STATE_TAG = "android:target_req_state";
    static final String TARGET_STATE_TAG = "android:target_state";
    static final String VIEW_STATE_TAG = "android:view_state";
    static final String USER_VISIBLE_HINT_TAG = "android:user_visible_hint";

    private static final class FragmentLifecycleCallbacksHolder {
        final FragmentLifecycleCallbacks mCallback;
        final boolean mRecursive;

        FragmentLifecycleCallbacksHolder(FragmentLifecycleCallbacks callback, boolean recursive) {
            mCallback = callback;
            mRecursive = recursive;
        }
    }

    ArrayList<OpGenerator> mPendingActions;
    boolean mExecutingActions;

    int mNextFragmentIndex = 0;

    final ArrayList<Fragment> mAdded = new ArrayList<>();
    final HashMap<String, Fragment> mActive = new HashMap<>();
    ArrayList<BackStackRecord> mBackStack;
    ArrayList<Fragment> mCreatedMenus;
    private OnBackPressedDispatcher mOnBackPressedDispatcher;
    private final OnBackPressedCallback mOnBackPressedCallback =
            new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            FragmentManagerImpl.this.handleOnBackPressed();
        }
    };

    // Must be accessed while locked.
    ArrayList<BackStackRecord> mBackStackIndices;
    ArrayList<Integer> mAvailBackStackIndices;

    ArrayList<OnBackStackChangedListener> mBackStackChangeListeners;
    private final CopyOnWriteArrayList<FragmentLifecycleCallbacksHolder>
            mLifecycleCallbacks = new CopyOnWriteArrayList<>();

    int mCurState = Fragment.INITIALIZING;
    FragmentHostCallback mHost;
    FragmentContainer mContainer;
    Fragment mParent;
    @Nullable
    Fragment mPrimaryNav;

    boolean mNeedMenuInvalidate;
    boolean mStateSaved;
    boolean mStopped;
    boolean mDestroyed;
    boolean mHavePendingDeferredStart;

    // Temporary vars for removing redundant operations in BackStackRecords:
    ArrayList<BackStackRecord> mTmpRecords;
    ArrayList<Boolean> mTmpIsPop;
    ArrayList<Fragment> mTmpAddedFragments;

    // Temporary vars for state save and restore.
    Bundle mStateBundle = null;
    SparseArray<Parcelable> mStateArray = null;

    // Postponed transactions.
    ArrayList<StartEnterTransitionListener> mPostponedTransactions;

    private FragmentManagerViewModel mNonConfig;

    Runnable mExecCommit = new Runnable() {
        @Override
        public void run() {
            execPendingActions();
        }
    };

    private void throwException(RuntimeException ex) {
        Log.e(TAG, ex.getMessage());
        Log.e(TAG, "Activity state:");
        LogWriter logw = new LogWriter(TAG);
        PrintWriter pw = new PrintWriter(logw);
        if (mHost != null) {
            try {
                mHost.onDump("  ", null, pw, new String[] { });
            } catch (Exception e) {
                Log.e(TAG, "Failed dumping state", e);
            }
        } else {
            try {
                dump("  ", null, pw, new String[] { });
            } catch (Exception e) {
                Log.e(TAG, "Failed dumping state", e);
            }
        }
        throw ex;
    }

    @NonNull
    @Override
    public FragmentTransaction beginTransaction() {
        return new BackStackRecord(this);
    }

    @Override
    public boolean executePendingTransactions() {
        boolean updates = execPendingActions();
        forcePostponedTransactions();
        return updates;
    }

    private void updateOnBackPressedCallbackEnabled() {
        // Always enable the callback if we have pending actions
        // as we don't know if they'll change the back stack entry count.
        // See handleOnBackPressed() for more explanation
        if (mPendingActions != null && !mPendingActions.isEmpty()) {
            mOnBackPressedCallback.setEnabled(true);
            return;
        }
        // This FragmentManager needs to have a back stack for this to be enabled
        // And the parent fragment, if it exists, needs to be the primary navigation
        // fragment.
        mOnBackPressedCallback.setEnabled(getBackStackEntryCount() > 0
                && isPrimaryNavigation(mParent));
    }

    /**
     * Recursively check up the FragmentManager hierarchy of primary
     * navigation Fragments to ensure that all of the parent Fragments are the
     * primary navigation Fragment for their associated FragmentManager
     */
    boolean isPrimaryNavigation(@Nullable Fragment parent) {
        // If the parent is null, then we're at the root host
        // and we're always the primary navigation
        if (parent == null) {
            return true;
        }
        FragmentManagerImpl parentFragmentManager = parent.mFragmentManager;
        Fragment primaryNavigationFragment = parentFragmentManager
                .getPrimaryNavigationFragment();
        // The parent Fragment needs to be the primary navigation Fragment
        // and, if it has a parent itself, that parent also needs to be
        // the primary navigation fragment, recursively up the stack
        return parent == primaryNavigationFragment
                && isPrimaryNavigation(parentFragmentManager.mParent);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleOnBackPressed() {
        // First, execute any pending actions to make sure we're in an
        // up to date view of the world just in case anyone is queuing
        // up transactions that change the back stack then immediately
        // calling onBackPressed()
        execPendingActions();
        if (mOnBackPressedCallback.isEnabled()) {
            // We still have a back stack, so we can pop
            popBackStackImmediate();
        } else {
            // Sigh. Due to FragmentManager's asynchronicity, we can
            // get into cases where we *think* we can handle the back
            // button but because of frame perfect dispatch, we fell
            // on our face. Since our callback is disabled, we can
            // re-trigger the onBackPressed() to dispatch to the next
            // enabled callback
            mOnBackPressedDispatcher.onBackPressed();
        }
    }

    @Override
    public void popBackStack() {
        enqueueAction(new PopBackStackState(null, -1, 0), false);
    }

    @Override
    public boolean popBackStackImmediate() {
        checkStateLoss();
        return popBackStackImmediate(null, -1, 0);
    }

    @Override
    public void popBackStack(@Nullable final String name, final int flags) {
        enqueueAction(new PopBackStackState(name, -1, flags), false);
    }

    @Override
    public boolean popBackStackImmediate(@Nullable String name, int flags) {
        checkStateLoss();
        return popBackStackImmediate(name, -1, flags);
    }

    @Override
    public void popBackStack(final int id, final int flags) {
        if (id < 0) {
            throw new IllegalArgumentException("Bad id: " + id);
        }
        enqueueAction(new PopBackStackState(null, id, flags), false);
    }

    @Override
    public boolean popBackStackImmediate(int id, int flags) {
        checkStateLoss();
        execPendingActions();
        if (id < 0) {
            throw new IllegalArgumentException("Bad id: " + id);
        }
        return popBackStackImmediate(null, id, flags);
    }

    /**
     * Used by all public popBackStackImmediate methods, this executes pending transactions and
     * returns true if the pop action did anything, regardless of what other pending
     * transactions did.
     *
     * @return true if the pop operation did anything or false otherwise.
     */
    private boolean popBackStackImmediate(String name, int id, int flags) {
        execPendingActions();
        ensureExecReady(true);

        if (mPrimaryNav != null // We have a primary nav fragment
                && id < 0 // No valid id (since they're local)
                && name == null) { // no name to pop to (since they're local)
            final FragmentManager childManager = mPrimaryNav.getChildFragmentManager();
            if (childManager.popBackStackImmediate()) {
                // We did something, just not to this specific FragmentManager. Return true.
                return true;
            }
        }

        boolean executePop = popBackStackState(mTmpRecords, mTmpIsPop, name, id, flags);
        if (executePop) {
            mExecutingActions = true;
            try {
                removeRedundantOperationsAndExecute(mTmpRecords, mTmpIsPop);
            } finally {
                cleanupExec();
            }
        }

        updateOnBackPressedCallbackEnabled();
        doPendingDeferredStart();
        burpActive();
        return executePop;
    }

    @Override
    public int getBackStackEntryCount() {
        return mBackStack != null ? mBackStack.size() : 0;
    }

    @Override
    public BackStackEntry getBackStackEntryAt(int index) {
        return mBackStack.get(index);
    }

    @Override
    public void addOnBackStackChangedListener(OnBackStackChangedListener listener) {
        if (mBackStackChangeListeners == null) {
            mBackStackChangeListeners = new ArrayList<OnBackStackChangedListener>();
        }
        mBackStackChangeListeners.add(listener);
    }

    @Override
    public void removeOnBackStackChangedListener(OnBackStackChangedListener listener) {
        if (mBackStackChangeListeners != null) {
            mBackStackChangeListeners.remove(listener);
        }
    }

    @Override
    public void putFragment(Bundle bundle, String key, Fragment fragment) {
        if (fragment.mFragmentManager != this) {
            throwException(new IllegalStateException("Fragment " + fragment
                    + " is not currently in the FragmentManager"));
        }
        bundle.putString(key, fragment.mWho);
    }

    @Override
    @Nullable
    public Fragment getFragment(Bundle bundle, String key) {
        String who = bundle.getString(key);
        if (who == null) {
            return null;
        }
        Fragment f = mActive.get(who);
        if (f == null) {
            throwException(new IllegalStateException("Fragment no longer exists for key "
                    + key + ": unique id " + who));
        }
        return f;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Fragment> getFragments() {
        if (mAdded.isEmpty()) {
            return Collections.emptyList();
        }
        synchronized (mAdded) {
            return (List<Fragment>) mAdded.clone();
        }
    }

    @NonNull
    ViewModelStore getViewModelStore(@NonNull Fragment f) {
        return mNonConfig.getViewModelStore(f);
    }

    @NonNull
    FragmentManagerViewModel getChildNonConfig(@NonNull Fragment f) {
        return mNonConfig.getChildNonConfig(f);
    }

    void addRetainedFragment(@NonNull Fragment f) {
        if (isStateSaved()) {
            if (FragmentManagerImpl.DEBUG) {
                Log.v(TAG, "Ignoring addRetainedFragment as the state is already saved");
            }
            return;
        }
        boolean added = mNonConfig.addRetainedFragment(f);
        if (added && FragmentManagerImpl.DEBUG) {
            Log.v(TAG, "Updating retained Fragments: Added " + f);
        }
    }

    void removeRetainedFragment(@NonNull Fragment f) {
        if (isStateSaved()) {
            if (FragmentManagerImpl.DEBUG) {
                Log.v(TAG, "Ignoring removeRetainedFragment as the state is already saved");
            }
            return;
        }
        boolean removed = mNonConfig.removeRetainedFragment(f);
        if (removed && FragmentManagerImpl.DEBUG) {
            Log.v(TAG, "Updating retained Fragments: Removed " + f);
        }
    }

    /**
     * This is used by FragmentController to get the Active fragments.
     *
     * @return A list of active fragments in the fragment manager, including those that are in the
     * back stack.
     */
    @NonNull
    List<Fragment> getActiveFragments() {
        return new ArrayList<>(mActive.values());
    }

    /**
     * Used by FragmentController to get the number of Active Fragments.
     *
     * @return The number of active fragments.
     */
    int getActiveFragmentCount() {
        return mActive.size();
    }

    @Override
    @Nullable
    public Fragment.SavedState saveFragmentInstanceState(@NonNull Fragment fragment) {
        if (fragment.mFragmentManager != this) {
            throwException( new IllegalStateException("Fragment " + fragment
                    + " is not currently in the FragmentManager"));
        }
        if (fragment.mState > Fragment.INITIALIZING) {
            Bundle result = saveFragmentBasicState(fragment);
            return result != null ? new Fragment.SavedState(result) : null;
        }
        return null;
    }

    @Override
    public boolean isDestroyed() {
        return mDestroyed;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("FragmentManager{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(" in ");
        if (mParent != null) {
            DebugUtils.buildShortClassTag(mParent, sb);
        } else {
            DebugUtils.buildShortClassTag(mHost, sb);
        }
        sb.append("}}");
        return sb.toString();
    }

    @Override
    public void dump(@NonNull String prefix, @Nullable FileDescriptor fd,
                     @NonNull PrintWriter writer, @Nullable String[] args) {
        String innerPrefix = prefix + "    ";

        if (!mActive.isEmpty()) {
            writer.print(prefix); writer.print("Active Fragments in ");
            writer.print(Integer.toHexString(System.identityHashCode(this)));
            writer.println(":");
            for (Fragment f : mActive.values()) {
                writer.print(prefix); writer.println(f);
                if (f != null) {
                    f.dump(innerPrefix, fd, writer, args);
                }
            }
        }

        int N = mAdded.size();
        if (N > 0) {
            writer.print(prefix); writer.println("Added Fragments:");
            for (int i = 0; i < N; i++) {
                Fragment f = mAdded.get(i);
                writer.print(prefix);
                writer.print("  #");
                writer.print(i);
                writer.print(": ");
                writer.println(f.toString());
            }
        }

        if (mCreatedMenus != null) {
            N = mCreatedMenus.size();
            if (N > 0) {
                writer.print(prefix); writer.println("Fragments Created Menus:");
                for (int i=0; i<N; i++) {
                    Fragment f = mCreatedMenus.get(i);
                    writer.print(prefix); writer.print("  #"); writer.print(i);
                    writer.print(": "); writer.println(f.toString());
                }
            }
        }

        if (mBackStack != null) {
            N = mBackStack.size();
            if (N > 0) {
                writer.print(prefix); writer.println("Back Stack:");
                for (int i=0; i<N; i++) {
                    BackStackRecord bs = mBackStack.get(i);
                    writer.print(prefix); writer.print("  #"); writer.print(i);
                    writer.print(": "); writer.println(bs.toString());
                    bs.dump(innerPrefix, writer);
                }
            }
        }

        synchronized (this) {
            if (mBackStackIndices != null) {
                N = mBackStackIndices.size();
                if (N > 0) {
                    writer.print(prefix); writer.println("Back Stack Indices:");
                    for (int i=0; i<N; i++) {
                        BackStackRecord bs = mBackStackIndices.get(i);
                        writer.print(prefix); writer.print("  #"); writer.print(i);
                        writer.print(": "); writer.println(bs);
                    }
                }
            }

            if (mAvailBackStackIndices != null && mAvailBackStackIndices.size() > 0) {
                writer.print(prefix); writer.print("mAvailBackStackIndices: ");
                writer.println(Arrays.toString(mAvailBackStackIndices.toArray()));
            }
        }

        if (mPendingActions != null) {
            N = mPendingActions.size();
            if (N > 0) {
                writer.print(prefix); writer.println("Pending Actions:");
                for (int i=0; i<N; i++) {
                    OpGenerator r = mPendingActions.get(i);
                    writer.print(prefix); writer.print("  #"); writer.print(i);
                    writer.print(": "); writer.println(r);
                }
            }
        }

        writer.print(prefix); writer.println("FragmentManager misc state:");
        writer.print(prefix); writer.print("  mHost="); writer.println(mHost);
        writer.print(prefix); writer.print("  mContainer="); writer.println(mContainer);
        if (mParent != null) {
            writer.print(prefix); writer.print("  mParent="); writer.println(mParent);
        }
        writer.print(prefix); writer.print("  mCurState="); writer.print(mCurState);
        writer.print(" mStateSaved="); writer.print(mStateSaved);
        writer.print(" mStopped="); writer.print(mStopped);
        writer.print(" mDestroyed="); writer.println(mDestroyed);
        if (mNeedMenuInvalidate) {
            writer.print(prefix); writer.print("  mNeedMenuInvalidate=");
            writer.println(mNeedMenuInvalidate);
        }
    }

    static final Interpolator DECELERATE_QUINT = new DecelerateInterpolator(2.5f);
    static final Interpolator DECELERATE_CUBIC = new DecelerateInterpolator(1.5f);

    static final int ANIM_DUR = 220;

    static AnimationOrAnimator makeOpenCloseAnimation(float startScale,
                                                      float endScale, float startAlpha, float endAlpha) {
        AnimationSet set = new AnimationSet(false);
        ScaleAnimation scale = new ScaleAnimation(startScale, endScale, startScale, endScale,
                Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        scale.setInterpolator(DECELERATE_QUINT);
        scale.setDuration(ANIM_DUR);
        set.addAnimation(scale);
        AlphaAnimation alpha = new AlphaAnimation(startAlpha, endAlpha);
        alpha.setInterpolator(DECELERATE_CUBIC);
        alpha.setDuration(ANIM_DUR);
        set.addAnimation(alpha);
        return new AnimationOrAnimator(set);
    }

    static AnimationOrAnimator makeFadeAnimation(float start, float end) {
        AlphaAnimation anim = new AlphaAnimation(start, end);
        anim.setInterpolator(DECELERATE_CUBIC);
        anim.setDuration(ANIM_DUR);
        return new AnimationOrAnimator(anim);
    }

    AnimationOrAnimator loadAnimation(Fragment fragment, int transit, boolean enter,
                                      int transitionStyle) {
        int nextAnim = fragment.getNextAnim();
        // Clear the Fragment animation
        fragment.setNextAnim(0);
        // If there is a transition on the container, clear those set on the fragment
        if (fragment.mContainer != null && fragment.mContainer.getLayoutTransition() != null) {
            return null;
        }
        Animation animation = fragment.onCreateAnimation(transit, enter, nextAnim);
        if (animation != null) {
            return new AnimationOrAnimator(animation);
        }

        Animator animator = fragment.onCreateAnimator(transit, enter, nextAnim);
        if (animator != null) {
            return new AnimationOrAnimator(animator);
        }

        if (nextAnim != 0) {
            String dir = mHost.getContext().getResources().getResourceTypeName(nextAnim);
            boolean isAnim = "anim".equals(dir);
            boolean successfulLoad = false;
            if (isAnim) {
                // try AnimationUtils first
                try {
                    animation = AnimationUtils.loadAnimation(mHost.getContext(), nextAnim);
                    if (animation != null) {
                        return new AnimationOrAnimator(animation);
                    }
                    // A null animation may be returned and that is acceptable
                    successfulLoad = true; // succeeded in loading animation, but it is null
                } catch (Resources.NotFoundException e) {
                    throw e; // Rethrow it -- the resource should be found if it is provided.
                } catch (RuntimeException e) {
                    // Other exceptions can occur when loading an Animator from AnimationUtils.
                }
            }
            if (!successfulLoad) {
                // try Animator
                try {
                    animator = AnimatorInflater.loadAnimator(mHost.getContext(), nextAnim);
                    if (animator != null) {
                        return new AnimationOrAnimator(animator);
                    }
                } catch (RuntimeException e) {
                    if (isAnim) {
                        // Rethrow it -- we already tried AnimationUtils and it failed.
                        throw e;
                    }
                    // Otherwise, it is probably an animation resource
                    animation = AnimationUtils.loadAnimation(mHost.getContext(), nextAnim);
                    if (animation != null) {
                        return new AnimationOrAnimator(animation);
                    }
                }
            }
        }

        if (transit == 0) {
            return null;
        }

        int styleIndex = transitToStyleIndex(transit, enter);
        if (styleIndex < 0) {
            return null;
        }

        switch (styleIndex) {
            case ANIM_STYLE_OPEN_ENTER:
                return makeOpenCloseAnimation(1.125f, 1.0f, 0, 1);
            case ANIM_STYLE_OPEN_EXIT:
                return makeOpenCloseAnimation(1.0f, .975f, 1, 0);
            case ANIM_STYLE_CLOSE_ENTER:
                return makeOpenCloseAnimation(.975f, 1.0f, 0, 1);
            case ANIM_STYLE_CLOSE_EXIT:
                return makeOpenCloseAnimation(1.0f, 1.075f, 1, 0);
            case ANIM_STYLE_FADE_ENTER:
                return makeFadeAnimation(0, 1);
            case ANIM_STYLE_FADE_EXIT:
                return makeFadeAnimation(1, 0);
        }

        // TODO: remove or fix transitionStyle -- it apparently never worked.
        if (transitionStyle == 0 && mHost.onHasWindowAnimations()) {
            transitionStyle = mHost.onGetWindowAnimations();
        }
        if (transitionStyle == 0) {
            return null;
        }

        //TypedArray attrs = mActivity.obtainStyledAttributes(transitionStyle,
        //        com.android.internal.R.styleable.FragmentAnimation);
        //int anim = attrs.getResourceId(styleIndex, 0);
        //attrs.recycle();

        //if (anim == 0) {
        //    return null;
        //}

        //return AnimatorInflater.loadAnimator(mActivity, anim);
        return null;
    }

    public void performPendingDeferredStart(Fragment f) {
        if (f.mDeferStart) {
            if (mExecutingActions) {
                // Wait until we're done executing our pending transactions
                mHavePendingDeferredStart = true;
                return;
            }
            f.mDeferStart = false;
            moveToState(f, mCurState, 0, 0, false);
        }
    }

    boolean isStateAtLeast(int state) {
        return mCurState >= state;
    }

    @SuppressWarnings("ReferenceEquality")
    void moveToState(Fragment f, int newState, int transit, int transitionStyle,
                     boolean keepActive) {
        // Fragments that are not currently added will sit in the onCreate() state.
        if ((!f.mAdded || f.mDetached) && newState > Fragment.CREATED) {
            newState = Fragment.CREATED;
        }
        if (f.mRemoving && newState > f.mState) {
            if (f.mState == Fragment.INITIALIZING && f.isInBackStack()) {
                // Allow the fragment to be created so that it can be saved later.
                newState = Fragment.CREATED;
            } else {
                // While removing a fragment, we can't change it to a higher state.
                newState = f.mState;
            }
        }
        // Defer start if requested; don't allow it to move to STARTED or higher
        // if it's not already started.
        if (f.mDeferStart && f.mState < Fragment.STARTED && newState > Fragment.ACTIVITY_CREATED) {
            newState = Fragment.ACTIVITY_CREATED;
        }
        // Don't allow the Fragment to go above its max lifecycle state
        // Ensure that Fragments are capped at CREATED instead of ACTIVITY_CREATED.
        if (f.mMaxState == Lifecycle.State.CREATED) {
            newState = Math.min(newState, Fragment.CREATED);
        } else {
            newState = Math.min(newState, f.mMaxState.ordinal());
        }
        if (f.mState <= newState) {
            // For fragments that are created from a layout, when restoring from
            // state we don't want to allow them to be created until they are
            // being reloaded from the layout.
            if (f.mFromLayout && !f.mInLayout) {
                return;
            }
            if (f.getAnimatingAway() != null || f.getAnimator() != null) {
                // The fragment is currently being animated...  but!  Now we
                // want to move our state back up.  Give up on waiting for the
                // animation, move to whatever the final state should be once
                // the animation is done, and then we can proceed from there.
                f.setAnimatingAway(null);
                f.setAnimator(null);
                moveToState(f, f.getStateAfterAnimating(), 0, 0, true);
            }
            switch (f.mState) {
                case Fragment.INITIALIZING:
                    if (newState > Fragment.INITIALIZING) {
                        if (DEBUG) Log.v(TAG, "moveto CREATED: " + f);
                        if (f.mSavedFragmentState != null) {
                            f.mSavedFragmentState.setClassLoader(mHost.getContext()
                                    .getClassLoader());
                            f.mSavedViewState = f.mSavedFragmentState.getSparseParcelableArray(
                                    FragmentManagerImpl.VIEW_STATE_TAG);
                            Fragment target = getFragment(f.mSavedFragmentState,
                                    FragmentManagerImpl.TARGET_STATE_TAG);
                            f.mTargetWho = target != null ? target.mWho : null;
                            if (f.mTargetWho != null) {
                                f.mTargetRequestCode = f.mSavedFragmentState.getInt(
                                        FragmentManagerImpl.TARGET_REQUEST_CODE_STATE_TAG, 0);
                            }
                            if (f.mSavedUserVisibleHint != null) {
                                f.mUserVisibleHint = f.mSavedUserVisibleHint;
                                f.mSavedUserVisibleHint = null;
                            } else {
                                f.mUserVisibleHint = f.mSavedFragmentState.getBoolean(
                                        FragmentManagerImpl.USER_VISIBLE_HINT_TAG, true);
                            }
                            if (!f.mUserVisibleHint) {
                                f.mDeferStart = true;
                                if (newState > Fragment.ACTIVITY_CREATED) {
                                    newState = Fragment.ACTIVITY_CREATED;
                                }
                            }
                        }

                        f.mHost = mHost;
                        f.mParentFragment = mParent;
                        f.mFragmentManager = mParent != null
                                ? mParent.mChildFragmentManager : mHost.mFragmentManager;

                        // If we have a target fragment, push it along to at least CREATED
                        // so that this one can rely on it as an initialized dependency.
                        if (f.mTarget != null) {
                            if (mActive.get(f.mTarget.mWho) != f.mTarget) {
                                throw new IllegalStateException("Fragment " + f
                                        + " declared target fragment " + f.mTarget
                                        + " that does not belong to this FragmentManager!");
                            }
                            if (f.mTarget.mState < Fragment.CREATED) {
                                moveToState(f.mTarget, Fragment.CREATED, 0, 0, true);
                            }
                            f.mTargetWho = f.mTarget.mWho;
                            f.mTarget = null;
                        }
                        if (f.mTargetWho != null) {
                            Fragment target = mActive.get(f.mTargetWho);
                            if (target == null) {
                                throw new IllegalStateException("Fragment " + f
                                        + " declared target fragment " + f.mTargetWho
                                        + " that does not belong to this FragmentManager!");
                            }
                            if (target.mState < Fragment.CREATED) {
                                moveToState(target, Fragment.CREATED, 0, 0, true);
                            }
                        }

                        dispatchOnFragmentPreAttached(f, mHost.getContext(), false);
                        f.performAttach();
                        if (f.mParentFragment == null) {
                            mHost.onAttachFragment(f);
                        } else {
                            f.mParentFragment.onAttachFragment(f);
                        }
                        dispatchOnFragmentAttached(f, mHost.getContext(), false);

                        if (!f.mIsCreated) {
                            dispatchOnFragmentPreCreated(f, f.mSavedFragmentState, false);
                            f.performCreate(f.mSavedFragmentState);
                            dispatchOnFragmentCreated(f, f.mSavedFragmentState, false);
                        } else {
                            f.restoreChildFragmentState(f.mSavedFragmentState);
                            f.mState = Fragment.CREATED;
                        }
                    }
                    // fall through
                case Fragment.CREATED:
                    // We want to unconditionally run this anytime we do a moveToState that
                    // moves the Fragment above INITIALIZING, including cases such as when
                    // we move from CREATED => CREATED as part of the case fall through above.
                    if (newState > Fragment.INITIALIZING) {
                        ensureInflatedFragmentView(f);
                    }

                    if (newState > Fragment.CREATED) {
                        if (DEBUG) Log.v(TAG, "moveto ACTIVITY_CREATED: " + f);
                        if (!f.mFromLayout) {
                            ViewGroup container = null;
                            if (f.mContainerId != 0) {
                                if (f.mContainerId == View.NO_ID) {
                                    throwException(new IllegalArgumentException(
                                            "Cannot create fragment "
                                                    + f
                                                    + " for a container view with no id"));
                                }
                                container = (ViewGroup) mContainer.onFindViewById(f.mContainerId);
                                if (container == null && !f.mRestored) {
                                    String resName;
                                    try {
                                        resName = f.getResources().getResourceName(f.mContainerId);
                                    } catch (Resources.NotFoundException e) {
                                        resName = "unknown";
                                    }
                                    throwException(new IllegalArgumentException(
                                            "No view found for id 0x"
                                                    + Integer.toHexString(f.mContainerId) + " ("
                                                    + resName
                                                    + ") for fragment " + f));
                                }
                            }
                            f.mContainer = container;
                            f.performCreateView(f.performGetLayoutInflater(
                                    f.mSavedFragmentState), container, f.mSavedFragmentState);
                            if (f.mView != null) {
                                f.mInnerView = f.mView;
                                f.mView.setSaveFromParentEnabled(false);
                                if (container != null) {
                                    container.addView(f.mView);
                                }
                                if (f.mHidden) {
                                    f.mView.setVisibility(View.GONE);
                                }
                                f.onViewCreated(f.mView, f.mSavedFragmentState);
                                dispatchOnFragmentViewCreated(f, f.mView, f.mSavedFragmentState,
                                        false);
                                // Only animate the view if it is visible. This is done after
                                // dispatchOnFragmentViewCreated in case visibility is changed
                                f.mIsNewlyAdded = (f.mView.getVisibility() == View.VISIBLE)
                                        && f.mContainer != null;
                            } else {
                                f.mInnerView = null;
                            }
                        }

                        f.performActivityCreated(f.mSavedFragmentState);
                        dispatchOnFragmentActivityCreated(f, f.mSavedFragmentState, false);
                        if (f.mView != null) {
                            f.restoreViewState(f.mSavedFragmentState);
                        }
                        f.mSavedFragmentState = null;
                    }
                    // fall through
                case Fragment.ACTIVITY_CREATED:
                    if (newState > Fragment.ACTIVITY_CREATED) {
                        if (DEBUG) Log.v(TAG, "moveto STARTED: " + f);
                        f.performStart();
                        dispatchOnFragmentStarted(f, false);
                    }
                    // fall through
                case Fragment.STARTED:
                    if (newState > Fragment.STARTED) {
                        if (DEBUG) Log.v(TAG, "moveto RESUMED: " + f);
                        f.performResume();
                        dispatchOnFragmentResumed(f, false);
                        f.mSavedFragmentState = null;
                        f.mSavedViewState = null;
                    }
            }
        } else if (f.mState > newState) {
            switch (f.mState) {
                case Fragment.RESUMED:
                    if (newState < Fragment.RESUMED) {
                        if (DEBUG) Log.v(TAG, "movefrom RESUMED: " + f);
                        f.performPause();
                        dispatchOnFragmentPaused(f, false);
                    }
                    // fall through
                case Fragment.STARTED:
                    if (newState < Fragment.STARTED) {
                        if (DEBUG) Log.v(TAG, "movefrom STARTED: " + f);
                        f.performStop();
                        dispatchOnFragmentStopped(f, false);
                    }
                    // fall through
                case Fragment.ACTIVITY_CREATED:
                    if (newState < Fragment.ACTIVITY_CREATED) {
                        if (DEBUG) Log.v(TAG, "movefrom ACTIVITY_CREATED: " + f);
                        if (f.mView != null) {
                            // Need to save the current view state if not
                            // done already.
                            if (mHost.onShouldSaveFragmentState(f) && f.mSavedViewState == null) {
                                saveFragmentViewState(f);
                            }
                        }
                        f.performDestroyView();
                        dispatchOnFragmentViewDestroyed(f, false);
                        if (f.mView != null && f.mContainer != null) {
                            // Stop any current animations:
                            f.mContainer.endViewTransition(f.mView);
                            f.mView.clearAnimation();
                            AnimationOrAnimator anim = null;
                            // If parent is being removed, no need to handle child animations.
                            if (f.getParentFragment() == null || !f.getParentFragment().mRemoving) {
                                if (mCurState > Fragment.INITIALIZING && !mDestroyed
                                        && f.mView.getVisibility() == View.VISIBLE
                                        && f.mPostponedAlpha >= 0) {
                                    anim = loadAnimation(f, transit, false,
                                            transitionStyle);
                                }
                                f.mPostponedAlpha = 0;
                                if (anim != null) {
                                    animateRemoveFragment(f, anim, newState);
                                }
                                f.mContainer.removeView(f.mView);
                            }
                        }
                        f.mContainer = null;
                        f.mView = null;
                        // Set here to ensure that Observers are called after
                        // the Fragment's view is set to null
                        f.mViewLifecycleOwner = null;
                        f.mViewLifecycleOwnerLiveData.setValue(null);
                        f.mInnerView = null;
                        f.mInLayout = false;
                    }
                    // fall through
                case Fragment.CREATED:
                    if (newState < Fragment.CREATED) {
                        if (mDestroyed) {
                            // The fragment's containing activity is
                            // being destroyed, but this fragment is
                            // currently animating away.  Stop the
                            // animation right now -- it is not needed,
                            // and we can't wait any more on destroying
                            // the fragment.
                            if (f.getAnimatingAway() != null) {
                                View v = f.getAnimatingAway();
                                f.setAnimatingAway(null);
                                v.clearAnimation();
                            } else if (f.getAnimator() != null) {
                                Animator animator = f.getAnimator();
                                f.setAnimator(null);
                                animator.cancel();
                            }
                        }
                        if (f.getAnimatingAway() != null || f.getAnimator() != null) {
                            // We are waiting for the fragment's view to finish
                            // animating away.  Just make a note of the state
                            // the fragment now should move to once the animation
                            // is done.
                            f.setStateAfterAnimating(newState);
                            newState = Fragment.CREATED;
                        } else {
                            if (DEBUG) Log.v(TAG, "movefrom CREATED: " + f);
                            boolean beingRemoved = f.mRemoving && !f.isInBackStack();
                            if (beingRemoved || mNonConfig.shouldDestroy(f)) {
                                boolean shouldClear;
                                if (mHost instanceof ViewModelStoreOwner) {
                                    shouldClear = mNonConfig.isCleared();
                                } else if (mHost.getContext() instanceof Activity) {
                                    Activity activity = (Activity) mHost.getContext();
                                    shouldClear = !activity.isChangingConfigurations();
                                } else {
                                    shouldClear = true;
                                }
                                if (beingRemoved || shouldClear) {
                                    mNonConfig.clearNonConfigState(f);
                                }
                                f.performDestroy();
                                dispatchOnFragmentDestroyed(f, false);
                            } else {
                                f.mState = Fragment.INITIALIZING;
                            }

                            f.performDetach();
                            dispatchOnFragmentDetached(f, false);
                            if (!keepActive) {
                                if (beingRemoved || mNonConfig.shouldDestroy(f)) {
                                    makeInactive(f);
                                } else {
                                    f.mHost = null;
                                    f.mParentFragment = null;
                                    f.mFragmentManager = null;
                                    if (f.mTargetWho != null) {
                                        Fragment target = mActive.get(f.mTargetWho);
                                        if (target != null && target.getRetainInstance()) {
                                            // Only keep references to other retained Fragments
                                            // to avoid developers accessing Fragments that
                                            // are never coming back
                                            f.mTarget = target;
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }

        if (f.mState != newState) {
            Log.w(TAG, "moveToState: Fragment state for " + f + " not updated inline; "
                    + "expected state " + newState + " found " + f.mState);
            f.mState = newState;
        }
    }

    /**
     * Animates the removal of a fragment with the given animator or animation. After animating,
     * the fragment's view will be removed from the hierarchy.
     *
     * @param fragment The fragment to animate out
     * @param anim The animator or animation to run on the fragment's view
     * @param newState The final state after animating.
     */
    private void animateRemoveFragment(@NonNull final Fragment fragment,
                                       @NonNull AnimationOrAnimator anim, final int newState) {
        final View viewToAnimate = fragment.mView;
        final ViewGroup container = fragment.mContainer;
        container.startViewTransition(viewToAnimate);
        fragment.setStateAfterAnimating(newState);
        if (anim.animation != null) {
            Animation animation =
                    new EndViewTransitionAnimation(anim.animation, container, viewToAnimate);
            fragment.setAnimatingAway(fragment.mView);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    // onAnimationEnd() comes during draw(), so there can still be some
                    // draw events happening after this call. We don't want to detach
                    // the view until after the onAnimationEnd()
                    container.post(new Runnable() {
                        @Override
                        public void run() {
                            if (fragment.getAnimatingAway() != null) {
                                fragment.setAnimatingAway(null);
                                moveToState(fragment, fragment.getStateAfterAnimating(), 0, 0,
                                        false);
                            }
                        }
                    });
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            fragment.mView.startAnimation(animation);
        } else {
            Animator animator = anim.animator;
            fragment.setAnimator(anim.animator);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator anim) {
                    container.endViewTransition(viewToAnimate);
                    // If an animator ends immediately, we can just pretend there is no animation.
                    // When that happens the the fragment's view won't have been removed yet.
                    Animator animator = fragment.getAnimator();
                    fragment.setAnimator(null);
                    if (animator != null && container.indexOfChild(viewToAnimate) < 0) {
                        moveToState(fragment, fragment.getStateAfterAnimating(), 0, 0, false);
                    }
                }
            });
            animator.setTarget(fragment.mView);
            animator.start();
        }
    }

    void moveToState(Fragment f) {
        moveToState(f, mCurState, 0, 0, false);
    }

    void ensureInflatedFragmentView(Fragment f) {
        if (f.mFromLayout && !f.mPerformedCreateView) {
            f.performCreateView(f.performGetLayoutInflater(
                    f.mSavedFragmentState), null, f.mSavedFragmentState);
            if (f.mView != null) {
                f.mInnerView = f.mView;
                f.mView.setSaveFromParentEnabled(false);
                if (f.mHidden) f.mView.setVisibility(View.GONE);
                f.onViewCreated(f.mView, f.mSavedFragmentState);
                dispatchOnFragmentViewCreated(f, f.mView, f.mSavedFragmentState, false);
            } else {
                f.mInnerView = null;
            }
        }
    }

    /**
     * Fragments that have been shown or hidden don't have their visibility changed or
     * animations run during the {@link #showFragment(Fragment)} or {@link #hideFragment(Fragment)}
     * calls. After fragments are brought to their final state in
     * {@link #moveFragmentToExpectedState(Fragment)} the fragments that have been shown or
     * hidden must have their visibility changed and their animations started here.
     *
     * @param fragment The fragment with mHiddenChanged = true that should change its View's
     *                 visibility and start the show or hide animation.
     */
    void completeShowHideFragment(final Fragment fragment) {
        if (fragment.mView != null) {
            AnimationOrAnimator anim = loadAnimation(fragment, fragment.getNextTransition(),
                    !fragment.mHidden, fragment.getNextTransitionStyle());
            if (anim != null && anim.animator != null) {
                anim.animator.setTarget(fragment.mView);
                if (fragment.mHidden) {
                    if (fragment.isHideReplaced()) {
                        fragment.setHideReplaced(false);
                    } else {
                        final ViewGroup container = fragment.mContainer;
                        final View animatingView = fragment.mView;
                        container.startViewTransition(animatingView);
                        // Delay the actual hide operation until the animation finishes,
                        // otherwise the fragment will just immediately disappear
                        anim.animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                container.endViewTransition(animatingView);
                                animation.removeListener(this);
                                if (fragment.mView != null && fragment.mHidden) {
                                    fragment.mView.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                } else {
                    fragment.mView.setVisibility(View.VISIBLE);
                }
                anim.animator.start();
            } else {
                if (anim != null) {
                    fragment.mView.startAnimation(anim.animation);
                    anim.animation.start();
                }
                final int visibility = fragment.mHidden && !fragment.isHideReplaced()
                        ? View.GONE
                        : View.VISIBLE;
                fragment.mView.setVisibility(visibility);
                if (fragment.isHideReplaced()) {
                    fragment.setHideReplaced(false);
                }
            }
        }
        if (fragment.mAdded && isMenuAvailable(fragment)) {
            mNeedMenuInvalidate = true;
        }
        fragment.mHiddenChanged = false;
        fragment.onHiddenChanged(fragment.mHidden);
    }

    /**
     * Moves a fragment to its expected final state or the fragment manager's state, depending
     * on whether the fragment manager's state is raised properly.
     *
     * @param f The fragment to change.
     */
    void moveFragmentToExpectedState(Fragment f) {
        if (f == null) {
            return;
        }
        if (!mActive.containsKey(f.mWho)) {
            if (DEBUG) {
                Log.v(TAG, "Ignoring moving " + f + " to state " + mCurState
                        + "since it is not added to " + this);
            }
            return;
        }
        int nextState = mCurState;
        if (f.mRemoving) {
            if (f.isInBackStack()) {
                nextState = Math.min(nextState, Fragment.CREATED);
            } else {
                nextState = Math.min(nextState, Fragment.INITIALIZING);
            }
        }
        moveToState(f, nextState, f.getNextTransition(), f.getNextTransitionStyle(), false);

        if (f.mView != null) {
            // Move the view if it is out of order
            Fragment underFragment = findFragmentUnder(f);
            if (underFragment != null) {
                final View underView = underFragment.mView;
                // make sure this fragment is in the right order.
                final ViewGroup container = f.mContainer;
                int underIndex = container.indexOfChild(underView);
                int viewIndex = container.indexOfChild(f.mView);
                if (viewIndex < underIndex) {
                    container.removeViewAt(viewIndex);
                    container.addView(f.mView, underIndex);
                }
            }
            if (f.mIsNewlyAdded && f.mContainer != null) {
                // Make it visible and run the animations
                if (f.mPostponedAlpha > 0f) {
                    f.mView.setAlpha(f.mPostponedAlpha);
                }
                f.mPostponedAlpha = 0f;
                f.mIsNewlyAdded = false;
                // run animations:
                AnimationOrAnimator anim = loadAnimation(f, f.getNextTransition(), true,
                        f.getNextTransitionStyle());
                if (anim != null) {
                    if (anim.animation != null) {
                        f.mView.startAnimation(anim.animation);
                    } else {
                        anim.animator.setTarget(f.mView);
                        anim.animator.start();
                    }
                }
            }
        }
        if (f.mHiddenChanged) {
            completeShowHideFragment(f);
        }
    }

    /**
     * Changes the state of the fragment manager to {@code newState}. If the fragment manager
     * changes state or {@code always} is {@code true}, any fragments within it have their
     * states updated as well.
     *
     * @param newState The new state for the fragment manager
     * @param always If {@code true}, all fragments update their state, even
     *               if {@code newState} matches the current fragment manager's state.
     */
    void moveToState(int newState, boolean always) {
        if (mHost == null && newState != Fragment.INITIALIZING) {
            throw new IllegalStateException("No activity");
        }

        if (!always && newState == mCurState) {
            return;
        }

        mCurState = newState;

        // Must add them in the proper order. mActive fragments may be out of order
        final int numAdded = mAdded.size();
        for (int i = 0; i < numAdded; i++) {
            Fragment f = mAdded.get(i);
            moveFragmentToExpectedState(f);
        }

        // Now iterate through all active fragments. These will include those that are removed
        // and detached.
        for (Fragment f : mActive.values()) {
            if (f != null && (f.mRemoving || f.mDetached) && !f.mIsNewlyAdded) {
                moveFragmentToExpectedState(f);
            }
        }

        startPendingDeferredFragments();

        if (mNeedMenuInvalidate && mHost != null && mCurState == Fragment.RESUMED) {
            mHost.onSupportInvalidateOptionsMenu();
            mNeedMenuInvalidate = false;
        }
    }

    void startPendingDeferredFragments() {
        for (Fragment f : mActive.values()) {
            if (f != null) {
                performPendingDeferredStart(f);
            }
        }
    }

    void makeActive(Fragment f) {
        if (mActive.get(f.mWho) != null) {
            return;
        }

        mActive.put(f.mWho, f);
        if (f.mRetainInstanceChangedWhileDetached) {
            if (f.mRetainInstance) {
                addRetainedFragment(f);
            } else {
                removeRetainedFragment(f);
            }
            f.mRetainInstanceChangedWhileDetached = false;
        }
        if (DEBUG) Log.v(TAG, "Added fragment to active set " + f);
    }

    void makeInactive(Fragment f) {
        if (mActive.get(f.mWho) == null) {
            return;
        }

        if (DEBUG) Log.v(TAG, "Removed fragment from active set " + f);
        // Ensure that any Fragment that had this Fragment as its
        // target Fragment retains a reference to the Fragment
        for (Fragment fragment : mActive.values()) {
            if (fragment != null && f.mWho.equals(fragment.mTargetWho)) {
                fragment.mTarget = f;
                fragment.mTargetWho = null;
            }
        }
        // Don't remove yet. That happens in burpActive(). This prevents
        // concurrent modification while iterating over mActive
        mActive.put(f.mWho, null);
        removeRetainedFragment(f);

        if (f.mTargetWho != null) {
            // Restore the target Fragment so that it can be accessed
            // even after the Fragment is removed.
            f.mTarget = mActive.get(f.mTargetWho);
        }
        f.initState();
    }

    public void addFragment(Fragment fragment, boolean moveToStateNow) {
        if (DEBUG) Log.v(TAG, "add: " + fragment);
        makeActive(fragment);
        if (!fragment.mDetached) {
            if (mAdded.contains(fragment)) {
                throw new IllegalStateException("Fragment already added: " + fragment);
            }
            synchronized (mAdded) {
                mAdded.add(fragment);
            }
            fragment.mAdded = true;
            fragment.mRemoving = false;
            if (fragment.mView == null) {
                fragment.mHiddenChanged = false;
            }
            if (isMenuAvailable(fragment)) {
                mNeedMenuInvalidate = true;
            }
            if (moveToStateNow) {
                moveToState(fragment);
            }
        }
    }

    public void removeFragment(Fragment fragment) {
        if (DEBUG) Log.v(TAG, "remove: " + fragment + " nesting=" + fragment.mBackStackNesting);
        final boolean inactive = !fragment.isInBackStack();
        if (!fragment.mDetached || inactive) {
            synchronized (mAdded) {
                mAdded.remove(fragment);
            }
            if (isMenuAvailable(fragment)) {
                mNeedMenuInvalidate = true;
            }
            fragment.mAdded = false;
            fragment.mRemoving = true;
        }
    }

    /**
     * Marks a fragment as hidden to be later animated in with
     * {@link #completeShowHideFragment(Fragment)}.
     *
     * @param fragment The fragment to be shown.
     */
    public void hideFragment(Fragment fragment) {
        if (DEBUG) Log.v(TAG, "hide: " + fragment);
        if (!fragment.mHidden) {
            fragment.mHidden = true;
            // Toggle hidden changed so that if a fragment goes through show/hide/show
            // it doesn't go through the animation.
            fragment.mHiddenChanged = !fragment.mHiddenChanged;
        }
    }

    /**
     * Marks a fragment as shown to be later animated in with
     * {@link #completeShowHideFragment(Fragment)}.
     *
     * @param fragment The fragment to be shown.
     */
    public void showFragment(Fragment fragment) {
        if (DEBUG) Log.v(TAG, "show: " + fragment);
        if (fragment.mHidden) {
            fragment.mHidden = false;
            // Toggle hidden changed so that if a fragment goes through show/hide/show
            // it doesn't go through the animation.
            fragment.mHiddenChanged = !fragment.mHiddenChanged;
        }
    }

    public void detachFragment(Fragment fragment) {
        if (DEBUG) Log.v(TAG, "detach: " + fragment);
        if (!fragment.mDetached) {
            fragment.mDetached = true;
            if (fragment.mAdded) {
                // We are not already in back stack, so need to remove the fragment.
                if (DEBUG) Log.v(TAG, "remove from detach: " + fragment);
                synchronized (mAdded) {
                    mAdded.remove(fragment);
                }
                if (isMenuAvailable(fragment)) {
                    mNeedMenuInvalidate = true;
                }
                fragment.mAdded = false;
            }
        }
    }

    public void attachFragment(Fragment fragment) {
        if (DEBUG) Log.v(TAG, "attach: " + fragment);
        if (fragment.mDetached) {
            fragment.mDetached = false;
            if (!fragment.mAdded) {
                if (mAdded.contains(fragment)) {
                    throw new IllegalStateException("Fragment already added: " + fragment);
                }
                if (DEBUG) Log.v(TAG, "add from attach: " + fragment);
                synchronized (mAdded) {
                    mAdded.add(fragment);
                }
                fragment.mAdded = true;
                if (isMenuAvailable(fragment)) {
                    mNeedMenuInvalidate = true;
                }
            }
        }
    }

    @Override
    @Nullable
    public Fragment findFragmentById(int id) {
        // First look through added fragments.
        for (int i = mAdded.size() - 1; i >= 0; i--) {
            Fragment f = mAdded.get(i);
            if (f != null && f.mFragmentId == id) {
                return f;
            }
        }
        // Now for any known fragment.
        for (Fragment f : mActive.values()) {
            if (f != null && f.mFragmentId == id) {
                return f;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Fragment findFragmentByTag(@Nullable String tag) {
        if (tag != null) {
            // First look through added fragments.
            for (int i=mAdded.size()-1; i>=0; i--) {
                Fragment f = mAdded.get(i);
                if (f != null && tag.equals(f.mTag)) {
                    return f;
                }
            }
        }
        if (tag != null) {
            // Now for any known fragment.
            for (Fragment f : mActive.values()) {
                if (f != null && tag.equals(f.mTag)) {
                    return f;
                }
            }
        }
        return null;
    }

    public Fragment findFragmentByWho(@NonNull String who) {
        for (Fragment f : mActive.values()) {
            if (f != null && (f = f.findFragmentByWho(who)) != null) {
                return f;
            }
        }
        return null;
    }

    private void checkStateLoss() {
        if (isStateSaved()) {
            // MOD: Fix fragment state loss exception
            //throw new IllegalStateException(
            //        "Can not perform this action after onSaveInstanceState");
        }
    }

    @Override
    public boolean isStateSaved() {
        // See saveAllState() for the explanation of this.  We do this for
        // all platform versions, to keep our behavior more consistent between
        // them.
        return mStateSaved || mStopped;
    }

    /**
     * Adds an action to the queue of pending actions.
     *
     * @param action the action to add
     * @param allowStateLoss whether to allow loss of state information
     * @throws IllegalStateException if the activity has been destroyed
     */
    public void enqueueAction(OpGenerator action, boolean allowStateLoss) {
        if (!allowStateLoss) {
            checkStateLoss();
        }
        synchronized (this) {
            if (mDestroyed || mHost == null) {
                if (allowStateLoss) {
                    // This FragmentManager isn't attached, so drop the entire transaction.
                    return;
                }
                throw new IllegalStateException("Activity has been destroyed");
            }
            if (mPendingActions == null) {
                mPendingActions = new ArrayList<>();
            }
            mPendingActions.add(action);
            scheduleCommit();
        }
    }

    /**
     * Schedules the execution when one hasn't been scheduled already. This should happen
     * the first time {@link #enqueueAction(OpGenerator, boolean)} is called or when
     * a postponed transaction has been started with
     * {@link Fragment#startPostponedEnterTransition()}
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void scheduleCommit() {
        synchronized (this) {
            boolean postponeReady =
                    mPostponedTransactions != null && !mPostponedTransactions.isEmpty();
            boolean pendingReady = mPendingActions != null && mPendingActions.size() == 1;
            if (postponeReady || pendingReady) {
                mHost.getHandler().removeCallbacks(mExecCommit);
                mHost.getHandler().post(mExecCommit);
                updateOnBackPressedCallbackEnabled();
            }
        }
    }

    public int allocBackStackIndex(BackStackRecord bse) {
        synchronized (this) {
            if (mAvailBackStackIndices == null || mAvailBackStackIndices.size() <= 0) {
                if (mBackStackIndices == null) {
                    mBackStackIndices = new ArrayList<BackStackRecord>();
                }
                int index = mBackStackIndices.size();
                if (DEBUG) Log.v(TAG, "Setting back stack index " + index + " to " + bse);
                mBackStackIndices.add(bse);
                return index;

            } else {
                int index = mAvailBackStackIndices.remove(mAvailBackStackIndices.size()-1);
                if (DEBUG) Log.v(TAG, "Adding back stack index " + index + " with " + bse);
                mBackStackIndices.set(index, bse);
                return index;
            }
        }
    }

    public void setBackStackIndex(int index, BackStackRecord bse) {
        synchronized (this) {
            if (mBackStackIndices == null) {
                mBackStackIndices = new ArrayList<BackStackRecord>();
            }
            int N = mBackStackIndices.size();
            if (index < N) {
                if (DEBUG) Log.v(TAG, "Setting back stack index " + index + " to " + bse);
                mBackStackIndices.set(index, bse);
            } else {
                while (N < index) {
                    mBackStackIndices.add(null);
                    if (mAvailBackStackIndices == null) {
                        mAvailBackStackIndices = new ArrayList<Integer>();
                    }
                    if (DEBUG) Log.v(TAG, "Adding available back stack index " + N);
                    mAvailBackStackIndices.add(N);
                    N++;
                }
                if (DEBUG) Log.v(TAG, "Adding back stack index " + index + " with " + bse);
                mBackStackIndices.add(bse);
            }
        }
    }

    public void freeBackStackIndex(int index) {
        synchronized (this) {
            mBackStackIndices.set(index, null);
            if (mAvailBackStackIndices == null) {
                mAvailBackStackIndices = new ArrayList<Integer>();
            }
            if (DEBUG) Log.v(TAG, "Freeing back stack index " + index);
            mAvailBackStackIndices.add(index);
        }
    }

    /**
     * Broken out from exec*, this prepares for gathering and executing operations.
     *
     * @param allowStateLoss true if state loss should be ignored or false if it should be
     *                       checked.
     */
    private void ensureExecReady(boolean allowStateLoss) {
        if (mExecutingActions) {
            throw new IllegalStateException("FragmentManager is already executing transactions");
        }

        if (mHost == null) {
            throw new IllegalStateException("Fragment host has been destroyed");
        }

        if (Looper.myLooper() != mHost.getHandler().getLooper()) {
            throw new IllegalStateException("Must be called from main thread of fragment host");
        }

        if (!allowStateLoss) {
            checkStateLoss();
        }

        if (mTmpRecords == null) {
            mTmpRecords = new ArrayList<>();
            mTmpIsPop = new ArrayList<>();
        }
        mExecutingActions = true;
        try {
            executePostponedTransaction(null, null);
        } finally {
            mExecutingActions = false;
        }
    }

    public void execSingleAction(OpGenerator action, boolean allowStateLoss) {
        if (allowStateLoss && (mHost == null || mDestroyed)) {
            // This FragmentManager isn't attached, so drop the entire transaction.
            return;
        }
        ensureExecReady(allowStateLoss);
        if (action.generateOps(mTmpRecords, mTmpIsPop)) {
            mExecutingActions = true;
            try {
                removeRedundantOperationsAndExecute(mTmpRecords, mTmpIsPop);
            } finally {
                cleanupExec();
            }
        }

        updateOnBackPressedCallbackEnabled();
        doPendingDeferredStart();
        burpActive();
    }

    /**
     * Broken out of exec*, this cleans up the mExecutingActions and the temporary structures
     * used in executing operations.
     */
    private void cleanupExec() {
        mExecutingActions = false;
        mTmpIsPop.clear();
        mTmpRecords.clear();
    }

    /**
     * Only call from main thread!
     */
    public boolean execPendingActions() {
        ensureExecReady(true);

        boolean didSomething = false;
        while (generateOpsForPendingActions(mTmpRecords, mTmpIsPop)) {
            mExecutingActions = true;
            try {
                removeRedundantOperationsAndExecute(mTmpRecords, mTmpIsPop);
            } finally {
                cleanupExec();
            }
            didSomething = true;
        }

        updateOnBackPressedCallbackEnabled();
        doPendingDeferredStart();
        burpActive();

        return didSomething;
    }

    /**
     * Complete the execution of transactions that have previously been postponed, but are
     * now ready.
     */
    private void executePostponedTransaction(ArrayList<BackStackRecord> records,
                                             ArrayList<Boolean> isRecordPop) {
        int numPostponed = mPostponedTransactions == null ? 0 : mPostponedTransactions.size();
        for (int i = 0; i < numPostponed; i++) {
            StartEnterTransitionListener listener = mPostponedTransactions.get(i);
            if (records != null && !listener.mIsBack) {
                int index = records.indexOf(listener.mRecord);
                if (index != -1 && isRecordPop.get(index)) {
                    mPostponedTransactions.remove(i);
                    i--;
                    numPostponed--;
                    listener.cancelTransaction();
                    continue;
                }
            }
            if (listener.isReady() || (records != null
                    && listener.mRecord.interactsWith(records, 0, records.size()))) {
                mPostponedTransactions.remove(i);
                i--;
                numPostponed--;
                int index;
                if (records != null && !listener.mIsBack
                        && (index = records.indexOf(listener.mRecord)) != -1
                        && isRecordPop.get(index)) {
                    // This is popping a postponed transaction
                    listener.cancelTransaction();
                } else {
                    listener.completeTransaction();
                }
            }
        }
    }

    /**
     * Remove redundant BackStackRecord operations and executes them. This method merges operations
     * of proximate records that allow reordering. See
     * {@link FragmentTransaction#setReorderingAllowed(boolean)}.
     * <p>
     * For example, a transaction that adds to the back stack and then another that pops that
     * back stack record will be optimized to remove the unnecessary operation.
     * <p>
     * Likewise, two transactions committed that are executed at the same time will be optimized
     * to remove the redundant operations as well as two pop operations executed together.
     *
     * @param records The records pending execution
     * @param isRecordPop The direction that these records are being run.
     */
    private void removeRedundantOperationsAndExecute(ArrayList<BackStackRecord> records,
                                                     ArrayList<Boolean> isRecordPop) {
        if (records == null || records.isEmpty()) {
            return;
        }

        if (isRecordPop == null || records.size() != isRecordPop.size()) {
            throw new IllegalStateException("Internal error with the back stack records");
        }

        // Force start of any postponed transactions that interact with scheduled transactions:
        executePostponedTransaction(records, isRecordPop);

        final int numRecords = records.size();
        int startIndex = 0;
        for (int recordNum = 0; recordNum < numRecords; recordNum++) {
            final boolean canReorder = records.get(recordNum).mReorderingAllowed;
            if (!canReorder) {
                // execute all previous transactions
                if (startIndex != recordNum) {
                    executeOpsTogether(records, isRecordPop, startIndex, recordNum);
                }
                // execute all pop operations that don't allow reordering together or
                // one add operation
                int reorderingEnd = recordNum + 1;
                if (isRecordPop.get(recordNum)) {
                    while (reorderingEnd < numRecords
                            && isRecordPop.get(reorderingEnd)
                            && !records.get(reorderingEnd).mReorderingAllowed) {
                        reorderingEnd++;
                    }
                }
                executeOpsTogether(records, isRecordPop, recordNum, reorderingEnd);
                startIndex = reorderingEnd;
                recordNum = reorderingEnd - 1;
            }
        }
        if (startIndex != numRecords) {
            executeOpsTogether(records, isRecordPop, startIndex, numRecords);
        }
    }

    /**
     * Executes a subset of a list of BackStackRecords, all of which either allow reordering or
     * do not allow ordering.
     * @param records A list of BackStackRecords that are to be executed
     * @param isRecordPop The direction that these records are being run.
     * @param startIndex The index of the first record in <code>records</code> to be executed
     * @param endIndex One more than the final record index in <code>records</code> to executed.
     */
    private void executeOpsTogether(ArrayList<BackStackRecord> records,
                                    ArrayList<Boolean> isRecordPop, int startIndex, int endIndex) {
        final boolean allowReordering = records.get(startIndex).mReorderingAllowed;
        boolean addToBackStack = false;
        if (mTmpAddedFragments == null) {
            mTmpAddedFragments = new ArrayList<>();
        } else {
            mTmpAddedFragments.clear();
        }
        mTmpAddedFragments.addAll(mAdded);
        Fragment oldPrimaryNav = getPrimaryNavigationFragment();
        for (int recordNum = startIndex; recordNum < endIndex; recordNum++) {
            final BackStackRecord record = records.get(recordNum);
            final boolean isPop = isRecordPop.get(recordNum);
            if (!isPop) {
                oldPrimaryNav = record.expandOps(mTmpAddedFragments, oldPrimaryNav);
            } else {
                oldPrimaryNav = record.trackAddedFragmentsInPop(mTmpAddedFragments, oldPrimaryNav);
            }
            addToBackStack = addToBackStack || record.mAddToBackStack;
        }
        mTmpAddedFragments.clear();

        if (!allowReordering) {
            FragmentTransition.startTransitions(this, records, isRecordPop, startIndex, endIndex,
                    false);
        }
        executeOps(records, isRecordPop, startIndex, endIndex);

        int postponeIndex = endIndex;
        if (allowReordering) {
            ArraySet<Fragment> addedFragments = new ArraySet<>();
            addAddedFragments(addedFragments);
            postponeIndex = postponePostponableTransactions(records, isRecordPop,
                    startIndex, endIndex, addedFragments);
            makeRemovedFragmentsInvisible(addedFragments);
        }

        if (postponeIndex != startIndex && allowReordering) {
            // need to run something now
            FragmentTransition.startTransitions(this, records, isRecordPop, startIndex,
                    postponeIndex, true);
            moveToState(mCurState, true);
        }

        for (int recordNum = startIndex; recordNum < endIndex; recordNum++) {
            final BackStackRecord record = records.get(recordNum);
            final boolean isPop = isRecordPop.get(recordNum);
            if (isPop && record.mIndex >= 0) {
                freeBackStackIndex(record.mIndex);
                record.mIndex = -1;
            }
            record.runOnCommitRunnables();
        }
        if (addToBackStack) {
            reportBackStackChanged();
        }
    }

    /**
     * Any fragments that were removed because they have been postponed should have their views
     * made invisible by setting their alpha to 0.
     *
     * @param fragments The fragments that were added during operation execution. Only the ones
     *                  that are no longer added will have their alpha changed.
     */
    private void makeRemovedFragmentsInvisible(ArraySet<Fragment> fragments) {
        final int numAdded = fragments.size();
        for (int i = 0; i < numAdded; i++) {
            final Fragment fragment = fragments.valueAt(i);
            if (!fragment.mAdded) {
                final View view = fragment.requireView();
                fragment.mPostponedAlpha = view.getAlpha();
                view.setAlpha(0f);
            }
        }
    }

    /**
     * Examine all transactions and determine which ones are marked as postponed. Those will
     * have their operations rolled back and moved to the end of the record list (up to endIndex).
     * It will also add the postponed transaction to the queue.
     *
     * @param records A list of BackStackRecords that should be checked.
     * @param isRecordPop The direction that these records are being run.
     * @param startIndex The index of the first record in <code>records</code> to be checked
     * @param endIndex One more than the final record index in <code>records</code> to be checked.
     * @return The index of the first postponed transaction or endIndex if no transaction was
     * postponed.
     */
    private int postponePostponableTransactions(ArrayList<BackStackRecord> records,
                                                ArrayList<Boolean> isRecordPop, int startIndex, int endIndex,
                                                ArraySet<Fragment> added) {
        int postponeIndex = endIndex;
        for (int i = endIndex - 1; i >= startIndex; i--) {
            final BackStackRecord record = records.get(i);
            final boolean isPop = isRecordPop.get(i);
            boolean isPostponed = record.isPostponed()
                    && !record.interactsWith(records, i + 1, endIndex);
            if (isPostponed) {
                if (mPostponedTransactions == null) {
                    mPostponedTransactions = new ArrayList<>();
                }
                StartEnterTransitionListener listener =
                        new StartEnterTransitionListener(record, isPop);
                mPostponedTransactions.add(listener);
                record.setOnStartPostponedListener(listener);

                // roll back the transaction
                if (isPop) {
                    record.executeOps();
                } else {
                    record.executePopOps(false);
                }

                // move to the end
                postponeIndex--;
                if (i != postponeIndex) {
                    records.remove(i);
                    records.add(postponeIndex, record);
                }

                // different views may be visible now
                addAddedFragments(added);
            }
        }
        return postponeIndex;
    }

    /**
     * When a postponed transaction is ready to be started, this completes the transaction,
     * removing, hiding, or showing views as well as starting the animations and transitions.
     * <p>
     * {@code runtransitions} is set to false when the transaction postponement was interrupted
     * abnormally -- normally by a new transaction being started that affects the postponed
     * transaction.
     *
     * @param record The transaction to run
     * @param isPop true if record is popping or false if it is adding
     * @param runTransitions true if the fragment transition should be run or false otherwise.
     * @param moveToState true if the state should be changed after executing the operations.
     *                    This is false when the transaction is canceled when a postponed
     *                    transaction is popped.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void completeExecute(BackStackRecord record, boolean isPop, boolean runTransitions,
                         boolean moveToState) {
        if (isPop) {
            record.executePopOps(moveToState);
        } else {
            record.executeOps();
        }
        ArrayList<BackStackRecord> records = new ArrayList<>(1);
        ArrayList<Boolean> isRecordPop = new ArrayList<>(1);
        records.add(record);
        isRecordPop.add(isPop);
        if (runTransitions) {
            FragmentTransition.startTransitions(this, records, isRecordPop, 0, 1, true);
        }
        if (moveToState) {
            moveToState(mCurState, true);
        }

        for (Fragment fragment : mActive.values()) {
            // Allow added fragments to be removed during the pop since we aren't going
            // to move them to the final state with moveToState(mCurState).
            if (fragment != null && fragment.mView != null && fragment.mIsNewlyAdded
                    && record.interactsWith(fragment.mContainerId)) {
                if (fragment.mPostponedAlpha > 0) {
                    fragment.mView.setAlpha(fragment.mPostponedAlpha);
                }
                if (moveToState) {
                    fragment.mPostponedAlpha = 0;
                } else {
                    fragment.mPostponedAlpha = -1;
                    fragment.mIsNewlyAdded = false;
                }
            }
        }
    }

    /**
     * Find a fragment within the fragment's container whose View should be below the passed
     * fragment. {@code null} is returned when the fragment has no View or if there should be
     * no fragment with a View below the given fragment.
     *
     * As an example, if mAdded has two Fragments with Views sharing the same container:
     * FragmentA
     * FragmentB
     *
     * Then, when processing FragmentB, FragmentA will be returned. If, however, FragmentA
     * had no View, null would be returned.
     *
     * @param f The fragment that may be on top of another fragment.
     * @return The fragment with a View under f, if one exists or null if f has no View or
     * there are no fragments with Views in the same container.
     */
    private Fragment findFragmentUnder(Fragment f) {
        final ViewGroup container = f.mContainer;
        final View view = f.mView;

        if (container == null || view == null) {
            return null;
        }

        final int fragmentIndex = mAdded.indexOf(f);
        for (int i = fragmentIndex - 1; i >= 0; i--) {
            Fragment underFragment = mAdded.get(i);
            if (underFragment.mContainer == container && underFragment.mView != null) {
                // Found the fragment under this one
                return underFragment;
            }
        }
        return null;
    }

    /**
     * Run the operations in the BackStackRecords, either to push or pop.
     *
     * @param records The list of records whose operations should be run.
     * @param isRecordPop The direction that these records are being run.
     * @param startIndex The index of the first entry in records to run.
     * @param endIndex One past the index of the final entry in records to run.
     */
    private static void executeOps(ArrayList<BackStackRecord> records,
                                   ArrayList<Boolean> isRecordPop, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            final BackStackRecord record = records.get(i);
            final boolean isPop = isRecordPop.get(i);
            if (isPop) {
                record.bumpBackStackNesting(-1);
                // Only execute the add operations at the end of
                // all transactions.
                boolean moveToState = i == (endIndex - 1);
                record.executePopOps(moveToState);
            } else {
                record.bumpBackStackNesting(1);
                record.executeOps();
            }
        }
    }

    /**
     * Ensure that fragments that are added are moved to at least the CREATED state.
     * Any newly-added Views are inserted into {@code added} so that the Transaction can be
     * postponed with {@link Fragment#postponeEnterTransition()}. They will later be made
     * invisible (by setting their alpha to 0) if they have been removed when postponed.
     */
    private void addAddedFragments(ArraySet<Fragment> added) {
        if (mCurState < Fragment.CREATED) {
            return;
        }
        // We want to leave the fragment in the started state
        final int state = Math.min(mCurState, Fragment.STARTED);
        final int numAdded = mAdded.size();
        for (int i = 0; i < numAdded; i++) {
            Fragment fragment = mAdded.get(i);
            if (fragment.mState < state) {
                moveToState(fragment, state, fragment.getNextAnim(), fragment.getNextTransition(),
                        false);
                if (fragment.mView != null && !fragment.mHidden && fragment.mIsNewlyAdded) {
                    added.add(fragment);
                }
            }
        }
    }

    /**
     * Starts all postponed transactions regardless of whether they are ready or not.
     */
    private void forcePostponedTransactions() {
        if (mPostponedTransactions != null) {
            while (!mPostponedTransactions.isEmpty()) {
                mPostponedTransactions.remove(0).completeTransaction();
            }
        }
    }

    /**
     * Ends the animations of fragments so that they immediately reach the end state.
     * This is used prior to saving the state so that the correct state is saved.
     */
    private void endAnimatingAwayFragments() {
        for (Fragment fragment : mActive.values()) {
            if (fragment != null) {
                if (fragment.getAnimatingAway() != null) {
                    // Give up waiting for the animation and just end it.
                    final int stateAfterAnimating = fragment.getStateAfterAnimating();
                    final View animatingAway = fragment.getAnimatingAway();
                    Animation animation = animatingAway.getAnimation();
                    if (animation != null) {
                        animation.cancel();
                        // force-clear the animation, as Animation#cancel() doesn't work prior to N,
                        // and will instead cause the animation to infinitely loop
                        animatingAway.clearAnimation();
                    }
                    fragment.setAnimatingAway(null);
                    moveToState(fragment, stateAfterAnimating, 0, 0, false);
                } else if (fragment.getAnimator() != null) {
                    fragment.getAnimator().end();
                }
            }
        }
    }

    /**
     * Adds all records in the pending actions to records and whether they are add or pop
     * operations to isPop. After executing, the pending actions will be empty.
     *
     * @param records All pending actions will generate BackStackRecords added to this.
     *                This contains the transactions, in order, to execute.
     * @param isPop All pending actions will generate booleans to add to this. This contains
     *              an entry for each entry in records to indicate whether or not it is a
     *              pop action.
     */
    private boolean generateOpsForPendingActions(ArrayList<BackStackRecord> records,
                                                 ArrayList<Boolean> isPop) {
        boolean didSomething = false;
        synchronized (this) {
            if (mPendingActions == null || mPendingActions.size() == 0) {
                return false;
            }

            final int numActions = mPendingActions.size();
            for (int i = 0; i < numActions; i++) {
                didSomething |= mPendingActions.get(i).generateOps(records, isPop);
            }
            mPendingActions.clear();
            mHost.getHandler().removeCallbacks(mExecCommit);
        }
        return didSomething;
    }

    void doPendingDeferredStart() {
        if (mHavePendingDeferredStart) {
            mHavePendingDeferredStart = false;
            startPendingDeferredFragments();
        }
    }

    void reportBackStackChanged() {
        if (mBackStackChangeListeners != null) {
            for (int i=0; i<mBackStackChangeListeners.size(); i++) {
                mBackStackChangeListeners.get(i).onBackStackChanged();
            }
        }
    }

    void addBackStackState(BackStackRecord state) {
        if (mBackStack == null) {
            mBackStack = new ArrayList<BackStackRecord>();
        }
        mBackStack.add(state);
    }

    @SuppressWarnings("unused")
    boolean popBackStackState(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop,
                              String name, int id, int flags) {
        if (mBackStack == null) {
            return false;
        }
        if (name == null && id < 0 && (flags & POP_BACK_STACK_INCLUSIVE) == 0) {
            int last = mBackStack.size() - 1;
            if (last < 0) {
                return false;
            }
            records.add(mBackStack.remove(last));
            isRecordPop.add(true);
        } else {
            int index = -1;
            if (name != null || id >= 0) {
                // If a name or ID is specified, look for that place in
                // the stack.
                index = mBackStack.size()-1;
                while (index >= 0) {
                    BackStackRecord bss = mBackStack.get(index);
                    if (name != null && name.equals(bss.getName())) {
                        break;
                    }
                    if (id >= 0 && id == bss.mIndex) {
                        break;
                    }
                    index--;
                }
                if (index < 0) {
                    return false;
                }
                if ((flags&POP_BACK_STACK_INCLUSIVE) != 0) {
                    index--;
                    // Consume all following entries that match.
                    while (index >= 0) {
                        BackStackRecord bss = mBackStack.get(index);
                        if ((name != null && name.equals(bss.getName()))
                                || (id >= 0 && id == bss.mIndex)) {
                            index--;
                            continue;
                        }
                        break;
                    }
                }
            }
            if (index == mBackStack.size()-1) {
                return false;
            }
            for (int i = mBackStack.size() - 1; i > index; i--) {
                records.add(mBackStack.remove(i));
                isRecordPop.add(true);
            }
        }
        return true;
    }

    /**
     * @deprecated Ideally, all {@link androidx.fragment.app.FragmentHostCallback} instances
     * implement ViewModelStoreOwner and we can remove this method entirely.
     */
    @Deprecated
    FragmentManagerNonConfig retainNonConfig() {
        if (mHost instanceof ViewModelStoreOwner) {
            throwException(new IllegalStateException("You cannot use retainNonConfig when your "
                    + "FragmentHostCallback implements ViewModelStoreOwner."));
        }
        return mNonConfig.getSnapshot();
    }

    void saveFragmentViewState(Fragment f) {
        if (f.mInnerView == null) {
            return;
        }
        if (mStateArray == null) {
            mStateArray = new SparseArray<Parcelable>();
        } else {
            mStateArray.clear();
        }
        f.mInnerView.saveHierarchyState(mStateArray);
        if (mStateArray.size() > 0) {
            f.mSavedViewState = mStateArray;
            mStateArray = null;
        }
    }

    Bundle saveFragmentBasicState(Fragment f) {
        Bundle result = null;

        if (mStateBundle == null) {
            mStateBundle = new Bundle();
        }
        f.performSaveInstanceState(mStateBundle);
        dispatchOnFragmentSaveInstanceState(f, mStateBundle, false);
        if (!mStateBundle.isEmpty()) {
            result = mStateBundle;
            mStateBundle = null;
        }

        if (f.mView != null) {
            saveFragmentViewState(f);
        }
        if (f.mSavedViewState != null) {
            if (result == null) {
                result = new Bundle();
            }
            result.putSparseParcelableArray(
                    FragmentManagerImpl.VIEW_STATE_TAG, f.mSavedViewState);
        }
        if (!f.mUserVisibleHint) {
            if (result == null) {
                result = new Bundle();
            }
            // Only add this if it's not the default value
            result.putBoolean(FragmentManagerImpl.USER_VISIBLE_HINT_TAG, f.mUserVisibleHint);
        }

        return result;
    }

    Parcelable saveAllState() {
        // Make sure all pending operations have now been executed to get
        // our state update-to-date.
        forcePostponedTransactions();
        endAnimatingAwayFragments();
        execPendingActions();

        mStateSaved = true;

        if (mActive.isEmpty()) {
            return null;
        }

        // First collect all active fragments.
        int size = mActive.size();
        ArrayList<FragmentState> active = new ArrayList<>(size);
        boolean haveFragments = false;
        for (Fragment f : mActive.values()) {
            if (f != null) {
                if (f.mFragmentManager != this) {
                    throwException(new IllegalStateException(
                            "Failure saving state: active " + f
                                    + " was removed from the FragmentManager"));
                }

                haveFragments = true;

                FragmentState fs = new FragmentState(f);
                active.add(fs);

                if (f.mState > Fragment.INITIALIZING && fs.mSavedFragmentState == null) {
                    fs.mSavedFragmentState = saveFragmentBasicState(f);

                    if (f.mTargetWho != null) {
                        Fragment target = mActive.get(f.mTargetWho);
                        if (target == null) {
                            throwException(new IllegalStateException(
                                    "Failure saving state: " + f
                                            + " has target not in fragment manager: "
                                            + f.mTargetWho));
                        }
                        if (fs.mSavedFragmentState == null) {
                            fs.mSavedFragmentState = new Bundle();
                        }
                        putFragment(fs.mSavedFragmentState,
                                FragmentManagerImpl.TARGET_STATE_TAG, target);
                        if (f.mTargetRequestCode != 0) {
                            fs.mSavedFragmentState.putInt(
                                    FragmentManagerImpl.TARGET_REQUEST_CODE_STATE_TAG,
                                    f.mTargetRequestCode);
                        }
                    }

                } else {
                    fs.mSavedFragmentState = f.mSavedFragmentState;
                }

                if (DEBUG) Log.v(TAG, "Saved state of " + f + ": "
                        + fs.mSavedFragmentState);
            }
        }

        if (!haveFragments) {
            if (DEBUG) Log.v(TAG, "saveAllState: no fragments!");
            return null;
        }

        ArrayList<String> added = null;
        BackStackState[] backStack = null;

        // Build list of currently added fragments.
        size = mAdded.size();
        if (size > 0) {
            added = new ArrayList<>(size);
            for (Fragment f : mAdded) {
                added.add(f.mWho);
                if (f.mFragmentManager != this) {
                    throwException(new IllegalStateException(
                            "Failure saving state: active " + f
                                    + " was removed from the FragmentManager"));
                }
                if (DEBUG) {
                    Log.v(TAG, "saveAllState: adding fragment (" + f.mWho
                            + "): " + f);
                }
            }
        }

        // Now save back stack.
        if (mBackStack != null) {
            size = mBackStack.size();
            if (size > 0) {
                backStack = new BackStackState[size];
                for (int i = 0; i < size; i++) {
                    backStack[i] = new BackStackState(mBackStack.get(i));
                    if (DEBUG) Log.v(TAG, "saveAllState: adding back stack #" + i
                            + ": " + mBackStack.get(i));
                }
            }
        }

        FragmentManagerState fms = new FragmentManagerState();
        fms.mActive = active;
        fms.mAdded = added;
        fms.mBackStack = backStack;
        if (mPrimaryNav != null) {
            fms.mPrimaryNavActiveWho = mPrimaryNav.mWho;
        }
        fms.mNextFragmentIndex = mNextFragmentIndex;
        return fms;
    }

    void restoreAllState(Parcelable state, FragmentManagerNonConfig nonConfig) {
        if (mHost instanceof ViewModelStoreOwner) {
            throwException(new IllegalStateException("You must use restoreSaveState when your "
                    + "FragmentHostCallback implements ViewModelStoreOwner"));
        }
        mNonConfig.restoreFromSnapshot(nonConfig);
        restoreSaveState(state);
    }

    void restoreSaveState(Parcelable state) {
        // If there is no saved state at all, then there's nothing else to do
        if (state == null) return;
        FragmentManagerState fms = (FragmentManagerState)state;
        if (fms.mActive == null) return;

        // First re-attach any non-config instances we are retaining back
        // to their saved state, so we don't try to instantiate them again.
        for (Fragment f : mNonConfig.getRetainedFragments()) {
            if (DEBUG) Log.v(TAG, "restoreSaveState: re-attaching retained " + f);
            FragmentState fs = null;
            for (FragmentState fragmentState : fms.mActive) {
                if (fragmentState.mWho.equals(f.mWho)) {
                    fs = fragmentState;
                    break;
                }
            }
            if (fs == null) {
                if (DEBUG) {
                    Log.v(TAG, "Discarding retained Fragment " + f
                            + " that was not found in the set of active Fragments " + fms.mActive);
                }
                // We need to ensure that onDestroy and any other clean up is done
                // so move the Fragment up to CREATED, then mark it as being removed, then
                // destroy it.
                moveToState(f, Fragment.CREATED, 0, 0, false);
                f.mRemoving = true;
                moveToState(f, Fragment.INITIALIZING, 0, 0, false);
                continue;
            }
            fs.mInstance = f;
            f.mSavedViewState = null;
            f.mBackStackNesting = 0;
            f.mInLayout = false;
            f.mAdded = false;
            f.mTargetWho = f.mTarget != null ? f.mTarget.mWho : null;
            f.mTarget = null;
            if (fs.mSavedFragmentState != null) {
                fs.mSavedFragmentState.setClassLoader(mHost.getContext().getClassLoader());
                f.mSavedViewState = fs.mSavedFragmentState.getSparseParcelableArray(
                        FragmentManagerImpl.VIEW_STATE_TAG);
                f.mSavedFragmentState = fs.mSavedFragmentState;
            }
        }

        // Build the full list of active fragments, instantiating them from
        // their saved state.
        mActive.clear();
        for (FragmentState fs : fms.mActive) {
            if (fs != null) {
                Fragment f = fs.instantiate(mHost.getContext().getClassLoader(),
                        getFragmentFactory());
                f.mFragmentManager = this;
                if (DEBUG) Log.v(TAG, "restoreSaveState: active (" + f.mWho + "): " + f);
                mActive.put(f.mWho, f);
                // Now that the fragment is instantiated (or came from being
                // retained above), clear mInstance in case we end up re-restoring
                // from this FragmentState again.
                fs.mInstance = null;
            }
        }

        // Build the list of currently added fragments.
        mAdded.clear();
        if (fms.mAdded != null) {
            for (String who : fms.mAdded) {
                Fragment f = mActive.get(who);
                if (f == null) {
                    throwException(new IllegalStateException(
                            "No instantiated fragment for (" + who + ")"));
                }
                f.mAdded = true;
                if (DEBUG) Log.v(TAG, "restoreSaveState: added (" + who + "): " + f);
                if (mAdded.contains(f)) {
                    throw new IllegalStateException("Already added " + f);
                }
                synchronized (mAdded) {
                    mAdded.add(f);
                }
            }
        }

        // Build the back stack.
        if (fms.mBackStack != null) {
            mBackStack = new ArrayList<BackStackRecord>(fms.mBackStack.length);
            for (int i=0; i<fms.mBackStack.length; i++) {
                BackStackRecord bse = fms.mBackStack[i].instantiate(this);
                if (DEBUG) {
                    Log.v(TAG, "restoreAllState: back stack #" + i
                            + " (index " + bse.mIndex + "): " + bse);
                    LogWriter logw = new LogWriter(TAG);
                    PrintWriter pw = new PrintWriter(logw);
                    bse.dump("  ", pw, false);
                    pw.close();
                }
                mBackStack.add(bse);
                if (bse.mIndex >= 0) {
                    setBackStackIndex(bse.mIndex, bse);
                }
            }
        } else {
            mBackStack = null;
        }

        if (fms.mPrimaryNavActiveWho != null) {
            mPrimaryNav = mActive.get(fms.mPrimaryNavActiveWho);
            dispatchParentPrimaryNavigationFragmentChanged(mPrimaryNav);
        }
        this.mNextFragmentIndex = fms.mNextFragmentIndex;
    }

    /**
     * To prevent list modification errors, mActive sets values to null instead of
     * removing them when the Fragment becomes inactive. This cleans up the list at the
     * end of executing the transactions.
     */
    private void burpActive() {
        Collection<Fragment> values = mActive.values();
        // values() provides a view into the map, so removing elements from it
        // removes the relevant pairs in the Map
        values.removeAll(Collections.singleton(null));
    }

    public void attachController(@NonNull FragmentHostCallback host,
            @NonNull FragmentContainer container, @Nullable final Fragment parent) {
        if (mHost != null) throw new IllegalStateException("Already attached");
        mHost = host;
        mContainer = container;
        mParent = parent;
        if (mParent != null) {
            // Since the callback depends on us being the primary navigation fragment,
            // update our callback now that we have a parent so that we have the correct
            // state by default
            updateOnBackPressedCallbackEnabled();
        }
        // Set up the OnBackPressedCallback
        if (host instanceof OnBackPressedDispatcherOwner) {
            OnBackPressedDispatcherOwner dispatcherOwner = ((OnBackPressedDispatcherOwner) host);
            mOnBackPressedDispatcher = dispatcherOwner.getOnBackPressedDispatcher();
            LifecycleOwner owner = parent != null ? parent : dispatcherOwner;
            mOnBackPressedDispatcher.addCallback(owner, mOnBackPressedCallback);
        }

        // Get the FragmentManagerViewModel
        if (parent != null) {
            mNonConfig = parent.mFragmentManager.getChildNonConfig(parent);
        } else if (host instanceof ViewModelStoreOwner) {
            ViewModelStore viewModelStore = ((ViewModelStoreOwner) host).getViewModelStore();
            mNonConfig = FragmentManagerViewModel.getInstance(viewModelStore);
        } else {
            mNonConfig = new FragmentManagerViewModel(false);
        }
    }

    public void noteStateNotSaved() {
        mStateSaved = false;
        mStopped = false;
        final int addedCount = mAdded.size();
        for (int i = 0; i < addedCount; i++) {
            Fragment fragment = mAdded.get(i);
            if (fragment != null) {
                fragment.noteStateNotSaved();
            }
        }
    }

    public void dispatchCreate() {
        mStateSaved = false;
        mStopped = false;
        dispatchStateChange(Fragment.CREATED);
    }

    public void dispatchActivityCreated() {
        mStateSaved = false;
        mStopped = false;
        dispatchStateChange(Fragment.ACTIVITY_CREATED);
    }

    public void dispatchStart() {
        mStateSaved = false;
        mStopped = false;
        dispatchStateChange(Fragment.STARTED);
    }

    public void dispatchResume() {
        mStateSaved = false;
        mStopped = false;
        dispatchStateChange(Fragment.RESUMED);
    }

    public void dispatchPause() {
        dispatchStateChange(Fragment.STARTED);
    }

    public void dispatchStop() {
        mStopped = true;
        dispatchStateChange(Fragment.ACTIVITY_CREATED);
    }

    public void dispatchDestroyView() {
        dispatchStateChange(Fragment.CREATED);
    }

    public void dispatchDestroy() {
        mDestroyed = true;
        execPendingActions();
        dispatchStateChange(Fragment.INITIALIZING);
        mHost = null;
        mContainer = null;
        mParent = null;
        if (mOnBackPressedDispatcher != null) {
            // mOnBackPressedDispatcher can hold a reference to the host
            // so we need to null it out to prevent memory leaks
            mOnBackPressedCallback.remove();
            mOnBackPressedDispatcher = null;
        }
    }

    private void dispatchStateChange(int nextState) {
        try {
            mExecutingActions = true;
            moveToState(nextState, false);
        } finally {
            mExecutingActions = false;
        }
        execPendingActions();
    }

    public void dispatchMultiWindowModeChanged(boolean isInMultiWindowMode) {
        for (int i = mAdded.size() - 1; i >= 0; --i) {
            final Fragment f = mAdded.get(i);
            if (f != null) {
                f.performMultiWindowModeChanged(isInMultiWindowMode);
            }
        }
    }

    public void dispatchPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        for (int i = mAdded.size() - 1; i >= 0; --i) {
            final Fragment f = mAdded.get(i);
            if (f != null) {
                f.performPictureInPictureModeChanged(isInPictureInPictureMode);
            }
        }
    }

    public void dispatchConfigurationChanged(@NonNull Configuration newConfig) {
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                f.performConfigurationChanged(newConfig);
            }
        }
    }

    public void dispatchLowMemory() {
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                f.performLowMemory();
            }
        }
    }

    public boolean dispatchCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        if (mCurState < Fragment.CREATED) {
            return false;
        }
        boolean show = false;
        ArrayList<Fragment> newMenus = null;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                if (f.performCreateOptionsMenu(menu, inflater)) {
                    show = true;
                    if (newMenus == null) {
                        newMenus = new ArrayList<Fragment>();
                    }
                    newMenus.add(f);
                }
            }
        }

        if (mCreatedMenus != null) {
            for (int i=0; i<mCreatedMenus.size(); i++) {
                Fragment f = mCreatedMenus.get(i);
                if (newMenus == null || !newMenus.contains(f)) {
                    f.onDestroyOptionsMenu();
                }
            }
        }

        mCreatedMenus = newMenus;

        return show;
    }

    public boolean dispatchPrepareOptionsMenu(@NonNull Menu menu) {
        if (mCurState < Fragment.CREATED) {
            return false;
        }
        boolean show = false;
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                if (f.performPrepareOptionsMenu(menu)) {
                    show = true;
                }
            }
        }
        return show;
    }

    public boolean dispatchOptionsItemSelected(@NonNull MenuItem item) {
        if (mCurState < Fragment.CREATED) {
            return false;
        }
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                if (f.performOptionsItemSelected(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean dispatchContextItemSelected(@NonNull MenuItem item) {
        if (mCurState < Fragment.CREATED) {
            return false;
        }
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                if (f.performContextItemSelected(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void dispatchOptionsMenuClosed(@NonNull Menu menu) {
        if (mCurState < Fragment.CREATED) {
            return;
        }
        for (int i = 0; i < mAdded.size(); i++) {
            Fragment f = mAdded.get(i);
            if (f != null) {
                f.performOptionsMenuClosed(menu);
            }
        }
    }

    @SuppressWarnings("ReferenceEquality")
    public void setPrimaryNavigationFragment(Fragment f) {
        if (f != null && (mActive.get(f.mWho) != f
                || (f.mHost != null && f.getFragmentManager() != this))) {
            throw new IllegalArgumentException("Fragment " + f
                    + " is not an active fragment of FragmentManager " + this);
        }
        Fragment previousPrimaryNav = mPrimaryNav;
        mPrimaryNav = f;
        dispatchParentPrimaryNavigationFragmentChanged(previousPrimaryNav);
        dispatchParentPrimaryNavigationFragmentChanged(mPrimaryNav);
    }

    private void dispatchParentPrimaryNavigationFragmentChanged(@Nullable Fragment f) {
        if (f != null && mActive.get(f.mWho) == f) {
            f.performPrimaryNavigationFragmentChanged();
        }
    }

    void dispatchPrimaryNavigationFragmentChanged() {
        updateOnBackPressedCallbackEnabled();
        // Dispatch the change event to this FragmentManager's primary navigation fragment
        dispatchParentPrimaryNavigationFragmentChanged(mPrimaryNav);
    }

    @Override
    @Nullable
    public Fragment getPrimaryNavigationFragment() {
        return mPrimaryNav;
    }

    public void setMaxLifecycle(Fragment f, Lifecycle.State state) {
        if ((mActive.get(f.mWho) != f
                || (f.mHost != null && f.getFragmentManager() != this))) {
            throw new IllegalArgumentException("Fragment " + f
                    + " is not an active fragment of FragmentManager " + this);
        }
        f.mMaxState = state;
    }

    @Override
    @NonNull
    public FragmentFactory getFragmentFactory() {
        FragmentFactory factory = super.getFragmentFactory();
        if (factory == DEFAULT_FACTORY) {
            if (mParent != null) {
                // This can't call setFragmentFactory since we need to
                // compute this each time getFragmentFactory() is called
                // so that if the parent's FragmentFactory changes, we
                // pick the change up here.
                return mParent.mFragmentManager.getFragmentFactory();
            }
            setFragmentFactory(new FragmentFactory() {
                @SuppressWarnings("deprecation")
                @NonNull
                @Override
                public Fragment instantiate(@NonNull ClassLoader classLoader,
                        @NonNull String className) {
                    return mHost.instantiate(mHost.getContext(), className, null);
                }
            });
        }
        return super.getFragmentFactory();
    }

    @Override
    public void registerFragmentLifecycleCallbacks(@NonNull FragmentLifecycleCallbacks cb,
                                                   boolean recursive) {
        mLifecycleCallbacks.add(new FragmentLifecycleCallbacksHolder(cb, recursive));
    }

    @Override
    public void unregisterFragmentLifecycleCallbacks(@NonNull FragmentLifecycleCallbacks cb) {
        synchronized (mLifecycleCallbacks) {
            for (int i = 0, N = mLifecycleCallbacks.size(); i < N; i++) {
                if (mLifecycleCallbacks.get(i).mCallback == cb) {
                    mLifecycleCallbacks.remove(i);
                    break;
                }
            }
        }
    }

    void dispatchOnFragmentPreAttached(@NonNull Fragment f, @NonNull Context context,
                                       boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentPreAttached(f, context, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentPreAttached(this, f, context);
            }
        }
    }

    void dispatchOnFragmentAttached(@NonNull Fragment f, @NonNull Context context,
                                    boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentAttached(f, context, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentAttached(this, f, context);
            }
        }
    }

    void dispatchOnFragmentPreCreated(@NonNull Fragment f, @Nullable Bundle savedInstanceState,
                                      boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentPreCreated(f, savedInstanceState, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentPreCreated(this, f, savedInstanceState);
            }
        }
    }

    void dispatchOnFragmentCreated(@NonNull Fragment f, @Nullable Bundle savedInstanceState,
                                   boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentCreated(f, savedInstanceState, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentCreated(this, f, savedInstanceState);
            }
        }
    }

    void dispatchOnFragmentActivityCreated(@NonNull Fragment f, @Nullable Bundle savedInstanceState,
                                           boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentActivityCreated(f, savedInstanceState, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentActivityCreated(this, f, savedInstanceState);
            }
        }
    }

    void dispatchOnFragmentViewCreated(@NonNull Fragment f, @NonNull View v,
                                       @Nullable Bundle savedInstanceState, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentViewCreated(f, v, savedInstanceState, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentViewCreated(this, f, v, savedInstanceState);
            }
        }
    }

    void dispatchOnFragmentStarted(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentStarted(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentStarted(this, f);
            }
        }
    }

    void dispatchOnFragmentResumed(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentResumed(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentResumed(this, f);
            }
        }
    }

    void dispatchOnFragmentPaused(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentPaused(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentPaused(this, f);
            }
        }
    }

    void dispatchOnFragmentStopped(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentStopped(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentStopped(this, f);
            }
        }
    }

    void dispatchOnFragmentSaveInstanceState(@NonNull Fragment f, @NonNull Bundle outState,
                                             boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentSaveInstanceState(f, outState, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentSaveInstanceState(this, f, outState);
            }
        }
    }

    void dispatchOnFragmentViewDestroyed(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentViewDestroyed(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentViewDestroyed(this, f);
            }
        }
    }

    void dispatchOnFragmentDestroyed(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentDestroyed(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentDestroyed(this, f);
            }
        }
    }

    void dispatchOnFragmentDetached(@NonNull Fragment f, boolean onlyRecursive) {
        if (mParent != null) {
            FragmentManager parentManager = mParent.getFragmentManager();
            if (parentManager instanceof FragmentManagerImpl) {
                ((FragmentManagerImpl) parentManager)
                        .dispatchOnFragmentDetached(f, true);
            }
        }
        for (FragmentLifecycleCallbacksHolder holder : mLifecycleCallbacks) {
            if (!onlyRecursive || holder.mRecursive) {
                holder.mCallback.onFragmentDetached(this, f);
            }
        }
    }

    // Checks if fragments that belong to this fragment manager (or their children) have menus,
    // and if they are visible.
    boolean checkForMenus() {
        boolean hasMenu = false;
        for (Fragment f: mActive.values()) {
            if (f != null) {
                hasMenu = isMenuAvailable(f);
            }
            if (hasMenu) {
                return true;
            }
        }
        return false;
    }

    private boolean isMenuAvailable(Fragment f) {
        return f.mHasMenu && f.mMenuVisible || f.mChildFragmentManager.checkForMenus();
    }

    public static int reverseTransit(int transit) {
        int rev = 0;
        switch (transit) {
            case FragmentTransaction.TRANSIT_FRAGMENT_OPEN:
                rev = FragmentTransaction.TRANSIT_FRAGMENT_CLOSE;
                break;
            case FragmentTransaction.TRANSIT_FRAGMENT_CLOSE:
                rev = FragmentTransaction.TRANSIT_FRAGMENT_OPEN;
                break;
            case FragmentTransaction.TRANSIT_FRAGMENT_FADE:
                rev = FragmentTransaction.TRANSIT_FRAGMENT_FADE;
                break;
        }
        return rev;

    }

    public static final int ANIM_STYLE_OPEN_ENTER = 1;
    public static final int ANIM_STYLE_OPEN_EXIT = 2;
    public static final int ANIM_STYLE_CLOSE_ENTER = 3;
    public static final int ANIM_STYLE_CLOSE_EXIT = 4;
    public static final int ANIM_STYLE_FADE_ENTER = 5;
    public static final int ANIM_STYLE_FADE_EXIT = 6;

    public static int transitToStyleIndex(int transit, boolean enter) {
        int animAttr = -1;
        switch (transit) {
            case FragmentTransaction.TRANSIT_FRAGMENT_OPEN:
                animAttr = enter ? ANIM_STYLE_OPEN_ENTER : ANIM_STYLE_OPEN_EXIT;
                break;
            case FragmentTransaction.TRANSIT_FRAGMENT_CLOSE:
                animAttr = enter ? ANIM_STYLE_CLOSE_ENTER : ANIM_STYLE_CLOSE_EXIT;
                break;
            case FragmentTransaction.TRANSIT_FRAGMENT_FADE:
                animAttr = enter ? ANIM_STYLE_FADE_ENTER : ANIM_STYLE_FADE_EXIT;
                break;
        }
        return animAttr;
    }

    @Override
    @Nullable
    public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context,
                             @NonNull AttributeSet attrs) {
        if (!"fragment".equals(name)) {
            return null;
        }

        String fname = attrs.getAttributeValue(null, "class");
        TypedArray a =  context.obtainStyledAttributes(attrs, FragmentTag.Fragment);
        if (fname == null) {
            fname = a.getString(FragmentTag.Fragment_name);
        }
        int id = a.getResourceId(FragmentTag.Fragment_id, View.NO_ID);
        String tag = a.getString(FragmentTag.Fragment_tag);
        a.recycle();

        if (fname == null || !FragmentFactory.isFragmentClass(context.getClassLoader(), fname)) {
            // Invalid support lib fragment; let the device's framework handle it.
            // This will allow android.app.Fragments to do the right thing.
            return null;
        }

        int containerId = parent != null ? parent.getId() : 0;
        if (containerId == View.NO_ID && id == View.NO_ID && tag == null) {
            throw new IllegalArgumentException(attrs.getPositionDescription()
                    + ": Must specify unique android:id, android:tag, or have a parent with an id for " + fname);
        }

        // If we restored from a previous state, we may already have
        // instantiated this fragment from the state and should use
        // that instance instead of making a new one.
        Fragment fragment = id != View.NO_ID ? findFragmentById(id) : null;
        if (fragment == null && tag != null) {
            fragment = findFragmentByTag(tag);
        }
        if (fragment == null && containerId != View.NO_ID) {
            fragment = findFragmentById(containerId);
        }

        if (FragmentManagerImpl.DEBUG) Log.v(TAG, "onCreateView: id=0x"
                + Integer.toHexString(id) + " fname=" + fname
                + " existing=" + fragment);
        if (fragment == null) {
            fragment = getFragmentFactory().instantiate(context.getClassLoader(), fname);
            fragment.mFromLayout = true;
            fragment.mFragmentId = id != 0 ? id : containerId;
            fragment.mContainerId = containerId;
            fragment.mTag = tag;
            fragment.mInLayout = true;
            fragment.mFragmentManager = this;
            fragment.mHost = mHost;
            fragment.onInflate(mHost.getContext(), attrs, fragment.mSavedFragmentState);
            addFragment(fragment, true);

        } else if (fragment.mInLayout) {
            // A fragment already exists and it is not one we restored from
            // previous state.
            throw new IllegalArgumentException(attrs.getPositionDescription()
                    + ": Duplicate id 0x" + Integer.toHexString(id)
                    + ", tag " + tag + ", or parent id 0x" + Integer.toHexString(containerId)
                    + " with another fragment for " + fname);
        } else {
            // This fragment was retained from a previous instance; get it
            // going now.
            fragment.mInLayout = true;
            fragment.mHost = mHost;
            // Give the Fragment the attributes to initialize itself.
            fragment.onInflate(mHost.getContext(), attrs, fragment.mSavedFragmentState);
        }

        // If we haven't finished entering the CREATED state ourselves yet,
        // push the inflated child fragment along. This will ensureInflatedFragmentView
        // at the right phase of the lifecycle so that we will have mView populated
        // for compliant fragments below.
        if (mCurState < Fragment.CREATED && fragment.mFromLayout) {
            moveToState(fragment, Fragment.CREATED, 0, 0, false);
        } else {
            moveToState(fragment);
        }

        if (fragment.mView == null) {
            // MOD: Fragment didn't create a view fix
            return null;
            //throw new IllegalStateException("Fragment " + fname
            //        + " did not create a view.");
        }
        if (id != 0) {
            fragment.mView.setId(id);
        }
        if (fragment.mView.getTag() == null) {
            fragment.mView.setTag(tag);
        }
        return fragment.mView;
    }

    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        return onCreateView(null, name, context, attrs);
    }

    LayoutInflater.Factory2 getLayoutInflaterFactory() {
        return this;
    }

    static class FragmentTag {
        public static final int[] Fragment = {
                0x01010003, 0x010100d0, 0x010100d1
        };
        public static final int Fragment_id = 1;
        public static final int Fragment_name = 0;
        public static final int Fragment_tag = 2;

        private FragmentTag() {
        }
    }

    /**
     * An add or pop transaction to be scheduled for the UI thread.
     */
    interface OpGenerator {
        /**
         * Generate transactions to add to {@code records} and whether or not the transaction is
         * an add or pop to {@code isRecordPop}.
         *
         * records and isRecordPop must be added equally so that each transaction in records
         * matches the boolean for whether or not it is a pop in isRecordPop.
         *
         * @param records A list to add transactions to.
         * @param isRecordPop A list to add whether or not the transactions added to records is
         *                    a pop transaction.
         * @return true if something was added or false otherwise.
         */
        boolean generateOps(ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop);
    }

    /**
     * A pop operation OpGenerator. This will be run on the UI thread and will generate the
     * transactions that will be popped if anything can be popped.
     */
    private class PopBackStackState implements OpGenerator {
        final String mName;
        final int mId;
        final int mFlags;

        PopBackStackState(String name, int id, int flags) {
            mName = name;
            mId = id;
            mFlags = flags;
        }

        @Override
        public boolean generateOps(ArrayList<BackStackRecord> records,
                                   ArrayList<Boolean> isRecordPop) {
            if (mPrimaryNav != null // We have a primary nav fragment
                    && mId < 0 // No valid id (since they're local)
                    && mName == null) { // no name to pop to (since they're local)
                final FragmentManager childManager = mPrimaryNav.getChildFragmentManager();
                if (childManager.popBackStackImmediate()) {
                    // We didn't add any operations for this FragmentManager even though
                    // a child did do work.
                    return false;
                }
            }
            return popBackStackState(records, isRecordPop, mName, mId, mFlags);
        }
    }

    /**
     * A listener for a postponed transaction. This waits until
     * {@link Fragment#startPostponedEnterTransition()} is called or a transaction is started
     * that interacts with this one, based on interactions with the fragment container.
     */
    static class StartEnterTransitionListener
            implements Fragment.OnStartEnterTransitionListener {
        final boolean mIsBack;
        final BackStackRecord mRecord;
        private int mNumPostponed;

        StartEnterTransitionListener(BackStackRecord record, boolean isBack) {
            mIsBack = isBack;
            mRecord = record;
        }

        /**
         * Called from {@link Fragment#startPostponedEnterTransition()}, this decreases the
         * number of Fragments that are postponed. This may cause the transaction to schedule
         * to finish running and run transitions and animations.
         */
        @Override
        public void onStartEnterTransition() {
            mNumPostponed--;
            if (mNumPostponed != 0) {
                return;
            }
            mRecord.mManager.scheduleCommit();
        }

        /**
         * Called from {@link Fragment#
         * setOnStartEnterTransitionListener(Fragment.OnStartEnterTransitionListener)}, this
         * increases the number of fragments that are postponed as part of this transaction.
         */
        @Override
        public void startListening() {
            mNumPostponed++;
        }

        /**
         * @return true if there are no more postponed fragments as part of the transaction.
         */
        public boolean isReady() {
            return mNumPostponed == 0;
        }

        /**
         * Completes the transaction and start the animations and transitions. This may skip
         * the transitions if this is called before all fragments have called
         * {@link Fragment#startPostponedEnterTransition()}.
         */
        public void completeTransaction() {
            final boolean canceled;
            canceled = mNumPostponed > 0;
            FragmentManagerImpl manager = mRecord.mManager;
            final int numAdded = manager.mAdded.size();
            for (int i = 0; i < numAdded; i++) {
                final Fragment fragment = manager.mAdded.get(i);
                fragment.setOnStartEnterTransitionListener(null);
                if (canceled && fragment.isPostponed()) {
                    fragment.startPostponedEnterTransition();
                }
            }
            mRecord.mManager.completeExecute(mRecord, mIsBack, !canceled, true);
        }

        /**
         * Cancels this transaction instead of completing it. That means that the state isn't
         * changed, so the pop results in no change to the state.
         */
        public void cancelTransaction() {
            mRecord.mManager.completeExecute(mRecord, mIsBack, false, false);
        }
    }

    /**
     * Contains either an animator or animation. One of these should be null.
     */
    private static class AnimationOrAnimator {
        public final Animation animation;
        public final Animator animator;

        AnimationOrAnimator(Animation animation) {
            this.animation = animation;
            this.animator = null;
            if (animation == null) {
                throw new IllegalStateException("Animation cannot be null");
            }
        }

        AnimationOrAnimator(Animator animator) {
            this.animation = null;
            this.animator = animator;
            if (animator == null) {
                throw new IllegalStateException("Animator cannot be null");
            }
        }
    }

    /**
     * We must call endViewTransition() before the animation ends or else the parent doesn't
     * get nulled out. We use both startViewTransition() and startAnimation() to solve a problem
     * with Views remaining in the hierarchy as disappearing children after the view has been
     * removed in some edge cases.
     */
    private static class EndViewTransitionAnimation extends AnimationSet implements Runnable {
        private final ViewGroup mParent;
        private final View mChild;
        private boolean mEnded;
        private boolean mTransitionEnded;
        private boolean mAnimating = true;

        EndViewTransitionAnimation(@NonNull Animation animation,
                                  @NonNull ViewGroup parent, @NonNull View child) {
            super(false);
            mParent = parent;
            mChild = child;
            addAnimation(animation);
            // We must call endViewTransition() even if the animation was never run or it
            // is interrupted in a way that can't be detected easily (app put in background)
            mParent.post(this);
        }

        @Override
        public boolean getTransformation(long currentTime, Transformation t) {
            mAnimating = true;
            if (mEnded) {
                return !mTransitionEnded;
            }
            boolean more = super.getTransformation(currentTime, t);
            if (!more) {
                mEnded = true;
                OneShotPreDrawListener.add(mParent, this);
            }
            return true;
        }

        @Override
        public boolean getTransformation(long currentTime, Transformation outTransformation,
                                         float scale) {
            mAnimating = true;
            if (mEnded) {
                return !mTransitionEnded;
            }
            boolean more = super.getTransformation(currentTime, outTransformation, scale);
            if (!more) {
                mEnded = true;
                OneShotPreDrawListener.add(mParent, this);
            }
            return true;
        }

        @Override
        public void run() {
            if (!mEnded && mAnimating) {
                mAnimating = false;
                // Called while animating, so we'll check again on next cycle
                mParent.post(this);
            } else {
                mParent.endViewTransition(mChild);
                mTransitionEnded = true;
            }
        }
    }
}
