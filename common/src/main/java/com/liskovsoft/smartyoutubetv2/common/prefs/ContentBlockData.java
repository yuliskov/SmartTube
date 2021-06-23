package com.liskovsoft.smartyoutubetv2.common.prefs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.data.SponsorSegment;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ContentBlockData {
    public static final String SPONSOR_BLOCK_NAME = "SponsorBlock";
    public static final String SPONSOR_BLOCK_URL = "https://sponsor.ajay.app";
    public static final int NOTIFICATION_TYPE_NONE = 0;
    public static final int NOTIFICATION_TYPE_TOAST = 1;
    public static final int NOTIFICATION_TYPE_DIALOG = 2;
    private static final String CONTENT_BLOCK_DATA = "content_block_data";
    @SuppressLint("StaticFieldLeak")
    private static ContentBlockData sInstance;
    private final Context mContext;
    private final AppPrefs mAppPrefs;
    private boolean mIsSponsorBlockEnabled;
    private final Set<String> mCategories = new HashSet<>();
    private int mNotificationType;

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

    public int getNotificationType() {
        return mNotificationType;
    }

    public void setNotificationType(int type) {
        mNotificationType = type;
        persistData();
    }

    private void restoreState() {
        String data = mAppPrefs.getData(CONTENT_BLOCK_DATA);

        String[] split = Helpers.splitObjectLegacy(data);

        mIsSponsorBlockEnabled = Helpers.parseBoolean(split, 0, true);
        mNotificationType = Helpers.parseInt(split, 1, NOTIFICATION_TYPE_TOAST);
        String categories = Helpers.parseStr(split, 2);

        if (categories != null) {
            String[] categoriesArr = Helpers.splitArray(categories);

            mCategories.clear();

            mCategories.addAll(Arrays.asList(categoriesArr));
        } else {
            mCategories.clear();

            mCategories.addAll(Arrays.asList(
                        SponsorSegment.CATEGORY_SPONSOR,
                        SponsorSegment.CATEGORY_INTRO,
                        SponsorSegment.CATEGORY_OUTRO,
                        SponsorSegment.CATEGORY_INTERACTION,
                        SponsorSegment.CATEGORY_SELF_PROMO,
                        SponsorSegment.CATEGORY_MUSIC_OFF_TOPIC
                    )
            );
        }
    }

    private void persistData() {
        String categories = Helpers.mergeArray(mCategories.toArray());

        mAppPrefs.setData(CONTENT_BLOCK_DATA, Helpers.mergeObject(mIsSponsorBlockEnabled, mNotificationType, categories));
    }
}
