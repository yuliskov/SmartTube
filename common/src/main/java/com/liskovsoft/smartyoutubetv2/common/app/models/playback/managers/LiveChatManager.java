package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiverImpl;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class LiveChatManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = LiveChatManager.class.getSimpleName();
    /**
     * NOTE: Don't remove duplicates! They contain different chars.
     */
    private static final String[] BLACK_LIST = {". XYZ", ". ХYZ", "⠄XYZ", "⠄ХYZ", "Ricardo Merlino", "⠄СОM", ".COM", ".СОM", ". COM"};
    private LiveChatService mChatService;
    private PlayerData mPlayerData;
    private PlayerTweaksData mPlayerTweaksData;
    private Disposable mChatAction;
    private String mLiveChatKey;

    @Override
    public void onInitDone() {
        mChatService = YouTubeMediaService.instance().getLiveChatService();
        mPlayerData = PlayerData.instance(getActivity());
        mPlayerTweaksData = PlayerTweaksData.instance(getActivity());
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
        if (mLiveChatKey != null) {
            enableLiveChat(!enabled);
        }
    }

    @Override
    public void onChatLongClicked(boolean enabled) {
        String chatCategoryTitle = getActivity().getString(R.string.open_chat);

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getActivity());

        settingsPresenter.clear();

        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getActivity().getString(R.string.option_disabled),
                optionItem -> {
                    enableLiveChat(false);
                    settingsPresenter.closeDialog();
                },
                !mPlayerData.isLiveChatEnabled()));

        options.add(UiOptionItem.from(getActivity().getString(R.string.chat_left),
                optionItem -> {
                    mPlayerTweaksData.placeChatLeft(true);
                    enableLiveChat(true);
                    settingsPresenter.closeDialog();
                },
                mPlayerData.isLiveChatEnabled() && mPlayerTweaksData.isChatPlacedLeft()));

        options.add(UiOptionItem.from(getActivity().getString(R.string.chat_right),
                optionItem -> {
                    mPlayerTweaksData.placeChatLeft(false);
                    enableLiveChat(true);
                    settingsPresenter.closeDialog();
                },
                mPlayerData.isLiveChatEnabled() && !mPlayerTweaksData.isChatPlacedLeft()));

        settingsPresenter.appendRadioCategory(chatCategoryTitle, options);

        settingsPresenter.showDialog(chatCategoryTitle);
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
            if (authorName.toLowerCase().contains(spammer.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    private void enableLiveChat(boolean enabled) {
        if (enabled) {
            openLiveChat();
        } else {
            disposeActions();
        }
        mPlayerData.enableLiveChat(enabled);

        if (mLiveChatKey != null) {
            getController().setChatButtonState(enabled ? PlaybackUIController.BUTTON_STATE_ON : PlaybackUIController.BUTTON_STATE_OFF);
        }
    }
}
