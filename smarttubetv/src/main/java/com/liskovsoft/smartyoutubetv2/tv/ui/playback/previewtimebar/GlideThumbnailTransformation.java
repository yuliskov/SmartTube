package com.liskovsoft.smartyoutubetv2.tv.ui.playback.previewtimebar;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class GlideThumbnailTransformation extends BitmapTransformation {
    private static final String TAG = GlideThumbnailTransformation.class.getSimpleName();
    private final int mWidth;
    private final int mHeight;
    private final int mMaxLines;
    private final int mMaxColumns;
    private final int mThumbDurationMS; // duration of each thumbnail in milliseconds

    private final int mX;
    private final int mY;

    public GlideThumbnailTransformation(long position) {
        this(position, 0, 0, 7, 7, 5000);
    }

    public GlideThumbnailTransformation(long position, int width, int height, int rowCount, int colCount, int durationMS) {
        mWidth = width;
        mHeight = height;
        mMaxLines = rowCount;
        mMaxColumns = colCount;
        mThumbDurationMS = durationMS;

        int thumbPos = (int) position / mThumbDurationMS;
        mY = thumbPos / mMaxLines;
        mX = thumbPos % mMaxColumns;

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Thumbnail coordinates (%sx%s): pos: %s, line: %s, col: %s", mMaxColumns, mMaxLines, thumbPos + 1, mY + 1, mX + 1);
        }
    }

    private int getX() {
        return mX;
    }

    private int getY() {
        return mY;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap groupBitmap,
                               int outWidth, int outHeight) {
        int width = mWidth == 0 ? groupBitmap.getWidth() / mMaxColumns : mWidth;
        int height = mHeight == 0 ? groupBitmap.getHeight() / mMaxLines : mHeight;
        return Bitmap.createBitmap(groupBitmap, mX * width, mY * height, width, height);
    }

    @Override
    public void updateDiskCacheKey(MessageDigest messageDigest) {
        byte[] data = ByteBuffer.allocate(8).putInt(mX).putInt(mY).array();
        messageDigest.update(data);
    }

    @Override
    public int hashCode() {
        return (String.valueOf(mX) + String.valueOf(mY)).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GlideThumbnailTransformation)) {
            return false;
        }
        GlideThumbnailTransformation transformation = (GlideThumbnailTransformation) obj;
        return transformation.getX() == mX && transformation.getY() == mY;
    }
}
