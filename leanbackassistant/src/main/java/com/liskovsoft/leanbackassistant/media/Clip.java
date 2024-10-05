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

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.tvprovider.media.tv.BasePreviewProgram.AspectRatio;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Clip class represents video entity with title, description, image thumbs and video url.
 */
public class Clip implements Parcelable {
    public static final Creator CREATOR = new Creator() {
        public Clip createFromParcel(Parcel in) {
            return new Clip(in);
        }

        public Clip[] newArray(int size) {
            return new Clip[size];
        }
    };
    private final String mClipId;
    private final String mContentId;
    private final String mTitle;
    private final String mDescription;
    private final long mDurationMs;
    private final String mBgImageUrl;
    private final String mCardImageUrl;
    private final String mVideoUrl;
    private final String mPreviewVideoUrl;
    private final boolean mIsVideoProtected;
    private final boolean mIsLive;
    private final String mCategory;
    private int mAspectRatio;
    private long mProgramId = -1;
    private int mViewCount;

    Clip(String title, String description, long durationMs, String bgImageUrl, String cardImageUrl,
            String videoUrl, String previewVideoUrl, boolean isVideoProtected, boolean isLive, String category,
            String clipId, String contentId, int aspectRatio) {
        mClipId = clipId;
        mContentId = contentId;
        mTitle = title;
        mDescription = description;
        mDurationMs = durationMs;
        mBgImageUrl = bgImageUrl;
        mCardImageUrl = cardImageUrl;
        mVideoUrl = videoUrl;
        mPreviewVideoUrl = previewVideoUrl;
        mIsVideoProtected = isVideoProtected;
        mIsLive = isLive;
        mCategory = category;
        mAspectRatio = aspectRatio;
    }

    private Clip(Parcel in) {
        mClipId = in.readString();
        mContentId = in.readString();
        mTitle = in.readString();
        mDescription = in.readString();
        mDurationMs = in.readLong();
        mBgImageUrl = in.readString();
        mCardImageUrl = in.readString();
        mVideoUrl = in.readString();
        mPreviewVideoUrl = in.readString();
        mIsVideoProtected = in.readByte() == 1;
        mIsLive = in.readByte() == 1;
        mCategory = in.readString();
        mProgramId = in.readLong();
        mViewCount = in.readInt();
    }

    public long getProgramId() {
        return mProgramId;
    }

    public void setProgramId(long programId) {
        mProgramId = programId;
    }

    public String getClipId() {
        return mClipId;
    }

    public String getContentId() {
        return mContentId;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getDescription() {
        return mDescription;
    }

    public long getDurationMs() {
        return mDurationMs;
    }

    public String getVideoUrl() {
        return mVideoUrl;
    }

    public String getPreviewVideoUrl() {
        return mPreviewVideoUrl;
    }

    public boolean isVideoProtected() {
        return mIsVideoProtected;
    }

    public boolean isLive() {
        return mIsLive;
    }

    String getBackgroundImageUrl() {
        return mBgImageUrl;
    }

    public String getCardImageUrl() {
        return mCardImageUrl;
    }

    URI getBackgroundImageURI() {
        try {
            return new URI(mBgImageUrl);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    URI getCardImageURI() {
        try {
            return new URI(getCardImageUrl());
        } catch (URISyntaxException e) {
            return null;
        }
    }

    int incrementViewCount() {
        return mViewCount += 1;
    }

    void setViewCount(int viewCount) {
        mViewCount = viewCount;
    }

    @AspectRatio
    public int getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mClipId);
        dest.writeString(mContentId);
        dest.writeString(mTitle);
        dest.writeString(mDescription);
        dest.writeLong(mDurationMs);
        dest.writeString(mBgImageUrl);
        dest.writeString(mCardImageUrl);
        dest.writeString(mVideoUrl);
        dest.writeString(mPreviewVideoUrl);
        dest.writeByte((byte) (isVideoProtected() ? 1 : 0));
        dest.writeByte((byte) (isLive() ? 1 : 0));
        dest.writeString(mCategory);
        dest.writeLong(mProgramId);
        dest.writeInt(mViewCount);
    }

    @NonNull
    @Override
    public String toString() {
        return "Clip{" +
                "clipId=" + mClipId +
                ", contentId='" + mContentId + '\'' +
                ", title='" + mTitle + '\'' +
                ", videoUrl='" + mVideoUrl + '\'' +
                ", backgroundImageUrl='" + mBgImageUrl + '\'' +
                ", backgroundImageURI='" + getBackgroundImageURI().toString() + '\'' +
                ", cardImageUrl='" + mCardImageUrl + '\'' +
                ", aspectRatio='" + mAspectRatio + '\'' +
                ", programId='" + mProgramId + '\'' +
                ", viewCount='" + mViewCount + '\'' +
                '}';
    }
}
