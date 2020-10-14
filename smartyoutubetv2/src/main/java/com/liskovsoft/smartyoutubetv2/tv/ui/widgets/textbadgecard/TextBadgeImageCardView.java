package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.textbadgecard;

import android.content.Context;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.widget.TextView;
import androidx.leanback.widget.ImageCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TextBadgeImageCardView extends ImageCardView {
    private TextBadgeImageView mTextBadgeImageLayout;

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

    private void enableTextAnimation(boolean enable) {
        TextView title = findViewById(R.id.title_text);
        TextView content = findViewById(R.id.content_text);

        if (title == null || content == null) {
            return;
        }

        if (enable) {
            title.setEllipsize(TruncateAt.MARQUEE);
            title.setMarqueeRepeatLimit(-1);
            title.setHorizontallyScrolling(true);

            content.setEllipsize(TruncateAt.MARQUEE);
            content.setMarqueeRepeatLimit(-1);
            content.setHorizontallyScrolling(true);
        } else {
            title.setEllipsize(TruncateAt.END);
            content.setEllipsize(TruncateAt.END);
        }
    }

    private void init() {
        mTextBadgeImageLayout = findViewById(R.id.main_image_wrapper);
    }

    /**
     * Sets the badge text.
     */
    public void setBadgeText(String text) {
        mTextBadgeImageLayout.setBadgeText(text);
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        enableTextAnimation(selected);
        enableVideoPreview(selected);
    }

    private void enableVideoPreview(boolean selected) {
        if (selected) {
            mTextBadgeImageLayout.setLoading();
        } else {
            mTextBadgeImageLayout.setFinished();
        }
    }

    public void setPreviewUrl(String previewUrl) {
        mTextBadgeImageLayout.setPreviewUrl(previewUrl);
    }
}
