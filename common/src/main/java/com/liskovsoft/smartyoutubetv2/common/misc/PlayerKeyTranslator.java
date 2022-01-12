package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.view.KeyEvent;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;

import java.util.Map;

public class PlayerKeyTranslator extends GlobalKeyTranslator {
    private final GeneralData mGeneralData;
    private final PlaybackView mPlaybackView;

    public PlayerKeyTranslator(Context context, PlaybackView playbackView) {
        super(context);
        mGeneralData = GeneralData.instance(context);
        mPlaybackView = playbackView;
        initKeyMapping();
        initActionMapping();
    }

    private void initKeyMapping() {
        Map<Integer, Integer> globalKeyMapping = getKeyMapping();

        if (mGeneralData.isRemapFastForwardToNextEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT);
            globalKeyMapping.put(KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        if (mGeneralData.isRemapPageUpToNextEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_NEXT);
            globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }

        if (mGeneralData.isRemapPageUpToLikeEnabled()) {
            globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_UNKNOWN);
            globalKeyMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_UNKNOWN);
        }

        // Reset global mapping to default
        globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_REWIND);
        globalKeyMapping.remove(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
    }

    private void initActionMapping() {
        Map<Integer, Runnable> actionMapping = getActionMapping();

        if (mGeneralData.isRemapPageUpToLikeEnabled()) {
            actionMapping.put(KeyEvent.KEYCODE_PAGE_UP, () -> {
                if (mPlaybackView.getEventListener() != null) {
                    mPlaybackView.getEventListener().onThumbsUpClicked(true);
                    mPlaybackView.getController().setLikeButtonState(true);
                    mPlaybackView.getController().setDislikeButtonState(false);
                }
            });
            actionMapping.put(KeyEvent.KEYCODE_PAGE_DOWN, () -> {
                if (mPlaybackView.getEventListener() != null) {
                    mPlaybackView.getEventListener().onThumbsDownClicked(true);
                    mPlaybackView.getController().setLikeButtonState(false);
                    mPlaybackView.getController().setDislikeButtonState(true);
                }
            });
        }
    }
}
