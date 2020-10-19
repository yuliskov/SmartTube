/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.leanback.R;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ControlButtonPresenterSelector;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.OnActionClickedListener;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackSeekDataProvider;
import androidx.leanback.widget.PlaybackSeekUi;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowPresenter;
import androidx.leanback.widget.SeekBar;
import androidx.leanback.widget.ThumbsBar;
import com.liskovsoft.smartyoutubetv2.common.utils.DateFormatter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.ControlBarPresenter.OnControlClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.ControlBarPresenter.OnControlSelectedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.MaxIconNumVideoPlayerGlue.OnQualityInfoCallback;

import java.util.Arrays;

/**
 * A PlaybackTransportRowPresenter renders a {@link PlaybackControlsRow} to display a
 * series of playback control buttons. Typically this row will be the first row in a fragment
 * such as the {@link androidx.leanback.app.PlaybackSupportFragment}.
 *
 * <p>The detailed description is rendered using a {@link Presenter} passed in
 * {@link #setDescriptionPresenter(Presenter)}.  This can be an instance of
 * {@link AbstractDetailsDescriptionPresenter}.  The application can access the
 * detailed description ViewHolder from {@link ViewHolder#getDescriptionViewHolder()}.
 * </p>
 */
public class PlaybackTransportRowPresenter extends PlaybackRowPresenter {

    static class BoundData extends PlaybackControlsPresenter.BoundData {
        ViewHolder mRowViewHolder;
    }

    /**
     * A ViewHolder for the PlaybackControlsRow supporting seek UI.
     */
    public class ViewHolder extends PlaybackRowPresenter.ViewHolder implements PlaybackSeekUi {
        private static final long SPEED_INCREASE_PERIOD_MS = 1000;
        private static final double SPEED_INCREASE_FACTOR = 1.5;
        private static final long START_SEEK_INCREMENT_MS = 10_000;
        final Presenter.ViewHolder mDescriptionViewHolder;
        final ImageView mImageView;
        final ViewGroup mDescriptionDock;
        final ViewGroup mControlsDock;
        final ViewGroup mSecondaryControlsDock;
        final TextView mTotalTime;
        final TextView mCurrentTime;
        final TextView mQualityInfo;
        final TextView mCurrentDate;
        final ViewGroup mAdditionalInfo;
        final SeekBar mProgressBar;
        final ThumbsBar mThumbsBar;
        long mTotalTimeInMs = Long.MIN_VALUE;
        long mCurrentTimeInMs = Long.MIN_VALUE;
        long mSecondaryProgressInMs;
        final StringBuilder mTempBuilder = new StringBuilder();
        ControlBarPresenter.ViewHolder mControlsVh;
        ControlBarPresenter.ViewHolder mSecondaryControlsVh;
        BoundData mControlsBoundData = new BoundData();
        BoundData mSecondaryBoundData = new BoundData();
        Presenter.ViewHolder mSelectedViewHolder;
        Object mSelectedItem;
        PlaybackControlsRow.PlayPauseAction mPlayPauseAction;
        int mThumbHeroIndex = -1;

        Client mSeekClient;
        boolean mInSeek;
        PlaybackSeekDataProvider mSeekDataProvider;
        long[] mPositions;
        int mPositionsLength;
        long mSeekIncrementMs = -1;
        long mSeekStartTimeMs;

        // MOD: update quality info
        final OnQualityInfoCallback mQualityListener = this::setQualityInfo;

        final PlaybackControlsRow.OnPlaybackProgressCallback mListener =
                new PlaybackControlsRow.OnPlaybackProgressCallback() {
            @Override
            public void onCurrentPositionChanged(PlaybackControlsRow row, long ms) {
                setCurrentPosition(ms);
            }

            @Override
            public void onDurationChanged(PlaybackControlsRow row, long ms) {
                setTotalTime(ms);
            }

            @Override
            public void onBufferedPositionChanged(PlaybackControlsRow row, long ms) {
                setBufferedPosition(ms);
            }
        };

