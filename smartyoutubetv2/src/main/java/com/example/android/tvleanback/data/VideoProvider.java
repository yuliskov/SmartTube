/*
 * Copyright (c) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.tvleanback.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import androidx.annotation.NonNull;

import java.util.HashMap;

/**
 * VideoProvider is a ContentProvider that provides videos for the rest of applications.
 */
public class VideoProvider extends ContentProvider {
    private static final UriMatcher sUriMatcher = buildUriMatcher();
    private VideoDbHelper mOpenHelper;

    // These codes are returned from sUriMatcher#match when the respective Uri matches.
    private static final int VIDEO = 1;
    private static final int VIDEO_WITH_CATEGORY = 2;
    private static final int SEARCH_SUGGEST = 3;
    private static final int REFRESH_SHORTCUT = 4;

    private static final SQLiteQueryBuilder sVideosContainingQueryBuilder;
    private static final String[] sVideosContainingQueryColumns;
    private static final HashMap<String, String> sColumnMap = buildColumnMap();
    private ContentResolver mContentResolver;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        mContentResolver = context.getContentResolver();
        mOpenHelper = new VideoDbHelper(context);
        return true;
    }

    static {
        sVideosContainingQueryBuilder = new SQLiteQueryBuilder();
        sVideosContainingQueryBuilder.setTables(VideoContract.VideoEntry.TABLE_NAME);
        sVideosContainingQueryBuilder.setProjectionMap(sColumnMap);
        sVideosContainingQueryColumns = new String[]{
                VideoContract.VideoEntry._ID,
                VideoContract.VideoEntry.COLUMN_NAME,
                VideoContract.VideoEntry.COLUMN_CATEGORY,
                VideoContract.VideoEntry.COLUMN_DESC,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_STUDIO,
                VideoContract.VideoEntry.COLUMN_CARD_IMG,
                VideoContract.VideoEntry.COLUMN_CONTENT_TYPE,
                VideoContract.VideoEntry.COLUMN_IS_LIVE,
                VideoContract.VideoEntry.COLUMN_VIDEO_WIDTH,
                VideoContract.VideoEntry.COLUMN_VIDEO_HEIGHT,
                VideoContract.VideoEntry.COLUMN_AUDIO_CHANNEL_CONFIG,
                VideoContract.VideoEntry.COLUMN_PURCHASE_PRICE,
                VideoContract.VideoEntry.COLUMN_RENTAL_PRICE,
                VideoContract.VideoEntry.COLUMN_RATING_STYLE,
                VideoContract.VideoEntry.COLUMN_RATING_SCORE,
                VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR,
                VideoContract.VideoEntry.COLUMN_DURATION,
                VideoContract.VideoEntry.COLUMN_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID
        };
    }

    static UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = VideoContract.CONTENT_AUTHORITY;

        // For each type of URI to add, create a corresponding code.
        matcher.addURI(authority, VideoContract.PATH_VIDEO, VIDEO);
        matcher.addURI(authority, VideoContract.PATH_VIDEO + "/*", VIDEO_WITH_CATEGORY);

        // Search related URIs.
        matcher.addURI(authority, "search/" + SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
        matcher.addURI(authority, "search/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);
        return matcher;
    }

    private Cursor getSuggestions(String query) {
        query = query.toLowerCase();
        return sVideosContainingQueryBuilder.query(
                mOpenHelper.getReadableDatabase(),
                sVideosContainingQueryColumns,
                VideoContract.VideoEntry.COLUMN_NAME + " LIKE ? OR " +
                        VideoContract.VideoEntry.COLUMN_DESC + " LIKE ?",
                new String[]{"%" + query + "%", "%" + query + "%"},
                null,
                null,
                null
        );
    }

    private static HashMap<String, String> buildColumnMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put(VideoContract.VideoEntry._ID, VideoContract.VideoEntry._ID);
        map.put(VideoContract.VideoEntry.COLUMN_NAME, VideoContract.VideoEntry.COLUMN_NAME);
        map.put(VideoContract.VideoEntry.COLUMN_DESC, VideoContract.VideoEntry.COLUMN_DESC);
        map.put(VideoContract.VideoEntry.COLUMN_CATEGORY, VideoContract.VideoEntry.COLUMN_CATEGORY);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEO_URL,
                VideoContract.VideoEntry.COLUMN_VIDEO_URL);
        map.put(VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL,
                VideoContract.VideoEntry.COLUMN_BG_IMAGE_URL);
        map.put(VideoContract.VideoEntry.COLUMN_CARD_IMG, VideoContract.VideoEntry.COLUMN_CARD_IMG);
        map.put(VideoContract.VideoEntry.COLUMN_STUDIO, VideoContract.VideoEntry.COLUMN_STUDIO);
        map.put(VideoContract.VideoEntry.COLUMN_CONTENT_TYPE,
                VideoContract.VideoEntry.COLUMN_CONTENT_TYPE);
        map.put(VideoContract.VideoEntry.COLUMN_IS_LIVE, VideoContract.VideoEntry.COLUMN_IS_LIVE);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEO_WIDTH,
                VideoContract.VideoEntry.COLUMN_VIDEO_WIDTH);
        map.put(VideoContract.VideoEntry.COLUMN_VIDEO_HEIGHT,
                VideoContract.VideoEntry.COLUMN_VIDEO_HEIGHT);
        map.put(VideoContract.VideoEntry.COLUMN_AUDIO_CHANNEL_CONFIG,
                VideoContract.VideoEntry.COLUMN_AUDIO_CHANNEL_CONFIG);
        map.put(VideoContract.VideoEntry.COLUMN_PURCHASE_PRICE,
                VideoContract.VideoEntry.COLUMN_PURCHASE_PRICE);
        map.put(VideoContract.VideoEntry.COLUMN_RENTAL_PRICE,
                VideoContract.VideoEntry.COLUMN_RENTAL_PRICE);
        map.put(VideoContract.VideoEntry.COLUMN_RATING_STYLE,
                VideoContract.VideoEntry.COLUMN_RATING_STYLE);
        map.put(VideoContract.VideoEntry.COLUMN_RATING_SCORE,
                VideoContract.VideoEntry.COLUMN_RATING_SCORE);
        map.put(VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR,
                VideoContract.VideoEntry.COLUMN_PRODUCTION_YEAR);
        map.put(VideoContract.VideoEntry.COLUMN_DURATION, VideoContract.VideoEntry.COLUMN_DURATION);
        map.put(VideoContract.VideoEntry.COLUMN_ACTION, VideoContract.VideoEntry.COLUMN_ACTION);
        map.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, VideoContract.VideoEntry._ID + " AS " +
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
        map.put(SearchManager.SUGGEST_COLUMN_SHORTCUT_ID,
                VideoContract.VideoEntry._ID + " AS " + SearchManager.SUGGEST_COLUMN_SHORTCUT_ID);
        return map;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        Cursor retCursor;
        switch (sUriMatcher.match(uri)) {
            case SEARCH_SUGGEST: {
                String rawQuery = "";
                if (selectionArgs != null && selectionArgs.length > 0) {
                    rawQuery = selectionArgs[0];
                }
                retCursor = getSuggestions(rawQuery);
                break;
            }
            case VIDEO: {
                retCursor = mOpenHelper.getReadableDatabase().query(
                        VideoContract.VideoEntry.TABLE_NAME,
                        projection,
                        selection,
                        selectionArgs,
                        null,
                        null,
                        sortOrder
                );
                break;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        retCursor.setNotificationUri(mContentResolver, uri);
        return retCursor;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            // The application is querying the db for its own contents.
            case VIDEO_WITH_CATEGORY:
                return VideoContract.VideoEntry.CONTENT_TYPE;
            case VIDEO:
                return VideoContract.VideoEntry.CONTENT_TYPE;

            // The Android TV global search is querying our app for relevant content.
            case SEARCH_SUGGEST:
                return SearchManager.SUGGEST_MIME_TYPE;
            case REFRESH_SHORTCUT:
                return SearchManager.SHORTCUT_MIME_TYPE;

            // We aren't sure what is being asked of us.
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        final Uri returnUri;
        final int match = sUriMatcher.match(uri);

        switch (match) {
            case VIDEO: {
                long _id = mOpenHelper.getWritableDatabase().insert(
                        VideoContract.VideoEntry.TABLE_NAME, null, values);
                if (_id > 0) {
                    returnUri = VideoContract.VideoEntry.buildVideoUri(_id);
                } else {
                    throw new SQLException("Failed to insert row into " + uri);
                }
                break;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        mContentResolver.notifyChange(uri, null);
        return returnUri;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        final int rowsDeleted;

        if (selection == null) {
            throw new UnsupportedOperationException("Cannot delete without selection specified.");
        }

        switch (sUriMatcher.match(uri)) {
            case VIDEO: {
                rowsDeleted = mOpenHelper.getWritableDatabase().delete(
                        VideoContract.VideoEntry.TABLE_NAME, selection, selectionArgs);
                break;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        if (rowsDeleted != 0) {
            mContentResolver.notifyChange(uri, null);
        }

        return rowsDeleted;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        final int rowsUpdated;

        switch (sUriMatcher.match(uri)) {
            case VIDEO: {
                rowsUpdated = mOpenHelper.getWritableDatabase().update(
                        VideoContract.VideoEntry.TABLE_NAME, values, selection, selectionArgs);
                break;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }

        if (rowsUpdated != 0) {
            mContentResolver.notifyChange(uri, null);
        }

        return rowsUpdated;
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        switch (sUriMatcher.match(uri)) {
            case VIDEO: {
                final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
                int returnCount = 0;

                db.beginTransaction();
                try {
                    for (ContentValues value : values) {
                        long _id = db.insertWithOnConflict(VideoContract.VideoEntry.TABLE_NAME,
                                null, value, SQLiteDatabase.CONFLICT_REPLACE);
                        if (_id != -1) {
                            returnCount++;
                        }
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }

                mContentResolver.notifyChange(uri, null);
                return returnCount;
            }
            default: {
                return super.bulkInsert(uri, values);
            }
        }
    }
}
