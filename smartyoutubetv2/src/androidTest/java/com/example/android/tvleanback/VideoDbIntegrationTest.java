package com.example.android.tvleanback;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import com.example.android.tvleanback.data.FetchVideoService;
import com.example.android.tvleanback.data.VideoContract;
import com.example.android.tvleanback.data.VideoContract.VideoEntry;
import com.example.android.tvleanback.data.VideoDbBuilder;
import com.example.android.tvleanback.data.VideoDbHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class VideoDbIntegrationTest {

    private Context mContext;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void resetAndRedownloadDatabase() throws InterruptedException {
        VideoDbHelper mVideoDbHelper = new VideoDbHelper(mContext);
        // Clear database by downgrading
        mVideoDbHelper.onDowngrade(mVideoDbHelper.getReadableDatabase(), 0, 0);
        String[] queryColumns = new String[] {
                VideoContract.VideoEntry._ID,
                VideoContract.VideoEntry.COLUMN_NAME,
                VideoContract.VideoEntry.COLUMN_CATEGORY,
                VideoContract.VideoEntry.COLUMN_DESC,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_STUDIO,
        };
        Cursor mCursor = mVideoDbHelper.getReadableDatabase().query(
                VideoContract.VideoEntry.TABLE_NAME,
                queryColumns,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(mCursor.getCount()).isEqualTo(0); // Confirm database is empty
        mCursor.close();
        try {
            mContext.startService(new Intent(mContext, FetchVideoService.class));
            Thread.sleep(1000*30);
            mCursor = mVideoDbHelper.getReadableDatabase().query(
                    VideoContract.VideoEntry.TABLE_NAME,
                    queryColumns,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            assertThat(mCursor.getCount()).isNotEqualTo(0); // Confirm database is no longer empty
            mCursor.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new InterruptedException("Thread was interrupted, cannot check download");
        }
    }

    @Test
    public void resetAndInsertLocalVideos() throws JSONException {
        VideoDbHelper mVideoDbHelper = new VideoDbHelper(mContext);
        mVideoDbHelper.onDowngrade(mVideoDbHelper.getReadableDatabase(), 0, 0);
        String[] queryColumns = new String[] {
                VideoContract.VideoEntry._ID,
                VideoContract.VideoEntry.COLUMN_NAME,
                VideoContract.VideoEntry.COLUMN_CATEGORY,
                VideoContract.VideoEntry.COLUMN_DESC,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_STUDIO,
        };
        Cursor mCursor = mVideoDbHelper.getReadableDatabase().query(
                VideoContract.VideoEntry.TABLE_NAME,
                queryColumns,
                null,
                null,
                null,
                null,
                null
        );
        assertThat(mCursor.getCount()).isEqualTo(0); // Confirm database is empty
        mCursor.close();

        // Create some test videos
        JSONArray mediaArray = new JSONArray();
        JSONObject video1 = new JSONObject();
        video1.put(VideoDbBuilder.TAG_TITLE, "New Dad")
                .put(VideoDbBuilder.TAG_DESCRIPTION, "Google+ Instant Upload backs up your photos")
                .put(VideoDbBuilder.TAG_STUDIO, "Google+")
                .put(VideoDbBuilder.TAG_SOURCES,
                        new JSONArray(Collections.singletonList("https://google.com")));
        JSONObject video2 = new JSONObject();
        video2.put(VideoDbBuilder.TAG_TITLE, "Pet Dog")
                .put(VideoDbBuilder.TAG_DESCRIPTION, "Google+ lets you share videos of your pets")
                .put(VideoDbBuilder.TAG_STUDIO, "Google+")
                .put(VideoDbBuilder.TAG_SOURCES,
                        new JSONArray(Collections.singletonList("https://youtube.com")));
        mediaArray.put(video1);
        mediaArray.put(video2);
        JSONObject myMediaGooglePlus = new JSONObject();
        myMediaGooglePlus.put(VideoDbBuilder.TAG_CATEGORY, "Google+")
                .put(VideoDbBuilder.TAG_MEDIA, mediaArray);
        JSONObject myMedia = new JSONObject();
        JSONArray mediaCategories = new JSONArray();
        mediaCategories.put(myMediaGooglePlus);
        myMedia.put(VideoDbBuilder.TAG_GOOGLE_VIDEOS, mediaCategories);

        VideoDbBuilder videoDbBuilder = new VideoDbBuilder(mContext);
        List<ContentValues> contentValuesList = videoDbBuilder.buildMedia(myMedia);
        ContentValues[] downloadedVideoContentValues =
                contentValuesList.toArray(new ContentValues[contentValuesList.size()]);
        mContext.getContentResolver().bulkInsert(VideoContract.VideoEntry.CONTENT_URI,
                downloadedVideoContentValues);

        // Test our makeshift database
        mCursor = mVideoDbHelper.getReadableDatabase().query(
                VideoContract.VideoEntry.TABLE_NAME,
                queryColumns,
                null,
                null,
                null,
                null,
                null
        );
        assertThat(mCursor.getCount()).isEqualTo(2); // Confirm database was populated
        mCursor.close();

        mCursor = mVideoDbHelper.getReadableDatabase().query(
                VideoContract.VideoEntry.TABLE_NAME,
                queryColumns,
                VideoContract.VideoEntry.COLUMN_NAME+" = ?",
                new String[] {"New Dad"},
                null,
                null,
                null
        );
        assertThat(mCursor.moveToFirst()).isTrue();
        String studio = mCursor.getString(mCursor.getColumnIndexOrThrow(VideoEntry.COLUMN_STUDIO));
        assertThat(studio).isEqualTo("Google+");
        mCursor.close();
    }

    @Test
    public void resetAndInsertOnlineVideos() throws JSONException, IOException {
        VideoDbHelper mVideoDbHelper = new VideoDbHelper(mContext);
        mVideoDbHelper.onDowngrade(mVideoDbHelper.getReadableDatabase(), 0, 0);
        String[] queryColumns = new String[] {
                VideoContract.VideoEntry._ID,
                VideoContract.VideoEntry.COLUMN_NAME,
                VideoContract.VideoEntry.COLUMN_CATEGORY,
                VideoContract.VideoEntry.COLUMN_DESC,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_STUDIO,
        };
        Cursor mCursor = mVideoDbHelper.getReadableDatabase().query(
                VideoContract.VideoEntry.TABLE_NAME,
                queryColumns,
                null,
                null,
                null,
                null,
                null
        );
        assertThat(mCursor.getCount()).isEqualTo(0); // Confirm database is empty
        mCursor.close();

        // Create some test videos
        VideoDbBuilder videoDbBuilder = new VideoDbBuilder(mContext);
        List<ContentValues> contentValuesList =
                videoDbBuilder.fetch(mContext.getResources().getString(R.string.catalog_url));
        // Insert into database
        ContentValues[] downloadedVideoContentValues =
                contentValuesList.toArray(new ContentValues[contentValuesList.size()]);
        mContext.getContentResolver().bulkInsert(VideoContract.VideoEntry.CONTENT_URI,
                downloadedVideoContentValues);

        // Test our makeshift database
        mCursor = mVideoDbHelper.getReadableDatabase().query(
                VideoContract.VideoEntry.TABLE_NAME,
                queryColumns,
                null,
                null,
                null,
                null,
                null
        );
        assertThat(mCursor.getCount()).isGreaterThan(0); // Confirm database was populated
        mCursor.close();
    }
}
