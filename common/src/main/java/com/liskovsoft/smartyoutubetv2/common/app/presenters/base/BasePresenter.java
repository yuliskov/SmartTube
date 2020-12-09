package com.liskovsoft.smartyoutubetv2.common.app.presenters.base;

import android.content.Context;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;

import java.lang.ref.WeakReference;

public abstract class BasePresenter<T> implements Presenter<T> {
    private WeakReference<T> mView = new WeakReference<T>(null);
    private WeakReference<Context> mContext = new WeakReference<>(null);

    public BasePresenter(Context context) {
        setContext(context);
    }

    @Override
    public void setView(T view) {
        mView = new WeakReference<T>(view);
    }

    @Override
    public T getView() {
        return mView.get();
    }

    @Override
    public void setContext(Context context) {
        mContext = new WeakReference<>(context);
    }

    @Override
    public Context getContext() {
        Context context = mContext.get();

        // View could also hold a context
        if (context == null) {
            T view = mView.get();
            if (view instanceof Fragment) {
                context = ((Fragment) view).getContext();
            }
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
