package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

/**
 * Ambient light ("Ambilight") in the black bars around the video.
 * <p>
 * Whenever the video does not fill the player (a 16:9 video on a 21:9 screen, a 4:3 video or a vertical Short
 * on a 16:9 screen, a 2.39:1 video that is letterboxed...), the empty bars are filled with a soft glow that
 * follows the colours of the picture edge next to them, like the bias lighting of some TVs.
 * <p>
 * How it works: about 15 times per second the video surface is copied into a tiny bitmap (64 px wide, scaled by
 * the GPU: {@link PixelCopy} for a SurfaceView, {@link TextureView#getBitmap(Bitmap)} for a TextureView). For each
 * bar, the strip of picture next to it is reduced to 16 colours along the edge (weighted towards the edge), smoothed
 * over time (fast on scene cuts, calm on slow drifts), blurred, slightly saturated and drawn as a small bitmap
 * stretched with bilinear filtering, fading out towards the screen edge.
 * <p>
 * Cost: nothing at all when the video fills the screen (no copy is made). Otherwise one 64 px wide copy per frame and
 * a redraw only when the colours visibly change. The view sits behind the video and the player UI, so controls,
 * subtitles and dialogs are never covered.
 * <p>
 * Limits: needs Android 7.0+ with a SurfaceView (PixelCopy). Tunneled playback and protected (DRM) surfaces cannot
 * be copied: the bars then simply stay black.
 */
public class AmbientLightView extends View {
    private static final long FRAME_MS = 66; // ~15 fps: plenty for a diffuse glow
    private static final long RETRY_MS = 2_000; // after a failed copy (tunneled/protected surface)
    private static final int COPY_W = 64;
    /** Cells along each bar. */
    private static final int N = 16;
    /** Weight of each sampled depth step, 0 = right at the picture edge: the light comes from the edge. */
    private static final float[] DEPTH_W = {0.40f, 0.25f, 0.14f, 0.08f, 0.05f, 0.04f, 0.02f, 0.02f};
    /** Columns of the rendered bar bitmap, from the picture edge to the screen edge. */
    private static final int OUT = 6;
    private static final int LEFT = 0, TOP = 1, RIGHT = 2, BOTTOM = 3;

    private final ViewGroup mVideoRoot;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mVideo = new Rect(), mRoot = new Rect(), mSrc = new Rect(), mDst = new Rect();
    /** Bar sizes in px: left, top, right, bottom. */
    private final int[] mBar = new int[4];
    private final float[][] mColors = new float[4][]; // smoothed colours, RGB per cell
    private final float[][] mSent = new float[4][];
    private final Bitmap[] mOut = new Bitmap[4];
    private final int[] mOutPx = new int[OUT * N];
    private Bitmap mCopy;
    private int[] mPx;
    private boolean mRunning, mCopyPending;
    /** A copy failed (tunneled or protected surface): no new try before then (uptime ms). */
    private long mRetryAt;
    private long mShownAt;
    private float mGain = -1;

    public AmbientLightView(Context context, ViewGroup videoRoot) {
        super(context);
        mVideoRoot = videoRoot;
        setWillNotDraw(false);
    }

