package com.liskovsoft.smartyoutubetv2.common.app.views;

import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.util.List;

public interface AccountSelectionView {
    void update(List<Account> accounts);
    void close();
}
