/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.leanback.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.IntProperty;
import android.util.Property;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Subclass of {@link Drawable} that can be used to draw a bitmap into a region. Bitmap
 * will be scaled to fit the full width of the region and will be aligned to the top left corner.
 * Any region outside the bounds will be clipped during {@link #draw(Canvas)} call. Top
 * position of the bitmap can be controlled by {@link #setVerticalOffset(int)} call or
 * {@link #PROPERTY_VERTICAL_OFFSET}.
 */
public class FitWidthBitmapDrawable extends Drawable {

    static class BitmapState extends Drawable.ConstantState {
        Paint mPaint;
        Bitmap mBitmap;
        Rect mSource;
        final Rect mDefaultSource = new Rect();
        int mOffset;

        BitmapState() {
            mPaint = new Paint();
        }

        BitmapState(BitmapState other) {
            mBitmap = other.mBitmap;
            mPaint = new Paint(other.mPaint);
            mSource = other.mSource != null ? new Rect(other.mSource) : null;
            mDefaultSource.set(other.mDefaultSource);
            mOffset = other.mOffset;
        }

        @NonNull
        @Override
        public Drawable newDrawable() {
            return new FitWidthBitmapDrawable(this);
        }

        @Override
        public int getChangingConfigurations() {
            return 0;
        }
    }

    final Rect mDest = new Rect();
    BitmapState mBitmapState;
    boolean mMutated = false;

    public FitWidthBitmapDrawable() {
        mBitmapState = new BitmapState();
    }

    FitWidthBitmapDrawable(BitmapState state) {
        mBitmapState = state;
    }

    @Override
    public ConstantState getConstantState() {
        return mBitmapState;
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mBitmapState = new BitmapState(mBitmapState);
            mMutated = true;
        }
        return this;
    }

    /**
     * Sets the bitmap.
     */
    public void setBitmap(Bitmap bitmap) {
        mBitmapState.mBitmap = bitmap;
        if (bitmap != null) {
            mBitmapState.mDefaultSource.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        } else {
            mBitmapState.mDefaultSource.set(0, 0, 0, 0);
        }
        mBitmapState.mSource = null;
    }

    /**
     * Returns the bitmap.
     */
    public Bitmap getBitmap() {
        return mBitmapState.mBitmap;
    }

    /**
     * Sets the {@link Rect} used for extracting the bitmap.
     */
    public void setSource(Rect source) {
        mBitmapState.mSource = source;
    }

    /**
     * Returns the {@link Rect} used for extracting the bitmap.
     */
    public Rect getSource() {
        return mBitmapState.mSource;
    }

    /**
     * Sets the vertical offset which will be used for drawing the bitmap. The bitmap drawing
     * will start the provided vertical offset.
     * @see #PROPERTY_VERTICAL_OFFSET
     */
    public void setVerticalOffset(int offset) {
        mBitmapState.mOffset = offset;
        invalidateSelf();
    }

    /**
     * Returns the current vertical offset.
     * @see #PROPERTY_VERTICAL_OFFSET
     */
    public int getVerticalOffset() {
        return mBitmapState.mOffset;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mBitmapState.mBitmap != null) {
            Rect bounds = getBounds();
            mDest.left = 0;
            mDest.top = mBitmapState.mOffset;
            mDest.right = bounds.width();

            Rect source = validateSource();
            float scale = (float) bounds.width() / source.width();
            mDest.bottom = mDest.top + (int) (source.height() * scale);
            int i = canvas.save();
            canvas.clipRect(bounds);
            canvas.drawBitmap(mBitmapState.mBitmap, source, mDest, mBitmapState.mPaint);
            canvas.restoreToCount(i);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        final int oldAlpha = mBitmapState.mPaint.getAlpha();
        if (alpha != oldAlpha) {
            mBitmapState.mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    /**
     * @return Alpha value between 0(inclusive) and 255(inclusive)
     */
    @Override
    public int getAlpha() {
        return mBitmapState.mPaint.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mBitmapState.mPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        final Bitmap bitmap = mBitmapState.mBitmap;
        return (bitmap == null || bitmap.hasAlpha() || mBitmapState.mPaint.getAlpha() < 255)
                ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
    }

    private Rect validateSource() {
        if (mBitmapState.mSource == null) {
            return mBitmapState.mDefaultSource;
        } else {
            return mBitmapState.mSource;
        }
    }

    /**
     * Property for {@link #setVerticalOffset(int)} and {@link #getVerticalOffset()}.
     */
    public static final Property<FitWidthBitmapDrawable, Integer> PROPERTY_VERTICAL_OFFSET;

    static {
        if (Build.VERSION.SDK_INT >= 24) {
            // use IntProperty
            PROPERTY_VERTICAL_OFFSET = getVerticalOffsetIntProperty();
        } else {
            // use Property
            PROPERTY_VERTICAL_OFFSET = new Property<FitWidthBitmapDrawable, Integer>(Integer.class,
                    "verticalOffset") {
                @Override
                public void set(FitWidthBitmapDrawable object, Integer value) {
                    object.setVerticalOffset(value);
                }

                @Override
                public Integer get(FitWidthBitmapDrawable object) {
                    return object.getVerticalOffset();
                }
            };
        }
    }

    @RequiresApi(24)
    static IntProperty<FitWidthBitmapDrawable> getVerticalOffsetIntProperty() {
        return new IntProperty<FitWidthBitmapDrawable>("verticalOffset") {
            @Override
            public void setValue(FitWidthBitmapDrawable fitWidthBitmapDrawable, int value) {
                fitWidthBitmapDrawable.setVerticalOffset(value);
            }

            @Override
            public Integer get(FitWidthBitmapDrawable fitWidthBitmapDrawable) {
                return fitWidthBitmapDrawable.getVerticalOffset();
            }
        };
    }
}
