package com.liskovsoft.smartyoutubetv2.common.misc;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

public class PlayerKeyTranslator extends KeyTranslator {
    @Override
    protected Map<Integer, Integer> getKeyMapping() {
        Map<Integer, Integer> keyMapping = new HashMap<>();
        keyMapping.put(KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK);
        // Fix for the unknown usb remote controller: https://smartyoutubetv.github.io/#comment-3742343397
        keyMapping.put(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK);

        return keyMapping;
    }
}
