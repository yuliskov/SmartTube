package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.Arrays;
import java.util.Map;

public class PlayerKeyTranslator extends GlobalKeyTranslator {
    private final GeneralData mGeneralData;
    private final Context mContext;

    public PlayerKeyTranslator(Context context) {
        super(context);
        mContext = context;
        mGeneralData = GeneralData.instance(context);
    }

    @Override
    protected void initKeyMapping() {
        super.initKeyMapping();

        Map<Integer, Integer> globalKeyMapping = getKeyMapping();

        // Reset global mapping to default
        globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_REWIND);
        globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);

        if (mGeneralData.isRemapFastForwardToNextEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT);
            globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        if (mGeneralData.isRemapPageUpToNextEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_NEXT);
            globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        if (mGeneralData.isRemapChannelUpToNextEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT);
            globalKeyMapping.put(KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
        
        if (!PlaybackPresenter.instance(mContext).isInPipMode() && mGeneralData.isRemapPlayPauseToOKEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER);
        } else {
            globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }

        globalKeyMapping.put(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_0); // reset position of the video (if enabled number key handling in the settings)
        // Toggle playback on PLAY/PAUSE
        globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    @Override
    protected void initActionMapping() {
        super.initActionMapping();

        Map<Integer, Runnable> actionMapping = getActionMapping();

        addLikeAction(actionMapping);
        addSpeedAction(actionMapping);
    }

    private void addLikeAction(Map<Integer, Runnable> actionMapping) {
        if (!mGeneralData.isRemapPageUpToLikeEnabled() && !mGeneralData.isRemapChannelUpToLikeEnabled()) {
            return;
        }

        Runnable likeAction = () -> {
            PlaybackView playbackView = getPlaybackView();
            if (playbackView != null && playbackView.getEventListener() != null) {
                playbackView.getEventListener().onLikeClicked(true);
                playbackView.getPlayer().setLikeButtonState(true);
                playbackView.getPlayer().setDislikeButtonState(false);
                MessageHelpers.showMessage(mContext, R.string.action_like);
            }
        };
        Runnable dislikeAction = () -> {
            PlaybackView playbackView = getPlaybackView();
            if (playbackView != null && playbackView.getEventListener() != null) {
                playbackView.getEventListener().onDislikeClicked(true);
                playbackView.getPlayer().setLikeButtonState(false);
                playbackView.getPlayer().setDislikeButtonState(true);
                MessageHelpers.showMessage(mContext, R.string.action_dislike);
            }
        };

        if (mGeneralData.isRemapPageUpToLikeEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_PAGE_UP, likeAction);
            actionMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, dislikeAction);
        }

        if (mGeneralData.isRemapChannelUpToLikeEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_CHANNEL_UP, likeAction);
            actionMapping.put(KeyEvent.KEYCODE_CHANNEL_DOWN, dislikeAction);
        }
    }

    private void addSpeedAction(Map<Integer, Runnable> actionMapping) {
        if (!mGeneralData.isRemapPageUpToSpeedEnabled() && !mGeneralData.isRemapChannelUpToSpeedEnabled()
            && !mGeneralData.isRemapFastForwardToSpeedEnabled() && !mGeneralData.isRemapNextPrevToSpeedEnabled()) {
            return;
        }

        Runnable speedUpAction = () -> speedUp(true);
        Runnable speedDownAction = () -> speedUp(false);

        if (mGeneralData.isRemapPageUpToSpeedEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_PAGE_UP, speedUpAction);
            actionMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, speedDownAction);
        }

        if (mGeneralData.isRemapChannelUpToSpeedEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_CHANNEL_UP, speedUpAction);
            actionMapping.put(KeyEvent.KEYCODE_CHANNEL_DOWN, speedDownAction);
        }

        if (mGeneralData.isRemapFastForwardToSpeedEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, speedUpAction);
            actionMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, speedDownAction);
        }

        if (mGeneralData.isRemapNextPrevToSpeedEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_MEDIA_NEXT, speedUpAction);
            actionMapping.put(KeyEvent.KEYCODE_MEDIA_PREVIOUS, speedDownAction);
        }
    }

    private void speedUp(boolean up) {
        float[] speedSteps = PlayerTweaksData.instance(mContext).isLongSpeedListEnabled() ? Utils.SPEED_LIST_LONG : Utils.SPEED_LIST_SHORT;

        PlaybackView playbackView = getPlaybackView();

        if (playbackView != null && playbackView.getPlayer() != null) {
            float currentSpeed = playbackView.getPlayer().getSpeed();
            int currentIndex = Arrays.binarySearch(speedSteps, currentSpeed);

            if (currentIndex < 0) {
                currentIndex = Arrays.binarySearch(speedSteps, 1.0f);
            }

            int newIndex = up ? currentIndex + 1 : currentIndex - 1;

            float speed = newIndex >= 0 && newIndex < speedSteps.length ? speedSteps[newIndex] : speedSteps[currentIndex];

            PlayerData.instance(mContext).setSpeed(speed);
            playbackView.getPlayer().setSpeed(speed);
            MessageHelpers.showMessage(mContext, String.format("%sx", speed));
        }
    }

    private PlaybackView getPlaybackView() {
        return PlaybackPresenter.instance(mContext).getView();
    }
}
