package com.liskovsoft.smartyoutubetv2.tv.ui.modern.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.common.misc.FormatInfoPrefetcher;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTheme;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernTiles;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * Tile of a video, a playlist or a channel.<br/>
 * Rounded thumbnail (circle for channels) with duration badge and resume bar, title and two metadata lines.
 * The focus ring and the playlist stack are drawn outside of the thumbnail bounds.
 */
public class ModernVideoCard extends LinearLayout {
    public static final int KIND_VIDEO = 0;
    public static final int KIND_PLAYLIST = 1;
    public static final int KIND_CHANNEL = 2;
    private static final long PREVIEW_START_DELAY_MS = 2_000;
    private static final long PREFETCH_DELAY_MS = 1_000;
    private static final long PREVIEW_STOP_DELAY_MS = 500;
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final Path mStackPath = new Path();
    private float mRingInset;
    private float mRingRadius;
    private float mThumbRadius;
    private float mRingAlpha;
    private ValueAnimator mRingAnimator;
    private FrameLayout mThumbFrame;
    private ImageView mThumb;
    private View mDurationBox;
    private ImageView mDurationIcon;
    private TextView mDuration;
    private View mResume;
    private View mResumeProgress;
    private final Rect mTitleClip = new Rect();
    private TextView mTitle;
    private TextView mLine1;
    private LinearLayout mLine2Container;
    private TextView mLabel;
    private TextView mLine2;
    private int mPrimaryColor;
    private int mSecondaryColor;
    private float mProgress;
    private int mKind = KIND_VIDEO;
    private int mChannelSize;
    private Video mVideo;
    private int mPreviewType = MainUIData.CARD_PREVIEW_DISABLED;
    private boolean mMultilineTitle = true;
    private boolean mTitleAutoScroll;
    private EmbedPlayerView mPreviewPlayer;
    private final Runnable mStartPreview = this::startPreview;
    private final Runnable mPrefetch = this::prefetch;

    public ModernVideoCard(Context context) {
        super(context);
    }

    public ModernVideoCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ModernVideoCard(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public static int kindOf(Video video) {
        // Channels section with the old look: the channels are items of the uploads group
        if (video.isChannel() || !video.hasVideo() && video.belongsToChannelUploads()) {
            return KIND_CHANNEL;
        }

        if (!video.hasVideo() && (video.hasPlaylist() || video.isPlaylistAsChannel() || video.isMix())) {
            return KIND_PLAYLIST;
        }

        return KIND_VIDEO;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        setWillNotDraw(false);

        mThumbFrame = findViewById(R.id.modern_card_thumb_frame);
        mThumb = findViewById(R.id.modern_card_thumb);
        mDurationBox = findViewById(R.id.modern_card_duration_box);
        mDurationIcon = findViewById(R.id.modern_card_duration_icon);
        mDuration = findViewById(R.id.modern_card_duration);
        mResume = findViewById(R.id.modern_card_resume);
        mResumeProgress = findViewById(R.id.modern_card_resume_progress);
        mTitle = findViewById(R.id.modern_card_title);
        mLine1 = findViewById(R.id.modern_card_line1);
        mLine2Container = findViewById(R.id.modern_card_line2_container);
        mLabel = findViewById(R.id.modern_card_label);
        mLine2 = findViewById(R.id.modern_card_line2);

        mPrimaryColor = ContextCompat.getColor(getContext(), R.color.modern_text_primary);
        mSecondaryColor = ContextCompat.getColor(getContext(), R.color.modern_text_secondary);

        mRingPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStrokeWidth(getResources().getDimension(R.dimen.modern_focus_ring_width));
        mRingPaint.setColor(ContextCompat.getColor(getContext(), R.color.modern_focus_ring));
        mRingInset = getResources().getDimension(R.dimen.modern_focus_ring_inset);
        mRingRadius = getResources().getDimension(R.dimen.modern_focus_ring_radius);
        mThumbRadius = getResources().getDimension(R.dimen.modern_tile_thumb_radius);
        mChannelSize = ModernTiles.size(getContext(), R.dimen.modern_channel_tile_size);
        mStackPaint.setColor(ContextCompat.getColor(getContext(), R.color.modern_white_20));

        // The marquee view adds its own vertical padding: keep the measured title layout
        mTitle.setPadding(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.modern_tile_title_bottom));
        applyLineHeight(mTitle, getResources().getDimensionPixelSize(R.dimen.modern_tile_title_line_height));
        clipThumbnail();
    }

