package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import android.content.Context;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicts the track the player will select for a video that isn't opened yet (e.g. to preload the next Shorts).<br/>
 * Same algorithm as {@link TrackSelectorManager} (findBestMatch) with the user's preferred format as the origin.
 */
public final class TrackSelectionPredictor {
    private static final String DEFAULT_LANGUAGE = "original"; // original, descriptive, dubbed, secondary
    private TrackSelectionPredictor() {
    }

    /**
     * @param formatGroups formats of the renderer, grouped like the player's track groups (adaptation sets)
     * @return the format the player will most likely select or null if unknown (e.g. auto quality)
     */
    @Nullable
    public static Format predict(Context context, int rendererIndex, List<List<Format>> formatGroups) {
        int formatType = rendererIndex == TrackSelectorManager.RENDERER_INDEX_VIDEO ? FormatItem.TYPE_VIDEO : FormatItem.TYPE_AUDIO;
        MediaTrack originTrack = FormatItem.toMediaTrack(PlayerData.instance(context).getFormat(formatType));

        if (originTrack == null || originTrack.format == null) {
            return null; // auto selection. Adaptive, can't be predicted.
        }

        if (rendererIndex == TrackSelectorManager.RENDERER_INDEX_AUDIO) {
            formatGroups = filterByLanguage(context, formatGroups, originTrack);
        }

        boolean allFormatsUnlocked = PlayerTweaksData.instance(context).isAllFormatsUnlocked();
        MediaTrack result = MediaTrack.forRendererIndex(rendererIndex);
        MediaTrack tmpResult = null;

        outerloop:
        for (int groupIndex = 0; groupIndex < formatGroups.size(); groupIndex++) {
            MediaTrack prevResult = result;
            List<Format> group = formatGroups.get(groupIndex);

            for (int trackIndex = 0; trackIndex < group.size(); trackIndex++) {
                MediaTrack mediaTrack = MediaTrack.forRendererIndex(rendererIndex);
                mediaTrack.format = group.get(trackIndex);
                mediaTrack.groupIndex = groupIndex;
                mediaTrack.trackIndex = trackIndex;

                if (!allFormatsUnlocked && !Utils.isFormatSupported(mediaTrack)) {
                    continue;
                }

                if (tmpResult == null) {
                    tmpResult = mediaTrack;
                }

                int bounds = originTrack.inBounds(mediaTrack);

                if (bounds == 0 && MediaTrack.bitrateEquals(originTrack, mediaTrack)) {
                    result = mediaTrack;
                    break outerloop;
                }

                if (bounds >= 0) {
                    int compare = mediaTrack.compare(result);

                    if (compare == 0 && MediaTrack.preferByDrc(originTrack, result, mediaTrack)) {
                        if (MediaTrack.codecEquals(mediaTrack, originTrack)) {
                            result = mediaTrack;
                        } else if (!MediaTrack.codecEquals(result, originTrack) &&
                                !MediaTrack.preferByCodec(result, mediaTrack)) {
                            result = mediaTrack;
                        }
                    } else if (compare > 0) {
                        if (!MediaTrack.preferByCodec(result, mediaTrack)) {
                            result = mediaTrack;
                        }
                    }
                }
            }

            // Don't let change the codec beside needed one
            if (MediaTrack.codecEquals(result, originTrack)) {
                if (MediaTrack.codecEquals(prevResult, originTrack) && prevResult.compare(result) > 0) {
                    result = prevResult;
                }
            } else if (MediaTrack.codecEquals(prevResult, originTrack)) {
                result = prevResult;
            } else if (prevResult.compare(result) == 0) {
                if (MediaTrack.preferByCodec(prevResult, result)) {
                    result = prevResult;
                }
            }
        }

        if (result.isEmpty() && tmpResult != null) {
            result = tmpResult;
        }

        return result.format;
    }

    /**
     * Same as TrackSelectorManager#filterByLanguage: preferred audio language, then the origin language, then the original track
     */
    private static List<List<Format>> filterByLanguage(Context context, List<List<Format>> formatGroups, MediaTrack originTrack) {
        if (formatGroups.size() <= 2) { // non-translated list has max 2 groups
            return formatGroups;
        }

        String audioLanguage = fixLangCode(PlayerData.instance(context).getAudioLanguage());
        String resultLanguage = null;
        String originLanguage = !TextUtils.isEmpty(originTrack.format.language) ? originTrack.format.language : null;

        if (originTrack.isSaved) {
            resultLanguage = TextUtils.isEmpty(audioLanguage) ? null : audioLanguage;
            originLanguage = null;
        }

        List<List<Format>> resultGroups = new ArrayList<>();
        List<List<Format>> originGroups = new ArrayList<>();
        List<List<Format>> fallbackGroups = new ArrayList<>();

        for (List<Format> group : formatGroups) {
            List<Format> resultGroup = new ArrayList<>();
            List<Format> originGroup = new ArrayList<>();
            List<Format> fallbackGroup = new ArrayList<>();

            for (Format format : group) {
                if (resultLanguage != null && Helpers.startsWith(format.language, resultLanguage)) {
                    resultGroup.add(format);
                } else if (originLanguage != null && Helpers.startsWith(format.language, originLanguage)) {
                    originGroup.add(format);
                } else if (Helpers.contains(format.language, DEFAULT_LANGUAGE)) {
                    fallbackGroup.add(format);
                }
            }

            if (!resultGroup.isEmpty()) {
                resultGroups.add(resultGroup);
            }

            if (!originGroup.isEmpty()) {
                originGroups.add(originGroup);
            }

            if (!fallbackGroup.isEmpty()) {
                fallbackGroups.add(fallbackGroup);
            }
        }

        return !resultGroups.isEmpty() ? resultGroups : !originGroups.isEmpty() ? originGroups
                : !fallbackGroups.isEmpty() ? fallbackGroups : formatGroups;
    }

    private static String fixLangCode(String langCode) {
        return "in".equals(langCode) ? "id" : langCode; // Wrong Indonesian
    }
}
