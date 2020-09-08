/*
 * Copyright (c) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import android.media.MediaDescription;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.RequiresApi;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;

/**
 * Video is an immutable object that holds the various metadata associated with a single video.
 */
public final class Video implements Parcelable {
    public long id;
    public String category;
    public String title;
    public String description;
    public String bgImageUrl;
    public String cardImageUrl;
    public String videoId;
    public String videoUrl;
    public String studio;
    public int percentWatched;
    public MediaItem mediaItem;
    public MediaItemMetadata mediaItemMetadata;

    private Video() {
        
    }

    private Video(
            final long id,
            final String category,
            final String title,
            final String desc,
            final String videoId,
            final String videoUrl,
            final String bgImageUrl,
            final String cardImageUrl,
            final String studio,
            final MediaItem mediaItem,
            final MediaItemMetadata mediaItemMetadata) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.description = desc;
        this.videoId = videoId;
        this.videoUrl = videoUrl;
        this.bgImageUrl = bgImageUrl;
        this.cardImageUrl = cardImageUrl;
        this.studio = studio;
        this.mediaItem = mediaItem;
        this.mediaItemMetadata = mediaItemMetadata;
    }

    protected Video(Parcel in) {
        id = in.readLong();
        category = in.readString();
        title = in.readString();
        description = in.readString();
        bgImageUrl = in.readString();
        cardImageUrl = in.readString();
        videoId = in.readString();
        videoUrl = in.readString();
        studio = in.readString();
        mediaItem = null;
        mediaItemMetadata = null;
    }

    public static Video from(MediaItem item) {
        Video video = new Video();

        video.id = item.getId();
        video.title = item.getTitle();
        video.category = item.getContentType();
        video.description = item.getDescription();
        video.videoId = item.getMediaId();
        video.videoUrl = item.getMediaUrl();
        video.bgImageUrl = item.getBackgroundImageUrl();
        video.cardImageUrl = item.getCardImageUrl();
        video.studio = item.getDescription();
        video.percentWatched = item.getPercentWatched();
        video.mediaItem = item;

        return video;
    }

    public static final Creator<Video> CREATOR = new Creator<Video>() {
        @Override
        public Video createFromParcel(Parcel in) {
            return new Video(in);
        }

        @Override
        public Video[] newArray(int size) {
            return new Video[size];
        }
    };

    @Override
    public boolean equals(Object m) {
        return m instanceof Video && id == ((Video) m).id;
    }

    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(category);
        dest.writeString(title);
        dest.writeString(description);
        dest.writeString(bgImageUrl);
        dest.writeString(cardImageUrl);
        dest.writeString(videoId);
        dest.writeString(videoUrl);
        dest.writeString(studio);
    }

    @Override
    public String toString() {
        String s = "Video{";
        s += "id=" + id;
        s += ", category='" + category + "'";
        s += ", title='" + title + "'";
        s += ", videoId='" + videoId + "'";
        s += ", videoUrl='" + videoUrl + "'";
        s += ", bgImageUrl='" + bgImageUrl + "'";
        s += ", cardImageUrl='" + cardImageUrl + "'";
        s += ", studio='" + cardImageUrl + "'";
        s += "}";
        return s;
    }

    // Builder for Video object.
    public static class VideoBuilder {
        private long id;
        private String category;
        private String title;
        private String desc;
        private String bgImageUrl;
        private String cardImageUrl;
        private String videoId;
        private String videoUrl;
        private String studio;
        private MediaItem mediaItem;
        private MediaItemMetadata mediaItemMetadata;

        public VideoBuilder id(long id) {
            this.id = id;
            return this;
        }

        public VideoBuilder category(String category) {
            this.category = category;
            return this;
        }

        public VideoBuilder title(String title) {
            this.title = title;
            return this;
        }

        public VideoBuilder description(String desc) {
            this.desc = desc;
            return this;
        }

        public VideoBuilder videoId(String videoId) {
            this.videoId = videoId;
            return this;
        }

        public VideoBuilder videoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
            return this;
        }

        public VideoBuilder bgImageUrl(String bgImageUrl) {
            this.bgImageUrl = bgImageUrl;
            return this;
        }

        public VideoBuilder cardImageUrl(String cardImageUrl) {
            this.cardImageUrl = cardImageUrl;
            return this;
        }

        public VideoBuilder studio(String studio) {
            this.studio = studio;
            return this;
        }

        public VideoBuilder mediaItem(MediaItem mediaItem) {
            this.mediaItem = mediaItem;
            return this;
        }

        public VideoBuilder mediaItemMetadata(MediaItemMetadata itemMetadata) {
            this.mediaItemMetadata = itemMetadata;
            return this;
        }

        @RequiresApi(21)
        public Video buildFromMediaDesc(MediaDescription desc) {
            return new Video(
                    Long.parseLong(desc.getMediaId()),
                    "", // Category - not provided by MediaDescription.
                    String.valueOf(desc.getTitle()),
                    String.valueOf(desc.getDescription()),
                    "", // Media ID - not provided by MediaDescription.
                    "", // Media URI - not provided by MediaDescription.
                    "", // Background Image URI - not provided by MediaDescription.
                    String.valueOf(desc.getIconUri()),
                    String.valueOf(desc.getSubtitle()),
                    null,
                    null
            );
        }

        public Video build() {
            return new Video(
                    id,
                    category,
                    title,
                    desc,
                    videoId,
                    videoUrl,
                    bgImageUrl,
                    cardImageUrl,
                    studio,
                    mediaItem,
                    mediaItemMetadata
            );
        }
    }
}
