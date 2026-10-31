package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import androidx.leanback.widget.ImageCardView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class ComplexImageCardView extends ImageCardView {
    private ComplexImageView mComplexImageView;
    private Handler mHandler;
    private boolean mIsCardTextAutoScrollEnabled;
    private boolean mIsBadgeEnabled;

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
            Runnable enableMarquee = () -> ViewUtil.enableMarquee(view);

            mHandler.postDelayed(enableMarquee, 1_000);

            view.setTag(enableMarquee);
        } else {
            if (view.getTag() instanceof Runnable) {
                mHandler.removeCallbacks((Runnable) view.getTag());
                view.setTag(null);
            }

            ViewUtil.disableMarquee(view);
        }
    }

    /**
     * Sets the badge text.
     */
    public void setBadgeText(String text) {
        if (mIsBadgeEnabled) {
            mComplexImageView.setBadgeText(text);
        }
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

        if (mIsCardTextAutoScrollEnabled) {
            enableTitleAnimation(selected);
            enableContentAnimation(selected);
        }

        enableVideoPreview(selected);
    }

    @Override
    public boolean performClick() {
        mComplexImageView.stopPlayback(true);

        return super.performClick();
    }

    @Override
    public boolean performLongClick() {
        mComplexImageView.stopPlayback(true);

        return super.performLongClick();
    }

    private void enableVideoPreview(boolean selected) {
        if (selected) {
            mComplexImageView.startPlayback();
        } else {
            mComplexImageView.stopPlayback();
        }
    }

    public void setPreview(Video video) {
        mComplexImageView.setPreview(video);
    }

    public void setMute(boolean muted) {
        mComplexImageView.setMute(muted);
    }

    public void setTitleLinesNum(int lines) {
        TextView titleView = findViewById(R.id.title_text);

        if (titleView == null || lines <= 0) {
            return;
        }

        titleView.setMaxLines(lines);
        titleView.setLines(lines);
    }

    public void setContentLinesNum(int lines) {
        TextView contentView = findViewById(R.id.content_text);

        if (contentView == null || lines <= 0) {
            return;
        }

        contentView.setMaxLines(lines);
        contentView.setLines(lines);
    }

    public void enableBadge(boolean enabled) {
        mIsBadgeEnabled = enabled;
    }

    public void enableTextAutoScroll(boolean enabled) {
        mIsCardTextAutoScrollEnabled = enabled;
    }

    public void setTextScrollSpeed(float speed) {
        ViewUtil.setTextScrollSpeed(findViewById(R.id.title_text), speed);
        ViewUtil.setTextScrollSpeed(findViewById(R.id.content_text), speed);
    }

    public void enableTitle(boolean enabled) {
        ViewUtil.enableView(findViewById(R.id.title_text), enabled);
    }

    public void enableContent(boolean enabled) {
        ViewUtil.enableView(findViewById(R.id.content_text), enabled);
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
