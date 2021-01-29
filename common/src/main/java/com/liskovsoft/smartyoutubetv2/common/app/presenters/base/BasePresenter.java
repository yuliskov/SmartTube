package com.liskovsoft.smartyoutubetv2.common.app.presenters.base;

import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;

import java.lang.ref.WeakReference;

public abstract class BasePresenter<T> implements Presenter<T> {
    private WeakReference<T> mView = new WeakReference<>(null);
    private WeakReference<Context> mContext = new WeakReference<>(null);
    private WeakReference<Context> mApplicationContext = new WeakReference<>(null);

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
            mContext = new WeakReference<>(context);
        }

        // In case view was disposed like SplashView does
        mApplicationContext = new WeakReference<>(context.getApplicationContext());
    }

    @Override
    public Context getContext() {
        Context context;

        // First, try to get localized contexts (regular and View contexts)
        // Fallback to non-localized ApplicationContext if others fail
        if (mContext.get() != null) {
            context = mContext.get();
        } else if (mView.get() instanceof Fragment) {
            context = ((Fragment) mView.get()).getContext();
        } else {
            // In case view was disposed like SplashView does
            context = mApplicationContext.get();
        }

        return context;
    }

    @Override
    public void onViewInitialized() {
        // NOP
    }

    @Override
    public void onViewDestroyed() {
        // NOP
    }

    @Override
    public void onViewResumed() {
        // NOP
    }
}
