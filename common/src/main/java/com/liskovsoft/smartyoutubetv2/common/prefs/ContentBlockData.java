package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ContentBlockData {
    private static final String CONTENT_BLOCK_DATA = "content_block_data";
    @SuppressLint("StaticFieldLeak")
    private static ContentBlockData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsSponsorBlockEnabled;
    private boolean mIsConfirmOnSkipEnabled;
    private final Set<String> mCategories = new HashSet<>();

    private ContentBlockData(Context context) {
        mContext = context;
        mAppPrefs = AppPrefs.instance(mContext);
        restoreState();
    }

    public static ContentBlockData instance(Context context) {
        if (sInstance == null) {
            sInstance = new ContentBlockData(context.getApplicationContext());
        }

        return sInstance;
    }

    public boolean isSponsorBlockEnabled() {
        return mIsSponsorBlockEnabled;
    }

    public void setSponsorBlockEnabled(boolean enabled) {
        mIsSponsorBlockEnabled = enabled;
        persistData();
    }

    public boolean isConfirmOnSkipEnabled() {
        return mIsConfirmOnSkipEnabled;
    }

    public void setConfirmOnSkipEnabled(boolean enabled) {
        mIsConfirmOnSkipEnabled = enabled;
        persistData();
    }

    public Set<String> getCategories() {
        return mCategories;
    }

    public void addCategory(String categoryKey) {
        mCategories.add(categoryKey);
        persistData();
    }

    public void removeCategory(String categoryKey) {
        mCategories.remove(categoryKey);
        persistData();
    }

    private void restoreState() {
        String data = mAppPrefs.getData(CONTENT_BLOCK_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsSponsorBlockEnabled = Helpers.parseBoolean(split, 0, false);
        mIsConfirmOnSkipEnabled = Helpers.parseBoolean(split, 1, false);
        String categories = Helpers.parseStr(split, 2);

        if (categories != null) {
            String[] categoriesArr = Helpers.splitArray(categories);

            mCategories.clear();

            mCategories.addAll(Arrays.asList(categoriesArr));
        }
    }

    private void persistData() {
        String categories = Helpers.mergeArray(mCategories.toArray());

        mAppPrefs.setData(CONTENT_BLOCK_DATA, Helpers.mergeObject(mIsSponsorBlockEnabled, mIsConfirmOnSkipEnabled, categories));
    }
}
