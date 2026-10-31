package com.liskovsoft.smartyoutubetv2.common.misc;

import android.view.KeyEvent;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public abstract class KeyTranslator {
    private static final String TAG = KeyTranslator.class.getSimpleName();
    private final Map<Integer, Integer> mKeyMapping = new HashMap<>();
    private final Map<Integer, Runnable> mActionMapping = new HashMap<>();
    private boolean mIsChecked;

    /**
     * NOTE: 'sendKey' won't work with Android 13
     */
    public final boolean translateOld(KeyEvent event) {
        boolean handled = false;

        Runnable action = mActionMapping.get(event.getKeyCode());
        if (action != null && checkEvent(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                action.run();
            }
            handled = true;
        }

        if (!handled) {
            Integer newKeyCode = mKeyMapping.get(event.getKeyCode());
            KeyEvent newKeyEvent = translate(event, newKeyCode);
            if (newKeyEvent != event && checkEvent(event)) {
                RxHelper.runAsync(() -> Utils.sendKey(newKeyEvent));
                handled = true;
            }
        }

        return handled;
    }

    public final KeyEvent translate(KeyEvent event) {
        Runnable action = mActionMapping.get(event.getKeyCode());
        if (action != null && checkEvent(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                action.run();
            }
            return null; // handled
        }

        Integer newKeyCode = mKeyMapping.get(event.getKeyCode());
        KeyEvent newKeyEvent = translate(event, newKeyCode);
        if (newKeyEvent != event && checkEvent(event)) {
            return newKeyEvent;
        }

        return event;
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

    private boolean checkEvent(KeyEvent event) {
        // Fix Volume binding when UI hide
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return mIsChecked;
        }

        mIsChecked = (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_DOWN &&
                event.getKeyCode() != KeyEvent.KEYCODE_DPAD_LEFT && event.getKeyCode() != KeyEvent.KEYCODE_DPAD_RIGHT) ||
                (PlaybackPresenter.instance(null).isPlaying() &&
                        !PlaybackPresenter.instance(null).isOverlayShown() &&
                        !AppDialogPresenter.instance(null).isDialogShown());

        return mIsChecked;
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
