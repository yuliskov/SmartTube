package com.liskovsoft.openvpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.*
import android.util.Log
import android.widget.Toast
import de.blinkt.openvpn.LaunchVPN
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.*
import de.blinkt.openvpn.core.VpnStatus.ByteCountListener
import de.blinkt.openvpn.core.VpnStatus.StateListener
import kotlinx.coroutines.*
import com.liskovsoft.openvpn.ProfileAsync.OnProfileLoadListener
import ru.yourok.openvpn.BuildConfig
import ru.yourok.openvpn.R
import java.lang.Runnable


class VPNService(private val context: Context) : ByteCountListener, StateListener {
    private var profileAsync: ProfileAsync? = null
    private var profileStatus = false
    private var listener: OnVPNStatusChangeListener? = null
    private var value = false

    fun setOnVPNStatusChangeListener(listener: OnVPNStatusChangeListener?) {
        this.listener = listener
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
        }
    }

    fun launchVPN(url: String?) {
        if (!App.isStart) {
            // TODO: store antizapret profile
            DataCleanManager.cleanApplicationData(context)
            setProfileLoadStatus(false)
            profileAsync = url?.let {
                ProfileAsync(context, object : OnProfileLoadListener {
                    override fun onProfileLoadSuccess() {
                        setProfileLoadStatus(true)
                    }

                    override fun onProfileLoadFailed(msg: String?) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(R.string.init_fail) + msg,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }, it)
            }
            profileAsync?.execute()
        }
    }

    fun init() {
        val r = Runnable {
            if (!App.isStart) {
                startVPN()
                App.isStart = true
            } else {
                stopVPN()
                App.isStart = false
            }
        }
        r.run()
    }

    private fun onStop() {
        VpnStatus.removeStateListener(this)
        VpnStatus.removeByteCountListener(this)
    }

    fun onResume() {
        VpnStatus.addStateListener(this)
        VpnStatus.addByteCountListener(this)
        val intent = Intent(context, OpenVPNService::class.java)
        intent.action = OpenVPNService.START_SERVICE
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
    }

    fun onPause() {
        context.unbindService(mConnection)
    }

    fun cleanup() {
        if (profileAsync != null && !profileAsync!!.isCancelled) {
            profileAsync?.cancel(true)
        }
    }

    private fun startVPN() {
        try {
            val pm = ProfileManager.getInstance(context)
            val profile = pm.getProfileByName(Build.MODEL)
            startVPNConnection(profile)
        } catch (ex: Exception) {
            App.isStart = false
        }
    }

    private fun stopVPN() {
        stopVPNConnection()
        cStatus = false
    }

    var cStatus: Boolean
        get() = value
        private set(value) {
            this.value = value
            listener?.onVPNStatusChanged(value)
        }

    private fun setProfileLoadStatus(profileStatus: Boolean) {
        this.profileStatus = profileStatus
        listener?.onProfileLoaded(profileStatus)
    }

    private fun startVPNConnection(vp: VpnProfile) {
        val intent = Intent(context, LaunchVPN::class.java)
        intent.putExtra(LaunchVPN.EXTRA_KEY, vp.uuid.toString())
        intent.action = Intent.ACTION_MAIN
        context.startActivity(intent)
    }

    private fun stopVPNConnection() {
        ProfileManager.setConnectedVpnProfileDisconnected(context)
        if (mService != null) {
            try {
                mService?.stopVPN(false)
                onStop()
            } catch (e: RemoteException) {
                VpnStatus.logException(e)
            }
        }
    }

    override fun updateState(
        state: String,
        logmessage: String,
        localizedResId: Int,
        level: ConnectionStatus
    ) {
        //context.runOnUiThread {
        CoroutineScope(Dispatchers.Default).launch {
            when (state) {
                "CONNECTED" -> {
                    Log.i("VPNService", "status: connected")
                    App.isStart = true
                    withContext(Dispatchers.Main) {
                        cStatus = true
                    }
                }
                "DISCONNECTED" -> {
                    Log.i("VPNService", "status: disconnected")
                    withContext(Dispatchers.Main) {
                        cStatus = false
                    }
                }
                "AUTH_FAILED" -> {
                    Log.i("VPNService", "status: auth failed")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Wrong Username or Password!", Toast.LENGTH_SHORT).show()
                        cStatus = false
                    }
                }
            }
        }
    }

    override fun setConnectedVPN(uuid: String) {}
    override fun updateByteCount(`in`: Long, out: Long, diffIn: Long, diffOut: Long) {}

    fun startVpnService(context: Context) {
        val pm = ProfileManager.getInstance(context)
        val vp = pm.getProfileByName(Build.MODEL)
        CoroutineScope(Dispatchers.IO).launch {
            var count = 60
            while (!isNetworkAvailable(context) && count > 0) {
                delay(1000) // wait for network
                count--
            }
            if (isNetworkAvailable(context)) {
                if (BuildConfig.DEBUG)
                    Log.d("*****", "startVpnService with profile $vp (${vp.mConnections[0].mServerName})")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val vpnPermissionIntent = VpnService.prepare(context) // context = this@ExternalOpenVPNService
                    /* Check if we need to show the confirmation dialog */
                    if (vpnPermissionIntent != null) {
                        launchVPN(vp, context)
                    } else {
                        VPNLaunchHelper.startOpenVpn(vp, context)
                    }
                } else {
                    launchVPN(vp, context)
                }
            }
        }
    }

    private fun launchVPN(vp: VpnProfile, context: Context) {
        val startVpnIntent = Intent(Intent.ACTION_MAIN)
        startVpnIntent.setClass(context, LaunchVPN::class.java)
        startVpnIntent.putExtra(LaunchVPN.EXTRA_KEY, vp.uuidString)
        startVpnIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startVpnIntent.putExtra(LaunchVPN.EXTRA_HIDELOG, true) // true
        context.startActivity(startVpnIntent)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                ?: return false
        val info = connectivityManager.activeNetworkInfo
        return info != null && info.isAvailable && info.isConnected
    }

    companion object {
        private var mService: IOpenVPNServiceInternal? = null
    }
}