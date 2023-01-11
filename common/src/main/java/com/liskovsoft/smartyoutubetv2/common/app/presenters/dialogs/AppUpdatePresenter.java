package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AppUpdatePresenter extends BasePresenter<Void> implements AppUpdateCheckerListener {
    @SuppressLint("StaticFieldLeak")
    private static AppUpdatePresenter sInstance;
    private final AppUpdateChecker mUpdateChecker;
    private final AppDialogPresenter mSettingsPresenter;
    private final String[] mUpdateManifestUrls;
    private boolean mIsForceCheck;

    public AppUpdatePresenter(Context context) {
        super(context);
        mUpdateChecker = new AppUpdateChecker(context, this);
        mSettingsPresenter = AppDialogPresenter.instance(context);
        mUpdateManifestUrls = context.getResources().getStringArray(R.array.update_urls);
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

        if (forceCheck) {
            LoadingManager.showLoading(getContext(), true);
            mUpdateChecker.forceCheckForUpdates(mUpdateManifestUrls);
        } else {
            mUpdateChecker.checkForUpdates(mUpdateManifestUrls);
        }
    }

    @Override
    public void onUpdateFound(String versionName, List<String> changelog, String apkPath) {
        if (mIsForceCheck) {
            LoadingManager.showLoading(getContext(), false);
        }

        // Don't show update dialog if player opened
        if (getContext() != null && !Utils.isPlayerInForeground(getContext()) && Utils.isAppInForeground()) {
            showUpdateDialog(versionName, changelog, apkPath);
        }
    }

    @Override
    public void onUpdateError(Exception error) {
        if (mIsForceCheck) {
            LoadingManager.showLoading(getContext(), false);

            if (AppUpdateCheckerListener.LATEST_VERSION.equals(error.getMessage())) {
                MessageHelpers.showMessage(getContext(), R.string.update_not_found);
            } else {
                MessageHelpers.showMessage(getContext(), R.string.update_error);
            }
        }

        onDone();
    }

    private void showUpdateDialog(String versionName, List<String> changelog, String apkPath) {
        mSettingsPresenter.appendStringsCategory(getContext().getString(R.string.update_changelog), createChangelogOptions(changelog));
        mSettingsPresenter.appendSingleButton(
                UiOptionItem.from(getContext().getString(R.string.install_update), optionItem -> {
                    mUpdateChecker.installUpdate();
                    SplashPresenter.instance(getContext()).saveBackupData();
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
}
