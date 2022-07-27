package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiverImpl;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class LiveChatManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = LiveChatManager.class.getSimpleName();
    private static final String[] BLACK_LIST = {". XYZ", ". ХYZ"};
    private LiveChatService mChatService;
    private PlayerData mPlayerData;
    private Disposable mChatAction;
    private String mLiveChatKey;

    @Override
    public void onInitDone() {
        mChatService = YouTubeMediaService.instance().getLiveChatService();
        mPlayerData = PlayerData.instance(getActivity());
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mLiveChatKey = metadata != null && metadata.getLiveChatKey() != null ? metadata.getLiveChatKey() : null;

        if (mLiveChatKey != null) {
            getController().setChatButtonState(mPlayerData.isLiveChatEnabled() ? PlaybackUIController.BUTTON_STATE_ON : PlaybackUIController.BUTTON_STATE_OFF);
        }

        if (mPlayerData.isLiveChatEnabled()) {
            openLiveChat();
        }
    }

    private void openLiveChat() {
        disposeActions();

        if (mLiveChatKey == null) {
            return;
        }

        ChatReceiver chatReceiver = new ChatReceiverImpl();
        getController().setChatReceiver(chatReceiver);

        mChatAction = mChatService.openLiveChatObserve(mLiveChatKey)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        chatItem -> {
                            Log.d(TAG, chatItem.getMessage());
                            if (checkItem(chatItem)) {
                                chatReceiver.addChatItem(chatItem);
                            }
                        },
                        error -> {
                            Log.e(TAG, error.getMessage());
                            error.printStackTrace();
                        },
                        () -> Log.e(TAG, "Live chat session has been closed")
                );
    }

    @Override
    public void onChatClicked(boolean enabled) {
        if (enabled) {
            openLiveChat();
        } else {
            disposeActions();
        }
        mPlayerData.enableLiveChat(enabled);
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

    private boolean checkItem(ChatItem chatItem) {
        if (chatItem == null || chatItem.getAuthorName() == null) {
            return false;
        }

        String authorName = chatItem.getAuthorName();

        for (String spammer : BLACK_LIST) {
            if (authorName.contains(spammer)) {
                return false;
            }
        }

        return true;
    }
}
