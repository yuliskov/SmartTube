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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.leanback.util.StateMachine;
import androidx.leanback.util.StateMachine.Condition;
import androidx.leanback.util.StateMachine.Event;
import androidx.leanback.util.StateMachine.State;

/**
 * Base class for leanback Fragments. This class is not intended to be subclassed by apps.
 */
@SuppressWarnings("FragmentNotInstantiable")
public class BaseSupportFragment extends BrandedSupportFragment {

    /**
     * The start state for all
     */
    final State STATE_START = new State("START", true, false);

    /**
     * Initial State for ENTRNACE transition.
     */
    final State STATE_ENTRANCE_INIT = new State("ENTRANCE_INIT");

    /**
     * prepareEntranceTransition is just called, but view not ready yet. We can enable the
     * busy spinner.
     */
    final State STATE_ENTRANCE_ON_PREPARED = new State("ENTRANCE_ON_PREPARED", true, false) {
        @Override
        public void run() {
            mProgressBarManager.show();
        }
    };

    /**
     * prepareEntranceTransition is called and main content view to slide in was created, so we can
     * call {@link #onEntranceTransitionPrepare}. Note that we dont set initial content to invisible
     * in this State, the process is very different in subclass, e.g. BrowseSupportFragment hide header
     * views and hide main fragment view in two steps.
     */
    final State STATE_ENTRANCE_ON_PREPARED_ON_CREATEVIEW = new State(
            "ENTRANCE_ON_PREPARED_ON_CREATEVIEW") {
        @Override
        public void run() {
            onEntranceTransitionPrepare();
        }
    };

    /**
     * execute the entrance transition.
     */
    final State STATE_ENTRANCE_PERFORM = new State("STATE_ENTRANCE_PERFORM") {
        @Override
        public void run() {
            mProgressBarManager.hide();
            onExecuteEntranceTransition();
        }
    };

    /**
     * execute onEntranceTransitionEnd.
     */
    final State STATE_ENTRANCE_ON_ENDED = new State("ENTRANCE_ON_ENDED") {
        @Override
        public void run() {
            onEntranceTransitionEnd();
        }
    };

    /**
     * either entrance transition completed or skipped
     */
    final State STATE_ENTRANCE_COMPLETE = new State("ENTRANCE_COMPLETE", true, false);

    /**
     * Event fragment.onCreate()
     */
    final Event EVT_ON_CREATE = new Event("onCreate");

    /**
     * Event fragment.onViewCreated()
     */
    final Event EVT_ON_CREATEVIEW = new Event("onCreateView");

    /**
     * Event for {@link #prepareEntranceTransition()} is called.
     */
    final Event EVT_PREPARE_ENTRANCE = new Event("prepareEntranceTransition");

    /**
     * Event for {@link #startEntranceTransition()} is called.
     */
    final Event EVT_START_ENTRANCE = new Event("startEntranceTransition");

    /**
     * Event for entrance transition is ended through Transition listener.
     */
    final Event EVT_ENTRANCE_END = new Event("onEntranceTransitionEnd");

    /**
     * Event for skipping entrance transition if not supported.
     */
    final Condition COND_TRANSITION_NOT_SUPPORTED = new Condition("EntranceTransitionNotSupport") {
        @Override
        public boolean canProceed() {
            return !TransitionHelper.systemSupportsEntranceTransitions();
        }
    };

    final StateMachine mStateMachine = new StateMachine();

    Object mEntranceTransition;
    final ProgressBarManager mProgressBarManager = new ProgressBarManager();