    public ImageView getThumbView() {
        return mThumb;
    }

    public int getKind() {
        return mKind;
    }

    /**
     * Inline preview of the focused video (SmartTube "card preview" setting).
     */
    public void setPreviewType(int previewType) {
        mPreviewType = previewType;
    }

    /**
     * Card text settings: title on two lines (like the reference) or one,
     * a one line title that doesn't fit scrolls while the card has the focus (at the card text scroll speed).
     */
    public void setTitleOptions(boolean multiline, boolean autoScroll, float scrollSpeed) {
        mMultilineTitle = multiline;
        mTitleAutoScroll = autoScroll;
        ViewUtil.setTextScrollSpeed(mTitle, scrollSpeed); // card text scroll speed setting
    }

    /**
     * UI tweak setting: square thumbnails (and focus ring) when rounded corners are off.
     */
    public void setRoundedCorners(boolean rounded) {
        float thumbRadius = rounded ? getResources().getDimension(R.dimen.modern_tile_thumb_radius) : 0;
        float ringRadius = rounded ? getResources().getDimension(R.dimen.modern_focus_ring_radius) : getResources().getDimension(R.dimen.modern_focus_ring_width);

        if (thumbRadius != mThumbRadius) {
            mThumbRadius = thumbRadius;
            mRingRadius = ringRadius;
            invalidateThumbOutline();
            invalidate();
        }
    }

    public void bind(Video video) {
        stopPreview(true);
        mVideo = video;
        mKind = kindOf(video);
        mTitle.setText(video.getTitle());

        CardMetadata meta = CardMetadata.parse(video.getSecondTitle());
        setTextOrHide(mLine1, meta.line1);
        setTextOrHide(mLabel, meta.label);
        setTextOrHide(mLine2, meta.line2);
        mLine2Container.setVisibility(meta.label == null && meta.line2 == null ? View.GONE : View.VISIBLE);

        if (video.isLive) {
            mDuration.setText(R.string.badge_live);
            mDurationBox.setBackground(ModernColorScheme.liveBackground(getContext()));
        } else {
            mDuration.setText(video.badge);
            mDurationBox.setBackgroundResource(R.drawable.modern_duration_bg);
        }
        mDurationIcon.setVisibility(mKind == KIND_PLAYLIST ? View.VISIBLE : View.GONE);
        mDurationBox.setVisibility(mKind == KIND_CHANNEL || TextUtils.isEmpty(mDuration.getText()) ? View.GONE : View.VISIBLE);

        // Count progress that very close to zero. E.g. when user closed video immediately.
        mProgress = video.percentWatched <= 0 ? 0 : Math.max(video.percentWatched, 1) / 100f;
        mResume.setVisibility(mProgress > 0 && mKind == KIND_VIDEO ? View.VISIBLE : View.GONE);

        int gravity = mKind == KIND_CHANNEL ? Gravity.CENTER_HORIZONTAL : Gravity.START;
        mTitle.setGravity(gravity);
        mLine1.setGravity(gravity | Gravity.CENTER_VERTICAL);
        mLine2Container.setGravity(gravity | Gravity.CENTER_VERTICAL);
        setTitleScrolling(false);

        mStackPaint.setColor(ContextCompat.getColor(getContext(), R.color.modern_white_20));
        invalidateThumbOutline();
        updateResumeProgress();
        invalidate();
    }

    /**
     * Playlist stack color (derived from the thumbnail).
     */
    public void setStackColor(int color) {
        mStackPaint.setColor(color);
        invalidate();
    }

