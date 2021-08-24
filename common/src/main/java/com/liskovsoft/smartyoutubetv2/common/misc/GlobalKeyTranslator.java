package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.util.HashMap;
import java.util.Map;

public class GlobalKeyTranslator extends KeyTranslator {
    private final GeneralData mGeneralData;
    private final Map<Integer, Integer> mKeyMapping = new HashMap<>();

    public GlobalKeyTranslator(Context context) {
        mGeneralData = GeneralData.instance(context);
        initKeyMapping();
    }

    private void initKeyMapping() {
        if (mGeneralData.isRemapFastForwardToNextEnabled()) {
            mKeyMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT);
            mKeyMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        // Fix rare situations with some remotes. E.g. remote doesn't work on search page.
        mKeyMapping.put(KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER);
        mKeyMapping.put(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER);
        mKeyMapping.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK);
        // Fix for the unknown usb remote controller: https://smartyoutubetv.github.io/#comment-3742343397
        mKeyMapping.put(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK);
    }

    @Override
    protected Map<Integer, Integer> getKeyMapping() {
        return mKeyMapping;
    }
}
