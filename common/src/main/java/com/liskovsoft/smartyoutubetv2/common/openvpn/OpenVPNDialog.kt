package com.liskovsoft.smartyoutubetv2.common.openvpn

import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.smartyoutubetv2.common.R

class OpenVPNDialog(private val mContext: Context) {
    private var mOpenVPNConfigDialog: AlertDialog? = null
    private val mOpenVPNManager: OpenVPNManager = OpenVPNManager(mContext)
    private val mProxyTestHandler: Handler = Handler(Looper.myLooper()!!)
    val isSupported: Boolean
        get() = mOpenVPNManager.isOpenVPNSupported
    val isEnabled: Boolean
        get() = mOpenVPNManager.isOpenVPNEnabled

    private companion object {
        val TAG: String = OpenVPNDialog::class.java.simpleName
    }

    @RequiresApi(19)
    fun enable(enabled: Boolean) {
        if (isSupported) {
            mOpenVPNManager.saveOpenVPNInfoToPrefs(enabled = enabled)
            if (enabled) {
                showProxyConfigDialog()
            } else {
                mOpenVPNManager.configureOpenVPN()
            }
        }
    }

    private fun appendStatusMessage(msgFormat: String?, vararg args: Any?) {
        val statusView = mOpenVPNConfigDialog!!.findViewById<TextView>(R.id.openvpn_config_message)
        val message = String.format(msgFormat!!, *args)
        if (statusView!!.text.toString().isEmpty())
            statusView.append(message)
        else
            statusView.append("\n$message")
    }

    private fun appendStatusMessage(resId: Int, vararg args: Any?) {
        appendStatusMessage(mContext.getString(resId), *args)
    }

    private fun validateOpenVPNConfigFields(): OpenVPNInfo? {
        var isConfigValid = true
        val openVPNAddress = (mOpenVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_address) as EditText?)!!.text.toString()
        if (openVPNAddress.isEmpty()) {
            isConfigValid = false
            appendStatusMessage(R.string.openvpn_address_invalid)
        }
        if (!isConfigValid) {
            return null
        }
        return OpenVPNInfo(openVPNAddress)
    }

    private fun testOpenVPNConnection() {
        val openVPNInfo = validateOpenVPNConfigFields()
        if (openVPNInfo == null) {
            appendStatusMessage(R.string.openvpn_test_aborted)
            return
        }
        mOpenVPNManager.saveOpenVPNInfoToPrefs(openVPNInfo, true)
        mOpenVPNManager.configureOpenVPN()

        // TODO: output OpenVPN status messages
    }

    @RequiresApi(19)
    private fun showProxyConfigDialog() {
        val builder = AlertDialog.Builder(mContext, R.style.AppDialog)
        val inflater = LayoutInflater.from(mContext)
        val contentView = inflater.inflate(R.layout.openvpn_dialog, null)

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        mOpenVPNConfigDialog = builder
            .setTitle(R.string.openvpn_settings_title)
            .setView(contentView)
            .setNeutralButton(R.string.proxy_test_btn) { dialog: DialogInterface?, which: Int -> }
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, which: Int -> }
            .create()
        mOpenVPNConfigDialog!!.show()
        mOpenVPNConfigDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { view: View? ->
            (mOpenVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_message) as TextView?)!!.text = ""
            val openVPNInfo = validateOpenVPNConfigFields()
            if (openVPNInfo == null) {
                appendStatusMessage(R.string.openvpn_application_aborted)
            } else {
                Log.d(TAG, "Saving OpenVPN info: $openVPNInfo")
                mOpenVPNManager.saveOpenVPNInfoToPrefs(openVPNInfo, true)
                mOpenVPNManager.configureOpenVPN()
                mOpenVPNConfigDialog!!.dismiss()
            }
        }
        mOpenVPNConfigDialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { view: View? ->
            (mOpenVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_message) as TextView?)!!.text = ""
            testOpenVPNConnection()
        }
        mOpenVPNConfigDialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { view: View? ->
            // TODO: cancel OpenVPN application
            mOpenVPNConfigDialog!!.dismiss()
        }
        mOpenVPNConfigDialog!!.setOnDismissListener { dialog: DialogInterface? ->
            val openVPNInfo = validateOpenVPNConfigFields()
            if (openVPNInfo != null) {
                Log.d(TAG, "Saving OpenVPN info: $openVPNInfo")
                mOpenVPNManager.saveOpenVPNInfoToPrefs(openVPNInfo, true)
                mOpenVPNManager.configureOpenVPN()
            }
        }
    }
}