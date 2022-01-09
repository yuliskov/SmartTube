package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;

import java.util.Map;

public class MediaKeyTranslator extends GlobalKeyTranslator {
    public MediaKeyTranslator(Context context) {
        super(context);
        initKeyMapping();
    }

    private void initKeyMapping() {
        Map<Integer, Integer> globalKeyMapping = getKeyMapping();

        globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER);
        // Remapping below doesn't work. Why?
        //globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT);
        //globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT);
    }
}
