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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.OnBackPressedDispatcherOwner;
import androidx.annotation.CallSuper;
import androidx.annotation.ContentView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.collection.SparseArrayCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.app.SharedElementCallback;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.loader.app.LoaderManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collection;

/**
 * Base class for activities that want to use the support-based
 * {@link Fragment Fragments}.
 *
 * <p>Known limitations:</p>
 * <ul>
 * <li> <p>When using the <code>&lt;fragment></code> tag, this implementation can not
 * use the parent view's ID as the new fragment's ID.  You must explicitly
 * specify an ID (or tag) in the <code>&lt;fragment></code>.</p>
 * </ul>
 */
public class FragmentActivity extends ComponentActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        ActivityCompat.RequestPermissionsRequestCodeValidator {
    private static final String TAG = "FragmentActivity";

    static final String FRAGMENTS_TAG = "android:support:fragments";
    static final String NEXT_CANDIDATE_REQUEST_INDEX_TAG = "android:support:next_request_index";
    static final String ALLOCATED_REQUEST_INDICIES_TAG = "android:support:request_indicies";
    static final String REQUEST_FRAGMENT_WHO_TAG = "android:support:request_fragment_who";
    static final int MAX_NUM_PENDING_FRAGMENT_ACTIVITY_RESULTS = 0xffff - 1;

    final FragmentController mFragments = FragmentController.createController(new HostCallbacks());
    /**
     * A {@link Lifecycle} that is exactly nested outside of when the FragmentController
     * has its state changed, providing the proper nesting of Lifecycle callbacks
     * <p>
     * TODO(b/127528777) Drive Fragment Lifecycle with LifecycleObserver
     */
    final LifecycleRegistry mFragmentLifecycleRegistry = new LifecycleRegistry(this);

    boolean mCreated;
    boolean mResumed;
    boolean mStopped = true;

    boolean mRequestedPermissionsFromFragment;

    // We need to keep track of whether startIntentSenderForResult originated from a Fragment, so we
    // can conditionally check whether the requestCode collides with our reserved ID space for the
    // request index (see above). Unfortunately we can't just call
    // super.startIntentSenderForResult(...) to bypass the check when the call didn't come from a
    // fragment, since we need to use the ActivityCompat version for backward compatibility.
    boolean mStartedIntentSenderFromFragment;
    // We need to keep track of whether startActivityForResult originated from a Fragment, so we
    // can conditionally check whether the requestCode collides with our reserved ID space for the
    // request index (see above). Unfortunately we can't just call
    // super.startActivityForResult(...) to bypass the check when the call didn't come from a
    // fragment, since we need to use the ActivityCompat version for backward compatibility.
    boolean mStartedActivityFromFragment;

    // A hint for the next candidate request index. Request indicies are ints between 0 and 2^16-1
    // which are encoded into the upper 16 bits of the requestCode for
    // Fragment.startActivityForResult(...) calls. This allows us to dispatch onActivityResult(...)
    // to the appropriate Fragment. Request indicies are allocated by allocateRequestIndex(...).
    int mNextCandidateRequestIndex;
    // A map from request index to Fragment "who" (i.e. a Fragment's unique identifier). Used to
    // keep track of the originating Fragment for Fragment.startActivityForResult(...) calls, so we
    // can dispatch the onActivityResult(...) to the appropriate Fragment. Will only contain entries
    // for startActivityForResult calls where a result has not yet been delivered.
    SparseArrayCompat<String> mPendingFragmentActivityResults;

    /**
     * Default constructor for FragmentActivity. All Activities must have a default constructor
     * for API 27 and lower devices or when using the default
     * {@link android.app.AppComponentFactory}.
     */
    public FragmentActivity() {
        super();
    }

