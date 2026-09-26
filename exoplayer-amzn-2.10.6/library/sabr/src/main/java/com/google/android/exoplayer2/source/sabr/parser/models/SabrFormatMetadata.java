package com.google.android.exoplayer2.source.sabr.parser.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.exoplayer2.metadata.Metadata;

import java.util.Objects;

/** Carries YouTube's SABR format discriminator through ExoPlayer track selection. */
public final class SabrFormatMetadata implements Metadata.Entry {
    public final String xTags;
    public final String audioTrackId;

    public SabrFormatMetadata(String xTags, String audioTrackId) {
        this.xTags = xTags;
        this.audioTrackId = audioTrackId;
    }

    private SabrFormatMetadata(Parcel in) {
        this.xTags = in.readString();
        this.audioTrackId = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(xTags);
        dest.writeString(audioTrackId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof SabrFormatMetadata
                && Objects.equals(xTags, ((SabrFormatMetadata) obj).xTags)
                && Objects.equals(audioTrackId, ((SabrFormatMetadata) obj).audioTrackId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xTags, audioTrackId);
    }

    public static final Parcelable.Creator<SabrFormatMetadata> CREATOR =
            new Parcelable.Creator<SabrFormatMetadata>() {
                @Override
                public SabrFormatMetadata createFromParcel(Parcel in) {
                    return new SabrFormatMetadata(in);
                }

                @Override
                public SabrFormatMetadata[] newArray(int size) {
                    return new SabrFormatMetadata[size];
                }
            };
}
