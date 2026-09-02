package com.liskovsoft.smartyoutubetv2.tv.presenter.vineyard;

import android.graphics.Color;
import android.os.Build;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag;
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.User;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.material.MaterialYouColors;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard.TagCardView;

public class TagPresenter extends LongClickPresenter {
    private static int sDefaultBackgroundColor;
    private static int sDefaultTextColor;
    private static int sSelectedBackgroundColor;
    private static int sSelectedTextColor;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        sDefaultBackgroundColor = MaterialYouColors.surfaceContainerHigh(parent.getContext());
        sDefaultTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_text);
        sSelectedBackgroundColor = MaterialYouColors.focusedCardSurface(parent.getContext());
        sSelectedTextColor = Color.WHITE;

        TagCardView cardView = new TagCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardSurface(this, selected);
                updateCardTextColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardSurface(cardView, false);
        updateCardTextColor(cardView, false);
        return new ViewHolder(cardView);
    }

    private static void updateCardSurface(TagCardView view, boolean selected) {
        view.setBackground(MaterialYouColors.roundedSurface(
                view.getContext(),
                selected ? sSelectedBackgroundColor : sDefaultBackgroundColor,
                18));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setForeground(MaterialYouColors.outlinedSurface(
                    view.getContext(),
                    Color.TRANSPARENT,
                    18,
                    selected ? MaterialYouColors.focusedCardOutline(view.getContext()) : Color.TRANSPARENT,
                    selected ? 2.0f : 0.0f));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float density = view.getResources().getDisplayMetrics().density;
            view.setElevation((selected ? 8 : 1) * density);
        }
    }

    private static void updateCardTextColor(TagCardView view, boolean selected) {
        view.setTextColor(selected ? sSelectedTextColor : sDefaultTextColor);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        if (item instanceof Tag) {
            Tag post = (Tag) item;
            TagCardView cardView = (TagCardView) viewHolder.view;

            if (post.tag != null) {
                cardView.setCardText(post.tag);
                cardView.setContentDescription(post.tag);
                //cardView.setCardIcon(R.drawable.ic_tag);
            }
        } else if (item instanceof User) {
            User post = (User) item;
            TagCardView cardView = (TagCardView) viewHolder.view;

            if (post.username != null) {
                cardView.setCardText(post.username);
                cardView.setCardIcon(R.drawable.ic_user);
                cardView.setContentDescription(post.username);
            }
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {

    }

}
