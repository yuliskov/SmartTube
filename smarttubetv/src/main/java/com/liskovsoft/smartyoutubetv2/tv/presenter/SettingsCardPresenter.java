package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class SettingsCardPresenter extends Presenter {
    private int mDefaultTextColor;
    private int mSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();
        mDefaultTextColor = ContextCompat.getColor(context, R.color.card_default_text);
        mSelectedTextColor = ContextCompat.getColor(context, R.color.card_selected_text_grey);

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, null);
        container.setBackground(ContextCompat.getDrawable(context, R.drawable.glass_card_bg));
        TextView textView = container.findViewById(R.id.settings_title);
        textView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        textView.setTextColor(mDefaultTextColor);
        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(ContextCompat.getDrawable(v.getContext(),
                    hasFocus ? R.drawable.glass_focus_bg : R.drawable.glass_card_bg));
            textView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            textView.setTextColor(hasFocus ? mSelectedTextColor : mDefaultTextColor);
            if (Build.VERSION.SDK_INT >= 21) {
                float density = v.getResources().getDisplayMetrics().density;
                v.setElevation(hasFocus ? 7f * density : 0f);
            }
            if (hasFocus) ViewUtil.enableMarquee(textView);
            else ViewUtil.disableMarquee(textView);
        });
        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;
        TextView textView = viewHolder.view.findViewById(R.id.settings_title);
        textView.setText(settingsItem.title);
        ImageView imageView = viewHolder.view.findViewById(R.id.settings_image);
        if (settingsItem.imageResId > 0) {
            Context context = viewHolder.view.getContext();
            imageView.setImageDrawable(ContextCompat.getDrawable(context, settingsItem.imageResId));
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setImageDrawable(null);
            imageView.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }
}
