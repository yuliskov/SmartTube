package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.MaxControlsVideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ChannelAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ClosedCaptioningAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.HighQualityAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.SeekIntervalAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ShareAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.VideoInfoAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PipAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PlaybackQueueAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PlaylistAddAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.RepeatAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ScreenOffAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.SearchAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.SubscribeAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.TwoStateAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ThumbsDownAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ThumbsUpAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.VideoSpeedAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.VideoStatsAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.VideoZoomAction;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.concurrent.TimeUnit;

/**
 * Manages customizing the actions in the {@link PlaybackControlsRow}. Adds and manages the
 * following actions to the primary and secondary controls:
 *
 * <ul>
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.RepeatAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsDownAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.ThumbsUpAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.SkipPreviousAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.SkipNextAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.FastForwardAction}
 *   <li>{@link androidx.leanback.widget.PlaybackControlsRow.RewindAction}
 * </ul>
 *
 * Note that the superclass, {@link PlaybackTransportControlGlue}, manages the playback controls
 * row.
 */
public class VideoPlayerGlue extends MaxControlsVideoPlayerGlue<PlayerAdapter> {
    private static final long TEN_SECONDS = TimeUnit.SECONDS.toMillis(10);
    private static final String TAG = VideoPlayerGlue.class.getSimpleName();
    private final ThumbsUpAction mThumbsUpAction;
    private final ThumbsDownAction mThumbsDownAction;
    private final PlaybackControlsRow.SkipPreviousAction mSkipPreviousAction;
    private final PlaybackControlsRow.SkipNextAction mSkipNextAction;
    private final PlaybackControlsRow.FastForwardAction mFastForwardAction;
    private final PlaybackControlsRow.RewindAction mRewindAction;
    private final RepeatAction mRepeatAction;
    private final HighQualityAction mHighQualityAction;
    private final ClosedCaptioningAction mClosedCaptioningAction;
    private final SubscribeAction mSubscribeAction;
    private final ChannelAction mChannelAction;
    private final PlaylistAddAction mPlaylistAddAction;
    private final VideoStatsAction mVideoStatsAction;
    private final VideoSpeedAction mVideoSpeedAction;
    private final SearchAction mSearchAction;
    private final VideoZoomAction mVideoZoomAction;
    private final PipAction mPipAction;
    private final ScreenOffAction mScreenOffAction;
    private final PlaybackQueueAction mPlaybackQueueAction;
    private final VideoInfoAction mVideoInfoAction;
    private final ShareAction mShareAction;
    private final SeekIntervalAction mSeekIntervalAction;
    private final OnActionClickedListener mActionListener;
    private String mQualityInfo;
    private QualityInfoListener mQualityInfoListener;
    private int mPreviousAction = KeyEvent.ACTION_UP;
    private boolean mIsSingleKeyDown;

