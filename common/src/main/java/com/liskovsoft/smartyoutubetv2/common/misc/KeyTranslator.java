package com.liskovsoft.smartyoutubetv2.common.misc;

import android.view.KeyEvent;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.Map;

public abstract class KeyTranslator {
    private static final String TAG = KeyTranslator.class.getSimpleName();
    private static final int UNDEFINED = -1;

    public KeyEvent translateKey(KeyEvent event) {
        Log.d(TAG, "Received key: " + event);

        int toKeyCode = UNDEFINED;

        Map<Integer, Integer> keyMapping = getKeyMapping();
        Integer outKey = keyMapping.get(event.getKeyCode());

        if (outKey != null) {
            toKeyCode = outKey;
        }

        return translate(event, toKeyCode);
    }

    private KeyEvent translate(KeyEvent origin, int toKeyCode) {
        if (toKeyCode == UNDEFINED) {
            Log.d(TAG, "No need to translate: " + origin);
            return origin;
        }

        KeyEvent newKey = new KeyEvent(
                origin.getDownTime(),
                origin.getEventTime(),
                origin.getAction(),
                toKeyCode,
                origin.getRepeatCount(),
                origin.getMetaState(),
                origin.getDeviceId(),
                origin.getScanCode(),
                origin.getFlags(),
                origin.getSource()
        );

        Log.d(TAG, "Translating to " + newKey);

        return newKey;
    }

    protected abstract Map<Integer, Integer> getKeyMapping();
}
