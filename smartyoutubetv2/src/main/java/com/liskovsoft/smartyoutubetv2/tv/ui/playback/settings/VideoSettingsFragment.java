package com.liskovsoft.smartyoutubetv2.tv.ui.playback.settings;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackListPreferenceDialogFragment;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;

import java.util.List;
import java.util.Set;

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

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            mStyledContext = (Context) Helpers.getField(this, "mStyledContext");

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(mStyledContext);

            //PreferenceCategory category = new PreferenceCategory(styledContext);
            //category.setTitle("Video formats");
            //category.setEnabled(true);
            //screen.addPreference(category);

            for (SettingsCategory category : mCategories) {
                if (category.items != null) {
                    screen.addPreference(createPreference(category));
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

        public Preference createPreference(SettingsCategory category) {
            ListPreference pref = new ListPreference(mStyledContext);
            pref.setTitle(category.title);
            pref.setKey(category.toString());

            ListPrefData prefData = createListPrefData(category.items);

            pref.setEntries(prefData.entries);
            pref.setEntryValues(prefData.values);
            pref.setValue(prefData.defaultValue);

            // don't close menu on select

            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                for (OptionItem optionItem : category.items) {
                    if (newValue.equals(optionItem.toString())) {
                        category.callback.onSelect(optionItem);
                        break;
                    }
                }

                return true;
            });

            return pref;
        }

        private ListPrefData createListPrefData(List<OptionItem> items) {
            CharSequence[] titles = new CharSequence[items.size()];
            CharSequence[] hashes = new CharSequence[items.size()];
            String defaultValue = null;

            for (int i = 0; i < items.size(); i++) {
                OptionItem optionItem = items.get(i);

                titles[i] = optionItem.getTitle();
                hashes[i] = optionItem.toString();

                if (optionItem.isSelected()) {
                    defaultValue = optionItem.toString();
                }
            }

            return new ListPrefData(titles, hashes, defaultValue);
        }

        private static class ListPrefData {
            public final CharSequence[] entries;
            public final CharSequence[] values;
            public final String defaultValue;

            public ListPrefData(CharSequence[] entries, CharSequence[] values, String defaultValue) {
                 this.entries = entries;
                 this.values = values;
                 this.defaultValue = defaultValue;
            }
        }
    }
}