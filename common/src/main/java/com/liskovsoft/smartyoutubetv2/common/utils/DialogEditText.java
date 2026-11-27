package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import androidx.appcompat.widget.AppCompatEditText;

public class DialogEditText extends AppCompatEditText {
    private Runnable mOnSelectLongPress;
    private boolean mSelectLongPressed;

    public DialogEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setOnSelectLongPress(Runnable onSelectLongPress) {
        mOnSelectLongPress = onSelectLongPress;
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (mOnSelectLongPress != null && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    mSelectLongPressed = false;
                } else if (event.isLongPress()) {
                    mSelectLongPressed = true;
                }
                if (mSelectLongPressed) {
                    return true;
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP && mSelectLongPressed) {
                mSelectLongPressed = false;
                // Wait for release so the press cannot carry over to the screen behind the dialog.
                if (!event.isCanceled()) {
                    mOnSelectLongPress.run();
                }
                return true;
            }
        }

        return super.onKeyPreIme(keyCode, event);
    }
}
