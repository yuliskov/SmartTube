package com.liskovsoft.leanbackassistant.search;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.liskovsoft.leanbackassistant.R;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.AppSchedulerProvider;
import com.liskovsoft.sharedutils.rx.SchedulerProvider;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.disposables.CompositeDisposable;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides global search on the app's movie service.<br/>
 * The assistant will query this provider for results.<br/>
 * Note: If you provide WatchAction feeds to Google, then you do not need this class.<br/>
 * You should still handle the playback intent and media controls in your fragment.<br/>
 * This class enables <a href="https://developer.android.com/training/tv/discovery/searchable.html">on-device search.</a>.
 */
public class VideoContentProvider extends ContentProvider {
    private static final String TAG = VideoContentProvider.class.getSimpleName();
    private static final int SEARCH_LIMIT = 20;

    // UriMatcher constant for search suggestions
    private static final int SEARCH_SUGGEST = 1;

    private UriMatcher mUriMatcher;

    private final String[] queryProjection = {
                BaseColumns._ID,
                MockDatabase.KEY_NAME,
                MockDatabase.KEY_DESCRIPTION,
                MockDatabase.KEY_ICON,
                MockDatabase.KEY_DATA_TYPE,
                MockDatabase.KEY_IS_LIVE,
                MockDatabase.KEY_VIDEO_WIDTH,
                MockDatabase.KEY_VIDEO_HEIGHT,
                MockDatabase.KEY_AUDIO_CHANNEL_CONFIG,
                MockDatabase.KEY_PURCHASE_PRICE,
                MockDatabase.KEY_RENTAL_PRICE,
                MockDatabase.KEY_RATING_STYLE,
                MockDatabase.KEY_RATING_SCORE,
                MockDatabase.KEY_PRODUCTION_YEAR,
                MockDatabase.KEY_COLUMN_DURATION,
                MockDatabase.KEY_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID
    };
    private CompositeDisposable mDisposable;
    private SchedulerProvider mSchedulerProvider;
    private static List<MediaItem> sCachedMediaItems = new ArrayList<>();
    private MediaGroup mSearch;

    @Override
    public boolean onCreate() {
        mUriMatcher = buildUriMatcher();

        return true;
    }

    private UriMatcher buildUriMatcher() {
        String authority = getAuthority();

        UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(authority, "/search/" + SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
        uriMatcher.addURI(authority, "/search/" + SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);

        return uriMatcher;
    }

    private String getAuthority() {
        String authority = null;

        if (getContext() != null) {
            authority = getContext().getResources().getString(R.string.search_authority);
            Log.d(TAG, "Authority found: " + authority);
        }

        return authority;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {

        Log.d(TAG, uri.toString());

        if (mUriMatcher.match(uri) == SEARCH_SUGGEST) {
            Log.d(TAG, "Search suggestions requested.");

            //String limitStr = uri.getQueryParameter("limit");
            //int limit = limitStr != null ? Integer.parseInt(limitStr) : SEARCH_LIMIT;
            return search(uri.getLastPathSegment(), SEARCH_LIMIT);
        } else {
            Log.d(TAG, "Unknown uri to query: " + uri);
            throw new IllegalArgumentException("Unknown Uri: " + uri);
        }
    }

    public static MediaItem findVideoWithId(int id) {
        if (sCachedMediaItems == null) {
            return null;
        }

        for (MediaItem video : sCachedMediaItems) {
            if (video != null && video.getId() == id) {
                return video;
            }
        }

        return null;
    }

    private Cursor search(String query, int limit) {
        MatrixCursor matrixCursor = new MatrixCursor(queryProjection);

        mSearch = YouTubeServiceManager.instance().getContentService().getSearch(query);

        if (mSearch != null) {
            List<MediaItem> mediaItems = mSearch.getMediaItems();

            Log.d(TAG, "Search result received: " + mediaItems);

            sCachedMediaItems.clear();

            apply(matrixCursor, mediaItems, limit);
        } else {
            Log.e(TAG, "Search is empty");
        }


        return matrixCursor;
    }

    private void nextSearch(MatrixCursor cursor, int limit) {
        mSearch = YouTubeServiceManager.instance().getContentService().continueGroup(mSearch);

        if (mSearch != null) {
            List<MediaItem> mediaItems = mSearch.getMediaItems();

            Log.d(TAG, "Next search result received: " + mediaItems);

            apply(cursor, mediaItems, limit);
        } else {
            Log.e(TAG, "Next search is empty");
        }
    }

    private void apply(MatrixCursor matrixCursor, List<MediaItem> mediaItems, int limit) {
        if (mediaItems != null) {
            int idx = 0;

            for (MediaItem mediaItem : mediaItems) {
                matrixCursor.addRow(convertVideoIntoRow(mediaItem));
                idx++;
            }

            sCachedMediaItems.addAll(mediaItems);

            if (idx < limit) {
                nextSearch(matrixCursor, limit - idx);
            }
        }
    }

    private SchedulerProvider getSchedulerProvider() {
        if (mSchedulerProvider == null) {
            mSchedulerProvider = new AppSchedulerProvider();
        }

        return mSchedulerProvider;
    }

    private CompositeDisposable getCompositeDisposable() {
        if (mDisposable == null) {
            mDisposable = new CompositeDisposable();
        }

        return mDisposable;
    }

    private Object[] convertVideoIntoRow(MediaItem mediaItem) {
        return new Object[] {
            mediaItem.getId(),
            mediaItem.getTitle(),
            mediaItem.getSecondTitle(),
            mediaItem.getCardImageUrl(),
            mediaItem.getContentType(),
            mediaItem.isLive(),
            mediaItem.getWidth(),
            mediaItem.getHeight(),
            mediaItem.getAudioChannelConfig(),
            mediaItem.getPurchasePrice(),
            mediaItem.getRentalPrice(),
            mediaItem.getRatingStyle(),
            mediaItem.getRatingScore(),
            mediaItem.getProductionDate(),
            mediaItem.getDurationMs(),
            "GLOBALSEARCH",
            mediaItem.getId()
        };
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        throw new UnsupportedOperationException("Insert is not implemented.");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        throw new UnsupportedOperationException("Delete is not implemented.");
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues contentValues,
            @Nullable String s,
            @Nullable String[] strings) {
        throw new UnsupportedOperationException("Update is not implemented.");
    }
}
