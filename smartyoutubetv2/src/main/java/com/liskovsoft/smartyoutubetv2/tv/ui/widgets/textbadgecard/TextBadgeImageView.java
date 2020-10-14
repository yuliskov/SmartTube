package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.textbadgecard;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextBadgeImageView extends RelativeLayout {
    private ImageView mMainImage;
    private ImageView mPreviewImage;
    private TextView mBadgeText;
    private String mPreviewUrl;

    public TextBadgeImageView(Context context) {
        super(context);
        init();
    }

    public TextBadgeImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TextBadgeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.text_badge_image_view, this);
        mMainImage = findViewById(R.id.main_image);
        mPreviewImage = findViewById(R.id.preview_image);
        mBadgeText = findViewById(R.id.extra_text_badge);
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

    public void setPreviewUrl(String videoUrl) {
        mPreviewUrl = videoUrl;
    }

    //public ImageView getImageView() {
    //    return mImageView;
    //}

    public void setLoading() {
        if (mPreviewUrl == null) {
            return;
        }

        mPreviewImage.setVisibility(View.VISIBLE);
        mMainImage.setVisibility(View.GONE);

        Glide.with(getContext())
                .load(mPreviewUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource,
                                                   boolean isFirstResource) {
                        //mMainImage.setVisibility(View.GONE);
                        //mPreviewImage.setVisibility(View.VISIBLE);
                        return false;
                    }
                })
                .into(mPreviewImage);
    }

    public void setFinished() {
        if (mPreviewUrl == null) {
            return;
        }

        mPreviewImage.setVisibility(View.GONE);
        mPreviewImage.setImageDrawable(null);
        mMainImage.setVisibility(View.VISIBLE);
    }
}
