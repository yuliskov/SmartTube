package com.stfalcon.chatkit.utils;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.annotation.DimenRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

/**
 * Thanks to Joonho Kim (https://github.com/pungrue26) for his lightweight SelectableRoundedImageView,
 * that was used as default image message representation
 */
public class RoundedImageView extends AppCompatImageView {

    private int mResource = 0;
    private Drawable mDrawable;

    private float[] mRadii = new float[]{0, 0, 0, 0, 0, 0, 0, 0};

    public RoundedImageView(Context context) {
        super(context);
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        mResource = 0;
        mDrawable = RoundedCornerDrawable.fromDrawable(drawable, getResources());
        super.setImageDrawable(mDrawable);
        updateDrawable();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        mResource = 0;
        mDrawable = RoundedCornerDrawable.fromBitmap(bm, getResources());
        super.setImageDrawable(mDrawable);
        updateDrawable();
    }

    @Override
    public void setImageResource(int resId) {
        if (mResource != resId) {
            mResource = resId;
            mDrawable = resolveResource();
            super.setImageDrawable(mDrawable);
            updateDrawable();
        }
    }

    @Override
    public void setImageURI(Uri uri) {
        super.setImageURI(uri);
        setImageDrawable(getDrawable());
    }

    public void setCorners(@DimenRes int leftTop, @DimenRes int rightTop,
                           @DimenRes int rightBottom, @DimenRes int leftBottom) {
        setCorners(
                leftTop == 0 ? 0 : getResources().getDimension(leftTop),
                rightTop == 0 ? 0 : getResources().getDimension(rightTop),
                rightBottom == 0 ? 0 : getResources().getDimension(rightBottom),
                leftBottom == 0 ? 0 : getResources().getDimension(leftBottom)
        );
    }

    public void setCorners(float leftTop, float rightTop, float rightBottom, float leftBottom) {
        mRadii = new float[]{
                leftTop, leftTop,
                rightTop, rightTop,
                rightBottom, rightBottom,
                leftBottom, leftBottom};

        updateDrawable();
    }

    private Drawable resolveResource() {
        Drawable d = null;

        if (mResource != 0) {
            try {
                d = ContextCompat.getDrawable(getContext(), mResource);
            } catch (NotFoundException e) {
                mResource = 0;
            }
        }
        return RoundedCornerDrawable.fromDrawable(d, getResources());
    }

    private void updateDrawable() {
        if (mDrawable == null) return;

        ((RoundedCornerDrawable) mDrawable).setCornerRadii(mRadii);
    }

    private static class RoundedCornerDrawable extends Drawable {
        private RectF mBounds = new RectF();

        private final RectF mBitmapRect = new RectF();
        private final int mBitmapWidth;
        private final int mBitmapHeight;

        private final Paint mBitmapPaint;

        private float[] mRadii = new float[]{0, 0, 0, 0, 0, 0, 0, 0};

        private Path mPath = new Path();
        private Bitmap mBitmap;
        private boolean mBoundsConfigured = false;

        private RoundedCornerDrawable(Bitmap bitmap, Resources r) {
            mBitmap = bitmap;
            BitmapShader mBitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

            mBitmapWidth = bitmap.getScaledWidth(r.getDisplayMetrics());
            mBitmapHeight = bitmap.getScaledHeight(r.getDisplayMetrics());

            mBitmapRect.set(0, 0, mBitmapWidth, mBitmapHeight);

            mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mBitmapPaint.setStyle(Paint.Style.FILL);
            mBitmapPaint.setShader(mBitmapShader);
        }

        private static RoundedCornerDrawable fromBitmap(Bitmap bitmap, Resources r) {
            if (bitmap != null) return new RoundedCornerDrawable(bitmap, r);
            else return null;
        }

        private static Drawable fromDrawable(Drawable drawable, Resources r) {
            if (drawable != null) {
                if (drawable instanceof RoundedCornerDrawable) {
                    return drawable;
                } else if (drawable instanceof LayerDrawable) {
                    LayerDrawable ld = (LayerDrawable) drawable;
                    final int num = ld.getNumberOfLayers();
                    for (int i = 0; i < num; i++) {
                        Drawable d = ld.getDrawable(i);
                        ld.setDrawableByLayerId(ld.getId(i), fromDrawable(d, r));
                    }
                    return ld;
                }

                Bitmap bm = drawableToBitmap(drawable);
                if (bm != null) return new RoundedCornerDrawable(bm, r);
            }
            return drawable;
        }

        private static Bitmap drawableToBitmap(Drawable drawable) {
            if (drawable == null) return null;

            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }

            Bitmap bitmap;
            int width = Math.max(drawable.getIntrinsicWidth(), 2);
            int height = Math.max(drawable.getIntrinsicHeight(), 2);
            try {
                bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                bitmap = null;
            }
            return bitmap;
        }

        private void configureBounds(Canvas canvas) {
            Matrix canvasMatrix = canvas.getMatrix();

            applyScaleToRadii(canvasMatrix);
            mBounds.set(mBitmapRect);
        }

        private void applyScaleToRadii(Matrix m) {
            float[] values = new float[9];
            m.getValues(values);
            for (int i = 0; i < mRadii.length; i++) {
                mRadii[i] = mRadii[i] / values[0];
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.save();
            if (!mBoundsConfigured) {
                configureBounds(canvas);
                mBoundsConfigured = true;
            }
            mPath.addRoundRect(mBounds, mRadii, Path.Direction.CW);
            canvas.drawPath(mPath, mBitmapPaint);
            canvas.restore();
        }

        void setCornerRadii(float[] radii) {
            if (radii == null) return;
            if (radii.length != 8)
                throw new ArrayIndexOutOfBoundsException("radii[] needs 8 values");

            System.arraycopy(radii, 0, mRadii, 0, radii.length);
        }

        @Override
        public int getOpacity() {
            return (mBitmap == null || mBitmap.hasAlpha() || mBitmapPaint.getAlpha() < 255)
                    ? PixelFormat.TRANSLUCENT
                    : PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int alpha) {
            mBitmapPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            mBitmapPaint.setColorFilter(cf);
            invalidateSelf();
        }

        @Override
        public void setDither(boolean dither) {
            mBitmapPaint.setDither(dither);
            invalidateSelf();
        }

        @Override
        public void setFilterBitmap(boolean filter) {
            mBitmapPaint.setFilterBitmap(filter);
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return mBitmapWidth;
        }

        @Override
        public int getIntrinsicHeight() {
            return mBitmapHeight;
        }
    }
}