    @SuppressLint("ValidFragment")
    BaseSupportFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        createStateMachineStates();
        createStateMachineTransitions();
        mStateMachine.start();
        super.onCreate(savedInstanceState);
        mStateMachine.fireEvent(EVT_ON_CREATE);
    }

    void createStateMachineStates() {
        mStateMachine.addState(STATE_START);
        mStateMachine.addState(STATE_ENTRANCE_INIT);
        mStateMachine.addState(STATE_ENTRANCE_ON_PREPARED);
        mStateMachine.addState(STATE_ENTRANCE_ON_PREPARED_ON_CREATEVIEW);
        mStateMachine.addState(STATE_ENTRANCE_PERFORM);
        mStateMachine.addState(STATE_ENTRANCE_ON_ENDED);
        mStateMachine.addState(STATE_ENTRANCE_COMPLETE);
    }

    void createStateMachineTransitions() {
        mStateMachine.addTransition(STATE_START, STATE_ENTRANCE_INIT, EVT_ON_CREATE);
        mStateMachine.addTransition(STATE_ENTRANCE_INIT, STATE_ENTRANCE_COMPLETE,
                COND_TRANSITION_NOT_SUPPORTED);
        mStateMachine.addTransition(STATE_ENTRANCE_INIT, STATE_ENTRANCE_COMPLETE,
                EVT_ON_CREATEVIEW);
        mStateMachine.addTransition(STATE_ENTRANCE_INIT, STATE_ENTRANCE_ON_PREPARED,
                EVT_PREPARE_ENTRANCE);
        mStateMachine.addTransition(STATE_ENTRANCE_ON_PREPARED,
                STATE_ENTRANCE_ON_PREPARED_ON_CREATEVIEW,
                EVT_ON_CREATEVIEW);
        mStateMachine.addTransition(STATE_ENTRANCE_ON_PREPARED,
                STATE_ENTRANCE_PERFORM,
                EVT_START_ENTRANCE);
        mStateMachine.addTransition(STATE_ENTRANCE_ON_PREPARED_ON_CREATEVIEW,
                STATE_ENTRANCE_PERFORM);
        mStateMachine.addTransition(STATE_ENTRANCE_PERFORM,
                STATE_ENTRANCE_ON_ENDED,
                EVT_ENTRANCE_END);
        mStateMachine.addTransition(STATE_ENTRANCE_ON_ENDED, STATE_ENTRANCE_COMPLETE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mStateMachine.fireEvent(EVT_ON_CREATEVIEW);
    }

    /**
     * Enables entrance transition.<p>
     * Entrance transition is the standard slide-in transition that shows rows of data in
     * browse screen and details screen.
     * <p>
     * The method is ignored before LOLLIPOP (API21).
     * <p>
     * This method must be called in or
     * before onCreate().  Typically entrance transition should be enabled when savedInstance is
     * null so that fragment restored from instanceState does not run an extra entrance transition.
     * When the entrance transition is enabled, the fragment will make headers and content
     * hidden initially.
     * When data of rows are ready, app must call {@link #startEntranceTransition()} to kick off
     * the transition, otherwise the rows will be invisible forever.
     * <p>
     * It is similar to android:windowsEnterTransition and can be considered a late-executed
     * android:windowsEnterTransition controlled by app.  There are two reasons that app needs it:
     * <li> Workaround the problem that activity transition is not available between launcher and
     * app.  Browse activity must programmatically start the slide-in transition.</li>
     * <li> Separates DetailsOverviewRow transition from other rows transition.  So that
     * the DetailsOverviewRow transition can be executed earlier without waiting for all rows
     * to be loaded.</li>
     * <p>
     * Transition object is returned by createEntranceTransition().  Typically the app does not need
     * override the default transition that browse and details provides.
     */
    public void prepareEntranceTransition() {
        mStateMachine.fireEvent(EVT_PREPARE_ENTRANCE);
    }

    /**
     * Create entrance transition.  Subclass can override to load transition from
     * resource or construct manually.  Typically app does not need to
     * override the default transition that browse and details provides.
     */
    protected Object createEntranceTransition() {
        return null;
    }

    /**
     * Run entrance transition.  Subclass may use TransitionManager to perform
     * go(Scene) or beginDelayedTransition().  App should not override the default
     * implementation of browse and details fragment.
     */
    protected void runEntranceTransition(Object entranceTransition) {
    }

    /**
     * Callback when entrance transition is prepared.  This is when fragment should
     * stop user input and animations.
     */
    protected void onEntranceTransitionPrepare() {
    }

    /**
     * Callback when entrance transition is started.  This is when fragment should
     * stop processing layout.
     */
    protected void onEntranceTransitionStart() {
    }

    /**
     * Callback when entrance transition is ended.
     */
    protected void onEntranceTransitionEnd() {
    }

    /**
     * When fragment finishes loading data, it should call startEntranceTransition()
     * to execute the entrance transition.
     * startEntranceTransition() will start transition only if both two conditions
     * are satisfied:
     * <li> prepareEntranceTransition() was called.</li>
     * <li> has not executed entrance transition yet.</li>
     * <p>
     * If startEntranceTransition() is called before onViewCreated(), it will be pending
     * and executed when view is created.
     */
    public void startEntranceTransition() {
        mStateMachine.fireEvent(EVT_START_ENTRANCE);
    }

    void onExecuteEntranceTransition() {
        // wait till views get their initial position before start transition
        final View view = getView();
        if (view == null) {
            // fragment view destroyed, transition not needed
            return;
        }
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                if (getContext() == null || getView() == null) {
                    // bail out if fragment is destroyed immediately after startEntranceTransition
                    return true;
                }
                internalCreateEntranceTransition();
                onEntranceTransitionStart();
                if (mEntranceTransition != null) {
                    runEntranceTransition(mEntranceTransition);
                } else {
                    mStateMachine.fireEvent(EVT_ENTRANCE_END);
                }
                return false;
            }
        });
        view.invalidate();
    }

    void internalCreateEntranceTransition() {
        mEntranceTransition = createEntranceTransition();
        if (mEntranceTransition == null) {
            return;
        }
        TransitionHelper.addTransitionListener(mEntranceTransition, new TransitionListener() {
            @Override
            public void onTransitionEnd(Object transition) {
                mEntranceTransition = null;
                mStateMachine.fireEvent(EVT_ENTRANCE_END);
            }
        });
    }

    /**
     * Returns the {@link ProgressBarManager}.
     * @return The {@link ProgressBarManager}.
     */
    public final ProgressBarManager getProgressBarManager() {
        return mProgressBarManager;
    }
}
