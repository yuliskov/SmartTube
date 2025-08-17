package com.google.android.exoplayer2.source.sabr.manifest;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.MimeTypes;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import java.util.List;

public class SabrManifestParser {
    public SabrManifest parse(MediaItemFormatInfo formatInfo) {
        // TODO: Convert format info to the manifest
        return new SabrManifest(
                -1,
                -1,
                -1,
                -1,
                -1,
                -1,
                null);
    }

    protected Format buildFormat(
            String id,
            String containerMimeType,
            int width,
            int height,
            float frameRate,
            int audioChannels,
            int audioSamplingRate,
            int bitrate,
            String language,
            String codecs) {
        String sampleMimeType = getSampleMimeType(containerMimeType, codecs);
        @C.SelectionFlags int selectionFlags = C.SELECTION_FLAG_DEFAULT;
        @C.RoleFlags int roleFlags = C.ROLE_FLAG_MAIN;
        if (sampleMimeType != null) {
            if (MimeTypes.isVideo(sampleMimeType)) {
                return Format.createVideoContainerFormat(
                        id,
                        /* label= */ null,
                        containerMimeType,
                        sampleMimeType,
                        codecs,
                        /* metadata= */ null,
                        bitrate,
                        width,
                        height,
                        frameRate,
                        /* initializationData= */ null,
                        selectionFlags,
                        roleFlags);
            } else if (MimeTypes.isAudio(sampleMimeType)) {
                return Format.createAudioContainerFormat(
                        id,
                        /* label= */ null,
                        containerMimeType,
                        sampleMimeType,
                        codecs,
                        /* metadata= */ null,
                        bitrate,
                        audioChannels,
                        audioSamplingRate,
                        /* initializationData= */ null,
                        selectionFlags,
                        roleFlags,
                        language);
            } else if (mimeTypeIsRawText(sampleMimeType)) {
                return Format.createTextContainerFormat(
                        id,
                        /* label= */ null,
                        containerMimeType,
                        sampleMimeType,
                        codecs,
                        bitrate,
                        selectionFlags,
                        roleFlags,
                        language,
                        Format.NO_VALUE);
            }
        }
        return Format.createContainerFormat(
                id,
                /* label= */ null,
                containerMimeType,
                sampleMimeType,
                codecs,
                bitrate,
                selectionFlags,
                roleFlags,
                language);
    }

    /**
     * Derives a sample mimeType from a container mimeType and codecs attribute.
     *
     * @param containerMimeType The mimeType of the container.
     * @param codecs The codecs attribute.
     * @return The derived sample mimeType, or null if it could not be derived.
     */
    private static String getSampleMimeType(String containerMimeType, String codecs) {
        if (MimeTypes.isAudio(containerMimeType)) {
            return MimeTypes.getAudioMediaMimeType(codecs);
        } else if (MimeTypes.isVideo(containerMimeType)) {
            return MimeTypes.getVideoMediaMimeType(codecs);
        } else if (mimeTypeIsRawText(containerMimeType)) {
            return containerMimeType;
        } else if (MimeTypes.APPLICATION_MP4.equals(containerMimeType)) {
            if (codecs != null) {
                if (codecs.startsWith("stpp")) {
                    return MimeTypes.APPLICATION_TTML;
                } else if (codecs.startsWith("wvtt")) {
                    return MimeTypes.APPLICATION_MP4VTT;
                }
            }
        } else if (MimeTypes.APPLICATION_RAWCC.equals(containerMimeType)) {
            if (codecs != null) {
                if (codecs.contains("cea708")) {
                    return MimeTypes.APPLICATION_CEA708;
                } else if (codecs.contains("eia608") || codecs.contains("cea608")) {
                    return MimeTypes.APPLICATION_CEA608;
                }
            }
            return null;
        }
        return null;
    }

    /**
     * Returns whether a mimeType is a text sample mimeType.
     *
     * @param mimeType The mimeType.
     * @return Whether the mimeType is a text sample mimeType.
     */
    private static boolean mimeTypeIsRawText(String mimeType) {
        return MimeTypes.isText(mimeType)
                || MimeTypes.APPLICATION_TTML.equals(mimeType)
                || MimeTypes.APPLICATION_MP4VTT.equals(mimeType)
                || MimeTypes.APPLICATION_CEA708.equals(mimeType)
                || MimeTypes.APPLICATION_CEA608.equals(mimeType);
    }
}
