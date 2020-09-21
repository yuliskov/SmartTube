package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ChannelAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.ClosedCaptioningAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.HighQualityAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.RepeatAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.SubscribeAction;

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
public class VideoPlayerGlue extends PlaybackTransportControlGlue<PlayerAdapter> {
    private static final long TEN_SECONDS = TimeUnit.SECONDS.toMillis(10);

    /** Listens for when skip to next and previous actions have been dispatched. */
    public interface OnActionClickedListener {

        /** Skip to the previous item in the queue. */
        void onPrevious();

        /** Skip to the next item in the queue. */
        void onNext();

        void onPlay();

        void onPause();

        void onKeyDown(int keyCode);

        void setRepeatMode(int modeIndex);

        void onHighQuality();

        void onSubscribe(boolean subscribed);

        void onThumbsDown(boolean thumbsDown);

        void onThumbsUp(boolean thumbsUp);

        void onChannel();

        void onClosedCaptions();
    }

    private final OnActionClickedListener mActionListener;

    private final PlaybackControlsRow.ThumbsUpAction mThumbsUpAction;
    private final PlaybackControlsRow.ThumbsDownAction mThumbsDownAction;
    private final PlaybackControlsRow.SkipPreviousAction mSkipPreviousAction;
    private final PlaybackControlsRow.SkipNextAction mSkipNextAction;
    private final PlaybackControlsRow.FastForwardAction mFastForwardAction;
    private final PlaybackControlsRow.RewindAction mRewindAction;
    private final RepeatAction mRepeatAction;
    private final HighQualityAction mHighQualityAction;
    private final ClosedCaptioningAction mClosedCaptioningAction;
    private final SubscribeAction mSubscribeAction;
    private final ChannelAction mChannelAction;

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

        mThumbsUpAction = new PlaybackControlsRow.ThumbsUpAction(context);
        mThumbsUpAction.setIndex(PlaybackControlsRow.ThumbsUpAction.INDEX_OUTLINE);
        mThumbsDownAction = new PlaybackControlsRow.ThumbsDownAction(context);
        mThumbsDownAction.setIndex(PlaybackControlsRow.ThumbsDownAction.INDEX_OUTLINE);
        mRepeatAction = new RepeatAction(context);
        mHighQualityAction = new HighQualityAction(context);
        mClosedCaptioningAction = new ClosedCaptioningAction(context);
        mSubscribeAction = new SubscribeAction(context);
        mChannelAction = new ChannelAction(context);
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter adapter) {
        // Order matters, super.onCreatePrimaryActions() will create the play / pause action.
        // Will display as follows:
        // play/pause, previous, rewind, fast forward, next
        //   > /||      |<        <<        >>         >|
        super.onCreatePrimaryActions(adapter);
        adapter.add(mSkipPreviousAction);
        adapter.add(mRewindAction);
        adapter.add(mFastForwardAction);
        adapter.add(mSkipNextAction);
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter adapter) {
        super.onCreateSecondaryActions(adapter);
        adapter.add(mChannelAction);
        adapter.add(mHighQualityAction);
        adapter.add(mThumbsDownAction);
        adapter.add(mThumbsUpAction);
        adapter.add(mSubscribeAction);
        adapter.add(mClosedCaptioningAction);
        adapter.add(mRepeatAction);
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
            newPosition = (newPosition > getDuration()) ? getDuration() : newPosition;
            getPlayerAdapter().seekTo(newPosition);
        }
    }

    public void setRepeatActionState(int modeIndex) {
        mRepeatAction.setIndex(modeIndex);
        invalidateUi(mRepeatAction);
    }

    public void setSubscribeActionState(boolean subscribed) {
        mSubscribeAction.setIndex(subscribed ? 1 : 0);
        invalidateUi(mSubscribeAction);
    }

    public void setThumbsUpActionState(boolean thumbsUp) {
        mThumbsUpAction.setIndex(thumbsUp ? 1 : 0);
        invalidateUi(mThumbsUpAction);
    }

    public void setThumbsDownActionState(boolean thumbsDown) {
        mThumbsDownAction.setIndex(thumbsDown ? 1 : 0);
        invalidateUi(mThumbsDownAction);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean handled = false;

        Action action = findAction(keyCode);

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            handled = dispatchAction(action);

            if (!handled) {
                handled = dispatchKey(keyCode);
            }

            mActionListener.onKeyDown(keyCode);
        }

        return handled || super.onKey(v, keyCode, event);
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
            mActionListener.onSubscribe(getActionIndex(action) != 0);
            handled = true;
        } else if (action == mThumbsDownAction) {
            incrementActionIndex(action);
            mActionListener.onThumbsDown(getActionIndex(action) != 0);
            handled = true;
        } else if (action == mThumbsUpAction) {
            incrementActionIndex(action);
            mActionListener.onThumbsUp(getActionIndex(action) != 0);
            handled = true;
        } else if (action == mChannelAction) {
            mActionListener.onChannel();
            handled = true;
        } else if (action == mClosedCaptioningAction) {
            mActionListener.onClosedCaptions();
            handled = true;
        }

        if (handled) {
            invalidateUi(action);
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
        if (action instanceof PlaybackControlsRow.MultiAction) {
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

    private boolean dispatchKey(int keyCode) {
        return false;
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
}
