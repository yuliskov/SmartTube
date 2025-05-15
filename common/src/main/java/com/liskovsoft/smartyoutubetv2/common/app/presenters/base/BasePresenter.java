package com.liskovsoft.smartyoutubetv2.common.app.presenters.base;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.interfaces.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.TickleManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public abstract class BasePresenter<T> implements Presenter<T> {
    private static final String TAG = BasePresenter.class.getSimpleName();
    private WeakReference<T> mView = new WeakReference<>(null);
    private WeakReference<Activity> mActivity = new WeakReference<>(null);
    private WeakReference<Context> mApplicationContext = new WeakReference<>(null);
    private Runnable mOnDone;

    public BasePresenter(Context context) {
        setContext(context);
    }

    @Override
    public void setView(T view) {
        if (checkView(view)) {
            mView = new WeakReference<>(view);
        }
    }

    @Override
    public T getView() {
        T view = mView.get();

        return checkView(view) ? view : null;
    }

    @Override
    public void setContext(Context context) {
        if (context == null) {
            return;
        }

        // Localization fix: prefer Activity context
        if (context instanceof Activity && Utils.checkActivity((Activity) context)) {
            mActivity = new WeakReference<>((Activity) context);
        }

        // In case view was disposed like SplashView does
        mApplicationContext = new WeakReference<>(context.getApplicationContext());
    }

    @Override
    public Context getContext() {
        Activity activity = null;

        Activity viewActivity = getViewActivity(mView.get());

        // Trying to find localized context.
        // First, try the view that belongs to this presenter.
        // Second, try the activity that presenter called (could be destroyed).
        if (viewActivity != null) {
            activity = viewActivity;
        } else if (mActivity.get() != null) {
            activity = mActivity.get();
        }

        // In case view was disposed like SplashView does
        // Fallback to non-localized ApplicationContext if others fail
        return Utils.checkActivity(activity) ? activity : mApplicationContext.get();
    }

    @Override
    public void onViewInitialized() {
        enableSync();
        //showBootDialogs();
    }

    @Override
    public void onViewDestroyed() {
        // Multiple views with same presenter fix?
        // View stays in RAM after has been destroyed. Is it a bug?
        //mView = new WeakReference<>(null);
        //mActivity = new WeakReference<>(null);
    }

    @Override
    public void onViewPaused() {
        // NOP
    }

    @Override
    public void onViewResumed() {
        if (canViewBeSynced()) {
            // NOTE: don't place cleanup in the onViewResumed!!! This could cause errors when view is resumed.
            if (syncItem(Playlist.instance().getChangedItems())) {
                Playlist.instance().onNewSession();
            }
        }

        //showBootDialogs();
    }

    @Override
    public void onFinish() {
        if (getSearchData().getTempBackgroundModeClass() == this.getClass() &&
            getPlaybackPresenter().isRunningInBackground()) {
            getViewManager().startView(PlaybackView.class);
        }

        onDone();
    }

    public void setOnDone(Runnable onDone) {
        mOnDone = onDone;
    }

    public Runnable getOnDone() {
        return mOnDone;
    }

    private void onDone() {
        if (mOnDone != null) {
            mOnDone.run();
            mOnDone = null;
        }
    }

    public void removeItem(Video item) {
        removeItem(Collections.singletonList(item), VideoGroup.ACTION_REMOVE);
    }

    public void removeItemAuthor(Video item) {
        removeItem(Collections.singletonList(item), VideoGroup.ACTION_REMOVE_AUTHOR);
    }

    public void removeItem(List<Video> items, int action) {
        if (items.isEmpty()) {
            return;
        }

        VideoGroup removedGroup = VideoGroup.from(items);
        removedGroup.setAction(action);
        T view = getView();

        updateView(removedGroup, view);
    }

    public boolean syncItem(Video item) {
        return syncItem(Collections.singletonList(item));
    }

    public boolean syncItem(List<Video> items) {
        if (items.isEmpty()) {
            return false;
        }

        VideoGroup syncGroup = VideoGroup.from(items);
        syncGroup.setAction(VideoGroup.ACTION_SYNC);
        T view = getView();

        return updateView(syncGroup, view);
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
        } else if (view instanceof PlaybackView) {
            ((PlaybackView) view).updateSuggestions(group);
        } else {
            return false;
        }

        return true;
    }

    private void enableSync() {
        if (this instanceof PlaybackPresenter) {
            Playlist.instance().onNewSession();
        }
    }

    /**
     * Check that view's activity is alive
     */
    private static <T> boolean checkView(T view) {
        Activity activity = getViewActivity(view);

        return Utils.checkActivity(activity);
    }

    private static <T> Activity getViewActivity(T view) {
        Activity activity = null;

        if (view instanceof Fragment) { // regular fragment
            activity = ((Fragment) view).getActivity();
        } else if (view instanceof android.app.Fragment) { // dialog fragment
            activity = ((android.app.Fragment) view).getActivity();
        } else if (view instanceof Activity) { // splash view
            activity = (Activity) view;
        } else if (view instanceof View) {
            Context context = ((View) view).getContext();
            if (context instanceof Activity) {
                activity = (Activity) context;
            }
        }
        return activity;
    }

    protected MainUIData getMainUIData() {
        return MainUIData.instance(getContext());
    }

    protected GeneralData getGeneralData() {
        return GeneralData.instance(getContext());
    }

    protected SearchData getSearchData() {
        return SearchData.instance(getContext());
    }

    protected SidebarService getSidebarService() {
        return SidebarService.instance(getContext());
    }

    protected CommentsService getCommentsService() {
        return YouTubeServiceManager.instance().getCommentsService();
    }

    protected ContentService getContentService() {
        return YouTubeServiceManager.instance().getContentService();
    }

    protected SignInService getSignInService() {
        return YouTubeServiceManager.instance().getSignInService();
    }

    protected NotificationsService getNotificationsService() {
        return YouTubeServiceManager.instance().getNotificationsService();
    }

    protected MediaItemService getMediaItemService() {
        return YouTubeServiceManager.instance().getMediaItemService();
    }

    protected MediaServiceManager getServiceManager() {
        return MediaServiceManager.instance();
    }

    protected ViewManager getViewManager() {
        return ViewManager.instance(getContext());
    }

    protected TickleManager getTickleManager() {
        return TickleManager.instance();
    }

    protected PlaybackPresenter getPlaybackPresenter() {
        return PlaybackPresenter.instance(getContext());
    }
}
