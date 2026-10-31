package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;

public class ChatPreference extends DialogPreference {
    private ChatReceiver mChatReceiver;

    public ChatPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ChatPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ChatPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChatPreference(Context context) {
        super(context);
    }

    public void setChatReceiver(ChatReceiver chatReceiver) {
        mChatReceiver = chatReceiver;
    }

    public ChatReceiver getChatReceiver() {
        return mChatReceiver;
    }
}
