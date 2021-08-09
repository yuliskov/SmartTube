package com.liskovsoft.smartyoutubetv2.common.app.presenters.base;

import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.lang.ref.WeakReference;

public abstract class BasePresenter<T> implements Presenter<T> {
    private WeakReference<T> mView = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private WeakReference<Context> mApplicationContext = new WeakReference<>(null);
    private static boolean sIsGlobalDataInitialized;
    private static boolean sIsGlobalDataInitializedTmp;

    public BasePresenter(Context context) {
        setContext(context);
    }

    @Override
    public void setView(T view) {
        if (view != null) {
            mView = new WeakReference<>(view);
        }
    }

    @Override
    public T getView() {
        return mView.get();
    }

    @Override
    public void setContext(Context context) {
        if (context == null) {
            return;
        }

        // Localization fix: prefer Activity context
        if (context instanceof Activity) {
            mActivity = new WeakReference<>((Activity) context);
        }

        // In case view was disposed like SplashView does
        mApplicationContext = new WeakReference<>(context.getApplicationContext());

        //initGlobalData();
    }

    @Override
    public Context getContext() {
        Activity activity = null;

        // First, try to get localized contexts (regular and View contexts)
        if (mActivity.get() != null) {
            activity = mActivity.get();
        } else if (mView.get() instanceof Fragment) {
            activity = ((Fragment) mView.get()).getActivity();
        }

        // In case view was disposed like SplashView does
        // Fallback to non-localized ApplicationContext if others fail
        return Utils.checkActivity(activity) ? activity : mApplicationContext.get();
    }

    @Override
    public void onViewInitialized() {
        // NOP
    }

    @Override
    public void onViewDestroyed() {
        // View stays in RAM after has been destroyed. Is it a bug?
        mView = new WeakReference<>(null);
    }

    @Override
    public void onViewResumed() {
        // NOP
    }

    //private void initGlobalData() {
    //    if (sIsGlobalDataInitialized || getContext() == null) {
    //        return;
    //    }
    //
    //    boolean isActivity = getContext() instanceof Activity;
    //
    //    if (isActivity || !sIsGlobalDataInitializedTmp) {
    //        Utils.initGlobalData(getContext());
    //        sIsGlobalDataInitialized = isActivity;
    //        sIsGlobalDataInitializedTmp = !isActivity;
    //    }
    //}
}
