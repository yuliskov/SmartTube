package com.liskovsoft.smartyoutubetv2.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoServiceUnsigned;
import com.liskovsoft.youtubeapi.videoinfo.models.VideoInfo;
import com.liskovsoft.youtubeapi.videoinfo.models.formats.AdaptiveVideoFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UrlProvider extends ContentProvider {
    private static final String TAG = "UrlProvider";
    private final String [] COLUMNS = {"video_url", "audio_url"};

    public UrlProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        // TODO: Implement this to handle requests for the MIME type of the data
        // at the given URI.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO: Implement this to handle requests to insert a new row.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate() called");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final long startTime = System.currentTimeMillis();
        Log.d(TAG, "query() called with: uri = [" + uri + "], projection = [" + projection + "], selection = [" + selection + "], selectionArgs = [" + selectionArgs + "], sortOrder = [" + sortOrder + "]");
        String videoPageUrl = uri.getQueryParameter("url");
        VideoInfo videoInfo = VideoInfoServiceUnsigned.instance().getVideoInfo(Uri.parse(videoPageUrl).getQueryParameter("v"), "");
        List<AdaptiveVideoFormat> formats = videoInfo.getAdaptiveFormats();
        String videoUrl = null;
        String audioUrl = null;
        if (!TextUtils.isEmpty(videoInfo.getDashManifestUrl())) {
            videoUrl = videoInfo.getDashManifestUrl();
            Log.d(TAG, "query: use dash manifest");
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                formats = formats.stream()
                        .filter(adaptiveVideoFormat -> !adaptiveVideoFormat.getMimeType().contains("av01"))
                        .filter(adaptiveVideoFormat -> {
                            int maxVideoHeight = 2160;
                            if (uri.getQueryParameter("max_video_height") != null) {
                                maxVideoHeight = Integer.parseInt(uri.getQueryParameter("max_video_height"));
                            }
                            if (adaptiveVideoFormat.getSize() != null) {
                                return Integer.parseInt(adaptiveVideoFormat.getSize().split("x")[1]) <= maxVideoHeight;
                            }
                            return true;
                        })
                        .sorted((o1, o2) -> o2.getBitrate() - o1.getBitrate())
                        .collect(Collectors.toList());
            }
            for (AdaptiveVideoFormat format : formats) {
                Log.d(TAG, "query: format=" + format);
                if (format.getMimeType().startsWith("video") && videoUrl == null) {
                    videoUrl = format.getUrl();
                } else if (format.getMimeType().startsWith("audio") && audioUrl == null) {
                    audioUrl = format.getUrl();
                }
            }
        }

        final MatrixCursor cursor = new MatrixCursor(COLUMNS);
        ArrayList<String> values = new ArrayList<>(COLUMNS.length);
        values.add(videoUrl);
        values.add(audioUrl);
        cursor.addRow(values);
        Log.d(TAG, "videoUrl=" + videoUrl);
        Log.d(TAG, "audioUrl=" + audioUrl);
        Log.d(TAG, "query elapsed:  " + (System.currentTimeMillis() - startTime));
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}