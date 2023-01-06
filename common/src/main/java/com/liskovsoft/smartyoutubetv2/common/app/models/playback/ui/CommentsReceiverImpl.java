package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;

public class CommentsReceiverImpl implements CommentsReceiver {
    private Callback mCallback;

    @Override
    public void addCommentGroup(CommentGroup commentGroup) {
        if (mCallback != null) {
            mCallback.onCommentGroup(commentGroup);
        }
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void onLoadMore(String nextCommentsKey) {

    }

    @Override
    public void onCommentClicked(String nestedCommentsKey) {

    }

    @Override
    public String getLoadingMessage() {
        return null;
    }
}
