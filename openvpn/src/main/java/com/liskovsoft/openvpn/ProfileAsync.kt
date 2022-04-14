package com.liskovsoft.openvpn

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConfigParser.ConfigParseError
import de.blinkt.openvpn.core.ProfileManager
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class ProfileAsync(
    context: Context,
    private val onProfileLoadListener: OnProfileLoadListener?,
    private val ovpnUrl: String?
    ) : CoroutinesAsyncTask<Void?, Void?, Boolean>("ProfileAsync") {
    private val context: WeakReference<Context> = WeakReference(context)
    override fun onPreExecute() {
        super.onPreExecute()
        val context = context.get()
        if (context == null || onProfileLoadListener == null) {
            cancel(true)
        } else if (!isNetworkAvailable(context)) {
            cancel(true)
            onProfileLoadListener.onProfileLoadFailed("No Network")
        }
    }

    override fun doInBackground(vararg params: Void?): Boolean {
        try {
            val url = URL(ovpnUrl)
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.connectTimeout = 10 * 1000
            httpURLConnection.readTimeout = 10 * 1000
            val inputStream = httpURLConnection.inputStream
            val bufferedReader =
                BufferedReader(InputStreamReader(inputStream /*, Charset.forName("UTF-8")*/))
            val cp = ConfigParser()
            cp.parseConfig(bufferedReader)
            val vp = cp.convertProfile()
            val vpl = ProfileManager.getInstance(context.get())
            vp.mName = Build.MODEL
            vp.mUsername = null
            vp.mPassword = null
            vpl.addProfile(vp)
            vpl.saveProfile(context.get(), vp)
            vpl.saveProfileList(context.get())
            return true
        } catch (e: MalformedURLException) {
            cancel(true)
            onProfileLoadListener?.onProfileLoadFailed("MalformedURLException")
        } catch (configParseError: ConfigParseError) {
            cancel(true)
            onProfileLoadListener?.onProfileLoadFailed("ConfigParseError")
        } catch (e: IOException) {
            cancel(true)
            onProfileLoadListener?.onProfileLoadFailed("IOException")
        }
        return false
    }

    override fun onPostExecute(result: Boolean?) {
        super.onPostExecute(result)
        if (result == true) {
            onProfileLoadListener?.onProfileLoadSuccess()
        } else {
            onProfileLoadListener?.onProfileLoadFailed("unknown error")
        }
    }

    interface OnProfileLoadListener {
        fun onProfileLoadSuccess()
        fun onProfileLoadFailed(msg: String?)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                ?: return false
        val info = connectivityManager.activeNetworkInfo
        return info != null && info.isAvailable && info.isConnected
    }

}