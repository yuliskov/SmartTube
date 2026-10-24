package com.liskovsoft.smartyoutubetv2.tv.ui.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.nio.charset.Charset;
import java.security.MessageDigest;

/** A dimmed, blurred thumbnail backdrop with the design document's slim arc loader. */
public final class VideoLoadingView extends FrameLayout {
    private final ImageView mBackdrop;
    private final SlimArcView mSpinner;
    private String mImageUrl;
    private long mRotationStartedAt;

    private final Runnable mRotate = new Runnable() {
        @Override
        public void run() {
            if (!isShown() || getWindowVisibility() != VISIBLE) {
                return;
            }
            float rotation = ((SystemClock.uptimeMillis() - mRotationStartedAt) % 1320) * 360f / 1320;
            mSpinner.setRotation(rotation);
            postOnAnimation(this);
        }
    };

    public VideoLoadingView(Context context) {
        super(context);
        setBackgroundColor(0xFF0F0F0F);
        setVisibility(INVISIBLE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        mBackdrop = new ImageView(context);
        mBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // Overscan keeps the blurred image edges outside the screen.
        mBackdrop.setScaleX(1.08f);
        mBackdrop.setScaleY(1.08f);
        addView(mBackdrop, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        View shade = new View(context);
        shade.setBackgroundColor(0xCC000000);
        addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        mSpinner = new SlimArcView(context);
        addView(mSpinner, new LayoutParams(dp(34), dp(34), Gravity.CENTER));
    }

    public void setVideo(Video video) {
        String url = video != null ? video.getCardImageUrl() : null;
        String fallback = video != null && video.videoId != null
                ? "https://i.ytimg.com/vi/" + video.videoId + "/mqdefault.jpg" : null;
        if (url == null) {
            url = fallback;
        }
        if (url != null && url.equals(mImageUrl)) {
            return;
        }
        mImageUrl = url;
        Context context = getContext().getApplicationContext();
        Glide.with(context).asBitmap().load(url)
                .override(256, 144).transform(new BackdropBlur()).dontAnimate()
                .placeholder(R.color.player_background)
                .error(Glide.with(context).asBitmap().load(fallback).override(256, 144).transform(new BackdropBlur()))
                .into(mBackdrop);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateSpinner() {
        removeCallbacks(mRotate);
        if (mSpinner == null) {
            return;
        }
        if (ViewCompat.isAttachedToWindow(this) && isShown() && getWindowVisibility() == VISIBLE) {
            mRotationStartedAt = SystemClock.uptimeMillis();
            postOnAnimation(mRotate);
        } else {
            mSpinner.setRotation(0);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateSpinner();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateSpinner();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateSpinner();
    }

    @Override
    protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    public void release() {
        removeCallbacks(mRotate);
        Glide.with(getContext().getApplicationContext()).clear(mBackdrop);
        mImageUrl = null;
    }

    /** Option 04: a 3dp red half-circle over a subtle gray ring. */
    private static final class SlimArcView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mBounds = new RectF();

        SlimArcView(Context context) {
            super(context);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            float inset = mPaint.getStrokeWidth() / 2;
            mBounds.set(inset, inset, width - inset, height - inset);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mPaint.setColor(0xFF333333);
            canvas.drawOval(mBounds, mPaint);
            mPaint.setColor(0xFFFF2222);
            canvas.drawArc(mBounds, -90, 180, false, mPaint);
        }
    }

    /** A low-resolution, three-pass box blur, performed by Glide's background transformation worker. */
    private static final class BackdropBlur extends BitmapTransformation {
        private static final String ID = "smarttube.video-loading-backdrop.v1";

        @Override
        protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap source, int outWidth, int outHeight) {
            int width = Math.min(256, source.getWidth());
            int height = Math.max(1, Math.round(source.getHeight() * width / (float) source.getWidth()));
            Bitmap result = pool.get(width, height, Bitmap.Config.ARGB_8888);
            new Canvas(result).drawBitmap(source, null, new Rect(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] pixels = new int[width * height];
            int[] scratch = new int[pixels.length];
            result.getPixels(pixels, 0, width, 0, 0, width, height);
            for (int pass = 0; pass < 3; pass++) {
                blur(pixels, scratch, width, height, true);
                blur(scratch, pixels, width, height, false);
            }
            result.setPixels(pixels, 0, width, 0, 0, width, height);
            return result;
        }

        private static void blur(int[] input, int[] output, int width, int height, boolean horizontal) {
            int radius = 6;
            int window = radius * 2 + 1;
            int lines = horizontal ? height : width;
            int length = horizontal ? width : height;
            int stride = horizontal ? 1 : width;
            for (int line = 0; line < lines; line++) {
                int base = horizontal ? line * width : line;
                int alpha = 0, red = 0, green = 0, blue = 0;
                for (int offset = -radius; offset <= radius; offset++) {
                    int color = input[base + Math.max(0, Math.min(length - 1, offset)) * stride];
                    alpha += color >>> 24;
                    red += (color >> 16) & 255;
                    green += (color >> 8) & 255;
                    blue += color & 255;
                }
                for (int position = 0; position < length; position++) {
                    output[base + position * stride] = (alpha / window << 24) | (red / window << 16)
                            | (green / window << 8) | (blue / window);
                    int leaving = input[base + Math.max(0, position - radius) * stride];
                    int entering = input[base + Math.min(length - 1, position + radius + 1) * stride];
                    alpha += (entering >>> 24) - (leaving >>> 24);
                    red += ((entering >> 16) & 255) - ((leaving >> 16) & 255);
                    green += ((entering >> 8) & 255) - ((leaving >> 8) & 255);
                    blue += (entering & 255) - (leaving & 255);
                }
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BackdropBlur;
        }

        @Override
        public int hashCode() {
            return ID.hashCode();
        }

        @Override
        public void updateDiskCacheKey(@NonNull MessageDigest digest) {
            digest.update(ID.getBytes(Charset.forName("UTF-8")));
        }
    }
}
