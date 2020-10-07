package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.widget.ImageCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextBadgeImageCardView extends ImageCardView {
    private TextView mBadgeText;

    public TextBadgeImageCardView(Context context) {
        super(context);

        createTextBadge();
    }

    public TextBadgeImageCardView(Context context, AttributeSet attrs) {
        super(context, attrs);

        createTextBadge();
    }

    public TextBadgeImageCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        createTextBadge();
    }

    private void createTextBadge() {
        //ViewGroup wrapper = findViewById(R.id.main_image_wrapper);
        //
        //if (wrapper != null) {
        //    LayoutInflater inflater = LayoutInflater.from(getContext());
        //
        //    int layoutId = R.layout.text_badge_image_card_view_badge;
        //    mBadgeText = (TextView) inflater.inflate(layoutId, wrapper, false);
        //    wrapper.addView(mBadgeText);
        //}
    }

    /**
     * Sets the badge text.
     */
    public void setBadgeText(String text) {
        if (mBadgeText == null) {
            return;
        }
        mBadgeText.setText(text);
        if (text != null) {
            mBadgeText.setVisibility(View.VISIBLE);
        } else {
            mBadgeText.setVisibility(View.GONE);
        }
    }
}
