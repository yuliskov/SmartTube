package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import androidx.annotation.NonNull;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

public interface FormatItem {
    FormatItem VIDEO_AUTO = ExoFormatItem.fromVideoParams(-1, -1, -1);
    FormatItem VIDEO_SD_AVC_30 = ExoFormatItem.fromVideoSpec("640,360,30,avc", false);
    FormatItem VIDEO_HD_AVC_30 = ExoFormatItem.fromVideoSpec("1280,720,30,avc", false);
    FormatItem VIDEO_FHD_AVC_30 = ExoFormatItem.fromVideoSpec("1920,1080,30,avc", false);
    FormatItem VIDEO_FHD_AVC_60 = ExoFormatItem.fromVideoSpec("1920,1080,60,avc", false);
    FormatItem VIDEO_FHD_VP9_60 = ExoFormatItem.fromVideoSpec("1920,1080,60,vp9", false);
    FormatItem VIDEO_4K_VP9_60 = ExoFormatItem.fromVideoSpec("3840,2160,60,vp9", false);
    FormatItem SUBTITLE_AUTO = ExoFormatItem.fromSubtitleParams(null);
    FormatItem AUDIO_HQ_MP4A = ExoFormatItem.fromAudioData(ExoFormatItem.FORMAT_MP4A);
    int TYPE_VIDEO = 0;
    int TYPE_AUDIO = 1;
    int TYPE_SUBTITLE = 2;
    int getId();
    CharSequence getTitle();
    boolean isDefault();
    boolean isSelected();
    boolean isPreset();
    float getFrameRate();
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
    }
}
