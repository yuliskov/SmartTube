package com.liskovsoft.smartyoutubetv2.common.exoplayer.other;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle;

import java.util.List;

/**
 * Resolves caption track metadata from {@link MediaItemFormatInfo} for the ExoPlayer subtitle
 * {@link com.google.android.exoplayer2.Format#id} (YouTube vss id).
 */
public final class SubtitleFormatInfoUtil {
    private SubtitleFormatInfoUtil() {
    }

    /** Returns the full {@link MediaSubtitle} for the given vssId, or {@code null} if not found. */
    public static MediaSubtitle findSubtitle(MediaItemFormatInfo formatInfo, String vssId) {
        if (formatInfo == null || vssId == null) {
            return null;
        }
        List<MediaSubtitle> subs = formatInfo.getSubtitles();
        if (subs == null) {
            return null;
        }
        for (MediaSubtitle sub : subs) {
            if (sub != null && vssId.equals(sub.getVssId())) {
                return sub;
            }
        }
        return null;
    }

    public static String findSubtitleBaseUrl(MediaItemFormatInfo formatInfo, String vssId) {
        MediaSubtitle sub = findSubtitle(formatInfo, vssId);
        return sub != null ? sub.getBaseUrl() : null;
    }
}
