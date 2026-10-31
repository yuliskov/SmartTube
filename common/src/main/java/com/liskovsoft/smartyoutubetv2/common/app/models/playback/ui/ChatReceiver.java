package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;

public interface ChatReceiver {
    interface Callback {
        void onChatItem(ChatItem chatItem);
    }
    void addChatItem(ChatItem chatItem);
    void setCallback(Callback callback);
}
