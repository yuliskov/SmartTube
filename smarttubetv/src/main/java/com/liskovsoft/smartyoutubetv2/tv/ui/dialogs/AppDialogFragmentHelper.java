package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs;

import android.content.Context;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter.SettingsCategory;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other.StringListPreference;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppDialogFragmentHelper {
    private final Context mStyledContext;

    public static class ListPreferenceData {
        public final CharSequence[] entries;
        public final CharSequence[] values;
        public final String defaultValue;
        public final Set<String> defaultValues;

        public ListPreferenceData(CharSequence[] entries, CharSequence[] values, String defaultValue, Set<String> defaultValues) {
            this.entries = entries;
            this.values = values;
            this.defaultValue = defaultValue;
            this.defaultValues = defaultValues;
        }
    }

    public AppDialogFragmentHelper(Context styledContext) {
        mStyledContext = styledContext;
    }

    public Preference createPreference(SettingsCategory category) {
        switch (category.type) {
            case SettingsCategory.TYPE_CHECKBOX_LIST:
                return createCheckedListPreference(category);
            case SettingsCategory.TYPE_RADIO_LIST:
                return createRadioListPreference(category);
            case SettingsCategory.TYPE_STRING_LIST:
                return createStringListPreference(category);
            case SettingsCategory.TYPE_SINGLE_SWITCH:
                return createSwitchPreference(category);
            case SettingsCategory.TYPE_SINGLE_BUTTON:
                return createButtonPreference(category);
        }

        throw  new IllegalStateException("Can't find matched preference for type: " + category.type);
    }

    private Preference createStringListPreference(SettingsCategory category) {
        MultiSelectListPreference pref = new StringListPreference(mStyledContext);

        initMultiSelectListPreference(category, pref);

        return pref;
    }

    public Preference createButtonPreference(SettingsCategory category) {
        Preference result = null;

        if (category.items.size() == 1) {
            OptionItem item = category.items.get(0);
            Preference preference = new Preference(mStyledContext);
            preference.setPersistent(false);
            preference.setTitle(item.getTitle());
            preference.setOnPreferenceClickListener(pref -> {
                item.onSelect(true);
                return true;
            });

            result = preference;
        }

        return result;
    }

    public Preference createSwitchPreference(SettingsCategory category) {
        Preference result = null;

        if (category.items.size() == 1) {
            OptionItem item = category.items.get(0);
            Preference preference = new SwitchPreference(mStyledContext);
            preference.setPersistent(false);
            preference.setTitle(item.getTitle());
            preference.setDefaultValue(item.isSelected());
            preference.setOnPreferenceChangeListener((pref, newValue) -> {
                item.onSelect((boolean) newValue);
                return true;
            });

            result = preference;
        }

        return result;
    }

    public Preference createRadioListPreference(SettingsCategory category) {
        ListPreference pref = new ListPreference(mStyledContext);

        initSingleSelectListPreference(category, pref);

        return pref;
    }

    public Preference createCheckedListPreference(SettingsCategory category) {
        MultiSelectListPreference pref = new MultiSelectListPreference(mStyledContext);

        initMultiSelectListPreference(category, pref);

        return pref;
    }

    private void initSingleSelectListPreference(SettingsCategory category, ListPreference pref) {
        initDialogPreference(category, pref);

        ListPreferenceData prefData = createListPreferenceData(category.items);

        pref.setEntries(prefData.entries);
        pref.setEntryValues(prefData.values);
        pref.setValue(prefData.defaultValue);

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            for (OptionItem optionItem : category.items) {
                if (newValue.equals(optionItem.toString())) {
                    optionItem.onSelect(true);
                    break;
                }
            }

            return true;
        });
    }

    private void initMultiSelectListPreference(SettingsCategory category, MultiSelectListPreference pref) {
        initDialogPreference(category, pref);

        ListPreferenceData prefData = createListPreferenceData(category.items);

        pref.setEntries(prefData.entries);
        pref.setEntryValues(prefData.values);
        pref.setValues(prefData.defaultValues);

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Set) {
                Set<?> values = ((Set<?>) newValue); // All checked items. That don't means that this items is pressed recently.
                for (OptionItem item : category.items) {
                    boolean isSelected = false;
                    for (Object value : values) {
                        isSelected = value.equals(item.toString());
                        if (isSelected) {
                            break;
                        }
                    }

                    if (item.isSelected() != isSelected) {
                        if (isSelected) {
                            OptionItem[] requiredItems = item.getRequire();

                            if (requiredItems != null) {
                                for (OptionItem requiredItem : requiredItems) {
                                    if (!requiredItem.isSelected()) {
                                        MessageHelpers.showMessageThrottled(mStyledContext, mStyledContext.getString(R.string.require_checked, requiredItem.getTitle()));
                                        //return false;
                                    }
                                }
                            }
                        }

                        item.onSelect(isSelected);

                        return true;
                    }
                }
            }

            return false;
        });
    }

    public ListPreferenceData createListPreferenceData(List<OptionItem> items) {
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

        return new ListPreferenceData(titles, hashes, defaultValue, defaultValues);
    }

    private void initDialogPreference(SettingsCategory category, DialogPreference pref) {
        pref.setPersistent(false);
        pref.setTitle(category.title);
        pref.setDialogTitle(category.title);
        pref.setKey(category.toString());
    }
}
