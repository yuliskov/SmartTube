package com.liskovsoft.smartyoutubetv2.common.openvpn

import android.content.Context
import android.os.Build
import com.liskovsoft.smartyoutubetv2.common.proxy.Proxy

class OpenVPNManager(val context: Context) {
    val isOpenVPNSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 19

    val isOpenVPNEnabled: Boolean
        get() = TODO("Not yet implemented")

    fun saveOpenVPNInfoToPrefs(proxy: Proxy?, checked: Boolean) {
        TODO("Not yet implemented")
    }

    fun configureOpenVPN() {
        TODO("Not yet implemented")
    }
}
