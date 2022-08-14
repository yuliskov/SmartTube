package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.R;

public class AppPrefs extends SharedPreferencesBase {
    private static final String TAG = AppPrefs.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String COMPLETED_ONBOARDING = "completed_onboarding";
    private static final String BACKUP_DATA = "backup_data";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String WEB_PROXY_URI = "web_proxy_uri";
    private static final String WEB_PROXY_ENABLED = "web_proxy_enabled";
    private static final String OPENVPN_CONFIG_URI = "openvpn_config_uri";
    private static final String OPENVPN_ENABLED = "openvpn_enabled";
    private static final String ANTIZAPRET_PROFILE = "https://antizapret.prostovpn.org/antizapret-tcp.ovpn";
    private static final String ZABORONA_PROFILE = "https://zaborona.help/openvpn-client-config/zaborona-help_maxroutes.ovpn";
    private String mBootResolution;

    private AppPrefs(Context context) {
        super(context, R.xml.app_prefs);
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context.getApplicationContext());
        }

        return sInstance;
    }

    public void setCompletedOnboarding(boolean completed) {
        putBoolean(COMPLETED_ONBOARDING, completed);
    }

    public boolean getCompletedOnboarding() {
        return getBoolean(COMPLETED_ONBOARDING, false);
    }

    public void setBootResolution(String resolution) {
        mBootResolution = resolution;
    }

    public String getBootResolution() {
        return mBootResolution;
    }

    public void setBackupData(String backupData) {
        putString(BACKUP_DATA, backupData);
    }

    public String getBackupData() {
        return getString(BACKUP_DATA, null);
    }

    public String getStateUpdaterData() {
        return getString(STATE_UPDATER_DATA, null);
    }

    public void setStateUpdaterData(String data) {
        putString(STATE_UPDATER_DATA, data);
    }

    public void setData(String key, String data) {
        putString(key, data);
    }

    public String getData(String key) {
        return getString(key, null);
    }

    public String getWebProxyUri() {
        return getString(WEB_PROXY_URI, "");
    }

    public void setWebProxyUri(String uri) {
        putString(WEB_PROXY_URI, uri);
    }

    public boolean isWebProxyEnabled() {
        return getBoolean(WEB_PROXY_ENABLED, false);
    }

    public void setWebProxyEnabled(boolean enabled) {
        putBoolean(WEB_PROXY_ENABLED, enabled);
    }

    public String getOpenVPNConfigUri() {
        return getString(OPENVPN_CONFIG_URI, ANTIZAPRET_PROFILE);
    }

    public void setOpenVPNConfigUri(String uri) {
        putString(OPENVPN_CONFIG_URI, uri);
    }

    public boolean isOpenVPNEnabled() {
        return getBoolean(OPENVPN_ENABLED, false);
    }

    public void setOpenVPNEnabled(boolean enabled) {
        putBoolean(OPENVPN_ENABLED, enabled);
    }
}
