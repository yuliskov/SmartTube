package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod;

import android.os.Bundle;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;
import androidx.leanback.app.PlaybackSupportFragment;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface.SurfacePlaybackFragment;

/**
 *  Every successfully handled event invokes {@link PlaybackSupportFragment#tickle} that makes ui to appear.
 *  Fixing that for keys.
 */
public class EventsOverridePlaybackFragment extends SurfacePlaybackFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Object onTouchInterceptListener = Helpers.getField(this, "mOnTouchInterceptListener");

        if (onTouchInterceptListener != null) {
            Helpers.setField(this, "mOnTouchInterceptListener", (VerticalGridView.OnTouchInterceptListener) this::onInterceptInputEvent);
        }

        Object onKeyInterceptListener = Helpers.getField(this, "mOnKeyInterceptListener");

        if (onKeyInterceptListener != null) {
            Helpers.setField(this, "mOnKeyInterceptListener", (VerticalGridView.OnKeyInterceptListener) this::onInterceptInputEvent);
        }
    }

    boolean onInterceptInputEvent(InputEvent event) {
        final boolean controlsHidden = !isControlsOverlayVisible();
        //if (DEBUG) Log.v(TAG, "onInterceptInputEvent hidden " + controlsHidden + " " + event);
        boolean consumeEvent = false;
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        int keyAction = 0;

        if (event instanceof KeyEvent) {
            keyCode = ((KeyEvent) event).getKeyCode();
            keyAction = ((KeyEvent) event).getAction();
            if (getInputEventHandler() != null) {
                // VideoPlayerGlue handler
                consumeEvent = getInputEventHandler().onKey(getView(), keyCode, (KeyEvent) event);
            }
        }

        if (consumeEvent) {
            return true;
        }

        switch (keyCode) {
            // Confirm key
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            // Navigation key
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Event may be consumed; regardless, if controls are hidden then these keys will
                // bring up the controls.

                // MOD: show ui and apply key immediately
                //if (controlsHidden) {
                //    consumeEvent = true;
                //}

                if (keyAction == KeyEvent.ACTION_DOWN) {
                    tickle();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (isInSeek()) {
                    // when in seek, the SeekUi will handle the BACK.
                    return false;
                }
                // If controls are not hidden, back will be consumed to fade
                // them out (even if the key was consumed by the handler).
                if (!controlsHidden) {
                    consumeEvent = true;

                    if (((KeyEvent) event).getAction() == KeyEvent.ACTION_UP) {
                        hideControlsOverlay(true);
                    }
                }
                break;
            default:
                if (consumeEvent) {
                    if (keyAction == KeyEvent.ACTION_DOWN) {
                        tickle();
                    }
                }
        }
        return consumeEvent;
    }

    private View.OnKeyListener getInputEventHandler() {
        return (View.OnKeyListener) Helpers.getField(this, "mInputEventHandler");
    }

    private boolean isInSeek() {
        Object mInSeek = Helpers.getField(this, "mInSeek");
        return mInSeek != null && (boolean) mInSeek;
    }
}
