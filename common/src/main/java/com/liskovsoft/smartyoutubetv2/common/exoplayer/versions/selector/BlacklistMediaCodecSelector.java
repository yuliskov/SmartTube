package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Usage {@link CustomOverridesRenderersFactory#setMediaCodecSelector}
 */
public class BlacklistMediaCodecSelector implements MediaCodecSelector {
    private static final String TAG = BlacklistMediaCodecSelector.class.getSimpleName();

    // list of strings used in blacklisting codecs
    final static String[] ALL_DECODERS = {
            "OMX.google.h264.decoder", "OMX.google.vp9.decoder", "OMX.Nvidia.vp9.decoder",
            "OMX.MTK.VIDEO.DECODER.VP9", "OMX.amlogic.vp9.decoder.awesome", "OMX.amlogic.avc.decoder.awesome",
            "OMX.qcom.video.decoder.avc", "OMX.rk.video_decoder.avc", "OMX.allwinner.video.decoder.avc"
    };
    final static String[] SW_DECODERS = {"OMX.google"};
    final static String[] HW_DECODERS = {"OMX.amlogic", "OMX.MTK", "OMX.Nvidia", "OMX.qcom", "OMX.rk", "OMX.allwinner"};

    // Ver. 2.9.6
    //@Override
    //public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder) throws MediaCodecUtil.DecoderQueryException {
    //
    //    List<MediaCodecInfo> codecInfos = MediaCodecUtil.getDecoderInfos(
    //            mimeType, requiresSecureDecoder);
    //    // filter codecs based on blacklist template
    //    List<MediaCodecInfo> filteredCodecInfos = new ArrayList<>();
    //    for (MediaCodecInfo codecInfo: codecInfos) {
    //        Log.d(TAG, "Checking codec: " + codecInfo);
    //        boolean blacklisted = false;
    //        for (String blackListedCodec: BLACKLISTEDCODECS) {
    //            if (codecInfo != null && codecInfo.name.toLowerCase().contains(blackListedCodec.toLowerCase())) {
    //                Log.d(TAG, "Blacklisting codec: " + blackListedCodec);
    //                blacklisted = true;
    //                break;
    //            }
    //        }
    //        if (!blacklisted) {
    //            filteredCodecInfos.add(codecInfo);
    //        }
    //    }
    //    return filteredCodecInfos;
    //}

    // Exo 2.10 and up
    @Override
    public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) throws MediaCodecUtil.DecoderQueryException {

        List<MediaCodecInfo> codecInfos = MediaCodecUtil.getDecoderInfos(
                mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
        // filter codecs based on blacklist template
        List<MediaCodecInfo> filteredCodecInfos = new ArrayList<>();
        for (MediaCodecInfo codecInfo: codecInfos) {
            Log.d(TAG, "Checking codec: " + codecInfo);
            boolean blacklisted = false;
            for (String blacklistedDecoder: HW_DECODERS) {
                if (codecInfo != null && codecInfo.name.toLowerCase().startsWith(blacklistedDecoder.toLowerCase())) {
                    Log.d(TAG, "Blacklisting decoder: " + blacklistedDecoder);
                    blacklisted = true;
                    break;
                }
            }
            if (!blacklisted) {
                filteredCodecInfos.add(codecInfo);
            }
        }
        return filteredCodecInfos;
    }

    // Exo 2.10
    @Nullable
    @Override
    public MediaCodecInfo getPassthroughDecoderInfo() throws MediaCodecUtil.DecoderQueryException {
        return MediaCodecUtil.getPassthroughDecoderInfo();
    }
}