    public VideoPlayerGlue(
            Context context,
            PlayerAdapter playerAdapter,
            OnActionClickedListener actionListener) {
        super(context, playerAdapter);

        mActionListener = actionListener;

        mSkipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(context);
        mSkipNextAction = new PlaybackControlsRow.SkipNextAction(context);
        mFastForwardAction = new PlaybackControlsRow.FastForwardAction(context);
        mRewindAction = new PlaybackControlsRow.RewindAction(context);

        mThumbsUpAction = new ThumbsUpAction(context);
        mThumbsDownAction = new ThumbsDownAction(context);
        mThumbsUpAction.setBoundAction(mThumbsDownAction);
        mThumbsDownAction.setBoundAction(mThumbsUpAction);
        mRepeatAction = new RepeatAction(context);
        mHighQualityAction = new HighQualityAction(context);
        mClosedCaptioningAction = new ClosedCaptioningAction(context);
        mSubscribeAction = new SubscribeAction(context);
        mChannelAction = new ChannelAction(context);
        mPlaylistAddAction = new PlaylistAddAction(context);
        mVideoStatsAction = new VideoStatsAction(context);
        mVideoSpeedAction = new VideoSpeedAction(context);
        mSearchAction = new SearchAction(context);
        mVideoZoomAction = new VideoZoomAction(context);
        mPipAction = new PipAction(context);
        mScreenOffAction = new ScreenOffAction(context);
        mPlaybackQueueAction = new PlaybackQueueAction(context);
        mVideoInfoAction = new VideoInfoAction(context);
        mShareAction = new ShareAction(context);
        mSeekIntervalAction = new SeekIntervalAction(context);
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(getContext());

        // Order matters, super.onCreatePrimaryActions() will create the play / pause action.
        // Will display as follows:
        // play/pause, previous, rewind, fast forward, next
        //   > /||      |<        <<        >>         >|
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE)) {
            super.onCreatePrimaryActions(adapter);
        }

        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PREVIOUS)) {
            adapter.add(mSkipPreviousAction);
        }
        //adapter.add(mRewindAction);
        //adapter.add(mFastForwardAction);
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_NEXT)) {
            adapter.add(mSkipNextAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_REPEAT_MODE)) {
            adapter.add(mRepeatAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED)) {
            adapter.add(mVideoSpeedAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL)) {
            adapter.add(mSeekIntervalAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PIP)) {
            adapter.add(mPipAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SCREEN_OFF)) {
            adapter.add(mScreenOffAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM)) {
            adapter.add(mVideoZoomAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SHARE)) {
            adapter.add(mShareAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SEARCH)) {
            adapter.add(mSearchAction);
        }
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        // Does nothing
        super.onCreateSecondaryActions(adapter);

        // MAX: 7 items. But with custom modification it supports more.
        // Origin: {@link androidx.leanback.widget.ControlBarPresenter#MAX_CONTROLS}
        // Custom mod: {@link com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.ControlBarPresenter#MAX_CONTROLS}

        PlayerTweaksData playerTweaksData = PlayerTweaksData.instance(getContext());

        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_HIGH_QUALITY)) {
            adapter.add(mHighQualityAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL)) {
            adapter.add(mChannelAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_LIKE)) {
            adapter.add(mThumbsUpAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_DISLIKE)) {
            adapter.add(mThumbsDownAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SUBTITLES)) {
            adapter.add(mClosedCaptioningAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST)) {
            adapter.add(mPlaylistAddAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SUBSCRIBE)) {
            adapter.add(mSubscribeAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_INFO)) {
            adapter.add(mVideoInfoAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE)) {
            adapter.add(mPlaybackQueueAction);
        }
        if (playerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_STATS)) {
            adapter.add(mVideoStatsAction);
        }
    }

    @Override
    public void onActionClicked(Action action) {
        if (!dispatchAction(action)) {
            // Super class handles play/pause and delegates to abstract methods next()/previous().
            super.onActionClicked(action);
        }
    }

    @Override
    public void play() {
        super.play();
    }

    @Override
    public void pause() {
        super.pause();
    }

    @Override
    public void next() {
        mActionListener.onNext();
    }

    @Override
    public void previous() {
        mActionListener.onPrevious();
    }

    public void togglePlayback() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    /** Skips backwards 10 seconds. */
    public void rewind() {
        long newPosition = getCurrentPosition() - TEN_SECONDS;
        newPosition = (newPosition < 0) ? 0 : newPosition;
        getPlayerAdapter().seekTo(newPosition);
    }

    /** Skips forward 10 seconds. */
    public void fastForward() {
        if (getDuration() > -1) {
            long newPosition = getCurrentPosition() + TEN_SECONDS;
            newPosition = Math.min(newPosition, getDuration());
            getPlayerAdapter().seekTo(newPosition);
        }
    }

    public void setRepeatActionState(int modeIndex) {
        mRepeatAction.setIndex(modeIndex);
        invalidateUi(mRepeatAction);
    }

    public int getRepeatActionState() {
        return mRepeatAction.getIndex();
    }

    public void setSubscribeActionState(boolean subscribed) {
        mSubscribeAction.setIndex(subscribed ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mSubscribeAction);
    }

    public void setPlaylistAddButtonState(boolean selected) {
        mPlaylistAddAction.setIndex(selected ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mPlaylistAddAction);
    }

    public void setSubtitleButtonState(boolean selected) {
        mClosedCaptioningAction.setIndex(selected ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mClosedCaptioningAction);
    }

    public void setSpeedButtonState(boolean selected) {
        mVideoSpeedAction.setIndex(selected ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mVideoSpeedAction);
    }

    public void setChannelIcon(String iconUrl) {
        Drawable originIcon = mChannelAction.getIcon();
        Glide.with(getContext())
                .load(iconUrl)
                .apply(ViewUtil.glideOptions())
                .circleCrop() // resize image
                .into(new SimpleTarget<Drawable>(originIcon.getIntrinsicWidth(), originIcon.getIntrinsicHeight()) {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        mChannelAction.setIcon(resource);
                        mChannelAction.setPadding(3);
                        invalidateUi(mChannelAction);
                    }
                });
    }

    public void setThumbsUpActionState(boolean thumbsUp) {
        mThumbsUpAction.setIndex(thumbsUp ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);

        invalidateUi(mThumbsUpAction);
    }

    public void setThumbsDownActionState(boolean thumbsDown) {
        mThumbsDownAction.setIndex(thumbsDown ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);

        invalidateUi(mThumbsDownAction);
    }

    public void setDebugInfoActionState(boolean show) {
        mVideoStatsAction.setIndex(show ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mVideoStatsAction);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean handled = false;
        isSingleKeyDown(event.getAction());

        if (mIsSingleKeyDown) {
            handled = mActionListener.onKeyDown(keyCode);

            if (!handled) {
                Action action = findAction(keyCode);

                handled = dispatchAction(action);
            }
        }

        // Ignore result to give a chance to handle this event in
        // com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.maxcontrols.PlaybackTransportRowPresenter.ViewHolder
        return handled || super.onKey(v, keyCode, event);
    }

    /**
     * Notify key down only when there are paired action available.
     */
    private void isSingleKeyDown(int action) {
        mIsSingleKeyDown = action == KeyEvent.ACTION_DOWN && mPreviousAction == KeyEvent.ACTION_UP;
        mPreviousAction = action;
    }

    private boolean dispatchAction(Action action) {
        if (action == null) {
            return false;
        }

        boolean handled = false;

        // Primary actions are handled manually.
        if (action == mRewindAction) {
            rewind();
            handled = true;
        } else if (action == mFastForwardAction) {
            fastForward();
            handled = true;
        } else if (action == mRepeatAction) {
            incrementActionIndex(action);
            mActionListener.setRepeatMode(getActionIndex(action));
            handled = true;
        } else if (action == mHighQualityAction) {
            mActionListener.onHighQuality();
            handled = true;
        } else if (action == mSubscribeAction) {
            incrementActionIndex(action);
            mActionListener.onSubscribe(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mThumbsDownAction) {
            incrementActionIndex(action);
            mActionListener.onThumbsDown(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mThumbsUpAction) {
            incrementActionIndex(action);
            mActionListener.onThumbsUp(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mChannelAction) {
            mActionListener.onChannel();
            handled = true;
        } else if (action == mClosedCaptioningAction) {
            mActionListener.onClosedCaptions();
            handled = true;
        } else if (action == mPlaylistAddAction) {
            mActionListener.onPlaylistAdd();
            handled = true;
        } else if (action == mVideoStatsAction) {
            incrementActionIndex(action);
            mActionListener.onDebugInfo(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mVideoSpeedAction) {
            mActionListener.onVideoSpeed();
            handled = true;
        } else if (action == mSearchAction) {
            mActionListener.onSearch();
            handled = true;
        } else if (action == mVideoZoomAction) {
            mActionListener.onVideoZoom();
            handled = true;
        } else if (action == mPipAction) {
            mActionListener.onPip();
            handled = true;
        } else if (action == mScreenOffAction) {
            mActionListener.onScreenOff();
            handled = true;
        } else if (action == mPlaybackQueueAction) {
            mActionListener.onPlaybackQueue();
            handled = true;
        } else if (action == mVideoInfoAction) {
            mActionListener.onVideoInfo();
            handled = true;
        } else if (action == mShareAction) {
            mActionListener.onShareLink();
            handled = true;
        } else if (action == mSeekIntervalAction) {
            mActionListener.onSeekInterval();
            handled = true;
        }

        if (handled) {
            invalidateUi(action);

            if (action instanceof TwoStateAction) {
                invalidateUi(((TwoStateAction) action).getBoundAction());
            }
        }

        return handled;
    }

    private int getActionIndex(Action action) {
        if (action instanceof PlaybackControlsRow.MultiAction) {
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            return multiAction.getIndex();
        }

        return 0;
    }

    private void incrementActionIndex(Action action) {
        if (action instanceof PlaybackControlsRow.MultiAction) {
            PlaybackControlsRow.MultiAction multiAction = (PlaybackControlsRow.MultiAction) action;
            multiAction.nextIndex();
        }
    }

    /**
     * Properly handle ui changes of multi-action buttons
     */
    private void invalidateUi(Action action) {
        if (action != null) {
            // Notify adapter of action changes to handle primary actions, such as, play/pause.
            notifyActionChanged(
                    action,
                    (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter());

            // Notify adapter of action changes to handle secondary actions, such as, thumbs up/down and repeat.
            notifyActionChanged(
                    action,
                    (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter());
        }
    }

    private void notifyActionChanged(
            Action action, ArrayObjectAdapter adapter) {
        if (adapter != null) {
            int index = adapter.indexOf(action);
            if (index >= 0) {
                adapter.notifyArrayItemRangeChanged(index, 1);
            }
        }
    }

    private Action findAction(int keyCode) {
        Action action = null;
        PlaybackControlsRow controlsRow = getControlsRow();

        if (controlsRow != null) {
            final ObjectAdapter primaryActionsAdapter = controlsRow.getPrimaryActionsAdapter();
            action = controlsRow.getActionForKeyCode(primaryActionsAdapter, keyCode);

            if (action == null) {
                action = controlsRow.getActionForKeyCode(controlsRow.getSecondaryActionsAdapter(),
                        keyCode);
            }
        }

        return action;
    }

    @Override
    protected void onAttachedToHost(PlaybackGlueHost host) {
        super.onAttachedToHost(host);

        Log.d(TAG, "On attached to host");
    }

    @Override
    public void onTopEdgeFocused() {
        mActionListener.onTopEdgeFocused();
    }

    /** Listens for when skip to next and previous actions have been dispatched. */
    public interface OnActionClickedListener {
        /** Skip to the previous item in the queue. */
        void onPrevious();

        /** Skip to the next item in the queue. */
        void onNext();

        void onPlay();

        void onPause();

        void setRepeatMode(int modeIndex);

        void onHighQuality();

        void onSubscribe(boolean subscribed);

        void onThumbsDown(boolean thumbsDown);

        void onThumbsUp(boolean thumbsUp);

        void onChannel();

        void onClosedCaptions();

        void onPlaylistAdd();

        void onDebugInfo(boolean enabled);

        void onVideoSpeed();

        void onSeekInterval();

        void onVideoInfo();

        void onShareLink();

        void onSearch();

        void onVideoZoom();

        void onPip();

        void onScreenOff();

        void onPlaybackQueue();

        void onTopEdgeFocused();

        boolean onKeyDown(int keyCode);
    }
}
