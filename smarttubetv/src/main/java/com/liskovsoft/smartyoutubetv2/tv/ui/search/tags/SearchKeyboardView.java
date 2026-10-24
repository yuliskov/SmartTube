package com.liskovsoft.smartyoutubetv2.tv.ui.search.tags;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.ImageViewCompat;

import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * YouTube TV style in-app keyboard: suggestions column + letter grid + SPACE/CLEAR/SEARCH.
 * Edits the given EditText directly, so the existing SearchBar text watcher drives suggestions/results.
 */
public class SearchKeyboardView extends LinearLayout {
    private static final String[] LETTERS = {"ABCDEFG", "HIJKLMN", "OPQRSTU", "VWXYZ-'"};
    private static final String[] SYMBOLS = {"1234567", "890.,?!", "@#$%&*(", ")_+=/:;"};
    private static final int MAX_SUGGESTIONS = 5;
    private static final int DARK = 0xFF0F0F0F;
    private static final int GREY = 0xFF272727;
    private static final int FOCUS = 0xFFF1F1F1;

    public interface Listener {
        void onSearch();
        void onGlobe();
        void onSuggestionClicked(Tag tag);
        void onSuggestionLongClicked(Tag tag);
    }

    private final LinearLayout mSuggestions;
    private final TextView[] mKeys = new TextView[28];
    private final List<View> mTopRow = new ArrayList<>();
    private TextView mToggle;
    private boolean mSymbols;
    private EditText mTarget;
    private Listener mListener;

