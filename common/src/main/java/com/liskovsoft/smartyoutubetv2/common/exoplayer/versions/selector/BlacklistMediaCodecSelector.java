package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.selector;

import android.annotation.TargetApi;
import android.media.MediaCodecList;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.renderer.CustomOverridesRenderersFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    // Name prefixes of software decoders. Used when the platform can't tell us (Android < 10).
    final static String[] SW_DECODERS = {"omx.google.", "omx.ffmpeg.", "c2.android.", "c2.google."};
    private static Map<String, Boolean> sSoftwareOnlyDecoders;

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
        // keep software decoders only
        List<MediaCodecInfo> filteredCodecInfos = new ArrayList<>();
        for (MediaCodecInfo codecInfo: codecInfos) {
            Log.d(TAG, "Checking codec: " + codecInfo);
            if (codecInfo != null && isSoftwareOnly(codecInfo.name)) {
                filteredCodecInfos.add(codecInfo);
            } else if (codecInfo != null) {
                Log.d(TAG, "Blacklisting decoder: " + codecInfo.name);
            }
        }

        // May be empty. Then the player picks another format that has a software decoder.
        return filteredCodecInfos;
    }

    private static boolean isSoftwareOnly(String decoderName) {
        if (Build.VERSION.SDK_INT >= 29) {
            Boolean softwareOnly = getSoftwareOnlyDecoders().get(decoderName);
            if (softwareOnly != null) {
                return softwareOnly;
            }
        }

        String name = decoderName.toLowerCase();
        for (String prefix: SW_DECODERS) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    @TargetApi(29)
    private static synchronized Map<String, Boolean> getSoftwareOnlyDecoders() {
        if (sSoftwareOnlyDecoders == null) {
            sSoftwareOnlyDecoders = new HashMap<>();
            try {
                for (android.media.MediaCodecInfo info: new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                    if (!info.isEncoder()) {
                        sSoftwareOnlyDecoders.put(info.getName(), info.isSoftwareOnly());
                    }
                }
            } catch (RuntimeException e) {
                // Some firmwares throw here. Fall back to name checks.
                Log.d(TAG, "Can't query decoders: " + e.getMessage());
            }
        }

        return sSoftwareOnlyDecoders;
    }

    // Exo 2.10
    @Nullable
    @Override
    public MediaCodecInfo getPassthroughDecoderInfo() throws MediaCodecUtil.DecoderQueryException {
        return MediaCodecUtil.getPassthroughDecoderInfo();
    }
}