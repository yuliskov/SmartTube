package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

public class LiveChatView extends com.stfalcon.chatkit.messages.MessagesList {
    private static final String SENDER_ID = LiveChatView.class.getSimpleName();
    private ChatReceiver mChatReceiver;
    private MessagesListAdapter<ChatItemMessage> mAdapter;

    public LiveChatView(Context context) {
        super(context);
    }

    public LiveChatView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LiveChatView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        setFocusable(false);
    }

    public void setChatReceiver(ChatReceiver chatReceiver) {
        if (mChatReceiver != null) {
            mChatReceiver.setCallback(null);
            mChatReceiver = null;
        }

        if (mAdapter != null) {
            mAdapter.clear();
        }

        mChatReceiver = chatReceiver;

        if (mChatReceiver == null) {
            setVisibility(View.GONE);
            return;
        }

        alignChat();

        setVisibility(View.VISIBLE);

        if (mAdapter == null) {
            mAdapter = new MessagesListAdapter<>(SENDER_ID, (imageView, url, payload) ->
                    Glide.with(getContext())
                            .load(url)
                            .apply(ViewUtil.glideOptions())
                            .circleCrop() // resize image
                            .into(imageView));
            mAdapter.setMaxItemsCount(20);
            setAdapter(mAdapter);
        }

        mChatReceiver.setCallback(chatItem -> mAdapter.addToStart(ChatItemMessage.from(chatItem), true));
    }

    private void alignChat() {
        int gravity = Gravity.RIGHT;

        if (PlayerTweaksData.instance(getContext()).isChatPlacedLeft()) {
            gravity = Gravity.LEFT;
        }

        ((FrameLayout.LayoutParams)((ViewGroup) getParent()).getLayoutParams()).gravity = gravity;
    }
}
