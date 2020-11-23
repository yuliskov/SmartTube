package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import androidx.leanback.widget.ImageCardView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ComplexImageCardView extends ImageCardView {
    private ComplexImageView mComplexImageView;
    private Handler mHandler;
    private Runnable mEnableMarquee;
    private boolean mIsMultilineTitlesEnabled;

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

    private void enableTextAnimation(boolean enable) {
        TextView title = findViewById(R.id.title_text);
        TextView content = findViewById(R.id.content_text);

        if (title == null || content == null) {
            return;
        }

        if (enable) {
            mEnableMarquee = () -> enableMarquee(title, content);

            mHandler.postDelayed(mEnableMarquee, 1_000);
        } else {
            if (mEnableMarquee != null) {
                mHandler.removeCallbacks(mEnableMarquee);
                mEnableMarquee = null;
            }

            disableMarquee(title, content);
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

        if (!mIsMultilineTitlesEnabled) {
            enableTextAnimation(selected);
        }
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

    public void enableMultilineTitles(boolean enable) {
        TextView titleView = (TextView) Helpers.getField(this, "mTitleView");

        if (titleView == null) {
            return;
        }

        mIsMultilineTitlesEnabled = enable;

        titleView.setMaxLines(enable ? 2 : 1);
        titleView.setLines(enable ? 2 : 1);
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
