package com.liskovsoft.smartyoutubetv2.tv.ui.playback.settings;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;

import java.util.List;

public class VideoSettingsFragment extends LeanbackSettingsFragment
        implements DialogPreference.TargetFragment, VideoSettingsView {
    private PrefFragment mPreferenceFragment;
    private VideoSettingsPresenter mSettingsPresenter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSettingsPresenter = VideoSettingsPresenter.instance(getActivity());
        mSettingsPresenter.register(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mSettingsPresenter.onInitDone(); // should run before onActivityCreated
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSettingsPresenter.unregister(this);
    }

    @Override
    public void onPreferenceStartInitialScreen() {
        mPreferenceFragment = buildPreferenceFragment();
        startPreferenceFragment(mPreferenceFragment);
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

    private PrefFragment buildPreferenceFragment() {
        return new PrefFragment();
    }

    @Override
    public void addCategories(List<SettingsCategory> categories) {
        mPreferenceFragment.addCategories(categories);
    }

    @Override
    public void setTitle(String title) {
        mPreferenceFragment.setTitle(title);
    }

    //@Override
    //public boolean onPreferenceDisplayDialog(@NonNull PreferenceFragment caller, Preference pref) {
    //    if (caller == null) {
    //        throw new IllegalArgumentException("Cannot display dialog for preference " + pref
    //                + ", Caller must not be null!");
    //    }
    //    final Fragment f;
    //    if (pref instanceof ListPreference) {
    //        final ListPreference listPreference = (ListPreference) pref;
    //        f = LeanbackListPreferenceDialogFragment.newInstanceSingle(listPreference.getKey());
    //        f.setTargetFragment(caller, 0);
    //        startPreferenceFragment(f);
    //    } else if (pref instanceof MultiSelectListPreference) {
    //        MultiSelectListPreference listPreference = (MultiSelectListPreference) pref;
    //        f = LeanbackListPreferenceDialogFragment.newInstanceMulti(listPreference.getKey());
    //        f.setTargetFragment(caller, 0);
    //        startPreferenceFragment(f);
    //    }
    //    // TODO
    //    //        else if (pref instanceof EditTextPreference) {
    //    //
    //    //        }
    //    else {
    //        return false;
    //    }
    //    return true;
    //}

    public static class PrefFragment extends LeanbackPreferenceFragment {
        private List<SettingsCategory> mCategories;
        private Context mStyledContext;
        private PreferenceFragmentHelper mManager;
        private String mTitle;

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            mStyledContext = (Context) Helpers.getField(this, "mStyledContext");
            mManager = new PreferenceFragmentHelper(mStyledContext);

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(mStyledContext);
            screen.setTitle(mTitle);

            for (SettingsCategory category : mCategories) {
                if (category.items != null) {
                    screen.addPreference(mManager.createPreference(category));
                }
            }

            setPreferenceScreen(screen);
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