package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import com.liskovsoft.mediaserviceinterfaces.yt.data.Account;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppPrefs extends SharedPreferencesBase implements AccountChangeListener {
    private static final String TAG = AppPrefs.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String ANONYMOUS_PROFILE_NAME = "anonymous";
    private static final String MULTI_PROFILES = "multi_profiles";
    private static final String STATE_UPDATER_DATA = "state_updater_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String WEB_PROXY_URI = "web_proxy_uri";
    private static final String WEB_PROXY_ENABLED = "web_proxy_enabled";
    private static final String LAST_PROFILE_NAME = "last_profile_name";
    private String mBootResolution;
    private final Map<String, Integer> mDataHashes = new HashMap<>();
    private final List<ProfileChangeListener> mListeners = new CopyOnWriteArrayList<>();

    public interface ProfileChangeListener {
        void onProfileChanged();
    }

    private AppPrefs(Context context) {
        super(context, R.xml.app_prefs);

        initProfiles();
    }

    private void initProfiles() {
        MediaServiceManager.instance().addAccountListener(this);
    }

    @Override
    public void onAccountChanged(Account account) {
        if (isMultiProfilesEnabled()) {
            selectAccount(account);
        }
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context.getApplicationContext());
        }

        return sInstance;
    }

    public void enableMultiProfiles(boolean enabled) {
        putBoolean(MULTI_PROFILES, enabled);
        selectAccount(enabled ? MediaServiceManager.instance().getSelectedAccount() : null);
    }

    public boolean isMultiProfilesEnabled() {
        return getBoolean(MULTI_PROFILES, false);
    }

    public void setBootResolution(String resolution) {
        mBootResolution = resolution;
    }

    public String getBootResolution() {
        return mBootResolution;
    }

    //public String getStateUpdaterData() {
    //    return getString(STATE_UPDATER_DATA, null);
    //}
    //
    //public void setStateUpdaterData(String data) {
    //    putString(STATE_UPDATER_DATA, data);
    //}

    public String getStateUpdaterData() {
        return getProfileData(STATE_UPDATER_DATA);
    }

    public void setStateUpdaterData(String data) {
        setProfileData(STATE_UPDATER_DATA, data);
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

    private void setProfileName(String profileName) {
        putString(LAST_PROFILE_NAME, profileName);
    }

    private String getProfileName() {
        return getString(LAST_PROFILE_NAME, null);
    }

    private void selectAccount(Account account) {
        selectProfile(account != null && account.getName() != null ? account.getName().replace(" ", "_") : null);
    }

    private void selectProfile(String profileName) {
        if (isMultiProfilesEnabled() && profileName == null) {
            profileName = ANONYMOUS_PROFILE_NAME;
        }

        setProfileName(profileName);

        onProfileChanged();
    }

    private void onProfileChanged() {
        for (ProfileChangeListener listener : mListeners) {
            listener.onProfileChanged();
        }
    }

    public void addListener(ProfileChangeListener listener) {
        if (!mListeners.contains(listener)) {
            if (listener instanceof GeneralData) {
                mListeners.add(0, listener); // data classes should be called before regular listeners
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
        String profileName = getProfileName();
        if (!TextUtils.isEmpty(profileName)) {
            key = profileName + "_" + key;
        }

        return key;
    }
}
