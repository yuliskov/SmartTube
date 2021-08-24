package com.liskovsoft.smartyoutubetv2.common.app.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class ViewManager {
    private static final String TAG = ViewManager.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ViewManager sInstance;
    private final Context mContext;
    private final Map<Class<?>, Class<? extends Activity>> mViewMapping;
    private final Map<Class<? extends Activity>, Class<? extends Activity>> mParentMapping;
    private final Stack<Class<?>> mActivityStack;
    private final AppPrefs mPrefs;
    private Class<?> mRootActivity;
    private Class<?> mDefaultTop;
    private long mPrevThrottleTimeMS;
    private boolean mIsMoveToBackEnabled;
    private boolean mIsFinishing;
    private boolean mIsSinglePlayerMode;
    private long mStartActivityMs;

    private ViewManager(Context context) {
        mContext = context;
        mViewMapping = new HashMap<>();
        mParentMapping = new HashMap<>();
        mActivityStack = new Stack<>();
        mPrefs = AppPrefs.instance(context);
    }

    public static ViewManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new ViewManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public void register(Class<?> viewClass, Class<? extends Activity> activityClass) {
        register(viewClass, activityClass, null);
    }

    public void register(Class<?> viewClass, Class<? extends Activity> activityClass, Class<? extends Activity> parentActivityClass) {
        mViewMapping.put(viewClass, activityClass);

        if (parentActivityClass != null) {
            mParentMapping.put(activityClass, parentActivityClass);
        }
    }

    public void unregister(Class<?> viewClass) {
        mViewMapping.remove(viewClass);
    }

    /**
     * Use carefully<br/>
     * On running activity, this method invokes standard activity lifecycle: pause, resume etc...
     */
    public void startView(Class<?> viewClass) {
        startView(viewClass, false);
    }

    public void startView(Class<?> viewClass, boolean forceStart) {
        mIsMoveToBackEnabled = false; // Essential part or new view will be pause immediately

        //if (!forceStart && doThrottle()) {
        //    Log.d(TAG, "Too many events. Skipping startView...");
        //    return;
        //}

        Class<?> activityClass = mViewMapping.get(viewClass);

        if (activityClass != null) {
            startActivity(activityClass);
        } else {
            Log.e(TAG, "Activity not registered for view " + viewClass.getSimpleName());
        }
    }

    public boolean startParentView(Activity activity) {
        if (activity.getIntent() != null) {
            removeTopActivity();

            Class<?> parentActivity = getTopActivity();

            if (parentActivity == null && !mIsSinglePlayerMode) {
                parentActivity = getDefaultParent(activity);
            }

            if (parentActivity == null) {
                Log.d(TAG, "Parent activity name doesn't stored in registry. Exiting to Home...");

                mIsMoveToBackEnabled = true;

                if (mIsSinglePlayerMode) {
                    safeMoveTaskToBack(activity);
                    return true;
                }

                return false;
            }

            try {
                Log.d(TAG, "Launching parent activity: " + parentActivity.getSimpleName());
                Intent intent = new Intent(activity, parentActivity);

                safeStartActivity(activity, intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "Parent activity not found.");
            }
        }

        return true;
    }

    public void startDefaultView() {
        mIsMoveToBackEnabled = false;
        mIsSinglePlayerMode = false;

        Class<?> lastActivity;

        if (mDefaultTop != null) {
            lastActivity = mDefaultTop;
        } else if (!mActivityStack.isEmpty()) {
            lastActivity = mActivityStack.peek();
        } else {
            lastActivity = mRootActivity;
        }

        Log.d(TAG, "Launching default activity: " + lastActivity.getSimpleName());

        startActivity(lastActivity);
    }

    private void startActivity(Class<?> activityClass) {
        Log.d(TAG, "Launching activity: " + activityClass.getSimpleName());

        mStartActivityMs = System.currentTimeMillis();

        Intent intent = new Intent(mContext, activityClass);

        // Fix: Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        safeStartActivity(mContext, intent);
    }

    private boolean doThrottle() {
        long currentTimeMS = System.currentTimeMillis();
        boolean skipEvent = currentTimeMS - mPrevThrottleTimeMS < 1_000;

        mPrevThrottleTimeMS = currentTimeMS;

        return skipEvent;
    }

    public void addTop(Activity activity) {
        if (checkMoveViewsToBack(activity)) {
            // NOTE: Unknown purpose of commented code!

            // Maybe finish whole app?
            // Move task to back is active.
            // So finishing the activity only.
            //((MotherActivity) activity).finishReally();
            return;
        }

        Class<?> activityClass = activity.getClass();

        // Open from phone's history fix. Not parent? Make the root then.
        if (mParentMapping.get(activityClass) == null) {
            mActivityStack.clear();
        }

        // reorder activity
        mActivityStack.remove(activityClass);
        mActivityStack.push(activityClass);
    }

    private void removeTopActivity() {
        if (!mActivityStack.isEmpty()) {
            mActivityStack.pop();
        }
    }

    private Class<?> getTopActivity() {
        Class<?> result = null;

        if (!mActivityStack.isEmpty()) {
            result = mActivityStack.peek();
        }

        return result;
    }

    public void setRoot(@NonNull Class<?> rootActivity) {
        mRootActivity = rootActivity;
    }

    private Class<?> getDefaultParent(Activity activity) {
        Class<?> parentActivity = null;

        for (Class<?> activityClass : mParentMapping.keySet()) {
            if (activityClass.isInstance(activity)) {
                parentActivity = mParentMapping.get(activityClass);
            }
        }

        return parentActivity;
    }

    public void blockTop(Activity activity) {
        mDefaultTop = activity == null ? null : activity.getClass();
    }

    public void removeTop(Activity activity) {
        mActivityStack.remove(activity.getClass());
    }

    private boolean checkMoveViewsToBack(Activity activity) {
        if (mIsMoveToBackEnabled) {
            safeMoveTaskToBack(activity);

            return true;
        }

        return false;
    }

    public void setSinglePlayerMode(boolean enable) {
        mActivityStack.clear();
        mIsSinglePlayerMode = enable;
    }

    public void clearCaches() {
        YouTubeMediaService.instance().invalidateCache();
        FileHelpers.deleteCache(mContext);
    }

    //public void restartApp() {
    //    //startView(SplashView.class);
    //    //
    //    //mMoveViewsToBack = true;
    //    //
    //    //persistState();
    //    //
    //    //System.exit(0);
    //
    //    mMoveViewsToBack = false;
    //
    //    triggerRebirth3(mContext, mViewMapping.get(SplashView.class));
    //}

    /**
     * More info: https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
     */
    private static void triggerRebirth(Context context, Class<?> rootActivity) {
        Intent intent = new Intent(context, rootActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        if (context instanceof MotherActivity) {
            ((MotherActivity) context).finishReally();
        }
        Runtime.getRuntime().exit(0);
    }

    /**
     * More info: https://stackoverflow.com/questions/6609414/how-do-i-programmatically-restart-an-android-app
     */
    private static void triggerRebirth2(Context context, Class<?> rootActivity) {
        Intent mStartActivity = new Intent(context, rootActivity);
        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(context, mPendingIntentId,    mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }

    public static void triggerRebirth3(Context context, Class<?> myClass) {
        Intent intent = new Intent(context, myClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        Runtime.getRuntime().exit(0);
    }

    private void exitToHome() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        safeStartActivity(mContext, intent);
    }

    /**
     * Only moves tasks to back.<br/>
     * Main magic happened in {@link MotherActivity}
     * @param activity this activity
     */
    public void properlyFinishTheApp(Context activity) {
        if (activity instanceof MotherActivity) {
            Log.d(TAG, "Trying finish the app...");
            mIsMoveToBackEnabled = true;
            mIsFinishing = true;

            ((MotherActivity) activity).finishReally();
        }
    }

    public void forceFinishTheApp() {
        SplashPresenter.instance(mContext).unhold();
        clearCaches();

        // We need to destroy the app only if settings are changed
        if (GeneralData.instance(mContext).isSettingsCategoryEnabled()) {
            destroyApp();
        }
    }

    private static void destroyApp() {
        Runtime.getRuntime().exit(0);
    }

    public Class<?> getTopView() {
        Class<?> topActivity = getTopActivity();

        if (topActivity == null) {
            return null;
        }

        for (Map.Entry<Class<?>, Class<? extends Activity>> entry: mViewMapping.entrySet()) {
            if (entry.getValue() == topActivity) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Fix: java.lang.NullPointerException Attempt to read from field<br/>
     * 'com.android.server.am.TaskRecord com.android.server.am.ActivityRecord.task'<br/>
     * on a null object reference<br/>
     * <br/>
     * Fix: java.lang.IllegalArgumentException<br/>
     * View=android.widget.TextView not attached to window manager
     */
    private void safeMoveTaskToBack(Activity activity) {
        try {
            activity.moveTaskToBack(true);
        } catch (NullPointerException | IllegalArgumentException e) {
            Log.e(TAG, "Error when moving task to back: %s", e.getMessage());
        }
    }

    /**
     * Fix: java.lang.IllegalArgumentException<br/>
     * View=android.widget.TextView not attached to window manager
     */
    private void safeStartActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error when starting activity: %s", e.getMessage());
        }
    }

    public boolean isFinishing() {
        return mIsFinishing;
    }

    public void enableMoveToBack(boolean enable) {
        mIsMoveToBackEnabled = enable;
    }

    public boolean isNewViewPending() {
        return System.currentTimeMillis() - mStartActivityMs < 1_000;
    }
}
