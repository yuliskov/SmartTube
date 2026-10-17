package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernIcons;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernText;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.dialogs.ModernDialogActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings page: title, list of categories on the start side, preview of the focused category on the end side.<br/>
 * Opening a category shows its full SmartTube options in the side panel.
 */
@SuppressLint("ViewConstructor") // created from code with its presenter, never inflated from a layout
public class ModernSettingsView extends FrameLayout implements ModernSection {
    private final LinearLayout mList;
    private final ImageView mPreviewIcon;
    private final TextView mPreviewTitle;
    private final View mPreview;
    private final List<SettingsItem> mItems = new ArrayList<>();
    private final int mOffsetTop;
    private int mLastIndex;

    public ModernSettingsView(Context context, CharSequence title) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.modern_settings_page, this, true);

        // Content starts below the top bar: compensate the browse content offset
        mOffsetTop = getResources().getDimensionPixelSize(R.dimen.modern_first_shelf_y);
        setTranslationY(-mOffsetTop);

        ((TextView) findViewById(R.id.modern_settings_title)).setText(ModernText.sentenceCase(title));
        mList = findViewById(R.id.modern_settings_list);
        mPreview = findViewById(R.id.modern_settings_preview);
        mPreviewIcon = findViewById(R.id.modern_settings_preview_icon);
        mPreviewTitle = findViewById(R.id.modern_settings_preview_title);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Reach the bottom of the screen despite the translation
        int height = MeasureSpec.getSize(heightMeasureSpec) + mOffsetTop;
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public void update(SettingsGroup group) {
        if (group == null || group.getItems() == null) {
            return;
        }

        boolean hadFocus = mList.hasFocus();
        mItems.clear();
        mItems.addAll(group.getItems());
        mList.removeAllViews();

        for (SettingsItem item : mItems) {
            mList.addView(createRow(item));
        }

        if (hadFocus && mList.getChildCount() > 0) {
            focusContent();
        } else if (!mItems.isEmpty()) {
            showPreview(mItems.get(clampIndex(mLastIndex)));
        }
    }

    private View createRow(SettingsItem item) {
        View row = LayoutInflater.from(getContext()).inflate(R.layout.modern_settings_row, mList, false);
        TextView text = row.findViewById(R.id.modern_settings_row_text);
        text.setText(ModernText.sentenceCase(item.title));
        row.setOnClickListener(v -> {
            if (item.onClick != null) {
                openWithPassword(item);
            }
        });
        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                mLastIndex = mList.indexOfChild(v);
                showPreview(item);
            }
        });
        return row;
    }

    /**
     * Same protection as the classic settings: the settings password is asked before every category.
     */
    private void openWithPassword(SettingsItem item) {
        String password = GeneralData.instance(getContext()).getSettingsPassword();

        if (password == null) {
            open(item);
            return;
        }

        SimpleEditDialog.showPassword(
                getContext(),
                getContext().getString(R.string.enter_settings_password),
                null,
                newValue -> {
                    if (Utils.passwordMatch(password, newValue)) {
                        open(item);
                        return true;
                    }
                    return false;
                });
    }

    private static void open(SettingsItem item) {
        // Categories open as pages, like the reference
        ModernDialogActivity.requestPage();
        item.onClick.run();
    }

    private void showPreview(SettingsItem item) {
        mPreviewTitle.setText(ModernText.sentenceCase(item.title));
        int icon = ModernIcons.forSettings(item.imageResId);
        if (icon > 0) {
            mPreviewIcon.setImageResource(icon);
            mPreviewIcon.setVisibility(View.VISIBLE);
        } else {
            mPreviewIcon.setVisibility(View.GONE);
        }
        mPreview.setAlpha(0.6f);
        mPreview.animate().alpha(1f).setDuration(ModernTheme.ANIM_FAST_MS);
    }

    private int clampIndex(int index) {
        return Math.max(0, Math.min(index, mList.getChildCount() - 1));
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void update(VideoGroup group) {
        // NOP
    }

    @Override
    public void clear() {
        mItems.clear();
        mList.removeAllViews();
    }

    @Override
    public boolean isEmpty() {
        return mItems.isEmpty();
    }

    @Override
    public int getPosition() {
        return mLastIndex;
    }

    @Override
    public void setPosition(int index) {
        mLastIndex = index;
        if (index >= 0 && index < mList.getChildCount()) {
            mList.getChildAt(index).requestFocus();
        }
    }

    @Override
    public void selectItem(Video item) {
        // NOP
    }

    @Override
    public boolean focusContent() {
        // Back to the last opened category, like the reference
        return mList.getChildCount() > 0 && mList.getChildAt(clampIndex(mLastIndex)).requestFocus();
    }
}
