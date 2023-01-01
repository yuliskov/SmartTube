package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.stfalcon.chatkit.commons.models.IMessage;

import java.util.Date;

public class ChatItemMessage implements IMessage {
    private String mId;
    private String mText;
    private ChatItemAuthor mAuthor;
    private Date mCreatedAt;

    public static ChatItemMessage from(ChatItem chatItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = chatItem.getId();
        if (chatItem.getMessage() != null && !chatItem.getMessage().trim().isEmpty()) {
            message.mText = String.format("%s: %s", chatItem.getAuthorName(), chatItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(chatItem);
        message.mCreatedAt = new Date();

        return message;
    }

    public static ChatItemMessage from(CommentItem commentItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = commentItem.getId();
        if (commentItem.getMessage() != null && !commentItem.getMessage().trim().isEmpty()) {
            message.mText = String.format("%s %s %s: %s",
                    commentItem.getAuthorName(), Video.TERTIARY_TEXT_DELIM, commentItem.getPublishedDate(), commentItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(commentItem);
        message.mCreatedAt = new Date();

        return message;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String getText() {
        return mText;
    }

    @Override
    public ChatItemAuthor getUser() {
        return mAuthor;
    }

    @Override
    public Date getCreatedAt() {
        return mCreatedAt;
    }
}
