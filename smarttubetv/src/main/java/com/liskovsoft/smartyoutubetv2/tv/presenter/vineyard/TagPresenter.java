package com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard;

import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.User;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard.TagCardView;

public class TagPresenter extends Presenter {
    private static int sDefaultBackgroundColor;
    private static int sDefaultTextColor;
    private static int sSelectedBackgroundColor;
    private static int sSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        sDefaultBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_background_dark);
        sDefaultTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_text);
        sSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_background_white);
        sSelectedTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_text_grey);

        TagCardView cardView = new TagCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                updateCardTextColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardBackgroundColor(cardView, false);
        updateCardTextColor(cardView, false);
        return new ViewHolder(cardView);
    }

    private static void updateCardBackgroundColor(TagCardView view, boolean selected) {
        view.setBackgroundColor(selected ? sSelectedBackgroundColor : sDefaultBackgroundColor);
    }

    private static void updateCardTextColor(TagCardView view, boolean selected) {
        view.setTextColor(selected ? sSelectedTextColor : sDefaultTextColor);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        if (item instanceof Tag) {
            Tag post = (Tag) item;
            TagCardView cardView = (TagCardView) viewHolder.view;

            if (post.tag != null) {
                cardView.setCardText(post.tag);
                //cardView.setCardIcon(R.drawable.ic_tag);
            }
        } else if (item instanceof User) {
            User post = (User) item;
            TagCardView cardView = (TagCardView) viewHolder.view;

            if (post.username != null) {
                cardView.setCardText(post.username);
                cardView.setCardIcon(R.drawable.ic_user);
            }
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }

}