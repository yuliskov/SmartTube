package com.liskovsoft.smartyoutubetv2.common.app.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;

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
    private Class<?> mRootActivity;
    private Class<?> mDefaultTop;
    private long mPrevThrottleTimeMS;
    private boolean mMoveViewsToBack;
    private boolean mIsSinglePlayerMode;

    private ViewManager(Context context) {
        mContext = context;
        mViewMapping = new HashMap<>();
        mParentMapping = new HashMap<>();
        mActivityStack = new Stack<>();
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
    
    public void startView(Class<?> viewClass) {
        mMoveViewsToBack = false; // Essential part or new view will be pause immediately

        if (doThrottle()) {
            return;
        }

        Class<?> activityClass = mViewMapping.get(viewClass);

        if (activityClass != null) {
            Intent intent = new Intent(mContext, activityClass);

            // Fix: Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Log.d(TAG, "Launching activity view: " + activityClass.getSimpleName());
            mContext.startActivity(intent);
        } else {
            Log.e(TAG, "Activity not registered for view " + viewClass.getSimpleName());
        }
    }

    public boolean startParentView(Activity activity) {
        if (doThrottle()) {
            return true;
        }

        if (activity.getIntent() != null) {
            removeTopActivity();

            Class<?> parentActivity = getTopActivity();

            if (parentActivity == null && !mIsSinglePlayerMode) {
                parentActivity = getDefaultParent(activity);
            }

            if (parentActivity == null) {
                Log.d(TAG, "Parent activity name doesn't stored in registry. Exiting to Home...");

                mMoveViewsToBack = true;

                if (mIsSinglePlayerMode) {
                    activity.moveTaskToBack(true);
                    return true;
                }

                clearCache();

                return false;
            }

            try {
                Log.d(TAG, "Launching parent activity: " + parentActivity.getSimpleName());
                Intent intent = new Intent(activity, parentActivity);

                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "Parent activity not found.");
            }
        }

        return true;
    }

    public void startDefaultView() {
        mMoveViewsToBack = false;
        mIsSinglePlayerMode = false;

        if (doThrottle()) {
            return;
        }

        Class<?> lastActivity;

        if (mDefaultTop != null) {
            lastActivity = mDefaultTop;
        } else if (!mActivityStack.isEmpty()) {
            lastActivity = mActivityStack.peek();
        } else {
            lastActivity = mRootActivity;
        }

        Log.d(TAG, "Launching default activity: " + lastActivity.getSimpleName());

        Intent intent = new Intent(mContext, lastActivity);

        // Fix: Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(intent);
    }

    private boolean doThrottle() {
        long currentTimeMS = System.currentTimeMillis();
        boolean skipEvent = currentTimeMS - mPrevThrottleTimeMS < 1_000;

        mPrevThrottleTimeMS = currentTimeMS;

        return skipEvent;
    }

    public boolean addTop(Activity activity) {
        if (checkMoveViewsToBack(activity)) {
            return false;
        }

        Class<?> activityClass = activity.getClass();

        // reorder activity
        mActivityStack.remove(activityClass);
        mActivityStack.push(activityClass);

        return true;
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

    private boolean checkMoveViewsToBack(Activity activity) {
        if (mMoveViewsToBack) {
            activity.moveTaskToBack(true);
            return true;
        }

        return false;
    }

    public void setSinglePlayerMode(boolean enable) {
        mActivityStack.clear();
        mIsSinglePlayerMode = enable;
    }

    private void clearCache() {
        FileHelpers.deleteCache(mContext);
    }
}
