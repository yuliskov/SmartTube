package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ProxyInfo;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;
import androidx.annotation.RequiresApi;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.BuildConfig;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Manages web proxy settings, to enable web view and http client using web proxy.
 * This implementation is based on the discussion here:
 * https://stackoverflow.com/questions/4488338/webview-android-proxy
 *
 * Note that the implementation uses non-public Android API that subject to
 * uninformed change in the future.
 *
 * DONE: Support SOCKS proxy
 * TODO: Support API level 14 ~ 18
 * TODO: Support exclusion list (?)
 * TODO: Support PAC (Proxy Auto-Configuration)
 */
public class ProxyManager {
    public static final String TAG = ProxyManager.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static ProxyManager sInstance;
    private final Context mContext;
    private final AppPrefs mPrefs;
    private Proxy mProxy;
    private boolean mEnabled;

    private ProxyManager(Context context) {
        mContext = context;
        mPrefs = AppPrefs.instance(mContext);
        //loadProxyInfoFromPrefs();

        File configFilePath = getConfigFilePath();
        if (!FileHelpers.isFileExists(configFilePath)) {
            FileHelpers.stringToFile("socks://myproxyaddress.com:80", configFilePath);
        }
    }

    public static ProxyManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new ProxyManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public static Proxy fromProxyParams(Proxy.Type proxyType, String proxyHost, int proxyPort) {
        return new Proxy(proxyType, InetSocketAddress.createUnresolved(proxyHost, proxyPort));
    }

    //protected Proxy validateProxyConfigFields() {
    //    boolean isConfigValid = true;
    //    int proxyTypeId = ((RadioGroup) mProxyConfigDialog.findViewById(R.id.proxy_type)).getCheckedRadioButtonId();
    //    if (proxyTypeId == -1) {
    //        isConfigValid = false;
    //        appendStatusMessage(R.string.proxy_type_invalid);
    //        mProxyConfigDialog.findViewById(R.id.proxy_type_http).requestFocus();
    //    }
    //    String proxyHost = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_host)).getText().toString();
    //    if (proxyHost.isEmpty()) {
    //        isConfigValid = false;
    //        appendStatusMessage(R.string.proxy_host_invalid);
    //    }
    //    String proxyPortString = ((EditText) mProxyConfigDialog.findViewById(R.id.proxy_port)).getText().toString();
    //    int proxyPort = proxyPortString.isEmpty() ? 0 : Integer.parseInt(proxyPortString);
    //    if (proxyPort <= 0) {
    //        isConfigValid = false;
    //        appendStatusMessage(R.string.proxy_port_invalid);
    //    }
    //    if (!isConfigValid) {
    //        return null;
    //    }
    //    Proxy.Type proxyType = proxyTypeId == R.id.proxy_type_http ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
    //    return new Proxy(proxyType, InetSocketAddress.createUnresolved(proxyHost, proxyPort));
    //}


    /**
     * Get the string representation of current proxy settings.
     * @return String representation of current proxy settings.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public String getWebProxyUri() {
        if (mProxy == null || mProxy.type() == Proxy.Type.DIRECT) {
            return "";
        }
        else {
            InetSocketAddress proxyAddr = (InetSocketAddress) mProxy.address();
            return mProxy.type().name().toLowerCase() + "://" + proxyAddr.getHostString()
                    + ":" + proxyAddr.getPort();
        }
    }

    public void enableProxy(boolean enabled) {
        if (mEnabled == enabled) {
            return;
        }

        mEnabled = enabled;

        loadProxyInfoFromStorage();
        configureProxy();
    }

    /**
     * Check if proxy is enabled in preference settings.
     * This doesn't reflect the status whether the proxy is in use, use
     * {@link #isProxyConfigured()} to check if the proxy is effectively configured.
     * @return True if proxy setting is enabled in preference. Otherwise false.
     */
    public boolean isProxyEnabled() {
        return mEnabled;
    }

