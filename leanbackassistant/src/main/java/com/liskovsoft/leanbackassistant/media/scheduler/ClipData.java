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

package com.liskovsoft.leanbackassistant.media.scheduler;

/**
 * This class encapsulates all the clip fields that are passed to the AddWatchNextService which is
 * the service responsible for adding videos to the watch next row. This is a convenience class
 * whose Builder is used to serialize the data passed to the JobService.
 */
public class ClipData {

    private final String mClipId;
    private final String mContentId;
    private final long mDuration;
    private final long mProgress;
    private final String mTitle;
    private final String mDescription;
    private final String mCardImageUrl;

    private ClipData(Builder b) {
        mClipId = b.mClipId;
        mContentId = b.mContentId;
        mDuration = b.mDuration;
        mProgress = b.mProgress;
        mTitle = b.mTitle;
        mDescription = b.mDescription;
        mCardImageUrl = b.mCardImageUrl;
    }

    public String getClipId() {
        return mClipId;
    }

    public String getContentId() {
        return mContentId;
    }

    public long getDuration() {
        return mDuration;
    }

    public long getProgress() {
        return mProgress;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getCardImageUrl() {
        return mCardImageUrl;
    }

    public static class Builder {

        String mClipId;
        String mContentId;
        long mDuration;
        long mProgress;
        String mTitle;
        String mDescription;
        String mCardImageUrl;

        public Builder setClipId(String id) {
            mClipId = id;
            return this;
        }

        public Builder setContentId(String contentId) {
            mContentId = contentId;
            return this;
        }

        public Builder setDuration(long duration) {
            mDuration = duration;
            return this;
        }

        public Builder setProgress(long progress) {
            mProgress = progress;
            return this;
        }

        public Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        public Builder setDescription(String description) {
            mDescription = description;
            return this;
        }

        public Builder setCardImageUrl(String cardImageUrl) {
            mCardImageUrl = cardImageUrl;
            return this;
        }

        public ClipData build() {
            return new ClipData(this);
        }
    }
}
