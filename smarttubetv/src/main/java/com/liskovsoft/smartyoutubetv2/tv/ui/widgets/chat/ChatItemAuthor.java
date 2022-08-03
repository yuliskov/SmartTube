package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.stfalcon.chatkit.commons.models.IUser;

public class ChatItemAuthor implements IUser {
    private String mId;
    private String mName;
    private String mAvatar;

    public static ChatItemAuthor from(ChatItem chatItem) {
        ChatItemAuthor author = new ChatItemAuthor();
        author.mAvatar = chatItem.getAuthorPhoto();
        author.mName = chatItem.getAuthorName();
        author.mId = chatItem.getAuthorName();

        return author;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public String getAvatar() {
        return mAvatar;
    }
}
