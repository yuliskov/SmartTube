package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.common.helpers.ServiceHelper;
import com.stfalcon.chatkit.commons.models.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ChatItemMessage implements IMessage {
    private static final int MAX_LENGTH = 700;
    private String mId;
    private CharSequence mText;
    private ChatItemAuthor mAuthor;
    private Date mCreatedAt;
    private CommentItem mCommentItem;

    public static ChatItemMessage from(ChatItem chatItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = chatItem.getId();
        if (chatItem.getMessage() != null && !chatItem.getMessage().trim().isEmpty()) {
            message.mText = TextUtils.concat(Utils.bold(chatItem.getAuthorName()), ": ", chatItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(chatItem);
        message.mCreatedAt = new Date();

        return message;
    }

    public static ChatItemMessage from(CommentItem commentItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = commentItem.getId();
        if (commentItem.getMessage() != null && !commentItem.getMessage().trim().isEmpty()) {
            String header = ServiceHelper.combineItems(
                    " " + Video.TERTIARY_TEXT_DELIM + " ",
                    commentItem.getAuthorName(),
                    commentItem.getLikeCount(),
                    commentItem.getPublishedDate(),
                    commentItem.getReplyCount());
            message.mText = TextUtils.concat(Utils.bold(header), "\n", commentItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(commentItem);
        message.mCreatedAt = new Date();
        message.mCommentItem = commentItem;

        return message;
    }

    public static List<ChatItemMessage> fromSplit(CommentItem commentItem) {
        if (shouldSplit(commentItem)) {
            List<String> comments = Helpers.splitStringBySize(commentItem.getMessage(), MAX_LENGTH);
            List<ChatItemMessage> result = new ArrayList<>();
            for (int i = 0; i < comments.size(); i++) {
                String prefix = i > 0 ? "..." : "";
                String postfix = i < (comments.size() - 1) ? "..." : "";
                String comment = prefix + comments.get(i) + postfix;
                result.add(from(new CommentItem() {
                    public String getId() {
                        return String.valueOf(comment.hashCode());
                    }

                    public String getMessage() {
                        return comment;
                    }

                    public String getAuthorName() {
                        return commentItem.getAuthorName();
                    }

                    public String getAuthorPhoto() {
                        return commentItem.getAuthorPhoto();
                    }

                    public String getPublishedDate() {
                        return commentItem.getPublishedDate();
                    }

                    public String getNestedCommentsKey() {
                        return commentItem.getNestedCommentsKey();
                    }

                    public boolean isLiked() {
                        return commentItem.isLiked();
                    }

                    public String getLikeCount() {
                        return commentItem.getLikeCount();
                    }

                    public String getReplyCount() {
                        return commentItem.getReplyCount();
                    }
                }));
            }
            return result;
        }

        return Collections.singletonList(from(commentItem));
    }

    public static boolean shouldSplit(CommentItem commentItem) {
        return commentItem != null && commentItem.getMessage() != null && commentItem.getMessage().trim().length() > MAX_LENGTH;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public CharSequence getText() {
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

    public CommentItem getCommentItem() {
        return mCommentItem;
    }
}
