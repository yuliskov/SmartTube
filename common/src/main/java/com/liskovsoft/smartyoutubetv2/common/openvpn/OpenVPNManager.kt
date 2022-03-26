package com.liskovsoft.smartyoutubetv2.common.openvpn

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.liskovsoft.smartyoutubetv2.common.BuildConfig
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs
import com.liskovsoft.openvpn.OnVPNStatusChangeListener
import com.liskovsoft.openvpn.VPNService
import java.io.File

class OpenVPNManager(context: Context) {
    private val context = context.applicationContext
    private val prefs = AppPrefs.instance(context.applicationContext)
    private val routineName = "AZDownload"
    private var isConnected = false
    private var isDownload = true
    private val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val ovpnFile = File(downloadDir, "config.ovpn")
    private val numVPNService by lazy {
        VPNService(context).apply {
            launchVPN(prefs.openVPNConfigUri)
            setOnVPNStatusChangeListener(object : OnVPNStatusChangeListener {
                override fun onProfileLoaded(profileLoaded: Boolean) {
                    // TODO: notify that profile is loaded
                }

                override fun onVPNStatusChanged(vpnActivated: Boolean) {
                    // TODO: notify that ovpn is connected
                    isConnected = vpnActivated
                }
            })
        }
    }

    val isOpenVPNSupported: Boolean
        get() = Build.VERSION.SDK_INT >= 19

    val isOpenVPNEnabled: Boolean
        get() = prefs.isOpenVPNEnabled

    fun saveOpenVPNInfoToPrefs(info: OpenVPNInfo? = null, enabled: Boolean) {
        info?.apply {
            prefs.openVPNConfigUri = configAddress
        }

        prefs.isOpenVPNEnabled = enabled
    }

    fun configureOpenVPN() {
        // TODO: check permissions beforehand
        checkPermissions()

        downloadConfig()

        numVPNService.init() // toggle connection

        isConnected = numVPNService.cStatus
    }

    private fun checkPermissions() {
        //val perms = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        //perms.forEach { perm ->
        //    when {
        //        ContextCompat.checkSelfPermission(activity.baseContext, perm) == PackageManager.PERMISSION_GRANTED -> {
        //            // granted
        //        }
        //        ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) -> {
        //            activity as SettingsActivity.requestPermissionLauncher.launch(perm)
        //        }
        //        // not granted
        //        else -> {
        //            activity as SettingsActivity.requestPermissionLauncher.launch(perm)
        //        }
        //    }
        //}
    }

    private fun downloadConfig() {
        isDownload = true
        downloadOVPN()
    }

    private fun cancelDownloadConfig() {
        Handler(Looper.getMainLooper()).post {
            Coroutines.cancel(routineName)
            isDownload = false
        }
    }

    private fun downloadOVPN() {
        // TODO: notify about config download start

        Coroutines.launch(routineName) {
            try {
                Download.download(prefs.openVPNConfigUri, ovpnFile) { prc ->
                    Handler(Looper.getMainLooper()).post {
                        try {
                            // TODO: notify about config download progress (prc var)
                        } catch (e: Exception) {
                            isDownload = false
                        }
                    }
                    isDownload
                }
                Handler(Looper.getMainLooper()).post {
                    try {
                        // TODO: notify that config has been downloaded
                        openOVPN()
                    } catch (e: Exception) {
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        // TODO: notify about config download error
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    private fun openOVPN() {
        Handler(Looper.getMainLooper()).post {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                val path = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    Uri.fromFile(ovpnFile)
                } else {
                    intent.flags += Intent.FLAG_GRANT_READ_URI_PERMISSION
                    FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        ovpnFile
                    )
                }
                intent.setDataAndType(path, "application/x-openvpn-profile")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                val activities: List<ResolveInfo> = context.packageManager.queryIntentActivities(intent, 0)
                if (activities.isNotEmpty()) { // && vpnConnected
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}