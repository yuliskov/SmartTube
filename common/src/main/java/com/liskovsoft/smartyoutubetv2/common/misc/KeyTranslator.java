package com.liskovsoft.smartyoutubetv2.common.misc;

import android.view.KeyEvent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public abstract class KeyTranslator {
    private static final String TAG = KeyTranslator.class.getSimpleName();
    private final Map<Integer, Integer> mKeyMapping = new HashMap<>();
    private final Map<Integer, Runnable> mActionMapping = new HashMap<>();

    public final boolean translate(KeyEvent event) {
        boolean handled = false;

        Runnable action = mActionMapping.get(event.getKeyCode());
        if (action != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                action.run();
            }
            handled = true;
        }

        if (!handled) {
            Integer newKeyCode = mKeyMapping.get(event.getKeyCode());
            KeyEvent newKeyEvent = translate(event, newKeyCode);
            if (newKeyEvent != event) {
                RxHelper.runAsync(() -> Utils.sendKey(newKeyEvent));
                handled = true;
            }
        }

        return handled;
    }

    private KeyEvent translate(KeyEvent origin, Integer newKeyCode) {
        if (newKeyCode == null) {
            return origin;
        }

        KeyEvent newKey = new KeyEvent(
                origin.getDownTime(),
                origin.getEventTime(),
                origin.getAction(),
                newKeyCode,
                origin.getRepeatCount(),
                origin.getMetaState(),
                origin.getDeviceId(),
                origin.getScanCode(),
                origin.getFlags(),
                origin.getSource()
        );

        Log.d(TAG, "Translating %s to %s", origin, newKey);

        return newKey;
    }

    protected abstract void initKeyMapping();
    protected abstract void initActionMapping();

    protected Map<Integer, Integer> getKeyMapping() {
        return mKeyMapping;
    }

    protected Map<Integer, Runnable> getActionMapping() {
        return mActionMapping;
    }

    public void apply() {
        initKeyMapping();
        initActionMapping();
    }
}
