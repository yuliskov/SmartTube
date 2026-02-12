package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.vineyard.videoview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.widget.BaseCardView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.vineyard.NetworkUtil;

public class VideoCardView extends BaseCardView {
    public static final int CARD_TYPE_FLAG_IMAGE_ONLY = 0;
    public static final int CARD_TYPE_FLAG_TITLE = 1;
    public static final int CARD_TYPE_FLAG_CONTENT = 2;

    PreviewCardView mPreviewCard;

    ViewGroup mInfoArea;

    private TextView mTitleView;
    private TextView mContentView;
    private boolean mAttachedToWindow;

    public VideoCardView(Context context, int styleResId) {
        super(new ContextThemeWrapper(context, styleResId), null, 0);
        buildImageCardView(styleResId);
    }

    public VideoCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(getStyledContext(context, attrs, defStyleAttr), attrs, defStyleAttr);
        buildImageCardView(getImageCardViewStyle(context, attrs, defStyleAttr));
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;
        ImageView mImageView = mPreviewCard.getImageView();
        if (mImageView.getAlpha() == 0) fadeIn();
    }

    @Override
    protected void onDetachedFromWindow() {
        ImageView mImageView = mPreviewCard.getImageView();
        mAttachedToWindow = false;
        mImageView.animate().cancel();
        mImageView.setAlpha(1f);
        super.onDetachedFromWindow();
    }

    private void buildImageCardView(int styleResId) {
        // Make sure the ImageCardView is focusable.
        setFocusable(true);
        setFocusableInTouchMode(true);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.lb_video_card_view, this);
        mPreviewCard = view.findViewById(R.id.layout_preview_card);
        mInfoArea = view.findViewById(R.id.info_field);

        TypedArray cardAttrs =
                getContext().obtainStyledAttributes(styleResId, R.styleable.lbImageCardView);
        int cardType =
                cardAttrs.getInt(
                        R.styleable.lbImageCardView_lbImageCardViewType, CARD_TYPE_FLAG_IMAGE_ONLY);
        boolean hasImageOnly = cardType == CARD_TYPE_FLAG_IMAGE_ONLY;
        boolean hasTitle = (cardType & CARD_TYPE_FLAG_TITLE) == CARD_TYPE_FLAG_TITLE;
        boolean hasContent = (cardType & CARD_TYPE_FLAG_CONTENT) == CARD_TYPE_FLAG_CONTENT;

        if (hasImageOnly) {
            removeView(mInfoArea);
            cardAttrs.recycle();
            return;
        }

        // Create children
        if (hasTitle) {
            mTitleView = (TextView) inflater.inflate(R.layout.lb_image_card_view_themed_title, mInfoArea, false);
            mInfoArea.addView(mTitleView);
        }

        if (hasContent) {
            mContentView = (TextView) inflater.inflate(R.layout.lb_image_card_view_themed_content, mInfoArea, false);
            mInfoArea.addView(mContentView);
        }

        // Backward compatibility: Newly created ImageCardViews should change
        // the InfoArea's background color in XML using the corresponding style.
        // However, since older implementations might make use of the
        // 'infoAreaBackground' attribute, we have to make sure to support it.
        // If the user has set a specific value here, it will differ from null.
        // In this case, we do want to override the value set in the style.
        Drawable background = cardAttrs.getDrawable(R.styleable.lbImageCardView_infoAreaBackground);
        if (null != background) {
            setInfoAreaBackground(background);
        }

        cardAttrs.recycle();
    }

    private static Context getStyledContext(Context context, AttributeSet attrs, int defStyleAttr) {
        int style = getImageCardViewStyle(context, attrs, defStyleAttr);
        return new ContextThemeWrapper(context, style);
    }

    private static int getImageCardViewStyle(Context context, AttributeSet attrs, int defStyleAttr) {
        // Read style attribute defined in XML layout.
        int style = null == attrs ? 0 : attrs.getStyleAttribute();
        if (0 == style) {
            // Not found? Read global ImageCardView style from Theme attribute.
            TypedArray styledAttrs = context.obtainStyledAttributes(R.styleable.LeanbackTheme);
            style = styledAttrs.getResourceId(R.styleable.LeanbackTheme_imageCardViewStyle, 0);
            styledAttrs.recycle();
        }
        return style;
    }

    public VideoCardView(Context context) {
        this(context, null);
    }

    public VideoCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageCardViewStyle);
    }

    /**
     * Returns the main image view.
     */
    public final ImageView getMainImageView() {
        return mPreviewCard.getImageView();
    }

    /**
     * Sets the layout dimensions of the ImageView.
     */
    public void setMainContainerDimensions(int width, int height) {
        ViewGroup.LayoutParams lp = mPreviewCard.getLayoutParams();
        lp.width = width;
        lp.height = height;
        mPreviewCard.setLayoutParams(lp);
    }

    /**
     * Sets the info area background drawable.
     */
    public void setInfoAreaBackground(Drawable drawable) {
        if (mInfoArea != null) {
            mInfoArea.setBackground(drawable);
        }
    }

    public void setVideoUrl(String url) {
        mPreviewCard.setVideoUrl(url);
    }

    public void startVideo() {
        if (NetworkUtil.isNetworkConnected(getContext())) {
            mPreviewCard.setLoading();
        }
    }

    public void stopVideo() {
        mPreviewCard.setFinished();
    }

    /**
     * Sets the title text.
     */
    public void setTitleText(CharSequence text) {
        if (mTitleView == null) {
            return;
        }
        mTitleView.setText(text);
    }

    /**
     * Sets the content text.
     */
    public void setContentText(CharSequence text) {
        if (mContentView == null) {
            return;
        }
        mContentView.setText(text);
    }

    private void fadeIn() {
        ImageView mImageView = mPreviewCard.getImageView();
        mImageView.setAlpha(0f);
        if (mAttachedToWindow) {
            int duration =
                    mImageView.getResources().getInteger(android.R.integer.config_shortAnimTime);
            mImageView.animate().alpha(1f).setDuration(duration);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);

        if (selected) {
            startVideo();
        } else {
            stopVideo();
        }
    }
}
