package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;

public class CategoryEmptyError implements ErrorFragmentData {
    private final Context mContext;
    private final Throwable mError;

    public CategoryEmptyError(Context context, Throwable error) {
        mContext = context;
        mError = error;
    }

    @Override
    public void onAction() {
        YTSignInPresenter.instance(mContext).start();
    }

    @Override
    public String getMessage() {
        String result = mContext.getString(R.string.msg_cant_load_content);
        if (!Helpers.containsAny(mError.getMessage(), "fromNullable result is null")) {
            result = mError.getMessage();
        }
        return result;
    }

    @Override
    public String getActionText() {
        return Helpers.startsWith(mError.getMessage(), "AuthErrorResponse") ? mContext.getString(R.string.action_signin) : null;
    }
}
