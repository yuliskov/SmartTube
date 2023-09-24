/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.liskovsoft.leanbackassistant.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Playlist {
    private final String mName;
    private final String mDescription;
    private final String mVideoUri;
    private final String mBgImage;
    private final String mTitle;
    private final String mPlaylistId;
    private List<Clip> mClips;
    private boolean mChannelPublished;
    private long mChannelId = -1;
    private static final String DELIM = ",";
    private String mChannelKey;
    private String mProgramsKey;
    private String mPlaylistUrl;
    private int mLogoResId = -1;
    private final boolean mIsDefault;

    public Playlist(String name, String playlistId) {
        this(name, Collections.emptyList(), playlistId, false);
    }

    public Playlist(String name, String playlistId, boolean isDefault) {
        this(name, Collections.emptyList(), playlistId, isDefault);
    }

    public Playlist(String name, List<Clip> clip, String playlistId, boolean isDefault) {
        mName = name;
        mTitle = "playlist title";
        mDescription = "playlist description";
        mVideoUri = "dsf";
        mBgImage = "asdf";
        mClips = clip;
        mPlaylistId = playlistId;
        mIsDefault = isDefault;
    }

    public String getName() {
        return mName;
    }

    public void setClips(List<Clip> clips) {
        mClips = clips;
    }

    public List<Clip> getClips() {
        return mClips;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getPlaylistId() {
        return mPlaylistId;
    }

    /**
     * Google TV fix: no dialog to set-up the channels.
     */
    public boolean isDefault() {
        return mIsDefault;
    }

    public boolean isChannelPublished() {
        return mChannelPublished;
    }

    public void setIsPublished(boolean channelPublished) {
        mChannelPublished = channelPublished;
    }

    public void setPublishedId(long id) {
        mChannelPublished = true;
        mChannelId = id;
    }

    public long getPublishedId() {
        return mChannelId;
    }

    public String toString() {
        return "Playlist { mName = '" + mName + "' mDescription = '" + mDescription
                + "' mVideoUri = '" + mVideoUri + "' mBgImage = '" + mBgImage + "' mTitle = '"
                + mTitle + "' mList = '" + mClips + "' mId = '" + mPlaylistId
                + "' mChannelPublished" + mChannelPublished + "'";
    }

    public String getPublishedClipsIds() {
        StringBuilder result = new StringBuilder();

        if (mClips != null) {
            for (Clip c : mClips) {
                long programId = c.getProgramId();

                if (programId == -1) { // not published
                    continue;
                }

                if (result.length() == 0) {
                    result.append(programId);
                } else {
                    result.append(DELIM);
                    result.append(programId);
                }

            }
        }

        return result.toString();
    }

    private static List<Long> parseClipIds(String clipsIds) {
        List<Long> result = new ArrayList<>();

        if (clipsIds != null && !clipsIds.isEmpty()) {
            String[] split = clipsIds.split(DELIM);

            for (String id : split) {
                result.add(Long.parseLong(id));
            }
        }

        return result;
    }

    public void restoreClipsIds(String clipsIds) {
        if (clipsIds != null && mClips != null) {
            List<Long> ids = parseClipIds(clipsIds);
            for (int i = 0; i < ids.size(); i++) {
                if (mClips.size() > i) { // avoid index of bound exception
                    Clip clip = mClips.get(i);
                    clip.setProgramId(ids.get(i));
                }
            }
        }
    }

    public void setChannelKey(String key) {
        mChannelKey = key;
    }

    public String getChannelKey() {
        return mChannelKey;
    }

    public void setProgramsKey(String key) {
        mProgramsKey = key;
    }

    public String getProgramsKey() {
        return mProgramsKey;
    }

    public String getPlaylistUrl() {
        return mPlaylistUrl;
    }

    public void setPlaylistUrl(String url) {
        mPlaylistUrl = url;
    }

    public int getLogoResId() {
        return mLogoResId;
    }

    public void setLogoResId(int resId) {
        mLogoResId = resId;
    }
}
