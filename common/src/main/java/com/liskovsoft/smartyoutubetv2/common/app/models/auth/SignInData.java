package com.liskovsoft.smartyoutubetv2.common.app.models.auth;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;

public class SignInData implements ErrorFragmentData {
    private final Header mHeader;

    public SignInData(Header header) {
        mHeader = header;
    }

    @Override
    public Header getHeader() {
        return mHeader;
    }
}
