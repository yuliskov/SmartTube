package com.liskovsoft.smartyoutubetv2.common.mvp.models.playback;

import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Playlist;
import com.liskovsoft.smartyoutubetv2.common.mvp.models.data.Video;

public class PlaylistUpdater extends PlayerCommandProcessorHelper {
    private PlayerCommandHandler mCommandHandler;
    private static PlaylistUpdater sInstance;
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

    @Override
    public void setCommandHandler(PlayerCommandHandler commandHandler) {
        mCommandHandler = commandHandler;
    }
}
