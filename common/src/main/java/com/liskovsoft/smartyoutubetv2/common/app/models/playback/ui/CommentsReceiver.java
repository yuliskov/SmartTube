package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;

public interface CommentsReceiver {
    interface Callback {
        void onCommentGroup(CommentGroup commentGroup);
        void onBackup(Backup backup);
    }
    interface Backup {}
    void addCommentGroup(CommentGroup commentGroup);
    void loadBackup(Backup backup);
    void setCallback(Callback callback);
    void onLoadMore(CommentGroup commentGroup);
    void onStart();
    void onCommentClicked(CommentItem commentItem);
    void onFinish(Backup backup);
    String getLoadingMessage();
    String getErrorMessage();
}
