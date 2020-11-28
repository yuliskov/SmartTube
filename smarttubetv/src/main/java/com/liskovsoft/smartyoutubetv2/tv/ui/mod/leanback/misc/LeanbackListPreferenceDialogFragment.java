/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;
import androidx.leanback.preference.LeanbackPreferenceDialogFragment;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.DialogPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LeanbackListPreferenceDialogFragment extends LeanbackPreferenceDialogFragment {

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
    protected CharSequence[] mEntries;
    protected CharSequence[] mEntryValues;
    private CharSequence mDialogTitle;
    private CharSequence mDialogMessage;
    protected Set<String> mInitialSelections;
    protected String mInitialSelection;

    public static LeanbackListPreferenceDialogFragment newInstanceSingle(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final LeanbackListPreferenceDialogFragment
                fragment = new LeanbackListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    public static LeanbackListPreferenceDialogFragment newInstanceMulti(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final LeanbackListPreferenceDialogFragment
                fragment = new LeanbackListPreferenceDialogFragment();
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(SAVE_STATE_TITLE, mDialogTitle);
        outState.putCharSequence(SAVE_STATE_MESSAGE, mDialogMessage);
        outState.putBoolean(SAVE_STATE_IS_MULTI, mMulti);
        outState.putCharSequenceArray(SAVE_STATE_ENTRIES, mEntries);
        outState.putCharSequenceArray(SAVE_STATE_ENTRY_VALUES, mEntryValues);
        if (mMulti) {
            outState.putStringArray(SAVE_STATE_INITIAL_SELECTIONS,
                    mInitialSelections.toArray(new String[mInitialSelections.size()]));
        } else {
            outState.putString(SAVE_STATE_INITIAL_SELECTION, mInitialSelection);
        }
    }

    @Override
    public @Nullable View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(androidx.leanback.preference.R.layout.leanback_list_preference_fragment, container,
                false);
        final VerticalGridView verticalGridView =
                (VerticalGridView) view.findViewById(android.R.id.list);

        verticalGridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE);
        verticalGridView.setFocusScrollStrategy(VerticalGridView.FOCUS_SCROLL_ALIGNED);
        verticalGridView.setAdapter(onCreateAdapter());
        verticalGridView.requestFocus();

        final CharSequence title = mDialogTitle;
        if (!TextUtils.isEmpty(title)) {
            final TextView titleView = (TextView) view.findViewById(androidx.leanback.preference.R.id.decor_title);
            titleView.setText(title);
        }

        final CharSequence message = mDialogMessage;
        if (!TextUtils.isEmpty(message)) {
            final TextView messageView = (TextView) view.findViewById(android.R.id.message);
            messageView.setVisibility(View.VISIBLE);
            messageView.setText(message);
        }

        return view;
    }

    public RecyclerView.Adapter onCreateAdapter() {
        //final DialogPreference preference = getPreference();
        if (mMulti) {
            return new AdapterMulti(mEntries, mEntryValues, mInitialSelections);
        } else {
            return new AdapterSingle(mEntries, mEntryValues, mInitialSelection);
        }
    }

    public class AdapterSingle extends RecyclerView.Adapter<ViewHolder>
            implements ViewHolder.OnItemClickListener {

        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        protected CharSequence mSelectedValue;

        public AdapterSingle(CharSequence[] entries, CharSequence[] entryValues,
                CharSequence selectedValue) {
            mEntries = entries;
            mEntryValues = entryValues;
            mSelectedValue = selectedValue;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            final View view = inflater.inflate(androidx.leanback.preference.R.layout.leanback_list_preference_item_single,
                    parent, false);
            return new ViewHolder(view, this);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.getWidgetView().setChecked(mEntryValues[position].equals(mSelectedValue));
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
            final CharSequence entry = mEntryValues[index];
            final ListPreference preference = (ListPreference) getPreference();
            if (index >= 0) {
                String value = mEntryValues[index].toString();
                if (preference.callChangeListener(value)) {
                    preference.setValue(value);
                    mSelectedValue = entry;
                }
            }

            getFragmentManager().popBackStack();
            notifyDataSetChanged();
        }
    }

    public class AdapterMulti extends RecyclerView.Adapter<ViewHolder>
            implements ViewHolder.OnItemClickListener {

        private final CharSequence[] mEntries;
        private final CharSequence[] mEntryValues;
        protected final Set<String> mSelections;

        public AdapterMulti(CharSequence[] entries, CharSequence[] entryValues,
                Set<String> initialSelections) {
            mEntries = entries;
            mEntryValues = entryValues;
            mSelections = new HashSet<>(initialSelections);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            final View view = inflater.inflate(androidx.leanback.preference.R.layout.leanback_list_preference_item_multi, parent,
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

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        public interface OnItemClickListener {
            void onItemClick(ViewHolder viewHolder);
        }

        private final Checkable mWidgetView;
        private final TextView mTitleView;
        private final ViewGroup mContainer;
        private final OnItemClickListener mListener;

        public ViewHolder(@NonNull View view, @NonNull OnItemClickListener listener) {
            super(view);
            mWidgetView = (Checkable) view.findViewById(androidx.leanback.preference.R.id.button);
            mContainer = (ViewGroup) view.findViewById(androidx.leanback.preference.R.id.container);
            mTitleView = (TextView) view.findViewById(android.R.id.title);
            mContainer.setOnClickListener(this);
            mListener = listener;
        }

        public Checkable getWidgetView() {
            return mWidgetView;
        }

        public TextView getTitleView() {
            return mTitleView;
        }

        public ViewGroup getContainer() {
            return mContainer;
        }

        @Override
        public void onClick(View v) {
            mListener.onItemClick(this);
        }
    }
}
