/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package androidx.leanback.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.KeyEvent;

import androidx.leanback.R;
import androidx.leanback.util.MathUtil;

/**
 * A {@link Row} of playback controls to be displayed by a {@link PlaybackControlsRowPresenter}.
 *
 * This row consists of some optional item detail, a series of primary actions,
 * and an optional series of secondary actions.
 *
 * <p>
 * Controls are specified via an {@link ObjectAdapter} containing one or more
 * {@link Action}s.
 * </p>
 * <p>
 * Adapters should have their {@link PresenterSelector} set to an instance of
 * {@link ControlButtonPresenterSelector}.
 * </p>
 */
public class PlaybackControlsRow extends Row {

    /**
     * Listener for progress or duration change.
     */
    public static class OnPlaybackProgressCallback {
        /**
         * Called when {@link PlaybackControlsRow#getCurrentPosition()} changed.
         * @param row The PlaybackControlsRow that current time changed.
         * @param currentTimeMs Current time in milliseconds.
         */
        public void onCurrentPositionChanged(PlaybackControlsRow row, long currentTimeMs) {
        }

        /**
         * Called when {@link PlaybackControlsRow#getDuration()} changed.
         * @param row The PlaybackControlsRow that total time changed.
         * @param totalTime Total time in milliseconds.
         */
        public void onDurationChanged(PlaybackControlsRow row, long totalTime) {
        }

        /**
         * Called when {@link PlaybackControlsRow#getBufferedPosition()} changed.
         * @param row The PlaybackControlsRow that buffered progress changed.
         * @param bufferedProgressMs Buffered time in milliseconds.
         */
        public void onBufferedPositionChanged(PlaybackControlsRow row, long bufferedProgressMs) {
        }
    }

    /**
     * Base class for an action comprised of a series of icons.
     */
    public static abstract class MultiAction extends Action {
        private int mIndex;
        private Drawable[] mDrawables;
        private String[] mLabels;
        private String[] mLabels2;

        /**
         * Constructor
         * @param id The id of the Action.
         */
        public MultiAction(int id) {
            super(id);
        }

        /**
         * Sets the array of drawables.  The size of the array defines the range
         * of valid indices for this action.
         */
        public void setDrawables(Drawable[] drawables) {
            mDrawables = drawables;
            setIndex(0);
        }

        /**
         * Sets the array of strings used as labels.  The size of the array defines the range
         * of valid indices for this action.  The labels are used to define the accessibility
         * content description unless secondary labels are provided.
         */
        public void setLabels(String[] labels) {
            mLabels = labels;
            setIndex(0);
        }

        /**
         * Sets the array of strings used as secondary labels.  These labels are used
         * in place of the primary labels for accessibility content description only.
         */
        public void setSecondaryLabels(String[] labels) {
            mLabels2 = labels;
            setIndex(0);
        }

        /**
         * Returns the number of actions.
         */
        public int getActionCount() {
            if (mDrawables != null) {
                return mDrawables.length;
            }
            if (mLabels != null) {
                return mLabels.length;
            }
            return 0;
        }

        /**
         * Returns the drawable at the given index.
         */
        public Drawable getDrawable(int index) {
            return mDrawables == null ? null : mDrawables[index];
        }

        /**
         * Returns the label at the given index.
         */
        public String getLabel(int index) {
            return mLabels == null ? null : mLabels[index];
        }

        /**
         * Returns the secondary label at the given index.
         */
        public String getSecondaryLabel(int index) {
            return mLabels2 == null ? null : mLabels2[index];
        }

        /**
         * Increments the index, wrapping to zero once the end is reached.
         */
        public void nextIndex() {
            setIndex(mIndex < getActionCount() - 1 ? mIndex + 1 : 0);
        }

        /**
         * Sets the current index.
         */
        public void setIndex(int index) {
            mIndex = index;
            if (mDrawables != null) {
                setIcon(mDrawables[mIndex]);
            }
            if (mLabels != null) {
                setLabel1(mLabels[mIndex]);
            }
            if (mLabels2 != null) {
                setLabel2(mLabels2[mIndex]);
            }
        }

        /**
         * Returns the current index.
         */
        public int getIndex() {
            return mIndex;
        }
    }

    /**
     * An action displaying icons for play and pause.
     */
    public static class PlayPauseAction extends MultiAction {
        /**
         * Action index for the play icon.
         * @deprecated Use {@link #INDEX_PLAY}
         */
        @Deprecated
        public static final int PLAY = 0;

