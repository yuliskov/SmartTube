package com.liskovsoft.smartyoutubetv2.common.openvpn

//import androidx.transition.Fade
//import ru.yourok.num.BuildConfig
//import ru.yourok.num.R
//import ru.yourok.num.activity.SettingsActivity
//import ru.yourok.num.activity.utils.setLanguage
//import ru.yourok.num.app.AntiZapretProfile
//import ru.yourok.num.app.App
//import ru.yourok.num.app.ZaboronaProfile
//import ru.yourok.num.utils.Coroutines
//import ru.yourok.num.utils.Coroutines
//import ru.yourok.num.utils.Download
//import ru.yourok.num.utils.Prefs
//import ru.yourok.num.utils.Utils
import android.app.Activity
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
import ru.yourok.openvpn.OnVPNStatusChangeListener
import ru.yourok.openvpn.VPNService
import java.io.File

class OpenVPNManager2(val activity: Activity, val context: Context) {
    private val routineName = "AZDownload"
    private var isConnected = false
    private var isDownload = true
    private val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    private val profile = AntiZapretProfile
    private val ovpnFile = File(downloadDir, "antizapret-tcp.ovpn")
    private val numVPNService by lazy {
        VPNService(activity, context).apply {
            launchVPN(profile)
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
        get() = TODO("Not yet implemented")

    fun saveOpenVPNInfoToPrefs(info: OpenVPNInfo?, checked: Boolean) {
        TODO("Not yet implemented")
    }

    fun configureOpenVPN() {
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
        downloadOVPN(activity)
    }

    private fun cancelDownloadConfig() {
        Handler(Looper.getMainLooper()).post {
            Coroutines.cancel(routineName)
            isDownload = false
        }
    }

    private fun downloadOVPN(activity: Activity) {
        // TODO: notify about config download start

        Coroutines.launch(routineName) {
            try {
                Download.download(profile, ovpnFile) { prc ->
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
                        openOVPN(activity, ovpnFile)
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

    private fun openOVPN(activity: Activity, ovpnFile: File) {
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

                val activities: List<ResolveInfo> = activity.packageManager.queryIntentActivities(intent, 0)
                if (activities.isNotEmpty()) { // && vpnConnected
                    activity.startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}