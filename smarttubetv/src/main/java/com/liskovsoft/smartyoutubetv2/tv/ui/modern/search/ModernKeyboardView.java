package com.liskovsoft.smartyoutubetv2.tv.ui.modern.search;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * On-screen keyboard of the search page.
 */
public class ModernKeyboardView extends FrameLayout {
    private Listener mListener;
    private boolean mSymbols;

    public interface Listener {
        void onKey(KeyboardModel.Key key);
    }

    public ModernKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        build();
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void toggleSymbols() {
        mSymbols = !mSymbols;
        int focusedIndex = getFocusedChild() != null ? indexOfChild(getFocusedChild()) : -1;
        build();
        if (focusedIndex >= 0 && focusedIndex < getChildCount()) {
            getChildAt(focusedIndex).requestFocus();
        }
    }

    public boolean focusFirstKey() {
        return getChildCount() > 0 && getChildAt(0).requestFocus();
    }

    private void build() {
        removeAllViews();
        Resources res = getResources();
        int pitch = res.getDimensionPixelSize(R.dimen.modern_key_pitch);
        int size = res.getDimensionPixelSize(R.dimen.modern_key_size);

        for (KeyboardModel.Key key : KeyboardModel.grid(mSymbols)) {
            View view;
            int width = size;

            switch (key.type) {
                case KeyboardModel.TYPE_BACKSPACE:
                    view = iconKey(R.drawable.modern_ic_backspace);
                    break;
                case KeyboardModel.TYPE_SYSTEM_KEYBOARD:
                    view = iconKey(R.drawable.modern_ic_language);
                    break;
                case KeyboardModel.TYPE_MODE:
                    view = textKey(getContext().getString(mSymbols ? R.string.modern_key_letters : R.string.modern_key_symbols), R.dimen.modern_key_action_text, true);
                    width = res.getDimensionPixelSize(R.dimen.modern_key_mode_width);
                    break;
                default:
                    view = textKey(KeyboardModel.label(key), R.dimen.modern_key_text, false);
                    break;
            }

            view.setBackgroundResource(R.drawable.modern_key_bg);
            add(view, key, key.column * pitch, key.row * pitch, width, size);
        }

        int top = 4 * pitch;
        int[] lefts = {R.dimen.modern_key_space_left, R.dimen.modern_key_clear_left, R.dimen.modern_key_search_left};
        int[] widths = {R.dimen.modern_key_space_width, R.dimen.modern_key_clear_width, R.dimen.modern_key_space_width};
        int[] labels = {R.string.modern_key_space, R.string.modern_key_clear, R.string.settings_search};
        int index = 0;

        for (KeyboardModel.Key key : KeyboardModel.actions()) {
            TextView view = textKey(getContext().getString(labels[index]), R.dimen.modern_key_action_text, true);
            view.setBackgroundResource(R.drawable.modern_key_action_bg);
            add(view, key, res.getDimensionPixelSize(lefts[index]), top, res.getDimensionPixelSize(widths[index]), size);
            index++;
        }
    }

    /**
     * Vertical moves between the character grid and the action row go to the closest key by x.
     */
    @Override
    public View focusSearch(View focused, int direction) {
        if ((direction == FOCUS_DOWN || direction == FOCUS_UP) && focused != null && focused.getParent() == this
                && focused.getTag() instanceof KeyboardModel.Key) {
            KeyboardModel.Key key = (KeyboardModel.Key) focused.getTag();
            int targetRow = direction == FOCUS_DOWN ? key.row + 1 : key.row - 1;

            if (targetRow == 4 || key.row == 4 && targetRow == 3) {
                View best = null;
                float center = focused.getX() + focused.getWidth() / 2f;

                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    KeyboardModel.Key other = (KeyboardModel.Key) child.getTag();
                    if (other.row == targetRow && (best == null ||
                            Math.abs(child.getX() + child.getWidth() / 2f - center) < Math.abs(best.getX() + best.getWidth() / 2f - center))) {
                        best = child;
                    }
                }

                if (best != null) {
                    return best;
                }
            }
        }

        return super.focusSearch(focused, direction);
    }

    private void add(View view, KeyboardModel.Key key, int x, int y, int width, int height) {
        view.setTag(key);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onKey(key);
            }
        });
        LayoutParams params = new LayoutParams(width, height);
        params.leftMargin = x;
        params.topMargin = y;
        addView(view, params);
    }

    private TextView textKey(String text, int sizeResId, boolean bold) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(sizeResId));
        view.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.modern_key_text));
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private ImageView iconKey(int iconResId) {
        ImageView view = new ImageView(getContext());
        int padding = (getResources().getDimensionPixelSize(R.dimen.modern_key_size) - getResources().getDimensionPixelSize(R.dimen.modern_key_icon)) / 2;
        view.setPadding(padding, padding, padding, padding);
        view.setImageResource(iconResId);
        androidx.core.widget.ImageViewCompat.setImageTintList(view, ContextCompat.getColorStateList(getContext(), R.color.modern_key_text));
        view.setDuplicateParentStateEnabled(false);
        return view;
    }
}
