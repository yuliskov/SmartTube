package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.text.TextUtils.TruncateAt;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class SettingsItemPresenter extends Presenter {
    private final Fragment mainFragment;
    private int mDefaultBackgroundColor;
    private int mDefaultTextColor;
    private int mSelectedBackgroundColor;
    private int mSelectedTextColor;

    public SettingsItemPresenter(Fragment mainFragment) {
        this.mainFragment = mainFragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mDefaultBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_background_dark);
        mDefaultTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_text);
        mSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_background_white);
        mSelectedTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_text_grey);

        View container = LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_item, null);
        container.setBackgroundColor(mDefaultBackgroundColor);

        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(mDefaultBackgroundColor);
        textView.setTextColor(mDefaultTextColor);

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int backgroundColor = hasFocus ? mSelectedBackgroundColor : mDefaultBackgroundColor;
            int textColor = hasFocus ? mSelectedTextColor : mDefaultTextColor;
            
            textView.setBackgroundColor(backgroundColor);
            textView.setTextColor(textColor);

            if (hasFocus) {
                enableMarquee(textView);
            } else {
                disableMarquee(textView);
            }
        });

        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);

        textView.setText(settingsItem.title);

        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    private void disableMarquee(TextView... textViews) {
        if (textViews == null || textViews.length == 0) {
            return;
        }

        for (TextView textView : textViews) {
            textView.setEllipsize(TruncateAt.END);
        }
    }

    private void enableMarquee(TextView... textViews) {
        if (textViews == null || textViews.length == 0) {
            return;
        }

        for (TextView textView : textViews) {
            textView.setEllipsize(TruncateAt.MARQUEE);
            textView.setMarqueeRepeatLimit(-1);
            textView.setHorizontallyScrolling(true);
        }
    }
}
