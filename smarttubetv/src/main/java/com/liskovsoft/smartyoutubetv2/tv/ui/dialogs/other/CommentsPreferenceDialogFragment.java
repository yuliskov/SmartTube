package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackPreferenceDialogFragment;
import androidx.preference.DialogPreference;
import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat.ChatItemMessage;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.lang.ref.WeakReference;

public class CommentsPreferenceDialogFragment extends LeanbackPreferenceDialogFragment {
    private static final String SENDER_ID = CommentsPreferenceDialogFragment.class.getSimpleName();
    private boolean mIsTransparent;
    private CommentsReceiver mCommentsReceiver;
    private CharSequence mDialogTitle;
    private String mNextCommentsKey;
    private WeakReference<View> mFocusedView = new WeakReference<>(null);

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
            mFocusedView = new WeakReference<>(v);
            mCommentsReceiver.onCommentClicked(message.getNestedCommentsKey());
        });
        messagesList.setAdapter(adapter);
        adapter.enableStackFromEnd(true);
        adapter.setLoadingMessage(mCommentsReceiver.getLoadingMessage(), false);

        mCommentsReceiver.setCallback(commentGroup -> {
            for (CommentItem commentItem : commentGroup.getComments()) {
                adapter.addToStart(ChatItemMessage.from(commentItem), false);
            }
            if (mNextCommentsKey == null) {
                adapter.scrollToTop();
            }
            mNextCommentsKey = commentGroup.getNextCommentsKey();
        });

        mCommentsReceiver.onStart();

        if (mIsTransparent) {
            ViewUtil.enableTransparentDialog(getActivity(), view);
        }

        return view;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    public void restoreFocus() {
        if (mFocusedView.get() != null) {
            mFocusedView.get().requestFocus();
        }
    }
}
