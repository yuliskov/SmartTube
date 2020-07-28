package com.example.android.tvleanback;

import android.content.ContentValues;

import com.example.android.tvleanback.data.VideoContract;
import com.example.android.tvleanback.data.VideoDbBuilder;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, manifest = "src/main/AndroidManifest.xml")
public class VideoDbUnitTest {
    private static final String TAG = "VideoDbTest";

    public VideoDbUnitTest() { }

    @Test
    public void getVideosFromLocalJson() throws JSONException {
        // Create some test videos
        JSONArray mediaArray = new JSONArray();
        JSONObject video1 = new JSONObject();
        video1.put(VideoDbBuilder.TAG_TITLE, "New Dad")
                .put(VideoDbBuilder.TAG_DESCRIPTION, "Google+ Instant Upload backs up your photos")
                .put(VideoDbBuilder.TAG_STUDIO, "Google+")
                .put(VideoDbBuilder.TAG_SOURCES,
                    new JSONArray().put("http://www.example.com/new_dad.mp4"));
        mediaArray.put(video1);

        JSONObject video2 = new JSONObject();
        video2.put(VideoDbBuilder.TAG_TITLE, "Pet Dog")
                .put(VideoDbBuilder.TAG_DESCRIPTION, "Google+ lets you share videos of your pets")
                .put(VideoDbBuilder.TAG_STUDIO, "Google+")
                .put(VideoDbBuilder.TAG_SOURCES,
                    new JSONArray().put("http://www.example.com/pet_dog.mp4"));
        mediaArray.put(video2);

        JSONObject myMediaGooglePlus = new JSONObject();
        myMediaGooglePlus.put(VideoDbBuilder.TAG_CATEGORY, "Google+")
                .put(VideoDbBuilder.TAG_MEDIA, mediaArray);
        JSONObject myMedia = new JSONObject();
        JSONArray mediaCategories = new JSONArray();
        mediaCategories.put(myMediaGooglePlus);
        myMedia.put(VideoDbBuilder.TAG_GOOGLE_VIDEOS, mediaCategories);

        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.buildMedia(myMedia);
        Assert.assertEquals(
            "Google+",
            contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_CATEGORY)
        );
        Assert.assertEquals(
            "Google+",
            contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_CATEGORY)
        );
        Assert.assertEquals(
            "Google+",
            contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_STUDIO)
        );
        Assert.assertEquals(
            "Google+",
            contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_STUDIO)
        );
        Assert.assertEquals(
            "New Dad",
            contentValuesList.get(0).getAsString(VideoContract.VideoEntry.COLUMN_NAME)
        );
        Assert.assertEquals(
            "Pet Dog",
            contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_NAME)
        );
        Assert.assertEquals(
            "Google+ lets you share videos of your pets",
            contentValuesList.get(1).getAsString(VideoContract.VideoEntry.COLUMN_DESC)
        );
    }

    @Test
    public void getVideosFromServer() throws IOException, JSONException {
        String serverUrl = "https://storage.googleapis.com/android-tv/android_tv_videos_new.json";
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder();
        List<ContentValues> contentValuesList = videoDbBuilder.fetch(serverUrl);
        Assert.assertTrue(contentValuesList.size() > 0);
        Assert.assertTrue(!contentValuesList.get(0)
            .getAsString(VideoContract.VideoEntry.COLUMN_NAME).isEmpty());
    }
}
