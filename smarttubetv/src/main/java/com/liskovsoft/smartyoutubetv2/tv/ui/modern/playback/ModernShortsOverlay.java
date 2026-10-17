package com.liskovsoft.smartyoutubetv2.tv.ui.modern.playback;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.modern.common.ModernColorScheme;

/**
 * Shorts screen: vertical video on the start side, details and actions on the end side.<br/>
 * The background is drawn around the video area, the video surface shows through it.
 */
public class ModernShortsOverlay extends FrameLayout {
    private static final long TICK_MS = 250;
    private final Paint mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mBackground = new Path();
    private final RectF mVideoRect = new RectF();
    private TextView mTitle;
    private View mChannel;
    private View mSubscribe;
    private LinearLayout mActions;
    private LinearLayout mPanel;
    private Listener mListener;
    private final Paint mTrackPaint = new Paint();
    private final Paint mProgressPaint = new Paint();
    private final Path mVideoClip = new Path();
    private final Runnable mTick = this::updatePlaybackState;
    private ImageView mState;
    private TextView mTime;
    private float mProgress;

    public interface Listener {
        void onShortsAction(int actionId);
        void onShortsNext();
        void onShortsPrevious();
        void onShortsTogglePlayback();
        void onShortsMore();
        boolean isShortsActionActive(int actionId);
        long getShortsPositionMs();
        long getShortsDurationMs();
        boolean isShortsPlaying();
    }