    /**
     * Alternate constructor that can be used to provide a default layout
     * that will be inflated as part of <code>super.onCreate(savedInstanceState)</code>.
     *
     * <p>This should generally be called from your constructor that takes no parameters,
     * as is required for API 27 and lower or when using the default
     * {@link android.app.AppComponentFactory}.
     *
     * @see #FragmentActivity()
     */
    @ContentView
    public FragmentActivity(@LayoutRes int contentLayoutId) {
        super(contentLayoutId);
    }

    // ------------------------------------------------------------------------
    // HOOKS INTO ACTIVITY
    // ------------------------------------------------------------------------

    /**
     * Dispatch incoming result to the correct fragment.
     */
    @Override
    @CallSuper
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        mFragments.noteStateNotSaved();
        int requestIndex = requestCode>>16;
        if (requestIndex != 0) {
            requestIndex--;

            String who = mPendingFragmentActivityResults.get(requestIndex);
            mPendingFragmentActivityResults.remove(requestIndex);
            if (who == null) {
                Log.w(TAG, "Activity result delivered for unknown Fragment.");
                return;
            }
            Fragment targetFragment = mFragments.findFragmentByWho(who);
            if (targetFragment == null) {
                Log.w(TAG, "Activity result no fragment exists for who: " + who);
            } else {
                targetFragment.onActivityResult(requestCode & 0xffff, resultCode, data);
            }
            return;
        }
        ActivityCompat.PermissionCompatDelegate delegate =
                ActivityCompat.getPermissionCompatDelegate();
        if (delegate != null && delegate.onActivityResult(this, requestCode, resultCode, data)) {
            // Delegate has handled the activity result
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Reverses the Activity Scene entry Transition and triggers the calling Activity
     * to reverse its exit Transition. When the exit Transition completes,
     * {@link #finish()} is called. If no entry Transition was used, finish() is called
     * immediately and the Activity exit Transition is run.
     *
     * <p>On Android 4.4 or lower, this method only finishes the Activity with no
     * special exit transition.</p>
     */
    public void supportFinishAfterTransition() {
        ActivityCompat.finishAfterTransition(this);
    }

    /**
     * When {@link android.app.ActivityOptions#makeSceneTransitionAnimation(Activity,
     * android.view.View, String)} was used to start an Activity, <var>callback</var>
     * will be called to handle shared elements on the <i>launched</i> Activity. This requires
     * {@link Window#FEATURE_CONTENT_TRANSITIONS}.
     *
     * @param callback Used to manipulate shared element transitions on the launched Activity.
     */
    public void setEnterSharedElementCallback(@Nullable SharedElementCallback callback) {
        ActivityCompat.setEnterSharedElementCallback(this, callback);
    }

    /**
     * When {@link android.app.ActivityOptions#makeSceneTransitionAnimation(Activity,
     * android.view.View, String)} was used to start an Activity, <var>listener</var>
     * will be called to handle shared elements on the <i>launching</i> Activity. Most
     * calls will only come when returning from the started Activity.
     * This requires {@link Window#FEATURE_CONTENT_TRANSITIONS}.
     *
     * @param listener Used to manipulate shared element transitions on the launching Activity.
     */
    public void setExitSharedElementCallback(@Nullable SharedElementCallback listener) {
        ActivityCompat.setExitSharedElementCallback(this, listener);
    }

    /**
     * Support library version of {@link android.app.Activity#postponeEnterTransition()} that works
     * only on API 21 and later.
     */
    public void supportPostponeEnterTransition() {
        ActivityCompat.postponeEnterTransition(this);
    }

    /**
     * Support library version of {@link android.app.Activity#startPostponedEnterTransition()}
     * that only works with API 21 and later.
     */
    public void supportStartPostponedEnterTransition() {
        ActivityCompat.startPostponedEnterTransition(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Note:</strong> If you override this method you must call
     * <code>super.onMultiWindowModeChanged</code> to correctly dispatch the event
     * to support fragments attached to this activity.</p>
     *
     * @param isInMultiWindowMode True if the activity is in multi-window mode.
     */
    @Override
    @CallSuper
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        mFragments.dispatchMultiWindowModeChanged(isInMultiWindowMode);
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Note:</strong> If you override this method you must call
     * <code>super.onPictureInPictureModeChanged</code> to correctly dispatch the event
     * to support fragments attached to this activity.</p>
     *
     * @param isInPictureInPictureMode True if the activity is in picture-in-picture mode.
     */
    @Override
    @CallSuper
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        mFragments.dispatchPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    /**
     * Dispatch configuration change to all fragments.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mFragments.noteStateNotSaved();
        mFragments.dispatchConfigurationChanged(newConfig);
    }

    /**
     * Perform initialization of all fragments.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mFragments.attachHost(null /*parent*/);

        if (savedInstanceState != null) {
            Parcelable p = savedInstanceState.getParcelable(FRAGMENTS_TAG);
            mFragments.restoreSaveState(p);

            // Check if there are any pending onActivityResult calls to descendent Fragments.
            if (savedInstanceState.containsKey(NEXT_CANDIDATE_REQUEST_INDEX_TAG)) {
                mNextCandidateRequestIndex =
                        savedInstanceState.getInt(NEXT_CANDIDATE_REQUEST_INDEX_TAG);
                int[] requestCodes = savedInstanceState.getIntArray(ALLOCATED_REQUEST_INDICIES_TAG);
                String[] fragmentWhos = savedInstanceState.getStringArray(REQUEST_FRAGMENT_WHO_TAG);
                if (requestCodes == null || fragmentWhos == null ||
                            requestCodes.length != fragmentWhos.length) {
                    Log.w(TAG, "Invalid requestCode mapping in savedInstanceState.");
                } else {
                    mPendingFragmentActivityResults = new SparseArrayCompat<>(requestCodes.length);
                    for (int i = 0; i < requestCodes.length; i++) {
                        mPendingFragmentActivityResults.put(requestCodes[i], fragmentWhos[i]);
                    }
                }
            }
        }

        if (mPendingFragmentActivityResults == null) {
            mPendingFragmentActivityResults = new SparseArrayCompat<>();
            mNextCandidateRequestIndex = 0;
        }

        super.onCreate(savedInstanceState);

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mFragments.dispatchCreate();
    }

    /**
     * Dispatch to Fragment.onCreateOptionsMenu().
     */
    @Override
    public boolean onCreatePanelMenu(int featureId, @NonNull Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean show = super.onCreatePanelMenu(featureId, menu);
            show |= mFragments.dispatchCreateOptionsMenu(menu, getMenuInflater());
            return show;
        }
        return super.onCreatePanelMenu(featureId, menu);
    }

    @Override
    @Nullable
    public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context,
            @NonNull AttributeSet attrs) {
        final View v = dispatchFragmentsOnCreateView(parent, name, context, attrs);
        if (v == null) {
            return super.onCreateView(parent, name, context, attrs);
        }
        return v;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull String name, @NonNull Context context,
            @NonNull AttributeSet attrs) {
        final View v = dispatchFragmentsOnCreateView(null, name, context, attrs);
        if (v == null) {
            return super.onCreateView(name, context, attrs);
        }
        return v;
    }