        void updateProgressInSeek(boolean forward) {
            long newPos;
            long pos = mCurrentTimeInMs;
            if (mPositionsLength > 0) {
                int index = Arrays.binarySearch(mPositions, 0, mPositionsLength, pos);
                int thumbHeroIndex;
                if (forward) {
                    if (index >= 0) {
                        // found it, seek to neighbour key position at higher side
                        if (index < mPositionsLength - 1) {
                            newPos = mPositions[index + 1];
                            thumbHeroIndex = index + 1;
                        } else {
                            newPos = mTotalTimeInMs;
                            thumbHeroIndex = index;
                        }
                    } else {
                        // not found, seek to neighbour key position at higher side.
                        int insertIndex = -1 - index;
                        if (insertIndex <= mPositionsLength - 1) {
                            newPos = mPositions[insertIndex];
                            thumbHeroIndex = insertIndex;
                        } else {
                            newPos = mTotalTimeInMs;
                            thumbHeroIndex = insertIndex > 0 ? insertIndex - 1 : 0;
                        }
                    }
                } else {
                    if (index >= 0) {
                        // found it, seek to neighbour key position at lower side.
                        if (index > 0) {
                            newPos = mPositions[index - 1];
                            thumbHeroIndex = index - 1;
                        } else {
                            newPos = 0;
                            thumbHeroIndex = 0;
                        }
                    } else {
                        // not found, seek to neighbour key position at lower side.
                        int insertIndex = -1 - index;
                        if (insertIndex > 0) {
                            newPos = mPositions[insertIndex - 1];
                            thumbHeroIndex = insertIndex - 1;
                        } else {
                            newPos = 0;
                            thumbHeroIndex = 0;
                        }
                    }
                }
                updateThumbsInSeek(thumbHeroIndex, forward);
            } else {
                long interval = calculateSeekIncrement();
                newPos = pos + (forward ? interval : -interval);
                if (newPos > mTotalTimeInMs) {
                    newPos = mTotalTimeInMs;
                } else if (newPos < 0) {
                    newPos = 0;
                }
            }
            double ratio = (double) newPos / mTotalTimeInMs;     // Range: [0, 1]
            mProgressBar.setProgress((int) (ratio * Integer.MAX_VALUE)); // Could safely cast to int
            mSeekClient.onSeekPositionChanged(newPos);
        }

        void resetSeekIncrement() {
            mSeekIncrementMs = -1;
        }

        /**
         * Implement non-linear seek speed<br/>
         * By increasing seek speed by 1.5 every 1 second.
         */
        long calculateSeekIncrement() {
            if (mSeekIncrementMs == -1) {
                mSeekStartTimeMs = System.currentTimeMillis();
                mSeekIncrementMs = START_SEEK_INCREMENT_MS;
            } else {
                // increase seek speed by 1.5 every 1 second
                long timePassed = System.currentTimeMillis() - mSeekStartTimeMs;
                long timeFactor = timePassed / SPEED_INCREASE_PERIOD_MS;
                if (timeFactor == 1) {
                    mSeekStartTimeMs = System.currentTimeMillis();
                    mSeekIncrementMs *= SPEED_INCREASE_FACTOR;
                }
            }

            return mSeekIncrementMs;
        }

