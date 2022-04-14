/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by arne on 08.11.16.
 */
public enum ConnectionStatus implements Parcelable {
    LEVEL_CONNECTED,
    LEVEL_VPNPAUSED,
    LEVEL_CONNECTING_SERVER_REPLIED,
    LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
    LEVEL_NONETWORK,
    LEVEL_NOTCONNECTED,
    LEVEL_START,
    LEVEL_AUTH_FAILED,
    LEVEL_WAITING_FOR_USER_INPUT,
    UNKNOWN_LEVEL;
    public static final Creator<ConnectionStatus> CREATOR = new Creator<ConnectionStatus>() {
        @Override
        public ConnectionStatus createFromParcel(Parcel in) {
            return ConnectionStatus.values()[in.readInt()];
        }
        @Override
        public ConnectionStatus[] newArray(int size) {
            return new ConnectionStatus[size];
        }
    };
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }
    @Override
    public int describeContents() {
        return 0;
    }
}