    /**
     * Check if proxy setting is configured and is being used by app.
     * @return Whether the proxy is being used, i.e. system properties are configured according to proxy settings.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public boolean isProxyConfigured() {
        if (mProxy == null || mProxy.type() == Proxy.Type.DIRECT) {
            return System.getProperty("http.proxyHost") == null
                    && System.getProperty("https.proxyHost") == null
                    && System.getProperty("socksProxyHost") == null;
        }
        InetSocketAddress proxyAddr = (InetSocketAddress) mProxy.address();
        String proxyHost = proxyAddr.getHostString();
        String proxyPort = Integer.toString(proxyAddr.getPort());
        switch (mProxy.type()) {
            case HTTP:
                return proxyHost.equals(System.getProperty("http.proxyHost"))
                        && proxyPort.equals(System.getProperty("http.proxyPort"))
                        && proxyHost.equals(System.getProperty("https.proxyHost"))
                        && proxyPort.equals(System.getProperty("https.proxyPort"));
            case SOCKS:
                return proxyHost.equals(System.getProperty("socksProxyHost"))
                        && proxyPort.equals(System.getProperty("socksProxyPort"));
        }
        return false;
    }

    public Proxy getCurrentProxy() {
        return mProxy;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public String getProxyHost() {
        return mProxy == null ? "" : ((InetSocketAddress)mProxy.address()).getHostString();
    }

    public int getProxyPort() {
        return mProxy == null ? 0 : ((InetSocketAddress)mProxy.address()).getPort();
    }

    public Proxy.Type getProxyType() {
        return mProxy == null ? Proxy.Type.DIRECT : mProxy.type();
    }

    protected void loadProxyInfoFromPrefs() {
        try {
            String proxyUriString = getWebProxyUriFromPrefs();
            Log.d(TAG, "Web Proxy URI from preferences: \""
                    + proxyUriString + "\"; " + mEnabled);
            if (proxyUriString.isEmpty() || proxyUriString.equalsIgnoreCase(Proxy.Type.DIRECT.name())) {
                mProxy = Proxy.NO_PROXY;
            }
            else {
                URI proxyURI = new URI(proxyUriString);
                mProxy = new Proxy(Proxy.Type.valueOf(proxyURI.getScheme().toUpperCase()),
                        InetSocketAddress.createUnresolved(proxyURI.getHost(), proxyURI.getPort()));
            }
        } catch (URISyntaxException e) {
            Log.e(TAG, e);
            mProxy = Proxy.NO_PROXY;
            mEnabled = false; // Disable. Invalid proxy settings.
        }
    }

    protected void loadProxyInfoFromStorage() {
        if (!mEnabled) {
            mProxy = Proxy.NO_PROXY;
            return;
        }

        try {
            String proxyUriString = FileHelpers.getFileContents(getConfigFilePath());
            Log.d(TAG, "Reading Web Proxy URI from external storage: \""
                    + proxyUriString + "\"; " + mEnabled);
            if (proxyUriString.isEmpty() || proxyUriString.equalsIgnoreCase(Proxy.Type.DIRECT.name())) {
                mProxy = Proxy.NO_PROXY;
            }
            else {
                URI proxyURI = new URI(proxyUriString);
                mProxy = new Proxy(Proxy.Type.valueOf(proxyURI.getScheme().toUpperCase()),
                        InetSocketAddress.createUnresolved(proxyURI.getHost(), proxyURI.getPort()));
            }
        } catch (URISyntaxException e) {
            Log.e(TAG, e);
            mProxy = Proxy.NO_PROXY;
            mEnabled = false; // Disable. Invalid proxy settings.
            MessageHelpers.showLongMessage(mContext, "Invalid proxy address.");
        }
    }

    /**
     * Save proxy settings to preferences.
     * This method only save the settings, it doesn't actually configure the system to use the proxy.
     * Use {@link #configureProxy()} to configure system proxy settings.
     *
     * @param proxy Specify new proxy settings, if null, current proxy setting will be saved.
     * @param enable Set proxy enabled/disabled.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void saveProxyInfoToPrefs(Proxy proxy, boolean enable) {
        if (proxy != null)
            mProxy = new Proxy(proxy.type(), proxy.address());
        mEnabled = enable;
        String proxyUriString = getWebProxyUri();
        mPrefs.setWebProxyUri(proxyUriString);
        mPrefs.setWebProxyEnabled(mEnabled);
        String toastMessage = enable ? mContext.getString(R.string.proxy_enabled, proxyUriString)
                : mContext.getString(R.string.proxy_disabled);
        MessageHelpers.showLongMessage(mContext, toastMessage);
        Log.d(TAG, "Saved Web Proxy URI to preferences: "
                + proxyUriString + "; Enabled: " + mEnabled);
    }

    /**
     * Create proxy info object required by {@link android.net.Proxy#PROXY_CHANGE_ACTION} intent.
     * Before API 21, it is an android.net.ProxyProperties object.
     * Since API 21, it is an {@link ProxyInfo} object.
     *
     * Note: this may NOT work in future if Android's internal implementation changes.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    protected Object createProxyChangeInfo(InetSocketAddress proxyAddr) throws
            ClassNotFoundException,
            NoSuchMethodException,
            IllegalAccessException,
            InvocationTargetException,
            InstantiationException
    {
        if (Build.VERSION.SDK_INT < 21) {
            // https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-4.4.4_r2.0.1/core/java/android/net/ProxyProperties.java#65
            @SuppressLint("PrivateApi") Class<?> proxyPropClazz = Class.forName("android.net.ProxyProperties");
            Constructor proxyPropCtor = proxyPropClazz.getDeclaredConstructor(String.class, String.class, String[].class);
            return proxyPropCtor.newInstance(proxyAddr.getHostString(), Integer.toString(proxyAddr.getPort()), null);
        }
        else {
            return ProxyInfo.buildDirectProxy(proxyAddr.getHostString(), proxyAddr.getPort());
        }
    }

    /**
     * Configure web proxy for the app.
     *
     * The implementation is based on:
     * <a href=https://stackoverflow.com/questions/4488338/webview-android-proxy>this StackOverflow discussion</a>.
     *
     * Also refer to Chromium source code of
     * <a href=https://chromium.googlesource.com/chromium/src/net/+/master/android/java/src/org/chromium/net/ProxyChangeListener.java>ProxyChangeListener.java</a>
     *
     * @return true if proxy is successfully enabled/disabled, otherwise false.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private boolean setWebProxyAPI19Plus() throws
            NoSuchFieldException,
            IllegalAccessException,
            NoSuchMethodException,
            InstantiationException,
            InvocationTargetException,
            ClassNotFoundException
    {
        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT < 19) {
            throw new AssertionError("API level must >= 19");
        }
        Proxy proxy = mEnabled && mProxy != null ? mProxy : Proxy.NO_PROXY;
        Context appContext = mContext.getApplicationContext();
        InetSocketAddress proxyAddr = (InetSocketAddress) proxy.address();
        switch (mProxy.type()) {
            case HTTP:
                System.setProperty("http.proxyHost", proxyAddr.getHostString());
                System.setProperty("http.proxyPort", proxyAddr.getPort() + "");
                System.setProperty("https.proxyHost", proxyAddr.getHostString());
                System.setProperty("https.proxyPort", proxyAddr.getPort() + "");
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");
                break;
            case SOCKS:
                System.setProperty("socksProxyHost", proxyAddr.getHostString());
                System.setProperty("socksProxyPort", proxyAddr.getPort() + "");
                System.clearProperty("http.proxyHost");
                System.clearProperty("http.proxyPort");
                System.clearProperty("https.proxyHost");
                System.clearProperty("https.proxyPort");
                break;
            case DIRECT:
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");
                System.clearProperty("http.proxyHost");
                System.clearProperty("http.proxyPort");
                System.clearProperty("https.proxyHost");
                System.clearProperty("https.proxyPort");
                break;
        }
        Field loadedApkField = appContext.getClass().getField("mLoadedApk");
        loadedApkField.setAccessible(true);
        Object loadedApk = loadedApkField.get(appContext);
        @SuppressLint("PrivateApi") Class<?> loadedApkCls = Class.forName("android.app.LoadedApk");
        Field receiversField = loadedApkCls.getDeclaredField("mReceivers");
        receiversField.setAccessible(true);
        ArrayMap receivers = (ArrayMap) receiversField.get(loadedApk);
        for (Object receiverMap : receivers.values()) {
            for (Object rec : ((ArrayMap) receiverMap).keySet()) {
                Class<? extends Object> clazz = rec.getClass();
                if (clazz.getName().contains("ProxyChangeListener")) {
                    Method onReceiveMethod = clazz.getDeclaredMethod("onReceive", Context.class, Intent.class);
                    Intent intent = new Intent(android.net.Proxy.PROXY_CHANGE_ACTION);
                    Object proxyInfo = createProxyChangeInfo(proxyAddr);
                    intent.putExtra("android.intent.extra.PROXY_INFO", (Parcelable) proxyInfo);
                    onReceiveMethod.invoke(rec, appContext, intent);
                }
            }
        }
        Log.d(TAG, "Web Proxy set to: " + getWebProxyUri());
        return true;
    }

    private boolean configureProxy() {
        if (mProxy == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT < 19) {
            Log.e(TAG, "Web Proxy support not implemented for API level < 19");
            return false;
        }

        if (mEnabled) {
            MessageHelpers.showMessage(mContext, "Starting proxy %s", getWebProxyUri());
        } else {
            MessageHelpers.showMessage(mContext, "Stopping proxy %s", getWebProxyUri());
        }

        try {
            // Required API level >= 19
            return setWebProxyAPI19Plus();
        } catch (Exception e) {
            Log.e(TAG, e);
            mEnabled = false;
            MessageHelpers.showLongMessage(mContext, e.getLocalizedMessage());
            return false;
        }
    }

    public String getConfigPath() {
        return getConfigFilePath().getAbsolutePath();
    }

    private File getConfigFilePath() {
        return new File(FileHelpers.getExternalFilesDir(mContext), "proxy.txt");
    }

    private String getWebProxyUriFromPrefs() {
        return mPrefs.getWebProxyUri();
    }
}
