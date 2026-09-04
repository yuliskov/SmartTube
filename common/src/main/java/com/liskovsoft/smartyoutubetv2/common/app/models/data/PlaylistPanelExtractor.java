package com.liskovsoft.smartyoutubetv2.common.app.models.data;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;

import java.util.List;

/**
 * Helpers for reverse playlist playback.
 *
 * A watch-next request made with an empty video id but a playlist id and index (the same trick
 * shuffle uses) returns a small "playlist panel" window of items around the requested index, but
 * no top level video id. These helpers pull a usable {@link Video} out of that panel.
 */
public final class PlaylistPanelExtractor {
    private PlaylistPanelExtractor() {
    }

    /**
     * Last playable item of the playlist panel (used to resolve the very last video of a playlist
     * when the request targets {@code size - 1}).
     */
    public static Video lastItem(MediaItemMetadata metadata, String playlistId) {
        List<MediaItem> panel = findPanel(metadata, playlistId);

        if (panel == null) {
            return null;
        }

        for (int i = panel.size() - 1; i >= 0; i--) {
            MediaItem item = panel.get(i);
            if (item != null && item.getVideoId() != null) {
                return Video.from(item);
            }
        }

        return null;
    }

    /**
     * Item right before {@code comingFromVideoId} in the panel. When that video is not part of the
     * window (the window sits entirely before it) the last panel item is returned instead, since
     * the panel was requested centred on the wanted index.
     */
    public static Video itemBefore(MediaItemMetadata metadata, String comingFromVideoId, String playlistId) {
        List<MediaItem> panel = findPanel(metadata, playlistId);

        if (panel == null) {
            return null;
        }

        int fromPos = -1;
        for (int i = 0; i < panel.size(); i++) {
            MediaItem item = panel.get(i);
            if (item != null && comingFromVideoId != null && comingFromVideoId.equals(item.getVideoId())) {
                fromPos = i;
                break;
            }
        }

        if (fromPos > 0) {
            return Video.from(panel.get(fromPos - 1));
        }

        if (fromPos == -1) {
            for (int i = panel.size() - 1; i >= 0; i--) {
                MediaItem item = panel.get(i);
                if (item != null && item.getVideoId() != null) {
                    return Video.from(item);
                }
            }
        }

        return null;
    }

    private static List<MediaItem> findPanel(MediaItemMetadata metadata, String playlistId) {
        if (metadata == null || metadata.getSuggestions() == null || playlistId == null) {
            return null;
        }

        for (MediaGroup group : metadata.getSuggestions()) {
            List<MediaItem> items = group != null ? group.getMediaItems() : null;
            if (items == null || items.isEmpty()) {
                continue;
            }

            for (MediaItem item : items) {
                if (item != null && playlistId.equals(item.getPlaylistId())) {
                    return items;
                }
            }
        }

        return null;
    }
}
