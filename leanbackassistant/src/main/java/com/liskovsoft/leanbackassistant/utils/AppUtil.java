package com.liskovsoft.leanbackassistant.utils;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.liskovsoft.sharedutils.configparser.AssetPropertyParser2;
import com.liskovsoft.sharedutils.configparser.ConfigParser;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.GlobalConstants;

public class AppUtil {
    private final Context mContext;
    private ConfigParser mParser;
    @SuppressLint("StaticFieldLeak")
    private static AppUtil sInstance;

    private AppUtil(Context context) {
        mContext = context;
    }

    public static AppUtil getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUtil(context);
        }

        return sInstance;
    }

    public String getBootstrapClassName() {
        ConfigParser parser = getParser();

        return parser.get("app_bootsrap_class_name");
    }

    public String getAppPackageName() {
        return mContext.getPackageName();
    }

    private ConfigParser getParser() {
        if (mParser == null) {
            mParser = new AssetPropertyParser2(mContext, "common.properties");
        }

        return mParser;
    }

    public Intent createAppIntent(String url) {
        if (url == null) {
            return null;
        }

        String mainActivityName = AppInfoHelpers.getMainActivityName(mContext);

        if (mainActivityName == null) {
            return null;
        }

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.setClassName(mContext.getPackageName(), mainActivityName);
        intent.putExtra(GlobalConstants.ATV_INTENT, true);

        return intent;
    }

    public PendingIntent createAppPendingIntent(String url) {
        Intent detailsIntent = createAppIntent(url);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
        //stackBuilder.addParentStack(DetailsActivity.class);
        stackBuilder.addNextIntent(detailsIntent);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= 23) {
            // IllegalArgumentException fix: Targeting S+ (version 31 and above) requires that one of FLAG_IMMUTABLE
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent intent = stackBuilder.getPendingIntent(0, flags);
        return intent;
    }
}
