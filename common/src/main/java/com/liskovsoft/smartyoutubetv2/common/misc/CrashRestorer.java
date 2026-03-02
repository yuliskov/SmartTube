package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.os.Bundle;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class CrashRestorer {
    private static final String SELECTED_HEADER_INDEX = "SelectedHeaderIndex";
    private static final String SELECTED_VIDEO = "SelectedVideo";
    private static final String IS_PLAYER_IN_FOREGROUND = "IsPlayerInForeground";
    private int mSelectedHeaderIndex = -1;
    private Video mSelectedVideo;
    private boolean mIsPlayerInForeground;
    private final Context mContext;

    public interface OnRestoreHeader {
        void onRestore(int selectedHeaderIndex, Video selectedVideo);
    }

    public CrashRestorer(Context context, Bundle savedState) {
        mContext = context.getApplicationContext();
        init(savedState);
    }

    private void init(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        mSelectedHeaderIndex = savedState.getInt(SELECTED_HEADER_INDEX, -1);
        mSelectedVideo = Video.fromString(savedState.getString(SELECTED_VIDEO));
        mIsPlayerInForeground = savedState.getBoolean(IS_PLAYER_IN_FOREGROUND, false);
    }

    public void persistHeaderIndex(Bundle outState, int selectedPosition) {
        if (selectedPosition == -1) { // multiple crashes without user interaction
            selectedPosition = mSelectedHeaderIndex;
        }

        outState.putInt(SELECTED_HEADER_INDEX, selectedPosition);
    }

    public void persistVideo(Bundle outState, Video currentVideo) {
        if (currentVideo == null) { // multiple crashes without user interaction
            currentVideo = mSelectedVideo;
        }

        if (currentVideo != null) {
            outState.putString(SELECTED_VIDEO, currentVideo.toString());
        }
        outState.putBoolean(IS_PLAYER_IN_FOREGROUND, ViewManager.instance(mContext).isPlayerInForeground());
    }

    public void restorePlayback() {
        if (mIsPlayerInForeground && PlaybackPresenter.instance(mContext).getPlayer() == null) {
            VideoStateService stateService = VideoStateService.instance(mContext);
            boolean isVideoStateSynced = mSelectedVideo == null || stateService.getByVideoId(mSelectedVideo.videoId) != null;
            State lastState = stateService.getLastState();
            PlaybackPresenter.instance(mContext).openVideo(lastState != null && isVideoStateSynced ? lastState.video : mSelectedVideo);
        }

        // Restore can be called only once
        mIsPlayerInForeground = false;
    }

    public void restoreHeader(OnRestoreHeader onRestoreHeader) {
        if (mSelectedHeaderIndex != -1) {
            onRestoreHeader.onRestore(mSelectedHeaderIndex, mSelectedVideo);
        }

        // Restore can be called only once
        mSelectedHeaderIndex = -1;
    }
}