        /**
         * Action index for the pause icon.
         * @deprecated Use {@link #INDEX_PAUSE}
         */
        @Deprecated
        public static final int PAUSE = 1;

        /**
         * Action index for the play icon.
         */
        public static final int INDEX_PLAY = 0;

        /**
         * Action index for the pause icon.
         */
        public static final int INDEX_PAUSE = 1;

        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public PlayPauseAction(Context context) {
            super(R.id.lb_control_play_pause);
            Drawable[] drawables = new Drawable[2];
            drawables[INDEX_PLAY] = getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_play);
            drawables[INDEX_PAUSE] = getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_pause);
            setDrawables(drawables);

            String[] labels = new String[drawables.length];
            labels[INDEX_PLAY] = context.getString(R.string.lb_playback_controls_play);
            labels[INDEX_PAUSE] = context.getString(R.string.lb_playback_controls_pause);
            setLabels(labels);
            addKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            addKeyCode(KeyEvent.KEYCODE_MEDIA_PLAY);
            addKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE);
        }
    }

    /**
     * An action displaying an icon for fast forward.
     */
    public static class FastForwardAction extends MultiAction {
        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public FastForwardAction(Context context) {
            this(context, 1);
        }

        /**
         * Constructor
         * @param context Context used for loading resources.
         * @param numSpeeds Number of supported fast forward speeds.
         */
        public FastForwardAction(Context context, int numSpeeds) {
            super(R.id.lb_control_fast_forward);

            if (numSpeeds < 1) {
                throw new IllegalArgumentException("numSpeeds must be > 0");
            }
            Drawable[] drawables = new Drawable[numSpeeds + 1];
            drawables[0] = getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_fast_forward);
            setDrawables(drawables);

            String[] labels = new String[getActionCount()];
            labels[0] = context.getString(R.string.lb_playback_controls_fast_forward);

            String[] labels2 = new String[getActionCount()];
            labels2[0] = labels[0];

            for (int i = 1; i <= numSpeeds; i++) {
                int multiplier = i + 1;
                labels[i] = context.getResources().getString(
                        R.string.lb_control_display_fast_forward_multiplier, multiplier);
                labels2[i] = context.getResources().getString(
                        R.string.lb_playback_controls_fast_forward_multiplier, multiplier);
            }
            setLabels(labels);
            setSecondaryLabels(labels2);
            addKeyCode(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
        }
    }

    /**
     * An action displaying an icon for rewind.
     */
    public static class RewindAction extends MultiAction {
        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public RewindAction(Context context) {
            this(context, 1);
        }

        /**
         * Constructor
         * @param context Context used for loading resources.
         * @param numSpeeds Number of supported fast forward speeds.
         */
        public RewindAction(Context context, int numSpeeds) {
            super(R.id.lb_control_fast_rewind);

            if (numSpeeds < 1) {
                throw new IllegalArgumentException("numSpeeds must be > 0");
            }
            Drawable[] drawables = new Drawable[numSpeeds + 1];
            drawables[0] = getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_rewind);
            setDrawables(drawables);

            String[] labels = new String[getActionCount()];
            labels[0] = context.getString(R.string.lb_playback_controls_rewind);

            String[] labels2 = new String[getActionCount()];
            labels2[0] = labels[0];

            for (int i = 1; i <= numSpeeds; i++) {
                int multiplier = i + 1;
                labels[i] = labels[i] = context.getResources().getString(
                        R.string.lb_control_display_rewind_multiplier, multiplier);
                labels2[i] = context.getResources().getString(
                        R.string.lb_playback_controls_rewind_multiplier, multiplier);
            }
            setLabels(labels);
            setSecondaryLabels(labels2);
            addKeyCode(KeyEvent.KEYCODE_MEDIA_REWIND);
        }
    }

    /**
     * An action displaying an icon for skip next.
     */
    public static class SkipNextAction extends Action {
        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public SkipNextAction(Context context) {
            super(R.id.lb_control_skip_next);
            setIcon(getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_skip_next));
            setLabel1(context.getString(R.string.lb_playback_controls_skip_next));
            addKeyCode(KeyEvent.KEYCODE_MEDIA_NEXT);
        }
    }

    /**
     * An action displaying an icon for skip previous.
     */
    public static class SkipPreviousAction extends Action {
        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public SkipPreviousAction(Context context) {
            super(R.id.lb_control_skip_previous);
            setIcon(getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_skip_previous));
            setLabel1(context.getString(R.string.lb_playback_controls_skip_previous));
            addKeyCode(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    /**
     * An action displaying an icon for picture-in-picture.
     */
    public static class PictureInPictureAction extends Action {
        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public PictureInPictureAction(Context context) {
            super(R.id.lb_control_picture_in_picture);
            setIcon(getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_picture_in_picture));
            setLabel1(context.getString(R.string.lb_playback_controls_picture_in_picture));
            addKeyCode(KeyEvent.KEYCODE_WINDOW);
        }
    }

    /**
     * An action displaying an icon for "more actions".
     */
    public static class MoreActions extends Action {
        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public MoreActions(Context context) {
            super(R.id.lb_control_more_actions);
            setIcon(context.getResources().getDrawable(R.drawable.lb_ic_more));
            setLabel1(context.getString(R.string.lb_playback_controls_more_actions));
        }
    }

    /**
     * A base class for displaying a thumbs action.
     */
    public static abstract class ThumbsAction extends MultiAction {
        /**
         * Action index for the solid thumb icon.
         * @deprecated Use {@link #INDEX_SOLID}
         */
        @Deprecated
        public static final int SOLID = 0;

        /**
         * Action index for the outline thumb icon.
         * @deprecated Use {@link #INDEX_OUTLINE}
         */
        @Deprecated
        public static final int OUTLINE = 1;

        /**
         * Action index for the solid thumb icon.
         */
        public static final int INDEX_SOLID = 0;

        /**
         * Action index for the outline thumb icon.
         */
        public static final int INDEX_OUTLINE = 1;

        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public ThumbsAction(int id, Context context, int solidIconIndex, int outlineIconIndex) {
            super(id);
            Drawable[] drawables = new Drawable[2];
            drawables[INDEX_SOLID] = getStyledDrawable(context, solidIconIndex);
            drawables[INDEX_OUTLINE] = getStyledDrawable(context, outlineIconIndex);
            setDrawables(drawables);
        }
    }

    /**
     * An action displaying an icon for thumbs up.
     */
    public static class ThumbsUpAction extends ThumbsAction {
        public ThumbsUpAction(Context context) {
            super(R.id.lb_control_thumbs_up, context,
                    R.styleable.lbPlaybackControlsActionIcons_thumb_up,
                    R.styleable.lbPlaybackControlsActionIcons_thumb_up_outline);
            String[] labels = new String[getActionCount()];
            labels[INDEX_SOLID] = context.getString(R.string.lb_playback_controls_thumb_up);
            labels[INDEX_OUTLINE] = context.getString(
                    R.string.lb_playback_controls_thumb_up_outline);
            setLabels(labels);
        }
    }

    /**
     * An action displaying an icon for thumbs down.
     */
    public static class ThumbsDownAction extends ThumbsAction {
        public ThumbsDownAction(Context context) {
            super(R.id.lb_control_thumbs_down, context,
                    R.styleable.lbPlaybackControlsActionIcons_thumb_down,
                    R.styleable.lbPlaybackControlsActionIcons_thumb_down_outline);
            String[] labels = new String[getActionCount()];
            labels[INDEX_SOLID] = context.getString(R.string.lb_playback_controls_thumb_down);
            labels[INDEX_OUTLINE] = context.getString(
                    R.string.lb_playback_controls_thumb_down_outline);
            setLabels(labels);
        }
    }

    /**
     * An action for displaying three repeat states: none, one, or all.
     */
    public static class RepeatAction extends MultiAction {
        /**
         * Action index for the repeat-none icon.
         * @deprecated Use {@link #INDEX_NONE}
         */
        @Deprecated
        public static final int NONE = 0;

        /**
         * Action index for the repeat-all icon.
         * @deprecated Use {@link #INDEX_ALL}
         */
        @Deprecated
        public static final int ALL = 1;

        /**
         * Action index for the repeat-one icon.
         * @deprecated Use {@link #INDEX_ONE}
         */
        @Deprecated
        public static final int ONE = 2;

        /**
         * Action index for the repeat-none icon.
         */
        public static final int INDEX_NONE = 0;

        /**
         * Action index for the repeat-all icon.
         */
        public static final int INDEX_ALL = 1;

        /**
         * Action index for the repeat-one icon.
         */
        public static final int INDEX_ONE = 2;

        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public RepeatAction(Context context) {
            this(context, getIconHighlightColor(context));
        }

        /**
         * Constructor
         * @param context Context used for loading resources
         * @param highlightColor Color to display the repeat-all and repeat0one icons.
         */
        public RepeatAction(Context context, int highlightColor) {
            this(context, highlightColor, highlightColor);
        }

        /**
         * Constructor
         * @param context Context used for loading resources
         * @param repeatAllColor Color to display the repeat-all icon.
         * @param repeatOneColor Color to display the repeat-one icon.
         */
        public RepeatAction(Context context, int repeatAllColor, int repeatOneColor) {
            super(R.id.lb_control_repeat);
            Drawable[] drawables = new Drawable[3];
            BitmapDrawable repeatDrawable = (BitmapDrawable) getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_repeat);
            BitmapDrawable repeatOneDrawable = (BitmapDrawable) getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_repeat_one);
            drawables[INDEX_NONE] = repeatDrawable;
            drawables[INDEX_ALL] = repeatDrawable == null ? null
                    : new BitmapDrawable(context.getResources(),
                            createBitmap(repeatDrawable.getBitmap(), repeatAllColor));
            drawables[INDEX_ONE] = repeatOneDrawable == null ? null
                    : new BitmapDrawable(context.getResources(),
                            createBitmap(repeatOneDrawable.getBitmap(), repeatOneColor));
            setDrawables(drawables);

            String[] labels = new String[drawables.length];
            // Note, labels denote the action taken when clicked
            labels[INDEX_NONE] = context.getString(R.string.lb_playback_controls_repeat_all);
            labels[INDEX_ALL] = context.getString(R.string.lb_playback_controls_repeat_one);
            labels[INDEX_ONE] = context.getString(R.string.lb_playback_controls_repeat_none);
            setLabels(labels);
        }
    }

    /**
     * An action for displaying a shuffle icon.
     */
    public static class ShuffleAction extends MultiAction {
        /**
         * Action index for shuffle is off.
         * @deprecated Use {@link #INDEX_OFF}
         */
        @Deprecated
        public static final int OFF = 0;

        /**
         * Action index for shuffle is on.
         * @deprecated Use {@link #INDEX_ON}
         */
        @Deprecated
        public static final int ON = 1;

        /**
         * Action index for shuffle is off
         */
        public static final int INDEX_OFF = 0;

        /**
         * Action index for shuffle is on.
         */
        public static final int INDEX_ON = 1;

        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public ShuffleAction(Context context) {
            this(context, getIconHighlightColor(context));
        }

        /**
         * Constructor
         * @param context Context used for loading resources.
         * @param highlightColor Color for the highlighted icon state.
         */
        public ShuffleAction(Context context, int highlightColor) {
            super(R.id.lb_control_shuffle);
            BitmapDrawable uncoloredDrawable = (BitmapDrawable) getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_shuffle);
            Drawable[] drawables = new Drawable[2];
            drawables[INDEX_OFF] = uncoloredDrawable;
            drawables[INDEX_ON] = new BitmapDrawable(context.getResources(),
                    createBitmap(uncoloredDrawable.getBitmap(), highlightColor));
            setDrawables(drawables);

            String[] labels = new String[drawables.length];
            labels[INDEX_OFF] = context.getString(R.string.lb_playback_controls_shuffle_enable);
            labels[INDEX_ON] = context.getString(R.string.lb_playback_controls_shuffle_disable);
            setLabels(labels);
        }
    }

    /**
     * An action for displaying a HQ (High Quality) icon.
     */
    public static class HighQualityAction extends MultiAction {
        /**
         * Action index for high quality is off.
         * @deprecated Use {@link #INDEX_OFF}
         */
        @Deprecated
        public static final int OFF = 0;

        /**
         * Action index for high quality is on.
         * @deprecated Use {@link #INDEX_ON}
         */
        @Deprecated
        public static final int ON = 1;

        /**
         * Action index for high quality is off.
         */
        public static final int INDEX_OFF = 0;

        /**
         * Action index for high quality is on.
         */
        public static final int INDEX_ON = 1;

        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public HighQualityAction(Context context) {
            this(context, getIconHighlightColor(context));
        }

        /**
         * Constructor
         * @param context Context used for loading resources.
         * @param highlightColor Color for the highlighted icon state.
         */
        public HighQualityAction(Context context, int highlightColor) {
            super(R.id.lb_control_high_quality);
            BitmapDrawable uncoloredDrawable = (BitmapDrawable) getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_high_quality);
            Drawable[] drawables = new Drawable[2];
            drawables[INDEX_OFF] = uncoloredDrawable;
            drawables[INDEX_ON] = new BitmapDrawable(context.getResources(),
                    createBitmap(uncoloredDrawable.getBitmap(), highlightColor));
            setDrawables(drawables);

            String[] labels = new String[drawables.length];
            labels[INDEX_OFF] = context.getString(
                    R.string.lb_playback_controls_high_quality_enable);
            labels[INDEX_ON] = context.getString(
                    R.string.lb_playback_controls_high_quality_disable);
            setLabels(labels);
        }
    }

    /**
     * An action for displaying a CC (Closed Captioning) icon.
     */
    public static class ClosedCaptioningAction extends MultiAction {
        /**
         * Action index for closed caption is off.
         * @deprecated Use {@link #INDEX_OFF}
         */
        @Deprecated
        public static final int OFF = 0;

        /**
         * Action index for closed caption is on.
         * @deprecated Use {@link #INDEX_ON}
         */
        @Deprecated
        public static final int ON = 1;

        /**
         * Action index for closed caption is off.
         */
        public static final int INDEX_OFF = 0;

        /**
         * Action index for closed caption is on.
         */
        public static final int INDEX_ON = 1;


        /**
         * Constructor
         * @param context Context used for loading resources.
         */
        public ClosedCaptioningAction(Context context) {
            this(context, getIconHighlightColor(context));
        }

        /**
         * Constructor
         * @param context Context used for loading resources.
         * @param highlightColor Color for the highlighted icon state.
         */
        public ClosedCaptioningAction(Context context, int highlightColor) {
            super(R.id.lb_control_closed_captioning);
            BitmapDrawable uncoloredDrawable = (BitmapDrawable) getStyledDrawable(context,
                    R.styleable.lbPlaybackControlsActionIcons_closed_captioning);
            Drawable[] drawables = new Drawable[2];
            drawables[INDEX_OFF] = uncoloredDrawable;
            drawables[INDEX_ON] = new BitmapDrawable(context.getResources(),
                    createBitmap(uncoloredDrawable.getBitmap(), highlightColor));
            setDrawables(drawables);

            String[] labels = new String[drawables.length];
            labels[INDEX_OFF] = context.getString(
                    R.string.lb_playback_controls_closed_captioning_enable);
            labels[INDEX_ON] = context.getString(
                    R.string.lb_playback_controls_closed_captioning_disable);
            setLabels(labels);
        }
    }

    static Bitmap createBitmap(Bitmap bitmap, int color) {
        Bitmap dst = bitmap.copy(bitmap.getConfig(), true);
        Canvas canvas = new Canvas(dst);
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return dst;
    }

    static int getIconHighlightColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.playbackControlsIconHighlightColor,
                outValue, true)) {
            return outValue.data;
        }
        return context.getResources().getColor(R.color.lb_playback_icon_highlight_no_theme);
    }

    static Drawable getStyledDrawable(Context context, int index) {
        TypedValue outValue = new TypedValue();
        if (!context.getTheme().resolveAttribute(
                R.attr.playbackControlsActionIcons, outValue, false)) {
            return null;
        }
        TypedArray array = context.getTheme().obtainStyledAttributes(outValue.data,
                R.styleable.lbPlaybackControlsActionIcons);
        Drawable drawable = array.getDrawable(index);
        array.recycle();
        return drawable;
    }

    private Object mItem;
    private Drawable mImageDrawable;
    private ObjectAdapter mPrimaryActionsAdapter;
    private ObjectAdapter mSecondaryActionsAdapter;
    private long mTotalTimeMs;
    private long mCurrentTimeMs;
    private long mBufferedProgressMs;
    private OnPlaybackProgressCallback mListener;

    /**
     * Constructor for a PlaybackControlsRow that displays some details from
     * the given item.
     *
     * @param item The main item for the row.
     */
    public PlaybackControlsRow(Object item) {
        mItem = item;
    }

    /**
     * Constructor for a PlaybackControlsRow that has no item details.
     */
    public PlaybackControlsRow() {
    }

    /**
     * Returns the main item for the details page.
     */
    public final Object getItem() {
        return mItem;
    }

    /**
     * Sets a {link @Drawable} image for this row.
     * <p>If set after the row has been bound to a view, the adapter must be notified that
     * this row has changed.</p>
     *
     * @param drawable The drawable to set.
     */
    public final void setImageDrawable(Drawable drawable) {
        mImageDrawable = drawable;
    }

    /**
     * Sets a {@link Bitmap} for this row.
     * <p>If set after the row has been bound to a view, the adapter must be notified that
     * this row has changed.</p>
     *
     * @param context The context to retrieve display metrics from.
     * @param bm The bitmap to set.
     */
    public final void setImageBitmap(Context context, Bitmap bm) {
        mImageDrawable = new BitmapDrawable(context.getResources(), bm);
    }

    /**
     * Returns the image {@link Drawable} of this row.
     *
     * @return The overview's image drawable, or null if no drawable has been
     *         assigned.
     */
    public final Drawable getImageDrawable() {
        return mImageDrawable;
    }

    /**
     * Sets the primary actions {@link ObjectAdapter}.
     * <p>If set after the row has been bound to a view, the adapter must be notified that
     * this row has changed.</p>
     */
    public final void setPrimaryActionsAdapter(ObjectAdapter adapter) {
        mPrimaryActionsAdapter = adapter;
    }

    /**
     * Sets the secondary actions {@link ObjectAdapter}.
     * <p>If set after the row has been bound to a view, the adapter must be notified that
     * this row has changed.</p>
     */
    public final void setSecondaryActionsAdapter(ObjectAdapter adapter) {
        mSecondaryActionsAdapter = adapter;
    }

    /**
     * Returns the primary actions {@link ObjectAdapter}.
     */
    public final ObjectAdapter getPrimaryActionsAdapter() {
        return mPrimaryActionsAdapter;
    }

    /**
     * Returns the secondary actions {@link ObjectAdapter}.
     */
    public final ObjectAdapter getSecondaryActionsAdapter() {
        return mSecondaryActionsAdapter;
    }

    /**
     * Sets the total time in milliseconds for the playback controls row.
     * <p>If set after the row has been bound to a view, the adapter must be notified that
     * this row has changed.</p>
     * @deprecated Use {@link #setDuration(long)}
     */
    @Deprecated
    public void setTotalTime(int ms) {
        setDuration((long) ms);
    }

    /**
     * Sets the total time in milliseconds (long type) for the playback controls row.
     * @param ms Total time in milliseconds of long type.
     * @deprecated Use {@link #setDuration(long)}
     */
    @Deprecated
    public void setTotalTimeLong(long ms) {
        setDuration(ms);
    }

    /**
     * Sets the total time in milliseconds (long type) for the playback controls row.
     * If this row is bound to a view, the view will automatically
     * be updated to reflect the new value.
     * @param ms Total time in milliseconds of long type.
     */
    public void setDuration(long ms) {
        if (mTotalTimeMs != ms) {
            mTotalTimeMs = ms;
            if (mListener != null) {
                mListener.onDurationChanged(this, mTotalTimeMs);
            }
        }
    }

    /**
     * Returns the total time in milliseconds for the playback controls row.
     * @throws ArithmeticException If total time in milliseconds overflows int.
     * @deprecated use {@link #getDuration()}
     */
    @Deprecated
    public int getTotalTime() {
        return MathUtil.safeLongToInt(getTotalTimeLong());
    }

    /**
     * Returns the total time in milliseconds of long type for the playback controls row.
     * @deprecated use {@link #getDuration()}
     */
    @Deprecated
    public long getTotalTimeLong() {
        return mTotalTimeMs;
    }

    /**
     * Returns duration in milliseconds.
     * @return Duration in milliseconds.
     */
    public long getDuration() {
        return mTotalTimeMs;
    }

    /**
     * Sets the current time in milliseconds for the playback controls row.
     * If this row is bound to a view, the view will automatically
     * be updated to reflect the new value.
     * @deprecated use {@link #setCurrentPosition(long)}
     */
    @Deprecated
    public void setCurrentTime(int ms) {
        setCurrentTimeLong((long) ms);
    }

    /**
     * Sets the current time in milliseconds for playback controls row in long type.
     * @param ms Current time in milliseconds of long type.
     * @deprecated use {@link #setCurrentPosition(long)}
     */
    @Deprecated
    public void setCurrentTimeLong(long ms) {
        setCurrentPosition(ms);
    }

    /**
     * Sets the current time in milliseconds for the playback controls row.
     * If this row is bound to a view, the view will automatically
     * be updated to reflect the new value.
     * @param ms Current time in milliseconds of long type.
     */
    public void setCurrentPosition(long ms) {
        if (mCurrentTimeMs != ms) {
            mCurrentTimeMs = ms;
            if (mListener != null) {
                mListener.onCurrentPositionChanged(this, mCurrentTimeMs);
            }
        }
    }

    /**
     * Returns the current time in milliseconds for the playback controls row.
     * @throws ArithmeticException If current time in milliseconds overflows int.
     * @deprecated Use {@link #getCurrentPosition()}
     */
    @Deprecated
    public int getCurrentTime() {
        return MathUtil.safeLongToInt(getCurrentTimeLong());
    }

    /**
     * Returns the current time in milliseconds of long type for playback controls row.
     * @deprecated Use {@link #getCurrentPosition()}
     */
    @Deprecated
    public long getCurrentTimeLong() {
        return mCurrentTimeMs;
    }

    /**
     * Returns the current time in milliseconds of long type for playback controls row.
     */
    public long getCurrentPosition() {
        return mCurrentTimeMs;
    }

    /**
     * Sets the buffered progress for the playback controls row.
     * If this row is bound to a view, the view will automatically
     * be updated to reflect the new value.
     * @deprecated Use {@link #setBufferedPosition(long)}
     */
    @Deprecated
    public void setBufferedProgress(int ms) {
        setBufferedPosition((long) ms);
    }

    /**
     * Sets the buffered progress for the playback controls row.
     * @param ms Buffered progress in milliseconds of long type.
     * @deprecated Use {@link #setBufferedPosition(long)}
     */
    @Deprecated
    public void setBufferedProgressLong(long ms) {
        setBufferedPosition(ms);
    }

    /**
     * Sets the buffered progress for the playback controls row.
     * @param ms Buffered progress in milliseconds of long type.
     */
    public void setBufferedPosition(long ms) {
        if (mBufferedProgressMs != ms) {
            mBufferedProgressMs = ms;
            if (mListener != null) {
                mListener.onBufferedPositionChanged(this, mBufferedProgressMs);
            }
        }
    }
    /**
     * Returns the buffered progress for the playback controls row.
     * @throws ArithmeticException If buffered progress in milliseconds overflows int.
     * @deprecated Use {@link #getBufferedPosition()}
     */
    @Deprecated
    public int getBufferedProgress() {
        return MathUtil.safeLongToInt(getBufferedPosition());
    }

    /**
     * Returns the buffered progress of long type for the playback controls row.
     * @deprecated Use {@link #getBufferedPosition()}
     */
    @Deprecated
    public long getBufferedProgressLong() {
        return mBufferedProgressMs;
    }

    /**
     * Returns the buffered progress of long type for the playback controls row.
     */
    public long getBufferedPosition() {
        return mBufferedProgressMs;
    }

    /**
     * Returns the Action associated with the given keycode, or null if no associated action exists.
     * Searches the primary adapter first, then the secondary adapter.
     */
    public Action getActionForKeyCode(int keyCode) {
        Action action = getActionForKeyCode(getPrimaryActionsAdapter(), keyCode);
        if (action != null) {
            return action;
        }
        return getActionForKeyCode(getSecondaryActionsAdapter(), keyCode);
    }

    /**
     * Returns the Action associated with the given keycode, or null if no associated action exists.
     */
    public Action getActionForKeyCode(ObjectAdapter adapter, int keyCode) {
        if (adapter != mPrimaryActionsAdapter && adapter != mSecondaryActionsAdapter) {
            throw new IllegalArgumentException("Invalid adapter");
        }
        for (int i = 0; i < adapter.size(); i++) {
            Action action = (Action) adapter.get(i);
            if (action.respondsToKeyCode(keyCode)) {
                return action;
            }
        }
        return null;
    }

    /**
     * Sets a listener to be called when the playback state changes.
     */
    public void setOnPlaybackProgressChangedListener(OnPlaybackProgressCallback listener) {
        mListener = listener;
    }
}
