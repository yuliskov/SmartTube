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

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaItemFormatInfo;
import com.liskovsoft.youtubeapi.videoinfo.V2.VideoInfoServiceUnsigned;
import com.liskovsoft.youtubeapi.videoinfo.models.VideoInfo;
import com.liskovsoft.youtubeapi.videoinfo.models.formats.AdaptiveVideoFormat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class UrlProvider extends ContentProvider {
    private static final String TAG = "UrlProvider";
    private final String [] COLUMNS = {"video_url", "audio_url", "playlist_content"};
    private final static int MAX_VIDEO_HEIGHT = 2160;
    private final static double ASPECT_RATIO_16_9 = 16 / 9.0;
    private final static int MAX_WIDTH_FOR_ULTRA_WIDE_ASPECT = 1920;
    private final static String MAX_VIDEO_HEIGHT_PARAM = "max_video_height";
    private final static String AVC_CODEC_PRIORITY_PARAM = "avc_codec_priority";
    private final static String MP4A_CODEC_PRIORITY_PARAM = "mp4a_codec_priority";

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
        Utils.initGlobalData(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final long startTime = System.currentTimeMillis();
        final String videoPageUrl = uri.getQueryParameter("url");
        final VideoInfo videoInfo = VideoInfoServiceUnsigned.instance()
                .getVideoInfo(Uri.parse(videoPageUrl).getQueryParameter("v"), "");
        String playlistContent = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            YouTubeMediaItemFormatInfo formatInfo = YouTubeMediaItemFormatInfo.from(videoInfo);
            playlistContent = new BufferedReader(new InputStreamReader(formatInfo.createMpdStream()))
                    .lines()
                    .collect(Collectors.joining("\n"));
        }

        @Nullable List<AdaptiveVideoFormat> formats = videoInfo.getAdaptiveFormats();
        String videoUrl = null;
        String audioUrl = null;
        if (!TextUtils.isEmpty(videoInfo.getDashManifestUrl())) {
            videoUrl = videoInfo.getDashManifestUrl();
            Log.d(TAG, "using dash manifest");
        } else if (formats != null) {
            int maxVideoHeight = MAX_VIDEO_HEIGHT;
            if (uri.getQueryParameter(MAX_VIDEO_HEIGHT_PARAM) != null) {
                maxVideoHeight = Integer.parseInt(uri.getQueryParameter(MAX_VIDEO_HEIGHT_PARAM));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                final boolean isAvcCodecPriority = Boolean.parseBoolean(uri.getQueryParameter(AVC_CODEC_PRIORITY_PARAM));
                final boolean isMp4aCodecPriority = Boolean.parseBoolean(uri.getQueryParameter(MP4A_CODEC_PRIORITY_PARAM));
                final int finalMaxVideoHeight = maxVideoHeight;
                formats = formats.stream()
                        .filter(adaptiveVideoFormat -> !adaptiveVideoFormat.getMimeType().contains("av01"))
                        .filter(adaptiveVideoFormat -> {
                            if (adaptiveVideoFormat.getSize() != null) {
                                final int width = Integer.parseInt(adaptiveVideoFormat.getSize().split("x")[0]);
                                final int height = Integer.parseInt(adaptiveVideoFormat.getSize().split("x")[1]);
                                final boolean isVerticalVideo = 1.0 * width / height <= 1.0;
                                final boolean isVp9Codec = adaptiveVideoFormat.getMimeType().contains("vp9");
                                // skip vp9 and vertical
                                if (isVerticalVideo && isVp9Codec) {
                                    return false;
                                }
                                final boolean isProperlyAspect = Math.abs(1.0 * width / height - ASPECT_RATIO_16_9) < 0.1;
                                return (isProperlyAspect && height <= finalMaxVideoHeight)
                                        // skip ultra wide video greater than 1920
                                        || (!isProperlyAspect && width <= MAX_WIDTH_FOR_ULTRA_WIDE_ASPECT);
                            }
                            return true;
                        })
                        .sorted((lhs, rhs) -> rhs.getBitrate() - lhs.getBitrate())
                        .sorted((lhs, rhs) -> {
                            final String priorityCodec = isAvcCodecPriority ? "avc" : "vp9";
                            final int lhsValue = lhs.getMimeType().contains(priorityCodec) ? 1 : 0;
                            final int rhsValue = rhs.getMimeType().contains(priorityCodec) ? 1 : 0;
                            return rhsValue - lhsValue;
                        })
                        .sorted((lhs, rhs) -> {
                            final String priorityCodec = isMp4aCodecPriority ? "mp4a" : "opus";
                            final int lhsValue = lhs.getMimeType().contains(priorityCodec) ? 1 : 0;
                            final int rhsValue = rhs.getMimeType().contains(priorityCodec) ? 1 : 0;
                            return rhsValue - lhsValue;
                        })
                        .collect(Collectors.toList());
            }
            for (AdaptiveVideoFormat format : formats) {
                Log.d(TAG, "format size=" + format.getSize()
                        + ", btr=" + format.getBitrate()
                        + ", codec=" + format.getMimeType()
                        + ", res=" + format.getSize()
                        + ", url=" + format.getUrl());
                if (format.getMimeType().startsWith("video") && videoUrl == null) {
                    videoUrl = format.getUrl();
                    Log.d(TAG, "format set video url=" + videoUrl);
                } else if (format.getMimeType().startsWith("audio") && audioUrl == null) {
                    audioUrl = format.getUrl();
                    Log.d(TAG, "format set audio url=" + audioUrl);
                }
            }
        } else {
            Log.d(TAG, "error=" + videoInfo.getPlayabilityStatus());
        }

        final MatrixCursor cursor = new MatrixCursor(COLUMNS);
        final ArrayList<String> row = new ArrayList<>(COLUMNS.length);
        row.add(videoUrl);
        row.add(audioUrl);
        row.add(playlistContent);
        cursor.addRow(row);
        Log.d(TAG, "query elapsed: " + (System.currentTimeMillis() - startTime) + "ms.");
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}