package com.liskovsoft.smartyoutubetv2.common.exoplayer.other.V2.videorenderer;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.os.Handler;
import android.util.Range;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer;
import com.google.android.exoplayer2.video.VideoRendererEventListener;
import com.liskovsoft.sharedutils.mylogger.Log;

public class AmlogicFixMediaCodecVideoRenderer extends MediaCodecVideoRenderer {
    private static final String TAG = AmlogicFixMediaCodecVideoRenderer.class.getSimpleName();

    public AmlogicFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector) {
        super(context, mediaCodecSelector);
    }

    public AmlogicFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs);
    }

    public AmlogicFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    public AmlogicFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    public AmlogicFixMediaCodecVideoRenderer(Context context, MediaCodecSelector mediaCodecSelector, long allowedJoiningTimeMs,
                                             @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, @Nullable Handler eventHandler, @Nullable VideoRendererEventListener eventListener, int maxDroppedFramesToNotify) {
        super(context, mediaCodecSelector, allowedJoiningTimeMs, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    /*
     * fix for amlogic bug - framedrop in 1080/60fps mode. need set max renderer resolution
     *
     */
    /**
     * Whether the decoder supports video with a given width, height and frame rate.
     *
     * <p>Must not be called if the device SDK version is less than 21.
     * @param codecInfo Information about the {@link MediaCodec} being configured.
     * @param width Width in pixels.
     * @param height Height in pixels.
     * @param frameRate Optional frame rate in frames per second. Ignored if set to {@link
     *     Format#NO_VALUE} or any value less than or equal to 0.
     * @return Whether the decoder supports video with the given width, height and frame rate.
     */
    private boolean isVideoSizeAndRateSupported_workaround(MediaCodecInfo codecInfo, int width, int height, double frameRate) {

        if (Util.SDK_INT >=21) {
            /*
            first try to test resolution/framerate by data in device static media_codecs.xml
            if blocks-per-second-range set correct by manufacturer, it should work ok
            */
            if (codecInfo.isVideoSizeAndRateSupportedV21(width, height, frameRate)) {
                logAssumedSupport(codecInfo, "sizeAndRate.support, " + width + "x" + height + "x" + frameRate);
                return true;
            } else {
                if (codecInfo.capabilities == null) {
                    logNoSupport(codecInfo, "sizeAndRate.caps");
                    return false;
                }
                assert codecInfo.capabilities != null;
                VideoCapabilities videoCapabilities = codecInfo.capabilities.getVideoCapabilities();
                if (videoCapabilities == null) {
                    logNoSupport(codecInfo, "sizeAndRate.vCaps");
                    return false;
                }
                /*
                 first fallback - if previous failed, because of incorrect/too low blocks-per-second-range
                 try test resolution/framerate using data in device static media_codecs_performance.xml
                 if corresponding measured-frame-rate value exist in desired resolution than we are ok
                */
                if (Util.SDK_INT >= 23) {
                    /*
                    The signaled frame rate may be slightly higher than the actual frame rate, so we take the
                    floor to avoid situations where a range check in areSizeAndRateSupported fails due to
                    slightly exceeding the limits for a standard format (e.g., 1080p at 30 fps).
                    */
                    double floorFrameRate = Math.floor(frameRate);

                    Range<Double> achivableFramerates = videoCapabilities.getAchievableFrameRatesFor(width, height);
                    if (achivableFramerates != null
                            && achivableFramerates.getUpper()!=null
                            && floorFrameRate <= achivableFramerates.getUpper()) {
                        logAssumedSupport(codecInfo, "sizeAndRate.support, " + width + "x" + height + "x" + frameRate);
                        return true;
                    }
                }
                /*
                 second and last fallback - if all previous failed,
                 try just check codec resolution support and ignore framerate
                */
                if (codecInfo.isVideoSizeAndRateSupportedV21(width, height, -1)) {
                    logAssumedSupport(codecInfo, "sizeAndRate.support, " + width + "x" + height + "x" + frameRate);
                    return true;
                } else {
                    logNoSupport(codecInfo, "sizeAndRate.support, " + width + "x" + height + "x" + frameRate + "]");
                }
            }
        } else {
            boolean isFormatSupported = false;
            try {
                isFormatSupported = width * height <= MediaCodecUtil.maxH264DecodableFrameSize();
            } catch (Exception ignored) {}
            if (!isFormatSupported) {
                logNoSupport(codecInfo, "legacyFrameSize, " + width + "x" + height);
                return false;
            } else {
                logAssumedSupport(codecInfo,
                        "sizeAndRate.support, " + width + "x" + height + "x" + frameRate + "]");
                return true;
            }
        }
        return false;
    }

    private void logNoSupport(MediaCodecInfo codecInfo, String message) {
        Log.d(TAG, "NoSupport [" + message + "]"
                +" [" + codecInfo.name + ", " + codecInfo.mimeType + "]"
                +" [" + Util.DEVICE_DEBUG_INFO + "]");
    }

    private void logAssumedSupport(MediaCodecInfo codecInfo, String message) {
        Log.d(TAG, "AssumedSupport [" + message + "]"
                +" [" + codecInfo.name + ", " + codecInfo.mimeType + "]"
                +" [" + Util.DEVICE_DEBUG_INFO + "]");
    }

    /**
     * Returns {@link CodecMaxValues} suitable for configuring a codec for {@code format} in a way
     * that will allow possible adaptation to other compatible formats in {@code streamFormats}.
     *
     * fix for amlogic buggy decoder by kruvas.
     * solve framedrops in 1080/60fps and high gpu usage / surfaceflinger cpu usage
     * by set MediaFormat.KEY_MAX_HEIGHT and MediaFormat.KEY_MAX_WIDTH
     * to codec or device max resolution instead of current video resolution
     *
     * @param codecInfo Information about the {@link MediaCodec} being configured.
     * @param format The {@link Format} for which the codec is being configured.
     * @param streamFormats The possible stream formats.
     * @return Suitable {@link CodecMaxValues}.
     */
    protected CodecMaxValues getCodecMaxValues(MediaCodecInfo codecInfo, Format format, Format[] streamFormats) {
        CodecMaxValues codecMaxValues = super.getCodecMaxValues(codecInfo, format, streamFormats);

        int maxWidth = codecMaxValues.width;
        int maxHeight = codecMaxValues.height;

        if (Util.SDK_INT >= 21 && codecInfo.isSeamlessAdaptationSupported(format, streamFormats[0], /* isNewFormatComplete= */ false)) {
            VideoCapabilities videoCapabilities = codecInfo.capabilities.getVideoCapabilities();
            if (videoCapabilities != null) {
                // get max codec resolution
                int maxSupportedWidth = videoCapabilities.getSupportedWidths().getUpper();
                int maxSupportedHeight = videoCapabilities.getSupportedHeights().getUpper();
                // check if current framerate at this resolution is supported
                if (!isVideoSizeAndRateSupported_workaround(codecInfo, maxSupportedWidth, maxSupportedHeight, format.frameRate)) {
                    // if not, then try check support 8k/4k/1080 consecutive at current resolution
                    if (maxSupportedHeight >= 4320 && isVideoSizeAndRateSupported_workaround(codecInfo, 7680, 4320, format.frameRate)) {
                        maxSupportedWidth = 7680;
                        maxSupportedHeight = 4320;
                    } else if (maxSupportedHeight >= 2160 && isVideoSizeAndRateSupported_workaround(codecInfo, 3840, 2160, format.frameRate)) {
                        maxSupportedWidth = 3840;
                        maxSupportedHeight = 2160;
                    } else if (maxSupportedHeight >= 1080 && isVideoSizeAndRateSupported_workaround(codecInfo, 1920, 1080, format.frameRate)) {
                        maxSupportedWidth = 1920;
                        maxSupportedHeight = 1080;
                    } else {
                        Log.e(TAG, "Failed to find a compatible resolution");
                        maxSupportedWidth = 1920;
                        maxSupportedHeight = 1080;
                    }
                }
                // Since we haven't passed the properties of the stream we're playing
                // down to this level, from our perspective, we could potentially
                // adapt up to 8k at any point. We thus request 8k buffers up front,
                // unless the decoder claims to not be able to do 8k, in which case
                // we're ok, since we would've rejected a 8k stream when canPlayType
                // was called, and then use those decoder values instead. We only
                // support 8k for API level 29 and above.
                if (Util.SDK_INT > 28) {
                    maxWidth = Math.min(7680, maxSupportedWidth);
                    maxHeight = Math.min(4320, maxSupportedHeight);
                } else {
                    // Android 5.0/5.1 seems not support 8K. Fallback to 4K until we get a
                    // better way to get maximum supported resolution.
                    maxWidth = Math.min(3840, maxSupportedWidth);
                    maxHeight = Math.min(2160, maxSupportedHeight);
                }
            }
        }

        return new CodecMaxValues(maxWidth, maxHeight, codecMaxValues.inputSize);
    }
}
