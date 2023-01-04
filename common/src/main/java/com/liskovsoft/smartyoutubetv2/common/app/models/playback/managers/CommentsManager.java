package com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers;

import com.liskovsoft.mediaserviceinterfaces.CommentsService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.PlaybackUIController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.managers.SuggestionsLoaderManager.MetadataListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiverImpl;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.youtubeapi.service.YouTubeMediaService;
import io.reactivex.disposables.Disposable;

import java.util.ArrayList;
import java.util.List;

public class CommentsManager extends PlayerEventListenerHelper implements MetadataListener {
    private static final String TAG = CommentsManager.class.getSimpleName();
    private CommentsService mCommentsService;
    private Disposable mCommentsAction;
    private String mLiveChatKey;
    private String mCommentsKey;
    private String mLastCommentsKey;

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

        CommentsReceiver commentsReceiver = new CommentsReceiverImpl() {
            @Override
            public void onLoadMore(String nextCommentsKey) {
                loadComments(this, nextCommentsKey);
            }

            @Override
            public void onCommentClicked(String nestedCommentsKey) {
                if (nestedCommentsKey == null) {
                    return;
                }

                CommentsReceiver nestedReceiver = new CommentsReceiverImpl() {
                    @Override
                    public void onLoadMore(String nextCommentsKey) {
                        loadComments(this, nextCommentsKey);
                    }
                };

                showDialogNested(nestedReceiver);

                loadComments(nestedReceiver, nestedCommentsKey);
            }
        };

        showDialog(commentsReceiver);

        loadComments(commentsReceiver, mCommentsKey);
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
        RxUtils.disposeActions(mCommentsAction);
        mLastCommentsKey = null;
    }

    private void loadComments(CommentsReceiver receiver, String commentsKey) {
        if (Helpers.equals(mLastCommentsKey, commentsKey)) {
            return;
        }

        disposeActions();

        mCommentsAction = mCommentsService.getCommentsObserve(commentsKey)
                .subscribe(
                        receiver::addCommentGroup,
                        error -> {
                            Log.e(TAG, error.getMessage());
                            error.printStackTrace();
                        }
                );

        mLastCommentsKey = commentsKey;
    }

    private void showDialog(CommentsReceiver receiver) {
        AppDialogPresenter appDialogPresenter = AppDialogPresenter.instance(getActivity());
        //appDialogPresenter.clear();
        String title = getController().getVideo().getTitle();

        appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
        appDialogPresenter.showDialog();
    }

    private void showDialogNested(CommentsReceiver receiver) {
        AppDialogPresenter appDialogPresenter = AppDialogPresenter.instance(getActivity());
        appDialogPresenter.clear();
        String title = getController().getVideo().getTitle();

        appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
        appDialogPresenter.showDialog();
    }

    //private void showDialog(CommentsReceiver receiver) {
    //    //VideoMenuPresenter.instance(getActivity()).showMenu(getController().getVideo());
    //
    //    AppDialogPresenter appDialogPresenter = AppDialogPresenter.instance(getActivity());
    //    String title = getController().getVideo().getTitle();
    //
    //    for (int i = 0; i < 4; i++) {
    //        appDialogPresenter.appendSingleButton((UiOptionItem.from(String.valueOf(Helpers.getRandomNumber(1, 100)), optionItem -> showDialogNested(receiver))));
    //    }
    //
    //    //appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
    //    appDialogPresenter.showDialog("Hello world");
    //}

    //private void showDialogNested(CommentsReceiver receiver) {
    //    AppDialogPresenter appDialogPresenter = AppDialogPresenter.instance(getActivity());
    //    appDialogPresenter.clear();
    //    String title = getController().getVideo().getTitle();
    //
    //    appDialogPresenter.appendLongTextCategory(title, UiOptionItem.from("Test content"));
    //
    //    //appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
    //    appDialogPresenter.showDialog("Hello world2");
    //}
}
