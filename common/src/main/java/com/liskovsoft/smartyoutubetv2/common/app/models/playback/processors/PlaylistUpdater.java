package com.liskovsoft.smartyoutubetv2.common.app.models.playback.processors;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.PlayerEventListenerHelper;

public class PlaylistUpdater extends PlayerEventListenerHelper {
    private final Playlist mPlaylist;

    public PlaylistUpdater() {
        mPlaylist = Playlist.instance();
    }

    @Override
    public void onInit(Video item) {
        mPlaylist.insertAfterCurrent(item);
        mPlaylist.setCurrentPosition(mPlaylist.getCurrentPosition() + 1);
    }

    @Override
    public void onSuggestionItemClicked(Video item) {
        mPlaylist.add(item);
        mPlaylist.setCurrentPosition(mPlaylist.size() - 1);
    }

    @Override
    public void onPrevious() {
        mPlaylist.setCurrentPosition(mPlaylist.getCurrentPosition() - 1);
    }

    @Override
    public void onNext() {
        mPlaylist.setCurrentPosition(mPlaylist.getCurrentPosition() + 1);
    }
}
