package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiverImpl;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class LiveChatManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = LiveChatManager.class.getSimpleName();
    private final LiveChatService mChatService;
    private Disposable mChatAction;

    public LiveChatManager() {
        mChatService = YouTubeMediaService.instance().getLiveChatService();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        if (metadata != null && metadata.getLiveChatKey() != null) {
            //openLiveChat(metadata.getLiveChatKey());
        }
    }

    private void openLiveChat(String chatKey) {
        disposeActions();

        AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());
        dialogPresenter.clear();
        Video video = getController().getVideo();
        String title = String.format("%s - %s", video.getTitle(), video.getAuthor());
        ChatReceiverImpl chatReceiver = new ChatReceiverImpl();
        dialogPresenter.appendChatCategory(title, UiOptionItem.from(title, chatReceiver));
        dialogPresenter.showDialog(title);

        mChatAction = mChatService.openLiveChatObserve(chatKey)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        chatItem -> {
                            Log.d(TAG, chatItem.getMessage());
                            chatReceiver.addChatItem(chatItem);
                        },
                        error -> {
                            Log.e(TAG, error.getMessage());
                            error.printStackTrace();
                        },
                        () -> Log.e(TAG, "Live chat session has been closed")
                );
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
    }

    @Override
    public void onFinish() {
        disposeActions();
    }

    private void disposeActions() {
        if (RxUtils.isAnyActionRunning(mChatAction)) {
            RxUtils.disposeActions(mChatAction);
            AppDialogPresenter dialogPresenter = AppDialogPresenter.instance(getActivity());
            dialogPresenter.closeDialog();
        }
    }
}
