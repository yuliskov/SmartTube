/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;
import android.annotation.TargetApi;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

/**
 * Created by arne on 26.11.14.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopDeviceStateListener extends ConnectivityManager.NetworkCallback {
    private String mLastConnectedStatus;
    private String mLastLinkProperties;
    private String mLastNetworkCapabilities;
    @Override
    public void onAvailable(Network network) {
        super.onAvailable(network);
        if (!network.toString().equals(mLastConnectedStatus)) {
            mLastConnectedStatus = network.toString();
            VpnStatus.logDebug("Connected to " + mLastConnectedStatus);
        }
    }
    @Override
    public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {
        super.onLinkPropertiesChanged(network, linkProperties);
        if (!linkProperties.toString().equals(mLastLinkProperties)) {
            mLastLinkProperties = linkProperties.toString();
            VpnStatus.logDebug(String.format("Linkproperties of %s: %s", network, linkProperties));
        }
    }
    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        if (!networkCapabilities.toString().equals(mLastNetworkCapabilities)) {
            mLastNetworkCapabilities = networkCapabilities.toString();
            VpnStatus.logDebug(String.format("Network capabilities of %s: %s", network, networkCapabilities));
        }
    }
}
