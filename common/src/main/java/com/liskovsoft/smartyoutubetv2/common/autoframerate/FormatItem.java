package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

public interface FormatItem {
    FormatItem VIDEO_FHD_AVC = ExoFormatItem.createFakeVideoFormat(ExoFormatItem.RESOLUTION_FHD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem VIDEO_HD_AVC = ExoFormatItem.createFakeVideoFormat(ExoFormatItem.RESOLUTION_HD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem VIDEO_SD_AVC = ExoFormatItem.createFakeVideoFormat(ExoFormatItem.RESOLUTION_SD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem VIDEO_LQ_AVC = ExoFormatItem.createFakeVideoFormat(ExoFormatItem.RESOLUTION_LD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem AUDIO_HQ_MP4A = ExoFormatItem.createFakeAudioFormat(ExoFormatItem.FORMAT_MP4A);
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
}
