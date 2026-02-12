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

import android.graphics.Rect;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.collection.ArrayMap;
import androidx.core.app.SharedElementCallback;
import androidx.core.view.OneShotPreDrawListener;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Contains the Fragment Transition functionality for both ordered and reordered
 * Fragment Transactions. With reordered fragment transactions, all Views have been
 * added to the View hierarchy prior to calling startTransitions. With ordered
 * fragment transactions, Views will be removed and added after calling startTransitions.
 */
class FragmentTransition {
    /**
     * The inverse of all BackStackRecord operation commands. This assumes that
     * REPLACE operations have already been replaced by add/remove operations.
     */
    private static final int[] INVERSE_OPS = {
            BackStackRecord.OP_NULL,                // inverse of OP_NULL (error)
            BackStackRecord.OP_REMOVE,              // inverse of OP_ADD
            BackStackRecord.OP_NULL,                // inverse of OP_REPLACE (error)
            BackStackRecord.OP_ADD,                 // inverse of OP_REMOVE
            BackStackRecord.OP_SHOW,                // inverse of OP_HIDE
            BackStackRecord.OP_HIDE,                // inverse of OP_SHOW
            BackStackRecord.OP_ATTACH,              // inverse of OP_DETACH
            BackStackRecord.OP_DETACH,              // inverse of OP_ATTACH
            BackStackRecord.OP_UNSET_PRIMARY_NAV,   // inverse of OP_SET_PRIMARY_NAV
            BackStackRecord.OP_SET_PRIMARY_NAV,     // inverse of OP_UNSET_PRIMARY_NAV
            BackStackRecord.OP_SET_MAX_LIFECYCLE
    };

    private static final FragmentTransitionImpl PLATFORM_IMPL = Build.VERSION.SDK_INT >= 21
            ? new FragmentTransitionCompat21()
            : null;

    private static final FragmentTransitionImpl SUPPORT_IMPL = resolveSupportImpl();

    private static FragmentTransitionImpl resolveSupportImpl() {
        try {
            @SuppressWarnings("unchecked")
            Class<FragmentTransitionImpl> impl = (Class<FragmentTransitionImpl>) Class.forName(
                    "androidx.transition.FragmentTransitionSupport");
            return impl.getDeclaredConstructor().newInstance();
        } catch (Exception ignored) {
            // support-transition is not loaded; ignore
        }
        return null;
    }

    /**
     * The main entry point for Fragment Transitions, this starts the transitions
     * set on the leaving Fragment's {@link Fragment#getExitTransition()}, the
     * entering Fragment's {@link Fragment#getEnterTransition()} and
     * {@link Fragment#getSharedElementEnterTransition()}. When popping,
     * the leaving Fragment's {@link Fragment#getReturnTransition()} and
     * {@link Fragment#getSharedElementReturnTransition()} and the entering
     * {@link Fragment#getReenterTransition()} will be run.
     * <p>
     * With reordered Fragment Transitions, all Views have been added to the
     * View hierarchy prior to calling this method. The incoming Fragment's Views
     * will be INVISIBLE. With ordered Fragment Transitions, this method
     * is called before any change has been made to the hierarchy. That means
     * that the added Fragments have not created their Views yet and the hierarchy
     * is unknown.
     *
     * @param fragmentManager The executing FragmentManagerImpl
     * @param records The list of transactions being executed.
     * @param isRecordPop For each transaction, whether it is a pop transaction or not.
     * @param startIndex The first index into records and isRecordPop to execute as
     *                   part of this transition.
     * @param endIndex One past the last index into records and isRecordPop to execute
     *                 as part of this transition.
     * @param isReordered true if this is a reordered transaction, meaning that the
     *                    Views of incoming fragments have been added. false if the
     *                    transaction has yet to be run and Views haven't been created.
     */
    static void startTransitions(FragmentManagerImpl fragmentManager,
            ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop,
            int startIndex, int endIndex, boolean isReordered) {
        if (fragmentManager.mCurState < Fragment.CREATED) {
            return;
        }

        SparseArray<FragmentContainerTransition> transitioningFragments =
                new SparseArray<>();
        for (int i = startIndex; i < endIndex; i++) {
            final BackStackRecord record = records.get(i);
            final boolean isPop = isRecordPop.get(i);
            if (isPop) {
                calculatePopFragments(record, transitioningFragments, isReordered);
            } else {
                calculateFragments(record, transitioningFragments, isReordered);
            }
        }

        if (transitioningFragments.size() != 0) {
            final View nonExistentView = new View(fragmentManager.mHost.getContext());
            final int numContainers = transitioningFragments.size();
            for (int i = 0; i < numContainers; i++) {
                int containerId = transitioningFragments.keyAt(i);
                ArrayMap<String, String> nameOverrides = calculateNameOverrides(containerId,
                        records, isRecordPop, startIndex, endIndex);

                FragmentContainerTransition containerTransition =
                        transitioningFragments.valueAt(i);

                if (isReordered) {
                    configureTransitionsReordered(fragmentManager, containerId,
                            containerTransition, nonExistentView, nameOverrides);
                } else {
                    configureTransitionsOrdered(fragmentManager, containerId,
                            containerTransition, nonExistentView, nameOverrides);
                }
            }
        }
    }

