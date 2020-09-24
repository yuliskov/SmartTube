package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;

public interface FormatItem {
    FormatItem FHD_AVC = ExoFormatItem.defaultVideo(ExoFormatItem.RESOLUTION_FHD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem HD_AVC = ExoFormatItem.defaultVideo(ExoFormatItem.RESOLUTION_HD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem SD_AVC = ExoFormatItem.defaultVideo(ExoFormatItem.RESOLUTION_SD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
    FormatItem LD_AVC = ExoFormatItem.defaultVideo(ExoFormatItem.RESOLUTION_LD, ExoFormatItem.FORMAT_AVC, ExoFormatItem.FPS_30);
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
