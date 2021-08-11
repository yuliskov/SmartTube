package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.RadioListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreference;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreferenceDialogFragment;

import java.util.List;

public class AppDialogFragment extends LeanbackSettingsFragment
        implements DialogPreference.TargetFragment, AppDialogView {
    private static final String TAG = AppDialogFragment.class.getSimpleName();
    private AppPreferenceFragment mPreferenceFragment;
    private AppDialogPresenter mSettingsPresenter;
    private boolean mIsStateSaved;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettingsPresenter = AppDialogPresenter.instance(getActivity());
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
        // Possible fix: Unable to add window -- token android.os.BinderProxy is not valid; is your activity running?
        if (mIsStateSaved || !Utils.checkActivity(getActivity())) {
            return;
        }

        // Fix mSettingsPresenter in null after init stage.
        // Seems concurrency between dialogs.
        mSettingsPresenter.setView(this);

        mPreferenceFragment = buildPreferenceFragment();
        startPreferenceFragment(mPreferenceFragment);

        mSettingsPresenter.onViewInitialized();
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        return false;
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment preferenceFragment, PreferenceScreen preferenceScreen) {
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
        if (mPreferenceFragment != null) {
            mPreferenceFragment.setTitle(title);
        }
    }

    @Override
    public void addCategories(List<SettingsCategory> categories) {
        if (mPreferenceFragment != null) {
            mPreferenceFragment.addCategories(categories);
        }
    }

    @Override
    public void clear() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::onPreferenceStartInitialScreen);
        }
    }

    @Override
    public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, Preference pref) {
        // Fix: IllegalStateException: Activity has been destroyed
        // Possible fix: Unable to add window -- token android.os.BinderProxy is not valid; is your activity running?
        if (!Utils.checkActivity(getActivity())) {
            return false;
        }

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
        private AppDialogFragmentHelper mManager;
        private String mTitle;
        private int mBackStackCount;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            // Note, place in field with different name to avoid field overlapping
            mExtractedContext = (Context) Helpers.getField(this, "mStyledContext");
            mManager = new AppDialogFragmentHelper(mExtractedContext);

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
            // Possible fix: java.lang.IllegalStateException Activity has been destroyed
            if (!Utils.checkActivity(getActivity())) {
                return;
            }

            // auto expand single list preference
            if (mCategories != null && mCategories.size() == 1 && screen.getPreferenceCount() > 0) {
                Preference preference = screen.getPreference(0);

                if (preference instanceof DialogPreference) {
                    onDisplayPreferenceDialog(preference);

                    if (getFragmentManager() != null) {
                        mBackStackCount = 0;

                        getFragmentManager().addOnBackStackChangedListener(() -> {
                            if (getFragmentManager() != null) {
                                int currentBackStackCount = getFragmentManager().getBackStackEntryCount();

                                if (currentBackStackCount < mBackStackCount) {
                                    if (currentBackStackCount == 0) {
                                        // single dialog
                                        getActivity().finish();
                                    } else {
                                        // multiple stacked dialogs
                                        getFragmentManager().popBackStack();
                                    }
                                }

                                mBackStackCount = currentBackStackCount;
                            }
                        });
                    }
                }
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