package com.liskovsoft.smartyoutubetv2.tv.ui.modern.dialogs;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernText;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets.ModernOptionRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings rendered as a page: entries on the start side, the content of the focused entry on the end side.<br/>
 * Lists (radio, checkbox, actions) are edited in place on the end side. Nested dialogs are pushed as new pages.
 */
@SuppressLint("ViewConstructor") // created from code with its presenter, never inflated from a layout
public class ModernDialogPageView extends FrameLayout implements AppDialogView {
    private final Activity mActivity;
    private final AppDialogPresenter mPresenter;
    private final List<Page> mPages = new ArrayList<>();
    private final TextView mTitle;
    private final VerticalGridView mEntries;
    private final VerticalGridView mOptions;
    private final TextView mOptionsTitle;
    private final View mPreview;
    private final TextView mPreviewTitle;
    private final TextView mPreviewSummary;
    private final RowAdapter mEntriesAdapter = new RowAdapter();
    private final RowAdapter mOptionsAdapter = new RowAdapter();
    // Radio lists keep their selection here: the items themselves are never unselected (no callbacks)
    private final Map<OptionCategory, OptionItem> mRadioSelection = new HashMap<>();
    private OptionCategory mShownCategory;
    private boolean mIsTransparent;
    private boolean mIsOverlay;
    private boolean mIsPaused;
    private int mId;

    private static final class Page {
        final CharSequence title;
        final List<OptionCategory> categories;
        // A single expandable category: its options are the entries
        final boolean flat;
        int focused;

        Page(CharSequence title, List<OptionCategory> categories, boolean flat) {
            this.title = title;
            this.categories = categories;
            this.flat = flat;
        }
    }

    private static final class Row {
        CharSequence title;
        CharSequence summary;
        int indicator;
        /**
         * Null if the row isn't a switch, a checkbox or a radio item.
         */
        Boolean checked;
        int role = ModernOptionRow.ROLE_NONE;
        Runnable onClick;
        OptionCategory category;
    }

    public ModernDialogPageView(Activity activity) {
        super(activity);
        mActivity = activity;
        mPresenter = AppDialogPresenter.instance(activity);
        setBackgroundColor(ModernColorScheme.background(activity));
        LayoutInflater.from(activity).inflate(R.layout.modern_dialog_page, this, true);

        mTitle = findViewById(R.id.modern_dialog_page_title);
        mEntries = findViewById(R.id.modern_dialog_page_entries);
        mOptions = findViewById(R.id.modern_dialog_page_options);
        mOptionsTitle = findViewById(R.id.modern_dialog_page_options_title);
        mPreview = findViewById(R.id.modern_dialog_page_preview);
        mPreviewTitle = findViewById(R.id.modern_dialog_page_preview_title);
        mPreviewSummary = findViewById(R.id.modern_dialog_page_preview_summary);

        setupGrid(mEntries, mEntriesAdapter);
        setupGrid(mOptions, mOptionsAdapter);
        mOptions.setFocusable(false);
        mOptions.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    private static void setupGrid(VerticalGridView grid, RowAdapter adapter) {
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        grid.setWindowAlignment(BaseGridView.WINDOW_ALIGN_BOTH_EDGE);
        grid.setWindowAlignmentOffsetPercent(35);
        grid.setVerticalSpacing(grid.getResources().getDimensionPixelSize(R.dimen.modern_dialog_page_row_gap));
    }

    // AppDialogView

    @Override
    public void show(List<OptionCategory> categories, CharSequence title, boolean isExpandable, boolean isTransparent, boolean isOverlay, int id) {
        mIsTransparent = isTransparent;
        mIsOverlay = isOverlay;
        mId = id;

        if (categories == null) {
            categories = Collections.emptyList();
        }

        boolean flat = isExpandable && categories.size() == 1;

        // Same as the side panel: an expandable single button runs at once
        if (flat && categories.get(0).type == OptionCategory.TYPE_SINGLE_BUTTON) {
            if (!categories.get(0).options.isEmpty()) {
                categories.get(0).options.get(0).onSelect(true);
            }
            if (mPages.isEmpty()) {
                finish();
            }
            return;
        }

        if (!mPages.isEmpty()) {
            mPages.get(mPages.size() - 1).focused = mEntries.getSelectedPosition();
        }

        CharSequence pageTitle = flat && categories.get(0).title != null ? categories.get(0).title : title;
        mPages.add(new Page(pageTitle, categories, flat));
        render();
    }

    @Override
    public void finish() {
        mActivity.finish();
    }

    @Override
    public void goBack() {
        if (canGoBack()) {
            mPages.remove(mPages.size() - 1);
            render();
        } else {
            finish();
        }
    }

    @Override
    public void clearBackstack() {
        while (mPages.size() > 1) {
            mPages.remove(0);
        }
    }

    @Override
    public boolean canGoBack() {
        return mPages.size() > 1;
    }

    @Override
    public boolean isShown() {
        return ViewCompat.isAttachedToWindow(this) && getVisibility() == View.VISIBLE;
    }

    @Override
    public boolean isTransparent() {
        return mIsTransparent;
    }

    @Override
    public boolean isOverlay() {
        return mIsOverlay;
    }

    @Override
    public boolean isPaused() {
        return mIsPaused;
    }

    @Override
    public int getViewId() {
        return mId;
    }

    public void setPaused(boolean paused) {
        mIsPaused = paused;
    }

    /**
     * Back from the end side returns to the entries instead of closing the page.
     * @return true if consumed
     */
    public boolean onBackKey(KeyEvent event) {
        if (!mOptions.hasFocus()) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            leaveOptions();
        }

        return true;
    }

