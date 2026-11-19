package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;

/**
 * Lets other apps (e.g. button mappers) control the player without bringing an activity to the front.<br/>
 * Example: am broadcast -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.common.misc.PlayerCommandReceiver -a com.liskovsoft.smartyoutubetv2.TOGGLE_SUBTITLES
 */
public class PlayerCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_TOGGLE_SUBTITLES = "com.liskovsoft.smartyoutubetv2.TOGGLE_SUBTITLES";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_TOGGLE_SUBTITLES.equals(intent.getAction())) {
            PlaybackPresenter.instance(context).toggleSubtitles();
        }
    }
}
