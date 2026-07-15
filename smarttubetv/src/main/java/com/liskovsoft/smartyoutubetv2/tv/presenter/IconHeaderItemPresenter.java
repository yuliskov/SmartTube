package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowHeaderPresenter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class IconHeaderItemPresenter extends RowHeaderPresenter {
    private static final String TAG = IconHeaderItemPresenter.class.getSimpleName();
    private float mUnselectedAlpha;
    private final int mResId;
    private final String mIconUrl;
    private Drawable mDefaultIcon;
    private int mUnselectedTextColor;
    private int mSelectedTextColor;

    private static class IconViewHolder extends ViewHolder {
        final ImageView icon;
        final TextView label;
        final GradientDrawable pill;
        // True while this row is the currently open section (e.g. "Home"), independent
        // of whether the sidebar itself currently has keyboard/D-pad focus.
        boolean isActive;

        IconViewHolder(View view, ImageView icon, TextView label, GradientDrawable pill) {
            super(view);
            this.icon = icon;
            this.label = label;
            this.pill = pill;
        }
    }

    public IconHeaderItemPresenter(int resId, String iconUrl) {
        mResId = resId;
        mIconUrl = iconUrl;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup) {
        mUnselectedAlpha = viewGroup.getResources()
                .getFraction(R.fraction.lb_browse_header_unselect_alpha, 1, 1);
        LayoutInflater inflater = (LayoutInflater) viewGroup.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDefaultIcon = new ColorDrawable(ContextCompat.getColor(viewGroup.getContext(), R.color.lb_grey));
        mUnselectedTextColor = ContextCompat.getColor(viewGroup.getContext(), R.color.sidebar_item_text);
        mSelectedTextColor = ContextCompat.getColor(viewGroup.getContext(), R.color.sidebar_item_selected_text);

        View view = inflater.inflate(R.layout.icon_header_item, null);
        view.setAlpha(mUnselectedAlpha); // Initialize icons to be at half-opacity.

        ImageView icon = view.findViewById(R.id.header_icon);
        TextView label = view.findViewById(R.id.header_label);
        View pillView = view.findViewById(R.id.header_pill);

        // Rounded "pill" highlight behind the currently focused sidebar item, YouTube style.
        // Starts fully transparent; onSelectLevelChanged() fades it in.
        GradientDrawable pill = pillView != null && pillView.getBackground() instanceof GradientDrawable
                ? (GradientDrawable) pillView.getBackground().mutate() : null;
        if (pill != null) {
            pill.setAlpha(0);
        }

        return new IconViewHolder(view, icon, label, pill);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        HeaderItem headerItem;

        if (item instanceof PageRow) {
            headerItem = ((PageRow) item).getHeaderItem();
        } else {
            headerItem = ((ListRow) item).getHeaderItem();
        }

        View rootView = viewHolder.view;
        rootView.setFocusable(true);

        ImageView iconView = rootView.findViewById(R.id.header_icon);
        if (iconView != null) {
            if (mIconUrl != null) {
                // Remote icon (e.g. a pinned channel's own avatar): keep its real colors, don't tint it.
                iconView.clearColorFilter();
                Glide.with(rootView.getContext())
                        .load(mIconUrl)
                        .apply(ViewUtil.glideOptions().error(mDefaultIcon))
                        .listener(mErrorListener)
                        .into(iconView);
            } else {
                Drawable icon = mResId > 0 ? ContextCompat.getDrawable(rootView.getContext(), mResId) : mDefaultIcon;
                iconView.setImageDrawable(icon);
            }
        }

        TextView label = rootView.findViewById(R.id.header_label);
        if (label != null) {
            label.setText(headerItem.getName());
        }

        if (viewHolder instanceof IconViewHolder) {
            IconViewHolder iconHolder = (IconViewHolder) viewHolder;
            iconHolder.isActive = isActiveSection(headerItem, rootView.getContext());
            // Rebinding (e.g. after switching sections) doesn't go through
            // onSelectLevelChanged, so re-apply the highlight here too.
            applyHighlight(iconHolder, Math.max(iconHolder.getSelectLevel(), iconHolder.isActive ? 1f : 0f));
        }
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        // NOP
    }

    // TODO: This is a temporary fix. Remove me when leanback onCreateViewHolder no longer sets the
    // mUnselectAlpha, and also assumes the xml inflation will return a RowHeaderView.
    @Override
    protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) {
        float selectLevel = holder.getSelectLevel();

        holder.view.setAlpha(mUnselectedAlpha + selectLevel * (1.0f - mUnselectedAlpha));

        if (!(holder instanceof IconViewHolder)) {
            return;
        }

        IconViewHolder iconHolder = (IconViewHolder) holder;

        // Keep the currently active section highlighted even as focus moves away from it,
        // e.g. into the video grid, like the official app.
        applyHighlight(iconHolder, Math.max(selectLevel, iconHolder.isActive ? 1f : 0f));
    }

    private void applyHighlight(IconViewHolder holder, float level) {
        if (holder.pill != null) {
            holder.pill.setAlpha(Math.round(255 * level));
        }

        int textColor = ColorUtils.blendARGB(mUnselectedTextColor, mSelectedTextColor, level);

        if (holder.label != null) {
            holder.label.setTextColor(textColor);
        }

        // Only tint the built-in monochrome icons. A remote icon (mIconUrl != null, e.g. a
        // pinned channel's avatar) keeps its real colors and shouldn't be flattened to a silhouette.
        if (holder.icon != null) {
            if (mIconUrl == null) {
                holder.icon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            } else {
                holder.icon.clearColorFilter();
            }
        }
    }

    private boolean isActiveSection(HeaderItem headerItem, Context context) {
        if (headerItem == null) {
            return false;
        }

        BrowseSection currentSection = BrowsePresenter.instance(context).getCurrentSection();

        return currentSection != null && currentSection.getId() == (int) headerItem.getId();
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}
