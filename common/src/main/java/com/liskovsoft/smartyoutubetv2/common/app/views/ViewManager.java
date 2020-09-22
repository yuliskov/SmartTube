package com.liskovsoft.smartyoutubetv2.common.app.views;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class ViewManager {
    private static final String TAG = ViewManager.class.getSimpleName();
    private static ViewManager sInstance;
    private final Context mContext;
    private final Map<Class<?>, Class<? extends Activity>> mViewMapping;
    private final Map<Class<? extends Activity>, Class<? extends Activity>> mParentMapping;
    private final Stack<Class<?>> mActivityStack;
    private Class<?> mRootActivity;
    private Class<?> mDefaultTop;

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
        Class<?> activityClass = mViewMapping.get(viewClass);

        if (activityClass != null) {
            Intent intent = new Intent(mContext, activityClass);

            // Fix: Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            mContext.startActivity(intent);
        } else {
            Log.e(TAG, "Activity not registered for view " + viewClass.getSimpleName());
        }
    }

    public void startParentView(Activity activity) {
        if (activity.getIntent() != null) {
            removeTopActivity();

            Class<?> parentActivity = getTopActivity();

            if (parentActivity == null) {
                parentActivity = getDefaultParent(activity);
            }

            if (parentActivity == null) {
                Log.d(TAG, "Parent activity name doesn't stored in registry. Exiting to Home...");
                activity.moveTaskToBack(true);
                return;
            }

            try {
                Log.d(TAG, "Launching parent activity...");
                Intent intent = new Intent(activity, parentActivity);

                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
                Log.e(TAG, "Parent activity not found.");
            }
        }
    }

    public void startDefaultView(Context context) {
        Class<?> lastActivity;

        if (mDefaultTop != null) {
            lastActivity = mDefaultTop;
        } else if (!mActivityStack.isEmpty()) {
            lastActivity = mActivityStack.peek();
        } else {
            lastActivity = mRootActivity;
        }

        Log.d(TAG, "Starting activity: " + lastActivity.getSimpleName());

        Intent intent = new Intent(context, lastActivity);

        context.startActivity(intent);
    }

    public void addTopActivity(@Nullable Class<?> activity) {
        if (!mActivityStack.isEmpty() && mActivityStack.peek() == activity) {
            return;
        }

        mActivityStack.push(activity);
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

    public void blockTop(boolean block) {
        mDefaultTop = block ? getTopActivity() : null;
    }
}
