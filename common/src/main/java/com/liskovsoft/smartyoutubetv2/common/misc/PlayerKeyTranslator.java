package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

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

        globalKeyMapping.put(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_0); // reset position of the video (if enabled number key handling in the settings)
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
                playbackView.getController().setLikeButtonState(true);
                playbackView.getController().setDislikeButtonState(false);
                MessageHelpers.showMessage(mContext, R.string.action_like);
            }
        };
        Runnable dislikeAction = () -> {
            PlaybackView playbackView = getPlaybackView();
            if (playbackView != null && playbackView.getEventListener() != null) {
                playbackView.getEventListener().onDislikeClicked(true);
                playbackView.getController().setLikeButtonState(false);
                playbackView.getController().setDislikeButtonState(true);
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
            && !mGeneralData.isRemapFastForwardToSpeedEnabled()) {
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
    }

    private void speedUp(boolean up) {
        float[] speedSteps =
                new float[] {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f, 3.0f};

        PlaybackView playbackView = getPlaybackView();

        if (playbackView != null && playbackView.getController() != null) {
            float currentSpeed = playbackView.getController().getSpeed();
            int currentIndex = -1;

            for (int i = 0; i < speedSteps.length; i++) {
                float step = speedSteps[i];
                if (Helpers.floatEquals(currentSpeed, step) || Math.abs(currentSpeed - step) < 0.25f) {
                    currentIndex = i;
                    break;
                }
            }

            if (currentIndex < 0) {
                currentIndex = 3;
            }

            int newIndex = up ? currentIndex + 1 : currentIndex - 1;

            float speed = newIndex >= 0 && newIndex < speedSteps.length ? speedSteps[newIndex] : speedSteps[currentIndex];

            PlayerData.instance(mContext).setSpeed(speed);
            playbackView.getController().setSpeed(speed);
            MessageHelpers.showMessage(mContext, String.format("%sx", speed));
        }
    }

    private PlaybackView getPlaybackView() {
        return PlaybackPresenter.instance(mContext).getView();
    }
}
