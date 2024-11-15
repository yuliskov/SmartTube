package com.liskovsoft.leanbackassistant.channels;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.DrawableRes;
import androidx.annotation.WorkerThread;
import androidx.tvprovider.media.tv.Channel;
import androidx.tvprovider.media.tv.Channel.Builder;
import androidx.tvprovider.media.tv.ChannelLogoUtils;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.TvContractCompat.Channels;
import androidx.tvprovider.media.tv.WatchNextProgram;
import android.text.TextUtils;

import com.liskovsoft.leanbackassistant.media.Clip;
import com.liskovsoft.leanbackassistant.media.Playlist;
import com.liskovsoft.leanbackassistant.media.scheduler.ClipData;
import com.liskovsoft.leanbackassistant.utils.AppUtil;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("RestrictedApi")
public class ChannelsProvider {
    private static final String TAG = ChannelsProvider.class.getSimpleName();
    private static final String SCHEME = "tvhomescreenchannels";
    private static final String APPS_LAUNCH_HOST = "com.google.android.tvhomescreenchannels";
    private static final String PLAY_VIDEO_ACTION_PATH = "playvideo";
    /**
     * Index into "WATCH_NEXT_MAP_PROJECTION" and if that changes, this should change too.
     */
    private static final int COLUMN_WATCH_NEXT_ID_INDEX = 0;
    private static final int COLUMN_WATCH_NEXT_INTERNAL_PROVIDER_ID_INDEX = 1;
    private static final int COLUMN_WATCH_NEXT_COLUMN_BROWSABLE_INDEX = 2;

    private static final String[] WATCH_NEXT_MAP_PROJECTION =
            {BaseColumns._ID, TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_BROWSABLE};

    private static final Uri PREVIEW_PROGRAMS_CONTENT_URI =
            Uri.parse("content://android.media.tv/preview_program");

    @TargetApi(21)
    private static final String[] CHANNEL_COLUMNS = {
            TvContractCompat.Channels._ID,
            TvContractCompat.Channels.COLUMN_DISPLAY_NAME,
            TvContractCompat.Channels.COLUMN_BROWSABLE,
            TvContractCompat.Channels.COLUMN_SYSTEM_CHANNEL_KEY,
            TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID
    };

    private ChannelsProvider() {
    }

    static private String createInputId(Context context) {
        // TODO: tv input service component name
        ComponentName cName = new ComponentName(context, ChannelsProvider.class.getName());
        return TvContractCompat.buildInputId(cName);
    }

