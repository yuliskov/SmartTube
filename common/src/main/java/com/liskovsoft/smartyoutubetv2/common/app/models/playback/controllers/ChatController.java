package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.LiveChatService;
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerUI;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiverImpl;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class ChatController extends BasePlayerController {
    private static final String TAG = ChatController.class.getSimpleName();
    /**
     * NOTE: Don't remove duplicates! They contain different chars.
     */
    private static final String[] BLACK_LIST = {". XYZ", ". ХYZ", "⠄XYZ", "⠄ХYZ", "Ricardo Merlino", "⠄СОM", ".COM", ".СОM", ". COM"};
    private LiveChatService mChatService;
    private Disposable mChatAction;
    private String mLiveChatKey;

    @Override
    public void onInit() {
        mChatService = YouTubeServiceManager.instance().getLiveChatService();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mLiveChatKey = metadata != null ? metadata.getLiveChatKey() : null;

        if (mLiveChatKey != null) {
            getPlayer().setButtonState(R.id.action_chat, getPlayerData().isLiveChatEnabled() ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }

        if (getPlayerData().isLiveChatEnabled()) {
            openLiveChat();
        }
    }

    private void openLiveChat() {
        disposeActions();

        if (mLiveChatKey == null) {
            return;
        }

        ChatReceiver chatReceiver = new ChatReceiverImpl();
        getPlayer().setChatReceiver(chatReceiver);

        mChatAction = mChatService.openLiveChatObserve(mLiveChatKey)
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
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_chat) {
            if (mLiveChatKey != null) {
                enableLiveChat(buttonState != PlayerUI.BUTTON_ON);
            }
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_chat) {
            String chatCategoryTitle = getContext().getString(R.string.open_chat);

            AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

            List<OptionItem> options = new ArrayList<>();

            options.add(UiOptionItem.from(getContext().getString(R.string.option_disabled),
                    optionItem -> {
                        enableLiveChat(false);
                        settingsPresenter.closeDialog();
                    },
                    !getPlayerData().isLiveChatEnabled()));

            options.add(UiOptionItem.from(getContext().getString(R.string.chat_left),
                    optionItem -> {
                        placeChatLeft(true);
                        enableLiveChat(true);
                        settingsPresenter.closeDialog();
                    },
                    getPlayerData().isLiveChatEnabled() && isChatPlacedLeft()));

            options.add(UiOptionItem.from(getContext().getString(R.string.chat_right),
                    optionItem -> {
                        placeChatLeft(false);
                        enableLiveChat(true);
                        settingsPresenter.closeDialog();
                    },
                    getPlayerData().isLiveChatEnabled() && !isChatPlacedLeft()));

            settingsPresenter.appendRadioCategory(chatCategoryTitle, options);

            settingsPresenter.showDialog(chatCategoryTitle);
        }
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
        if (RxHelper.isAnyActionRunning(mChatAction)) {
            RxHelper.disposeActions(mChatAction);
            getPlayer().setChatReceiver(null);
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
        getPlayerData().enableLiveChat(enabled);

        if (mLiveChatKey != null) {
            getPlayer().setButtonState(R.id.action_chat, enabled ? PlayerUI.BUTTON_ON : PlayerUI.BUTTON_OFF);
        }
    }

    private void placeChatLeft(boolean left) {
        if (mLiveChatKey != null) {
            getPlayerTweaksData().placeChatLeft(left);
        } else {
            getPlayerTweaksData().placeCommentsLeft(left);
        }
    }

    private boolean isChatPlacedLeft() {
        return mLiveChatKey != null ? getPlayerTweaksData().isChatPlacedLeft() : getPlayerTweaksData().isCommentsPlacedLeft();
    }
}
