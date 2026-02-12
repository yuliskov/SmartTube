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
package androidx.leanback.util;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.util.Log;

import androidx.annotation.RestrictTo;

import java.util.ArrayList;

/**
 * State: each State has incoming Transitions and outgoing Transitions.
 * When {@link State#mBranchStart} is true, all the outgoing Transitions may be triggered, when
 * {@link State#mBranchStart} is false, only first outgoing Transition will be triggered.
 * When {@link State#mBranchEnd} is true, all the incoming Transitions must be triggered for the
 * State to run. When {@link State#mBranchEnd} is false, only need one incoming Transition triggered
 * for the State to run.
 * Transition: three types:
 * 1. Event based transition, transition will be triggered when {@link #fireEvent(Event)} is called.
 * 2. Auto transition, transition will be triggered when {@link Transition#mFromState} is executed.
 * 3. Condiitonal Auto transition, transition will be triggered when {@link Transition#mFromState}
 * is executed and {@link Transition#mCondition} passes.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public final class StateMachine {

    static final boolean DEBUG = false;
    static final String TAG = "StateMachine";

    /**
     * No request on the State
     */
    public static final int STATUS_ZERO = 0;

    /**
     * Has been executed
     */
    public static final int STATUS_INVOKED = 1;

    /**
     * Used in Transition
     */
    public static class Event {
        final String mName;

        public Event(String name) {
            mName = name;
        }
    }

    /**
     * Used in transition
     */
    public static class Condition {
        final String mName;

        public Condition(String name) {
            mName = name;
        }

        /**
         * @return True if can proceed and mark the transition INVOKED
         */
        public boolean canProceed() {
            return true;
        }
    }

    static class Transition {
        final State mFromState;
        final State mToState;
        final Event mEvent;
        final Condition mCondition;
        int mState = STATUS_ZERO;

        Transition(State fromState, State toState, Event event) {
            if (event == null) {
                throw new IllegalArgumentException();
            }
            mFromState = fromState;
            mToState = toState;
            mEvent = event;
            mCondition = null;
        }

        Transition(State fromState, State toState) {
            mFromState = fromState;
            mToState = toState;
            mEvent = null;
            mCondition = null;
        }

        Transition(State fromState, State toState, Condition condition) {
            if (condition == null) {
                throw new IllegalArgumentException();
            }
            mFromState = fromState;
            mToState = toState;
            mEvent = null;
            mCondition = condition;
        }

        @Override
        public String toString() {
            String signalName;
            if (mEvent != null) {
                signalName = mEvent.mName;
            } else if (mCondition != null) {
                signalName = mCondition.mName;
            } else {
                signalName = "auto";
            }
            return "[" + mFromState.mName + " -> " + mToState.mName + " <"
                    + signalName + ">]";
        }
    }

    /**
     * @see StateMachine
     */
    public static class State {

        final String mName;
        final boolean mBranchStart;
        final boolean mBranchEnd;
        int mStatus = STATUS_ZERO;
        int mInvokedOutTransitions = 0;
        ArrayList<Transition> mIncomings;
        ArrayList<Transition> mOutgoings;

        @Override
        public String toString() {
            return "[" + mName + " " + mStatus + "]";
        }

        /**
         * Create a State which is not branch start and a branch end.
         */
        public State(String name) {
            this(name, false, true);
        }

        /**
         * Create a State
         * @param branchStart True if can run all out going transitions or false execute the first
         *                    out going transition.
         * @param branchEnd True if wait all incoming transitions executed or false
         *                              only need one of the transition executed.
         */
        public State(String name, boolean branchStart, boolean branchEnd) {
            mName = name;
            mBranchStart = branchStart;
            mBranchEnd = branchEnd;
        }

        void addIncoming(Transition t) {
            if (mIncomings == null) {
                mIncomings = new ArrayList();
            }
            mIncomings.add(t);
        }

        void addOutgoing(Transition t) {
            if (mOutgoings == null) {
                mOutgoings = new ArrayList();
            }
            mOutgoings.add(t);
        }

        /**
         * Run State, Subclass may override.
         */
        public void run() {
        }

        final boolean checkPreCondition() {
            if (mIncomings == null) {
                return true;
            }
            if (mBranchEnd) {
                for (Transition t: mIncomings) {
                    if (t.mState != STATUS_INVOKED) {
                        return false;
                    }
                }
                return true;
            } else {
                for (Transition t: mIncomings) {
                    if (t.mState == STATUS_INVOKED) {
                        return true;
                    }
                }
                return false;
            }
        }

        /**
         * @return True if the State has been executed.
         */
        final boolean runIfNeeded() {
            if (mStatus != STATUS_INVOKED) {
                if (checkPreCondition()) {
                    if (DEBUG) {
                        Log.d(TAG, "execute " + this);
                    }
                    mStatus = STATUS_INVOKED;
                    run();
                    signalAutoTransitionsAfterRun();
                    return true;
                }
            }
            return false;
        }

        final void signalAutoTransitionsAfterRun() {
            if (mOutgoings != null) {
                for (Transition t: mOutgoings) {
                    if (t.mEvent == null) {
                        if (t.mCondition == null || t.mCondition.canProceed()) {
                            if (DEBUG) {
                                Log.d(TAG, "signal " + t);
                            }
                            mInvokedOutTransitions++;
                            t.mState = STATUS_INVOKED;
                            if (!mBranchStart) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        /**
         * Get status, return one of {@link #STATUS_ZERO}, {@link #STATUS_INVOKED}.
         * @return Status of the State.
         */
        public final int getStatus() {
            return mStatus;
        }
    }

    final ArrayList<State> mStates = new ArrayList<State>();
    final ArrayList<State> mFinishedStates = new ArrayList();
    final ArrayList<State> mUnfinishedStates = new ArrayList();

    public StateMachine() {
    }

    /**
     * Add a State to StateMachine, ignore if it is already added.
     * @param state The state to add.
     */
    public void addState(State state) {
        if (!mStates.contains(state)) {
            mStates.add(state);
        }
    }

    /**
     * Add event-triggered transition between two states.
     * @param fromState The from state.
     * @param toState The to state.
     * @param event The event that needed to perform the transition.
     */
    public void addTransition(State fromState, State toState, Event event) {
        Transition transition = new Transition(fromState, toState, event);
        toState.addIncoming(transition);
        fromState.addOutgoing(transition);
    }

    /**
     * Add a conditional auto transition between two states.
     * @param fromState The from state.
     * @param toState The to state.
     */
    public void addTransition(State fromState, State toState, Condition condition) {
        Transition transition = new Transition(fromState, toState, condition);
        toState.addIncoming(transition);
        fromState.addOutgoing(transition);
    }

    /**
     * Add an auto transition between two states.
     * @param fromState The from state to add.
     * @param toState The to state to add.
     */
    public void addTransition(State fromState, State toState) {
        Transition transition = new Transition(fromState, toState);
        toState.addIncoming(transition);
        fromState.addOutgoing(transition);
    }

    /**
     * Start the state machine.
     */
    public void start() {
        if (DEBUG) {
            Log.d(TAG, "start");
        }
        mUnfinishedStates.addAll(mStates);
        runUnfinishedStates();
    }

    void runUnfinishedStates() {
        boolean changed;
        do {
            changed = false;
            for (int i = mUnfinishedStates.size() - 1; i >= 0; i--) {
                State state = mUnfinishedStates.get(i);
                if (state.runIfNeeded()) {
                    mUnfinishedStates.remove(i);
                    mFinishedStates.add(state);
                    changed = true;
                }
            }
        } while (changed);
    }

    /**
     * Find outgoing Transitions of invoked State whose Event matches, mark the Transition invoked.
     */
    public void fireEvent(Event event) {
        for (int i = 0; i < mFinishedStates.size(); i++) {
            State state = mFinishedStates.get(i);
            if (state.mOutgoings != null) {
                if (!state.mBranchStart && state.mInvokedOutTransitions > 0) {
                    continue;
                }
                for (Transition t : state.mOutgoings) {
                    if (t.mState != STATUS_INVOKED && t.mEvent == event) {
                        if (DEBUG) {
                            Log.d(TAG, "signal " + t);
                        }
                        t.mState = STATUS_INVOKED;
                        state.mInvokedOutTransitions++;
                        if (!state.mBranchStart) {
                            break;
                        }
                    }
                }
            }
        }
        runUnfinishedStates();
    }

    /**
     * Reset status to orignal status
     */
    public void reset() {
        if (DEBUG) {
            Log.d(TAG, "reset");
        }
        mUnfinishedStates.clear();
        mFinishedStates.clear();
        for (State state: mStates) {
            state.mStatus = STATUS_ZERO;
            state.mInvokedOutTransitions = 0;
            if (state.mOutgoings != null) {
                for (Transition t: state.mOutgoings) {
                    t.mState = STATUS_ZERO;
                }
            }
        }
    }
}
