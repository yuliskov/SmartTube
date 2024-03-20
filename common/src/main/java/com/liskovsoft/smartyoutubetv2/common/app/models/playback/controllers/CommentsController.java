package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.SuggestionsController.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver.Backup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiverImpl;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeHubService;
import io.reactivex.disposables.Disposable;

public class CommentsController extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = CommentsController.class.getSimpleName();
    private CommentsService mCommentsService;
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
    public void onInit() {
        mCommentsService = YouTubeHubService.instance().getCommentsService();
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
        disposeActions();

        if (mCommentsKey == null) {
            return;
        }

        if (getPlayer() != null) {
            getPlayer().showControls(false);
        }

        String title = getPlayer() != null && getPlayer().getVideo() != null ? getPlayer().getVideo().getTitle() : mTitle;

        CommentsReceiver commentsReceiver = new CommentsReceiverImpl(getContext()) {
            @Override
            public void onLoadMore(CommentGroup commentGroup) {
                loadComments(this, commentGroup.getNextCommentsKey());
            }

            @Override
            public void onStart() {
                if (mBackup != null) {
                    loadBackup(mBackup.second);
                    return;
                }

                loadComments(this, mCommentsKey);
            }

            @Override
            public void onCommentClicked(CommentItem commentItem) {
                if (commentItem.getNestedCommentsKey() == null) {
                    return;
                }

                CommentsReceiver nestedReceiver = new CommentsReceiverImpl(getContext()) {
                    @Override
                    public void onLoadMore(CommentGroup commentGroup) {
                        loadComments(this, commentGroup.getNextCommentsKey());
                    }

                    @Override
                    public void onStart() {
                        loadComments(this, commentItem.getNestedCommentsKey());
                    }
                };

                showDialog(nestedReceiver, title);
            }

            @Override
            public void onFinish(Backup backup) {
                mBackup = new Pair<>(mCommentsKey, backup);
            }
        };

        showDialog(commentsReceiver, title);
    }

    @Override
    public void onChatClicked(boolean enabled) {
        if (mCommentsKey != null && mLiveChatKey == null) {
            openCommentsDialog();
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

        mCommentsAction = mCommentsService.getCommentsObserve(commentsKey)
                .subscribe(
                        receiver::addCommentGroup,
                        error -> {
                            Log.e(TAG, error.getMessage());
                            receiver.addCommentGroup(null); // remove loading message
                        }
                );
    }

    private void showDialog(CommentsReceiver receiver, String title) {
        AppDialogPresenter appDialogPresenter = AppDialogPresenter.instance(getContext());

        appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
        appDialogPresenter.showDialog();
    }
}
