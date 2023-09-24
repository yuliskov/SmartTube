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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.RestrictTo;
import androidx.leanback.R;

/**
 * RowHeaderPresenter provides a default presentation for {@link HeaderItem} using a
 * {@link RowHeaderView} and optionally a TextView for description. If a subclass creates its own
 * view, the subclass must also override {@link #onCreateViewHolder(ViewGroup)},
 * {@link #onSelectLevelChanged(ViewHolder)}.
 */
public class RowHeaderPresenter extends Presenter {

    private final int mLayoutResourceId;
    private final Paint mFontMeasurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mNullItemVisibilityGone;
    private final boolean mAnimateSelect;

    /**
     * Creates default RowHeaderPresenter using a title view and a description view.
     * @see ViewHolder#RowHeaderPresenter.ViewHolder(View)
     */
    public RowHeaderPresenter() {
        this(R.layout.lb_row_header);
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public RowHeaderPresenter(int layoutResourceId) {
        this(layoutResourceId, true);
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public RowHeaderPresenter(int layoutResourceId, boolean animateSelect) {
        mLayoutResourceId = layoutResourceId;
        mAnimateSelect = animateSelect;
    }

    /**
     * Optionally sets the view visibility to {@link View#GONE} when bound to null.
     */
    public void setNullItemVisibilityGone(boolean nullItemVisibilityGone) {
        mNullItemVisibilityGone = nullItemVisibilityGone;
    }

    /**
     * Returns true if the view visibility is set to {@link View#GONE} when bound to null.
     */
    public boolean isNullItemVisibilityGone() {
        return mNullItemVisibilityGone;
    }

    /**
     * A ViewHolder for the RowHeaderPresenter.
     */
    public static class ViewHolder extends Presenter.ViewHolder {
        float mSelectLevel;
        int mOriginalTextColor;
        float mUnselectAlpha;
        RowHeaderView mTitleView;
        TextView mDescriptionView;

        /**
         * Creating a new ViewHolder that supports title and description.
         * @param view Root of Views.
         */
        public ViewHolder(View view) {
            super(view);
            mTitleView = (RowHeaderView)view.findViewById(R.id.row_header);
            mDescriptionView = (TextView)view.findViewById(R.id.row_header_description);
            initColors();
        }

        /**
         * Uses a single {@link RowHeaderView} for creating a new ViewHolder.
         * @param view The single RowHeaderView.
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public ViewHolder(RowHeaderView view) {
            super(view);
            mTitleView = view;
            initColors();
        }

        void initColors() {
            if (mTitleView != null) {
                mOriginalTextColor = mTitleView.getCurrentTextColor();
            }

            mUnselectAlpha = view.getResources().getFraction(
                    R.fraction.lb_browse_header_unselect_alpha, 1, 1);
        }

        public final float getSelectLevel() {
            return mSelectLevel;
        }
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        View root = LayoutInflater.from(parent.getContext())
                .inflate(mLayoutResourceId, parent, false);

        ViewHolder viewHolder = new ViewHolder(root);
        if (mAnimateSelect) {
            setSelectLevel(viewHolder, 0);
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        HeaderItem headerItem = item == null ? null : ((Row) item).getHeaderItem();
        RowHeaderPresenter.ViewHolder vh = (RowHeaderPresenter.ViewHolder)viewHolder;
        if (headerItem == null) {
            if (vh.mTitleView != null) {
                vh.mTitleView.setText(null);
            }
            if (vh.mDescriptionView != null) {
                vh.mDescriptionView.setText(null);
            }

            viewHolder.view.setContentDescription(null);
            if (mNullItemVisibilityGone) {
                viewHolder.view.setVisibility(View.GONE);
            }
        } else {
            if (vh.mTitleView != null) {
                vh.mTitleView.setText(headerItem.getName());
            }
            if (vh.mDescriptionView != null) {
                if (TextUtils.isEmpty(headerItem.getDescription())) {
                    vh.mDescriptionView.setVisibility(View.GONE);
                } else {
                    vh.mDescriptionView.setVisibility(View.VISIBLE);
                }
                vh.mDescriptionView.setText(headerItem.getDescription());
            }
            viewHolder.view.setContentDescription(headerItem.getContentDescription());
            viewHolder.view.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        RowHeaderPresenter.ViewHolder vh = (ViewHolder)viewHolder;
        if (vh.mTitleView != null) {
            vh.mTitleView.setText(null);
        }
        if (vh.mDescriptionView != null) {
            vh.mDescriptionView.setText(null);
        }

        if (mAnimateSelect) {
            setSelectLevel((ViewHolder) viewHolder, 0);
        }
    }

    /**
     * Sets the select level.
     */
    public final void setSelectLevel(ViewHolder holder, float selectLevel) {
        holder.mSelectLevel = selectLevel;
        onSelectLevelChanged(holder);
    }

    /**
     * Called when the select level changes.  The default implementation sets the alpha on the view.
     */
    protected void onSelectLevelChanged(ViewHolder holder) {
        if (mAnimateSelect) {
            holder.view.setAlpha(holder.mUnselectAlpha + holder.mSelectLevel
                    * (1f - holder.mUnselectAlpha));
        }
    }

    /**
     * Returns the space (distance in pixels) below the baseline of the
     * text view, if one exists; otherwise, returns 0.
     */
    public int getSpaceUnderBaseline(ViewHolder holder) {
        int space = holder.view.getPaddingBottom();
        if (holder.view instanceof TextView) {
            space += (int) getFontDescent((TextView) holder.view, mFontMeasurePaint);
        }
        return space;
    }

    @SuppressWarnings("ReferenceEquality")
    protected static float getFontDescent(TextView textView, Paint fontMeasurePaint) {
        if (fontMeasurePaint.getTextSize() != textView.getTextSize()) {
            fontMeasurePaint.setTextSize(textView.getTextSize());
        }
        if (fontMeasurePaint.getTypeface() != textView.getTypeface()) {
            fontMeasurePaint.setTypeface(textView.getTypeface());
        }
        return fontMeasurePaint.descent();
    }
}
