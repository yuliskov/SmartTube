package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.search;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Embedded on-screen QWERTY keyboard for the search screen, so typing doesn't rely on the
 * system IME popup. Character keys are plain TextViews styled via {@code R.style.OnScreenKeyboardKey};
 * whatever text a key displays is the character it produces, so no per-key wiring is needed -
 * only the control keys (space/backspace/clear/search) have explicit ids.
 */
public class OnScreenKeyboardView extends LinearLayout {

    public interface OnScreenKeyboardListener {
        void onCharacter(char character);
        void onSpace();
        void onBackspace();
        void onClear();
        void onSubmit();
    }

    private OnScreenKeyboardListener mListener;

    public OnScreenKeyboardView(Context context) {
        super(context);
        init();
    }

    public OnScreenKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OnScreenKeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        inflate(getContext(), R.layout.view_onscreen_keyboard, this);

        attachCharacterKeyListeners(this);

        setupControlKey(R.id.key_space, v -> {
            if (mListener != null) {
                mListener.onSpace();
            }
        });
        setupControlKey(R.id.key_backspace, v -> {
            if (mListener != null) {
                mListener.onBackspace();
            }
        });
        setupControlKey(R.id.key_clear, v -> {
            if (mListener != null) {
                mListener.onClear();
            }
        });
        setupControlKey(R.id.key_search, v -> {
            if (mListener != null) {
                mListener.onSubmit();
            }
        });
    }

    public void setOnScreenKeyboardListener(OnScreenKeyboardListener listener) {
        mListener = listener;
    }

    private void setupControlKey(int id, OnClickListener listener) {
        View key = findViewById(id);

        if (key != null) {
            key.setOnClickListener(listener);
        }
    }

    /**
     * Character keys aren't given individual ids (there'd be 36 of them); instead every
     * TextView that isn't one of the control keys is treated as a character key, and its
     * own displayed text is the character it types.
     */
    private void attachCharacterKeyListeners(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);

            if (child instanceof ViewGroup) {
                attachCharacterKeyListeners((ViewGroup) child);
            } else if (child instanceof TextView && isCharacterKey(child)) {
                TextView key = (TextView) child;
                key.setOnClickListener(v -> {
                    CharSequence text = key.getText();
                    if (mListener != null && text != null && text.length() > 0) {
                        mListener.onCharacter(text.charAt(0));
                    }
                });
            }
        }
    }

    private boolean isCharacterKey(View view) {
        int id = view.getId();

        return id != R.id.key_space && id != R.id.key_backspace
                && id != R.id.key_clear && id != R.id.key_search;
    }
}
