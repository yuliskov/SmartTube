package com.liskovsoft.smartyoutubetv2.tv.ui.settings;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackPreferenceDialogFragment;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppSettingsPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppSettingsView;
import com.liskovsoft.smartyoutubetv2.tv.ui.settings.dialogs.RadioListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.settings.dialogs.StringListPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.settings.dialogs.StringListPreferenceDialogFragment;

import java.util.List;

public class AppSettingsFragment extends LeanbackSettingsFragment
        implements DialogPreference.TargetFragment, AppSettingsView {
    private static final String TAG = AppSettingsFragment.class.getSimpleName();
    private AppPreferenceFragment mPreferenceFragment;
    private AppSettingsPresenter mSettingsPresenter;
    private boolean mIsStateSaved;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettingsPresenter = AppSettingsPresenter.instance(getActivity());
        mSettingsPresenter.setView(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mSettingsPresenter.onViewDestroyed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mIsStateSaved = true;
    }

    @Override
    public void onPreferenceStartInitialScreen() {
        // FIX: Can not perform this action after onSaveInstanceState
        if (mIsStateSaved) {
            return;
        }

        mPreferenceFragment = buildPreferenceFragment();
        startPreferenceFragment(mPreferenceFragment);

        mSettingsPresenter.onViewInitialized();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment,
        Preference preference) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment preferenceFragment,
        PreferenceScreen preferenceScreen) {
        PreferenceFragment frag = buildPreferenceFragment();
        startPreferenceFragment(frag);
        return true;
    }

    @Override
    public Preference findPreference(CharSequence charSequence) {
        return mPreferenceFragment.findPreference(charSequence);
    }

    private AppPreferenceFragment buildPreferenceFragment() {
        return new AppPreferenceFragment();
    }

    @Override
    public void setTitle(String title) {
        mPreferenceFragment.setTitle(title);
    }

    @Override
    public void addCategories(List<SettingsCategory> categories) {
        mPreferenceFragment.addCategories(categories);
    }

    @Override
    public void clear() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::onPreferenceStartInitialScreen);
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, Preference pref) {
        if (pref instanceof StringListPreference) {
            StringListPreference listPreference = (StringListPreference) pref;
            LeanbackPreferenceDialogFragment f = StringListPreferenceDialogFragment.newInstanceStringList(listPreference.getKey());
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);

            return true;
        } else if (pref instanceof ListPreference) {
            ListPreference listPreference = (ListPreference) pref;
            LeanbackPreferenceDialogFragment f = RadioListPreferenceDialogFragment.newInstanceSingle(listPreference.getKey());
            f.setTargetFragment(caller, 0);
            startPreferenceFragment(f);

            return true;
        }

        return super.onPreferenceDisplayDialog(caller, pref);
    }

    @Override
    public void finish() {
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    public void onFinish() {
        mSettingsPresenter.onClose();
    }

    public static class AppPreferenceFragment extends LeanbackPreferenceFragment {
        private static final String TAG = AppPreferenceFragment.class.getSimpleName();
        private List<SettingsCategory> mCategories;
        private Context mExtractedContext;
        private AppSettingsFragmentHelper mManager;
        private String mTitle;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            // Note, place in field with different name to avoid field overlapping
            mExtractedContext = (Context) Helpers.getField(this, "mStyledContext");
            mManager = new AppSettingsFragmentHelper(mExtractedContext);

            initPrefs();

            Log.d(TAG, "onCreatePreferences");
        }

        private void initPrefs() {
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(mExtractedContext);
            setPreferenceScreen(screen);

            screen.setTitle(mTitle);

            addCategories(screen);

            setSingleCategoryAsRoot(screen);
        }

        private void addCategories(PreferenceScreen screen) {
            if (mCategories != null) {
                for (SettingsCategory category : mCategories) {
                    if (category.items != null) {
                        screen.addPreference(mManager.createPreference(category));
                    }
                }
            }
        }
        
        private void setSingleCategoryAsRoot(PreferenceScreen screen) {
            // auto expand single list preference
            if (mCategories != null && mCategories.size() == 1 && screen.getPreferenceCount() > 0) {
                onDisplayPreferenceDialog(screen.getPreference(0));

                getFragmentManager().addOnBackStackChangedListener(() -> {
                    if (getFragmentManager() != null && getFragmentManager().getBackStackEntryCount() == 0) {
                        getActivity().onBackPressed();
                    }
                });
            }
        }

        @Override
        public void onDisplayPreferenceDialog(Preference preference) {
            super.onDisplayPreferenceDialog(preference);
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            return super.onPreferenceTreeClick(preference);
        }

        public void addCategories(List<SettingsCategory> categories) {
            mCategories = categories;
        }

        public void setTitle(String title) {
            mTitle = title;
        }
    }
}