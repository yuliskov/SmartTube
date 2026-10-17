package com.liskovsoft.smartyoutubetv2.tv.ui.modern.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Row of a channels list (side list pages): round avatar and name.
 */
class ModernChannelItemPresenter extends Presenter {
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.modern_subscription_channel, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        Video channel = (Video) item;
        View view = viewHolder.view;
        ((TextView) view.findViewById(R.id.modern_subs_name)).setText(channel.getTitle());
        ImageView avatar = view.findViewById(R.id.modern_subs_avatar);
        ImageViewCompat.setImageTintList(avatar, null);
        Glide.with(view.getContext()).load(channel.getCardImageUrl()).transform(new CircleCrop()).into(avatar);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        Glide.with(viewHolder.view.getContext()).clear((View) viewHolder.view.findViewById(R.id.modern_subs_avatar));
    }

    /**
     * Row with an icon and a label instead of a channel (e.g. "All", the filter field).
     */
    static class IconItemPresenter extends Presenter {
        private final int mIcon;

        IconItemPresenter(int icon) {
            mIcon = icon;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.modern_subscription_channel, parent, false);
            ImageView icon = view.findViewById(R.id.modern_subs_avatar);
            icon.setImageResource(mIcon);
            ImageViewCompat.setImageTintList(icon, ContextCompat.getColorStateList(parent.getContext(), R.color.modern_pill_text));
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            // Label set by the list
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }
}
