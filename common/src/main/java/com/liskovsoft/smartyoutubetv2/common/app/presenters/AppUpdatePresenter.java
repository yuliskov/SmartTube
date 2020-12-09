package com.liskovsoft.smartyoutubetv2.common.app.presenters;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;

import java.util.ArrayList;
import java.util.List;

public class AppUpdatePresenter extends BasePresenter<Void> implements AppUpdateCheckerListener {
    @SuppressLint("StaticFieldLeak")
    private static AppUpdatePresenter sInstance;
    private final AppUpdateChecker mUpdateChecker;
    private final AppSettingsPresenter mSettingsPresenter;
    private final String mUpdateManifestUrl;
    private boolean mUpdateInstalled;
    private boolean mIsForceCheck;

    public AppUpdatePresenter(Context context) {
        super(context);
        mUpdateChecker = new AppUpdateChecker(context, this);
        mSettingsPresenter = AppSettingsPresenter.instance(context);
        mUpdateManifestUrl = context.getString(R.string.update_manifest_url);
    }

    public static AppUpdatePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUpdatePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public void unhold() {
        sInstance = null;
    }

    public void start(boolean forceCheck) {
        mIsForceCheck = forceCheck;
        mUpdateInstalled = false;

        if (forceCheck) {
            mUpdateChecker.forceCheckForUpdates(mUpdateManifestUrl);
        } else {
            mUpdateChecker.checkForUpdates(mUpdateManifestUrl);
        }
    }

    @Override
    public void onUpdateFound(String versionName, List<String> changelog, String apkPath) {
        showUpdateDialog(versionName, changelog, apkPath);
    }

    private void showUpdateDialog(String versionName, List<String> changelog, String apkPath) {
        mSettingsPresenter.clear();

        mSettingsPresenter.appendStringsCategory(getContext().getString(R.string.update_changelog), createChangelogOptions(changelog));
        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.install_update), optionItem -> {
                    mUpdateChecker.installUpdate();
                    SplashPresenter.instance(getContext()).saveBackupData();
                    mUpdateInstalled = true;
                }, false));
        mSettingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.show_again), optionItem -> {
            mUpdateChecker.enableUpdateCheck(optionItem.isSelected());
        }, mUpdateChecker.isUpdateCheckEnabled()));

        mSettingsPresenter.showDialog(String.format("%s %s", getContext().getString(R.string.app_name), versionName), this::unhold);
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
        if (mIsForceCheck) {
            if (AppUpdateCheckerListener.LATEST_VERSION.equals(error.getMessage())) {
                MessageHelpers.showMessage(getContext(), R.string.update_not_found);
            } else {
                MessageHelpers.showMessage(getContext(), R.string.update_in_progess);
            }
        }
    }
}
