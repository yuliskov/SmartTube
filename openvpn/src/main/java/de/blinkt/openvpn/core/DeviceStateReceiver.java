/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Handler;


import java.util.LinkedList;

import de.blinkt.openvpn.core.VpnStatus.ByteCountListener;
import com.liskovsoft.openvpn.R;

import static de.blinkt.openvpn.core.OpenVPNManagement.pauseReason;

public class DeviceStateReceiver extends BroadcastReceiver implements ByteCountListener, OpenVPNManagement.PausedStateCallback {
    private final Handler mDisconnectHandler;
    // Window time in s
    private final int TRAFFIC_WINDOW = 60;
    // Data traffic limit in bytes
    private final long TRAFFIC_LIMIT = 64 * 1024;
    // Time to wait after network disconnect to pause the VPN
    private final int DISCONNECT_WAIT = 20;
    connectState network = connectState.DISCONNECTED;
    connectState screen = connectState.SHOULDBECONNECTED;
    connectState userpause = connectState.SHOULDBECONNECTED;
    private int lastNetwork = -1;
    private OpenVPNManagement mManagement;
    private String lastStateMsg = null;
    private Runnable mDelayDisconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!(network == connectState.PENDINGDISCONNECT)) return;
            network = connectState.DISCONNECTED;
            // Set screen state to be disconnected if disconnect pending
            if (screen == connectState.PENDINGDISCONNECT) screen = connectState.DISCONNECTED;
            mManagement.pause(getPauseReason());
        }
    };
    private NetworkInfo lastConnectedNetwork;
    private LinkedList<Datapoint> trafficdata = new LinkedList<>();

    public DeviceStateReceiver(OpenVPNManagement magnagement) {
        super();
        mManagement = magnagement;
        mManagement.setPauseCallback(this);
        mDisconnectHandler = new Handler();
    }

    public static boolean equalsObj(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    @Override
    public boolean shouldBeRunning() {
        return shouldBeConnected();
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (screen != connectState.PENDINGDISCONNECT) return;
        long total = diffIn + diffOut;
        trafficdata.add(new Datapoint(System.currentTimeMillis(), total));
        while (trafficdata.getFirst().timestamp <= (System.currentTimeMillis() - TRAFFIC_WINDOW * 1000)) {
            trafficdata.removeFirst();
        }
        long windowtraffic = 0;
        for (Datapoint dp : trafficdata)
            windowtraffic += dp.data;
        if (windowtraffic < TRAFFIC_LIMIT) {
            screen = connectState.DISCONNECTED;
            VpnStatus.logInfo(R.string.screenoff_pause, "64 kB", TRAFFIC_WINDOW);
            mManagement.pause(getPauseReason());
        }
    }

    public void userPause(boolean pause) {
        if (pause) {
            userpause = connectState.DISCONNECTED;
            // Check if we should disconnect
            mManagement.pause(getPauseReason());
        } else {
            boolean wereConnected = shouldBeConnected();
            userpause = connectState.SHOULDBECONNECTED;
            if (shouldBeConnected() && !wereConnected) mManagement.resume();
            else
                // Update the reason why we currently paused
                mManagement.pause(getPauseReason());
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
        if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            networkStateChange(context);
        } else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            boolean screenOffPause = prefs.getBoolean("screenoff", false);
            if (screenOffPause) {
                if (ProfileManager.getLastConnectedVpn() != null && !ProfileManager.getLastConnectedVpn().mPersistTun) VpnStatus.logError(R.string.screen_nopersistenttun);
                screen = connectState.PENDINGDISCONNECT;
                fillTrafficData();
                if (network == connectState.DISCONNECTED || userpause == connectState.DISCONNECTED) screen = connectState.DISCONNECTED;
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
            // Network was disabled because screen off
            boolean connected = shouldBeConnected();
            screen = connectState.SHOULDBECONNECTED;
            /* We should connect now, cancel any outstanding disconnect timer */
            mDisconnectHandler.removeCallbacks(mDelayDisconnectRunnable);
            /* should be connected has changed because the screen is on now, connect the VPN */
            if (shouldBeConnected() != connected) mManagement.resume();
            else if (!shouldBeConnected())
                /*Update the reason why we are still paused */ mManagement.pause(getPauseReason());
        }
    }

    private void fillTrafficData() {
        trafficdata.add(new Datapoint(System.currentTimeMillis(), TRAFFIC_LIMIT));
    }

    public void networkStateChange(Context context) {
        NetworkInfo networkInfo = getCurrentNetworkInfo(context);
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(context);
        boolean sendusr1 = prefs.getBoolean("netchangereconnect", true);
        String netstatestring;
        if (networkInfo == null) {
            netstatestring = "not connected";
        } else {
            String subtype = networkInfo.getSubtypeName();
            if (subtype == null) subtype = "";
            String extrainfo = networkInfo.getExtraInfo();
            if (extrainfo == null) extrainfo = "";
            /*
            if(networkInfo.getType()==android.net.ConnectivityManager.TYPE_WIFI) {
				WifiManager wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiinfo = wifiMgr.getConnectionInfo();
				extrainfo+=wifiinfo.getBSSID();
				subtype += wifiinfo.getNetworkId();
			}*/
            netstatestring = String.format("%2$s %4$s to %1$s %3$s", networkInfo.getTypeName(), networkInfo.getDetailedState(), extrainfo, subtype);
        }
        if (networkInfo != null && networkInfo.getState() == State.CONNECTED) {
            int newnet = networkInfo.getType();
            boolean pendingDisconnect = (network == connectState.PENDINGDISCONNECT);
            network = connectState.SHOULDBECONNECTED;
            boolean sameNetwork;
            sameNetwork = !(lastConnectedNetwork == null || lastConnectedNetwork.getType() != networkInfo.getType() || !equalsObj(lastConnectedNetwork.getExtraInfo(), networkInfo.getExtraInfo()));
            /* Same network, connection still 'established' */
            if (pendingDisconnect && sameNetwork) {
                mDisconnectHandler.removeCallbacks(mDelayDisconnectRunnable);
                // Reprotect the sockets just be sure
                mManagement.networkChange(true);
            } else {
                /* Different network or connection not established anymore */
                if (screen == connectState.PENDINGDISCONNECT) screen = connectState.DISCONNECTED;
                if (shouldBeConnected()) {
                    mDisconnectHandler.removeCallbacks(mDelayDisconnectRunnable);
                    if (pendingDisconnect || !sameNetwork) mManagement.networkChange(sameNetwork);
                    else mManagement.resume();
                }
                lastNetwork = newnet;
                lastConnectedNetwork = networkInfo;
            }
        } else if (networkInfo == null) {
            // Not connected, stop openvpn, set last connected network to no network
            lastNetwork = -1;
            if (sendusr1) {
                network = connectState.PENDINGDISCONNECT;
                mDisconnectHandler.postDelayed(mDelayDisconnectRunnable, DISCONNECT_WAIT * 1000);
            }
        }
        if (!netstatestring.equals(lastStateMsg)) VpnStatus.logInfo(R.string.netstatus, netstatestring);
        VpnStatus.logDebug(String.format("Debug state info: %s, pause: %s, shouldbeconnected: %s, network: %s ", netstatestring, getPauseReason(), shouldBeConnected(), network));
        lastStateMsg = netstatestring;
    }

    public boolean isUserPaused() {
        return userpause == connectState.DISCONNECTED;
    }

    private boolean shouldBeConnected() {
        return (screen == connectState.SHOULDBECONNECTED && userpause == connectState.SHOULDBECONNECTED && network == connectState.SHOULDBECONNECTED);
    }

    private pauseReason getPauseReason() {
        if (userpause == connectState.DISCONNECTED) return pauseReason.userPause;
        if (screen == connectState.DISCONNECTED) return pauseReason.screenOff;
        if (network == connectState.DISCONNECTED) return pauseReason.noNetwork;
        return pauseReason.userPause;
    }

    private NetworkInfo getCurrentNetworkInfo(Context context) {
        ConnectivityManager conn = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return conn.getActiveNetworkInfo();
    }

    private enum connectState {
        SHOULDBECONNECTED, PENDINGDISCONNECT, DISCONNECTED
    }

    private static class Datapoint {
        long timestamp;
        long data;

        private Datapoint(long t, long d) {
            timestamp = t;
            data = d;
        }
    }
}
