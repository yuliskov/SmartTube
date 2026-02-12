package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver.Backup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.AbstractCommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import io.reactivex.disposables.Disposable;

public class CommentsController extends BasePlayerController {
    private static final String TAG = CommentsController.class.getSimpleName();
    private Disposable mCommentsAction;
    private String mLiveChatKey;
    private String mCommentsKey;
    private String mTitle;
    private Pair<String, Backup> mBackup;

    public CommentsController() {
    }

    public CommentsController(Context context, MediaItemMetadata metadata) {
        setAltContext(context);
        onInit();
        onMetadata(metadata);
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mLiveChatKey = metadata != null && metadata.getLiveChatKey() != null ? metadata.getLiveChatKey() : null;
        mCommentsKey = metadata != null && metadata.getCommentsKey() != null ? metadata.getCommentsKey() : null;
        mTitle = metadata != null ? metadata.getTitle() : null;
        if (mBackup != null && !Helpers.equals(mBackup.first, mCommentsKey)) {
            mBackup = null;
        }
    }

    private void openCommentsDialog() {
        fitVideoIntoDialog();

        disposeActions();

        if (mCommentsKey == null) {
            return;
        }

        final String backupKey = mCommentsKey;

        if (getPlayer() != null) {
            getPlayer().showControls(false);
        }

        String title = getPlayer() != null && getPlayer().getVideo() != null ? getPlayer().getVideo().getTitleFull() : mTitle;

        CommentsReceiver commentsReceiver = new AbstractCommentsReceiver(getContext()) {
            @Override
            public void onLoadMore(CommentGroup commentGroup) {
                loadComments(this, commentGroup.getNextCommentsKey());
            }

            @Override
            public void onStart() {
                if (mBackup != null && Helpers.equals(mBackup.first, mCommentsKey)) {
                    loadBackup(mBackup.second);
                    return;
                }

                loadComments(this, mCommentsKey);
            }

            @Override
            public void onCommentClicked(CommentItem commentItem) {
                if (commentItem.isEmpty()) {
                    return;
                }

                CommentsReceiver nestedReceiver = new AbstractCommentsReceiver(getContext()) {
                    @Override
                    public void onLoadMore(CommentGroup commentGroup) {
                        loadComments(this, commentGroup.getNextCommentsKey());
                    }

                    @Override
                    public void onStart() {
                        loadComments(this, commentItem.getNestedCommentsKey());
                    }

                    @Override
                    public void onCommentLongClicked(CommentItem commentItem) {
                        toggleLike(this, commentItem);
                    }
                };

                showDialog(nestedReceiver, title);
            }

            @Override
            public void onCommentLongClicked(CommentItem commentItem) {
                toggleLike(this, commentItem);
            }

            @Override
            public void onFinish(Backup backup) {
                if (Helpers.equals(backupKey, mCommentsKey)) {
                    mBackup = new Pair<>(mCommentsKey, backup);
                }
            }
        };

        showDialog(commentsReceiver, title);
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_chat) {
            if (mCommentsKey != null && mLiveChatKey == null) {
                openCommentsDialog();
            }

            if (mCommentsKey == null && mLiveChatKey == null) {
                MessageHelpers.showMessage(getContext(), R.string.section_is_empty);
            }
        }
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
        mBackup = null;
    }

    @Override
    public void onFinish() {
        disposeActions();
    }

    private void disposeActions() {
        RxHelper.disposeActions(mCommentsAction);
    }

    private void loadComments(CommentsReceiver receiver, String commentsKey) {
        disposeActions();

        mCommentsAction = getCommentsService().getCommentsObserve(commentsKey)
                .subscribe(
                        receiver::addCommentGroup,
                        error -> {
                            Log.e(TAG, error.getMessage());
                            receiver.addCommentGroup(null); // remove loading message
                        }
                );
    }

    private void showDialog(CommentsReceiver receiver, String title) {
        AppDialogPresenter appDialogPresenter = getAppDialogPresenter();

        appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
        //appDialogPresenter.enableTransparent(true);
        appDialogPresenter.showDialog();
    }

    private void toggleLike(CommentsReceiver receiver, CommentItem commentItem) {
        MyCommentItem myCommentItem = MyCommentItem.from(commentItem);
        myCommentItem.setLiked(!myCommentItem.isLiked());

        receiver.sync(myCommentItem);

        RxHelper.execute(
                getCommentsService().toggleLikeObserve(commentItem.getNestedCommentsKey()), e -> MessageHelpers.showMessage(getContext(), e.getMessage()));
    }

    private static final class MyCommentItem implements CommentItem {
        private final String mId;
        private final String mMessage;
        private final String mAuthorName;
        private final String mAuthorPhoto;
        private final String mPublishedDate;
        private final String mNestedCommentsKey;
        private boolean mIsLiked;
        private String mLikeCount;
        private final String mReplyCount;
        private final boolean mIsEmpty;

        private MyCommentItem(
                String id, String message, String authorName, String authorPhoto, String publishedDate,
                String nestedCommentsKey, boolean isLiked, String likeCount, String replyCount, boolean isEmpty) {
            mId = id;
            mMessage = message;
            mAuthorName = authorName;
            mAuthorPhoto = authorPhoto;
            mPublishedDate = publishedDate;
            mNestedCommentsKey = nestedCommentsKey;
            mIsLiked = isLiked;
            mLikeCount = likeCount;
            mReplyCount = replyCount;
            mIsEmpty = isEmpty;
        }

        @Override
        public String getId() {
            return mId;
        }

        @Override
        public String getMessage() {
            return mMessage;
        }

        @Override
        public String getAuthorName() {
            return mAuthorName;
        }

        @Override
        public String getAuthorPhoto() {
            return mAuthorPhoto;
        }

        @Override
        public String getPublishedDate() {
            return mPublishedDate;
        }

        @Override
        public String getNestedCommentsKey() {
            return mNestedCommentsKey;
        }

        @Override
        public boolean isLiked() {
            return mIsLiked;
        }

        public void setLiked(boolean isLiked) {
            if (mIsLiked == isLiked) {
                return;
            }

            mIsLiked = isLiked;

            if (mLikeCount == null) {
                mLikeCount = String.valueOf(0);
            }

            if (Helpers.isInteger(getLikeCount())) {
                int likeCount = Helpers.parseInt(getLikeCount());
                int count = isLiked ? ++likeCount : --likeCount;
                mLikeCount = count > 0 ? String.valueOf(count) : null;
            }
        }

        @Override
        public String getLikeCount() {
            return mLikeCount;
        }

        @Override
        public String getReplyCount() {
            return mReplyCount;
        }

        @Override
        public boolean isEmpty() {
            return mIsEmpty;
        }

        public static MyCommentItem from(CommentItem commentItem) {
            return new MyCommentItem(commentItem.getId(), commentItem.getMessage(), commentItem.getAuthorName(),
                    commentItem.getAuthorPhoto(), commentItem.getPublishedDate(), commentItem.getNestedCommentsKey(),
                    commentItem.isLiked(), commentItem.getLikeCount(), commentItem.getReplyCount(), commentItem.isEmpty());
        }
    }
}
