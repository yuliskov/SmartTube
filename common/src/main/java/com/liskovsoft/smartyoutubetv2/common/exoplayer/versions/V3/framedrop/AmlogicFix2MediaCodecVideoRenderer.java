package com.liskovsoft.smartyoutubetv2.common.exoplayer.versions.V3.framedrop;

import android.content.Context;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;

/**
 * <a href="https://github.com/google/ExoPlayer/issues/5003">More info</a>
 */
public class AmlogicFix2MediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    private static final String TAG = AmlogicFix2MediaCodecVideoRenderer.class.getSimpleName();

    public AmlogicFix2MediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                               boolean enableDecoderFallback, @Nullable Handler eventHandler,
                                               @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @Override
    protected CodecMaxValues getCodecMaxValues(
            MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        CodecMaxValues maxValues =
                super.getCodecMaxValues(codecInfo, format, streamFormats);

        if (maxValues.width < 1920 || maxValues.height < 1089) {
            Log.d(TAG, "Applying Amlogic fix...");
            return new CodecMaxValues(
                    Math.max(maxValues.width, 1920),
                    Math.max(maxValues.height, 1089),
                    maxValues.inputSize);
        }

        return maxValues;
    }
}
