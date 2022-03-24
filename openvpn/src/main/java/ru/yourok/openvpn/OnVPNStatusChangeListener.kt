package ru.yourok.openvpn

interface OnVPNStatusChangeListener {
    fun onProfileLoaded(profileLoaded: Boolean)
    fun onVPNStatusChanged(vpnActivated: Boolean)
}