    public SearchKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);

        mSuggestions = new LinearLayout(context);
        mSuggestions.setOrientation(VERTICAL);
        addView(mSuggestions, new LayoutParams(dp(264), LayoutParams.WRAP_CONTENT));

        LinearLayout kb = new LinearLayout(context);
        kb.setOrientation(VERTICAL);
        LayoutParams kbLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        kbLp.leftMargin = dp(12);
        addView(kb, kbLp);

        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(context);
            for (int c = 0; c < 7; c++) {
                TextView key = text(22, state(rounded(FOCUS, dp(4)), dp(3), dp(3)), 0xFFFFFFFF);
                key.setOnClickListener(v -> type(((TextView) v).getText().toString()));
                mKeys[r * 7 + c] = key;
                row.addView(key, new LayoutParams(dp(33), dp(33)));
            }
            View extra;
            if (r == 0) {
                extra = icon(R.drawable.ic_kb_backspace, v -> backspace());
            } else if (r == 1) {
                extra = mToggle = text(15, keyBg(), 0xFFFFFFFF);
                mToggle.setOnClickListener(v -> setSymbols(!mSymbols));
            } else if (r == 2) {
                extra = icon(R.drawable.ic_kb_globe, v -> { if (mListener != null) mListener.onGlobe(); });
            } else {
                extra = null;
            }
            if (extra != null) {
                LayoutParams lp = new LayoutParams(dp(40), dp(33));
                lp.leftMargin = dp(8);
                row.addView(extra, lp);
            }
            if (r == 0) {
                for (int i = 0; i < row.getChildCount(); i++) mTopRow.add(row.getChildAt(i));
            }
            kb.addView(row);
        }

        LinearLayout bottom = new LinearLayout(context);
        bottom.addView(button("SPACE", v -> type(" ")));
        bottom.addView(button("CLEAR", v -> { if (mTarget != null) mTarget.setText(""); }));
        bottom.addView(button("SEARCH", v -> { if (mListener != null) mListener.onSearch(); }));
        LayoutParams bLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        bLp.leftMargin = dp(11);
        bLp.topMargin = dp(3);
        kb.addView(bottom, bLp);

        mKeys[0].setId(View.generateViewId());
        setSymbols(false);
    }

    public void setTarget(EditText target) {
        mTarget = target;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** Where UP from the top keyboard row goes (the mic). */
    public void setNextFocusUpForTopRow(int id) {
        for (View v : mTopRow) v.setNextFocusUpId(id);
    }

    public View getFirstKey() {
        return mKeys[0];
    }

    public void focusFirstKey() {
        mKeys[0].requestFocus();
    }

    public void setSuggestions(List<Tag> tags, boolean isHistory) {
        boolean hadFocus = mSuggestions.hasFocus();
        mSuggestions.removeAllViews();
        if (tags == null) {
            return;
        }
        for (int i = 0; i < tags.size() && i < MAX_SUGGESTIONS; i++) {
            Tag tag = tags.get(i);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true);
            row.setPadding(dp(14), 0, dp(14), 0);
            row.setBackground(state(rounded(FOCUS, dp(16)), 0, dp(2)));
            row.setTag(tag);
            row.setOnClickListener(v -> { if (mListener != null) mListener.onSuggestionClicked(tag); });
            row.setOnLongClickListener(v -> { if (mListener != null) mListener.onSuggestionLongClicked(tag); return true; });

            ImageView icon = new ImageView(getContext());
            icon.setImageResource(isHistory ? R.drawable.ic_kb_history : R.drawable.ic_kb_search);
            icon.setDuplicateParentStateEnabled(true);
            ImageViewCompat.setImageTintList(icon, textColors(0xFFFFFFFF));
            row.addView(icon, new LayoutParams(dp(19), dp(19)));

            TextView text = new TextView(getContext());
            text.setText(tag.tag);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextColor(textColors(0xFFFFFFFF));
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setDuplicateParentStateEnabled(true);
            LayoutParams tLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1); // fill the row so long text ends with "…"
            tLp.leftMargin = dp(10);
            row.addView(text, tLp);

            mSuggestions.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(33))); // full-width pill like YouTube
        }
        if (hadFocus) {
            focusFirstKey();
        }
    }

    public void removeSuggestion(Tag tag) {
        for (int i = 0; i < mSuggestions.getChildCount(); i++) {
            if (mSuggestions.getChildAt(i).getTag() == tag) {
                mSuggestions.removeViewAt(i);
                return;
            }
        }
    }

    private void setSymbols(boolean symbols) {
        mSymbols = symbols;
        String[] layout = symbols ? SYMBOLS : LETTERS;
        for (int i = 0; i < mKeys.length; i++) {
            mKeys[i].setText(String.valueOf(layout[i / 7].charAt(i % 7)));
        }
        mToggle.setText(symbols ? "ABC" : "&123");
    }

    private void type(String s) {
        if (mTarget != null) {
            mTarget.getText().append(mSymbols || " ".equals(s) ? s : s.toLowerCase());
        }
    }

    private void backspace() {
        if (mTarget != null) {
            Editable e = mTarget.getText();
            if (e.length() > 0) e.delete(e.length() - 1, e.length());
        }
    }

    private TextView text(int sp, Drawable bg, int color) {
        TextView v = new TextView(getContext());
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(textColors(color));
        v.setBackground(bg);
        v.setFocusable(true);
        v.setIncludeFontPadding(false);
        return v;
    }

    private ImageView icon(int res, OnClickListener l) {
        ImageView v = new ImageView(getContext());
        v.setImageResource(res);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setPadding(dp(9), dp(8), dp(9), dp(8));
        ImageViewCompat.setImageTintList(v, textColors(0xFFFFFFFF));
        v.setBackground(keyBg());
        v.setFocusable(true);
        v.setOnClickListener(l);
        return v;
    }

    private TextView button(String label, OnClickListener l) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, rounded(FOCUS, dp(4)));
        bg.addState(new int[] {}, rounded(GREY, dp(4)));
        TextView v = text(15, bg, 0xFFAAAAAA);
        v.setText(label);
        v.setOnClickListener(l);
        LayoutParams lp = new LayoutParams(dp(66), dp(27));
        lp.rightMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private Drawable keyBg() {
        return state(rounded(FOCUS, dp(4)), 0, dp(3));
    }

    private static Drawable state(Drawable focused, int insetH, int insetV) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, new InsetDrawable(focused, insetH, insetV, insetH, insetV));
        return bg;
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static ColorStateList textColors(int normal) {
        return new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}}, new int[] {DARK, normal});
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
