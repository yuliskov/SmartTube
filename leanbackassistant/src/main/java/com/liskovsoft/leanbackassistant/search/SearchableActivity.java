package com.liskovsoft.leanbackassistant.search;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.liskovsoft.googlecommon.common.helpers.ServiceHelper;
import com.liskovsoft.leanbackassistant.utils.AppUtil;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import static androidx.core.content.IntentCompat.EXTRA_START_PLAYBACK;

public class SearchableActivity extends Activity {
    private static final String TAG = "SearchableActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Search data " + getIntent().getData());

        if (getIntent() != null && getIntent().getData() != null) {
            Uri uri = getIntent().getData();

            if (uri.getLastPathSegment() != null) {
                int id = Helpers.parseInt(uri.getLastPathSegment());

                boolean startPlayback = getIntent().getBooleanExtra(EXTRA_START_PLAYBACK, false);
                Log.d(TAG, "Should start playback? " + (startPlayback ? "yes" : "no"));

                String url = obtainVideoOrChannelUrl(id);

                if (url != null) {
                    Intent intent = AppUtil.getInstance(this).createAppIntent(url);
                    Log.d(TAG, "Starting search intent: " + intent);

                    startActivity(intent);
                } else {
                    Log.e(TAG, "Cannot find video id inside the url: %s", uri);
                }
            }
        }

        finish();
    }

    private String obtainVideoOrChannelUrl(int id) {
        MediaItem video = VideoContentProvider.findVideoWithId(id);

        if (video != null) {
            return video.getVideoId() != null ? ServiceHelper.videoIdToFullUrl(video.getVideoId()) : ServiceHelper.channelIdToFullUrl(video.getChannelId());
        }

        return null;
    }
}
