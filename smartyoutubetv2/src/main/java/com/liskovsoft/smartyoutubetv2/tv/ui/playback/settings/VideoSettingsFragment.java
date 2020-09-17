package com.liskovsoft.smartyoutubetv2.tv.ui.playback.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import androidx.annotation.Nullable;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controller.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.VideoSettingsPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.common.app.views.VideoSettingsView;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.old.AuthenticationActivity;

import java.util.List;
import java.util.Locale.Category;

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
                screen.addPreference(createPreference(category));
            }

            setPreferenceScreen(screen);
        }

        public Preference createPreference(SettingsCategory category) {
            ListPreference pref = new ListPreference(mStyledContext);
            pref.setTitle(category.title);
            pref.setKey(category.toString());

            pref.setEntries(toTitleArray(category.items));
            pref.setEntryValues(toHashArray(category.items));

            for (OptionItem item : category.items) {
                if (item.isSelected()) {
                    pref.setValue(item.toString());
                    break;
                }
            }

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

        private CharSequence[] toTitleArray(List<OptionItem> items) {
            CharSequence[] result = new CharSequence[items.size()];

            for (int i = 0; i < items.size(); i++) {
                result[i] = items.get(i).getTitle();
            }

            return result;
        }

        private CharSequence[] toHashArray(List<OptionItem> items) {
            CharSequence[] result = new CharSequence[items.size()];

            for (int i = 0; i < items.size(); i++) {
                result[i] = items.get(i).toString();
            }

            return result;
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            return super.onPreferenceTreeClick(preference);
        }

        private Context getStyledContext() {
            final TypedValue tv = new TypedValue();
            getActivity().getTheme().resolveAttribute(R.attr.preferenceTheme, tv, true);
            int theme = tv.resourceId;
            if (theme == 0) {
                // Fallback to default theme.
                theme = R.style.PreferenceThemeOverlay;
            }

            return new ContextThemeWrapper(getActivity(), theme);
        }

        public void addCategories(List<SettingsCategory> categories) {
            mCategories = categories;
        }
    }
}