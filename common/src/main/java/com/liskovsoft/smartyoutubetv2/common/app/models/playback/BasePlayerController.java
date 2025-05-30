package com.liskovsoft.smartyoutubetv2.common.app.models.playback;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.SignInService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.common.misc.ScreensaverManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.ContentBlockData;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.prefs.RemoteControlData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;

public abstract class BasePlayerController implements PlayerEventListener {
    private PlaybackPresenter mMainController;
    private Context mContext;

    public void setMainController(PlaybackPresenter mainController) {
        mMainController = mainController;
    }

    protected PlayerEventListener getMainController() {
        return mMainController;
    }

    protected <T extends PlayerEventListener> T getController(Class<T> clazz) {
        return mMainController != null ? mMainController.getController(clazz) : null;
    }

    @Nullable
    public PlaybackView getPlayer() {
        return mMainController != null ? mMainController.getPlayer() : null;
    }

    @Nullable
    public Video getVideo() {
        return mMainController != null ? mMainController.getVideo() : null;
    }

    protected void setAltContext(Context context) {
        mContext = context;
    }

    public Context getContext() {
        return mMainController != null ? mMainController.getContext() : mContext;
    }

    public Activity getActivity() {
        return mMainController != null ? mMainController.getActivity() : null;
    }
    
    @Override
    public void onInit() {
        // NOP
    }

    @Override
    public void onNewVideo(Video item) {
        // NOP
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        // NOP
    }

    @Override
    public void onSuggestionItemLongClicked(Video item) {
        // NOP
    }

    @Override
    public void onScrollEnd(Video item) {
        // NOP
    }

    @Override
    public boolean onPreviousClicked() {
        // NOP
        return false;
    }

    @Override
    public boolean onNextClicked() {
        // NOP
        return false;
    }

    @Override
    public void onViewCreated() {
        // NOP
    }

    @Override
    public void onViewDestroyed() {
        // NOP
    }

    @Override
    public void onViewPaused() {
        // NOP
    }

    @Override
    public void onViewResumed() {
        // NOP
    }

    @Override
    public void onFinish() {
        // NOP
    }

    @Override
    public void onSourceChanged(Video item) {
        // NOP
    }

    @Override
    public void onVideoLoaded(Video item) {
        // NOP
    }

    @Override
    public void onEngineInitialized() {
        // NOP
    }

    @Override
    public void onEngineReleased() {
        // NOP
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        // NOP
    }

    @Override
    public void onPlay() {
        // NOP
    }

    @Override
    public void onPause() {
        // NOP
    }

    @Override
    public void onPlayClicked() {
        // NOP
    }

    @Override
    public void onPauseClicked() {
        // NOP
    }

    @Override
    public void onSeekEnd() {
        // NOP
    }

    @Override
    public void onSeekPositionChanged(long positionMs) {
        // NOP
    }

    @Override
    public void onSpeedChanged(float speed) {
        // NOP
    }

    @Override
    public void onPlayEnd() {
        // NOP
    }

    @Override
    public void onBuffering() {
        // NOP
    }

    @Override
    public void onControlsShown(boolean shown) {
        // NOP
    }

    @Override
    public boolean onKeyDown(int keyCode) {
        // NOP
        return false;
    }

    @Override
    public void onHighQualityClicked() {
        // NOP
    }

    @Override
    public void onDislikeClicked(boolean dislike) {
        // NOP
    }

    @Override
    public void onLikeClicked(boolean like) {
        // NOP
    }

    @Override
    public void onTrackSelected(FormatItem track) {
        // NOP
    }

    @Override
    public void onSubtitleClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSubtitleLongClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        // NOP
    }

    @Override
    public void onPlaylistAddClicked() {
        // NOP
    }

    @Override
    public void onDebugInfoClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSpeedClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSpeedLongClicked(boolean enabled) {
        // NOP
    }

    @Override
    public void onSeekIntervalClicked() {
        // NOP
    }

    @Override
    public void onVideoInfoClicked() {
        // NOP
    }

    @Override
    public void onShareLinkClicked() {
        // NOP
    }

    @Override
    public void onSearchClicked() {
        // NOP
    }

    @Override
    public void onVideoZoomClicked() {
        // NOP
    }

    @Override
    public void onPipClicked() {
        // NOP
    }

    @Override
    public void onPlaybackQueueClicked() {
        // NOP
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        // NOP
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        // NOP
    }

    @Override
    public void onTickle() {
        // NOP
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        // NOP
    }

    protected PlayerData getPlayerData() {
        return PlayerData.instance(getContext());
    }

    protected GeneralData getGeneralData() {
        return GeneralData.instance(getContext());
    }

    protected MediaServiceData getMediaServiceData() {
        return MediaServiceData.instance();
    }

    protected PlayerTweaksData getPlayerTweaksData() {
        return PlayerTweaksData.instance(getContext());
    }

    protected RemoteControlData getRemoteControlData() {
        return RemoteControlData.instance(getContext());
    }

    protected VideoStateService getStateService() {
        return VideoStateService.instance(getContext());
    }

    protected ContentBlockData getContentBlockData() {
        return ContentBlockData.instance(getContext());
    }

    protected SearchData getSearchData() {
        return SearchData.instance(getContext());
    }

    protected MediaServiceManager getServiceManager() {
        return MediaServiceManager.instance();
    }

    protected ViewManager getViewManager() {
        return ViewManager.instance(getContext());
    }

    protected AppDialogPresenter getAppDialogPresenter() {
        return AppDialogPresenter.instance(getContext());
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

    protected SearchPresenter getSearchPresenter() {
        return SearchPresenter.instance(getContext());
    }

    protected PlaybackPresenter getPlaybackPresenter() {
        return PlaybackPresenter.instance(getContext());
    }

    protected boolean isEmbedPlayer() {
        return getPlayer() != null && getPlayer().isEmbed();
    }

    protected ScreensaverManager getScreensaverManager() {
        Activity activity = getActivity();
        if (activity instanceof MotherActivity) {
            return ((MotherActivity) activity).getScreensaverManager();
        }

        return null;
    }
}
