package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public final class VoiceTranslateAction extends TwoStateAction {
    public VoiceTranslateAction(Context context) {
        super(context, R.id.action_voice_translate, R.drawable.action_voice_translate);
        setLabels(new String[]{context.getString(R.string.vot_enable), context.getString(R.string.vot_disable)});
    }
}