    /**
     * Writes a drawable as the channel logo.
     *
     * @param channelId  identifies the channel to write the logo.
     * @param drawableId resource to write as the channel logo. This must be a bitmap and not, say
     *                   a vector drawable.
     */
    @WorkerThread
    static private void writeChannelLogo(Context context, long channelId, @DrawableRes int drawableId) {
        if (channelId != -1 && drawableId != -1) {
            Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), drawableId);
            ChannelLogoUtils.storeChannelLogo(context, channelId, bitmap);
        }
    }

    @WorkerThread
    public static void addWatchNextContinue(Context context, ClipData clipData) {
        final String clipId = clipData.getClipId();
        final String contentId = clipData.getContentId();

        // Check if program "key" has already been added.
        boolean isProgramPresent = false;
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
                    null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(COLUMN_WATCH_NEXT_INTERNAL_PROVIDER_ID_INDEX)
                            && TextUtils.equals(clipId, cursor.getString(
                            COLUMN_WATCH_NEXT_INTERNAL_PROVIDER_ID_INDEX))) {
                        // Found a row that contains an equal COLUMN_INTERNAL_PROVIDER_ID.
                        long watchNextProgramId = cursor.getLong(COLUMN_WATCH_NEXT_ID_INDEX);
                        // If the clip exists in watch next programs, there are 2 cases:
                        // 1. The program was not removed by the user (browsable == 1) and we
                        // only need to update the existing info for that program
                        // 2. The program was removed by the user from watch next
                        // (browsable== 0), in which case we will first remove it from watch
                        // next database and then treat it as a new watch next program to be
                        // inserted.
                        if (cursor.getInt(COLUMN_WATCH_NEXT_COLUMN_BROWSABLE_INDEX) == 0) {
                            int rowsDeleted = context.getContentResolver().delete(
                                    TvContractCompat.buildWatchNextProgramUri(
                                            watchNextProgramId), null,
                                    null);
                            if (rowsDeleted < 1) {
                                Log.e(TAG, "Delete program failed");
                            }
                        } else {
                            WatchNextProgram existingProgram = WatchNextProgram.fromCursor(
                                    cursor);
                            // Updating the following columns since when a program is added
                            // manually through the launcher interface to the WatchNext row:
                            // 1. watchNextType is set to WATCH_NEXT_TYPE_WATCHLIST which
                            // should be changed to WATCH_NEXT_TYPE_CONTINUE when at least 1
                            // minute of the video is played.
                            // 2. The duration may not have been set for the programs in a
                            // channel row since the video wasn't processed then to set this
                            // column. Also setting lastPlaybackPosition to maintain the
                            // correct progressBar upon returning to the launcher.
                            WatchNextProgram.Builder builder = new WatchNextProgram.Builder(
                                    existingProgram)
                                    .setWatchNextType(TvContractCompat.WatchNextPrograms
                                            .WATCH_NEXT_TYPE_CONTINUE)
                                    .setLastPlaybackPositionMillis((int) clipData.getProgress())
                                    .setDurationMillis((int) clipData.getDuration());
                            ContentValues contentValues = builder.build().toContentValues();
                            Uri watchNextProgramUri = TvContractCompat.buildWatchNextProgramUri(
                                    watchNextProgramId);
                            int rowsUpdated = context.getContentResolver().update(
                                    watchNextProgramUri,
                                    contentValues, null, null);
                            if (rowsUpdated < 1) {
                                Log.e(TAG, "Update program failed");
                            }
                            isProgramPresent = true;
                        }
                    }
                }
            }
            if (!isProgramPresent) {
                WatchNextProgram.Builder builder = new WatchNextProgram.Builder();
                builder.setType(TvContractCompat.WatchNextPrograms.TYPE_CLIP)
                        .setWatchNextType(
                                TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                        .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                        .setTitle(clipData.getTitle())
                        .setDescription(clipData.getDescription())
                        .setPosterArtUri(Uri.parse(clipData.getCardImageUrl()))
                        .setIntentUri(Uri.parse(SCHEME + "://" + APPS_LAUNCH_HOST
                                + "/" + PLAY_VIDEO_ACTION_PATH + "/" + clipId))
                        .setInternalProviderId(clipId)
                        // Setting the contentId to avoid having duplicate programs with the same
                        // content added to the watch next row (The launcher will use the contentId
                        // to detect duplicates). Note that, programs of different channels can
                        // still point to the same content i.e. their contentId can be the same.
                        .setContentId(contentId)
                        .setLastPlaybackPositionMillis((int) clipData.getProgress())
                        .setDurationMillis((int) clipData.getDuration());
                ContentValues contentValues = builder.build().toContentValues();
                Uri programUri = context.getContentResolver().insert(
                        TvContractCompat.WatchNextPrograms.CONTENT_URI, contentValues);
                if (programUri == null || programUri.equals(Uri.EMPTY)) {
                    Log.e(TAG, "Insert watch next program failed");
                }
            }

            // TODO: update api
            //SampleContentDb.getInstance(context).updateClipProgress(clipId, clipData.getProgress());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @WorkerThread
    public static void deleteWatchNextContinue(Context context, String clipId) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI, WATCH_NEXT_MAP_PROJECTION, null,
                    null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(COLUMN_WATCH_NEXT_INTERNAL_PROVIDER_ID_INDEX)
                            && TextUtils.equals(clipId, cursor.getString(
                            COLUMN_WATCH_NEXT_INTERNAL_PROVIDER_ID_INDEX))) {
                        long watchNextProgramId = cursor.getLong(COLUMN_WATCH_NEXT_ID_INDEX);
                        int rowsDeleted = context.getContentResolver().delete(
                                TvContractCompat.buildWatchNextProgramUri(watchNextProgramId), null,
                                null);
                        if (rowsDeleted < 1) {
                            Log.e(TAG, "Delete program failed");
                        }

                        // TODO: delete api
                        //SampleContentDb.getInstance(context).deleteClipProgress(clipId);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @WorkerThread
    public static void createOrUpdateChannel(Context context, Playlist playlist) {
        long publishedId = playlist.getPublishedId();

        if (publishedId != -1) {
            Log.d(TAG, "Oops: channel already published. Doing update instead... publishedId: " + publishedId);
            updateChannel(context, playlist);
            //addClipsToChannel(context, publishedId, Helpers.isGoogleTVLauncher(context) || channel.isBrowsable() ? playlist.getClips() : Collections.emptyList());
            addClipsToChannel(context, publishedId, playlist.getClips());
            return;
        }

        Channel channel = findChannelByProviderId(context, playlist.getPlaylistId());

        if (channel != null) {
            Log.d(TAG, "Oops: channel already published but not memorized by the app. Doing update instead... foundId: " + channel.getId());
            playlist.setPublishedId(channel.getId());
            updateChannel(context, playlist);
            //addClipsToChannel(context, channel.getId(), Helpers.isGoogleTVLauncher(context) || channel.isBrowsable() ? playlist.getClips() : Collections.emptyList());
            addClipsToChannel(context, channel.getId(), playlist.getClips());
            return;
        }

        Log.d(TAG, "Creating channel: " + playlist.getName());

        long channelId = createChannel(context, playlist);

        // The channels are disabled by default (don't populate to save resources)
        //addClipsToChannel(context, channelId, Collections.emptyList());
        addClipsToChannel(context, channelId, playlist.getClips());
    }

    private static long createChannel(Context context, Playlist playlist) {
        Channel.Builder builder = createChannelBuilder(context, playlist);

        Uri channelUri = null;

        try {
            channelUri = context.getContentResolver().insert(
                    Channels.CONTENT_URI,
                    builder.build().toContentValues());
        } catch (Exception e) { // channels not supported
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        if (channelUri == null || channelUri.equals(Uri.EMPTY)) {
            Log.e(TAG, "Insert channel failed");
            return -1;
        }

        long channelId = ContentUris.parseId(channelUri);

        playlist.setPublishedId(channelId);

        writeChannelLogo(context, channelId, playlist.getLogoResId());

        // Google TV fix (no dialog to enable the channels)
        if (playlist.isDefault()) {
            TvContractCompat.requestChannelBrowsable(context, channelId);
        }

        return channelId;
    }

    @WorkerThread
    private static void addClipsToChannel(Context context, long channelId, List<Clip> clips) {
        if (channelId == -1) {
            Log.d(TAG, "Cant add clips: channelId == -1");
            return;
        }

        if (clips.size() == 0) {
            Log.d(TAG, "Cant add clips: clips.size() == 0");
            return;
        }

        cleanupChannel(context, channelId);

        int weight = clips.size();
        for (int i = 0; i < clips.size(); ++i, --weight) {
            Clip clip = clips.get(i);

            publishProgram(context, clip, channelId, weight);
        }
    }

    private static void cleanupChannel(Context context, long channelId) {
        context.getContentResolver().delete(TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null);
    }

    @WorkerThread
    private static void updateChannel(Context context, Playlist playlist) {
        long channelId = playlist.getPublishedId();

        if (channelId == -1) {
            Log.d(TAG, "Error: channel not published yet: " + channelId);
            return;
        }

        writeChannelLogo(context, channelId, playlist.getLogoResId());

        Builder builder = createChannelBuilder(context, playlist);

        int rowsUpdated = context.getContentResolver().update(
                TvContractCompat.buildChannelUri(channelId), builder.build().toContentValues(), null, null);

        if (rowsUpdated < 1) {
            Log.e(TAG, "Update channel failed");
        } else {
            Log.d(TAG, "Channel updated " + playlist.getName());
        }
    }

    @WorkerThread
    public static void deleteAllChannels(Context context) {
         visitChannels(context, (Channel channel) -> {
             deleteChannel(context, channel.getId());
             return true;
         });
    }

    @WorkerThread
    public static void deleteChannel(Context context, long channelId) {
        if (channelId == -1) {
            Log.d(TAG, "Invalid channel id " + channelId);
            return;
        }

        int rowsDeleted = context.getContentResolver().delete(
                TvContractCompat.buildChannelUri(channelId), null, null);
        if (rowsDeleted < 1) {
            Log.e(TAG, "Delete channel failed");
        }
    }

    @WorkerThread
    public static void deleteProgram(Context context, Clip clip) {
        deleteProgram(context, clip.getProgramId());
    }

    @WorkerThread
    private static void deleteProgram(Context context, long programId) {
        int rowsDeleted = context.getContentResolver().delete(
                TvContractCompat.buildPreviewProgramUri(programId), null, null);
        if (rowsDeleted < 1) {
            Log.e(TAG, "Delete program failed");
        }
    }

    private static void publishProgram(Context context, Clip clip, long channelId, int weight) {
        if (clip.getProgramId() != -1) {
            Log.e(TAG, "Clip already published. Skipping...");
            return;
        }

        if (clip.getVideoUrl() == null) {
            // Seems like this is an ads
            Log.e(TAG, "Clip doesn't contain url. Skipping...");
            return;
        }

        PreviewProgram.Builder builder =
                createProgramBuilder(context, clip)
                        .setWeight(weight)
                        .setChannelId(channelId);

        Uri programUri = null;

        try {
            programUri = context.getContentResolver().insert(PREVIEW_PROGRAMS_CONTENT_URI, builder.build().toContentValues());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }

        if (programUri == null || programUri.equals(Uri.EMPTY)) {
            Log.e(TAG, "Insert program failed");
            return;
        }

        clip.setProgramId(ContentUris.parseId(programUri));
    }

    static String decodeVideoId(Uri uri) {
        List<String> paths = uri.getPathSegments();
        if (paths.size() == 2 && TextUtils.equals(paths.get(0), PLAY_VIDEO_ACTION_PATH)) {
            return paths.get(1);
        }

        return new String();
    }

    @WorkerThread
    static void setProgramViewCount(Context context, long programId, int numberOfViews) {
        Uri programUri = TvContractCompat.buildPreviewProgramUri(programId);
        try (Cursor cursor = context.getContentResolver().query(programUri, null, null, null,
                null)) {
            if (!cursor.moveToFirst()) {
                return;
            }
            PreviewProgram existingProgram = PreviewProgram.fromCursor(cursor);
            PreviewProgram.Builder builder = new PreviewProgram.Builder(existingProgram)
                    .setInteractionCount(numberOfViews)
                    .setInteractionType(TvContractCompat.PreviewProgramColumns
                            .INTERACTION_TYPE_VIEWS);
            int rowsUpdated = context.getContentResolver().update(
                    TvContractCompat.buildPreviewProgramUri(programId),
                    builder.build().toContentValues(), null, null);
            if (rowsUpdated != 1) {
                Log.e(TAG, "Update program failed");
            }
        }
    }

    private static PreviewProgram.Builder createProgramBuilder(Context context, Clip clip) {
        return createProgramBuilder(new PreviewProgram.Builder(), context, clip);
    }

    private static PreviewProgram.Builder createProgramBuilder(PreviewProgram.Builder baseBuilder, Context context, Clip clip) {
        Uri previewUri = clip.getPreviewVideoUrl() == null ? null : Uri.parse(clip.getPreviewVideoUrl());
        Uri cardUri = clip.getCardImageUrl() == null ? null : Uri.parse(clip.getCardImageUrl());

        baseBuilder
            .setTitle(clip.getTitle())
            .setDescription(clip.getDescription())
            .setDurationMillis((int) clip.getDurationMs())
            .setLive(clip.isLive())
            .setPosterArtUri(cardUri)
            .setIntent(AppUtil.getInstance(context).createAppIntent(clip.getVideoUrl()))
            .setPreviewVideoUri(previewUri)
            .setInternalProviderId(clip.getClipId())
            .setContentId(clip.getContentId())
            .setThumbnailAspectRatio(clip.getAspectRatio())
            .setPosterArtAspectRatio(clip.getAspectRatio())
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE);

        return baseBuilder;
    }

    private static Channel.Builder createChannelBuilder(Context context, Playlist playlist) {
        Channel.Builder builder = new Channel.Builder()
                .setDisplayName(playlist.getName())
                .setDescription(playlist.getDescription())
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setInputId(createInputId(context))
                .setAppLinkIntent(AppUtil.getInstance(context).createAppIntent(playlist.getPlaylistUrl()))
                .setSystemChannelKey(playlist.getChannelKey())
                .setInternalProviderId(playlist.getPlaylistId());

        return builder;
    }

    /**
     * Not robust way because its behavior depends on current locale
     */
    private static long findChannelByName(Context context, String name) {
        final AtomicLong channelId = new AtomicLong(-1);

        visitChannels(context, (Channel channel) -> {
            if (name.equals(channel.getDisplayName())) {
                if (channelId.get() == -1) {
                    Log.d(TAG, "Channel found. DisplayName: " + name);
                    channelId.set(channel.getId());
                } else {
                    Log.d(TAG, "Duplicate channel deleted. DisplayName: " + name);
                    deleteChannel(context, channel.getId());
                }
            }

            return true; // continue visiting
        });

        return channelId.get();
    }

    private static Channel findChannelByProviderId(Context context, String providerId) {
        final AtomicReference<Channel> myChannel = new AtomicReference<>(null);

        visitChannels(context, (Channel channel) -> {
            if (providerId.equals(channel.getInternalProviderId())) {
                if (myChannel.get() == null) {
                    Log.d(TAG, "Channel found. ProviderId: " + providerId);
                    myChannel.set(channel);
                } else {
                    Log.d(TAG, "Duplicate channel deleted. ProviderId: " + providerId);
                    deleteChannel(context, channel.getId());
                }
            }

            return true; // continue visiting
        });

        return myChannel.get();
    }

    private static long findChannelByChannelKey(Context context, String channelKey) {
        final AtomicLong channelId = new AtomicLong(-1);

        visitChannels(context, (Channel channel) -> {
            if (channelKey.equals(channel.getSystemChannelKey())) {
                if (channelId.get() == -1) {
                    Log.d(TAG, "Channel found. ProviderId: " + channelKey);
                    channelId.set(channel.getId());
                } else {
                    Log.d(TAG, "Duplicate channel deleted. ProviderId: " + channelKey);
                    deleteChannel(context, channel.getId());
                }
            }

            return true; // continue visiting
        });

        return channelId.get();
    }

    private interface ChannelVisitor {
        boolean onChannel(Channel channel);
    }
    
    private static void visitChannels(Context context, ChannelVisitor visitor) {
        Cursor cursor = context.getContentResolver().query(
                TvContractCompat.Channels.CONTENT_URI,
                CHANNEL_COLUMNS,
                null,
                null,
                null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        Channel channel = Channel.fromCursor(cursor);
                        Log.d(TAG, "visitChannels. Channel found: " + channel);
                        boolean continueVisiting = visitor.onChannel(channel);

                        if (!continueVisiting) {
                            break;
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }

        }
    }
}
