package com.liskovsoft.smartyoutubetv2.common.exoplayer.previewtimebar;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

public class GlideThumbnailTransformation extends BitmapTransformation {
    private final int mWidth;
    private final int mHeight;
    private final int mMaxLines;
    private final int mMaxColumns;
    private final int mThumbnailsEach; // duration of each thumbnail in millisseconds

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
        mThumbnailsEach = durationMS;

        int square = (int) position / mThumbnailsEach;
        mY = square / mMaxLines;
        mX = square % mMaxColumns;
    }

    private int getX() {
        return mX;
    }

    private int getY() {
        return mY;
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform,
                               int outWidth, int outHeight) {
        int width = mWidth == 0 ? toTransform.getWidth() / mMaxColumns : mWidth;
        int height = mHeight == 0 ? toTransform.getHeight() / mMaxLines : mHeight;
        return Bitmap.createBitmap(toTransform, mX * width, mY * height, width, height);
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
