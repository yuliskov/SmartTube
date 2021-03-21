package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;

public class ExoUtils {
    public static boolean isPlaying(ExoPlayer player) {
        if (player == null) {
            return false;
        }

        // Exo 2.10 and up
        // return player.isPlaying();

        // Exo 2.9
        return player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_READY;
    }

    public static MediaCodecInfo getDecoderInfo(String mimeType) {
        MediaCodecInfo info = null;

        try {
            // Exo 2.10 and up
            //info = MediaCodecUtil.getDecoderInfo(mimeType, false, false);

            // Exo 2.9
            info = MediaCodecUtil.getDecoderInfo(mimeType, false);
        } catch (DecoderQueryException e) {
            e.printStackTrace();
        }

        return info;
    }
}
