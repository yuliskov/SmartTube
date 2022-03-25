package com.liskovsoft.smartyoutubetv2.common.openvpn

import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.sharedutils.okhttp.OkHttpHelpers
import com.liskovsoft.smartyoutubetv2.common.R
import com.liskovsoft.smartyoutubetv2.common.proxy.PasswdInetSocketAddress
import com.liskovsoft.smartyoutubetv2.common.proxy.Proxy
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.*

class OpenVPNDialog(private val mContext: Context) {
    private var mOpenVPNConfigDialog: AlertDialog? = null
    private val mOpenVPNManager: OpenVPNManager = OpenVPNManager(mContext)
    private val mProxyTestHandler: Handler = Handler(Looper.myLooper()!!)
    private val mUrlTests: ArrayList<Call> = ArrayList()
    private var mNumTests = 0
    val isSupported: Boolean
        get() = mOpenVPNManager.isOpenVPNSupported
    val isEnabled: Boolean
        get() = mOpenVPNManager.isOpenVPNEnabled

    companion object {
        val TAG = OpenVPNDialog::class.java.simpleName
    }

    @RequiresApi(19)
    fun enable(checked: Boolean) {
        if (isSupported) {
            mOpenVPNManager.saveOpenVPNInfoToPrefs(null, checked)
            if (checked) {
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

    private fun validateProxyConfigFields(): Proxy? {
        var isConfigValid = true
        val proxyTypeId = (mOpenVPNConfigDialog!!.findViewById<View>(R.id.proxy_type) as RadioGroup?)!!.checkedRadioButtonId
        if (proxyTypeId == -1) {
            isConfigValid = false
            appendStatusMessage(R.string.proxy_type_invalid)
            mOpenVPNConfigDialog!!.findViewById<View>(R.id.proxy_type_http)!!.requestFocus()
        }
        val proxyHost = (mOpenVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_address) as EditText?)!!.text.toString()
        if (proxyHost.isEmpty()) {
            isConfigValid = false
            appendStatusMessage(R.string.proxy_host_invalid)
        }
        val proxyPortString = (mOpenVPNConfigDialog!!.findViewById<View>(R.id.proxy_port) as EditText?)!!.text.toString()
        val proxyPort = if (proxyPortString.isEmpty()) 0 else proxyPortString.toInt()
        if (proxyPort <= 0) {
            isConfigValid = false
            appendStatusMessage(R.string.proxy_port_invalid)
        }
        val proxyUser = (mOpenVPNConfigDialog!!.findViewById<View>(R.id.proxy_username) as EditText?)!!.text.toString()
        val proxyPassword = (mOpenVPNConfigDialog!!.findViewById<View>(R.id.proxy_password) as EditText?)!!.text.toString()
        if (proxyUser.isEmpty() != proxyPassword.isEmpty()) {
            isConfigValid = false
            appendStatusMessage(R.string.proxy_credentials_invalid)
        }
        if (!isConfigValid) {
            return null
        }
        val proxyType = if (proxyTypeId == R.id.proxy_type_http) Proxy.Type.HTTP else Proxy.Type.SOCKS
        return Proxy(proxyType, PasswdInetSocketAddress.createUnresolved(proxyHost, proxyPort, proxyUser, proxyPassword))
    }

    protected fun testProxyConnections() {
        val proxy = validateProxyConfigFields()
        if (proxy == null) {
            appendStatusMessage(R.string.proxy_test_aborted)
            return
        }
        mOpenVPNManager.saveOpenVPNInfoToPrefs(proxy, true)
        mOpenVPNManager.configureOpenVPN()
        val testUrls = mContext.getString(R.string.proxy_test_urls).split("\n".toRegex()).toTypedArray()
        val okHttpClient = OkHttpHelpers.createOkHttpClient()
        for (urlString in testUrls) {
            val serialNo = ++mNumTests
            val request = Request.Builder().url(urlString).build()
            appendStatusMessage(R.string.proxy_test_start, serialNo, urlString)
            val call = okHttpClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled) mProxyTestHandler.post {
                        appendStatusMessage(
                            R.string.proxy_test_cancelled,
                            serialNo
                        )
                    } else mProxyTestHandler.post { appendStatusMessage(R.string.proxy_test_error, serialNo, e) }
                }

                override fun onResponse(call: Call, response: Response) {
                    val protocol = response.protocol().toString().toUpperCase()
                    val code = response.code()
                    val status = response.message()
                    mProxyTestHandler.post {
                        appendStatusMessage(
                            R.string.proxy_test_status,
                            serialNo, protocol, code, if (status.isEmpty()) "OK" else status
                        )
                    }
                }
            })
            mUrlTests.add(call)
        }
    }

    @RequiresApi(19)
    private fun showProxyConfigDialog() {
        val builder = AlertDialog.Builder(mContext, R.style.AppDialog)
        val inflater = LayoutInflater.from(mContext)
        val contentView = inflater.inflate(R.layout.openvpn_dialog, null)
        //if (mOpenVPNManager.proxyType == Proxy.Type.DIRECT) {
        //    (contentView.findViewById<View>(R.id.openvpn_config_address) as EditText).setText("")
        //} else {
        //    (contentView.findViewById<View>(R.id.openvpn_config_address) as EditText).setText(mOpenVPNManager.proxyHost)
        //}

        // keep empty, will override below.
        // https://stackoverflow.com/a/15619098/5379584
        mOpenVPNConfigDialog = builder
            .setTitle(R.string.proxy_settings_title)
            .setView(contentView)
            .setNeutralButton(R.string.proxy_test_btn) { dialog: DialogInterface?, which: Int -> }
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> }
            .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, which: Int -> }
            .create()
        mNumTests = 0
        mOpenVPNConfigDialog!!.show()
        mOpenVPNConfigDialog!!.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { view: View? ->
            (mOpenVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_message) as TextView?)!!.text = ""
            val proxy = validateProxyConfigFields()
            if (proxy == null) {
                appendStatusMessage(R.string.proxy_application_aborted)
            } else {
                Log.d(TAG, "Saving proxy info: $proxy")
                mOpenVPNManager.saveOpenVPNInfoToPrefs(proxy, true)
                mOpenVPNManager.configureOpenVPN()
                for (call in mUrlTests) call.cancel()
                mOpenVPNConfigDialog!!.dismiss()
            }
        }
        mOpenVPNConfigDialog!!.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { view: View? ->
            for (call in mUrlTests) call.cancel()
            mUrlTests.clear()
            (mOpenVPNConfigDialog!!.findViewById<View>(R.id.openvpn_config_message) as TextView?)!!.text = ""
            testProxyConnections()
        }
        mOpenVPNConfigDialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { view: View? ->
            for (call in mUrlTests) call.cancel()
            mOpenVPNConfigDialog!!.dismiss()
        }
        mOpenVPNConfigDialog!!.setOnDismissListener { dialog: DialogInterface? ->
            val proxy = validateProxyConfigFields()
            if (proxy != null) {
                Log.d(TAG, "Saving proxy info: $proxy")
                mOpenVPNManager.saveOpenVPNInfoToPrefs(proxy, true)
                mOpenVPNManager.configureOpenVPN()
                for (call in mUrlTests) call.cancel()
            }
        }
    }
}