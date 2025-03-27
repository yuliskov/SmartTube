package com.liskovsoft.smartyoutubetv2.tv.ui.playback.other;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import androidx.leanback.widget.PlaybackRowPresenter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.MaxControlsVideoPlayerGlue;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PlaybackTransportRowPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.widget.OnActionLongClickedListener;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.AFRAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ActionHelpers;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ChannelAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ChatAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ClosedCaptioningAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ContentBlockAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.FlipAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.HighQualityAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.RotateAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ScreenOffTimeoutAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.SeekIntervalAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ShareAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.SoundOffAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.VideoInfoAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PipAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PlaybackQueueAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PlaylistAddAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PlaybackModeAction;
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

import java.util.HashMap;
import java.util.Map;
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
public class VideoPlayerGlue extends MaxControlsVideoPlayerGlue<PlayerAdapter> implements OnActionLongClickedListener {
    private static final long TEN_SECONDS = TimeUnit.SECONDS.toMillis(10);
    private static final String TAG = VideoPlayerGlue.class.getSimpleName();
    private final ThumbsUpAction mThumbsUpAction;
    private final ThumbsDownAction mThumbsDownAction;
    private final PlaybackControlsRow.SkipPreviousAction mSkipPreviousAction;
    private final PlaybackControlsRow.SkipNextAction mSkipNextAction;
    private final PlaybackControlsRow.FastForwardAction mFastForwardAction;
    private final PlaybackControlsRow.RewindAction mRewindAction;
    private final HighQualityAction mHighQualityAction;
    private final ClosedCaptioningAction mClosedCaptioningAction;
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
    private final Map<Integer, Action> mActions = new HashMap<>();
    private final OnActionClickedListener mActionListener;
    private final PlayerTweaksData mPlayerTweaksData;
    private final GeneralData mGeneralData;
    private int mPreviousAction = KeyEvent.ACTION_UP;

