package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.base;

import androidx.leanback.preference.LeanbackPreferenceDialogFragment;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;

public class LeanbackPreferenceDialogFragmentBase extends LeanbackPreferenceDialogFragment {
    private Preference mPref;

    public void setPreference(Preference pref) {
        mPref = pref;
    }

    @Override
    public DialogPreference getPreference() {
        return mPref != null ? (DialogPreference) mPref : super.getPreference();
    }
}
