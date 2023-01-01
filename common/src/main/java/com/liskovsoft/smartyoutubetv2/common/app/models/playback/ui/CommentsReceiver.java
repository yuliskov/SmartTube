package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;

public interface CommentsReceiver {
    interface Callback {
        void onCommentGroup(CommentGroup commentGroup);
    }
    void addCommentGroup(CommentGroup commentGroup);
    void setCallback(Callback callback);
    void onGroupEnd(CommentGroup commentGroup);
    void onCommentClicked(CommentItem commentItem);
}
