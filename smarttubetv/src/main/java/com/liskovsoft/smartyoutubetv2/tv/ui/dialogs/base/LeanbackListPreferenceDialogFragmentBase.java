package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.base;

import android.os.Bundle;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.LeanbackListPreferenceDialogFragment;

public class LeanbackListPreferenceDialogFragmentBase extends LeanbackListPreferenceDialogFragment {
    private Preference mPref;

    public static LeanbackListPreferenceDialogFragmentBase newInstanceMulti(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);

        final LeanbackListPreferenceDialogFragmentBase
                fragment = new LeanbackListPreferenceDialogFragmentBase();
        fragment.setArguments(args);

        return fragment;
    }

    public void setPreference(Preference pref) {
        mPref = pref;
    }

    @Override
    public DialogPreference getPreference() {
        return mPref != null ? (DialogPreference) mPref : super.getPreference();
    }
}
