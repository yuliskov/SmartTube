package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.misc.WeakHashSet;
import com.liskovsoft.sharedutils.prefs.SharedPreferencesBase;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager.AccountChangeListener;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public class AppPrefs extends SharedPreferencesBase implements AccountChangeListener {
    private static final String TAG = AppPrefs.class.getSimpleName();
    private static final String PREFS_DIR = "app_prefs";
    @SuppressLint("StaticFieldLeak")
    private static AppPrefs sInstance;
    private static final String ANONYMOUS_PROFILE_NAME = "anonymous";
    private static final String MULTI_PROFILES = "multi_profiles";
    private static final String CHANNEL_GROUP_DATA = "channel_group_data";
    private static final String SIDEBAR_DATA = "sidebar_data";
    private static final String VIEW_MANAGER_DATA = "view_manager_data";
    private static final String WEB_PROXY_URI = "web_proxy_uri";
    private static final String WEB_PROXY_ENABLED = "web_proxy_enabled";
    private static final String LAST_PROFILE_NAME = "last_profile_name";
    private String mBootResolution;
    private String mStateUpdaterKey;
    private String mLegacyStateUpdaterKey;
    private boolean mCanMigrateHistory;
    private final Map<Account, String> mSessionHistoryKeys = new IdentityHashMap<>();
    private final WeakHashSet<ProfileChangeListener> mListeners = new WeakHashSet<>();

    public interface ProfileChangeListener {
        void onProfileChanged();
    }

    private AppPrefs(Context context) {
        //super(context, R.xml.app_prefs); // IndexOutOfBoundsException on Google Streamer
        super(context);

        initProfiles();
    }

    private void initProfiles() {
        MediaServiceManager.instance().addAccountListener(this);
    }

    @Override
    public void onAccountChanged(Account account) {
        selectProfile(account);
        onProfileChanged();
    }

    public static AppPrefs instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppPrefs(context);
        }

        return sInstance;
    }

    public boolean isMultiProfilesEnabled() {
        return getBoolean(MULTI_PROFILES, false);
    }

    public void enableMultiProfiles(boolean enabled) {
        if (isMultiProfilesEnabled() == enabled) {
            return;
        }

        putBoolean(MULTI_PROFILES, enabled);
        onProfileChanged();
        //selectAccount(enabled ? MediaServiceManager.instance().getSelectedAccount() : null);
    }

    public String getBootResolution() {
        return mBootResolution;
    }

    public void setBootResolution(String resolution) {
        mBootResolution = resolution;
    }

    public String getStateUpdaterData() {
        return getStateUpdaterData(getStateUpdaterKey());
    }

    public void setStateUpdaterData(String data) {
        setStateUpdaterData(getStateUpdaterKey(), data);
    }

    public String getStateUpdaterKey() {
        if (mStateUpdaterKey == null) {
            selectHistoryProfile(MediaServiceManager.instance().getSelectedAccount());
        }
        return mStateUpdaterKey;
    }

    public String getStateUpdaterData(String key) {
        if (!HistoryProfileKey.isPersistent(key)) {
            return null;
        }

        String data = getData(key);
        String migratedKey = "history_migrated_" + key;
        if (data != null || getBoolean(migratedKey, false)) {
            return data;
        }

        if (key.equals(mStateUpdaterKey) && mLegacyStateUpdaterKey != null) {
            String ownerKey = HistoryProfileKey.migrationOwnerKey(mLegacyStateUpdaterKey);
            String owner = getString(ownerKey, null);
            if (mCanMigrateHistory && (owner == null || owner.equals(key))) {
                data = getData(mLegacyStateUpdaterKey);
                if (data != null) {
                    setData(key, data);
                    putString(ownerKey, key);
                }
            } else if (!mCanMigrateHistory && owner == null) {
                // Do not attribute mixed legacy history after a conflicting account is removed.
                putString(ownerKey, "ambiguous");
            }
        }

        // Preserve the legacy file, but never restore it again after history has been cleared.
        putBoolean(migratedKey, true);
        return data;
    }

    public void setStateUpdaterData(String key, String data) {
        if (HistoryProfileKey.isPersistent(key)) {
            setData(key, data);
            putBoolean("history_migrated_" + key, true);
        }
    }

    public String getChannelGroupData() {
        // Always use multiple profiles
        return getData(getProfileKey(CHANNEL_GROUP_DATA,  true));
    }

    public void setChannelGroupData(String data) {
        // Always use multiple profiles
        setData(getProfileKey(CHANNEL_GROUP_DATA,  true), data);
    }

    public String getSidebarData() {
        // Always use multiple profiles
        return getData(getProfileKey(SIDEBAR_DATA,  true));
    }

    public void setSidebarData(String data) {
        // Always use multiple profiles
        setData(getProfileKey(SIDEBAR_DATA,  true), data);
    }

    public String getProfileData(String key) {
        //String data = getData(getProfileKey(key, isMultiProfilesEnabled()));

        // Fallback to non-profile settings
        //return data != null ? data : getData(key);

        return getData(getProfileKey(key, isMultiProfilesEnabled()));
    }

    public void setProfileData(String key, String data) {
        setData(getProfileKey(key, isMultiProfilesEnabled()), data);
    }

    //public String getData(String key) {
    //    // Don't sync hash here. Hashes won't match.
    //    return getString(key, null);
    //}
    //
    //public void setData(String key, String data) {
    //    if (checkData(key, data)) {
    //        putString(key, data);
    //    }
    //}

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

    private String getProfileName() {
        return getString(LAST_PROFILE_NAME, null);
    }

    private void setProfileName(String profileName) {
        putString(LAST_PROFILE_NAME, profileName);
    }

    private void selectProfile(Account account) {
        String profileName = account != null && account.getName() != null ? account.getName().replace(" ", "_") : ANONYMOUS_PROFILE_NAME;

        setProfileName(profileName);

        selectHistoryProfile(account);
    }

    private void selectHistoryProfile(Account account) {
        mStateUpdaterKey = HistoryProfileKey.forAccount(account);
        if (mStateUpdaterKey == null) {
            // An incomplete sign-in must not share guest history or a display-name profile.
            if (!mSessionHistoryKeys.containsKey(account)) {
                mSessionHistoryKeys.put(account, "history_session_" + UUID.randomUUID());
            }
            mStateUpdaterKey = mSessionHistoryKeys.get(account);
        }
        mLegacyStateUpdaterKey = HistoryProfileKey.legacyKey(account);
        MediaServiceManager.instance().loadAccounts(accounts ->
                mCanMigrateHistory = HistoryProfileKey.canMigrateLegacy(account, accounts));
    }

    private void onProfileChanged() {
        mListeners.forEach(ProfileChangeListener::onProfileChanged);
    }

    public void addListener(ProfileChangeListener listener) {
        if (!mListeners.contains(listener)) {
            if (listener instanceof GeneralData) {
                mListeners.add(0, listener); // data classes should be called before regular listeners
            } else if (listener instanceof SidebarService) {
                mListeners.add(mListeners.isEmpty() ? 0 : 1, listener); // data classes should be called before regular listeners
            } else {
                mListeners.add(listener);
            }
        }
    }

    public void removeListener(ProfileChangeListener listener) {
        mListeners.remove(listener);
    }

    //private String getProfileKey(String key) {
    //    String profileName = getProfileName();
    //    if (!TextUtils.isEmpty(profileName)) {
    //        key = profileName + "_" + key;
    //    }
    //
    //    return key;
    //}

    private String getProfileKey(String key, boolean isMultiProfilesEnabled) {
        String profileName = getProfileName();
        if (!TextUtils.isEmpty(profileName) && isMultiProfilesEnabled) {
            key = profileName + "_" + key;
        }

        return key;
    }
    
    @Override
    protected String getPrefsDir() {
        return PREFS_DIR;
    }
}
