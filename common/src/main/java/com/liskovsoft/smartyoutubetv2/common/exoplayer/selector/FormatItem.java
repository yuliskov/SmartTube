package com.liskovsoft.smartyoutubetv2.common.exoplayer.selector;

import androidx.annotation.NonNull;

public interface FormatItem {
    FormatItem VIDEO_AUTO = ExoFormatItem.fromVideoParams(-1, -1, -1);
    FormatItem VIDEO_SD_AVC_30 = ExoFormatItem.fromVideoSpec("640,360,30,avc", false);
    FormatItem VIDEO_HD_AVC_30 = ExoFormatItem.fromVideoSpec("1280,720,30,avc", false);
    FormatItem VIDEO_FHD_AVC_30 = ExoFormatItem.fromVideoSpec("1920,1080,30,avc", false);
    FormatItem VIDEO_FHD_AVC_60 = ExoFormatItem.fromVideoSpec("1920,1080,60,avc", false);
    FormatItem VIDEO_FHD_VP9_60 = ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", false);
    FormatItem VIDEO_4K_VP9_60 = ExoFormatItem.fromVideoSpec("3840,2160,60,vp9", false);
    FormatItem SUBTITLE_DEFAULT = ExoFormatItem.fromSubtitleParams(null);
    FormatItem AUDIO_HQ_MP4A = ExoFormatItem.fromAudioSpecs(String.format("%s,null", "mp4a")); // Note, 5.1 mp4a won't work
    FormatItem AUDIO_51_EC3 = ExoFormatItem.fromAudioSpecs(String.format("%s,null", "ec-3")); // Note, 5.1 mp4a won't work
    FormatItem AUDIO_51_AC3 = ExoFormatItem.fromAudioSpecs(String.format("%s,null", "ac-3")); // Note, 5.1 mp4a won't work
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

    static FormatItem checkFormat(FormatItem format, int type) {
        return format != null && format.getType() == type ? format : null;
    }
    static @NonNull FormatItem fromLanguage(String langCode) {
        return ExoFormatItem.fromSubtitleParams(langCode);
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
