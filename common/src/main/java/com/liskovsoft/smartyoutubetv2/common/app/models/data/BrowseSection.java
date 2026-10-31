package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.helpers.Helpers;

public class BrowseSection {
    public static final int TYPE_GRID = 0;
    public static final int TYPE_ROW = 1;
    public static final int TYPE_SETTINGS_GRID = 2;
    public static final int TYPE_MULTI_GRID = 3;
    public static final int TYPE_ERROR = 4;
    public static final int TYPE_SHORTS_GRID = 5;
    private static final int MAX_TITLE_LENGTH_CHARS = 30;
    private final int mId;
    private String mTitle;
    private final int mResId;
    private final String mIconUrl;
    private final boolean mIsAuthOnly;
    private final Object mData;
    private boolean mEnabled = true;
    private int mType;

    public BrowseSection(int id, String title, int type, int resId) {
        this(id, title, type, resId, false);
    }

    public BrowseSection(int id, String title, int type, String iconUrl) {
        this(id, title, type, iconUrl, false);
    }

    public BrowseSection(int id, String title, int type, String iconUrl, boolean isAuthOnly) {
        this(id, title, type, -1, iconUrl, isAuthOnly, null);
    }

    public BrowseSection(int id, String title, int type, String iconUrl, boolean isAuthOnly, Object data) {
        this(id, title, type, -1, iconUrl, isAuthOnly, data);
    }

    public BrowseSection(int id, String title, int type, int resId, boolean isAuthOnly) {
        this(id, title, type, resId, null, isAuthOnly, null);
    }

    public BrowseSection(int id, String title, int type, int resId, boolean isAuthOnly, Object data) {
        this(id, title, type, resId, null, isAuthOnly, data);
    }

    public BrowseSection(int id, String title, int type, int resId, String iconUrl, boolean isAuthOnly, Object data) {
        mId = id;
        mTitle = Helpers.abbreviate(title, MAX_TITLE_LENGTH_CHARS);
        mType = type;
        mResId = resId;
        mIconUrl = iconUrl;
        mIsAuthOnly = isAuthOnly;
        mData = data;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public int getId() {
        return mId;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public int getResId() {
        return mResId;
    }

    public String getIconUrl() {
        return mIconUrl;
    }

    public boolean isAuthOnly() {
        return mIsAuthOnly;
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public Object getData() {
        return mData;
    }

    /**
     * Check reserved ids range for default (built-in) sections
     */
    public boolean isDefault() {
        return mId < 30;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj instanceof BrowseSection && ((BrowseSection) obj).getId() == getId();
    }
}
