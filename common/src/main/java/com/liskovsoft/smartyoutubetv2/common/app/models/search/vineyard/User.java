package com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard;

import android.os.Parcel;
import android.os.Parcelable;

public class User implements Parcelable {
    public String username;
    public int following;
    public int followerCount;
    public int verified;
    public String description;
    public String avatarUrl;
    public int twitterId;
    public String userId;
    public int twitterConnected;
    public int likeCount;
    public int facebookConnected;
    public int postCount;
    public String phoneNumber;
    public String location;
    public int followingCount;
    public String email;
    public String error;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.username);
        dest.writeInt(this.following);
        dest.writeInt(this.followerCount);
        dest.writeInt(this.verified);
        dest.writeString(this.description);
        dest.writeString(this.avatarUrl);
        dest.writeInt(this.twitterId);
        dest.writeString(this.userId);
        dest.writeInt(this.twitterConnected);
        dest.writeInt(this.likeCount);
        dest.writeInt(this.facebookConnected);
        dest.writeInt(this.postCount);
        dest.writeString(this.phoneNumber);
        dest.writeString(this.location);
        dest.writeInt(this.followingCount);
        dest.writeString(this.email);
        dest.writeString(this.error);
    }

    public User() {
    }

    protected User(Parcel in) {
        this.username = in.readString();
        this.following = in.readInt();
        this.followerCount = in.readInt();
        this.verified = in.readInt();
        this.description = in.readString();
        this.avatarUrl = in.readString();
        this.twitterId = in.readInt();
        this.userId = in.readString();
        this.twitterConnected = in.readInt();
        this.likeCount = in.readInt();
        this.facebookConnected = in.readInt();
        this.postCount = in.readInt();
        this.phoneNumber = in.readString();
        this.location = in.readString();
        this.followingCount = in.readInt();
        this.email = in.readString();
        this.error = in.readString();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        public User createFromParcel(Parcel source) {
            return new User(source);
        }

        public User[] newArray(int size) {
            return new User[size];
        }
    };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        if (following != user.following) return false;
        if (followerCount != user.followerCount) return false;
        if (verified != user.verified) return false;
        if (twitterId != user.twitterId) return false;
        if (twitterConnected != user.twitterConnected) return false;
        if (likeCount != user.likeCount) return false;
        if (facebookConnected != user.facebookConnected) return false;
        if (postCount != user.postCount) return false;
        if (followingCount != user.followingCount) return false;
        if (username != null ? !username.equals(user.username) : user.username != null)
            return false;
        if (description != null ? !description.equals(user.description) : user.description != null)
            return false;
        if (avatarUrl != null ? !avatarUrl.equals(user.avatarUrl) : user.avatarUrl != null)
            return false;
        if (userId != null ? !userId.equals(user.userId) : user.userId != null) return false;
        if (phoneNumber != null ? !phoneNumber.equals(user.phoneNumber) : user.phoneNumber != null)
            return false;
        if (location != null ? !location.equals(user.location) : user.location != null)
            return false;
        if (email != null ? !email.equals(user.email) : user.email != null) return false;
        return !(error != null ? !error.equals(user.error) : user.error != null);

    }

    @Override
    public int hashCode() {
        int result = username != null ? username.hashCode() : 0;
        result = 31 * result + following;
        result = 31 * result + followerCount;
        result = 31 * result + verified;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (avatarUrl != null ? avatarUrl.hashCode() : 0);
        result = 31 * result + twitterId;
        result = 31 * result + (userId != null ? userId.hashCode() : 0);
        result = 31 * result + twitterConnected;
        result = 31 * result + likeCount;
        result = 31 * result + facebookConnected;
        result = 31 * result + postCount;
        result = 31 * result + (phoneNumber != null ? phoneNumber.hashCode() : 0);
        result = 31 * result + (location != null ? location.hashCode() : 0);
        result = 31 * result + followingCount;
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (error != null ? error.hashCode() : 0);
        return result;
    }
}