    /**
     * Iterates through the transactions that affect a given fragment container
     * and tracks the shared element names across transactions. This is most useful
     * in pop transactions where the names of shared elements are known.
     *
     * @param containerId The container ID that is executing the transition.
     * @param records The list of transactions being executed.
     * @param isRecordPop For each transaction, whether it is a pop transaction or not.
     * @param startIndex The first index into records and isRecordPop to execute as
     *                   part of this transition.
     * @param endIndex One past the last index into records and isRecordPop to execute
     *                 as part of this transition.
     * @return A map from the initial shared element name to the final shared element name
     * before any onMapSharedElements is run.
     */
    private static ArrayMap<String, String> calculateNameOverrides(int containerId,
            ArrayList<BackStackRecord> records, ArrayList<Boolean> isRecordPop,
            int startIndex, int endIndex) {
        ArrayMap<String, String> nameOverrides = new ArrayMap<>();
        for (int recordNum = endIndex - 1; recordNum >= startIndex; recordNum--) {
            final BackStackRecord record = records.get(recordNum);
            if (!record.interactsWith(containerId)) {
                continue;
            }
            final boolean isPop = isRecordPop.get(recordNum);
            if (record.mSharedElementSourceNames != null) {
                final int numSharedElements = record.mSharedElementSourceNames.size();
                final ArrayList<String> sources;
                final ArrayList<String> targets;
                if (isPop) {
                    targets = record.mSharedElementSourceNames;
                    sources = record.mSharedElementTargetNames;
                } else {
                    sources = record.mSharedElementSourceNames;
                    targets = record.mSharedElementTargetNames;
                }
                for (int i = 0; i < numSharedElements; i++) {
                    String sourceName = sources.get(i);
                    String targetName = targets.get(i);
                    String previousTarget = nameOverrides.remove(targetName);
                    if (previousTarget != null) {
                        nameOverrides.put(sourceName, previousTarget);
                    } else {
                        nameOverrides.put(sourceName, targetName);
                    }
                }
            }
        }
        return nameOverrides;
    }

    /**
     * Configures a transition for a single fragment container for which the transaction was
     * reordered. That means that all Fragment Views have been added and incoming fragment
     * Views are marked invisible.
     *
     * @param fragmentManager The executing FragmentManagerImpl
     * @param containerId The container ID that is executing the transition.
     * @param fragments A structure holding the transitioning fragments in this container.
     * @param nonExistentView A View that does not exist in the hierarchy. This is used to
     *                        prevent transitions from acting on other Views when there is no
     *                        other target.
     * @param nameOverrides A map of the shared element names from the starting fragment to
     *                      the final fragment's Views as given in
     *                      {@link FragmentTransaction#addSharedElement(View, String)}.
     */
    private static void configureTransitionsReordered(FragmentManagerImpl fragmentManager,
            int containerId, FragmentContainerTransition fragments,
            View nonExistentView, ArrayMap<String, String> nameOverrides) {
        ViewGroup sceneRoot = null;
        if (fragmentManager.mContainer.onHasView()) {
            sceneRoot = (ViewGroup) fragmentManager.mContainer.onFindViewById(containerId);
        }
        if (sceneRoot == null) {
            return;
        }
        final Fragment inFragment = fragments.lastIn;
        final Fragment outFragment = fragments.firstOut;
        final FragmentTransitionImpl impl = chooseImpl(outFragment, inFragment);
        if (impl == null) {
            return;
        }
        final boolean inIsPop = fragments.lastInIsPop;
        final boolean outIsPop = fragments.firstOutIsPop;

        ArrayList<View> sharedElementsIn = new ArrayList<>();
        ArrayList<View> sharedElementsOut = new ArrayList<>();
        Object enterTransition = getEnterTransition(impl, inFragment, inIsPop);
        Object exitTransition = getExitTransition(impl, outFragment, outIsPop);

        Object sharedElementTransition = configureSharedElementsReordered(impl, sceneRoot,
                nonExistentView, nameOverrides, fragments, sharedElementsOut, sharedElementsIn,
                enterTransition, exitTransition);

        if (enterTransition == null && sharedElementTransition == null
                && exitTransition == null) {
            return; // no transitions!
        }

        ArrayList<View> exitingViews = configureEnteringExitingViews(impl, exitTransition,
                outFragment, sharedElementsOut, nonExistentView);

        ArrayList<View> enteringViews = configureEnteringExitingViews(impl, enterTransition,
                inFragment, sharedElementsIn, nonExistentView);

        setViewVisibility(enteringViews, View.INVISIBLE);

        Object transition = mergeTransitions(impl, enterTransition, exitTransition,
                sharedElementTransition, inFragment, inIsPop);

        if (transition != null) {
            replaceHide(impl, exitTransition, outFragment, exitingViews);
            ArrayList<String> inNames =
                    impl.prepareSetNameOverridesReordered(sharedElementsIn);
            impl.scheduleRemoveTargets(transition,
                    enterTransition, enteringViews, exitTransition, exitingViews,
                    sharedElementTransition, sharedElementsIn);
            impl.beginDelayedTransition(sceneRoot, transition);
            impl.setNameOverridesReordered(sceneRoot, sharedElementsOut,
                    sharedElementsIn, inNames, nameOverrides);
            setViewVisibility(enteringViews, View.VISIBLE);
            impl.swapSharedElementTargets(sharedElementTransition,
                    sharedElementsOut, sharedElementsIn);
        }
    }

    /**
     * Replace hide operations with visibility changes on the exiting views. Instead of making
     * the entire fragment's view GONE, make each exiting view INVISIBLE. At the end of the
     * transition, make the fragment's view GONE.
     */
    private static void replaceHide(FragmentTransitionImpl impl,
            Object exitTransition, Fragment exitingFragment,
            final ArrayList<View> exitingViews) {
        if (exitingFragment != null && exitTransition != null && exitingFragment.mAdded
                && exitingFragment.mHidden && exitingFragment.mHiddenChanged) {
            exitingFragment.setHideReplaced(true);
            impl.scheduleHideFragmentView(exitTransition,
                    exitingFragment.getView(), exitingViews);
            final ViewGroup container = exitingFragment.mContainer;
            OneShotPreDrawListener.add(container, new Runnable() {
                @Override
                public void run() {
                    setViewVisibility(exitingViews, View.INVISIBLE);
                }
            });
        }
    }

