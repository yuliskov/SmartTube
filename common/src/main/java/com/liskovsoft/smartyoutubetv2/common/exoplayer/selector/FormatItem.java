package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import androidx.annotation.NonNull;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.VideoTrack;

public interface FormatItem {
    FormatItem NO_VIDEO = ExoFormatItem.from(MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_VIDEO));
    FormatItem NO_AUDIO = ExoFormatItem.from(MediaTrack.forRendererIndex(TrackSelectorManager.RENDERER_INDEX_AUDIO));
    FormatItem VIDEO_AUTO = ExoFormatItem.fromVideoParams(-1, -1, -1);
    FormatItem VIDEO_SUB_SD_AVC_30 = ExoFormatItem.fromVideoSpec("426,240,30,avc", false);
    FormatItem VIDEO_SD_AVC_30 = ExoFormatItem.fromVideoSpec("640,360,30,avc", false);
    FormatItem VIDEO_HD_AVC_30 = ExoFormatItem.fromVideoSpec("1280,720,30,avc", false);
    FormatItem VIDEO_FHD_AVC_30 = ExoFormatItem.fromVideoSpec("1920,1080,30,avc", false);
    FormatItem VIDEO_FHD_AVC_60 = ExoFormatItem.fromVideoSpec("1920,1080,60,avc", false);
    FormatItem VIDEO_FHD_VP9_60 = ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", false);
    FormatItem VIDEO_4K_VP9_60 = ExoFormatItem.fromVideoSpec("3840,2160,60,vp9", false);
    FormatItem SUBTITLE_NONE = ExoFormatItem.fromSubtitleParams(null);
    FormatItem AUDIO_HQ_MP4A = ExoFormatItem.fromAudioSpecs(String.format("%s,null", "mp4a")); // Note, 5.1 mp4a doesn't work in 5.1 mode
    FormatItem AUDIO_51_EC3 = ExoFormatItem.fromAudioSpecs(String.format("%s,null", "ec-3")); // Note, 5.1 mp4a doesn't work in 5.1 mode
    FormatItem AUDIO_51_AC3 = ExoFormatItem.fromAudioSpecs(String.format("%s,null", "ac-3")); // Note, 5.1 mp4a doesn't work in 5.1 mode
    int TYPE_VIDEO = 0;
    int TYPE_AUDIO = 1;
    int TYPE_SUBTITLE = 2;
    int getId();
    CharSequence getTitle();
    boolean isDefault();
    boolean isSelected();
    boolean isPreset();
    float getFrameRate();
    String getLanguage();
    int getWidth();
    int getHeight();
    int getType();
    MediaTrack getTrack();

    static FormatItem checkFormat(FormatItem format, int type) {
        return format != null && format.getType() == type ? format : null;
    }

    static @NonNull FormatItem fromLanguage(String langCode) {
        return ExoFormatItem.fromSubtitleParams(langCode);
    }

    static MediaTrack toMediaTrack(FormatItem item) {
        return item != null ? item.getTrack() : null;
    }

    class VideoPreset {
        public final String name;
        public final FormatItem format;

        public VideoPreset(String presetName, String presetSpec) {
            this.name = presetName;
            // "2560,1440,30,vp9"
            this.format = ExoFormatItem.fromVideoSpec(presetSpec, true);
        }

        public boolean isVP9Preset() {
            return name != null && name.contains("vp9");
        }

        public boolean isAV1Preset() {
            return name != null && name.contains("av01");
        }

        public int getWidth() {
            return format != null ? format.getWidth() : -1;
        }

        public int getHeight() {
            return format != null ? format.getHeight() : -1;
        }
    }
}
