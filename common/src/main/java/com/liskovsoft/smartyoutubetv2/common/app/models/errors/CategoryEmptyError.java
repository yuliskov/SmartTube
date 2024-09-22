package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter;

public class CategoryEmptyError implements ErrorFragmentData {
    private final Context mContext;

    public CategoryEmptyError(Context context) {
        mContext = context;
    }

    @Override
    public void onAction() {
        YTSignInPresenter.instance(mContext).start();
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.msg_cant_load_content);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.action_signin);
    }
}
