package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.leanback.widget.Presenter;
import androidx.core.content.ContextCompat;
import android.view.ViewGroup;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.complexcardview.ComplexImageCardView;

/*
 * A CardPresenter is used to generate Views and bind Objects to them on demand.
 * It contains an Image CardView
 */
public class CardPresenter extends Presenter {
    private static final String TAG = CardPresenter.class.getSimpleName();
    private int mDefaultBackgroundColor = -1;
    private int mDefaultTextColor = -1;
    private int mSelectedBackgroundColor = -1;
    private int mSelectedTextColor = -1;
    private Drawable mDefaultCardImage;
    private boolean mIsAnimatedPreviewsEnabled;
    private boolean mIsMultilineTitlesEnabled;
    private float mVideoGridScale;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        mDefaultBackgroundColor =
            ContextCompat.getColor(parent.getContext(), Helpers.getThemeAttr(parent.getContext(), R.attr.cardDefaultBackground));
        mDefaultTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_default_text);
        mSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), Helpers.getThemeAttr(parent.getContext(), R.attr.cardSelectedBackground));
        mSelectedTextColor =
                ContextCompat.getColor(parent.getContext(), R.color.card_selected_text_grey);
        mDefaultCardImage = new ColorDrawable(ContextCompat.getColor(parent.getContext(), R.color.lb_grey));

        MainUIData mainUIData = MainUIData.instance(parent.getContext());
        mIsAnimatedPreviewsEnabled = mainUIData.isAnimatedPreviewsEnabled();
        mVideoGridScale = mainUIData.getVideoGridScale();
        mIsMultilineTitlesEnabled = mainUIData.isMultilineTitlesEnabled();

        ComplexImageCardView cardView = new ComplexImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.enableMultilineTitles(mIsMultilineTitlesEnabled);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        updateCardBackgroundColor(cardView, false);
        return new ViewHolder(cardView);
    }

    private void updateCardBackgroundColor(ComplexImageCardView view, boolean selected) {
        int backgroundColor = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;
        int textColor = selected ? mSelectedTextColor : mDefaultTextColor;

        // Both background colors should be set because the view's
        // background is temporarily visible during animations.
        view.setBackgroundColor(backgroundColor);
        View infoField = view.findViewById(R.id.info_field);
        if (infoField != null) {
            infoField.setBackgroundColor(backgroundColor);
        }

        TextView titleText = view.findViewById(R.id.title_text);
        if (titleText != null) {
            titleText.setTextColor(textColor);
        }
        TextView contentText = view.findViewById(R.id.content_text);
        if (contentText != null) {
            contentText.setTextColor(textColor);
        }
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        Video video = (Video) item;

        ComplexImageCardView cardView = (ComplexImageCardView) viewHolder.view;
        Context context = cardView.getContext();
        Resources res = cardView.getResources();

        cardView.setTitleText(video.title);
        cardView.setContentText(video.description);
        cardView.setProgress(video.percentWatched);
        cardView.setBadgeText(video.hasNewContent ?
                context.getString(R.string.badge_new_content) : video.isLive ? context.getString(R.string.badge_live) : video.badge);
        cardView.setBadgeColor(video.hasNewContent || video.isLive || video.isUpcoming ?
                ContextCompat.getColor(context, R.color.dark_red) : ContextCompat.getColor(context, R.color.black));

        if (mIsAnimatedPreviewsEnabled) {
            cardView.setPreviewUrl(video.previewUrl);
        }

        // Set card size from dimension resources.
        int width = res.getDimensionPixelSize(R.dimen.card_width);
        int height = res.getDimensionPixelSize(R.dimen.card_height);

        if (mVideoGridScale > 1.0f) {
            width *= mVideoGridScale;
            height *= mVideoGridScale;
        }

        cardView.setMainImageDimensions(width, height);

        Glide.with(context)
                .load(video.cardImageUrl)
                .apply(RequestOptions.errorOf(mDefaultCardImage))
                .listener(mErrorListener)
                .into(cardView.getMainImageView());
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ComplexImageCardView cardView = (ComplexImageCardView) viewHolder.view;

        // Remove references to images so that the garbage collector can free up memory.
        cardView.setBadgeImage(null);
        cardView.setMainImage(null);
    }

    private final RequestListener<Drawable> mErrorListener = new RequestListener<Drawable>() {
        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            Log.e(TAG, "Glide load failed: " + e);
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            return false;
        }
    };
}
