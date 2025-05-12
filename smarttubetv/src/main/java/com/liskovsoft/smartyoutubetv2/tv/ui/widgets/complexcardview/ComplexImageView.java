package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class ComplexImageView extends RelativeLayout {
    private ImageView mMainImage;
    private ImageView mPreviewImage;
    private EmbedPlayerView mPreviewPlayer;
    private FrameLayout mPreviewContainer;
    private ProgressBar mProgressBar;
    private TextView mBadgeText;
    private String mPreviewUrl;
    private String mPreviewVideoId;
    private ViewGroup mProgressContainer;
    private int mPreviewWidth;
    private int mPreviewHeight;
    private Runnable mCreateAndStartPlayer;

    public ComplexImageView(Context context) {
        super(context);
        init();
    }

    public ComplexImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ComplexImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.text_badge_image_view, this);
        mMainImage = findViewById(R.id.main_image);
        mBadgeText = findViewById(R.id.extra_text_badge);
        mProgressBar = findViewById(R.id.clip_progress);
        mProgressContainer = findViewById(R.id.clip_info);
        mPreviewContainer = findViewById(R.id.preview_container);
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
        if (text != null) {
            mBadgeText.setText(text);
            mBadgeText.setVisibility(View.VISIBLE);
        } else {
            mBadgeText.setVisibility(View.INVISIBLE);
        }
    }

    public void setBadgeColor(int color) {
        if (mBadgeText == null) {
            return;
        }

        mBadgeText.setBackgroundColor(color);
    }

    /**
     * Sets the progress.
     */
    public void setProgress(int percent) {
        if (mProgressBar == null) {
            return;
        }
        if (percent > 0) {
            mProgressBar.setProgress(percent);
            mProgressBar.setVisibility(View.VISIBLE);
        } else {
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    public void setPreviewUrl(String videoUrl) {
        mPreviewUrl = videoUrl;
    }

    public void setPreviewVideoId(String videoId) {
        mPreviewVideoId = videoId;
    }

    public void startPlayback() {
        if (mPreviewUrl != null) {
            if (mPreviewImage == null) {
                mPreviewImage = new ImageView(getContext());
                mPreviewImage.setScaleType(ScaleType.CENTER_CROP);
                mPreviewImage.setAdjustViewBounds(true);
                mPreviewContainer.addView(mPreviewImage, new FrameLayout.LayoutParams(mPreviewWidth, mPreviewHeight));
                mPreviewContainer.setVisibility(View.VISIBLE);
            }

            Glide.with(getContext().getApplicationContext()) // FIX: "You cannot start a load for a destroyed activity"
                    .load(mPreviewUrl)
                    .apply(ViewUtil.glideOptions())
                    .into(mPreviewImage);
        } else if (mPreviewVideoId != null) {
            if (mCreateAndStartPlayer == null) {
                mCreateAndStartPlayer = this::createAndStartPlayer;
            }

            Utils.postDelayed(mCreateAndStartPlayer, 3_000);
        }
    }

    private void createAndStartPlayer() {
        if (mPreviewPlayer == null) {
            mPreviewPlayer = new EmbedPlayerView(getContext());
            mPreviewPlayer.setUseController(false);
            mPreviewPlayer.setOnLoad(() -> {
                mPreviewContainer.addView(mPreviewPlayer, new FrameLayout.LayoutParams(mPreviewWidth, mPreviewHeight));
                mPreviewContainer.setVisibility(View.VISIBLE);
            });
        }
        
        mPreviewPlayer.openVideo(mPreviewVideoId);
    }

    public void stopPlayback() {
        if (mPreviewUrl != null) {
            mPreviewContainer.removeView(mPreviewImage);
            mPreviewContainer.setVisibility(View.GONE);
            mPreviewImage.setImageDrawable(null);
            Glide.with(getContext()).clear(mPreviewImage);
            mPreviewImage = null;
        } else if (mPreviewVideoId != null) {
            Utils.removeCallbacks(mCreateAndStartPlayer);

            if (mPreviewPlayer != null) {
                mPreviewContainer.removeView(mPreviewPlayer);
                mPreviewContainer.setVisibility(View.GONE);
                mPreviewPlayer.finish();
                mPreviewPlayer = null;
            }
        }
    }

    public void setMainImageAdjustViewBounds(boolean adjustViewBounds) {
        if (mPreviewImage != null) {
            mPreviewImage.setAdjustViewBounds(adjustViewBounds);
        }
    }

    public void setMainImageScaleType(ScaleType scaleType) {
        if (mPreviewImage != null) {
            mPreviewImage.setScaleType(scaleType);
        }
    }

    public void setMainImageDimensions(int width, int height) {
        setPreviewDimensions(width, height);
        setProgressDimensions(width, height);
    }

    private void setProgressDimensions(int width, int height) {
        ViewGroup.LayoutParams lp = mProgressContainer.getLayoutParams();
        lp.width = width;
        mProgressContainer.setLayoutParams(lp);
    }

    private void setPreviewDimensions(int width, int height) {
        mPreviewWidth = width;
        mPreviewHeight = height;
        //ViewGroup.LayoutParams lp = mPreviewImage.getLayoutParams();
        //lp.width = width;
        //lp.height = height;
        //mPreviewImage.setLayoutParams(lp);
    }
}
