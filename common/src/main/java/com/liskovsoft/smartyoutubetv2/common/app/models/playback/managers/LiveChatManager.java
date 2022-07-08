package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiverImpl;
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
            openLiveChat(metadata.getLiveChatKey());
        }
    }

    private void openLiveChat(String chatKey) {
        disposeActions();

        ChatReceiver chatReceiver = new ChatReceiverImpl();
        getController().setChatReceiver(chatReceiver);

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
            getController().setChatReceiver(null);
        }
    }
}
