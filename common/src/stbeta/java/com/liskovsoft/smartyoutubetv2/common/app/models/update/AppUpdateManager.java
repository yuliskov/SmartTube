package com.liskovsoft.smartyoutubetv2.common.app.models.update;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;

import java.util.ArrayList;
import java.util.List;

public class AppUpdateManager implements AppUpdateCheckerListener, IAppUpdateManager {
    private static final String UPDATE_MANIFEST_URL = "https://github.com/yuliskov/SmartYouTubeTV/releases/download/beta/smarttube_beta.json";
    @SuppressLint("StaticFieldLeak")
    private static AppUpdateManager sInstance;
    private final Context mContext;
    private final AppUpdateChecker mUpdateChecker;
    private final AppSettingsPresenter mSettingsPresenter;
    private boolean mUpdateInstalled;
    private boolean mForceCheck;

    public AppUpdateManager(Context context) {
        mContext = context;
        mUpdateChecker = new AppUpdateChecker(mContext, this);
        mSettingsPresenter = AppSettingsPresenter.instance(context);
    }

    public static AppUpdateManager instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUpdateManager(context.getApplicationContext());
        }

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start(boolean forceCheck) {
        mUpdateInstalled = false;
        mForceCheck = forceCheck;

        if (forceCheck) {
            mUpdateChecker.forceCheckForUpdates(UPDATE_MANIFEST_URL);
        } else {
            mUpdateChecker.checkForUpdates(UPDATE_MANIFEST_URL);
        }
    }

    @Override
    public void onUpdateFound(String versionName, List<String> changelog, String apkPath) {
        showUpdateDialog(versionName, changelog, apkPath);
    }

    private void showUpdateDialog(String versionName, List<String> changelog, String apkPath) {
        mSettingsPresenter.clear();

        mSettingsPresenter.appendStringsCategory(mContext.getString(R.string.update_changelog), createChangelogOptions(changelog));
        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(mContext.getString(R.string.install_update), optionItem -> {
                    mUpdateChecker.installUpdate();
                    SplashPresenter.instance(mContext).saveBackupData();
                    mUpdateInstalled = true;
                }, false));
        mSettingsPresenter.appendSingleSwitch(UiOptionItem.from(mContext.getString(R.string.show_again), optionItem -> {
            mUpdateChecker.enableUpdateCheck(optionItem.isSelected());
        }, mUpdateChecker.isUpdateCheckEnabled()));

        mSettingsPresenter.showDialog(String.format("%s %s", mContext.getString(R.string.app_name), versionName), this::unhold);
    }

    private List<OptionItem> createChangelogOptions(List<String> changelog) {
        List<OptionItem> options = new ArrayList<>();

        for (String change : changelog) {
            options.add(UiOptionItem.from(change, null, false));
        }

        return options;
    }

    @Override
    public void onError(Exception error) {
        if (mForceCheck) {
            if (AppUpdateCheckerListener.LATEST_VERSION.equals(error.getMessage())) {
                MessageHelpers.showMessage(mContext, R.string.update_not_found);
            } else {
                MessageHelpers.showMessage(mContext, R.string.update_in_progess);
            }
        }
    }

    @Override
    public void enableUpdateCheck(boolean enable) {
        mUpdateChecker.enableUpdateCheck(enable);
    }

    @Override
    public boolean isUpdateCheckEnabled() {
        return mUpdateChecker.isUpdateCheckEnabled();
    }
}
