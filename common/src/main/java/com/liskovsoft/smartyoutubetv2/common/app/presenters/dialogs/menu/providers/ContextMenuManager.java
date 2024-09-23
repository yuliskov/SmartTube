package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.providers;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContextMenuManager {
    private final Context mContext;
    private final ArrayList<ContextMenuProvider> mProviders;

    public ContextMenuManager(Context context) {
        mContext = context;
        mProviders = new ArrayList<>();
        mProviders.add(new SubscriptionGroupMenuProvider(context, 0));
    }

    public List<ContextMenuProvider> getProviders() {
        return Collections.unmodifiableList(mProviders);
    }
}
