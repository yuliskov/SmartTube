package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;

public interface CommentsReceiver {
    interface Callback {
        void onCommentGroup(CommentGroup commentGroup);
    }
    void addCommentGroup(CommentGroup commentGroup);
    void setCallback(Callback callback);
    void onLoadMore(String nextCommentsKey);
    void onCommentClicked(String nestedCommentsKey);
    String getLoadingMessage();
}
