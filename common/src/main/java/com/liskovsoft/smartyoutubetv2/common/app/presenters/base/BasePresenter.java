package com.liskovsoft.smartyoutubetv2.common.app.presenters.base;

import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
//import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.BootDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public abstract class BasePresenter<T> implements Presenter<T> {
    private static boolean sSync;
    private WeakReference<T> mView = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private WeakReference<Context> mApplicationContext = new WeakReference<>(null);
    private Runnable mOnDone;
    private static boolean sRunOnce;
    private long mUpdateCheckMs;

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

        if (!sRunOnce) {
            sRunOnce = true;
            // Init shared prefs used inside remote control service.
            Utils.initGlobalData(context);
        }

        // Localization fix: prefer Activity context
        if (context instanceof Activity && Utils.checkActivity((Activity) context)) {
            mActivity = new WeakReference<>((Activity) context);
        }

        // In case view was disposed like SplashView does
        mApplicationContext = new WeakReference<>(context.getApplicationContext());

        //initGlobalData();
    }

    @Override
    public Context getContext() {
        Activity activity = null;

        // Trying to find localized context.
        // First, try the view that belongs to this presenter.
        // Second, try the activity that presenter called (could be destroyed).
        if (mView.get() instanceof Fragment) {
            activity = ((Fragment) mView.get()).getActivity();
        } else if (mActivity.get() != null) {
            activity = mActivity.get();
        }

        // In case view was disposed like SplashView does
        // Fallback to non-localized ApplicationContext if others fail
        return Utils.checkActivity(activity) ? activity : mApplicationContext.get();
    }

    @Override
    public void onViewInitialized() {
        showBootDialogs();
    }

    @Override
    public void onViewDestroyed() {
        // View stays in RAM after has been destroyed. Is it a bug?
        mView = new WeakReference<>(null);
        mActivity = new WeakReference<>(null);
    }

    @Override
    public void onViewResumed() {
        if (sSync && canViewBeSynced()) {
            // NOTE: don't place cleanup in the onViewResumed!!! This could cause errors when view is resumed.
            syncItem(Playlist.instance().getChangedItems());
        }

        showBootDialogs();
    }

    @Override
    public void onFinish() {
        if (SearchData.instance(getContext()).isTempBackgroundModeStarted() &&
            PlaybackPresenter.instance(getContext()).isRunningInBackground()) {
            ViewManager.instance(getContext()).startView(SplashView.class);
        }
    }

    public void setOnDone(Runnable onDone) {
        mOnDone = onDone;
    }

    protected void onDone() {
        if (mOnDone != null) {
            mOnDone.run();
            mOnDone = null;
        }
    }

    protected void removeItem(Video item) {
        removeItem(Collections.singletonList(item), VideoGroup.ACTION_REMOVE);
    }

    protected void removeItemAuthor(Video item) {
        removeItem(Collections.singletonList(item), VideoGroup.ACTION_REMOVE_AUTHOR);
    }

    private void removeItem(List<Video> items, int action) {
        if (items.size() == 0) {
            return;
        }

        VideoGroup removedGroup = VideoGroup.from(items);
        removedGroup.setAction(action);
        T view = getView();

        updateView(removedGroup, view);
    }

    public void syncItem(Video item) {
        syncItem(Collections.singletonList(item));
    }

    public void syncItem(List<Video> items) {
        if (items.size() == 0) {
            return;
        }

        VideoGroup syncGroup = VideoGroup.from(items);
        syncGroup.setAction(VideoGroup.ACTION_SYNC);
        T view = getView();

        if (updateView(syncGroup, view)) {
            sSync = false;
        }
    }

    private boolean canViewBeSynced() {
        T view = getView();
        return view instanceof BrowseView ||
               view instanceof ChannelView ||
               view instanceof ChannelUploadsView ||
               view instanceof SearchView;
    }

    private boolean updateView(VideoGroup group, T view) {
        if (view instanceof BrowseView) {
            ((BrowseView) view).updateSection(group);
        } else if (view instanceof ChannelView) {
            ((ChannelView) view).update(group);
        } else if (view instanceof ChannelUploadsView) {
            ((ChannelUploadsView) view).update(group);
        } else if (view instanceof SearchView) {
            ((SearchView) view).updateSearch(group);
        } else {
            return false;
        }

        return true;
    }

    protected static void enableSync() {
        sSync = true;
        Playlist.instance().onNewSession();
    }

    private void showBootDialogs() {

    }
}
