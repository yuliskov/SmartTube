package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.Transformation;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.presenter.base.LongClickPresenter;

/**
 * Binds {@link Video} items to {@link ModernVideoCard}.<br/>
 * Clicks are handled by the host, long press by {@link LongClickPresenter}.
 */
public class ModernCardPresenter extends LongClickPresenter {
    private final int mWidthResId;
    private final int mThumbHeightResId;
    private int mThumbQuality = -1;
    private int mPreviewType;
    private boolean mMultilineTitle;
    private float mTitleScrollSpeed;
    private boolean mTitleAutoScroll;
    private boolean mRoundedCorners;

    public ModernCardPresenter() {
        this(0, 0);
    }

    /**
     * Fixed tile size, for hosts that don't size the cards themselves.
     */
    public ModernCardPresenter(int widthResId, int thumbHeightResId) {
        mWidthResId = widthResId;
        mThumbHeightResId = thumbHeightResId;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        if (mThumbQuality == -1) {
            mThumbQuality = MainUIData.instance(context).getThumbQuality();
            mPreviewType = MainUIData.instance(context).getCardPreviewType();
            mMultilineTitle = MainUIData.instance(context).isCardMultilineTitleEnabled();
            mTitleAutoScroll = MainUIData.instance(context).isCardTextAutoScrollEnabled();
            mTitleScrollSpeed = MainUIData.instance(context).getCardTextScrollSpeed();
            mRoundedCorners = MainUIData.instance(context).isUiTweakEnabled(MainUIData.UI_TWEAK_ROUNDED_CORNERS);
        }

        ModernVideoCard card = (ModernVideoCard) LayoutInflater.from(context).inflate(R.layout.modern_video_card, parent, false);

        if (mWidthResId != 0) {
            card.setThumbSize(context.getResources().getDimensionPixelSize(mWidthResId),
                    context.getResources().getDimensionPixelSize(mThumbHeightResId));
        }

        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        super.onBindViewHolder(viewHolder, item);

        ModernVideoCard card = (ModernVideoCard) viewHolder.view;
        Video video = (Video) item;
        Context context = card.getContext();

        card.setPreviewType(mPreviewType);
        card.setTitleOptions(mMultilineTitle, mTitleAutoScroll, mTitleScrollSpeed);
        card.setRoundedCorners(mRoundedCorners);
        card.bind(video);

        if (context instanceof Activity && ((Activity) context).isDestroyed()) {
            return;
        }

        // The card clips the thumbnail with its outline (API 21+): no costly bitmap rounding per image
        Transformation<Bitmap> shape;
        if (VERSION.SDK_INT >= 21) {
            shape = new CenterCrop();
        } else {
            int radius = context.getResources().getDimensionPixelSize(R.dimen.modern_tile_thumb_radius);
            shape = card.getKind() == ModernVideoCard.KIND_CHANNEL ? new MultiTransformation<>(new CenterCrop(), new CircleCrop()) :
                    mRoundedCorners ? new MultiTransformation<>(new CenterCrop(), new RoundedCorners(radius)) : new CenterCrop();
        }

        RequestBuilder<Drawable> request = Glide.with(context)
                .load(ClickbaitRemover.updateThumbnail(video, mThumbQuality))
                .transform(shape)
                // Network loads fade in, memory cache hits show at once
                .transition(DrawableTransitionOptions.withCrossFade(ModernTheme.ANIM_FAST_MS))
                .diskCacheStrategy(VERSION.SDK_INT > 21 ? DiskCacheStrategy.ALL : DiskCacheStrategy.NONE)
                .error(Glide.with(context).load(video.cardImageUrl).transform(shape));

        if (card.getKind() == ModernVideoCard.KIND_PLAYLIST) {
            request = request.listener(new StackColorListener(card));
        }

        request.into(card.getThumbView());
    }

    /**
     * Playlist stack takes the darkened average color of the thumbnail.
     */
    private static final class StackColorListener implements RequestListener<Drawable> {
        private final ModernVideoCard mCard;

        StackColorListener(ModernVideoCard card) {
            mCard = card;
        }

        @Override
        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
            return false;
        }

        @Override
        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
            if (resource instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) resource).getBitmap();
                if (bitmap != null && !bitmap.isRecycled()) {
                    Bitmap pixel = Bitmap.createScaledBitmap(bitmap, 1, 1, true);
                    int color = pixel.getPixel(0, 0);
                    float darken = 0.6f;
                    mCard.setStackColor(Color.rgb(Math.round(Color.red(color) * darken),
                            Math.round(Color.green(color) * darken), Math.round(Color.blue(color) * darken)));
                }
            }
            return false;
        }
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        super.onUnbindViewHolder(viewHolder);

        ModernVideoCard card = (ModernVideoCard) viewHolder.view;
        Glide.with(card.getContext().getApplicationContext()).clear(card.getThumbView());
        card.getThumbView().setImageDrawable(null);
    }
}
