package com.liskovsoft.smartyoutubetv2.common.app.models.errors;

public interface ErrorFragmentData {
    void onAction();
    String getMessage();
    String getActionText();
}
