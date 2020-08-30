package com.liskovsoft.smartyoutubetv2.common.mvp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.mvp.views.PlaybackView;

import java.util.Map;

public class ViewManager {
    private static final String TAG = ViewManager.class.getSimpleName();
    private static ViewManager sInstance;
    private final Context mContext;
    private Map<Class<?>, Class<? extends Activity>> mViewMapping;

    private ViewManager(Context context) {
        mContext = context;
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
            mContext.startActivity(intent);
        } else {
            Log.e(TAG, "Activity not registered for view " + viewClass.getSimpleName());
        }
    }
}
