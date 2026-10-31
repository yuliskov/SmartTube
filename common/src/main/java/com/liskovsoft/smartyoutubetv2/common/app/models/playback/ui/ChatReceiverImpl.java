package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;

public class ChatReceiverImpl implements ChatReceiver {
    private Callback mCallback;

    @Override
    public void addChatItem(ChatItem chatItem) {
        if (mCallback != null) {
            mCallback.onChatItem(chatItem);
        }
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }
}
