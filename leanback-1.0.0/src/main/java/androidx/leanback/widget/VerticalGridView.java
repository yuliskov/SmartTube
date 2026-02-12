/*
 * Copyright (C) 2014 The Android Open Source Project
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
package androidx.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.leanback.R;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A {@link android.view.ViewGroup} that shows items in a vertically scrolling list. The items
 * come from the {@link RecyclerView.Adapter} associated with this view.
 * <p>
 * {@link RecyclerView.Adapter} can optionally implement {@link FacetProviderAdapter} which
 * provides {@link FacetProvider} for a given view type;  {@link RecyclerView.ViewHolder}
 * can also implement {@link FacetProvider}.  Facet from ViewHolder
 * has a higher priority than the one from FacetProviderAdapter associated with viewType.
 * Supported optional facets are:
 * <ol>
 * <li> {@link ItemAlignmentFacet}
 * When this facet is provided by ViewHolder or FacetProviderAdapter,  it will
 * override the item alignment settings set on VerticalGridView.  This facet also allows multiple
 * alignment positions within one ViewHolder.
 * </li>
 * </ol>
 */
public class VerticalGridView extends BaseGridView {

    public VerticalGridView(Context context) {
        this(context, null);
    }

    public VerticalGridView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VerticalGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLayoutManager.setOrientation(RecyclerView.VERTICAL);
        initAttributes(context, attrs);
    }

    protected void initAttributes(Context context, AttributeSet attrs) {
        initBaseGridViewAttributes(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbVerticalGridView);
        setColumnWidth(a);
        setNumColumns(a.getInt(R.styleable.lbVerticalGridView_numberOfColumns, 1));
        a.recycle();
    }

    void setColumnWidth(TypedArray array) {
        TypedValue typedValue = array.peekValue(R.styleable.lbVerticalGridView_columnWidth);
        if (typedValue != null) {
            int size = array.getLayoutDimension(R.styleable.lbVerticalGridView_columnWidth, 0);
            setColumnWidth(size);
        }
    }

    /**
     * Sets the number of columns.  Defaults to one.
     */
    public void setNumColumns(int numColumns) {
        mLayoutManager.setNumRows(numColumns);
        requestLayout();
    }

    /**
     * Sets the column width.
     *
     * @param width May be {@link android.view.ViewGroup.LayoutParams#WRAP_CONTENT}, or a size
     *              in pixels. If zero, column width will be fixed based on number of columns
     *              and view width.
     */
    public void setColumnWidth(int width) {
        mLayoutManager.setRowHeight(width);
        requestLayout();
    }
}