        void updateThumbsInSeek(int thumbHeroIndex, boolean forward) {
            if (mThumbHeroIndex == thumbHeroIndex) {
                return;
            }

            final int totalNum = mThumbsBar.getChildCount();
            if (totalNum < 0 || (totalNum & 1) == 0) {
                throw new RuntimeException();
            }
            final int heroChildIndex = totalNum / 2;
            final int start = Math.max(thumbHeroIndex - (totalNum / 2), 0);
            final int end = Math.min(thumbHeroIndex + (totalNum / 2), mPositionsLength - 1);
            final int newRequestStart;
            final int newRequestEnd;

            if (mThumbHeroIndex < 0) {
                // first time
                newRequestStart = start;
                newRequestEnd = end;
            } else {
                forward = thumbHeroIndex > mThumbHeroIndex;
                final int oldStart = Math.max(mThumbHeroIndex - (totalNum / 2), 0);
                final int oldEnd = Math.min(mThumbHeroIndex + (totalNum / 2),
                        mPositionsLength - 1);
                if (forward) {
                    newRequestStart = Math.max(oldEnd + 1, start);
                    newRequestEnd = end;
                    // overlapping area directly assign bitmap from previous result
                    for (int i = start; i <= newRequestStart - 1; i++) {
                        mThumbsBar.setThumbBitmap(heroChildIndex + (i - thumbHeroIndex),
                                mThumbsBar.getThumbBitmap(heroChildIndex + (i - mThumbHeroIndex)));
                    }
                } else {
                    newRequestEnd = Math.min(oldStart - 1, end);
                    newRequestStart = start;
                    // overlapping area directly assign bitmap from previous result in backward
                    for (int i = end; i >= newRequestEnd + 1; i--) {
                        mThumbsBar.setThumbBitmap(heroChildIndex + (i - thumbHeroIndex),
                                mThumbsBar.getThumbBitmap(heroChildIndex + (i - mThumbHeroIndex)));
                    }
                }
            }
            // processing new requests with mThumbHeroIndex updated
            mThumbHeroIndex = thumbHeroIndex;
            if (forward) {
                for (int i = newRequestStart; i <= newRequestEnd; i++) {
                    mSeekDataProvider.getThumbnail(i, mThumbResult);
                }
            } else {
                for (int i = newRequestEnd; i >= newRequestStart; i--) {
                    mSeekDataProvider.getThumbnail(i, mThumbResult);
                }
            }
            // set thumb bitmaps outside (start , end) to null
            for (int childIndex = 0; childIndex < heroChildIndex - mThumbHeroIndex + start;
                    childIndex++) {
                mThumbsBar.setThumbBitmap(childIndex, null);
            }
            for (int childIndex = heroChildIndex + end - mThumbHeroIndex + 1;
                    childIndex < totalNum; childIndex++) {
                mThumbsBar.setThumbBitmap(childIndex, null);
            }
        }

        PlaybackSeekDataProvider.ResultCallback mThumbResult =
                new PlaybackSeekDataProvider.ResultCallback() {
                    @Override
                    public void onThumbnailLoaded(Bitmap bitmap, int index) {
                        int childIndex = index - (mThumbHeroIndex - mThumbsBar.getChildCount() / 2);
                        if (childIndex < 0 || childIndex >= mThumbsBar.getChildCount()) {
                            return;
                        }
                        mThumbsBar.setThumbBitmap(childIndex, bitmap);
                    }
        };

        boolean onForward() {
            if (!startSeek()) {
                return false;
            }
            updateProgressInSeek(true);
            return true;
        }

