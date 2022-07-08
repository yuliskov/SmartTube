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
import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.util.Date;

public class ChatPreferenceDialogFragment extends LeanbackPreferenceDialogFragment {
    private static final String SENDER_ID = ChatPreferenceDialogFragment.class.getSimpleName();
    private boolean mIsTransparent;
    private ChatReceiver mChatReceiver;
    private CharSequence mDialogTitle;

    public static ChatPreferenceDialogFragment newInstance(ChatReceiver chatReceiver, String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final ChatPreferenceDialogFragment
                fragment = new ChatPreferenceDialogFragment();
        fragment.setArguments(args);
        fragment.mChatReceiver = chatReceiver;

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
        MessagesListAdapter<Message> adapter = new MessagesListAdapter<>(SENDER_ID, (imageView, url, payload) ->
                Glide.with(view.getContext())
                    .load(url)
                    .apply(ViewUtil.glideOptions())
                    .circleCrop() // resize image
                    .into(imageView));
        messagesList.setAdapter(adapter);

        if (mChatReceiver != null) {
            mChatReceiver.setCallback(chatItem -> {
                if (chatItem.getId() != null) {
                    adapter.addToStart(Message.from(chatItem), true);
                }
            });
        }

        if (mIsTransparent) {
            ViewUtil.enableTransparentDialog(getActivity(), view);
        }

        return view;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    private static class Message implements IMessage {
        private String mId;
        private String mText;
        private Author mAuthor;
        private Date mCreatedAt;

        public static Message from(ChatItem chatItem) {
            Message message = new Message();
            message.mId = chatItem.getId();
            message.mText = String.format("%s: %s", chatItem.getAuthorName(), chatItem.getMessage());
            message.mAuthor = Author.from(chatItem);
            message.mCreatedAt = new Date();

            return message;
        }

        @Override
        public String getId() {
            return mId;
        }

        @Override
        public String getText() {
            return mText;
        }

        @Override
        public Author getUser() {
            return mAuthor;
        }

        @Override
        public Date getCreatedAt() {
            return mCreatedAt;
        }
    }

    private static class Author implements IUser {
        private String mId;
        private String mName;
        private String mAvatar;

        public static Author from(ChatItem chatItem) {
            Author author = new Author();
            author.mAvatar = chatItem.getAuthorPhoto();
            author.mName = chatItem.getAuthorName();
            author.mId = chatItem.getAuthorName();

            return author;
        }

        @Override
        public String getId() {
            return mId;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public String getAvatar() {
            return mAvatar;
        }
    }
}
