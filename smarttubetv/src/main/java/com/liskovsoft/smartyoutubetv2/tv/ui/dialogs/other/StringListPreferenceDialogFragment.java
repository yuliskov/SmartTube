package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.preference.LeanbackListPreferenceDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.util.Set;

public class StringListPreferenceDialogFragment extends LeanbackListPreferenceDialogFragment {
    private boolean mIsTransparent;

    public static StringListPreferenceDialogFragment newInstanceStringList(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final StringListPreferenceDialogFragment
                fragment = new StringListPreferenceDialogFragment();
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public Adapter<ViewHolder> onCreateAdapter() {
        return new AdapterMultiStrings(mEntries, mEntryValues, mInitialSelections);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        if (mIsTransparent && view != null) {
            ViewUtil.enableTransparentDialog(getActivity(), view);
        }

        return view;
    }

    public void enableTransparent(boolean enable) {
        mIsTransparent = enable;
    }

    public class AdapterMultiStrings extends AdapterMulti {
        public AdapterMultiStrings(CharSequence[] entries, CharSequence[] entryValues, Set<String> initialSelections) {
            super(entries, entryValues, initialSelections);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            final View view = inflater.inflate(R.layout.dialog_list_preference_item_multi, parent,
                    false);
            return new ViewHolder(view, this);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.getWidgetView().setChecked(
                    mSelections.contains(mEntryValues[position].toString()));
            holder.getTitleView().setText(mEntries[position]);
        }
    }
}
