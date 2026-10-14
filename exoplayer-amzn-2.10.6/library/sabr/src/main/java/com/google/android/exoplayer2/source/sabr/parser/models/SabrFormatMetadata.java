package com.google.android.exoplayer2.source.sabr.parser.models;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.exoplayer2.metadata.Metadata;

import java.util.Objects;

/** Carries YouTube's SABR format discriminator through ExoPlayer track selection. */
public final class SabrFormatMetadata implements Metadata.Entry {
    public final String xTags;
    public final String audioTrackId;
    /** Voice boost audio variant (same itag as the original, distinguished only by xtags). */
    public final boolean isVb;

    public SabrFormatMetadata(String xTags, String audioTrackId) {
        this(xTags, audioTrackId, false);
    }

    public SabrFormatMetadata(String xTags, String audioTrackId, boolean isVb) {
        this.xTags = xTags;
        this.audioTrackId = audioTrackId;
        this.isVb = isVb;
    }

    private SabrFormatMetadata(Parcel in) {
        this.xTags = in.readString();
        this.audioTrackId = in.readString();
        this.isVb = in.readInt() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(xTags);
        dest.writeString(audioTrackId);
        dest.writeInt(isVb ? 1 : 0);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof SabrFormatMetadata
                && Objects.equals(xTags, ((SabrFormatMetadata) obj).xTags)
                && Objects.equals(audioTrackId, ((SabrFormatMetadata) obj).audioTrackId)
                && isVb == ((SabrFormatMetadata) obj).isVb;
    }

    @Override
    public int hashCode() {
        return Objects.hash(xTags, audioTrackId, isVb);
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
