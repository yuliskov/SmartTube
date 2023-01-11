package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.smartyoutubetv2.common.R;

public abstract class CommentsReceiverImpl implements CommentsReceiver {
    private final Context mContext;
    private Callback mCallback;

    public CommentsReceiverImpl(Context context) {
        mContext = context;
    }

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
        return mContext.getString(R.string.loading);
    }
}