    /**
     * Card size depends on the focus state of its shelf. Channels keep a fixed size.
     */
    public void setThumbSize(int width, int height) {
        if (mKind == KIND_CHANNEL) {
            width = mChannelSize;
            height = mChannelSize;
        }

        setSize(mThumbFrame, width, height);
        setSize(mTitle, width, -1);
        setSize(mLine1, width, -1);
        setSize(mLine2Container, width, -1);
        updateResumeProgress();
    }

    private int titleLines() {
        return mKind == KIND_CHANNEL || !mMultilineTitle ? 1 : 2;
    }

    private void setTitleScrolling(boolean scroll) {
        boolean marquee = scroll && mTitleAutoScroll && titleLines() == 1;

        if (marquee) {
            mTitle.setSingleLine(true);
            mTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            mTitle.setMarqueeRepeatLimit(-1);
            mTitle.setSelected(true);
        } else {
            mTitle.setSelected(false);
            mTitle.setSingleLine(false);
            mTitle.setMaxLines(titleLines());
            mTitle.setEllipsize(TextUtils.TruncateAt.END);
        }
    }

    public int getThumbHeight() {
        return mThumbFrame.getLayoutParams().height;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        mTitle.setTextColor(gainFocus ? mPrimaryColor : mSecondaryColor);
        animateRing(gainFocus ? 1 : 0);
        setTitleScrolling(gainFocus);

        if (gainFocus) {
            if (mKind == KIND_VIDEO && mVideo != null && mVideo.videoId != null) {
                if (mPreviewType != MainUIData.CARD_PREVIEW_DISABLED) {
                    Utils.postDelayed(mStartPreview, PREVIEW_START_DELAY_MS);
                } else {
                    // The inline preview loads the video itself
                    Utils.postDelayed(mPrefetch, PREFETCH_DELAY_MS);
                }
            }
        } else {
            Utils.removeCallbacks(mPrefetch);
            stopPreview(false);
        }
    }

    @Override
    public boolean performClick() {
        Utils.removeCallbacks(mPrefetch);
        stopPreview(true);
        return super.performClick();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopPreview(true);
        super.onDetachedFromWindow();
    }

    /**
     * The focus rests on the video: load its formats so it opens without waiting.
     */
    private void prefetch() {
        if (mVideo != null && (hasFocus() || isFocused())) {
            FormatInfoPrefetcher.instance().prefetch(mVideo.videoId);
        }
    }

