package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

import java.util.Arrays;
import java.util.Map;

public class PlayerKeyTranslator extends GlobalKeyTranslator {
    private final GeneralData mGeneralData;
    private final Context mContext;
    private final PlaybackView mPlaybackView;

    public PlayerKeyTranslator(Context context, PlaybackView playbackView) {
        super(context);
        mContext = context;
        mGeneralData = GeneralData.instance(context);
        mPlaybackView = playbackView;
        initKeyMapping();
        initActionMapping();
    }

    private void initKeyMapping() {
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

    private void initActionMapping() {
        Map<Integer, Runnable> actionMapping = getActionMapping();

        addLikeAction(actionMapping);
        addSpeedAction(actionMapping);
    }

    private void addLikeAction(Map<Integer, Runnable> actionMapping) {
        if (!mGeneralData.isRemapPageUpToLikeEnabled() && !mGeneralData.isRemapChannelUpToLikeEnabled()) {
            return;
        }

        Runnable likeAction = () -> {
            if (mPlaybackView.getEventListener() != null) {
                mPlaybackView.getEventListener().onLikeClicked(true);
                mPlaybackView.getController().setLikeButtonState(true);
                mPlaybackView.getController().setDislikeButtonState(false);
                MessageHelpers.showMessage(mContext, R.string.action_like);
            }
        };
        Runnable dislikeAction = () -> {
            if (mPlaybackView.getEventListener() != null) {
                mPlaybackView.getEventListener().onDislikeClicked(true);
                mPlaybackView.getController().setLikeButtonState(false);
                mPlaybackView.getController().setDislikeButtonState(true);
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
                new float[]{0.25f, 0.5f, 0.75f, 0.80f, 0.85f, 0.90f, 0.95f, 1.0f, 1.05f, 1.1f, 1.15f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3.0f};

        if (mPlaybackView.getController() != null) {
            float currentSpeed = mPlaybackView.getController().getSpeed();
            int currentIndex = Arrays.binarySearch(speedSteps, currentSpeed);

            if (currentIndex >= 0) {
                int newIndex = up ? currentIndex + 1 : currentIndex - 1;

                float speed = newIndex >= 0 && newIndex < speedSteps.length ? speedSteps[newIndex] : speedSteps[currentIndex];

                PlayerData.instance(mContext).setSpeed(speed);
                mPlaybackView.getController().setSpeed(speed);
                MessageHelpers.showMessage(mContext, String.format("%sx", speed));
            }
        }
    }
}