    @Nullable
    final View dispatchFragmentsOnCreateView(@Nullable View parent, @NonNull String name,
            @NonNull Context context, @NonNull AttributeSet attrs) {
        return mFragments.onCreateView(parent, name, context, attrs);
    }

    /**
     * Destroy all fragments.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mFragments.dispatchDestroy();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
    }

    /**
     * Dispatch onLowMemory() to all fragments.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mFragments.dispatchLowMemory();
    }

    /**
     * Dispatch context and options menu to fragments.
     */
    @Override
    public boolean onMenuItemSelected(int featureId, @NonNull MenuItem item) {
        if (super.onMenuItemSelected(featureId, item)) {
            return true;
        }

        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                return mFragments.dispatchOptionsItemSelected(item);

            case Window.FEATURE_CONTEXT_MENU:
                return mFragments.dispatchContextItemSelected(item);

            default:
                return false;
        }
    }

    /**
     * Call onOptionsMenuClosed() on fragments.
     */
    @Override
    public void onPanelClosed(int featureId, @NonNull Menu menu) {
        switch (featureId) {
            case Window.FEATURE_OPTIONS_PANEL:
                mFragments.dispatchOptionsMenuClosed(menu);
                break;
        }
        super.onPanelClosed(featureId, menu);
    }

    /**
     * Dispatch onPause() to fragments.
     */
    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        mFragments.dispatchPause();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
    }

    /**
     * Handle onNewIntent() to inform the fragment manager that the
     * state is not saved.  If you are handling new intents and may be
     * making changes to the fragment state, you want to be sure to call
     * through to the super-class here first.  Otherwise, if your state
     * is saved but the activity is not stopped, you could get an
     * onNewIntent() call which happens before onResume() and trying to
     * perform fragment operations at that point will throw IllegalStateException
     * because the fragment manager thinks the state is still saved.
     */
    @Override
    @CallSuper
    protected void onNewIntent(@SuppressLint("UnknownNullness") Intent intent) {
        super.onNewIntent(intent);
        mFragments.noteStateNotSaved();
    }

    /**
     * Hook in to note that fragment state is no longer saved.
     */
    @Override
    public void onStateNotSaved() {
        mFragments.noteStateNotSaved();
    }

    /**
     * Dispatch onResume() to fragments.  Note that for better inter-operation
     * with older versions of the platform, at the point of this call the
     * fragments attached to the activity are <em>not</em> resumed.
     */
    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        mFragments.noteStateNotSaved();
        mFragments.execPendingActions();
    }

    /**
     * Dispatch onResume() to fragments.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        onResumeFragments();
    }

    /**
     * This is the fragment-orientated version of {@link #onResume()} that you
     * can override to perform operations in the Activity at the same point
     * where its fragments are resumed.  Be sure to always call through to
     * the super-class.
     */
    protected void onResumeFragments() {
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        mFragments.dispatchResume();
    }

    /**
     * Dispatch onPrepareOptionsMenu() to fragments.
     */
    @Override
    public boolean onPreparePanel(int featureId, @Nullable View view, @NonNull Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean goforit = onPrepareOptionsPanel(view, menu);
            goforit |= mFragments.dispatchPrepareOptionsMenu(menu);
            return goforit;
        }
        return super.onPreparePanel(featureId, view, menu);
    }

    /**
     * @hide
     * @deprecated Override {@link #onPreparePanel(int, View, Menu)}.
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    @Deprecated
    protected boolean onPrepareOptionsPanel(@Nullable View view, @NonNull Menu menu) {
        return super.onPreparePanel(Window.FEATURE_OPTIONS_PANEL, view, menu);
    }

    /**
     * Save all appropriate fragment state.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        markFragmentsCreated();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
        Parcelable p = mFragments.saveAllState();
        if (p != null) {
            outState.putParcelable(FRAGMENTS_TAG, p);
        }
        if (mPendingFragmentActivityResults.size() > 0) {
            outState.putInt(NEXT_CANDIDATE_REQUEST_INDEX_TAG, mNextCandidateRequestIndex);

            int[] requestCodes = new int[mPendingFragmentActivityResults.size()];
            String[] fragmentWhos = new String[mPendingFragmentActivityResults.size()];
            for (int i = 0; i < mPendingFragmentActivityResults.size(); i++) {
                requestCodes[i] = mPendingFragmentActivityResults.keyAt(i);
                fragmentWhos[i] = mPendingFragmentActivityResults.valueAt(i);
            }
            outState.putIntArray(ALLOCATED_REQUEST_INDICIES_TAG, requestCodes);
            outState.putStringArray(REQUEST_FRAGMENT_WHO_TAG, fragmentWhos);
        }
    }

    /**
     * Dispatch onStart() to all fragments.
     */
    @Override
    protected void onStart() {
        super.onStart();

        mStopped = false;

        if (!mCreated) {
            mCreated = true;
            mFragments.dispatchActivityCreated();
        }

        mFragments.noteStateNotSaved();
        mFragments.execPendingActions();

        // NOTE: HC onStart goes here.

        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
        mFragments.dispatchStart();
    }

    /**
     * Dispatch onStop() to all fragments.
     */
    @Override
    protected void onStop() {
        super.onStop();

        mStopped = true;
        markFragmentsCreated();

        mFragments.dispatchStop();
        mFragmentLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
    }

    // ------------------------------------------------------------------------
    // NEW METHODS
    // ------------------------------------------------------------------------

    /**
     * Support library version of {@link Activity#invalidateOptionsMenu}.
     *
     * <p>Invalidate the activity's options menu. This will cause relevant presentations
     * of the menu to fully update via calls to onCreateOptionsMenu and
     * onPrepareOptionsMenu the next time the menu is requested.
     *
     * @deprecated Call {@link Activity#invalidateOptionsMenu} directly.
     */
    @Deprecated
    public void supportInvalidateOptionsMenu() {
        invalidateOptionsMenu();
    }

    /**
     * Print the Activity's state into the given stream.  This gets invoked if
     * you run "adb shell dumpsys activity <activity_component_name>".
     *
     * @param prefix Desired prefix to prepend at each line of output.
     * @param fd The raw file descriptor that the dump is being sent to.
     * @param writer The PrintWriter to which you should dump your state.  This will be
     * closed for you after you return.
     * @param args additional arguments to the dump request.
     */
    @Override
    public void dump(@NonNull String prefix, @Nullable FileDescriptor fd,
            @NonNull PrintWriter writer, @Nullable String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.print(prefix); writer.print("Local FragmentActivity ");
                writer.print(Integer.toHexString(System.identityHashCode(this)));
                writer.println(" State:");
        String innerPrefix = prefix + "  ";
        writer.print(innerPrefix); writer.print("mCreated=");
                writer.print(mCreated); writer.print(" mResumed=");
                writer.print(mResumed); writer.print(" mStopped=");
                writer.print(mStopped);

        if (getApplication() != null) {
            LoaderManager.getInstance(this).dump(innerPrefix, fd, writer, args);
        }
        mFragments.getSupportFragmentManager().dump(prefix, fd, writer, args);
    }

    // ------------------------------------------------------------------------
    // FRAGMENT SUPPORT
    // ------------------------------------------------------------------------

    /**
     * Called when a fragment is attached to the activity.
     *
     * <p>This is called after the attached fragment's <code>onAttach</code> and before
     * the attached fragment's <code>onCreate</code> if the fragment has not yet had a previous
     * call to <code>onCreate</code>.</p>
     */
    @SuppressWarnings("unused")
    public void onAttachFragment(@NonNull Fragment fragment) {
    }

    /**
     * Return the FragmentManager for interacting with fragments associated
     * with this activity.
     */
    @NonNull
    public FragmentManager getSupportFragmentManager() {
        return mFragments.getSupportFragmentManager();
    }

    /**
     * @deprecated Use
     * {@link LoaderManager#getInstance(LifecycleOwner) LoaderManager.getInstance(this)}.
     */
    @Deprecated
    @NonNull
    public LoaderManager getSupportLoaderManager() {
        return LoaderManager.getInstance(this);
    }

    /**
     * Modifies the standard behavior to allow results to be delivered to fragments.
     * This imposes a restriction that requestCode be <= 0xffff.
     */
    @Override
    public void startActivityForResult(@SuppressLint("UnknownNullness") Intent intent,
            int requestCode) {
        // If this was started from a Fragment we've already checked the upper 16 bits were not in
        // use, and then repurposed them for the Fragment's index.
        if (!mStartedActivityFromFragment) {
            if (requestCode != -1) {
                checkForValidRequestCode(requestCode);
            }
        }
        // MOD: swallow activity not found in Editor
        try {
            super.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            // ActivityNotFoundException: No Activity found to handle Intent { act=com.android.settings.USER_DICTIONARY_INSERT flg=0x10000000 (has extras) }
            Log.e(TAG, e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void startActivityForResult(@SuppressLint("UnknownNullness") Intent intent,
            int requestCode, @Nullable Bundle options) {
        // If this was started from a Fragment we've already checked the upper 16 bits were not in
        // use, and then repurposed them for the Fragment's index.
        if (!mStartedActivityFromFragment) {
            if (requestCode != -1) {
                checkForValidRequestCode(requestCode);
            }
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override
    public void startIntentSenderForResult(@SuppressLint("UnknownNullness") IntentSender intent,
            int requestCode, @Nullable Intent fillInIntent, int flagsMask,
            int flagsValues, int extraFlags) throws IntentSender.SendIntentException {
        // If this was started from a Fragment we've already checked the upper 16 bits were not in
        // use, and then repurposed them for the Fragment's index.
        if (!mStartedIntentSenderFromFragment) {
            if (requestCode != -1) {
                checkForValidRequestCode(requestCode);
            }
        }
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues,
                extraFlags);
    }

    @Override
    public void startIntentSenderForResult(@SuppressLint("UnknownNullness") IntentSender intent,
            int requestCode, @Nullable Intent fillInIntent, int flagsMask, int flagsValues,
            int extraFlags, @Nullable Bundle options) throws IntentSender.SendIntentException {
        // If this was started from a Fragment we've already checked the upper 16 bits were not in
        // use, and then repurposed them for the Fragment's index.
        if (!mStartedIntentSenderFromFragment) {
            if (requestCode != -1) {
                checkForValidRequestCode(requestCode);
            }
        }
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues,
                extraFlags, options);
    }

    /**
     * Checks whether the given request code is a valid code by masking it with 0xffff0000. Throws
     * an {@link IllegalArgumentException} if the code is not valid.
     */
    static void checkForValidRequestCode(int requestCode) {
        if ((requestCode & 0xffff0000) != 0) {
            throw new IllegalArgumentException("Can only use lower 16 bits for requestCode");
        }
    }

    @Override
    public final void validateRequestPermissionsRequestCode(int requestCode) {
        // We use 16 bits of the request code to encode the fragment id when
        // requesting permissions from a fragment. Hence, requestPermissions()
        // should validate the code against that but we cannot override it as
        // we can not then call super and also the ActivityCompat would call
        // back to this override. To handle this we use dependency inversion
        // where we are the validator of request codes when requesting
        // permissions in ActivityCompat.
        if (!mRequestedPermissionsFromFragment
                && requestCode != -1) {
            checkForValidRequestCode(requestCode);
        }
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
     *
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        mFragments.noteStateNotSaved();
        int index = (requestCode >> 16) & 0xffff;
        if (index != 0) {
            index--;

            String who = mPendingFragmentActivityResults.get(index);
            mPendingFragmentActivityResults.remove(index);
            if (who == null) {
                Log.w(TAG, "Activity result delivered for unknown Fragment.");
                return;
            }
            Fragment frag = mFragments.findFragmentByWho(who);
            if (frag == null) {
                Log.w(TAG, "Activity result no fragment exists for who: " + who);
            } else {
                frag.onRequestPermissionsResult(requestCode & 0xffff, permissions, grantResults);
            }
        }
    }

    /**
     * Called by Fragment.startActivityForResult() to implement its behavior.
     */
    public void startActivityFromFragment(@NonNull Fragment fragment,
            @SuppressLint("UnknownNullness") Intent intent, int requestCode) {
        startActivityFromFragment(fragment, intent, requestCode, null);
    }

    /**
     * Called by Fragment.startActivityForResult() to implement its behavior.
     */
    public void startActivityFromFragment(@NonNull Fragment fragment,
            @SuppressLint("UnknownNullness") Intent intent, int requestCode,
            @Nullable Bundle options) {
        mStartedActivityFromFragment = true;
        try {
            if (requestCode == -1) {
                ActivityCompat.startActivityForResult(this, intent, -1, options);
                return;
            }
            checkForValidRequestCode(requestCode);
            int requestIndex = allocateRequestIndex(fragment);
            ActivityCompat.startActivityForResult(
                    this, intent, ((requestIndex + 1) << 16) + (requestCode & 0xffff), options);
        } finally {
            mStartedActivityFromFragment = false;
        }
    }

    /**
     * Called by Fragment.startIntentSenderForResult() to implement its behavior.
     */
    public void startIntentSenderFromFragment(@NonNull Fragment fragment,
            @SuppressLint("UnknownNullness") IntentSender intent, int requestCode,
            @Nullable Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags,
            @Nullable Bundle options) throws IntentSender.SendIntentException {
        mStartedIntentSenderFromFragment = true;
        try {
            if (requestCode == -1) {
                ActivityCompat.startIntentSenderForResult(this, intent, requestCode, fillInIntent,
                        flagsMask, flagsValues, extraFlags, options);
                return;
            }
            checkForValidRequestCode(requestCode);
            int requestIndex = allocateRequestIndex(fragment);
            ActivityCompat.startIntentSenderForResult(this, intent,
                    ((requestIndex + 1) << 16) + (requestCode & 0xffff), fillInIntent,
                    flagsMask, flagsValues, extraFlags, options);
        } finally {
            mStartedIntentSenderFromFragment = false;
        }
    }

    // Allocates the next available startActivityForResult request index.
    private int allocateRequestIndex(@NonNull Fragment fragment) {
        // Sanity check that we havn't exhaused the request index space.
        if (mPendingFragmentActivityResults.size() >= MAX_NUM_PENDING_FRAGMENT_ACTIVITY_RESULTS) {
            throw new IllegalStateException("Too many pending Fragment activity results.");
        }

        // Find an unallocated request index in the mPendingFragmentActivityResults map.
        while (mPendingFragmentActivityResults.indexOfKey(mNextCandidateRequestIndex) >= 0) {
            mNextCandidateRequestIndex =
                    (mNextCandidateRequestIndex + 1) % MAX_NUM_PENDING_FRAGMENT_ACTIVITY_RESULTS;
        }

        int requestIndex = mNextCandidateRequestIndex;
        mPendingFragmentActivityResults.put(requestIndex, fragment.mWho);
        mNextCandidateRequestIndex =
                (mNextCandidateRequestIndex + 1) % MAX_NUM_PENDING_FRAGMENT_ACTIVITY_RESULTS;

        return requestIndex;
    }

    /**
     * Called by Fragment.requestPermissions() to implement its behavior.
     */
    void requestPermissionsFromFragment(@NonNull Fragment fragment, @NonNull String[] permissions,
            int requestCode) {
        if (requestCode == -1) {
            ActivityCompat.requestPermissions(this, permissions, requestCode);
            return;
        }
        checkForValidRequestCode(requestCode);
        try {
            mRequestedPermissionsFromFragment = true;
            int requestIndex = allocateRequestIndex(fragment);
            ActivityCompat.requestPermissions(this, permissions,
                    ((requestIndex + 1) << 16) + (requestCode & 0xffff));
        } finally {
            mRequestedPermissionsFromFragment = false;
        }
    }

    class HostCallbacks extends FragmentHostCallback<FragmentActivity> implements
            ViewModelStoreOwner,
            OnBackPressedDispatcherOwner {
        public HostCallbacks() {
            super(FragmentActivity.this /*fragmentActivity*/);
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            // Instead of directly using the Activity's Lifecycle, we
            // use a LifecycleRegistry that is nested exactly outside of
            // when Fragments get their lifecycle changed
            // TODO(b/127528777) Drive Fragment Lifecycle with LifecycleObserver
            return mFragmentLifecycleRegistry;
        }

        @NonNull
        @Override
        public ViewModelStore getViewModelStore() {
            return FragmentActivity.this.getViewModelStore();
        }

        @NonNull
        @Override
        public OnBackPressedDispatcher getOnBackPressedDispatcher() {
            return FragmentActivity.this.getOnBackPressedDispatcher();
        }

        @Override
        public void onDump(@NonNull String prefix, @Nullable FileDescriptor fd,
                @NonNull PrintWriter writer, @Nullable String[] args) {
            FragmentActivity.this.dump(prefix, fd, writer, args);
        }

        @Override
        public boolean onShouldSaveFragmentState(@NonNull Fragment fragment) {
            return !isFinishing();
        }

        @Override
        @NonNull
        public LayoutInflater onGetLayoutInflater() {
            return FragmentActivity.this.getLayoutInflater().cloneInContext(FragmentActivity.this);
        }

        @Override
        public FragmentActivity onGetHost() {
            return FragmentActivity.this;
        }

        @Override
        public void onSupportInvalidateOptionsMenu() {
            FragmentActivity.this.supportInvalidateOptionsMenu();
        }

        @Override
        public void onStartActivityFromFragment(@NonNull Fragment fragment, Intent intent,
                int requestCode) {
            FragmentActivity.this.startActivityFromFragment(fragment, intent, requestCode);
        }

        @Override
        public void onStartActivityFromFragment(@NonNull Fragment fragment, Intent intent,
                int requestCode, @Nullable Bundle options) {
            FragmentActivity.this.startActivityFromFragment(fragment, intent, requestCode, options);
        }

        @Override
        public void onStartIntentSenderFromFragment(
                @NonNull Fragment fragment, IntentSender intent, int requestCode,
                @Nullable Intent fillInIntent, int flagsMask, int flagsValues,
                int extraFlags, Bundle options) throws IntentSender.SendIntentException {
            FragmentActivity.this.startIntentSenderFromFragment(fragment, intent, requestCode,
                    fillInIntent, flagsMask, flagsValues, extraFlags, options);
        }

        @Override
        public void onRequestPermissionsFromFragment(@NonNull Fragment fragment,
                @NonNull String[] permissions, int requestCode) {
            FragmentActivity.this.requestPermissionsFromFragment(fragment, permissions,
                    requestCode);
        }

        @Override
        public boolean onShouldShowRequestPermissionRationale(@NonNull String permission) {
            return ActivityCompat.shouldShowRequestPermissionRationale(
                    FragmentActivity.this, permission);
        }

        @Override
        public boolean onHasWindowAnimations() {
            return getWindow() != null;
        }

        @Override
        public int onGetWindowAnimations() {
            final Window w = getWindow();
            return (w == null) ? 0 : w.getAttributes().windowAnimations;
        }

        @Override
        public void onAttachFragment(@NonNull Fragment fragment) {
            FragmentActivity.this.onAttachFragment(fragment);
        }

        @Nullable
        @Override
        public View onFindViewById(int id) {
            return FragmentActivity.this.findViewById(id);
        }

        @Override
        public boolean onHasView() {
            final Window w = getWindow();
            return (w != null && w.peekDecorView() != null);
        }
    }

    private void markFragmentsCreated() {
        boolean reiterate;
        do {
            reiterate = markState(getSupportFragmentManager(), Lifecycle.State.CREATED);
        } while (reiterate);
    }

    private static boolean markState(FragmentManager manager, Lifecycle.State state) {
        boolean hadNotMarked = false;
        Collection<Fragment> fragments = manager.getFragments();
        for (Fragment fragment : fragments) {
            if (fragment == null) {
                continue;
            }
            if (fragment.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                fragment.mLifecycleRegistry.setCurrentState(state);
                hadNotMarked = true;
            }

            if (fragment.getHost() != null) {
                FragmentManager childFragmentManager = fragment.getChildFragmentManager();
                hadNotMarked |= markState(childFragmentManager, state);
            }
        }
        return hadNotMarked;
    }
}