    public ModernShortsOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        mBackgroundPaint.setColor(ModernColorScheme.background(context));
        mTrackPaint.setColor(ContextCompat.getColor(context, R.color.modern_white_30));
        mProgressPaint.setColor(ModernColorScheme.accent(context));
    }

    public static ModernShortsOverlay inflate(ViewGroup parent) {
        return (ModernShortsOverlay) LayoutInflater.from(parent.getContext()).inflate(R.layout.modern_shorts_overlay, parent, false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPanel = findViewById(R.id.modern_shorts_panel);
        mTitle = findViewById(R.id.modern_shorts_title);
        mChannel = findViewById(R.id.modern_shorts_channel);
        mSubscribe = findViewById(R.id.modern_shorts_subscribe);
        mActions = findViewById(R.id.modern_shorts_actions);
        mState = findViewById(R.id.modern_shorts_state);
        mTime = findViewById(R.id.modern_shorts_time);

        bindRow(mSubscribe, R.drawable.modern_ic_add, getContext().getString(R.string.action_subscribe));
        mChannel.setOnClickListener(v -> mListener.onShortsAction(R.id.action_channel));
        mSubscribe.setOnClickListener(v -> {
            mListener.onShortsAction(R.id.action_subscribe);
            // The player updates the state asynchronously
            postDelayed(this::refreshActions, 500);
        });

        addAction(R.id.action_thumbs_up, R.string.action_like);
        addAction(R.id.action_thumbs_down, R.string.action_dislike);
        addAction(R.id.action_chat, R.string.open_comments);
        View more = addButton(R.drawable.modern_ic_more_vert, R.string.context_menu);
        more.setOnClickListener(v -> mListener.onShortsMore());
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void bind(Video video) {
        mTitle.setText(video.getTitle());
        bindRow(mChannel, 0, video.getAuthor());
        ImageView avatar = mChannel.findViewById(R.id.modern_shorts_row_icon);
        ImageViewCompat.setImageTintList(avatar, null);
        refreshActions();
    }

    public void setChannelIcon(String url) {
        if (url != null && mChannel != null) {
            Glide.with(getContext()).load(url).transform(new CircleCrop()).into((ImageView) mChannel.findViewById(R.id.modern_shorts_row_icon));
        }
    }

    /**
     * Re-read like/dislike states from the player actions.
     */
    public void refreshActions() {
        boolean subscribed = mListener != null && mListener.isShortsActionActive(R.id.action_subscribe);
        bindRow(mSubscribe, subscribed ? R.drawable.modern_ic_notifications_active_filled : R.drawable.modern_ic_add,
                getContext().getString(subscribed ? R.string.subscribed_to_channel : R.string.action_subscribe));

        for (int i = 0; i < mActions.getChildCount(); i++) {
            View button = mActions.getChildAt(i);
            Object id = button.getTag();
            if (id instanceof Integer && mListener != null) {
                int icon = ModernActionStyle.iconOf((Integer) id, mListener.isShortsActionActive((Integer) id));
                if (icon != 0) {
                    ((ImageView) button).setImageResource(icon);
                }
            }
        }
    }

    public void setVideoRect(int left, int top, int right, int bottom) {
        mVideoRect.set(left, top, right, bottom);

        // Play icon in the middle of the video, time at its bottom start
        int stateSize = getResources().getDimensionPixelSize(R.dimen.modern_shorts_state_size);
        LayoutParams stateParams = (LayoutParams) mState.getLayoutParams();
        stateParams.leftMargin = (left + right - stateSize) / 2;
        stateParams.topMargin = (top + bottom - stateSize) / 2;
        mState.setLayoutParams(stateParams);

        int margin = getResources().getDimensionPixelSize(R.dimen.modern_shorts_time_margin);
        LayoutParams timeParams = (LayoutParams) mTime.getLayoutParams();
        timeParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
        timeParams.leftMargin = left + margin;
        timeParams.bottomMargin = getHeight() > 0 ? getHeight() - bottom + margin : margin;
        mTime.setLayoutParams(timeParams);

        invalidate();
    }

    /**
     * Like the reference: a thin progress line at the bottom of the video; while paused a play icon in the middle and
     * the time. OK toggles the playback.
     */
    private void updatePlaybackState() {
        removeCallbacks(mTick);

        if (mListener == null || !isShown()) {
            return;
        }

        long duration = mListener.getShortsDurationMs();
        long position = Math.max(0, mListener.getShortsPositionMs());
        float progress = duration > 0 ? Math.min(1f, position / (float) duration) : 0;
        boolean paused = !mListener.isShortsPlaying();

        if (progress != mProgress) {
            mProgress = progress;
            invalidate();
        }

        mState.setVisibility(paused ? View.VISIBLE : View.GONE);
        mTime.setVisibility(paused && duration > 0 ? View.VISIBLE : View.GONE);

        if (paused && duration > 0) {
            mTime.setText(String.format("%s / %s", formatTime(position), formatTime(duration)));
        }

        postDelayed(mTick, TICK_MS);
    }

    private static String formatTime(long ms) {
        long seconds = ms / 1_000;
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updatePlaybackState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePlaybackState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mTick);
    }

    public boolean isPanelFocused() {
        return mPanel.hasFocus();
    }

    /**
     * Keys while the video (not the panel) is focused.
     */
    public boolean onVideoKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return isHandledKey(event.getKeyCode());
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mListener.onShortsNext();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                mListener.onShortsPrevious();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                mListener.onShortsTogglePlayback();
                // The new state shows at once, not at the next tick
                post(this::updatePlaybackState);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mTitle.setFocusable(false);
                mChannel.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
        }

        return false;
    }

    /**
     * Left from the panel goes back to the video.
     */
    public boolean onPanelKey(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && movePanelFocus(event.getKeyCode())) {
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
            View focused = findFocus();
            View next = focused != null ? focused.focusSearch(View.FOCUS_LEFT) : null;
            if (next == null || !isInside(next, mPanel)) {
                focused.clearFocus();
                return true;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            View focused = findFocus();
            if (focused != null) {
                focused.clearFocus();
                return true;
            }
        }

        return false;
    }

    /**
     * Panel order, top to bottom: channel, subscribe, actions row. Down from a row always reaches the first item
     * of the next one (a geometric search picks whatever button is closest), the ends of the panel keep the focus.
     */
    private boolean movePanelFocus(int keyCode) {
        View focused = findFocus();

        if (focused == null) {
            return false;
        }

        boolean inActions = focused.getParent() == mActions;

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focused == mChannel) {
                    return mSubscribe.requestFocus();
                }
                if (focused == mSubscribe) {
                    return mActions.getChildAt(0).requestFocus();
                }
                return inActions;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (inActions) {
                    return mSubscribe.requestFocus();
                }
                if (focused == mSubscribe) {
                    return mChannel.requestFocus();
                }
                return focused == mChannel;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return !inActions || focused == mActions.getChildAt(mActions.getChildCount() - 1);
        }

        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Everything but the video area (rounded)
        float radius = getResources().getDimension(R.dimen.modern_shorts_video_radius);
        mBackground.reset();
        mBackground.setFillType(Path.FillType.EVEN_ODD);
        mBackground.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        if (!mVideoRect.isEmpty()) {
            mBackground.addRoundRect(mVideoRect, radius, radius, Path.Direction.CW);
        }
        canvas.drawPath(mBackground, mBackgroundPaint);

        if (!mVideoRect.isEmpty()) {
            // Progress line along the bottom of the video, inside its rounded corners
            int height = getResources().getDimensionPixelSize(R.dimen.modern_shorts_progress_height);
            mVideoClip.reset();
            mVideoClip.addRoundRect(mVideoRect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(mVideoClip);
            canvas.drawRect(mVideoRect.left, mVideoRect.bottom - height, mVideoRect.right, mVideoRect.bottom, mTrackPaint);
            canvas.drawRect(mVideoRect.left, mVideoRect.bottom - height, mVideoRect.left + mVideoRect.width() * mProgress, mVideoRect.bottom, mProgressPaint);
            canvas.restore();
        }

        super.onDraw(canvas);
    }

    private static boolean isHandledKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private static boolean isInside(View view, View parent) {
        View current = view;
        while (current != null) {
            if (current == parent) {
                return true;
            }
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return false;
    }

    private void bindRow(View row, int iconResId, CharSequence text) {
        ImageView icon = row.findViewById(R.id.modern_shorts_row_icon);
        if (iconResId != 0) {
            icon.setImageResource(iconResId);
            ImageViewCompat.setImageTintList(icon, ContextCompat.getColorStateList(getContext(), R.color.modern_pill_text));
        }
        ((TextView) row.findViewById(R.id.modern_shorts_row_text)).setText(text);
    }

    private void addAction(int actionId, int labelResId) {
        View button = addButton(ModernActionStyle.iconOf(actionId, false), labelResId);
        button.setTag(actionId);
        button.setOnClickListener(v -> {
            mListener.onShortsAction(actionId);
            // The player updates the state asynchronously
            postDelayed(this::refreshActions, 500);
        });
    }

    private View addButton(int iconResId, int labelResId) {
        ImageView button = new ImageView(getContext());
        int size = getResources().getDimensionPixelSize(R.dimen.modern_player_button);
        int padding = (size - getResources().getDimensionPixelSize(R.dimen.modern_player_button_icon)) / 2;
        button.setPadding(padding, padding, padding, padding);
        button.setImageResource(iconResId);
        button.setBackgroundResource(R.drawable.modern_player_button_bg_grouped);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setContentDescription(getContext().getString(labelResId));
        ColorStateList tint = ContextCompat.getColorStateList(getContext(), R.color.modern_pill_text);
        ImageViewCompat.setImageTintList(button, tint);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.rightMargin = getResources().getDimensionPixelSize(R.dimen.modern_player_group_gap) * 2;
        mActions.addView(button, params);
        return button;
    }
}
