package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.yt.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.yt.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver.Backup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver.Callback;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.preference.LeanbackPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat.ChatItemMessage;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.util.List;

public class CommentsPreferenceDialogFragment extends LeanbackPreferenceDialogFragment {
    private static final String SENDER_ID = CommentsPreferenceDialogFragment.class.getSimpleName();
    private boolean mIsTransparent;
    private CommentsReceiver mCommentsReceiver;
    private CharSequence mDialogTitle;
    private CommentGroup mCurrentGroup;
    private List<ChatItemMessage> mBackupMessages;
    private ChatItemMessage mFocusedMessage;

    private static class CommentsBackup implements Backup {
        public CommentsBackup(List<ChatItemMessage> backupMessages, ChatItemMessage focusedMessage, CommentGroup currentGroup) {
            this.backupMessages = backupMessages;
            this.focusedMessage = focusedMessage;
            this.currentGroup = currentGroup;
        }

        public final List<ChatItemMessage> backupMessages;
        public final ChatItemMessage focusedMessage;
        public final CommentGroup currentGroup;
    }

    public static CommentsPreferenceDialogFragment newInstance(CommentsReceiver commentsReceiver, String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final CommentsPreferenceDialogFragment
                fragment = new CommentsPreferenceDialogFragment();
        fragment.setArguments(args);
        fragment.mCommentsReceiver = commentsReceiver;

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final DialogPreference preference = getPreference();
            mDialogTitle = preference.getDialogTitle();
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.chat_preference_fragment, container,
                false);

        final CharSequence title = mDialogTitle;
        if (!TextUtils.isEmpty(title)) {
            final TextView titleView = (TextView) view.findViewById(R.id.decor_title);
            titleView.setText(title);
        }

        if (mCommentsReceiver == null) {
            return view;
        }

        MessagesList messagesList = (MessagesList) view.findViewById(R.id.messagesList);
        MessagesListAdapter<ChatItemMessage> adapter = new MessagesListAdapter<>(SENDER_ID, (imageView, url, payload) ->
                Glide.with(view.getContext())
                    .load(url)
                    .apply(ViewUtil.glideOptions())
                    .circleCrop() // resize image
                    .into(imageView));
        adapter.setLoadMoreListener((page, totalItemsCount) -> mCommentsReceiver.onLoadMore(mCurrentGroup));
        adapter.setOnMessageViewClickListener((v, message) -> mCommentsReceiver.onCommentClicked(message.getCommentItem()));
        adapter.setOnMessageViewFocusListener((view1, message) -> mFocusedMessage = message);
        messagesList.setAdapter(adapter);
        messagesList.requestFocus(); // hold focus even when there's no messages
        adapter.enableStackFromEnd(true);
        adapter.setLoadingMessage(mCommentsReceiver.getLoadingMessage());

        mCommentsReceiver.setCallback(new Callback() {
            @Override
            public void onCommentGroup(CommentGroup commentGroup) {
                if (commentGroup == null || commentGroup.getComments() == null) {
                    adapter.setLoadingMessage(mCommentsReceiver.getErrorMessage());
                    return;
                }

                for (CommentItem commentItem : commentGroup.getComments()) {
                    if (ChatItemMessage.shouldSplit(commentItem)) {
                        List<ChatItemMessage> split = ChatItemMessage.fromSplit(commentItem);
                        for (ChatItemMessage splitItem : split) {
                            renderMessage(adapter, splitItem);
                        }
                    } else {
                        renderMessage(adapter, ChatItemMessage.from(commentItem));
                    }
                }
                if (adapter.getMessagesCount() == 0) { // No comments under the video
                    adapter.setLoadingMessage(mCommentsReceiver.getErrorMessage());
                }
                if (mCurrentGroup == null || mCurrentGroup.getNextCommentsKey() == null) {
                    adapter.scrollToTop();
                }
                mCurrentGroup = commentGroup;
            }

            @Override
            public void onBackup(Backup backup) {
                mBackupMessages = ((CommentsBackup) backup).backupMessages;
                mFocusedMessage = ((CommentsBackup) backup).focusedMessage;
                mCurrentGroup = ((CommentsBackup) backup).currentGroup;
                adapter.addToEnd(mBackupMessages, false);
                adapter.setFocusedMessage(mFocusedMessage);
                adapter.scrollToPosition(adapter.getMessagePosition(mFocusedMessage));
            }
        });

        if (mBackupMessages == null) {
            mCommentsReceiver.onStart();
        } else {
            adapter.addToEnd(mBackupMessages, false);
            adapter.setFocusedMessage(mFocusedMessage);
        }

        if (mIsTransparent) {
            ViewUtil.enableTransparentDialog(getActivity(), view);
        }

        if (PlayerTweaksData.instance(getActivity()).isChatPlacedLeft()) {
            ViewUtil.enableLeftDialog(getActivity(), container);
        }

        return view;
    }

    private void renderMessage(MessagesListAdapter<ChatItemMessage> adapter, ChatItemMessage message) {
        adapter.addToStart(message, false);

        if (mFocusedMessage == null && IMessage.checkMessage(message)) {
            mFocusedMessage = message;
            adapter.setFocusedMessage(message);
        }
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        backupMessages();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCommentsReceiver != null) {
            mCommentsReceiver.onFinish(new CommentsBackup(mBackupMessages, mFocusedMessage, mCurrentGroup));
        }
    }

    private void backupMessages() {
        MessagesList messagesList = getView().findViewById(R.id.messagesList);

        MessagesListAdapter<ChatItemMessage> adapter = (MessagesListAdapter<ChatItemMessage>) messagesList.getAdapter();

        mBackupMessages = adapter != null ? adapter.getMessages() : null;
    }
}
