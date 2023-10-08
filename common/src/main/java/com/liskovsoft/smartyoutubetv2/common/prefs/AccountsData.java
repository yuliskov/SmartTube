package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

public class AccountsData {
    private static final String ACCOUNTS_DATA = "accounts_data";
    @SuppressLint("StaticFieldLeak")
    private static AccountsData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsSelectAccountOnBootEnabled;
    private boolean mIsAccountProtectedWithPassword;

    private AccountsData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static AccountsData instance(Context context) {
        if (sInstance == null) {
            sInstance = new AccountsData(context.getApplicationContext());
        }

        return sInstance;
    }

    public void selectAccountOnBoot(boolean select) {
        mIsSelectAccountOnBootEnabled = select;
        persistState();
    }

    public boolean isSelectAccountOnBootEnabled() {
        return mIsSelectAccountOnBootEnabled;
    }

    public void protectAccountWithPassword(boolean protect) {
        mIsAccountProtectedWithPassword = protect;
        persistState();
    }

    public boolean isAccountProtectedWithPassword() {
        return mIsAccountProtectedWithPassword;
    }

    private void restoreState() {
        String data = mAppPrefs.getData(ACCOUNTS_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsSelectAccountOnBootEnabled = Helpers.parseBoolean(split, 0, false);
        mIsAccountProtectedWithPassword = Helpers.parseBoolean(split, 1, false);
    }

    private void persistState() {
        mAppPrefs.setData(ACCOUNTS_DATA, Helpers.mergeObject(mIsSelectAccountOnBootEnabled, mIsAccountProtectedWithPassword));
    }
}