    // Rendering

    private Page top() {
        return mPages.isEmpty() ? null : mPages.get(mPages.size() - 1);
    }

    private void render() {
        Page page = top();

        if (page == null) {
            return;
        }

        mTitle.setText(ModernText.sentenceCase(page.title));
        mEntriesAdapter.setRows(createEntries(page));
        mShownCategory = null;
        leaveOptions();

        int position = Math.max(0, Math.min(page.focused, mEntriesAdapter.getItemCount() - 1));
        mEntries.setSelectedPosition(position);
        mEntries.requestFocus();
        showDetails(position);
    }

    private List<Row> createEntries(Page page) {
        List<Row> rows = new ArrayList<>();

        if (page.flat) {
            OptionCategory category = page.categories.get(0);
            rows.addAll(createOptionRows(category, this::refreshEntries));
            return rows;
        }

        for (OptionCategory category : page.categories) {
            if (category.options == null) {
                continue;
            }

            Row row = new Row();
            row.category = category;
            row.title = categoryTitle(category);
            row.summary = summary(category);

            if (category.type == OptionCategory.TYPE_SINGLE_SWITCH && !category.options.isEmpty()) {
                OptionItem item = category.options.get(0);
                row.indicator = item.isSelected() ? R.drawable.modern_ic_toggle_on : R.drawable.modern_ic_toggle_off;
                row.checked = item.isSelected();
                row.role = ModernOptionRow.ROLE_SWITCH;
                row.onClick = () -> {
                    item.onSelect(!item.isSelected());
                    refreshEntries();
                };
            } else if (category.type == OptionCategory.TYPE_SINGLE_BUTTON && !category.options.isEmpty()) {
                OptionItem item = category.options.get(0);
                row.onClick = () -> item.onSelect(true);
            } else if (hasOptionsList(category)) {
                row.indicator = R.drawable.modern_ic_chevron_right;
                row.onClick = this::enterOptions;
            }

            rows.add(row);
        }

        return rows;
    }

    private List<Row> createOptionRows(OptionCategory category, Runnable onChanged) {
        List<Row> rows = new ArrayList<>();

        if (category.options == null) {
            return rows;
        }

        if (category.type == OptionCategory.TYPE_LONG_TEXT) {
            for (OptionItem item : category.options) {
                Row row = new Row();
                row.title = item.getTitle();
                rows.add(row);
            }
            return rows;
        }

        for (OptionItem item : category.options) {
            Row row = new Row();
            row.title = item.getTitle();
            row.summary = item.getDescription();

            switch (category.type) {
                case OptionCategory.TYPE_RADIO_LIST:
                    row.indicator = item == radioSelected(category) ? R.drawable.modern_ic_check : 0;
                    row.checked = item == radioSelected(category);
                    row.role = ModernOptionRow.ROLE_RADIO;
                    row.onClick = () -> {
                        mRadioSelection.put(category, item);
                        item.onSelect(true);
                        onOptionsChanged(onChanged);
                    };
                    break;
                case OptionCategory.TYPE_CHECKBOX_LIST:
                    row.indicator = item.isSelected() ? R.drawable.modern_ic_check_box : R.drawable.modern_ic_check_box_outline_blank;
                    row.checked = item.isSelected();
                    row.role = ModernOptionRow.ROLE_CHECKBOX;
                    row.onClick = () -> {
                        toggle(item);
                        onOptionsChanged(onChanged);
                    };
                    break;
                case OptionCategory.TYPE_SINGLE_SWITCH:
                    row.indicator = item.isSelected() ? R.drawable.modern_ic_toggle_on : R.drawable.modern_ic_toggle_off;
                    row.checked = item.isSelected();
                    row.role = ModernOptionRow.ROLE_SWITCH;
                    row.onClick = () -> {
                        item.onSelect(!item.isSelected());
                        onOptionsChanged(onChanged);
                    };
                    break;
                default: // strings, buttons
                    row.onClick = () -> {
                        toggle(item);
                        onOptionsChanged(onChanged);
                    };
                    break;
            }

            rows.add(row);
        }

        return rows;
    }

