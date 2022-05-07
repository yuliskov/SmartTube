package com.liskovsoft.smartyoutubetv2.common.misc;

import android.view.KeyEvent;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxUtils;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.Map;

public abstract class KeyTranslator {
    private static final String TAG = KeyTranslator.class.getSimpleName();
    private KeyEvent mHandledEvent;

    public final boolean translate(KeyEvent event) {
        Map<Integer, Integer> keyMapping = getKeyMapping();
        Integer newKeyCode = null;
        boolean handled = false;

        if (keyMapping != null) {
            newKeyCode = keyMapping.get(event.getKeyCode());
        }

        Map<Integer, Runnable> actionMapping = getActionMapping();

        if (actionMapping != null) {
            Runnable action = actionMapping.get(event.getKeyCode());
            if (action != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    action.run();
                }
                newKeyCode = KeyEvent.KEYCODE_UNKNOWN; // disable original mapping
            }
        }

        KeyEvent newKeyEvent = translate(event, newKeyCode);

        if (newKeyEvent != event) {
            handled = true;

            checkAndFixUnpairedEvent(newKeyEvent);

            RxUtils.runAsync(() -> Utils.sendKey(newKeyEvent));
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

    private void checkAndFixUnpairedEvent(KeyEvent newKeyEvent) {
        if (newKeyEvent == null) {
            return;
        }

        if (newKeyEvent.getAction() == KeyEvent.ACTION_UP &&
                (mHandledEvent == null || mHandledEvent.getKeyCode() != newKeyEvent.getKeyCode())) {
            KeyEvent keyDownEvent = new KeyEvent(
                    newKeyEvent.getDownTime(),
                    newKeyEvent.getEventTime(),
                    KeyEvent.ACTION_DOWN,
                    newKeyEvent.getKeyCode(),
                    newKeyEvent.getRepeatCount(),
                    newKeyEvent.getMetaState(),
                    newKeyEvent.getDeviceId(),
                    newKeyEvent.getScanCode(),
                    newKeyEvent.getFlags(),
                    newKeyEvent.getSource()
            );

            RxUtils.runAsync(() -> Utils.sendKey(keyDownEvent));
        }

        mHandledEvent = newKeyEvent;
    }

    protected abstract Map<Integer, Integer> getKeyMapping();

    protected abstract Map<Integer, Runnable> getActionMapping();
}