        boolean onBackward() {
            if (!startSeek()) {
                return false;
            }
            updateProgressInSeek(false);
            return true;
        }
        /**
         * Constructor of ViewHolder of PlaybackTransportRowPresenter
         * @param rootView Root view of the ViewHolder.
         * @param descriptionPresenter The presenter that will be used to create description
         *                             ViewHolder. The description view will be added into tree.
         */
        public ViewHolder(View rootView, Presenter descriptionPresenter) {
            super(rootView);
            mImageView = (ImageView) rootView.findViewById(R.id.image);
            mDescriptionDock = (ViewGroup) rootView.findViewById(R.id.description_dock);
            mCurrentTime = (TextView) rootView.findViewById(R.id.current_time);
            mTotalTime = (TextView) rootView.findViewById(R.id.total_time);
            mQualityInfo = (TextView) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.quality_info);
            mCurrentDate = (TextView) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.current_date);
            mAdditionalInfo = (ViewGroup) rootView.findViewById(com.liskovsoft.smartyoutubetv2.tv.R.id.additional_info);
            updateDateLabel();
            mProgressBar = (SeekBar) rootView.findViewById(R.id.playback_progress);
            mProgressBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onProgressBarClicked(ViewHolder.this);
                }
            });
            mProgressBar.setOnKeyListener(new View.OnKeyListener() {

                @Override
                public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                    // when in seek only allow this keys
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_DPAD_UP:
                        case KeyEvent.KEYCODE_DPAD_DOWN:
                            if (!mInSeek) {
                                enableCompactMode(false);
                            }

                            // eat DPAD UP/DOWN in seek mode
                            return mInSeek;
                        case KeyEvent.KEYCODE_DPAD_LEFT:
                        case KeyEvent.KEYCODE_MINUS:
                        case KeyEvent.KEYCODE_MEDIA_REWIND:
                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                                enableCompactMode(true);
                                onBackward();
                            } else {
                                // MOD: resume immediately after seeking
                                stopSeek(false);
                            }
                            return true;
                        case KeyEvent.KEYCODE_DPAD_RIGHT:
                        case KeyEvent.KEYCODE_PLUS:
                        case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                                enableCompactMode(true);
                                onForward();
                            } else {
                                // MOD: resume immediately after seeking
                                stopSeek(false);
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: // MOD: act as OK?
                        case KeyEvent.KEYCODE_DPAD_CENTER:
                        case KeyEvent.KEYCODE_ENTER:
                            if (!mInSeek) {
                                return false;
                            }
                            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                                stopSeek(false);
                            }
                            return true;
                        case KeyEvent.KEYCODE_BACK:
                        case KeyEvent.KEYCODE_ESCAPE:
                            if (!mInSeek) {
                                return false;
                            }
                            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                                // SeekBar does not support cancel in accessibility mode, so always
                                // "confirm" if accessibility is on.
                                stopSeek(Build.VERSION.SDK_INT >= 21
                                        ? !mProgressBar.isAccessibilityFocused() : true);
                            }
                            return true;
                    }
                    return false;
                }
            });
            mProgressBar.setAccessibilitySeekListener(new SeekBar.AccessibilitySeekListener() {
                @Override
                public boolean onAccessibilitySeekForward() {
                    return onForward();
                }

                @Override
                public boolean onAccessibilitySeekBackward() {
                    return onBackward();
                }
            });
            mProgressBar.setMax(Integer.MAX_VALUE); //current progress will be a fraction of this
            mControlsDock = (ViewGroup) rootView.findViewById(R.id.controls_dock);
            mSecondaryControlsDock =
                    (ViewGroup) rootView.findViewById(R.id.secondary_controls_dock);
            mDescriptionViewHolder = descriptionPresenter == null ? null :
                    descriptionPresenter.onCreateViewHolder(mDescriptionDock);
            if (mDescriptionViewHolder != null) {
                mDescriptionDock.addView(mDescriptionViewHolder.view);
            }
            mThumbsBar = (ThumbsBar) rootView.findViewById(R.id.thumbs_row);
        }

        /**
         * @return The ViewHolder for description.
         */
        public final Presenter.ViewHolder getDescriptionViewHolder() {
            return mDescriptionViewHolder;
        }

        @Override
        public void setPlaybackSeekUiClient(Client client) {
            mSeekClient = client;
        }

        boolean startSeek() {
            if (mInSeek) {
                return true;
            }
            if (mSeekClient == null || !mSeekClient.isSeekEnabled()
                    || mTotalTimeInMs <= 0) {
                return false;
            }
            mInSeek = true;
            mSeekClient.onSeekStarted();
            mSeekDataProvider = mSeekClient.getPlaybackSeekDataProvider();
            mPositions = mSeekDataProvider != null ? mSeekDataProvider.getSeekPositions() : null;
            if (mPositions != null) {
                int pos = Arrays.binarySearch(mPositions, mTotalTimeInMs);
                if (pos >= 0) {
                    mPositionsLength = pos + 1;
                } else {
                    mPositionsLength = -1 - pos;
                }
            } else {
                mPositionsLength = 0;
            }

            return true;
        }

        void stopSeek(boolean cancelled) {
            if (!mInSeek) {
                return;
            }
            mInSeek = false;
            mSeekClient.onSeekFinished(cancelled);
            if (mSeekDataProvider != null) {
                mSeekDataProvider.reset();
            }
            mThumbHeroIndex = -1;
            mThumbsBar.clearThumbBitmaps();
            mSeekDataProvider = null;
            mPositions = null;
            mPositionsLength = 0;
            resetSeekIncrement();
        }

        // MOD: seek ui tweaks
        void enableCompactMode(boolean enable) {
            if (enable) {
                mControlsVh.view.setVisibility(View.GONE);
                mSecondaryControlsVh.view.setVisibility(View.INVISIBLE);
                mDescriptionViewHolder.view.setVisibility(View.INVISIBLE);
                mAdditionalInfo.setVisibility(View.INVISIBLE);
                mThumbsBar.setVisibility(View.VISIBLE);
            } else {
                mControlsVh.view.setVisibility(View.VISIBLE);
                mSecondaryControlsVh.view.setVisibility(View.VISIBLE);
                mDescriptionViewHolder.view.setVisibility(View.VISIBLE);
                mAdditionalInfo.setVisibility(View.VISIBLE);
                mThumbsBar.setVisibility(View.INVISIBLE);
            }
        }

        void dispatchItemSelection() {
            if (!isSelected()) {
                return;
            }
            if (mSelectedViewHolder == null) {
                if (getOnItemViewSelectedListener() != null) {
                    getOnItemViewSelectedListener().onItemSelected(null, null,
                            ViewHolder.this, getRow());
                }
            } else {
                if (getOnItemViewSelectedListener() != null) {
                    getOnItemViewSelectedListener().onItemSelected(mSelectedViewHolder,
                            mSelectedItem, ViewHolder.this, getRow());
                }
            }
        };

        Presenter getPresenter(boolean primary) {
            ObjectAdapter adapter = primary
                    ? ((PlaybackControlsRow) getRow()).getPrimaryActionsAdapter()
                    : ((PlaybackControlsRow) getRow()).getSecondaryActionsAdapter();
            if (adapter == null) {
                return null;
            }
            if (adapter.getPresenterSelector() instanceof ControlButtonPresenterSelector) {
                ControlButtonPresenterSelector selector =
                        (ControlButtonPresenterSelector) adapter.getPresenterSelector();
                return selector.getSecondaryPresenter();
            }
            return adapter.getPresenter(adapter.size() > 0 ? adapter.get(0) : null);
        }

        /**
         * Returns the TextView that showing total time label. This method might be used in
         * {@link #onSetDurationLabel}.
         * @return The TextView that showing total time label.
         */
        public final TextView getDurationView() {
            return mTotalTime;
        }

        /**
         * Called to update total time label. Default implementation updates the TextView
         * {@link #getDurationView()}. Subclass might override.
         * @param totalTimeMs Total duration of the media in milliseconds.
         */
        protected void onSetDurationLabel(long totalTimeMs) {
            if (mTotalTime != null) {
                formatTime(totalTimeMs, mTempBuilder);
                mTotalTime.setText(mTempBuilder.toString());
            }
        }

        void setTotalTime(long totalTimeMs) {
            if (mTotalTimeInMs != totalTimeMs) {
                mTotalTimeInMs = totalTimeMs;
                onSetDurationLabel(totalTimeMs);
            }
        }

        /**
         * Returns the TextView that showing current position label. This method might be used in
         * {@link #onSetCurrentPositionLabel}.
         * @return The TextView that showing current position label.
         */
        public final TextView getCurrentPositionView() {
            return mCurrentTime;
        }

        /**
         * Called to update current time label. Default implementation updates the TextView
         * {@link #getCurrentPositionView}. Subclass might override.
         * @param currentTimeMs Current playback position in milliseconds.
         */
        protected void onSetCurrentPositionLabel(long currentTimeMs) {
            if (mCurrentTime != null) {
                formatTime(currentTimeMs, mTempBuilder);
                mCurrentTime.setText(mTempBuilder.toString());
            }
        }

        void setCurrentPosition(long currentTimeMs) {
            if (currentTimeMs != mCurrentTimeInMs) {
                mCurrentTimeInMs = currentTimeMs;
                onSetCurrentPositionLabel(currentTimeMs);
            }
            if (!mInSeek) {
                int progressRatio = 0;
                if (mTotalTimeInMs > 0) {
                    // Use ratio to represent current progres
                    double ratio = (double) mCurrentTimeInMs / mTotalTimeInMs;     // Range: [0, 1]
                    progressRatio = (int) (ratio * Integer.MAX_VALUE);  // Could safely cast to int
                }
                mProgressBar.setProgress((int) progressRatio);
            }
        }

        void setBufferedPosition(long progressMs) {
            mSecondaryProgressInMs = progressMs;
            // Solve the progress bar by using ratio
            double ratio = (double) progressMs / mTotalTimeInMs;           // Range: [0, 1]
            double progressRatio = ratio * Integer.MAX_VALUE;   // Could safely cast to int
            mProgressBar.setSecondaryProgress((int) progressRatio);
        }

        void setQualityInfo(String content) {
            if (content != null) {
                mQualityInfo.setText(content);
            }
        }

        void updateDateLabel() {
            mCurrentDate.setText(DateFormatter.getCurrentDateShort(mCurrentDate.getContext()));
        }
    }

    static void formatTime(long ms, StringBuilder sb) {
        sb.setLength(0);
        if (ms < 0) {
            sb.append("--");
            return;
        }
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        seconds -= minutes * 60;
        minutes -= hours * 60;

        if (hours > 0) {
            sb.append(hours).append(':');
            if (minutes < 10) {
                sb.append('0');
            }
        }
        sb.append(minutes).append(':');
        if (seconds < 10) {
            sb.append('0');
        }
        sb.append(seconds);
    }

    float mDefaultSeekIncrement = 0.01f;
    int mProgressColor = Color.TRANSPARENT;
    int mSecondaryProgressColor = Color.TRANSPARENT;
    boolean mProgressColorSet;
    boolean mSecondaryProgressColorSet;
    Presenter mDescriptionPresenter;
    ControlBarPresenter mPlaybackControlsPresenter;
    ControlBarPresenter mSecondaryControlsPresenter;
    OnActionClickedListener mOnActionClickedListener;

    private final OnControlSelectedListener mOnControlSelectedListener =
            new OnControlSelectedListener() {
        @Override
        public void onControlSelected(Presenter.ViewHolder itemViewHolder, Object item,
                ControlBarPresenter.BoundData data) {
            ViewHolder vh = ((BoundData) data).mRowViewHolder;
            if (vh.mSelectedViewHolder != itemViewHolder || vh.mSelectedItem != item) {
                vh.mSelectedViewHolder = itemViewHolder;
                vh.mSelectedItem = item;
                vh.dispatchItemSelection();
            }
        }
    };

    private final OnControlClickedListener mOnControlClickedListener =
            new OnControlClickedListener() {
        @Override
        public void onControlClicked(Presenter.ViewHolder itemViewHolder, Object item,
                ControlBarPresenter.BoundData data) {
            ViewHolder vh = ((BoundData) data).mRowViewHolder;
            if (vh.getOnItemViewClickedListener() != null) {
                vh.getOnItemViewClickedListener().onItemClicked(itemViewHolder, item,
                        vh, vh.getRow());
            }
            if (mOnActionClickedListener != null && item instanceof Action) {
                mOnActionClickedListener.onActionClicked((Action) item);
            }
        }
    };

    public PlaybackTransportRowPresenter() {
        setHeaderPresenter(null);
        setSelectEffectEnabled(false);

        mPlaybackControlsPresenter = new ControlBarPresenter(com.liskovsoft.smartyoutubetv2.tv.R.layout.lb_control_bar);
        mPlaybackControlsPresenter.setDefaultFocusToMiddle(false);
        mSecondaryControlsPresenter = new ControlBarPresenter(com.liskovsoft.smartyoutubetv2.tv.R.layout.lb_control_bar);
        mSecondaryControlsPresenter.setDefaultFocusToMiddle(false);

        mPlaybackControlsPresenter.setOnControlSelectedListener(mOnControlSelectedListener);
        mSecondaryControlsPresenter.setOnControlSelectedListener(mOnControlSelectedListener);
        mPlaybackControlsPresenter.setOnControlClickedListener(mOnControlClickedListener);
        mSecondaryControlsPresenter.setOnControlClickedListener(mOnControlClickedListener);
    }

    /**
     * @param descriptionPresenter Presenter for displaying item details.
     */
    public void setDescriptionPresenter(Presenter descriptionPresenter) {
        mDescriptionPresenter = descriptionPresenter;
    }

    /**
     * Sets the listener for {@link Action} click events.
     */
    public void setOnActionClickedListener(OnActionClickedListener listener) {
        mOnActionClickedListener = listener;
    }

    /**
     * Returns the listener for {@link Action} click events.
     */
    public OnActionClickedListener getOnActionClickedListener() {
        return mOnActionClickedListener;
    }

    /**
     * Sets the primary color for the progress bar.  If not set, a default from
     * the theme will be used.
     */
    public void setProgressColor(@ColorInt int color) {
        mProgressColor = color;
        mProgressColorSet = true;
    }

    /**
     * Returns the primary color for the progress bar.  If no color was set, transparent
     * is returned.
     */
    @ColorInt
    public int getProgressColor() {
        return mProgressColor;
    }

    /**
     * Sets the secondary color for the progress bar.  If not set, a default from
     * the theme {@link R.attr#playbackProgressSecondaryColor} will be used.
     * @param color Color used to draw secondary progress.
     */
    public void setSecondaryProgressColor(@ColorInt int color) {
        mSecondaryProgressColor = color;
        mSecondaryProgressColorSet = true;
    }

    /**
     * Returns the secondary color for the progress bar.  If no color was set, transparent
     * is returned.
     */
    @ColorInt
    public int getSecondaryProgressColor() {
        return mSecondaryProgressColor;
    }

    @Override
    public void onReappear(RowPresenter.ViewHolder rowViewHolder) {
        ViewHolder vh = (ViewHolder) rowViewHolder;
        if (vh.view.hasFocus()) {
            vh.updateDateLabel();
            vh.enableCompactMode(false);
            vh.mProgressBar.requestFocus();
        }
    }

    private static int getDefaultProgressColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme()
                .resolveAttribute(R.attr.playbackProgressPrimaryColor, outValue, true)) {
            return context.getResources().getColor(outValue.resourceId);
        }
        return context.getResources().getColor(R.color.lb_playback_progress_color_no_theme);
    }

    private static int getDefaultSecondaryProgressColor(Context context) {
        TypedValue outValue = new TypedValue();
        if (context.getTheme()
                .resolveAttribute(R.attr.playbackProgressSecondaryColor, outValue, true)) {
            return context.getResources().getColor(outValue.resourceId);
        }
        return context.getResources().getColor(
                R.color.lb_playback_progress_secondary_color_no_theme);
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                com.liskovsoft.smartyoutubetv2.tv.R.layout.lb_playback_transport_controls_row, parent, false);
        ViewHolder vh = new ViewHolder(v, mDescriptionPresenter);
        initRow(vh);
        return vh;
    }

    private void initRow(final ViewHolder vh) {
        vh.mControlsVh = (ControlBarPresenter.ViewHolder) mPlaybackControlsPresenter
                .onCreateViewHolder(vh.mControlsDock);
        vh.mProgressBar.setProgressColor(mProgressColorSet ? mProgressColor
                : getDefaultProgressColor(vh.mControlsDock.getContext()));
        vh.mProgressBar.setSecondaryProgressColor(mSecondaryProgressColorSet
                ? mSecondaryProgressColor
                : getDefaultSecondaryProgressColor(vh.mControlsDock.getContext()));
        vh.mControlsDock.addView(vh.mControlsVh.view);

        vh.mSecondaryControlsVh = (ControlBarPresenter.ViewHolder) mSecondaryControlsPresenter
                .onCreateViewHolder(vh.mSecondaryControlsDock);
        vh.mSecondaryControlsDock.addView(vh.mSecondaryControlsVh.view);
        ((PlaybackTransportRowView) vh.view.findViewById(R.id.transport_row))
                .setOnUnhandledKeyListener(new PlaybackTransportRowView.OnUnhandledKeyListener() {
                @Override
                public boolean onUnhandledKey(KeyEvent event) {
                    if (vh.getOnKeyListener() != null) {
                        if (vh.getOnKeyListener().onKey(vh.view, event.getKeyCode(), event)) {
                            return true;
                        }
                    }
                    return false;
                }
            });
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);

        ViewHolder vh = (ViewHolder) holder;
        PlaybackControlsRow row = (PlaybackControlsRow) vh.getRow();

        if (row.getItem() == null) {
            vh.mDescriptionDock.setVisibility(View.GONE);
        } else {
            vh.mDescriptionDock.setVisibility(View.VISIBLE);
            if (vh.mDescriptionViewHolder != null) {
                mDescriptionPresenter.onBindViewHolder(vh.mDescriptionViewHolder, row.getItem());
            }
        }

        if (row.getImageDrawable() == null) {
            vh.mImageView.setVisibility(View.GONE);
        } else {
            vh.mImageView.setVisibility(View.VISIBLE);
        }
        vh.mImageView.setImageDrawable(row.getImageDrawable());

        vh.mControlsBoundData.adapter = row.getPrimaryActionsAdapter();
        vh.mControlsBoundData.presenter = vh.getPresenter(true);
        vh.mControlsBoundData.mRowViewHolder = vh;
        mPlaybackControlsPresenter.onBindViewHolder(vh.mControlsVh, vh.mControlsBoundData);

        vh.mSecondaryBoundData.adapter = row.getSecondaryActionsAdapter();
        vh.mSecondaryBoundData.presenter = vh.getPresenter(false);
        vh.mSecondaryBoundData.mRowViewHolder = vh;
        mSecondaryControlsPresenter.onBindViewHolder(vh.mSecondaryControlsVh,
                vh.mSecondaryBoundData);

        vh.setTotalTime(row.getDuration());
        vh.setCurrentPosition(row.getCurrentPosition());
        vh.setBufferedPosition(row.getBufferedPosition());
        row.setOnPlaybackProgressChangedListener(vh.mListener);
    }

    @Override
    protected void onUnbindRowViewHolder(RowPresenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        PlaybackControlsRow row = (PlaybackControlsRow) vh.getRow();

        if (vh.mDescriptionViewHolder != null) {
            mDescriptionPresenter.onUnbindViewHolder(vh.mDescriptionViewHolder);
        }
        mPlaybackControlsPresenter.onUnbindViewHolder(vh.mControlsVh);
        mSecondaryControlsPresenter.onUnbindViewHolder(vh.mSecondaryControlsVh);
        row.setOnPlaybackProgressChangedListener(null);

        super.onUnbindRowViewHolder(holder);
    }

    /**
     * Client of progress bar is clicked, default implementation delegate click to
     * PlayPauseAction.
     *
     * @param vh ViewHolder of PlaybackTransportRowPresenter
     */
    protected void onProgressBarClicked(ViewHolder vh) {
        if (vh != null) {
            if (vh.mPlayPauseAction == null) {
                vh.mPlayPauseAction = new PlaybackControlsRow.PlayPauseAction(vh.view.getContext());
            }
            if (vh.getOnItemViewClickedListener() != null) {
                vh.getOnItemViewClickedListener().onItemClicked(vh, vh.mPlayPauseAction,
                        vh, vh.getRow());
            }
            if (mOnActionClickedListener != null) {
                mOnActionClickedListener.onActionClicked(vh.mPlayPauseAction);
            }
        }
    }

    /**
     * Set default seek increment if {@link PlaybackSeekDataProvider} is null.
     * @param ratio float value between 0(inclusive) and 1(inclusive).
     */
    public void setDefaultSeekIncrement(float ratio) {
        mDefaultSeekIncrement = ratio;
    }

    /**
     * Get default seek increment if {@link PlaybackSeekDataProvider} is null.
     * @return float value between 0(inclusive) and 1(inclusive).
     */
    public float getDefaultSeekIncrement() {
        return mDefaultSeekIncrement;
    }

    @Override
    protected void onRowViewSelected(RowPresenter.ViewHolder vh, boolean selected) {
        super.onRowViewSelected(vh, selected);
        if (selected) {
            ((ViewHolder) vh).dispatchItemSelection();
        }
    }

    @Override
    protected void onRowViewAttachedToWindow(RowPresenter.ViewHolder vh) {
        super.onRowViewAttachedToWindow(vh);
        if (mDescriptionPresenter != null) {
            mDescriptionPresenter.onViewAttachedToWindow(
                    ((ViewHolder) vh).mDescriptionViewHolder);
        }
    }

    @Override
    protected void onRowViewDetachedFromWindow(RowPresenter.ViewHolder vh) {
        super.onRowViewDetachedFromWindow(vh);
        if (mDescriptionPresenter != null) {
            mDescriptionPresenter.onViewDetachedFromWindow(
                    ((ViewHolder) vh).mDescriptionViewHolder);
        }
    }

}
