package com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard;

import android.content.Context;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Option;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard.IconCardView;

public class IconItemPresenter extends Presenter {
    
    private static int GRID_ITEM_WIDTH = 350;
    private static int GRID_ITEM_HEIGHT = 400;

    public IconItemPresenter() {

    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        IconCardView iconCardView = new IconCardView(parent.getContext());
        return new ViewHolder(iconCardView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (item instanceof Option) {
            Option option = (Option) item;
            IconCardView optionView = (IconCardView) viewHolder.view;
            optionView.setMainImageDimensions(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT);
            optionView.setOptionTitleText(option.title);
            String value = option.value;
            if (value != null) optionView.setOptionValueText(option.value);
            Context context = viewHolder.view.getContext();
            optionView.setOptionIcon(ContextCompat.getDrawable(context, option.iconResource));
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) { }
}