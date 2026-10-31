package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.DialogPreference;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;

public class CommentsPreference extends DialogPreference {
    private CommentsReceiver mCommentsReceiver;

    public CommentsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public CommentsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public CommentsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommentsPreference(Context context) {
        super(context);
    }

    public void setCommentsReceiver(CommentsReceiver commentsReceiver) {
        mCommentsReceiver = commentsReceiver;
    }

    public CommentsReceiver getCommentsReceiver() {
        return mCommentsReceiver;
    }
}
