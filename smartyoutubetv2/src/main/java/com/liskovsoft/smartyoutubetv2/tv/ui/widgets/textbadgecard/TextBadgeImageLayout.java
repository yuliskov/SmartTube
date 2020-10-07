package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.textbadgecard;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextBadgeImageLayout extends RelativeLayout {
    private ImageView mMainImage;

    public TextBadgeImageLayout(Context context) {
        super(context);
        init();
    }

    public TextBadgeImageLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextBadgeImageLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.image_card_view, this);
        mMainImage = findViewById(R.id.main_image);
    }

    /**
     * Main trick is to apply visibility to child image views<br/>
     * See: androidx.leanback.widget.BaseCardView#findChildrenViews()
     */
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (mMainImage != null) {
            mMainImage.setVisibility(visibility);
        }
    }
}
