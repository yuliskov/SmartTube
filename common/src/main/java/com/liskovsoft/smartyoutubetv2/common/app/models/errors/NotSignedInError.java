package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;

/**
 * Shown instead of all content when 'Hide all content if not logged in' is enabled and no account is selected.
 */
public class NotSignedInError implements ErrorFragmentData {
    private final Context mContext;

    public NotSignedInError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        AccountSettingsPresenter.instance(mContext).show();
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.content_hidden_not_signed);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.settings_accounts);
    }
}
