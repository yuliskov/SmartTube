package com.liskovsoft.smartyoutubetv2.tv.ui.settings.dialogs;

import android.os.Bundle;
import androidx.preference.ListPreference;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.LeanbackListPreferenceDialogFragment;

public class RadioListPreferenceDialogFragment extends LeanbackListPreferenceDialogFragment {
    @Override
    public Adapter<ViewHolder> onCreateAdapter() {
        return new AdapterRadio(mEntries, mEntryValues, mInitialSelection);
    }

    public static LeanbackListPreferenceDialogFragment newInstanceSingle(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final LeanbackListPreferenceDialogFragment
                fragment = new RadioListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    public class AdapterRadio extends AdapterSingle {
        public AdapterRadio(CharSequence[] entries, CharSequence[] entryValues, CharSequence selectedValue) {
            super(entries, entryValues, selectedValue);
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

            //getFragmentManager().popBackStack();
            notifyDataSetChanged();
        }
    }
}