    private final Runnable mTick = this::tick;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mRunning = true;
        mHandler.post(mTick);
    }

    @Override
    protected void onDetachedFromWindow() {
        mRunning = false;
        mHandler.removeCallbacks(mTick);
        super.onDetachedFromWindow();
    }

    private void tick() {
        if (!mRunning) {
            return;
        }
        long delay = FRAME_MS;
        View video = mVideoRoot.getChildCount() > 0 ? mVideoRoot.getChildAt(0) : null;
        if (video == null || !measureBars(video) || getVisibility() != VISIBLE) {
            clear();
        } else if (!mCopyPending && SystemClock.uptimeMillis() >= mRetryAt) {
            delay = copyFrame(video) ? FRAME_MS : RETRY_MS;
        }
        mHandler.postDelayed(mTick, delay);
    }

    /** Where the video is drawn now (after the player's aspect/zoom/flip). @return true when there is a bar to fill */
    private boolean measureBars(View video) {
        if (mVideoRoot.getRotation() != 0 || !video.isShown() || video.getWidth() == 0 || video.getHeight() == 0
                || !video.getGlobalVisibleRect(mVideo) || !getGlobalVisibleRect(mRoot)) {
            return false;
        }
        mBar[LEFT] = mVideo.left - mRoot.left;
        mBar[TOP] = mVideo.top - mRoot.top;
        mBar[RIGHT] = mRoot.right - mVideo.right;
        mBar[BOTTOM] = mRoot.bottom - mVideo.bottom;
        mVideo.offset(-mRoot.left, -mRoot.top);
        // A couple of pixels is rounding, not a bar.
        int min = Math.max(2, Math.min(getWidth(), getHeight()) / 100);
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            if (mBar[i] < min) {
                mBar[i] = 0;
            } else {
                any = true;
            }
        }
        return any;
    }

    /** @return false when the surface cannot be copied */
    private boolean copyFrame(View video) {
        int h = Math.max(2, Math.round((float) COPY_W * video.getHeight() / video.getWidth()));
        if (mCopy == null || mCopy.getHeight() != h) {
            mCopy = Bitmap.createBitmap(COPY_W, h, Bitmap.Config.ARGB_8888);
            mPx = new int[COPY_W * h];
        }
        if (video instanceof TextureView) {
            TextureView tv = (TextureView) video;
            if (!tv.isAvailable()) {
                return false;
            }
            tv.getBitmap(mCopy);
            onFrame();
            return true;
        }
        if (video instanceof SurfaceView && Build.VERSION.SDK_INT >= 24) {
            SurfaceView sv = (SurfaceView) video;
            if (!sv.getHolder().getSurface().isValid()) {
                return false;
            }
            try {
                mCopyPending = true;
                PixelCopy.request(sv, mCopy, result -> {
                    mCopyPending = false;
                    if (result != PixelCopy.SUCCESS) {
                        mRetryAt = SystemClock.uptimeMillis() + RETRY_MS;
                    } else if (mRunning) {
                        onFrame();
                    }
                }, mHandler);
                return true;
            } catch (IllegalArgumentException e) {
                mCopyPending = false; // surface just went away
                return false;
            }
        }
        return false;
    }

    private void onFrame() {
        int w = mCopy.getWidth(), h = mCopy.getHeight();
        mCopy.getPixels(mPx, 0, w, 0, 0, w, h);
        boolean flipped = mVideoRoot.getScaleX() < 0;
        boolean changed = false;
        for (int side = 0; side < 4; side++) {
            if (mBar[side] == 0) {
                mColors[side] = null;
                continue;
            }
            int s = side;
            if (flipped && (side == LEFT || side == RIGHT)) {
                s = side == LEFT ? RIGHT : LEFT; // the picture's left edge is drawn on the right
            }
            mColors[side] = smooth(mColors[side], sample(s, w, h));
            changed |= !same(mColors[side], mSent[side]);
        }
        long now = System.currentTimeMillis();
        if (mGain < 0) {
            mShownAt = now; // bars appear: fade in over 0.4 s, no flash
        }
        float gain = Math.min(1f, (now - mShownAt) / 400f);
        if (!changed && Math.abs(gain - mGain) < 0.01f) {
            return; // nothing the eye would see: no redraw
        }
        mGain = gain;
        for (int side = 0; side < 4; side++) {
            mSent[side] = mColors[side] == null ? null : mColors[side].clone();
            if (mColors[side] != null) {
                mOut[side] = render(mOut[side], mColors[side], side, gain);
            }
        }
        invalidate();
    }

    /**
     * N colours along one edge of the copied picture. Each cell averages the strip from the edge inwards
     * (a sixth of the picture), weighted towards the edge.
     */
    private float[] sample(int side, int w, int h) {
        boolean vertical = side == LEFT || side == RIGHT; // the bar runs along the picture height
        int along = vertical ? h : w, across = vertical ? w : h;
        int region = Math.max(DEPTH_W.length, across / 6);
        float[] out = new float[N * 3];
        for (int c = 0; c < N; c++) {
            int a0 = c * along / N, a1 = Math.max(a0 + 1, (c + 1) * along / N);
            float r = 0, g = 0, b = 0;
            for (int d = 0; d < DEPTH_W.length; d++) {
                int depth = d * region / DEPTH_W.length;
                int pos = side == LEFT || side == TOP ? depth : across - 1 - depth;
                int sr = 0, sg = 0, sb = 0, n = 0;
                for (int a = a0; a < a1; a++) {
                    int px = vertical ? mPx[a * w + pos] : mPx[pos * w + a];
                    sr += (px >> 16) & 0xff;
                    sg += (px >> 8) & 0xff;
                    sb += px & 0xff;
                    n++;
                }
                r += sr / (float) n * DEPTH_W[d];
                g += sg / (float) n * DEPTH_W[d];
                b += sb / (float) n * DEPTH_W[d];
            }
            out[c * 3] = r;
            out[c * 3 + 1] = g;
            out[c * 3 + 2] = b;
        }
        return out;
    }

    /** Temporal smoothing: follows scene cuts fast, slow drifts calmly. */
    private static float[] smooth(float[] cur, float[] next) {
        if (cur == null) {
            return next;
        }
        for (int i = 0; i < cur.length; i++) {
            float d = Math.abs(next[i] - cur[i]);
            float k = d > 60 ? 0.7f : d > 25 ? 0.4f : 0.22f;
            cur[i] += (next[i] - cur[i]) * k;
        }
        return cur;
    }

    private static boolean same(float[] a, float[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > 1.5f) {
                return false;
            }
        }
        return true;
    }

    /**
     * Colours -> small bitmap for one bar, as diffuse light rather than a mirror: a blur along the edge, a bit
     * more saturation and a soft fall-off from the picture to the screen edge.
     */
    private Bitmap render(Bitmap bm, float[] colors, int side, float gain) {
        float[] row = colors.clone();
        for (int pass = 0; pass < 2; pass++) { // [1 4 6 4 1] / 16, twice
            float[] t = new float[row.length];
            for (int c = 0; c < N; c++) {
                for (int k = 0; k < 3; k++) {
                    t[c * 3 + k] = (row[Math.max(0, c - 2) * 3 + k] + 4 * row[Math.max(0, c - 1) * 3 + k]
                            + 6 * row[c * 3 + k] + 4 * row[Math.min(N - 1, c + 1) * 3 + k]
                            + row[Math.min(N - 1, c + 2) * 3 + k]) / 16f;
                }
            }
            row = t;
        }
        boolean vertical = side == LEFT || side == RIGHT;
        int bw = vertical ? OUT : N, bh = vertical ? N : OUT;
        if (bm == null || bm.getWidth() != bw || bm.getHeight() != bh) {
            bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        }
        for (int c = 0; c < N; c++) {
            float m = (row[c * 3] + row[c * 3 + 1] + row[c * 3 + 2]) / 3f;
            for (int o = 0; o < OUT; o++) {
                float d = o / (OUT - 1f); // 0 at the picture, 1 at the screen edge
                float fade = (0.92f - 0.5f * d * d) * gain;
                int[] v = new int[3];
                for (int k = 0; k < 3; k++) {
                    float x = (m + (row[c * 3 + k] - m) * 1.35f) * fade;
                    v[k] = x < 0 ? 0 : x > 255 ? 255 : (int) x;
                }
                // Column/row 0 of the bitmap is the screen edge for left/top bars.
                int depth = side == LEFT || side == TOP ? OUT - 1 - o : o;
                int x = vertical ? depth : c, y = vertical ? c : depth;
                mOutPx[y * bw + x] = 0xFF000000 | (v[0] << 16) | (v[1] << 8) | v[2];
            }
        }
        bm.setPixels(mOutPx, 0, bw, 0, 0, bw, bh);
        return bm;
    }

    private void clear() {
        if (mGain < 0) {
            return;
        }
        mGain = -1;
        for (int i = 0; i < 4; i++) {
            mColors[i] = mSent[i] = null;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mGain < 0) {
            return;
        }
        int w = getWidth(), h = getHeight();
        for (int side = 0; side < 4; side++) {
            Bitmap b = mOut[side];
            if (mBar[side] == 0 || mColors[side] == null || b == null) {
                continue;
            }
            switch (side) {
                case LEFT: mDst.set(0, mVideo.top, mBar[LEFT], mVideo.bottom); break;
                case RIGHT: mDst.set(w - mBar[RIGHT], mVideo.top, w, mVideo.bottom); break;
                case TOP: mDst.set(mVideo.left, 0, mVideo.right, mBar[TOP]); break;
                default: mDst.set(mVideo.left, h - mBar[BOTTOM], mVideo.right, h); break;
            }
            mSrc.set(0, 0, b.getWidth(), b.getHeight());
            canvas.drawBitmap(b, mSrc, mDst, mPaint);
        }
    }
}
