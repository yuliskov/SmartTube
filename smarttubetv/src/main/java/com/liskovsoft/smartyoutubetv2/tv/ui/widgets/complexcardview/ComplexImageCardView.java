package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import androidx.leanback.widget.ImageCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ComplexImageCardView extends ImageCardView {
    private ComplexImageView mComplexImageView;
    private Handler mHandler;
    private Runnable mEnableMarquee;
    private boolean mIsCardTitleAutoScrollEnabled = true;

    public ComplexImageCardView(Context context) {
        super(context);

        init();
    }

    public ComplexImageCardView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public ComplexImageCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        mComplexImageView = findViewById(R.id.main_image_wrapper);
        mHandler = new Handler(Looper.getMainLooper());
    }

    private void enableTitleAnimation(boolean enable) {
        enableTextAnimation(findViewById(R.id.title_text), enable);
    }

    private void enableContentAnimation(boolean enable) {
        enableTextAnimation(findViewById(R.id.content_text), enable);
    }

    private void enableTextAnimation(TextView view, boolean enable) {
        if (view == null) {
            return;
        }

        if (enable) {
            mEnableMarquee = () -> enableMarquee(view);

            mHandler.postDelayed(mEnableMarquee, 1_000);
        } else {
            if (mEnableMarquee != null) {
                mHandler.removeCallbacks(mEnableMarquee);
                mEnableMarquee = null;
            }

            disableMarquee(view);
        }
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

    /**
     * Sets the badge text.
     */
    public void setBadgeText(String text) {
        mComplexImageView.setBadgeText(text);
    }

    public void setBadgeColor(int color) {
        mComplexImageView.setBadgeColor(color);
    }

    /**
     * Sets the progress.
     */
    public void setProgress(int percent) {
        mComplexImageView.setProgress(percent);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        if (mIsCardTitleAutoScrollEnabled) {
            enableTitleAnimation(selected);
        }
        enableContentAnimation(selected);
        enableVideoPreview(selected);
    }

    private void enableVideoPreview(boolean selected) {
        if (selected) {
            mComplexImageView.startPlayback();
        } else {
            mComplexImageView.stopPlayback();
        }
    }

    public void setPreviewUrl(String previewUrl) {
        mComplexImageView.setPreviewUrl(previewUrl);
    }

    public void setTitleLinesNum(int lines) {
        TextView titleView = findViewById(R.id.title_text);

        if (titleView == null || lines < 1) {
            return;
        }

        mIsCardTitleAutoScrollEnabled = lines == 1;

        titleView.setMaxLines(lines);
        titleView.setLines(lines);
    }

    /**
     * Enables or disables adjustment of view bounds on the main image.
     */
    @Override
    public void setMainImageAdjustViewBounds(boolean adjustViewBounds) {
        super.setMainImageAdjustViewBounds(adjustViewBounds);
        mComplexImageView.setMainImageAdjustViewBounds(adjustViewBounds);
    }

    /**
     * Sets the ScaleType of the main image.
     */
    @Override
    public void setMainImageScaleType(ScaleType scaleType) {
        super.setMainImageScaleType(scaleType);
        mComplexImageView.setMainImageScaleType(scaleType);
    }

    /**
     * Sets the layout dimensions of the ImageView.
     */
    @Override
    public void setMainImageDimensions(int width, int height) {
        super.setMainImageDimensions(width, height);
        mComplexImageView.setMainImageDimensions(width, height);
    }
}
