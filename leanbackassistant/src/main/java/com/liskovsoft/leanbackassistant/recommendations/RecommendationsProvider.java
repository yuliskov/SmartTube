package com.liskovsoft.leanbackassistant.recommendations;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.RequiresApi;

import com.liskovsoft.leanbackassistant.R;
import com.liskovsoft.leanbackassistant.media.Clip;
import com.liskovsoft.leanbackassistant.media.Playlist;
import com.liskovsoft.leanbackassistant.utils.AppUtil;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import okhttp3.Response;

@RequiresApi(21)
public class RecommendationsProvider {
    private static final String TAG = RecommendationsProvider.class.getSimpleName();
    private static final int MAX_RECOMMENDATIONS = 30;

    public static void createOrUpdateRecommendations(Context context, Playlist playlist) {
        if (playlist != null) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                int clipCounter = 0;
                for (Clip clip : playlist.getClips()) {
                    if (clipCounter++ > MAX_RECOMMENDATIONS) {
                        break;
                    }

                    Response response = OkHttpManager.instance().doGetRequest(clip.getCardImageUrl());

                    Bitmap image = null;

                    if (response.body() != null) {
                        image = BitmapFactory.decodeStream(response.body().byteStream());
                    }

                    Notification rec = new RecommendationBuilder()
                            .setContext(context)
                            .setDescription(clip.getDescription())
                            .setImage(image)
                            .setTitle(clip.getTitle())
                            .setSmallIcon(R.drawable.generic_channels)
                            .setIntent(AppUtil.getInstance(context).createAppPendingIntent(clip.getVideoUrl()))
                            .build();

                    notificationManager.notify(Integer.parseInt(clip.getClipId()), rec);

                    Log.d(TAG, "Posting recommendation: " + clip.getTitle());
                }
            }
        }
    }
}
