/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package androidx.leanback.widget.picker;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.leanback.R;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Picker is a widget showing multiple customized {@link PickerColumn}s. The PickerColumns are
 * initialized in {@link #setColumns(List)}. Call {@link #setColumnAt(int, PickerColumn)} if the
 * column value range or labels change. Call {@link #setColumnValue(int, int, boolean)} to update
 * the current value of PickerColumn.
 * <p>
 * Picker has two states and will change height:
 * <li>{@link #isActivated()} is true: Picker shows typically three items vertically (see
 * {@link #getActivatedVisibleItemCount()}}. Columns other than {@link #getSelectedColumn()} still
 * shows one item if the Picker is focused. On a touch screen device, the Picker will not get focus
 * so it always show three items on all columns. On a non-touch device (a TV), the Picker will show
 * three items only on currently activated column. If the Picker has focus, it will intercept DPAD
 * directions and select activated column.
 * <li>{@link #isActivated()} is false: Picker shows one item vertically (see
 * {@link #getVisibleItemCount()}) on all columns. The size of Picker shrinks.
 */
public class Picker extends FrameLayout {

    public interface PickerValueListener {
        public void onValueChanged(Picker picker, int column);
    }

    private ViewGroup mRootView;
    private ViewGroup mPickerView;
    final List<VerticalGridView> mColumnViews = new ArrayList<VerticalGridView>();
    ArrayList<PickerColumn> mColumns;

    private float mUnfocusedAlpha;
    private float mFocusedAlpha;
    private float mVisibleColumnAlpha;
    private float mInvisibleColumnAlpha;
    private int mAlphaAnimDuration;
    private Interpolator mDecelerateInterpolator;
    private Interpolator mAccelerateInterpolator;
    private ArrayList<PickerValueListener> mListeners;
    private float mVisibleItemsActivated = 3;
    private float mVisibleItems = 1;
    private int mSelectedColumn = 0;

    private List<CharSequence> mSeparators = new ArrayList<>();
    private int mPickerItemLayoutId = R.layout.lb_picker_item;
    private int mPickerItemTextViewId = 0;

    /**
     * Gets separator string between columns.
     *
     * @return The separator that will be populated between all the Picker columns.
     * @deprecated Use {@link #getSeparators()}
     */
    public final CharSequence getSeparator() {
        return mSeparators.get(0);
    }

    /**
     * Sets separator String between Picker columns.
     *
     * @param separator Separator String between Picker columns.
     */
    public final void setSeparator(CharSequence separator) {
        setSeparators(Arrays.asList(separator));
    }

    /**
     * Returns the list of separators that will be populated between the picker column fields.
     *
     * @return The list of separators populated between the picker column fields.
     */
    public final List<CharSequence> getSeparators() {
        return mSeparators;
    }

    /**
     * Sets the list of separators that will be populated between the Picker columns. The
     * number of the separators should be either 1 indicating the same separator used between all
     * the columns fields (and nothing will be placed before the first and after the last column),
     * or must be one unit larger than the number of columns passed to {@link #setColumns(List)}.
     * In the latter case, the list of separators corresponds to the positions before the first
     * column all the way to the position after the last column.
     * An empty string for a given position indicates no separators needs to be placed for that
     * position, otherwise a TextView with the given String will be created and placed there.
     *
     * @param separators The list of separators to be populated between the Picker columns.
     */
    public final void setSeparators(List<CharSequence> separators) {
        mSeparators.clear();
        mSeparators.addAll(separators);
    }

    /**
     * Classes extending {@link Picker} can choose to override this method to
     * supply the {@link Picker}'s item's layout id
     */
    public final int getPickerItemLayoutId() {
        return mPickerItemLayoutId;
    }

    /**
     * Returns the {@link Picker}'s item's {@link TextView}'s id from within the
     * layout provided by {@link Picker#getPickerItemLayoutId()} or 0 if the
     * layout provided by {@link Picker#getPickerItemLayoutId()} is a {link
     * TextView}.
     */
    public final int getPickerItemTextViewId() {
        return mPickerItemTextViewId;
    }

    /**
     * Sets the {@link Picker}'s item's {@link TextView}'s id from within the
     * layout provided by {@link Picker#getPickerItemLayoutId()} or 0 if the
     * layout provided by {@link Picker#getPickerItemLayoutId()} is a {link
     * TextView}.
     *
     * @param textViewId View id of TextView inside a Picker item, or 0 if the Picker item is a
     *                   TextView.
     */
    public final void setPickerItemTextViewId(int textViewId) {
        mPickerItemTextViewId = textViewId;
    }

    /**
     * Creates a Picker widget.
     */
    public Picker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Make it enabled and clickable to receive Click event.
        setEnabled(true);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        mFocusedAlpha = 1f; //getFloat(R.dimen.list_item_selected_title_text_alpha);
        mUnfocusedAlpha = 1f; //getFloat(R.dimen.list_item_unselected_text_alpha);
        mVisibleColumnAlpha = 0.5f; //getFloat(R.dimen.picker_item_visible_column_item_alpha);
        mInvisibleColumnAlpha = 0f; //getFloat(R.dimen.picker_item_invisible_column_item_alpha);

        mAlphaAnimDuration =
                200; // mContext.getResources().getInteger(R.integer.dialog_animation_duration);

        mDecelerateInterpolator = new DecelerateInterpolator(2.5F);
        mAccelerateInterpolator = new AccelerateInterpolator(2.5F);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        mRootView = (ViewGroup) inflater.inflate(R.layout.lb_picker, this, true);
        mPickerView = (ViewGroup) mRootView.findViewById(R.id.picker);
    }

    /**
     * Get nth PickerColumn.
     *
     * @param colIndex Index of PickerColumn.
     * @return PickerColumn at colIndex or null if {@link #setColumns(List)} is not called yet.
     */
    public PickerColumn getColumnAt(int colIndex) {
        if (mColumns == null) {
            return null;
        }
        return mColumns.get(colIndex);
    }

    /**
     * Get number of PickerColumns.
     *
     * @return Number of PickerColumns or 0 if {@link #setColumns(List)} is not called yet.
     */
    public int getColumnsCount() {
        if (mColumns == null) {
            return 0;
        }
        return mColumns.size();
    }

    /**
     * Set columns and create Views.
     *
     * @param columns The actual focusable columns of a picker which are scrollable if the field
     *                takes more than one value (e.g. for a DatePicker, day, month, and year fields
     *                and for TimePicker, hour, minute, and am/pm fields form the columns).
     */
    public void setColumns(List<PickerColumn> columns) {
        if (mSeparators.size() == 0) {
            throw new IllegalStateException("Separators size is: " + mSeparators.size()
                    + ". At least one separator must be provided");
        } else if (mSeparators.size() == 1) {
            CharSequence separator = mSeparators.get(0);
            mSeparators.clear();
            mSeparators.add("");
            for (int i = 0; i < columns.size() - 1; i++) {
                mSeparators.add(separator);
            }
            mSeparators.add("");
        } else {
            if (mSeparators.size() != (columns.size() + 1)) {
                throw new IllegalStateException("Separators size: " + mSeparators.size() + " must"
                        + "equal the size of columns: " + columns.size() + " + 1");
            }
        }

        mColumnViews.clear();
        mPickerView.removeAllViews();
        mColumns = new ArrayList<PickerColumn>(columns);
        if (mSelectedColumn > mColumns.size() - 1) {
            mSelectedColumn = mColumns.size() - 1;
        }
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int totalCol = getColumnsCount();

        if (!TextUtils.isEmpty(mSeparators.get(0))) {
            TextView separator = (TextView) inflater.inflate(
                    R.layout.lb_picker_separator, mPickerView, false);
            separator.setText(mSeparators.get(0));
            mPickerView.addView(separator);
        }
        for (int i = 0; i < totalCol; i++) {
            final int colIndex = i;
            final VerticalGridView columnView = (VerticalGridView) inflater.inflate(
                    R.layout.lb_picker_column, mPickerView, false);
            // we don't want VerticalGridView to receive focus.
            updateColumnSize(columnView);
            // always center aligned, not aligning selected item on top/bottom edge.
            columnView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
            // Width is dynamic, so has fixed size is false.
            columnView.setHasFixedSize(false);
            columnView.setFocusable(isActivated());
            // Setting cache size to zero in order to rebind item views when picker widget becomes
            // activated. Rebinding is necessary to update the alphas when the columns are expanded
            // as a result of the picker getting activated, otherwise the cached views with the
            // wrong alphas could be laid out.
            columnView.setItemViewCacheSize(0);

            mColumnViews.add(columnView);
            // add view to root
            mPickerView.addView(columnView);

            if (!TextUtils.isEmpty(mSeparators.get(i + 1))) {
                // add a separator if not the last element
                TextView separator = (TextView) inflater.inflate(
                        R.layout.lb_picker_separator, mPickerView, false);
                separator.setText(mSeparators.get(i + 1));
                mPickerView.addView(separator);
            }

            columnView.setAdapter(new PickerScrollArrayAdapter(getContext(),
                    getPickerItemLayoutId(), getPickerItemTextViewId(), colIndex));
            columnView.setOnChildViewHolderSelectedListener(mColumnChangeListener);
        }
    }

    /**
     * When column labels change or column range changes, call this function to re-populate the
     * selection list.  Note this function cannot be called from RecyclerView layout/scroll pass.
     *
     * @param columnIndex Index of column to update.
     * @param column      New column to update.
     */
    public void setColumnAt(int columnIndex, PickerColumn column) {
        mColumns.set(columnIndex, column);
        VerticalGridView columnView = mColumnViews.get(columnIndex);
        PickerScrollArrayAdapter adapter = (PickerScrollArrayAdapter) columnView.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        columnView.setSelectedPosition(column.getCurrentValue() - column.getMinValue());
    }

    /**
     * Manually set current value of a column.  The function will update UI and notify listeners.
     *
     * @param columnIndex  Index of column to update.
     * @param value        New value of the column.
     * @param runAnimation True to scroll to the value or false otherwise.
     */
    public void setColumnValue(int columnIndex, int value, boolean runAnimation) {
        PickerColumn column = mColumns.get(columnIndex);
        if (column.getCurrentValue() != value) {
            column.setCurrentValue(value);
            notifyValueChanged(columnIndex);
            VerticalGridView columnView = mColumnViews.get(columnIndex);
            if (columnView != null) {
                int position = value - mColumns.get(columnIndex).getMinValue();
                if (runAnimation) {
                    columnView.setSelectedPositionSmooth(position);
                } else {
                    columnView.setSelectedPosition(position);
                }
            }
        }
    }

    private void notifyValueChanged(int columnIndex) {
        if (mListeners != null) {
            for (int i = mListeners.size() - 1; i >= 0; i--) {
                mListeners.get(i).onValueChanged(this, columnIndex);
            }
        }
    }

    /**
     * Register a callback to be invoked when the picker's value has changed.
     *
     * @param listener The callback to ad
     */
    public void addOnValueChangedListener(PickerValueListener listener) {
        if (mListeners == null) {
            mListeners = new ArrayList<Picker.PickerValueListener>();
        }
        mListeners.add(listener);
    }

    /**
     * Remove a previously installed value changed callback
     *
     * @param listener The callback to remove.
     */
    public void removeOnValueChangedListener(PickerValueListener listener) {
        if (mListeners != null) {
            mListeners.remove(listener);
        }
    }

    void updateColumnAlpha(int colIndex, boolean animate) {
        VerticalGridView column = mColumnViews.get(colIndex);

        int selected = column.getSelectedPosition();
        View item;

        for (int i = 0; i < column.getAdapter().getItemCount(); i++) {
            item = column.getLayoutManager().findViewByPosition(i);
            if (item != null) {
                setOrAnimateAlpha(item, (selected == i), colIndex, animate);
            }
        }
    }

    void setOrAnimateAlpha(View view, boolean selected, int colIndex,
            boolean animate) {
        boolean columnShownAsActivated = colIndex == mSelectedColumn || !hasFocus();
        if (selected) {
            // set alpha for main item (selected) in the column
            if (columnShownAsActivated) {
                setOrAnimateAlpha(view, animate, mFocusedAlpha, -1, mDecelerateInterpolator);
            } else {
                setOrAnimateAlpha(view, animate, mUnfocusedAlpha, -1, mDecelerateInterpolator);
            }
        } else {
            // set alpha for remaining items in the column
            if (columnShownAsActivated) {
                setOrAnimateAlpha(view, animate, mVisibleColumnAlpha, -1, mDecelerateInterpolator);
            } else {
                setOrAnimateAlpha(view, animate, mInvisibleColumnAlpha, -1,
                        mDecelerateInterpolator);
            }
        }
    }

    private void setOrAnimateAlpha(View view, boolean animate, float destAlpha, float startAlpha,
            Interpolator interpolator) {
        view.animate().cancel();
        if (!animate) {
            view.setAlpha(destAlpha);
        } else {
            if (startAlpha >= 0.0f) {
                // set a start alpha
                view.setAlpha(startAlpha);
            }
            view.animate().alpha(destAlpha)
                    .setDuration(mAlphaAnimDuration).setInterpolator(interpolator)
                    .start();
        }
    }

    /**
     * Classes extending {@link Picker} can override this function to supply the
     * behavior when a list has been scrolled.  Subclass may call {@link #setColumnValue(int, int,
     * boolean)} and or {@link #setColumnAt(int, PickerColumn)}.  Subclass should not directly call
     * {@link PickerColumn#setCurrentValue(int)} which does not update internal state or notify
     * listeners.
     *
     * @param columnIndex index of which column was changed.
     * @param newValue    A new value desired to be set on the column.
     */
    public void onColumnValueChanged(int columnIndex, int newValue) {
        PickerColumn column = mColumns.get(columnIndex);
        if (column.getCurrentValue() != newValue) {
            column.setCurrentValue(newValue);
            notifyValueChanged(columnIndex);
        }
    }

    private float getFloat(int resourceId) {
        TypedValue buffer = new TypedValue();
        getContext().getResources().getValue(resourceId, buffer, true);
        return buffer.getFloat();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        ViewHolder(View v, TextView textView) {
            super(v);
            this.textView = textView;
        }
    }

    class PickerScrollArrayAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final int mResource;
        private final int mColIndex;
        private final int mTextViewResourceId;
        private PickerColumn mData;

        PickerScrollArrayAdapter(Context context, int resource, int textViewResourceId,
                int colIndex) {
            mResource = resource;
            mColIndex = colIndex;
            mTextViewResourceId = textViewResourceId;
            mData = mColumns.get(mColIndex);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View v = inflater.inflate(mResource, parent, false);
            TextView textView;
            if (mTextViewResourceId != 0) {
                textView = (TextView) v.findViewById(mTextViewResourceId);
            } else {
                textView = (TextView) v;
            }
            ViewHolder vh = new ViewHolder(v, textView);
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            if (holder.textView != null && mData != null) {
                holder.textView.setText(mData.getLabelFor(mData.getMinValue() + position));
            }
            setOrAnimateAlpha(holder.itemView,
                    (mColumnViews.get(mColIndex).getSelectedPosition() == position),
                    mColIndex, false);
        }

        @Override
        public void onViewAttachedToWindow(ViewHolder holder) {
            holder.itemView.setFocusable(isActivated());
        }

        @Override
        public int getItemCount() {
            return mData == null ? 0 : mData.getCount();
        }
    }

    private final OnChildViewHolderSelectedListener mColumnChangeListener = new
            OnChildViewHolderSelectedListener() {

                @Override
                public void onChildViewHolderSelected(RecyclerView parent,
                        RecyclerView.ViewHolder child,
                        int position, int subposition) {
                    PickerScrollArrayAdapter pickerScrollArrayAdapter =
                            (PickerScrollArrayAdapter) parent
                                    .getAdapter();

                    int colIndex = mColumnViews.indexOf(parent);
                    updateColumnAlpha(colIndex, true);
                    if (child != null) {
                        int newValue = mColumns.get(colIndex).getMinValue() + position;
                        onColumnValueChanged(colIndex, newValue);
                    }
                }

            };

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (isActivated()) {
            final int keyCode = event.getKeyCode();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        performClick();
                    }
                    break;
                default:
                    return super.dispatchKeyEvent(event);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        int column = getSelectedColumn();
        if (column < mColumnViews.size()) {
            return mColumnViews.get(column).requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    /**
     * Classes extending {@link Picker} can choose to override this method to
     * supply the {@link Picker}'s column's single item height in pixels.
     */
    protected int getPickerItemHeightPixels() {
        return getContext().getResources().getDimensionPixelSize(R.dimen.picker_item_height);
    }

    private void updateColumnSize() {
        for (int i = 0; i < getColumnsCount(); i++) {
            updateColumnSize(mColumnViews.get(i));
        }
    }

    private void updateColumnSize(VerticalGridView columnView) {
        ViewGroup.LayoutParams lp = columnView.getLayoutParams();
        float itemCount = isActivated() ? getActivatedVisibleItemCount() : getVisibleItemCount();
        lp.height = (int) (getPickerItemHeightPixels() * itemCount
                + columnView.getVerticalSpacing() * (itemCount - 1));
        columnView.setLayoutParams(lp);
    }

    private void updateItemFocusable() {
        final boolean activated = isActivated();
        for (int i = 0; i < getColumnsCount(); i++) {
            VerticalGridView grid = mColumnViews.get(i);
            for (int j = 0; j < grid.getChildCount(); j++) {
                View view = grid.getChildAt(j);
                view.setFocusable(activated);
            }
        }
    }

    /**
     * Returns number of visible items showing in a column when it's activated.  The default value
     * is 3.
     *
     * @return Number of visible items showing in a column when it's activated.
     */
    public float getActivatedVisibleItemCount() {
        return mVisibleItemsActivated;
    }

    /**
     * Changes number of visible items showing in a column when it's activated.  The default value
     * is 3.
     *
     * @param visiblePickerItems Number of visible items showing in a column when it's activated.
     */
    public void setActivatedVisibleItemCount(float visiblePickerItems) {
        if (visiblePickerItems <= 0) {
            throw new IllegalArgumentException();
        }
        if (mVisibleItemsActivated != visiblePickerItems) {
            mVisibleItemsActivated = visiblePickerItems;
            if (isActivated()) {
                updateColumnSize();
            }
        }
    }

    /**
     * Returns number of visible items showing in a column when it's not activated.  The default
     * value is 1.
     *
     * @return Number of visible items showing in a column when it's not activated.
     */
    public float getVisibleItemCount() {
        return 1;
    }

    /**
     * Changes number of visible items showing in a column when it's not activated.  The default
     * value is 1.
     *
     * @param pickerItems Number of visible items showing in a column when it's not activated.
     */
    public void setVisibleItemCount(float pickerItems) {
        if (pickerItems <= 0) {
            throw new IllegalArgumentException();
        }
        if (mVisibleItems != pickerItems) {
            mVisibleItems = pickerItems;
            if (!isActivated()) {
                updateColumnSize();
            }
        }
    }

    @Override
    public void setActivated(boolean activated) {
        if (activated == isActivated()) {
            super.setActivated(activated);
            return;
        }
        super.setActivated(activated);
        boolean hadFocus = hasFocus();
        int column = getSelectedColumn();
        // To avoid temporary focus loss in both the following cases, we set Picker's flag to
        // FOCUS_BEFORE_DESCENDANTS first, and then back to FOCUS_AFTER_DESCENDANTS once done with
        // the focus logic.
        // 1. When changing from activated to deactivated, the Picker should grab the focus
        // back if it's focusable. However, calling requestFocus on it will transfer the focus down
        // to its children if it's flag is FOCUS_AFTER_DESCENDANTS.
        // 2. When changing from deactivated to activated, while setting focusable flags on each
        // column VerticalGridView, that column will call requestFocus (regardless of which column
        // is the selected column) since the currently focused view (Picker) has a flag of
        // FOCUS_AFTER_DESCENDANTS.
        setDescendantFocusability(FOCUS_BEFORE_DESCENDANTS);
        if (!activated && hadFocus && isFocusable()) {
            // When picker widget that originally had focus is deactivated and it is focusable, we
            // should not pass the focus down to the children. The Picker itself will capture focus.
            requestFocus();
        }

        for (int i = 0; i < getColumnsCount(); i++) {
            mColumnViews.get(i).setFocusable(activated);
        }

        updateColumnSize();
        updateItemFocusable();
        if (activated && hadFocus && (column >= 0)) {
            mColumnViews.get(column).requestFocus();
        }
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        for (int i = 0; i < mColumnViews.size(); i++) {
            if (mColumnViews.get(i).hasFocus()) {
                setSelectedColumn(i);
            }
        }
    }

    /**
     * Change current selected column.  Picker shows multiple items on selected column if Picker has
     * focus.  Picker shows multiple items on all column if Picker has no focus (e.g. a Touchscreen
     * screen).
     *
     * @param columnIndex Index of column to activate.
     */
    public void setSelectedColumn(int columnIndex) {
        if (mSelectedColumn != columnIndex) {
            mSelectedColumn = columnIndex;
            for (int i = 0; i < mColumnViews.size(); i++) {
                updateColumnAlpha(i, true);
            }
        }
    }

    /**
     * Get current activated column index.
     *
     * @return Current activated column index.
     */
    public int getSelectedColumn() {
        return mSelectedColumn;
    }

}
