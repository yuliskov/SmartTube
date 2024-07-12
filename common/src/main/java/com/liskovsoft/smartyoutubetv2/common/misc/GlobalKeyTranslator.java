package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.util.Map;

public class GlobalKeyTranslator extends KeyTranslator {
    private final GeneralData mGeneralData;
    private final Context mContext;

    public GlobalKeyTranslator(Context context) {
        mContext = context;
        mGeneralData = GeneralData.instance(context);
    }

    @Override
    protected void initKeyMapping() {
        Map<Integer, Integer> globalKeyMapping = getKeyMapping();

        // Fix rare situations with some remotes. E.g. Shield.
        // NOTE: 'sendKey' won't work with Android 13
        globalKeyMapping.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK);
        // Fix for the unknown usb remote controller: https://smartyoutubetv.github.io/#comment-3742343397
        globalKeyMapping.put(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK);

        // Could cause serious 'OK not working' bug (where Enter key is used as OK)
        // See: KeyHelpers#fixEnterKey
        //globalKeyMapping.put(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER); // G20s fix: show keyboard on textview click
        //globalKeyMapping.put(KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER); // G20s fix: show keyboard on textview click

        // May help on buggy firmwares (where Enter key is used as OK)
        if (!PlaybackPresenter.instance(mContext).isInPipMode()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER);
        } else {
            globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }

        // 4pda users have an issues with btn remapping
        //globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP);
        //globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        //globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT);
        //globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    @Override
    protected void initActionMapping() {
        addSearchAction();
    }

    private void addSearchAction() {
        Runnable searchAction = () -> SearchPresenter.instance(mContext).startSearch(null);

        Map<Integer, Runnable> actionMapping = getActionMapping();

        actionMapping.put(KeyEvent.KEYCODE_AT, searchAction);

        if (mGeneralData.isRemapChannelUpToSearchEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_CHANNEL_UP, searchAction);
            actionMapping.put(KeyEvent.KEYCODE_CHANNEL_DOWN, searchAction);
        }
    }
}
