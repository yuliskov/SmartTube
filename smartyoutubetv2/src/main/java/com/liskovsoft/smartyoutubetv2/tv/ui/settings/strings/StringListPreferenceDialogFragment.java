package com.liskovsoft.smartyoutubetv2.tv.ui.settings.strings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.collection.ArraySet;
import androidx.leanback.preference.LeanbackListPreferenceDialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StringListPreferenceDialogFragment extends LeanbackListPreferenceDialogFragment {
    private static final String SAVE_STATE_IS_MULTI =
            "LeanbackListPreferenceDialogFragment.isMulti";
    private static final String SAVE_STATE_ENTRIES = "LeanbackListPreferenceDialogFragment.entries";
    private static final String SAVE_STATE_ENTRY_VALUES =
            "LeanbackListPreferenceDialogFragment.entryValues";
    private static final String SAVE_STATE_TITLE = "LeanbackListPreferenceDialogFragment.title";
    private static final String SAVE_STATE_MESSAGE = "LeanbackListPreferenceDialogFragment.message";
    private static final String SAVE_STATE_INITIAL_SELECTIONS =
            "LeanbackListPreferenceDialogFragment.initialSelections";
    private static final String SAVE_STATE_INITIAL_SELECTION =
            "LeanbackListPreferenceDialogFragment.initialSelection";

    private boolean mMulti;
    private CharSequence[] mEntries;
    private CharSequence[] mEntryValues;
    private CharSequence mDialogTitle;
    private CharSequence mDialogMessage;
    Set<String> mInitialSelections;
    private String mInitialSelection;

    public static StringListPreferenceDialogFragment newInstanceStringList(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final StringListPreferenceDialogFragment
                fragment = new StringListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final DialogPreference preference = getPreference();
            mDialogTitle = preference.getDialogTitle();
            mDialogMessage = preference.getDialogMessage();

            if (preference instanceof ListPreference) {
                mMulti = false;
                mEntries = ((ListPreference) preference).getEntries();
                mEntryValues = ((ListPreference) preference).getEntryValues();
                mInitialSelection = ((ListPreference) preference).getValue();
            } else if (preference instanceof MultiSelectListPreference) {
                mMulti = true;
                mEntries = ((MultiSelectListPreference) preference).getEntries();
                mEntryValues = ((MultiSelectListPreference) preference).getEntryValues();
                mInitialSelections = ((MultiSelectListPreference) preference).getValues();
            } else {
                throw new IllegalArgumentException("Preference must be a ListPreference or "
                        + "MultiSelectListPreference");
            }
        } else {
            mDialogTitle = savedInstanceState.getCharSequence(SAVE_STATE_TITLE);
            mDialogMessage = savedInstanceState.getCharSequence(SAVE_STATE_MESSAGE);
            mMulti = savedInstanceState.getBoolean(SAVE_STATE_IS_MULTI);
            mEntries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES);
            mEntryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES);
            if (mMulti) {
                final String[] initialSelections = savedInstanceState.getStringArray(
                        SAVE_STATE_INITIAL_SELECTIONS);
                mInitialSelections = new ArraySet<>(
                        initialSelections != null ? initialSelections.length : 0);
                if (initialSelections != null) {
                    Collections.addAll(mInitialSelections, initialSelections);
                }
            } else {
                mInitialSelection = savedInstanceState.getString(SAVE_STATE_INITIAL_SELECTION);
            }
        }
    }

    @Override
    public Adapter<ViewHolder> onCreateAdapter() {
        return new AdapterMultiStrings(mEntries, mEntryValues, mInitialSelections);
    }

    public class AdapterMultiStrings extends RecyclerView.Adapter<ViewHolder>
            implements ViewHolder.OnItemClickListener {

        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        private final Set<String> mSelections;

        public AdapterMultiStrings(CharSequence[] entries, CharSequence[] entryValues,
                                   Set<String> initialSelections) {
            mEntries = entries;
            mEntryValues = entryValues;
            mSelections = new HashSet<>(initialSelections);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            final View view = inflater.inflate(R.layout.string_list_preference_item_multi, parent,
                    false);
            return new ViewHolder(view, this);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.getWidgetView().setChecked(
                    mSelections.contains(mEntryValues[position].toString()));
            holder.getTitleView().setText(mEntries[position]);
        }

        @Override
        public int getItemCount() {
            return mEntries.length;
        }

        @Override
        public void onItemClick(ViewHolder viewHolder) {
            final int index = viewHolder.getAdapterPosition();
            if (index == RecyclerView.NO_POSITION) {
                return;
            }
            final String entry = mEntryValues[index].toString();
            if (mSelections.contains(entry)) {
                mSelections.remove(entry);
            } else {
                mSelections.add(entry);
            }
            final MultiSelectListPreference multiSelectListPreference
                    = (MultiSelectListPreference) getPreference();
            // Pass copies of the set to callChangeListener and setValues to avoid mutations
            if (multiSelectListPreference.callChangeListener(new HashSet<>(mSelections))) {
                multiSelectListPreference.setValues(new HashSet<>(mSelections));
                mInitialSelections = mSelections;
            } else {
                // Change refused, back it out
                if (mSelections.contains(entry)) {
                    mSelections.remove(entry);
                } else {
                    mSelections.add(entry);
                }
            }

            notifyDataSetChanged();
        }
    }
}
