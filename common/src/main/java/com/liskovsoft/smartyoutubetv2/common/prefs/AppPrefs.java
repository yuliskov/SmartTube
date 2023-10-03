package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppPrefs extends SharedPreferencesBase {
    private static final String TAG = AppPrefs.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String COMPLETED_ONBOARDING = "completed_onboarding";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String WEB_PROXY_URI = "web_proxy_uri";
    private static final String WEB_PROXY_ENABLED = "web_proxy_enabled";
    private String mBootResolution;
    private final Map<String, Integer> mDataHashes = new HashMap<>();
    private final List<ProfileChangeListener> mListeners = new ArrayList<>();
    private String mProfileName;

    public interface ProfileChangeListener {
        void onProfileChanged();
    }

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

    public String getStateUpdaterData() {
        return getString(STATE_UPDATER_DATA, null);
    }

    public void setStateUpdaterData(String data) {
        putString(STATE_UPDATER_DATA, data);
    }

    public void setProfileData(String key, String data) {
        setData(getProfileKey(key), data);
    }

    public String getProfileData(String key) {
        String data = getData(getProfileKey(key));

        // Fallback to non-profile settings
        return data != null ? data : getData(key);
    }

    public void setData(String key, String data) {
        if (checkData(key, data)) {
            putString(key, data);
        }
    }

    public String getData(String key) {
        // Don't sync hash here. Hashes won't match.
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

    public void changeProfile(String profileName) {
        mProfileName = profileName;

        for (ProfileChangeListener listener : mListeners) {
            listener.onProfileChanged();
        }
    }

    public void addListener(ProfileChangeListener listener) {
        if (!mListeners.contains(listener)) {
            if (listener instanceof GeneralData) {
                mListeners.add(0, listener);
            } else {
                mListeners.add(listener);
            }
        }
    }

    public void removeListener(ProfileChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Check that the data has been modified.
     */
    private boolean checkData(String key, String data) {
        Integer oldHashCode = mDataHashes.get(key);
        int newHashCode = data != null ? data.hashCode() : -1;

        if (oldHashCode != null && oldHashCode == newHashCode) {
            return false;
        }

        mDataHashes.put(key, newHashCode);

        return true;
    }

    private String getProfileKey(String key) {
        if (!TextUtils.isEmpty(mProfileName)) {
            key = mProfileName + "_" + key;
        }

        return key;
    }
}
