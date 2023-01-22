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
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.preference.LeanbackPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat.ChatItemMessage;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.util.List;

public class CommentsPreferenceDialogFragment extends LeanbackPreferenceDialogFragment {
    private static final String SENDER_ID = CommentsPreferenceDialogFragment.class.getSimpleName();
    private boolean mIsTransparent;
    private CommentsReceiver mCommentsReceiver;
    private CharSequence mDialogTitle;
    private String mNextCommentsKey;
    private List<ChatItemMessage> mBackupMessages;
    private ChatItemMessage mFocusedMessage;

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

        MessagesList messagesList = (MessagesList) view.findViewById(R.id.messagesList);
        MessagesListAdapter<ChatItemMessage> adapter = new MessagesListAdapter<>(SENDER_ID, (imageView, url, payload) ->
                Glide.with(view.getContext())
                    .load(url)
                    .apply(ViewUtil.glideOptions())
                    .circleCrop() // resize image
                    .into(imageView));
        adapter.setLoadMoreListener((page, totalItemsCount) -> mCommentsReceiver.onLoadMore(mNextCommentsKey));
        adapter.setOnMessageViewClickListener((v, message) -> {
            mFocusedMessage = message;
            mCommentsReceiver.onCommentClicked(message.getNestedCommentsKey());
        });
        messagesList.setAdapter(adapter);
        messagesList.requestFocus(); // hold focus even when there's no messages
        adapter.enableStackFromEnd(true);
        adapter.setLoadingMessage(mCommentsReceiver.getLoadingMessage(), false);

        mCommentsReceiver.setCallback(commentGroup -> {
            if (commentGroup == null || commentGroup.getComments() == null) {
                adapter.removeLoadingMessageIfNeeded();
                return;
            }

            for (CommentItem commentItem : commentGroup.getComments()) {
                ChatItemMessage message = ChatItemMessage.from(commentItem);

                adapter.addToStart(message, false);

                if (mFocusedMessage == null) {
                    mFocusedMessage = message;
                    adapter.setFocusedMessage(message);
                }
            }
            if (mNextCommentsKey == null) {
                adapter.scrollToTop();
            }
            mNextCommentsKey = commentGroup.getNextCommentsKey();
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

        return view;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        backupMessages();
    }

    private void backupMessages() {
        MessagesList messagesList = getView().findViewById(R.id.messagesList);

        mBackupMessages = ((MessagesListAdapter<ChatItemMessage>) messagesList.getAdapter()).getMessages();
    }
}
