package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import androidx.annotation.NonNull;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

public interface FormatItem {
    FormatItem VIDEO_FHD_AVC_30 = ExoFormatItem.fromVideoData(ExoFormatItem.RESOLUTION_FHD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem VIDEO_HD_AVC_30 = ExoFormatItem.fromVideoData(ExoFormatItem.RESOLUTION_HD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem VIDEO_SD_AVC_30 = ExoFormatItem.fromVideoData(ExoFormatItem.RESOLUTION_SD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem VIDEO_LQ_AVC_30 = ExoFormatItem.fromVideoData(ExoFormatItem.RESOLUTION_LD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem AUDIO_HQ_MP4A = ExoFormatItem.fromAudioData(ExoFormatItem.FORMAT_MP4A);
    int TYPE_VIDEO = 0;
    int TYPE_AUDIO = 1;
    int TYPE_SUBTITLE = 2;
    int getId();
    CharSequence getTitle();
    boolean isDefault();
    float getFrameRate();
    int getWidth();
    int getHeight();
    boolean isSelected();
    int getType();

    static FormatItem checkFormat(FormatItem format, int type) {
        return format != null && format.getType() == type ? format : null;
    }
    static @NonNull FormatItem fromLanguage(String langCode) {
        return ExoFormatItem.fromSubtitleData(langCode);
    }

    class VideoPreset {
        public final String name;
        public final FormatItem format;

        public VideoPreset(String presetName, String presetSpec) {
            this.name = presetName;
            // "2560,1440,30,vp9"
            this.format = ExoFormatItem.fromVideoPreset(presetSpec);
        }
    }
}
