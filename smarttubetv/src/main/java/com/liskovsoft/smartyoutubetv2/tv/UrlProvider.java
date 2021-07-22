package com.liskovsoft.smartyoutubetv2.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

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
    private final static int MAX_VIDEO_HEIGHT = 2160;
    private final static String MAX_VIDEO_HEIGHT_PARAM = "max_video_height";

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
        final String videoPageUrl = uri.getQueryParameter("url");
        final VideoInfo videoInfo = VideoInfoServiceUnsigned.instance()
                .getVideoInfo(Uri.parse(videoPageUrl).getQueryParameter("v"), "");
        @Nullable List<AdaptiveVideoFormat> formats = videoInfo.getAdaptiveFormats();
        String videoUrl = null;
        String audioUrl = null;
        if (!TextUtils.isEmpty(videoInfo.getDashManifestUrl())) {
            videoUrl = videoInfo.getDashManifestUrl();
            Log.d(TAG, "using dash manifest");
        } else if (formats != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                formats = formats.stream()
                        .filter(adaptiveVideoFormat -> !adaptiveVideoFormat.getMimeType().contains("av01"))
                        .filter(adaptiveVideoFormat -> {
                            int maxVideoHeight = MAX_VIDEO_HEIGHT;
                            if (uri.getQueryParameter(MAX_VIDEO_HEIGHT_PARAM) != null) {
                                maxVideoHeight = Integer.parseInt(uri.getQueryParameter(MAX_VIDEO_HEIGHT_PARAM));
                            }
                            if (adaptiveVideoFormat.getSize() != null) {
                                return Integer.parseInt(adaptiveVideoFormat.getSize().split("x")[1]) <= maxVideoHeight;
                            }
                            return true;
                        })
                        .sorted((lhs, rhs) -> rhs.getBitrate() - lhs.getBitrate())
                        .collect(Collectors.toList());
            }
            for (AdaptiveVideoFormat format : formats) {
                if (format.getMimeType().startsWith("video") && videoUrl == null) {
                    videoUrl = format.getUrl();
                    Log.d(TAG, "using video btr=" + format.getBitrate()
                            + ", codec=" + format.getMimeType()
                            + ", res=" + format.getSize()
                            + ", url=" + videoUrl);
                } else if (format.getMimeType().startsWith("audio") && audioUrl == null) {
                    audioUrl = format.getUrl();
                    Log.d(TAG, "using audio btr=" + format.getBitrate()
                            + ", codec=" + format.getMimeType()
                            + ", url=" + videoUrl);
                }
            }
        } else {
            Log.d(TAG, "error=" + videoInfo.getPlayabilityStatus());
        }

        final MatrixCursor cursor = new MatrixCursor(COLUMNS);
        final ArrayList<String> values = new ArrayList<>(COLUMNS.length);
        values.add(videoUrl);
        values.add(audioUrl);
        cursor.addRow(values);
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