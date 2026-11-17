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
            message.mText = TextUtils.concat(Utils.bold(header), "\n", Utils.createSmallNewLine(), commentItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(commentItem);
        message.mCreatedAt = new Date();
        message.mCommentItem = commentItem;

        return message;
    }

    public static List<ChatItemMessage> fromSplit(Context context, CommentItem commentItem) {
        if (shouldSplit(commentItem)) {
            List<String> comments = splitByWidth(commentItem.getMessage(), getRealMaxLen(commentItem.getMessage()));
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
        return commentItem != null && commentItem.getMessage() != null && width(commentItem.getMessage()) > getRealMaxLen(commentItem.getMessage());
    }

    private static int getRealMaxLen(String text) {
        if (text == null) {
            return -1;
        }

        String[] split = text.split("\n");

        if (split.length == 1) {
            return MAX_LENGTH;
        }

        // Measured in display width (see width()): every line takes at least one full line on screen
        int realCount = 0;
        int fakeCount = 0;

        for (String line : split) {
            int lineWidth = width(line);
            int lines = Math.max(1, (lineWidth + LINE_LENGTH - 1) / LINE_LENGTH);
            realCount += lineWidth;
            fakeCount += lines * LINE_LENGTH;

            if (fakeCount > MAX_LENGTH) {
                return realCount;
            }
        }

        return MAX_LENGTH;
    }

    /**
     * Display width: CJK and other full-width characters take about twice the space of Latin ones.
     */
    private static int width(CharSequence text) {
        int width = 0;

        for (int i = 0; i < text.length(); i++) {
            width += isWide(text.charAt(i)) ? 2 : 1;
        }

        return width;
    }

    private static boolean isWide(char c) {
        return (c >= 0x1100 && c <= 0x115F) // Hangul Jamo
                || (c >= 0x2E80 && c <= 0xA4CF) // CJK, Hiragana, Katakana, Bopomofo...
                || (c >= 0xAC00 && c <= 0xD7A3) // Hangul syllables
                || (c >= 0xF900 && c <= 0xFAFF) // CJK compatibility ideographs
                || (c >= 0xFE30 && c <= 0xFE4F) // CJK compatibility forms
                || (c >= 0xFF00 && c <= 0xFF60) // Fullwidth forms
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    /**
     * Splits by display width. Breaks at the last space when there is one, otherwise inside the text
     * (CJK text has no spaces, so splitting by words left long comments in one piece).
     */
    private static List<String> splitByWidth(String text, int maxWidth) {
        List<String> result = new ArrayList<>();

        if (maxWidth <= 0) {
            result.add(text);
            return result;
        }

        StringBuilder chunk = new StringBuilder();
        int chunkWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            chunk.append(c);
            chunkWidth += isWide(c) ? 2 : 1;

            if (chunkWidth > maxWidth) {
                int lastSpace = chunk.lastIndexOf(" ");
                String rest = "";

                if (lastSpace > 0) {
                    rest = chunk.substring(lastSpace + 1);
                    chunk.setLength(lastSpace);
                }

                result.add(chunk.toString());
                chunk = new StringBuilder(rest);
                chunkWidth = width(rest);
            }
        }

        if (chunk.length() > 0) {
            result.add(chunk.toString());
        }

        return result;
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
