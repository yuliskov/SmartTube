package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.textbadgeview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import androidx.leanback.widget.ImageCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextBadgeImageCardView extends ImageCardView {
    private TextBadgeImageView mTextBadgeImageLayout;
    private Handler mHandler;
    private Runnable mEnableMarquee;

    public TextBadgeImageCardView(Context context) {
        super(context);

        init();
    }

    public TextBadgeImageCardView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public TextBadgeImageCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        init();
    }

    private void init() {
        mTextBadgeImageLayout = findViewById(R.id.main_image_wrapper);
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
        mTextBadgeImageLayout.setBadgeText(text);
    }

    /**
     * Sets the progress.
     */
    public void setProgress(int percent) {
        mTextBadgeImageLayout.setProgress(percent);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        enableTextAnimation(selected);
        enableVideoPreview(selected);
    }

    private void enableVideoPreview(boolean selected) {
        if (selected) {
            mTextBadgeImageLayout.startPlayback();
        } else {
            mTextBadgeImageLayout.stopPlayback();
        }
    }

    public void setPreviewUrl(String previewUrl) {
        mTextBadgeImageLayout.setPreviewUrl(previewUrl);
    }

    /**
     * Enables or disables adjustment of view bounds on the main image.
     */
    @Override
    public void setMainImageAdjustViewBounds(boolean adjustViewBounds) {
        super.setMainImageAdjustViewBounds(adjustViewBounds);
        mTextBadgeImageLayout.setMainImageAdjustViewBounds(adjustViewBounds);
    }

    /**
     * Sets the ScaleType of the main image.
     */
    @Override
    public void setMainImageScaleType(ScaleType scaleType) {
        super.setMainImageScaleType(scaleType);
        mTextBadgeImageLayout.setMainImageScaleType(scaleType);
    }

    /**
     * Sets the layout dimensions of the ImageView.
     */
    @Override
    public void setMainImageDimensions(int width, int height) {
        super.setMainImageDimensions(width, height);
        mTextBadgeImageLayout.setMainImageDimensions(width, height);
    }
}
