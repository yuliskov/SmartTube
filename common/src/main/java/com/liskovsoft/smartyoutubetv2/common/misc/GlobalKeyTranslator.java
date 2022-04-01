package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.util.HashMap;
import java.util.Map;

public class GlobalKeyTranslator extends KeyTranslator {
    private final Map<Integer, Integer> mKeyMapping = new HashMap<>();
    private final Map<Integer, Runnable> mActionMapping = new HashMap<>();
    private final GeneralData mGeneralData;
    private final Context mContext;

    public GlobalKeyTranslator(Context context) {
        mGeneralData = GeneralData.instance(context);
        mContext = context;
        initKeyMapping();
        initActionMapping();
    }

    private void initKeyMapping() {
        // Fix rare situations with some remotes. E.g. remote doesn't work on search page.
        mKeyMapping.put(KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER); // G20s: keyboard popup fix?
        mKeyMapping.put(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER); // G20s: keyboard popup fix?
        mKeyMapping.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK);
        // Fix for the unknown usb remote controller: https://smartyoutubetv.github.io/#comment-3742343397
        mKeyMapping.put(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK);

        // Navigation in categories
        mKeyMapping.put(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER);

        // Remapping below isn't confirmed to be useful
        mKeyMapping.put(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_UP);
        mKeyMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_DOWN);
        //mKeyMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT);
        //mKeyMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    private void initActionMapping() {
        addSearchAction();
    }

    private void addSearchAction() {
        if (!mGeneralData.isRemapChannelUpToSearchEnabled()) {
            return;
        }

        Runnable searchAction = () -> SearchPresenter.instance(mContext).startSearch(null);

        mActionMapping.put(KeyEvent.KEYCODE_CHANNEL_UP, searchAction);
        mActionMapping.put(KeyEvent.KEYCODE_CHANNEL_DOWN, searchAction);
    }

    @Override
    protected Map<Integer, Integer> getKeyMapping() {
        return mKeyMapping;
    }

    @Override
    protected Map<Integer, Runnable> getActionMapping() {
        return mActionMapping;
    }
}
