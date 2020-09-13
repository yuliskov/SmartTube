package com.liskovsoft.smartyoutubetv2.common.app.models.auth;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;

public class SignInData implements ErrorFragmentData {
    private final Context mContext;
    private final Header mHeader;

    public SignInData(Context context, Header header) {
        mContext = context;
        mHeader = header;
    }

    @Override
    public Header getHeader() {
        return mHeader;
    }

    @Override
    public void onAction() {
        ViewManager.instance(mContext).startView(SignInView.class);
    }

    @Override
    public String getMessage() {
        return mContext.getString(R.string.library_signin_title);
    }

    @Override
    public String getActionText() {
        return mContext.getString(R.string.library_signin_button_text);
    }
}
