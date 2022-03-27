package com.liskovsoft.smartyoutubetv2.common.openvpn

import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.liskovsoft.sharedutils.helpers.FileHelpers
import com.liskovsoft.sharedutils.helpers.PermissionHelpers
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity

class OpenVPNDialog(private val context: Context): OpenVPNManager.OpenVPNCallback, MotherActivity.OnPermissions {
    private var pendingHandler: (() -> Unit)? = null
    private var openVPNConfigDialog: AlertDialog? = null
    private val openVPNManager: OpenVPNManager = OpenVPNManager.instance(context, this)

    val isOpenVPNSupported: Boolean
        get() = openVPNManager.isOpenVPNSupported
    val isOpenVPNEnabled: Boolean
        get() = openVPNManager.isOpenVPNEnabled

    private companion object {
        val TAG: String = OpenVPNDialog::class.java.simpleName
    }

    @RequiresApi(19)
    fun enable(enabled: Boolean) {
        if (isOpenVPNSupported) {
            openVPNManager.saveOpenVPNInfoToPrefs(enabled = enabled)
            if (enabled) {
                showProxyConfigDialog()
            } else {
                checkPermissionsAndConfigureOpenVPN()
            }
        }
    }

    override fun onPermissions(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if (requestCode == PermissionHelpers.REQUEST_EXTERNAL_STORAGE) {
            if (grantResults != null && grantResults.size >= 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "REQUEST_EXTERNAL_STORAGE permission has been granted");

                pendingHandler?.invoke()
            }
        }
    }

    override fun onProfileLoaded(profileLoaded: Boolean) {
        appendStatusMessage("Profile loaded: %s", if (profileLoaded) "YES" else "NO")
    }

    override fun onVPNStatusChanged(vpnActivated: Boolean) {
        appendStatusMessage("OpenVPN activated: %s", if (vpnActivated) "YES" else "NO")
    }

    override fun onConfigDownloadStart() {
        appendStatusMessage("Config download start")
    }

    //override fun onConfigDownloadProgress(progress: Int) {
    //    appendStatusMessage("Config download progress: %d", progress)
    //}

    override fun onConfigDownloadEnd() {
        appendStatusMessage("Config download end")
    }

    override fun onConfigDownloadError() {
        appendStatusMessage("Config download error")
    }

    private fun appendStatusMessage(msgFormat: String?, vararg args: Any?) {
        if (openVPNConfigDialog == null) {
            return
        }

        val statusView = openVPNConfigDialog!!.findViewById<TextView>(R.id.openvpn_config_message)
        val message = String.format(msgFormat!!, *args)
        if (statusView!!.text.toString().isEmpty()) {
            statusView.append(message)
        } else {
            statusView.append("\n$message")
        }
    }

    private fun appendStatusMessage(resId: Int, vararg args: Any?) {
        appendStatusMessage(context.getString(resId), *args)
    }

    private fun validateOpenVPNConfigFields(): OpenVPNManager.OpenVPNInfo? {
        var isConfigValid = true
        val openVPNAddress = (openVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_address) as EditText?)!!.text.toString()
        if (openVPNAddress.isEmpty()) {
            isConfigValid = false
            appendStatusMessage(R.string.openvpn_address_invalid)
        }
        if (!isConfigValid) {
            return null
        }
        return OpenVPNManager.OpenVPNInfo(openVPNAddress)
    }

    private fun testOpenVPNConnection() {
        val openVPNInfo = validateOpenVPNConfigFields()
        if (openVPNInfo == null) {
            appendStatusMessage(R.string.openvpn_test_aborted)
            return
        }
        openVPNManager.saveOpenVPNInfoToPrefs(openVPNInfo, true)
        checkPermissionsAndConfigureOpenVPN()
    }

    @RequiresApi(19)
    private fun showProxyConfigDialog() {
        val builder = AlertDialog.Builder(context, R.style.AppDialog)
        val inflater = LayoutInflater.from(context)
        val contentView = inflater.inflate(R.layout.openvpn_dialog, null)

        (contentView!!.findViewById<View>(R.id.openvpn_config_address) as EditText?)!!.setText(openVPNManager.openVPNConfigUri)

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        openVPNConfigDialog = builder
            .setTitle(R.string.openvpn_settings_title)
            .setView(contentView)
            .setNeutralButton(R.string.proxy_test_btn) { dialog: DialogInterface?, which: Int -> }
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, which: Int -> }
            .create()
        openVPNConfigDialog!!.show()
        openVPNConfigDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { view: View? ->
            (openVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_message) as TextView?)!!.text = ""
            val openVPNInfo = validateOpenVPNConfigFields()
            if (openVPNInfo == null) {
                appendStatusMessage(R.string.openvpn_application_aborted)
            } else {
                Log.d(TAG, "Saving OpenVPN info: $openVPNInfo")
                openVPNManager.saveOpenVPNInfoToPrefs(openVPNInfo, true)
                checkPermissionsAndConfigureOpenVPN()
                openVPNConfigDialog!!.dismiss()
            }
        }
        openVPNConfigDialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { view: View? ->
            (openVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_message) as TextView?)!!.text = ""
            testOpenVPNConnection()
        }
        openVPNConfigDialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { view: View? ->
            // TODO: cancel OpenVPN application
            openVPNConfigDialog!!.dismiss()
        }
        openVPNConfigDialog!!.setOnDismissListener { dialog: DialogInterface? ->
            val openVPNInfo = validateOpenVPNConfigFields()
            if (openVPNInfo != null) {
                Log.d(TAG, "Saving OpenVPN info: $openVPNInfo")
                openVPNManager.saveOpenVPNInfoToPrefs(openVPNInfo, true)
                checkPermissionsAndConfigureOpenVPN()
            }
        }
    }

    private fun checkPermissionsAndConfigureOpenVPN() {
        if (FileHelpers.isExternalStorageReadable()) {
            if (PermissionHelpers.hasStoragePermissions(context)) {
                openVPNManager.configureOpenVPN()
            } else {
                pendingHandler = { openVPNManager.configureOpenVPN() }
                PermissionHelpers.verifyStoragePermissions(context)
            }
        }
    }
}