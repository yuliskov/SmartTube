package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.disposables.Disposable;

public class CommentsManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = CommentsManager.class.getSimpleName();
    private CommentsService mCommentsService;
    private Disposable mCommentsAction;
    private String mLiveChatKey;
    private String mCommentsKey;

    @Override
    public void onInitDone() {
        mCommentsService = YouTubeMediaService.instance().getCommentsService();
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mLiveChatKey = metadata != null && metadata.getLiveChatKey() != null ? metadata.getLiveChatKey() : null;
        mCommentsKey = metadata != null && metadata.getCommentsKey() != null ? metadata.getCommentsKey() : null;

        if (mCommentsKey != null && mLiveChatKey == null) {
            getController().setChatButtonState(PlaybackUIController.BUTTON_STATE_OFF);
        }
    }

    private void openCommentsDialog() {
        disposeActions();

        if (mCommentsKey == null) {
            return;
        }

        CommentsReceiver commentsReceiver = new CommentsReceiver() {
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
            public void onGroupEnd(CommentGroup commentGroup) {

            }

            @Override
            public void onCommentClicked(CommentItem commentItem) {

            }
        };

        AppDialogPresenter appDialogPresenter = AppDialogPresenter.instance(getActivity());
        String title = getController().getVideo().getTitle();
        appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, commentsReceiver));
        appDialogPresenter.showDialog();

        mCommentsAction = mCommentsService.getCommentsObserve(mCommentsKey)
                .subscribe(
                        commentsReceiver::addCommentGroup,
                        error -> {
                            Log.e(TAG, error.getMessage());
                            error.printStackTrace();
                        }
                );
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
    }

    @Override
    public void onFinish() {
        disposeActions();
    }

    private void disposeActions() {
        if (RxUtils.isAnyActionRunning(mCommentsAction)) {
            RxUtils.disposeActions(mCommentsAction);
            getController().setChatReceiver(null);
        }
    }
}
