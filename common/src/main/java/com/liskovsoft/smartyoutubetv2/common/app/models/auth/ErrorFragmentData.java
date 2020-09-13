package com.liskovsoft.smartyoutubetv2.common.app.models.auth;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Header;

public interface ErrorFragmentData {
    Header getHeader();
    void onAction();
    String getMessage();
    String getActionText();
}