    /**
     * Same rules as the side panel checkbox lists: required items warning, radio groups.
     */
    private void toggle(OptionItem item) {
        boolean selected = !item.isSelected();

        if (selected) {
            OptionItem[] requiredItems = item.getRequired();

            if (requiredItems != null) {
                for (OptionItem requiredItem : requiredItems) {
                    if (!requiredItem.isSelected()) {
                        MessageHelpers.showMessage(getContext(), getContext().getString(R.string.require_checked, requiredItem.getTitle()));
                    }
                }
            }

            OptionItem[] radioItems = item.getRadio();

            if (radioItems != null) {
                for (OptionItem radioItem : radioItems) {
                    radioItem.onSelect(false);
                }
            }
        }

        item.onSelect(selected);
    }

    private void onOptionsChanged(Runnable onChanged) {
        // A callback may have pushed or popped a page
        if (!ViewCompat.isAttachedToWindow(this)) {
            return;
        }

        onChanged.run();
    }

    private void refreshEntries() {
        Page page = top();

        if (page == null) {
            return;
        }

        List<Row> rows = createEntries(page);

        if (rows.size() == mEntriesAdapter.getItemCount()) {
            mEntriesAdapter.updateRows(rows);
        } else {
            mEntriesAdapter.setRows(rows);
        }
    }

    private void showDetails(int position) {
        Page page = top();

        if (page == null) {
            return;
        }

        if (page.flat) {
            Row row = mEntriesAdapter.getRow(position);
            showPreview(null, row != null ? row.summary : null);
            return;
        }

        Row row = mEntriesAdapter.getRow(position);
        OptionCategory category = row != null ? row.category : null;

        if (category == mShownCategory && category != null) {
            return;
        }

        mShownCategory = category;

        if (category == null) {
            showPreview(null, null);
        } else if (hasOptionsList(category)) {
            mPreview.setVisibility(View.GONE);
            mOptionsTitle.setVisibility(View.VISIBLE);
            mOptions.setVisibility(View.VISIBLE);
            mOptionsTitle.setText(categoryTitle(category));
            mOptionsAdapter.setRows(createOptionRows(category, this::onCategoryOptionsChanged));
            mOptions.setSelectedPosition(Math.max(0, firstSelected(category)));
        } else {
            showPreview(categoryTitle(category), summary(category));
        }
    }

    private void showPreview(CharSequence title, CharSequence summary) {
        mOptionsTitle.setVisibility(View.GONE);
        mOptions.setVisibility(View.GONE);
        mPreview.setVisibility(title != null || summary != null ? View.VISIBLE : View.GONE);
        mPreviewTitle.setText(title);
        mPreviewTitle.setVisibility(title != null ? View.VISIBLE : View.GONE);
        mPreviewSummary.setText(summary);
        mPreviewSummary.setVisibility(summary != null ? View.VISIBLE : View.GONE);
    }

    private void onCategoryOptionsChanged() {
        if (mShownCategory != null) {
            mOptionsAdapter.updateRows(createOptionRows(mShownCategory, this::onCategoryOptionsChanged));
        }
        refreshEntries();
    }

    private void enterOptions() {
        if (mOptions.getVisibility() != View.VISIBLE || mOptionsAdapter.getItemCount() == 0) {
            return;
        }

        setEntryActivated(true);
        mOptions.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        mOptions.requestFocus();
    }

    private void leaveOptions() {
        boolean hadFocus = mOptions.hasFocus();
        mOptions.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setEntryActivated(false);

        if (hadFocus) {
            mEntries.requestFocus();
        }
    }

    private void setEntryActivated(boolean activated) {
        RecyclerView.ViewHolder holder = mEntries.findViewHolderForAdapterPosition(mEntries.getSelectedPosition());

        if (holder != null) {
            holder.itemView.setActivated(activated);
        }
    }

