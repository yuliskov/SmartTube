package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.yt.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.CommentItem;
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
    public void loadBackup(Backup backup) {
        if (mCallback != null) {
            mCallback.onBackup(backup);
        }
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public void onLoadMore(CommentGroup commentGroup) {

    }

    @Override
    public void onCommentClicked(CommentItem commentItem) {

    }

    @Override
    public void onFinish(Backup backup) {
        
    }

    @Override
    public String getLoadingMessage() {
        if (mContext == null) {
            return null;
        }

        return mContext.getString(R.string.loading);
    }

    @Override
    public String getErrorMessage() {
        return mContext.getString(R.string.section_is_empty);
    }
}
