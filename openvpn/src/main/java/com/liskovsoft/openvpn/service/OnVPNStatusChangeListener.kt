package com.liskovsoft.openvpn.service

interface OnVPNStatusChangeListener {
    fun onProfileLoaded(profileLoaded: Boolean)
    fun onVPNStatusChanged(vpnActivated: Boolean)
}