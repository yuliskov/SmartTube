package com.liskovsoft.smartyoutubetv2.common.app.views;

public interface SignInView {
    void showCode(String userCode, String signInUrl);
    void showCode(String userCode, String signInUrl, String fullSignInUrl);
    void close();
}