    /**
     * Configures a transition for a single fragment container for which the transaction was
     * ordered. That means that the transaction has not been executed yet, so incoming
     * Views are not yet known.
     *
     * @param fragmentManager The executing FragmentManagerImpl
     * @param containerId The container ID that is executing the transition.
     * @param fragments A structure holding the transitioning fragments in this container.
     * @param nonExistentView A View that does not exist in the hierarchy. This is used to
     *                        prevent transitions from acting on other Views when there is no
     *                        other target.
     * @param nameOverrides A map of the shared element names from the starting fragment to
     *                      the final fragment's Views as given in
     *                      {@link FragmentTransaction#addSharedElement(View, String)}.
     */
    private static void configureTransitionsOrdered(FragmentManagerImpl fragmentManager,
            int containerId, FragmentContainerTransition fragments,
            View nonExistentView, ArrayMap<String, String> nameOverrides) {
        ViewGroup sceneRoot = null;
        if (fragmentManager.mContainer.onHasView()) {
            sceneRoot = (ViewGroup) fragmentManager.mContainer.onFindViewById(containerId);
        }
        if (sceneRoot == null) {
            return;
        }
        final Fragment inFragment = fragments.lastIn;
        final Fragment outFragment = fragments.firstOut;
        final FragmentTransitionImpl impl = chooseImpl(outFragment, inFragment);
        if (impl == null) {
            return;
        }
        final boolean inIsPop = fragments.lastInIsPop;
        final boolean outIsPop = fragments.firstOutIsPop;

        Object enterTransition = getEnterTransition(impl, inFragment, inIsPop);
        Object exitTransition = getExitTransition(impl, outFragment, outIsPop);

        ArrayList<View> sharedElementsOut = new ArrayList<>();
        ArrayList<View> sharedElementsIn = new ArrayList<>();

        Object sharedElementTransition = configureSharedElementsOrdered(impl, sceneRoot,
                nonExistentView, nameOverrides, fragments, sharedElementsOut, sharedElementsIn,
                enterTransition, exitTransition);

        if (enterTransition == null && sharedElementTransition == null
                && exitTransition == null) {
            return; // no transitions!
        }

        ArrayList<View> exitingViews = configureEnteringExitingViews(impl, exitTransition,
                outFragment, sharedElementsOut, nonExistentView);

        if (exitingViews == null || exitingViews.isEmpty()) {
            exitTransition = null;
        }

        // Ensure the entering transition doesn't target anything until the views are made
        // visible
        impl.addTarget(enterTransition, nonExistentView);

        Object transition = mergeTransitions(impl, enterTransition, exitTransition,
                sharedElementTransition, inFragment, fragments.lastInIsPop);

        if (transition != null) {
            final ArrayList<View> enteringViews = new ArrayList<>();
            impl.scheduleRemoveTargets(transition,
                    enterTransition, enteringViews, exitTransition, exitingViews,
                    sharedElementTransition, sharedElementsIn);
            scheduleTargetChange(impl, sceneRoot, inFragment, nonExistentView, sharedElementsIn,
                    enterTransition, enteringViews, exitTransition, exitingViews);
            impl.setNameOverridesOrdered(sceneRoot, sharedElementsIn, nameOverrides);

            impl.beginDelayedTransition(sceneRoot, transition);
            impl.scheduleNameReset(sceneRoot, sharedElementsIn, nameOverrides);
        }
    }

    /**
     * This method is used for fragment transitions for ordrered transactions to change the
     * enter and exit transition targets after the call to
     * {@link FragmentTransitionCompat21#beginDelayedTransition(ViewGroup, Object)}. The exit
     * transition must ensure that it does not target any Views and the enter transition must start
     * targeting the Views of the incoming Fragment.
     *
     * @param sceneRoot The fragment container View
     * @param inFragment The last fragment that is entering
     * @param nonExistentView A view that does not exist in the hierarchy that is used as a
     *                        transition target to ensure no View is targeted.
     * @param sharedElementsIn The shared element Views of the incoming fragment
     * @param enterTransition The enter transition of the incoming fragment
     * @param enteringViews The entering Views of the incoming fragment
     * @param exitTransition The exit transition of the outgoing fragment
     * @param exitingViews The exiting views of the outgoing fragment
     */
    private static void scheduleTargetChange(final FragmentTransitionImpl impl,
            final ViewGroup sceneRoot,
            final Fragment inFragment, final View nonExistentView,
            final ArrayList<View> sharedElementsIn,
            final Object enterTransition, final ArrayList<View> enteringViews,
            final Object exitTransition, final ArrayList<View> exitingViews) {
        OneShotPreDrawListener.add(sceneRoot, new Runnable() {
            @Override
            public void run() {
                if (enterTransition != null) {
                    impl.removeTarget(enterTransition,
                            nonExistentView);
                    ArrayList<View> views = configureEnteringExitingViews(impl,
                            enterTransition, inFragment, sharedElementsIn, nonExistentView);
                    enteringViews.addAll(views);
                }

                if (exitingViews != null) {
                    if (exitTransition != null) {
                        ArrayList<View> tempExiting = new ArrayList<>();
                        tempExiting.add(nonExistentView);
                        impl.replaceTargets(exitTransition, exitingViews,
                                tempExiting);
                    }
                    exitingViews.clear();
                    exitingViews.add(nonExistentView);
                }
            }
        });
    }

    /**
     * Chooses the appropriate implementation depending on the Transition instances hold by the
     * Fragments.
     */
    private static FragmentTransitionImpl chooseImpl(Fragment outFragment, Fragment inFragment) {
        // Collect all transition instances
        final ArrayList<Object> transitions = new ArrayList<>();
        if (outFragment != null) {
            final Object exitTransition = outFragment.getExitTransition();
            if (exitTransition != null) {
                transitions.add(exitTransition);
            }
            final Object returnTransition = outFragment.getReturnTransition();
            if (returnTransition != null) {
                transitions.add(returnTransition);
            }
            final Object sharedReturnTransition = outFragment.getSharedElementReturnTransition();
            if (sharedReturnTransition != null) {
                transitions.add(sharedReturnTransition);
            }
        }
        if (inFragment != null) {
            final Object enterTransition = inFragment.getEnterTransition();
            if (enterTransition != null) {
                transitions.add(enterTransition);
            }
            final Object reenterTransition = inFragment.getReenterTransition();
            if (reenterTransition != null) {
                transitions.add(reenterTransition);
            }
            final Object sharedEnterTransition = inFragment.getSharedElementEnterTransition();
            if (sharedEnterTransition != null) {
                transitions.add(sharedEnterTransition);
            }
        }
        if (transitions.isEmpty()) {
            return null; // No transition to run
        }
        // Pick the implementation that can handle all the transitions
        if (PLATFORM_IMPL != null && canHandleAll(PLATFORM_IMPL, transitions)) {
            return PLATFORM_IMPL;
        }
        if (SUPPORT_IMPL != null && canHandleAll(SUPPORT_IMPL, transitions)) {
            return SUPPORT_IMPL;
        }
        if (PLATFORM_IMPL != null || SUPPORT_IMPL != null) {
            throw new IllegalArgumentException("Invalid Transition types");
        }
        return null;
    }

