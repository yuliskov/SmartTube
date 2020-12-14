package com.liskovsoft.smartyoutubetv2.common.app.models.update;

public interface IAppUpdateManager {
    void start(boolean forceCheck);
    void enableUpdateCheck(boolean b);
    boolean isUpdateCheckEnabled();
    void unhold();
}