    private static boolean hasOptionsList(OptionCategory category) {
        return category.type == OptionCategory.TYPE_RADIO_LIST
                || category.type == OptionCategory.TYPE_CHECKBOX_LIST
                || category.type == OptionCategory.TYPE_STRING_LIST
                || category.type == OptionCategory.TYPE_LONG_TEXT;
    }

    private OptionItem radioSelected(OptionCategory category) {
        if (mRadioSelection.containsKey(category)) {
            return mRadioSelection.get(category);
        }

        for (OptionItem item : category.options) {
            if (item.isSelected()) {
                return item;
            }
        }

        return null;
    }

    private int firstSelected(OptionCategory category) {
        if (category.type == OptionCategory.TYPE_RADIO_LIST) {
            return category.options.indexOf(radioSelected(category));
        }

        for (int i = 0; i < category.options.size(); i++) {
            if (category.options.get(i).isSelected()) {
                return i;
            }
        }
        return -1;
    }

    private static CharSequence categoryTitle(OptionCategory category) {
        if (category.title != null) {
            return category.title;
        }
        return category.options != null && !category.options.isEmpty() ? category.options.get(0).getTitle() : null;
    }

    private CharSequence summary(OptionCategory category) {
        if (category.options == null || category.options.isEmpty()) {
            return null;
        }

        switch (category.type) {
            case OptionCategory.TYPE_RADIO_LIST:
                int selected = firstSelected(category);
                return selected != -1 ? category.options.get(selected).getTitle() : null;
            case OptionCategory.TYPE_CHECKBOX_LIST:
                List<CharSequence> titles = new ArrayList<>();
                for (OptionItem item : category.options) {
                    if (item.isSelected()) {
                        titles.add(item.getTitle());
                    }
                }
                return titles.isEmpty() ? getContext().getString(R.string.modern_off) : TextUtils.join(", ", titles);
            case OptionCategory.TYPE_SINGLE_SWITCH:
                return getContext().getString(category.options.get(0).isSelected() ? R.string.modern_on : R.string.modern_off);
            default:
                return null;
        }
    }

    // Rows

    private final class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        private final List<Row> mRows = new ArrayList<>();

        @SuppressLint("NotifyDataSetChanged") // a new page: every row changes
        void setRows(List<Row> rows) {
            mRows.clear();
            mRows.addAll(rows);
            notifyDataSetChanged();
        }

        /**
         * Same items, new state: rebind in place so the focus stays.
         */
        void updateRows(List<Row> rows) {
            mRows.clear();
            mRows.addAll(rows);
            notifyItemRangeChanged(0, rows.size());
        }

        Row getRow(int position) {
            return position >= 0 && position < mRows.size() ? mRows.get(position) : null;
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.modern_dialog_page_row, parent, false), this);
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            holder.bind(mRows.get(position));
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }
    }

    private final class RowHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private final TextView mSummary;
        private final ImageView mIndicator;

        RowHolder(View view, RowAdapter adapter) {
            super(view);
            mTitle = view.findViewById(R.id.modern_dialog_page_row_title);
            mSummary = view.findViewById(R.id.modern_dialog_page_row_summary);
            mIndicator = view.findViewById(R.id.modern_dialog_page_row_indicator);
            ImageViewCompat.setImageTintList(mIndicator, ContextCompat.getColorStateList(view.getContext(), R.color.modern_pill_text));

            view.setOnClickListener(v -> {
                Row row = adapter.getRow(getAdapterPosition());
                if (row != null && row.onClick != null) {
                    row.onClick.run();
                }
            });

            if (adapter == mEntriesAdapter) {
                // Entries show the current value on one line, options show their full description
                mSummary.setMaxLines(1);
                view.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.setActivated(false);
                        showDetails(getAdapterPosition());
                    }
                });
                view.setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            enterOptions();
                        }
                        return true;
                    }
                    return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
                });
            } else {
                view.setOnKeyListener((v, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            leaveOptions();
                        }
                        return true;
                    }
                    return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
                });
            }
        }

        void bind(Row row) {
            ((ModernOptionRow) itemView).setCheckedState(row.role, Boolean.TRUE.equals(row.checked));
            mTitle.setText(row.title);
            mSummary.setText(row.summary);
            mSummary.setVisibility(TextUtils.isEmpty(row.summary) ? View.GONE : View.VISIBLE);
            mIndicator.setImageResource(row.indicator);
            mIndicator.setVisibility(row.indicator != 0 ? View.VISIBLE : View.GONE);
        }
    }
}
