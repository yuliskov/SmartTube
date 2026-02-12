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

import android.app.Activity;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;
import androidx.leanback.transition.TransitionHelper;
import androidx.leanback.transition.TransitionListener;
import androidx.leanback.widget.FullWidthDetailsOverviewRowPresenter.ViewHolder;

import java.lang.ref.WeakReference;

/**
 * Helper class to assist delayed shared element activity transition for view created by
 * {@link FullWidthDetailsOverviewRowPresenter}. User must call
 * {@link #setSharedElementEnterTransition(Activity, String, long)} during activity onCreate() and
 * call {@link FullWidthDetailsOverviewRowPresenter#setListener(FullWidthDetailsOverviewRowPresenter.Listener)}.
 * The helper implements {@link FullWidthDetailsOverviewRowPresenter.Listener} and starts delayed
 * activity transition once {@link FullWidthDetailsOverviewRowPresenter.Listener#onBindLogo(ViewHolder)}
 * is called.
 */
public class FullWidthDetailsOverviewSharedElementHelper extends
        FullWidthDetailsOverviewRowPresenter.Listener {

    static final String TAG = "DetailsTransitionHelper";
    static final boolean DEBUG = false;

    private static final long DEFAULT_TIMEOUT = 5000;

    static class TransitionTimeOutRunnable implements Runnable {
        WeakReference<FullWidthDetailsOverviewSharedElementHelper> mHelperRef;

        TransitionTimeOutRunnable(FullWidthDetailsOverviewSharedElementHelper helper) {
            mHelperRef = new WeakReference<FullWidthDetailsOverviewSharedElementHelper>(helper);
        }

        @Override
        public void run() {
            FullWidthDetailsOverviewSharedElementHelper helper = mHelperRef.get();
            if (helper == null) {
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "timeout " + helper.mActivityToRunTransition);
            }
            helper.startPostponedEnterTransition();
        }
    }

    ViewHolder mViewHolder;
    Activity mActivityToRunTransition;
    private boolean mStartedPostpone;
    String mSharedElementName;
    private boolean mAutoStartSharedElementTransition = true;

    public void setSharedElementEnterTransition(Activity activity, String sharedElementName) {
        setSharedElementEnterTransition(activity, sharedElementName, DEFAULT_TIMEOUT);
    }

    public void setSharedElementEnterTransition(Activity activity, String sharedElementName,
            long timeoutMs) {
        if ((activity == null && !TextUtils.isEmpty(sharedElementName))
                || (activity != null && TextUtils.isEmpty(sharedElementName))) {
            throw new IllegalArgumentException();
        }
        if (activity == mActivityToRunTransition
                && TextUtils.equals(sharedElementName, mSharedElementName)) {
            return;
        }
        mActivityToRunTransition = activity;
        mSharedElementName = sharedElementName;
        if (DEBUG) {
            Log.d(TAG, "postponeEnterTransition " + mActivityToRunTransition);
        }
        Object transition = TransitionHelper.getSharedElementEnterTransition(activity.getWindow());
        setAutoStartSharedElementTransition(transition != null);
        ActivityCompat.postponeEnterTransition(mActivityToRunTransition);
        if (timeoutMs > 0) {
            new Handler().postDelayed(new TransitionTimeOutRunnable(this), timeoutMs);
        }
    }

    /**
     * Enable or disable auto startPostponedEnterTransition() when bound to logo. When it's
     * disabled, app must call {@link #startPostponedEnterTransition()} to kick off
     * windowEnterTransition. By default, it is disabled when there is no
     * windowEnterSharedElementTransition set on the activity.
     */
    public void setAutoStartSharedElementTransition(boolean enabled) {
        mAutoStartSharedElementTransition = enabled;
    }

    /**
     * Returns true if auto startPostponedEnterTransition() when bound to logo. When it's
     * disabled, app must call {@link #startPostponedEnterTransition()} to kick off
     * windowEnterTransition. By default, it is disabled when there is no
     * windowEnterSharedElementTransition set on the activity.
     */
    public boolean getAutoStartSharedElementTransition() {
        return mAutoStartSharedElementTransition;
    }

    @Override
    public void onBindLogo(ViewHolder vh) {
        if (DEBUG) {
            Log.d(TAG, "onBindLogo, could start transition of " + mActivityToRunTransition);
        }
        mViewHolder = vh;
        if (!mAutoStartSharedElementTransition) {
            return;
        }
        if (mViewHolder != null) {
            if (DEBUG) {
                Log.d(TAG, "rebind? clear transitionName on current viewHolder "
                        + mViewHolder.getOverviewView());
            }
            ViewCompat.setTransitionName(mViewHolder.getLogoViewHolder().view, null);
        }
        // After we got a image drawable,  we can determine size of right panel.
        // We want right panel to have fixed size so that the right panel don't change size
        // when the overview is layout as a small bounds in transition.
        mViewHolder.getDetailsDescriptionFrame().postOnAnimation(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) {
                    Log.d(TAG, "setTransitionName "+mViewHolder.getOverviewView());
                }
                ViewCompat.setTransitionName(mViewHolder.getLogoViewHolder().view,
                        mSharedElementName);
                Object transition = TransitionHelper.getSharedElementEnterTransition(
                        mActivityToRunTransition.getWindow());
                if (transition != null) {
                    TransitionHelper.addTransitionListener(transition, new TransitionListener() {
                        @Override
                        public void onTransitionEnd(Object transition) {
                            if (DEBUG) {
                                Log.d(TAG, "onTransitionEnd " + mActivityToRunTransition);
                            }
                            // after transition if the action row still focused, transfer
                            // focus to its children
                            if (mViewHolder.getActionsRow().isFocused()) {
                                mViewHolder.getActionsRow().requestFocus();
                            }
                            TransitionHelper.removeTransitionListener(transition, this);
                        }
                    });
                }
                startPostponedEnterTransitionInternal();
            }
        });
    }

    /**
     * Manually start postponed enter transition.
     */
    public void startPostponedEnterTransition() {
        new Handler().post(new Runnable(){
            @Override
            public void run() {
                startPostponedEnterTransitionInternal();
            }
        });
    }

    void startPostponedEnterTransitionInternal() {
        if (!mStartedPostpone && mViewHolder != null) {
            if (DEBUG) {
                Log.d(TAG, "startPostponedEnterTransition " + mActivityToRunTransition);
            }
            ActivityCompat.startPostponedEnterTransition(mActivityToRunTransition);
            mStartedPostpone = true;
        }
    }
}
