package com.liskovsoft.smartyoutubetv2.tv.ui.playback.settings;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;

import java.util.HashSet;
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
            if (category.type == SettingsCategory.TYPE_CHECKBOX) {
                return createCheckedPreference(category);
            }

            return createRadioPreference(category);
        }

        private Preference createRadioPreference(SettingsCategory category) {
            ListPreference pref = new ListPreference(mStyledContext);
            pref.setTitle(category.title);
            pref.setKey(category.toString());

            ListPrefData prefData = createListPrefData(category.items);

            pref.setEntries(prefData.entries);
            pref.setEntryValues(prefData.values);
            pref.setValue(prefData.defaultValue);

            // TODO: don't close menu on select

            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                for (OptionItem optionItem : category.items) {
                    if (newValue.equals(optionItem.toString())) {
                        optionItem.onSelect(true);
                        break;
                    }
                }

                return true;
            });

            return pref;
        }

        private Preference createCheckedPreference(SettingsCategory category) {
            MultiSelectListPreference pref = new MultiSelectListPreference(mStyledContext);
            pref.setTitle(category.title);
            pref.setKey(category.toString());

            ListPrefData prefData = createListPrefData(category.items);

            pref.setEntries(prefData.entries);
            pref.setEntryValues(prefData.values);
            pref.setValues(prefData.defaultValues);

            // TODO: don't close menu on select

            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue instanceof Set) {
                    Set values = ((Set) newValue);
                    for (OptionItem item : category.items) {
                        boolean found = false;
                        for (Object value : values) {
                            found = value.equals(item.toString());
                            if (found) {
                                break;
                            }
                        }
                        item.onSelect(found);
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
            Set<String> defaultValues = new HashSet<>(); // used in multi set lists

            for (int i = 0; i < items.size(); i++) {
                OptionItem optionItem = items.get(i);

                CharSequence title = optionItem.getTitle();
                String value = optionItem.toString();

                titles[i] = title;
                hashes[i] = value;

                if (optionItem.isSelected()) {
                    defaultValue = value;
                    defaultValues.add(value);
                }
            }

            return new ListPrefData(titles, hashes, defaultValue, defaultValues);
        }

        private static class ListPrefData {
            public final CharSequence[] entries;
            public final CharSequence[] values;
            public final String defaultValue;
            public final Set<String> defaultValues;

            public ListPrefData(CharSequence[] entries, CharSequence[] values, String defaultValue, Set<String> defaultValues) {
                this.entries = entries;
                this.values = values;
                this.defaultValue = defaultValue;
                this.defaultValues = defaultValues;
            }
        }
    }
}