    private void startPreview() {
        if (mVideo == null || !hasFocus() && !isFocused()) {
            return;
        }

        if (mPreviewPlayer == null) {
            mPreviewPlayer = new EmbedPlayerView(getContext());
            mPreviewPlayer.setQuality(EmbedPlayerView.QUALITY_NORMAL);
            mPreviewPlayer.setUseController(false);
            mPreviewPlayer.setMute(mPreviewType == MainUIData.CARD_PREVIEW_MUTED);
            mPreviewPlayer.setBackgroundColor(Color.BLACK);
            mPreviewPlayer.setAlpha(0f);
            // Below the duration badge and the resume bar
            mThumbFrame.addView(mPreviewPlayer, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mPreviewPlayer.animate().alpha(1f).setDuration(ModernTheme.ANIM_PREVIEW_FADE_MS).setInterpolator(ModernTheme.previewFadeInterpolator());
        }

        mPreviewPlayer.openVideo(mVideo);
    }

    private void stopPreview(boolean immediately) {
        Utils.removeCallbacks(mStartPreview);

        if (mPreviewPlayer == null) {
            return;
        }

        EmbedPlayerView player = mPreviewPlayer;
        mPreviewPlayer = null;

        if (immediately) {
            player.finish();
            mThumbFrame.removeView(player);
        } else {
            player.setMute(true);
            player.animate().alpha(0f).setDuration(ModernTheme.ANIM_FAST_MS);
            Utils.postDelayed(() -> {
                player.finish();
                mThumbFrame.removeView(player);
            }, PREVIEW_STOP_DELAY_MS);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        // A scrolling title draws its text moved: never past its own bounds (the card doesn't clip its children)
        if (Build.VERSION.SDK_INT >= 18 && (mTitleClip.width() != mTitle.getWidth() || mTitleClip.height() != mTitle.getHeight())) {
            mTitleClip.set(0, 0, mTitle.getWidth(), mTitle.getHeight());
            mTitle.setClipBounds(mTitleClip);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        float left = mThumbFrame.getLeft();
        float top = mThumbFrame.getTop();
        float right = mThumbFrame.getRight();
        float bottom = mThumbFrame.getBottom();

        if (mKind == KIND_PLAYLIST) {
            drawStack(canvas, left, top, right);
        }

        if (mRingAlpha <= 0) {
            return;
        }

        float halfStroke = mRingPaint.getStrokeWidth() / 2;
        mRect.set(left - mRingInset + halfStroke, top - mRingInset + halfStroke,
                right + mRingInset - halfStroke, bottom + mRingInset - halfStroke);
        mRingPaint.setAlpha(Math.round(mRingAlpha * 255));
        float radius = mKind == KIND_CHANNEL ? mRect.width() / 2 : mRingRadius - halfStroke;
        canvas.drawRoundRect(mRect, radius, radius, mRingPaint);
    }

    private void drawStack(Canvas canvas, float left, float top, float right) {
        float inset = getResources().getDimension(R.dimen.modern_stack_inset);
        float height = getResources().getDimension(R.dimen.modern_stack_height);
        float gap = getResources().getDimension(R.dimen.modern_stack_gap);
        float radius = getResources().getDimension(R.dimen.modern_stack_radius);
        float stackBottom = top - gap;
        float stackTop = stackBottom - height;

        // Rounded only at the top
        mStackPath.reset();
        mRect.set(left + inset, stackTop, right - inset, stackTop + radius * 2);
        mStackPath.addRoundRect(mRect, new float[] {radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
        canvas.save();
        canvas.clipRect(left + inset, stackTop, right - inset, stackBottom);
        canvas.drawPath(mStackPath, mStackPaint);
        canvas.restore();
    }

    private void animateRing(float target) {
        if (mRingAnimator != null) {
            mRingAnimator.cancel();
        }

        mRingAnimator = ValueAnimator.ofFloat(mRingAlpha, target);
        mRingAnimator.setDuration(ModernTheme.ANIM_FAST_MS);
        mRingAnimator.setInterpolator(ModernTheme.fastInterpolator());
        mRingAnimator.addUpdateListener(animation -> {
            mRingAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        mRingAnimator.start();
    }

    private void updateResumeProgress() {
        int width = mThumbFrame.getLayoutParams() != null ? mThumbFrame.getLayoutParams().width : 0;
        ViewGroup.LayoutParams params = mResumeProgress.getLayoutParams();
        params.width = Math.round(width * Math.min(mProgress, 1f));
        mResumeProgress.setLayoutParams(params);
    }

    /**
     * Rounded (or round) thumbnail from Android 5.0, square before.
     */
    private void clipThumbnail() {
        if (Build.VERSION.SDK_INT >= 21) {
            mThumbFrame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    if (mKind == KIND_CHANNEL) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    } else {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mThumbRadius);
                    }
                }
            });
            mThumbFrame.setClipToOutline(true);
        }
    }

    private void invalidateThumbOutline() {
        if (Build.VERSION.SDK_INT >= 21) {
            mThumbFrame.invalidateOutline();
        }
    }

    private static void applyLineHeight(TextView view, int lineHeightPx) {
        int fontHeight = view.getPaint().getFontMetricsInt(null);
        view.setLineSpacing(lineHeightPx - fontHeight, 1f);
    }

    private static void setTextOrHide(TextView view, CharSequence text) {
        view.setText(text);
        view.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private static void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        if (height > 0) {
            params.height = height;
        }
        view.setLayoutParams(params);
    }
}
