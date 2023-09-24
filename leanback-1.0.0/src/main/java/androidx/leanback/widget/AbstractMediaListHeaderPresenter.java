/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.graphics.Color;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.leanback.R;

/**
 * Abstract presenter class for rendering the header for a list of media items in a playlist.
 * The presenter creates a {@link ViewHolder} for the TextView holding the header text.
 * <p>
 *    Subclasses of this class must override {@link
 *    #onBindMediaListHeaderViewHolder(ViewHolder, Object)} in order to bind their header text to
 *    the media list header view.
 * </p>
 * <p>
 * {@link AbstractMediaItemPresenter} can be used in conjunction with this presenter in order to
 * display a playlist with a header view.
 * </p>
 */
public abstract class AbstractMediaListHeaderPresenter extends RowPresenter{

    private final Context mContext;
    private int mBackgroundColor = Color.TRANSPARENT;
    private boolean mBackgroundColorSet;

    /**
     * The ViewHolder for the {@link AbstractMediaListHeaderPresenter}. It references the TextView
     * that places the header text provided by the data binder.
     */
    public static class ViewHolder extends RowPresenter.ViewHolder {

        private final TextView mHeaderView;

        public ViewHolder(View view) {
            super(view);
            mHeaderView = (TextView) view.findViewById(R.id.mediaListHeader);
        }

        /**
         *
         * @return the header {@link TextView} responsible for rendering the playlist header text.
         */
        public TextView getHeaderView() {
            return mHeaderView;
        }
    }

    /**
     * Constructor used for creating an abstract media-list header presenter of a given theme.
     * @param context The context the user of this presenter is running in.
     * @param mThemeResId The resource id of the desired theme used for styling of this presenter.
     */
    public AbstractMediaListHeaderPresenter(Context context, int mThemeResId) {
        mContext = new ContextThemeWrapper(context.getApplicationContext(), mThemeResId);
        setHeaderPresenter(null);
    }

    /**
     * Constructor used for creating an abstract media-list header presenter.
     * The styling for this presenter is extracted from Context of parent in
     * {@link #createRowViewHolder(ViewGroup)}.
     */
    public AbstractMediaListHeaderPresenter() {
        mContext = null;
        setHeaderPresenter(null);
    }

    @Override
    public boolean isUsingDefaultSelectEffect() {
        return false;
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        Context context = (mContext != null) ? mContext : parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.lb_media_list_header,
                parent, false);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
        ViewHolder vh = new ViewHolder(view);
        if (mBackgroundColorSet) {
            vh.view.setBackgroundColor(mBackgroundColor);
        }
        return vh;
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
        onBindMediaListHeaderViewHolder((ViewHolder) vh, item);
    }

    /**
     * Sets the background color for the row views within the playlist.
     * If this is not set, a default color, defaultBrandColor, from theme is used.
     * This defaultBrandColor defaults to android:attr/colorPrimary on v21, if it's specified.
     * @param color The ARGB color used to set as the header text background color.
     */
    public void setBackgroundColor(int color) {
        mBackgroundColorSet = true;
        mBackgroundColor = color;
    }

    /**
     * Binds the playlist header data model provided by the user to the {@link ViewHolder}
     * provided by the {@link AbstractMediaListHeaderPresenter}.
     * The subclasses of this presenter can access and bind the text view corresponding to the
     * header by calling {@link ViewHolder#getHeaderView()}, on the
     * {@link ViewHolder} provided as the argument {@code vh} by this presenter.
     *
     * @param vh The ViewHolder for this {@link AbstractMediaListHeaderPresenter}.
     * @param item The header data object being presented.
     */
    protected abstract void onBindMediaListHeaderViewHolder(ViewHolder vh, Object item);

}