    private static boolean canHandleAll(FragmentTransitionImpl impl, List<Object> transitions) {
        for (int i = 0, size = transitions.size(); i < size; i++) {
            if (!impl.canHandle(transitions.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a TransitionSet containing the shared element transition. The wrapping TransitionSet
     * targets all shared elements to ensure that no other Views are targeted. The shared element
     * transition can then target any or all shared elements without worrying about accidentally
     * targeting entering or exiting Views.
     *
     * @param inFragment The incoming fragment
     * @param outFragment the outgoing fragment
     * @param isPop True if this is a pop transaction or false if it is a normal (add) transaction.
     * @return A TransitionSet wrapping the shared element transition or null if no such transition
     * exists.
     */
    private static Object getSharedElementTransition(FragmentTransitionImpl impl,
            Fragment inFragment, Fragment outFragment, boolean isPop) {
        if (inFragment == null || outFragment == null) {
            return null;
        }
        Object transition = impl.cloneTransition(isPop
                ? outFragment.getSharedElementReturnTransition()
                : inFragment.getSharedElementEnterTransition());
        return impl.wrapTransitionInSet(transition);
    }

    /**
     * Returns a clone of the enter transition or null if no such transition exists.
     */
    private static Object getEnterTransition(FragmentTransitionImpl impl,
            Fragment inFragment, boolean isPop) {
        if (inFragment == null) {
            return null;
        }
        return impl.cloneTransition(isPop
                ? inFragment.getReenterTransition()
                : inFragment.getEnterTransition());
    }

    /**
     * Returns a clone of the exit transition or null if no such transition exists.
     */
    private static Object getExitTransition(FragmentTransitionImpl impl,
            Fragment outFragment, boolean isPop) {
        if (outFragment == null) {
            return null;
        }
        return impl.cloneTransition(isPop
                ? outFragment.getReturnTransition()
                : outFragment.getExitTransition());
    }

    /**
     * Configures the shared elements of a reordered fragment transaction's transition.
     * This retrieves the shared elements of the outgoing and incoming fragments, maps the
     * views, and sets up the epicenter on the transitions.
     * <p>
     * The epicenter of exit and shared element transitions is the first shared element
     * in the outgoing fragment. The epicenter of the entering transition is the first shared
     * element in the incoming fragment.
     *
     * @param sceneRoot The fragment container View
     * @param nonExistentView A View that does not exist in the hierarchy. This is used to
     *                        prevent transitions from acting on other Views when there is no
     *                        other target.
     * @param nameOverrides A map of the shared element names from the starting fragment to
     *                      the final fragment's Views as given in
     *                      {@link FragmentTransaction#addSharedElement(View, String)}.
     * @param fragments A structure holding the transitioning fragments in this container.
     * @param sharedElementsOut A list modified to contain the shared elements in the outgoing
     *                          fragment
     * @param sharedElementsIn A list modified to contain the shared elements in the incoming
     *                         fragment
     * @param enterTransition The transition used for entering Views, modified by applying the
     *                        epicenter
     * @param exitTransition The transition used for exiting Views, modified by applying the
     *                       epicenter
     * @return The shared element transition or null if no shared elements exist
     */
    private static Object configureSharedElementsReordered(final FragmentTransitionImpl impl,
            final ViewGroup sceneRoot,
            final View nonExistentView, final ArrayMap<String, String> nameOverrides,
            final FragmentContainerTransition fragments,
            final ArrayList<View> sharedElementsOut,
            final ArrayList<View> sharedElementsIn,
            final Object enterTransition, final Object exitTransition) {
        final Fragment inFragment = fragments.lastIn;
        final Fragment outFragment = fragments.firstOut;
        if (inFragment != null) {
            inFragment.requireView().setVisibility(View.VISIBLE);
        }
        if (inFragment == null || outFragment == null) {
            return null; // no shared element without a fragment
        }

        final boolean inIsPop = fragments.lastInIsPop;
        Object sharedElementTransition = nameOverrides.isEmpty() ? null
                : getSharedElementTransition(impl, inFragment, outFragment, inIsPop);

        final ArrayMap<String, View> outSharedElements = captureOutSharedElements(impl,
                nameOverrides, sharedElementTransition, fragments);

        final ArrayMap<String, View> inSharedElements = captureInSharedElements(impl,
                nameOverrides, sharedElementTransition, fragments);

        if (nameOverrides.isEmpty()) {
            sharedElementTransition = null;
            if (outSharedElements != null) {
                outSharedElements.clear();
            }
            if (inSharedElements != null) {
                inSharedElements.clear();
            }
        } else {
            addSharedElementsWithMatchingNames(sharedElementsOut, outSharedElements,
                    nameOverrides.keySet());
            addSharedElementsWithMatchingNames(sharedElementsIn, inSharedElements,
                    nameOverrides.values());
        }

        if (enterTransition == null && exitTransition == null && sharedElementTransition == null) {
            // don't call onSharedElementStart/End since there is no transition
            return null;
        }

        callSharedElementStartEnd(inFragment, outFragment, inIsPop, outSharedElements, true);

        final Rect epicenter;
        final View epicenterView;
        if (sharedElementTransition != null) {
            sharedElementsIn.add(nonExistentView);
            impl.setSharedElementTargets(sharedElementTransition,
                    nonExistentView, sharedElementsOut);
            final boolean outIsPop = fragments.firstOutIsPop;
            final BackStackRecord outTransaction = fragments.firstOutTransaction;
            setOutEpicenter(impl, sharedElementTransition, exitTransition, outSharedElements,
                    outIsPop, outTransaction);
            epicenter = new Rect();
            epicenterView = getInEpicenterView(inSharedElements, fragments,
                    enterTransition, inIsPop);
            if (epicenterView != null) {
                impl.setEpicenter(enterTransition, epicenter);
            }
        } else {
            epicenter = null;
            epicenterView = null;
        }

        OneShotPreDrawListener.add(sceneRoot, new Runnable() {
            @Override
            public void run() {
                callSharedElementStartEnd(inFragment, outFragment, inIsPop,
                        inSharedElements, false);
                if (epicenterView != null) {
                    impl.getBoundsOnScreen(epicenterView, epicenter);
                }
            }
        });
        return sharedElementTransition;
    }

    /**
     * Add Views from sharedElements into views that have the transitionName in the
     * nameOverridesSet.
     *
     * @param views               Views list to add shared elements to
     * @param sharedElements      List of shared elements
     * @param nameOverridesSet    The transition names for all views to be copied from
     *                            sharedElements to views.
     */
    private static void addSharedElementsWithMatchingNames(ArrayList<View> views,
            ArrayMap<String, View> sharedElements, Collection<String> nameOverridesSet) {
        for (int i = sharedElements.size() - 1; i >= 0; i--) {
            View view = sharedElements.valueAt(i);
            if (nameOverridesSet.contains(ViewCompat.getTransitionName(view))) {
                views.add(view);
            }
        }
    }

    /**
     * Configures the shared elements of an ordered fragment transaction's transition.
     * This retrieves the shared elements of the incoming fragments, and schedules capturing
     * the incoming fragment's shared elements. It also maps the views, and sets up the epicenter
     * on the transitions.
     * <p>
     * The epicenter of exit and shared element transitions is the first shared element
     * in the outgoing fragment. The epicenter of the entering transition is the first shared
     * element in the incoming fragment.
     *
     * @param sceneRoot The fragment container View
     * @param nonExistentView A View that does not exist in the hierarchy. This is used to
     *                        prevent transitions from acting on other Views when there is no
     *                        other target.
     * @param nameOverrides A map of the shared element names from the starting fragment to
     *                      the final fragment's Views as given in
     *                      {@link FragmentTransaction#addSharedElement(View, String)}.
     * @param fragments A structure holding the transitioning fragments in this container.
     * @param sharedElementsOut A list modified to contain the shared elements in the outgoing
     *                          fragment
     * @param sharedElementsIn A list modified to contain the shared elements in the incoming
     *                         fragment
     * @param enterTransition The transition used for entering Views, modified by applying the
     *                        epicenter
     * @param exitTransition The transition used for exiting Views, modified by applying the
     *                       epicenter
     * @return The shared element transition or null if no shared elements exist
     */
    private static Object configureSharedElementsOrdered(final FragmentTransitionImpl impl,
            final ViewGroup sceneRoot,
            final View nonExistentView, final ArrayMap<String, String> nameOverrides,
            final FragmentContainerTransition fragments,
            final ArrayList<View> sharedElementsOut,
            final ArrayList<View> sharedElementsIn,
            final Object enterTransition, final Object exitTransition) {
        final Fragment inFragment = fragments.lastIn;
        final Fragment outFragment = fragments.firstOut;

        if (inFragment == null || outFragment == null) {
            return null; // no transition
        }

        final boolean inIsPop = fragments.lastInIsPop;
        Object sharedElementTransition = nameOverrides.isEmpty() ? null
                : getSharedElementTransition(impl, inFragment, outFragment, inIsPop);

        ArrayMap<String, View> outSharedElements = captureOutSharedElements(impl, nameOverrides,
                sharedElementTransition, fragments);

        if (nameOverrides.isEmpty()) {
            sharedElementTransition = null;
        } else {
            sharedElementsOut.addAll(outSharedElements.values());
        }

        if (enterTransition == null && exitTransition == null && sharedElementTransition == null) {
            // don't call onSharedElementStart/End since there is no transition
            return null;
        }

        callSharedElementStartEnd(inFragment, outFragment, inIsPop, outSharedElements, true);

        final Rect inEpicenter;
        if (sharedElementTransition != null) {
            inEpicenter = new Rect();
            impl.setSharedElementTargets(sharedElementTransition,
                    nonExistentView, sharedElementsOut);
            final boolean outIsPop = fragments.firstOutIsPop;
            final BackStackRecord outTransaction = fragments.firstOutTransaction;
            setOutEpicenter(impl, sharedElementTransition, exitTransition, outSharedElements,
                    outIsPop, outTransaction);
            if (enterTransition != null) {
                impl.setEpicenter(enterTransition, inEpicenter);
            }
        } else {
            inEpicenter = null;
        }


        final Object finalSharedElementTransition = sharedElementTransition;
        OneShotPreDrawListener.add(sceneRoot, new Runnable() {
            @Override
            public void run() {
                ArrayMap<String, View> inSharedElements = captureInSharedElements(impl,
                        nameOverrides, finalSharedElementTransition, fragments);

                if (inSharedElements != null) {
                    sharedElementsIn.addAll(inSharedElements.values());
                    sharedElementsIn.add(nonExistentView);
                }

                callSharedElementStartEnd(inFragment, outFragment, inIsPop,
                        inSharedElements, false);
                if (finalSharedElementTransition != null) {
                    impl.swapSharedElementTargets(
                            finalSharedElementTransition, sharedElementsOut,
                            sharedElementsIn);

                    final View inEpicenterView = getInEpicenterView(inSharedElements,
                            fragments, enterTransition, inIsPop);
                    if (inEpicenterView != null) {
                        impl.getBoundsOnScreen(inEpicenterView,
                                inEpicenter);
                    }
                }
            }
        });

        return sharedElementTransition;
    }

    /**
     * Finds the shared elements in the outgoing fragment. It also calls
     * {@link SharedElementCallback#onMapSharedElements(List, Map)} to allow more control
     * of the shared element mapping. {@code nameOverrides} is updated to match the
     * actual transition name of the mapped shared elements.
     *
     * @param nameOverrides A map of the shared element names from the starting fragment to
     *                      the final fragment's Views as given in
     *                      {@link FragmentTransaction#addSharedElement(View, String)}.
     * @param sharedElementTransition The shared element transition
     * @param fragments A structure holding the transitioning fragments in this container.
     * @return The mapping of shared element names to the Views in the hierarchy or null
     * if there is no shared element transition.
     */
    private static ArrayMap<String, View> captureOutSharedElements(FragmentTransitionImpl impl,
            ArrayMap<String, String> nameOverrides, Object sharedElementTransition,
            FragmentContainerTransition fragments) {
        if (nameOverrides.isEmpty() || sharedElementTransition == null) {
            nameOverrides.clear();
            return null;
        }
        final Fragment outFragment = fragments.firstOut;
        final ArrayMap<String, View> outSharedElements = new ArrayMap<>();
        impl.findNamedViews(outSharedElements, outFragment.requireView());

        final SharedElementCallback sharedElementCallback;
        final ArrayList<String> names;
        final BackStackRecord outTransaction = fragments.firstOutTransaction;
        if (fragments.firstOutIsPop) {
            sharedElementCallback = outFragment.getEnterTransitionCallback();
            names = outTransaction.mSharedElementTargetNames;
        } else {
            sharedElementCallback = outFragment.getExitTransitionCallback();
            names = outTransaction.mSharedElementSourceNames;
        }

        outSharedElements.retainAll(names);
        if (sharedElementCallback != null) {
            sharedElementCallback.onMapSharedElements(names, outSharedElements);
            for (int i = names.size() - 1; i >= 0; i--) {
                String name = names.get(i);
                View view = outSharedElements.get(name);
                if (view == null) {
                    nameOverrides.remove(name);
                } else if (!name.equals(ViewCompat.getTransitionName(view))) {
                    String targetValue = nameOverrides.remove(name);
                    nameOverrides.put(ViewCompat.getTransitionName(view), targetValue);
                }
            }
        } else {
            nameOverrides.retainAll(outSharedElements.keySet());
        }
        return outSharedElements;
    }

    /**
     * Finds the shared elements in the incoming fragment. It also calls
     * {@link SharedElementCallback#onMapSharedElements(List, Map)} to allow more control
     * of the shared element mapping. {@code nameOverrides} is updated to match the
     * actual transition name of the mapped shared elements.
     *
     * @param nameOverrides A map of the shared element names from the starting fragment to
     *                      the final fragment's Views as given in
     *                      {@link FragmentTransaction#addSharedElement(View, String)}.
     * @param sharedElementTransition The shared element transition
     * @param fragments A structure holding the transitioning fragments in this container.
     * @return The mapping of shared element names to the Views in the hierarchy or null
     * if there is no shared element transition.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static ArrayMap<String, View> captureInSharedElements(FragmentTransitionImpl impl,
            ArrayMap<String, String> nameOverrides, Object sharedElementTransition,
            FragmentContainerTransition fragments) {
        Fragment inFragment = fragments.lastIn;
        final View fragmentView = inFragment.getView();
        if (nameOverrides.isEmpty() || sharedElementTransition == null || fragmentView == null) {
            nameOverrides.clear();
            return null;
        }
        final ArrayMap<String, View> inSharedElements = new ArrayMap<>();
        impl.findNamedViews(inSharedElements, fragmentView);

        final SharedElementCallback sharedElementCallback;
        final ArrayList<String> names;
        final BackStackRecord inTransaction = fragments.lastInTransaction;
        if (fragments.lastInIsPop) {
            sharedElementCallback = inFragment.getExitTransitionCallback();
            names = inTransaction.mSharedElementSourceNames;
        } else {
            sharedElementCallback = inFragment.getEnterTransitionCallback();
            names = inTransaction.mSharedElementTargetNames;
        }

        if (names != null) {
            inSharedElements.retainAll(names);
            inSharedElements.retainAll(nameOverrides.values());
        }
        if (sharedElementCallback != null) {
            sharedElementCallback.onMapSharedElements(names, inSharedElements);
            for (int i = names.size() - 1; i >= 0; i--) {
                String name = names.get(i);
                View view = inSharedElements.get(name);
                if (view == null) {
                    String key = findKeyForValue(nameOverrides, name);
                    if (key != null) {
                        nameOverrides.remove(key);
                    }
                } else if (!name.equals(ViewCompat.getTransitionName(view))) {
                    String key = findKeyForValue(nameOverrides, name);
                    if (key != null) {
                        nameOverrides.put(key, ViewCompat.getTransitionName(view));
                    }
                }
            }
        } else {
            retainValues(nameOverrides, inSharedElements);
        }
        return inSharedElements;
    }

    /**
     * Utility to find the String key in {@code map} that maps to {@code value}.
     */
    private static String findKeyForValue(ArrayMap<String, String> map, String value) {
        final int numElements = map.size();
        for (int i = 0; i < numElements; i++) {
            if (value.equals(map.valueAt(i))) {
                return map.keyAt(i);
            }
        }
        return null;
    }

    /**
     * Returns the View in the incoming Fragment that should be used as the epicenter.
     *
     * @param inSharedElements The mapping of shared element names to Views in the
     *                         incoming fragment.
     * @param fragments A structure holding the transitioning fragments in this container.
     * @param enterTransition The transition used for the incoming Fragment's views
     * @param inIsPop Is the incoming fragment being added as a pop transaction?
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static View getInEpicenterView(ArrayMap<String, View> inSharedElements,
            FragmentContainerTransition fragments,
            Object enterTransition, boolean inIsPop) {
        BackStackRecord inTransaction = fragments.lastInTransaction;
        if (enterTransition != null && inSharedElements != null
                && inTransaction.mSharedElementSourceNames != null
                && !inTransaction.mSharedElementSourceNames.isEmpty()) {
            final String targetName = inIsPop
                    ? inTransaction.mSharedElementSourceNames.get(0)
                    : inTransaction.mSharedElementTargetNames.get(0);
            return inSharedElements.get(targetName);
        }
        return null;
    }

    /**
     * Sets the epicenter for the exit transition.
     *
     * @param sharedElementTransition The shared element transition
     * @param exitTransition The transition for the outgoing fragment's views
     * @param outSharedElements Shared elements in the outgoing fragment
     * @param outIsPop Is the outgoing fragment being removed as a pop transaction?
     * @param outTransaction The transaction that caused the fragment to be removed.
     */
    private static void setOutEpicenter(FragmentTransitionImpl impl, Object sharedElementTransition,
            Object exitTransition, ArrayMap<String, View> outSharedElements, boolean outIsPop,
            BackStackRecord outTransaction) {
        if (outTransaction.mSharedElementSourceNames != null
                && !outTransaction.mSharedElementSourceNames.isEmpty()) {
            final String sourceName = outIsPop
                    ? outTransaction.mSharedElementTargetNames.get(0)
                    : outTransaction.mSharedElementSourceNames.get(0);
            final View outEpicenterView = outSharedElements.get(sourceName);
            impl.setEpicenter(sharedElementTransition, outEpicenterView);

            if (exitTransition != null) {
                impl.setEpicenter(exitTransition, outEpicenterView);
            }
        }
    }

    /**
     * A utility to retain only the mappings in {@code nameOverrides} that have a value
     * that has a key in {@code namedViews}. This is a useful equivalent to
     * {@link ArrayMap#retainAll(Collection)} for values.
     */
    private static void retainValues(ArrayMap<String, String> nameOverrides,
            ArrayMap<String, View> namedViews) {
        for (int i = nameOverrides.size() - 1; i >= 0; i--) {
            final String targetName = nameOverrides.valueAt(i);
            if (!namedViews.containsKey(targetName)) {
                nameOverrides.removeAt(i);
            }
        }
    }

    /**
     * Calls the {@link SharedElementCallback#onSharedElementStart(List, List, List)} or
     * {@link SharedElementCallback#onSharedElementEnd(List, List, List)} on the appropriate
     * incoming or outgoing fragment.
     *
     * @param inFragment The incoming fragment
     * @param outFragment The outgoing fragment
     * @param isPop Is the incoming fragment part of a pop transaction?
     * @param sharedElements The shared element Views
     * @param isStart Call the start or end call on the SharedElementCallback
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void callSharedElementStartEnd(Fragment inFragment, Fragment outFragment,
            boolean isPop, ArrayMap<String, View> sharedElements, boolean isStart) {
        SharedElementCallback sharedElementCallback = isPop
                ? outFragment.getEnterTransitionCallback()
                : inFragment.getEnterTransitionCallback();
        if (sharedElementCallback != null) {
            ArrayList<View> views = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            final int count = sharedElements == null ? 0 : sharedElements.size();
            for (int i = 0; i < count; i++) {
                names.add(sharedElements.keyAt(i));
                views.add(sharedElements.valueAt(i));
            }
            if (isStart) {
                sharedElementCallback.onSharedElementStart(names, views, null);
            } else {
                sharedElementCallback.onSharedElementEnd(names, views, null);
            }
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static ArrayList<View> configureEnteringExitingViews(FragmentTransitionImpl impl,
            Object transition,
            Fragment fragment, ArrayList<View> sharedElements, View nonExistentView) {
        ArrayList<View> viewList = null;
        if (transition != null) {
            viewList = new ArrayList<>();
            View root = fragment.getView();
            if (root != null) {
                impl.captureTransitioningViews(viewList, root);
            }
            if (sharedElements != null) {
                viewList.removeAll(sharedElements);
            }
            if (!viewList.isEmpty()) {
                viewList.add(nonExistentView);
                impl.addTargets(transition, viewList);
            }
        }
        return viewList;
    }

    /**
     * Sets the visibility of all Views in {@code views} to {@code visibility}.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void setViewVisibility(ArrayList<View> views, int visibility) {
        if (views == null) {
            return;
        }
        for (int i = views.size() - 1; i >= 0; i--) {
            final View view = views.get(i);
            view.setVisibility(visibility);
        }
    }

    /**
     * Merges exit, shared element, and enter transitions so that they act together or
     * sequentially as defined in the fragments.
     */
    private static Object mergeTransitions(FragmentTransitionImpl impl, Object enterTransition,
            Object exitTransition, Object sharedElementTransition, Fragment inFragment,
            boolean isPop) {
        boolean overlap = true;
        if (enterTransition != null && exitTransition != null && inFragment != null) {
            overlap = isPop ? inFragment.getAllowReturnTransitionOverlap() :
                    inFragment.getAllowEnterTransitionOverlap();
        }

        // Wrap the transitions. Explicit targets like in enter and exit will cause the
        // views to be targeted regardless of excluded views. If that happens, then the
        // excluded fragments views (hidden fragments) will still be in the transition.

        Object transition;
        if (overlap) {
            // Regular transition -- do it all together
            transition = impl.mergeTransitionsTogether(exitTransition,
                    enterTransition, sharedElementTransition);
        } else {
            // First do exit, then enter, but allow shared element transition to happen
            // during both.
            transition = impl.mergeTransitionsInSequence(exitTransition,
                    enterTransition, sharedElementTransition);
        }
        return transition;
    }

    /**
     * Finds the first removed fragment and last added fragments when going forward.
     * If none of the fragments have transitions, then both lists will be empty.
     *
     * @param transitioningFragments Keyed on the container ID, the first fragments to be removed,
     *                               and last fragments to be added. This will be modified by
     *                               this method.
     */
    public static void calculateFragments(BackStackRecord transaction,
            SparseArray<FragmentContainerTransition> transitioningFragments,
            boolean isReordered) {
        final int numOps = transaction.mOps.size();
        for (int opNum = 0; opNum < numOps; opNum++) {
            final BackStackRecord.Op op = transaction.mOps.get(opNum);
            addToFirstInLastOut(transaction, op, transitioningFragments, false, isReordered);
        }
    }

    /**
     * Finds the first removed fragment and last added fragments when popping the back stack.
     * If none of the fragments have transitions, then both lists will be empty.
     *
     * @param transitioningFragments Keyed on the container ID, the first fragments to be removed,
     *                               and last fragments to be added. This will be modified by
     *                               this method.
     */
    public static void calculatePopFragments(BackStackRecord transaction,
            SparseArray<FragmentContainerTransition> transitioningFragments, boolean isReordered) {
        if (!transaction.mManager.mContainer.onHasView()) {
            return; // nothing to see, so no transitions
        }
        final int numOps = transaction.mOps.size();
        for (int opNum = numOps - 1; opNum >= 0; opNum--) {
            final BackStackRecord.Op op = transaction.mOps.get(opNum);
            addToFirstInLastOut(transaction, op, transitioningFragments, true, isReordered);
        }
    }

    static boolean supportsTransition() {
        return PLATFORM_IMPL != null || SUPPORT_IMPL != null;
    }

    /**
     * Examines the {@code command} and may set the first out or last in fragment for the fragment's
     * container.
     *
     * @param transaction The executing transaction
     * @param op The operation being run.
     * @param transitioningFragments A structure holding the first in and last out fragments
     *                               for each fragment container.
     * @param isPop Is the operation a pop?
     * @param isReorderedTransaction True if the operations have been partially executed and the
     *                               added fragments have Views in the hierarchy or false if the
     *                               operations haven't been executed yet.
     */
    @SuppressWarnings("ReferenceEquality")
    private static void addToFirstInLastOut(BackStackRecord transaction, BackStackRecord.Op op,
            SparseArray<FragmentContainerTransition> transitioningFragments, boolean isPop,
            boolean isReorderedTransaction) {
        final Fragment fragment = op.mFragment;
        if (fragment == null) {
            return; // no fragment, no transition
        }
        final int containerId = fragment.mContainerId;
        if (containerId == 0) {
            return; // no container, no transition
        }
        final int command = isPop ? INVERSE_OPS[op.mCmd] : op.mCmd;
        boolean setLastIn = false;
        boolean wasRemoved = false;
        boolean setFirstOut = false;
        boolean wasAdded = false;
        switch (command) {
            case BackStackRecord.OP_SHOW:
                if (isReorderedTransaction) {
                    setLastIn = fragment.mHiddenChanged && !fragment.mHidden && fragment.mAdded;
                } else {
                    setLastIn = fragment.mHidden;
                }
                wasAdded = true;
                break;
            case BackStackRecord.OP_ADD:
            case BackStackRecord.OP_ATTACH:
                if (isReorderedTransaction) {
                    setLastIn = fragment.mIsNewlyAdded;
                } else {
                    setLastIn = !fragment.mAdded && !fragment.mHidden;
                }
                wasAdded = true;
                break;
            case BackStackRecord.OP_HIDE:
                if (isReorderedTransaction) {
                    setFirstOut = fragment.mHiddenChanged && fragment.mAdded && fragment.mHidden;
                } else {
                    setFirstOut = fragment.mAdded && !fragment.mHidden;
                }
                wasRemoved = true;
                break;
            case BackStackRecord.OP_REMOVE:
            case BackStackRecord.OP_DETACH:
                if (isReorderedTransaction) {
                    setFirstOut = !fragment.mAdded && fragment.mView != null
                            && fragment.mView.getVisibility() == View.VISIBLE
                            && fragment.mPostponedAlpha >= 0;
                } else {
                    setFirstOut = fragment.mAdded && !fragment.mHidden;
                }
                wasRemoved = true;
                break;
        }
        FragmentContainerTransition containerTransition = transitioningFragments.get(containerId);
        if (setLastIn) {
            containerTransition =
                    ensureContainer(containerTransition, transitioningFragments, containerId);
            containerTransition.lastIn = fragment;
            containerTransition.lastInIsPop = isPop;
            containerTransition.lastInTransaction = transaction;
        }
        if (!isReorderedTransaction && wasAdded) {
            if (containerTransition != null && containerTransition.firstOut == fragment) {
                containerTransition.firstOut = null;
            }

            /*
             * Ensure that fragments that are entering are at least at the CREATED state
             * so that they may load Transitions using TransitionInflater.
             */
            FragmentManagerImpl manager = transaction.mManager;
            if (fragment.mState < Fragment.CREATED && manager.mCurState >= Fragment.CREATED
                    && !transaction.mReorderingAllowed) {
                manager.makeActive(fragment);
                manager.moveToState(fragment, Fragment.CREATED, 0, 0, false);
            }
        }
        if (setFirstOut && (containerTransition == null || containerTransition.firstOut == null)) {
            containerTransition =
                    ensureContainer(containerTransition, transitioningFragments, containerId);
            containerTransition.firstOut = fragment;
            containerTransition.firstOutIsPop = isPop;
            containerTransition.firstOutTransaction = transaction;
        }

        if (!isReorderedTransaction && wasRemoved
                && (containerTransition != null && containerTransition.lastIn == fragment)) {
            containerTransition.lastIn = null;
        }
    }

    /**
     * Ensures that a FragmentContainerTransition has been added to the SparseArray. If so,
     * it returns the existing one. If not, one is created and added to the SparseArray and
     * returned.
     */
    private static FragmentContainerTransition ensureContainer(
            FragmentContainerTransition containerTransition,
            SparseArray<FragmentContainerTransition> transitioningFragments, int containerId) {
        if (containerTransition == null) {
            containerTransition = new FragmentContainerTransition();
            transitioningFragments.put(containerId, containerTransition);
        }
        return containerTransition;
    }

    /**
     * Tracks the last fragment added and first fragment removed for fragment transitions.
     * This also tracks which fragments are changed by push or pop transactions.
     */
    static class FragmentContainerTransition {
        /**
         * The last fragment added/attached/shown in its container
         */
        public Fragment lastIn;

        /**
         * true when lastIn was added during a pop transaction or false if added with a push
         */
        public boolean lastInIsPop;

        /**
         * The transaction that included the last in fragment
         */
        public BackStackRecord lastInTransaction;

        /**
         * The first fragment with a View that was removed/detached/hidden in its container.
         */
        public Fragment firstOut;

        /**
         * true when firstOut was removed during a pop transaction or false otherwise
         */
        public boolean firstOutIsPop;

        /**
         * The transaction that included the first out fragment
         */
        public BackStackRecord firstOutTransaction;
    }

    private FragmentTransition() {
    }
}
