/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;
public interface OpenVPNManagement {
    int mBytecountInterval = 2;
    void reconnect();
    void pause(pauseReason reason);
    void resume();
    /**
     * @param replaceConnection True if the VPN is connected by a new connection.
     * @return true if there was a process that has been send a stop signal
     */
    boolean stopVPN(boolean replaceConnection);
    /*
     * Rebind the interface
     */
    void networkChange(boolean sameNetwork);
    void setPauseCallback(PausedStateCallback callback);
    enum pauseReason {
        noNetwork,
        userPause,
        screenOff,
    }
    interface PausedStateCallback {
        boolean shouldBeRunning();
    }
}
