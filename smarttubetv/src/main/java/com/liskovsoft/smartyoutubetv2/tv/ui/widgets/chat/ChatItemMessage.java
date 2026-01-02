package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.googlecommon.common.helpers.ServiceHelper;
import com.stfalcon.chatkit.commons.models.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ChatItemMessage implements IMessage {
    private static final int MAX_LENGTH = 700;
    private static final int LINE_LENGTH = 30;
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

    public static ChatItemMessage from(Context context, CommentItem commentItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = commentItem.getId();
        if (commentItem.getMessage() != null && !commentItem.getMessage().trim().isEmpty()) {
            CharSequence header = ServiceHelper.combineItems(
                    " " + Video.TERTIARY_TEXT_DELIM + " ",
                    commentItem.getAuthorName(),
                    commentItem.getLikeCount() != null ? String.format("%s %s", commentItem.getLikeCount(), Helpers.THUMB_UP) : null,
                    commentItem.getPublishedDate(),
                    commentItem.getReplyCount(),
                    commentItem.isLiked() ? String.format("(%s)", context.getString(R.string.you_liked)) : null);
            message.mText = TextUtils.concat(Utils.bold(header), "\n", commentItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(commentItem);
        message.mCreatedAt = new Date();
        message.mCommentItem = commentItem;

        return message;
    }

    public static List<ChatItemMessage> fromSplit(Context context, CommentItem commentItem) {
        if (shouldSplit(commentItem)) {
            List<String> comments = Helpers.splitStringBySize(commentItem.getMessage(), getRealMaxLen(commentItem.getMessage()));
            List<ChatItemMessage> result = new ArrayList<>();
            for (int i = 0; i < comments.size(); i++) {
                String prefix = i > 0 ? "..." : "";
                String postfix = i < (comments.size() - 1) ? "..." : "";
                String comment = prefix + comments.get(i) + postfix;
                result.add(from(context, new CommentItem() {
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

                    @Override
                    public boolean isEmpty() {
                        return commentItem.isEmpty();
                    }
                }));
            }
            return result;
        }

        return Collections.singletonList(from(context, commentItem));
    }

    public static boolean shouldSplit(CommentItem commentItem) {
        return commentItem != null && commentItem.getMessage() != null && commentItem.getMessage().length() > getRealMaxLen(commentItem.getMessage());
    }

    private static int getRealMaxLen(String text) {
        if (text == null) {
            return -1;
        }

        String[] split = text.split("\n");

        if (split.length == 1) {
            return MAX_LENGTH;
        }

        // My additions:
        List<String> splitNoLongLines = new ArrayList<>();
        for (String line : split) {
            while (line.length() > LINE_LENGTH) {
                // Find last space before LINE_LENGTH
                int breakPoint = line.lastIndexOf(' ', LINE_LENGTH);

                // If no space found, just break at LINE_LENGTH
                if (breakPoint == -1) {
                    breakPoint = LINE_LENGTH;
                }

                splitNoLongLines.add(line.substring(0, breakPoint));
                line = line.substring(breakPoint).trim();
            }
            // Add remaining part (or whole line if it was short)
            splitNoLongLines.add(line);
        }

        // Then convert back to array and continue with original logic
        split = splitNoLongLines.toArray(new String[0]);

        int realCount = 0;
        int fakeCount = 0;

        for (String part : split) {
            realCount += part.length();
            fakeCount += Math.max(part.length(), LINE_LENGTH);

            if (fakeCount > MAX_LENGTH) {
                return realCount;
            }
        }

        return MAX_LENGTH;
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
