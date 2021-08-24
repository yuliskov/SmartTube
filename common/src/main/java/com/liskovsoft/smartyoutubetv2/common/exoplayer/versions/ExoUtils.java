package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;

public class ExoUtils {
    private static String sVideoDecoderName;

    public static boolean isPlaying(ExoPlayer player) {
        if (player == null) {
            return false;
        }

        // Exo 2.9
        //return player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_READY;

        // Exo 2.10 and up
        return player.isPlaying();
    }

    public static boolean isLoading(ExoPlayer player) {
        if (player == null) {
            return false;
        }

        return player.isLoading();
    }

    public static MediaCodecInfo getCapsDecoderInfo(String mimeType) {
        MediaCodecInfo info = null;

        try {
            // Exo 2.9
            //info = MediaCodecUtil.getDecoderInfo(mimeType, false);

            // Exo 2.10 and up
            info = MediaCodecUtil.getDecoderInfo(mimeType, false, false);
        } catch (DecoderQueryException e) {
            e.printStackTrace();
        }

        return info;
    }

    public static void updateVideoDecoderInfo(MediaCodecInfo codecInfo) {
        if (codecInfo == null) {
            return;
        }

        sVideoDecoderName = codecInfo.name;
    }

    public static String getVideoDecoderName() {
        return sVideoDecoderName;
    }
}
