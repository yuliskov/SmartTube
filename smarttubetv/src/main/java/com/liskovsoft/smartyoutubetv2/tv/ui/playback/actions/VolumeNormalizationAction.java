package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;

/** Quick ON/OFF control for the effective volume normalization state. */
public class VolumeNormalizationAction extends TwoStateAction {
    public VolumeNormalizationAction(Context context) {
        super(context, R.id.action_volume_normalization, R.drawable.action_sound_on);

        String[] labels = new String[2];
        labels[INDEX_OFF] = context.getString(R.string.volume_normalization_enable);
        labels[INDEX_ON] = context.getString(R.string.volume_normalization_disable);
        setLabels(labels);
    }
}
