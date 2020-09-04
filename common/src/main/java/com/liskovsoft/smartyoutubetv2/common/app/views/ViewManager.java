package com.liskovsoft.smartyoutubetv2.common.app.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.HashMap;
import java.util.Map;

public class ViewManager {
    private static final String TAG = ViewManager.class.getSimpleName();
    private static ViewManager sInstance;
    private final Context mContext;
    private final Map<Class<?>, Class<? extends Activity>> mViewMapping;

    private ViewManager(Context context) {
        mContext = context;
        mViewMapping = new HashMap<>();
    }

    public static ViewManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new ViewManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public void register(Class<?> viewClass, Class<? extends Activity> activityClass) {
        mViewMapping.put(viewClass, activityClass);
    }

    public void unregister(Class<?> viewClass) {
        mViewMapping.remove(viewClass);
    }
    
    public void startView(Class<?> viewClass) {
        Class<?> activityClass = mViewMapping.get(viewClass);

        if (activityClass != null) {
            //Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
            //        getActivity(),
            //        ((ImageCardView) itemViewHolder.view).getMainImageView(),
            //        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
            //getActivity().startActivity(intent, bundle);

            Intent intent = new Intent(mContext, activityClass);

            // Fix: Calling startActivity() from outside of an Activity  context requires the FLAG_ACTIVITY_NEW_TASK flag
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            mContext.startActivity(intent);
        } else {
            Log.e(TAG, "Activity not registered for view " + viewClass.getSimpleName());
        }
    }

    public void startView(Object initiator, Class<?> viewClass) {
        if (initiator instanceof Fragment) {
            Fragment fragment = (Fragment) initiator;

            Class<?> activityClass = mViewMapping.get(viewClass);

            if (activityClass != null) {
                //Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                //        getActivity(),
                //        ((ImageCardView) itemViewHolder.view).getMainImageView(),
                //        VideoDetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                //getActivity().startActivity(intent, bundle);

                Intent intent = new Intent(fragment.getActivity(), activityClass);
                fragment.startActivity(intent);
            } else {
                Log.e(TAG, "Activity not registered for view " + viewClass.getSimpleName());
            }
        }
    }
}