    public VideoPlayerGlue(
            Context context,
            PlayerAdapter playerAdapter,
            OnActionClickedListener actionListener) {
        super(context, playerAdapter);

        mPlayerTweaksData = PlayerTweaksData.instance(getContext());
        mGeneralData = GeneralData.instance(getContext());

        mActionListener = actionListener;

        mSkipPreviousAction = new PlaybackControlsRow.SkipPreviousAction(context);
        mSkipNextAction = new PlaybackControlsRow.SkipNextAction(context);
        mFastForwardAction = new PlaybackControlsRow.FastForwardAction(context);
        mRewindAction = new PlaybackControlsRow.RewindAction(context);

        mThumbsUpAction = new ThumbsUpAction(context);
        mThumbsDownAction = new ThumbsDownAction(context);
        mThumbsUpAction.setBoundAction(mThumbsDownAction);
        mThumbsDownAction.setBoundAction(mThumbsUpAction);
        mHighQualityAction = new HighQualityAction(context);
        mClosedCaptioningAction = new ClosedCaptioningAction(context);
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

        putAction(new RotateAction(context));
        putAction(new FlipAction(context));
        putAction(new ContentBlockAction(context));
        putAction(new ScreenOffAction(context));
        putAction(new ScreenOffTimeoutAction(context));
        putAction(new SubscribeAction(context));
        putAction(new SoundOffAction(context));
        putAction(new AFRAction(context));
        putAction(new PlaybackModeAction(context));
        putAction(new ChannelAction(context));
        putAction(new ChatAction(context));
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        // Order matters, super.onCreatePrimaryActions() will create the play / pause action.
        // Will display as follows:
        // play/pause, previous, rewind, fast forward, next
        //   > /||      |<        <<        >>         >|
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PLAY_PAUSE)) {
            super.onCreatePrimaryActions(adapter);
        }

        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PREVIOUS)) {
            adapter.add(mSkipPreviousAction);
        }
        //adapter.add(mRewindAction);
        //adapter.add(mFastForwardAction);
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_NEXT)) {
            adapter.add(mSkipNextAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_REPEAT_MODE)) {
            adapter.add(mActions.get(R.id.action_repeat));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_SPEED)) {
            adapter.add(mVideoSpeedAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PIP)) {
            adapter.add(mPipAction);
        }
        //if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SCREEN_OFF)) {
        //    adapter.add(mActions.get(R.id.action_screen_off));
        //}
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SCREEN_OFF_TIMEOUT)) {
            adapter.add(mActions.get(R.id.action_screen_off_timeout));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_CHAT)) {
            adapter.add(mActions.get(R.id.action_chat));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SEARCH)) {
            adapter.add(mSearchAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SHARE)) {
            adapter.add(mShareAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SEEK_INTERVAL)) {
            adapter.add(mSeekIntervalAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_ZOOM)) {
            adapter.add(mVideoZoomAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_ROTATE)) {
            adapter.add(mActions.get(R.id.action_rotate));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_FLIP)) {
            adapter.add(mActions.get(R.id.action_flip));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SOUND_OFF)) {
            adapter.add(mActions.get(R.id.action_sound_off));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_AFR)) {
            adapter.add(mActions.get(R.id.action_afr));
        }
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        // Does nothing
        super.onCreateSecondaryActions(adapter);

        // MAX: 7 items. But with custom modification it supports more.
        // Origin: {@link androidx.leanback.widget.ControlBarPresenter#MAX_CONTROLS}
        // Custom mod: {@link com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.ControlBarPresenter#MAX_CONTROLS}

        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_HIGH_QUALITY)) {
            adapter.add(mHighQualityAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_OPEN_CHANNEL)) {
            adapter.add(mActions.get(R.id.action_channel));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_LIKE)) {
            adapter.add(mThumbsUpAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_DISLIKE)) {
            adapter.add(mThumbsDownAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SUBTITLES)) {
            adapter.add(mClosedCaptioningAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_ADD_TO_PLAYLIST)) {
            adapter.add(mPlaylistAddAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_SUBSCRIBE)) {
            adapter.add(mActions.get(R.id.action_subscribe));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_INFO)) {
            adapter.add(mVideoInfoAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_PLAYBACK_QUEUE)) {
            adapter.add(mPlaybackQueueAction);
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_CONTENT_BLOCK)) {
            adapter.add(mActions.get(R.id.action_content_block));
        }
        if (mPlayerTweaksData.isPlayerButtonEnabled(PlayerTweaksData.PLAYER_BUTTON_VIDEO_STATS)) {
            adapter.add(mVideoStatsAction);
        }
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        PlaybackRowPresenter rowPresenter = super.onCreateRowPresenter();

        ((PlaybackTransportRowPresenter) rowPresenter).setOnActionLongClickedListener(this);

        return rowPresenter;
    }

    @Override
    public void onActionClicked(Action action) {
        if (!dispatchAction(action)) {
            // Super class handles play/pause and delegates to abstract methods next()/previous().
            super.onActionClicked(action);
        }
    }

    @Override
    public boolean onActionLongClicked(Action action) {
        return dispatchLongClickAction(action);
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

    public void setPlaylistAddButtonState(boolean selected) {
        mPlaylistAddAction.setIndex(selected ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mPlaylistAddAction);
    }

    public void setClosedCaptionsButtonState(boolean selected) {
        mClosedCaptioningAction.setIndex(selected ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mClosedCaptioningAction);
    }

    public void setButtonState(int buttonId, int buttonState) {
        setActionIndex(mActions.get(buttonId), buttonState);
    }

    public void setSpeedButtonState(boolean selected) {
        mVideoSpeedAction.setIndex(selected ? TwoStateAction.INDEX_ON : TwoStateAction.INDEX_OFF);
        invalidateUi(mVideoSpeedAction);
    }

    public void setChannelIcon(String iconUrl) {
        ChannelAction channelAction = (ChannelAction) mActions.get(R.id.action_channel);

        if (channelAction == null) {
            return;
        }

        if (iconUrl == null) {
            channelAction.setIcon(ContextCompat.getDrawable(getContext(), R.drawable.action_channel));
            invalidateUi(channelAction);
            return;
        }

        Drawable originIcon = channelAction.getIcon();
        Glide.with(getContext())
                .load(iconUrl)
                .apply(ViewUtil.glideOptions())
                .circleCrop() // resize image
                .into(new SimpleTarget<Drawable>(originIcon.getIntrinsicWidth(), originIcon.getIntrinsicHeight()) {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        channelAction.setIcon(resource);
                        channelAction.setPadding(3);
                        invalidateUi(channelAction);
                    }
                });
    }

    public void setNextTitle(CharSequence title) {
        mSkipNextAction.setLabel1(title != null ? title : getContext().getString(R.string.lb_playback_controls_skip_next));
        invalidateUi(mSkipNextAction);
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

        if (isSingleKeyDown(event.getAction())) {
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
     * Fixing sticky key press? <br/>
     * Notify key down only when there are paired action available.
     */
    private boolean isSingleKeyDown(int action) {
        boolean result = action == KeyEvent.ACTION_DOWN && mPreviousAction == KeyEvent.ACTION_UP;
        mPreviousAction = action;
        return result;
    }

    private boolean dispatchAction(Action action) {
        if (action == null) {
            return false;
        }

        if (checkShortActionDisabled(action)) {
            return true;
        }

        boolean handled = false;

        // Primary actions are handled manually.
        if (action == mRewindAction) {
            rewind();
            handled = true;
        } else if (action == mFastForwardAction) {
            fastForward();
            handled = true;
        } else if (action == mHighQualityAction) {
            mActionListener.onHighQuality();
            handled = true;
        } else if (action == mThumbsDownAction) {
            incrementActionIndex(action);
            mActionListener.onThumbsDown(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mThumbsUpAction) {
            incrementActionIndex(action);
            mActionListener.onThumbsUp(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mClosedCaptioningAction) {
            mActionListener.onClosedCaptions(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mPlaylistAddAction) {
            mActionListener.onPlaylistAdd();
            handled = true;
        } else if (action == mVideoStatsAction) {
            incrementActionIndex(action);
            mActionListener.onDebugInfo(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mVideoSpeedAction) {
            mActionListener.onVideoSpeed(getActionIndex(action) == TwoStateAction.INDEX_ON);
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
        } else if (mActions.containsKey((int) action.getId())) {
            mActionListener.onAction((int) action.getId(), getActionIndex(action));
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

    private boolean dispatchLongClickAction(Action action) {
        if (action == null) {
            return false;
        }

        if (checkLongActionDisabled(action)) {
            return false;
        }

        boolean handled = false;

        if (action == mClosedCaptioningAction) {
            mActionListener.onClosedCaptionsLongPress(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (action == mVideoSpeedAction) {
            mActionListener.onVideoSpeedLongPress(getActionIndex(action) == TwoStateAction.INDEX_ON);
            handled = true;
        } else if (mActions.containsKey((int) action.getId())) {
            mActionListener.onLongAction((int) action.getId(), getActionIndex(action));
            handled = true;
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

    private void removePrimaryAction(Action action) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
        if (adapter != null) {
            adapter.remove(action);
        }
    }

    private void removeSecondaryAction(Action action) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter();
        if (adapter != null) {
            adapter.remove(action);
        }
    }

    private void addPrimaryAction(Action action, int position) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getPrimaryActionsAdapter();
        addAction(action, position, adapter);
    }

    private void addSecondaryAction(Action action, int position) {
        ArrayObjectAdapter adapter = (ArrayObjectAdapter) getControlsRow().getSecondaryActionsAdapter();
        addAction(action, position, adapter);
    }

    private void addAction(Action action, int position, ArrayObjectAdapter adapter) {
        if (adapter != null) {
            int index = adapter.indexOf(action);
            if (index == -1) {
                int size = adapter.size();
                adapter.add(Math.min(position, size), action);
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

    private void putAction(Action action) {
        mActions.put((int) action.getId(), action);
    }

    private void setActionIndex(Action action, int actionIndex) {
        if (actionIndex == -1) { // button disabled
            disableAction(action);
        } else if (action instanceof MultiAction) {
            ((MultiAction) action).setIndex(actionIndex);
            invalidateUi(action);
        }
    }

    private void disableAction(Action action) {
        Drawable icon = action.getIcon();
        action.setIcon(ActionHelpers.createDrawable(getContext(), (BitmapDrawable) icon, ActionHelpers.getIconGrayedOutColor(getContext())));
        invalidateUi(action);
    }

    /**
     * Long press actions usually more important than short ones. So, try to use it first in case long click is disabled.
     */
    private boolean checkShortActionDisabled(Action action) {
        if (!mGeneralData.isOkButtonLongPressDisabled() && mPlayerTweaksData.isButtonLongClickEnabled()) {
            return false;
        }

        return (action == mClosedCaptioningAction || action == mVideoSpeedAction) &&
                dispatchLongClickAction(action); // replace short with long
    }

    private boolean checkLongActionDisabled(Action action) {
        if (!mGeneralData.isOkButtonLongPressDisabled() && mPlayerTweaksData.isButtonLongClickEnabled()) {
            return false;
        }

        return action.getId() == R.id.action_chat;
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

        void onHighQuality();

        void onThumbsDown(boolean thumbsDown);

        void onThumbsUp(boolean thumbsUp);

        void onClosedCaptions(boolean enabled);

        void onClosedCaptionsLongPress(boolean enabled);

        void onPlaylistAdd();

        void onDebugInfo(boolean enabled);

        void onVideoSpeed(boolean enabled);

        void onVideoSpeedLongPress(boolean enabled);

        void onSeekInterval();

        void onVideoInfo();

        void onShareLink();

        void onSearch();

        void onVideoZoom();

        void onPip();

        void onPlaybackQueue();

        void onAction(int actionId, int actionIndex);

        void onLongAction(int actionId, int actionIndex);

        void onTopEdgeFocused();

        boolean onKeyDown(int keyCode);
    }
}
