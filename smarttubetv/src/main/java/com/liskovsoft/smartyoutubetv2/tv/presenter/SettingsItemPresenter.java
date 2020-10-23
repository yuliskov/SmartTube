package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.res.Resources;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class SettingsItemPresenter extends Presenter {
    private final Fragment mainFragment;

    public SettingsItemPresenter(Fragment mainFragment) {
        this.mainFragment = mainFragment;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        TextView view = new TextView(parent.getContext());

        Resources res = parent.getResources();
        int width = res.getDimensionPixelSize(R.dimen.grid_item_width);
        int height = res.getDimensionPixelSize(R.dimen.grid_item_height);

        view.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setBackgroundColor(ContextCompat.getColor(parent.getContext(),
                R.color.card_default_background_dark));
        view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        ((TextView) viewHolder.view).setText(((SettingsItem) item).title);
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
