package com.liskovsoft.smartyoutubetv2.common.proxy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ProxyInfo;
import android.os.Build;
import android.os.Parcelable;
import android.util.ArrayMap;
import androidx.annotation.RequiresApi;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.AppPrefs;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URISyntaxException;

import com.liskovsoft.smartyoutubetv2.common.BuildConfig;

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
    private final Context mContext;
    private final AppPrefs mPrefs;
    private Proxy mProxy;
    private boolean mEnabled;

    public ProxyManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = AppPrefs.instance(mContext);
        loadProxyInfoFromPrefs();
    }


    /**
     * Get the string representation of current proxy settings.
     * @return String representation of current proxy settings.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public String getProxyUriString() {
        if (mProxy == null || mProxy.type() == Proxy.Type.DIRECT) {
            return "";
        } else {
            PasswdInetSocketAddress proxyAddr = (PasswdInetSocketAddress) mProxy.address();
            String usernameAndPasswd = "";
            if (proxyAddr.getUsername() != null && proxyAddr.getPassword() != null) {
                usernameAndPasswd = proxyAddr.getUsername() + ":" + proxyAddr.getPassword() + "@";
            }
            return mProxy.type().name().toLowerCase() + "://" + usernameAndPasswd + proxyAddr.getHostString()
                    + ":" + proxyAddr.getPort();
        }
    }

    /**
     * Check if proxy is enabled in preference settings.
     * This doesn't reflect the status whether the proxy is in use, use
     * {@link #isProxyConfigured()} to check if the proxy is effectively configured.
     * @return True if proxy setting is enabled in preference. Otherwise false.
     */
    public boolean isProxyEnabled() {
        return isProxySupported() && mEnabled;
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
        PasswdInetSocketAddress proxyAddr = (PasswdInetSocketAddress) mProxy.address();
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
        return mProxy == null ? "" : ((PasswdInetSocketAddress) mProxy.address()).getHostString();
    }

    public int getProxyPort() {
        return mProxy == null ? 0 : ((PasswdInetSocketAddress) mProxy.address()).getPort();
    }

    public Proxy.Type getProxyType() {
        return mProxy == null ? Proxy.Type.DIRECT : mProxy.type();
    }

    public String getProxyUsername() {
        return mProxy == null ? "" : ((PasswdInetSocketAddress) mProxy.address()).getUsername();
    }

    public String getProxyPassword() {
        return mProxy == null ? "" : ((PasswdInetSocketAddress) mProxy.address()).getPassword();
    }

    protected void loadProxyInfoFromPrefs() {
        try {
            String proxyUriString = mPrefs.getWebProxyUri();

            Log.d(TAG, "Web Proxy URI from preferences: \""
                    + proxyUriString + "\"; " + mEnabled);
            if (proxyUriString.isEmpty() || proxyUriString.equalsIgnoreCase(Proxy.Type.DIRECT.name())) {
                mProxy = Proxy.NO_PROXY;
            }
            else {
                PasswdURI proxyURI = new PasswdURI(proxyUriString);
                mProxy = new Proxy(Proxy.Type.valueOf(proxyURI.getScheme().toUpperCase()),
                        PasswdInetSocketAddress.createUnresolved(proxyURI.getHost(), proxyURI.getPort(), proxyURI.getUsername(), proxyURI.getPassword()));
            }

            mEnabled = mPrefs.isWebProxyEnabled();
        } catch (URISyntaxException | IllegalArgumentException e) {
            Log.e(TAG, e);
            mProxy = Proxy.NO_PROXY;
            mEnabled = false; // invalid settings found. disable proxy.
        }
    }

    /**
     * Save proxy settings to preferences.
     * This method only save the settings, it doesn't actually configure the system to use the proxy.
     * Use {@link #configureSystemProxy()} to configure system proxy settings.
     *
     * @param proxy Specify new proxy settings, if null, current proxy setting will be saved.
     * @param enable Set proxy enabled/disabled.
     */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void saveProxyInfoToPrefs(Proxy proxy, boolean enable) {
        if (proxy != null)
            mProxy = new Proxy(proxy.type(), proxy.address());
        mEnabled = enable;
        String proxyUriString = getProxyUriString();
        
        mPrefs.setWebProxyUri(proxyUriString);
        mPrefs.setWebProxyEnabled(mEnabled);
        
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
    protected Object createProxyChangeInfo(PasswdInetSocketAddress proxyAddr) throws
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
        Proxy proxy = isProxyEnabled() && mProxy != null ? mProxy : Proxy.NO_PROXY;
        Context appContext = mContext.getApplicationContext();
        PasswdInetSocketAddress proxyAddr = (PasswdInetSocketAddress) proxy.address();
        String username = proxyAddr != null ? proxyAddr.getUsername() : null;
        String password = proxyAddr != null ? proxyAddr.getPassword() : null;

        if (username != null && password != null) {
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password.toCharArray());
                }
            });
        }

        switch (proxy.type()) {
            case HTTP:
                System.setProperty("http.proxyHost", proxyAddr.getHostString());
                System.setProperty("http.proxyPort", proxyAddr.getPort() + "");
                System.setProperty("https.proxyHost", proxyAddr.getHostString());
                System.setProperty("https.proxyPort", proxyAddr.getPort() + "");
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");

                if (username != null && password != null) {
                    System.setProperty("http.proxyUser", username);
                    System.setProperty("http.proxyPassword", password);
                    System.setProperty("https.proxyUser", username);
                    System.setProperty("https.proxyPassword", password);
                } else {
                    System.clearProperty("http.proxyUser");
                    System.clearProperty("http.proxyPassword");
                    System.clearProperty("https.proxyUser");
                    System.clearProperty("https.proxyPassword");
                }
                System.clearProperty("socksProxyUser");
                System.clearProperty("socksProxyPassword");
                break;
            case SOCKS:
                System.setProperty("socksProxyHost", proxyAddr.getHostString());
                System.setProperty("socksProxyPort", proxyAddr.getPort() + "");
                System.clearProperty("http.proxyHost");
                System.clearProperty("http.proxyPort");
                System.clearProperty("https.proxyHost");
                System.clearProperty("https.proxyPort");

                if (username != null && password != null) {
                    System.setProperty("socksProxyUser", username);
                    System.setProperty("socksProxyPassword", password);
                } else {
                    System.clearProperty("socksProxyUser");
                    System.clearProperty("socksProxyPassword");
                }
                System.clearProperty("http.proxyUser");
                System.clearProperty("http.proxyPassword");
                System.clearProperty("https.proxyUser");
                System.clearProperty("https.proxyPassword");
                break;
            case DIRECT:
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");
                System.clearProperty("http.proxyHost");
                System.clearProperty("http.proxyPort");
                System.clearProperty("https.proxyHost");
                System.clearProperty("https.proxyPort");

                System.clearProperty("socksProxyUser");
                System.clearProperty("socksProxyPassword");
                System.clearProperty("http.proxyUser");
                System.clearProperty("http.proxyPassword");
                System.clearProperty("https.proxyUser");
                System.clearProperty("https.proxyPassword");
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
        Log.d(TAG, "Web Proxy set to: " + getProxyUriString());
        return true;
    }

    public boolean configureSystemProxy() {
        try {
            if (isProxySupported()) {
                return setWebProxyAPI19Plus();
            }
        } catch (Exception e) {
            Log.e(TAG, e);
        }

        return false;
    }

    public boolean isProxySupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }
}
