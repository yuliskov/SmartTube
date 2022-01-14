package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

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
    }

    private void initActionMapping() {
        Map<Integer, Runnable> actionMapping = getActionMapping();

        addLikeAction(actionMapping);
    }

    private void addLikeAction(Map<Integer, Runnable> actionMapping) {
        Runnable likeAction = () -> {
            if (mPlaybackView.getEventListener() != null) {
                mPlaybackView.getEventListener().onThumbsUpClicked(true);
                mPlaybackView.getController().setLikeButtonState(true);
                mPlaybackView.getController().setDislikeButtonState(false);
                MessageHelpers.showMessage(mContext, R.string.action_like);
            }
        };
        Runnable dislikeAction = () -> {
            if (mPlaybackView.getEventListener() != null) {
                mPlaybackView.getEventListener().onThumbsDownClicked(true);
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
}
