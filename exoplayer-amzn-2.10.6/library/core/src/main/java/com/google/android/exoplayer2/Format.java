/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.ColorInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Representation of a media format.
 */
public final class Format implements Parcelable {

  /**
   * A value for various fields to indicate that the field's value is unknown or not applicable.
   */
  public static final int NO_VALUE = -1;

  /**
   * A value for {@link #subsampleOffsetUs} to indicate that subsample timestamps are relative to
   * the timestamps of their parent samples.
   */
  public static final long OFFSET_SAMPLE_RELATIVE = Long.MAX_VALUE;

  /** An identifier for the format, or null if unknown or not applicable. */
  public final @Nullable String id;
  /** The human readable label, or null if unknown or not applicable. */
  public final @Nullable String label;
  /** Track selection flags. */
  @C.SelectionFlags public final int selectionFlags;
  /** Track role flags. */
  @C.RoleFlags public final int roleFlags;
  /**
   * The average bandwidth in bits per second, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int bitrate;
  /** Codecs of the format as described in RFC 6381, or null if unknown or not applicable. */
  public final @Nullable String codecs;
  /** Metadata, or null if unknown or not applicable. */
  public final @Nullable Metadata metadata;

  // Container specific.

  /** The mime type of the container, or null if unknown or not applicable. */
  public final @Nullable String containerMimeType;

  // Elementary stream specific.

  /**
   * The mime type of the elementary stream (i.e. the individual samples), or null if unknown or not
   * applicable.
   */
  public final @Nullable String sampleMimeType;
  /**
   * The maximum size of a buffer of data (typically one sample), or {@link #NO_VALUE} if unknown or
   * not applicable.
   */
  public final int maxInputSize;
  /**
   * Initialization data that must be provided to the decoder. Will not be null, but may be empty
   * if initialization data is not required.
   */
  public final List<byte[]> initializationData;
  /** DRM initialization data if the stream is protected, or null otherwise. */
  public final @Nullable DrmInitData drmInitData;

  /**
   * For samples that contain subsamples, this is an offset that should be added to subsample
   * timestamps. A value of {@link #OFFSET_SAMPLE_RELATIVE} indicates that subsample timestamps are
   * relative to the timestamps of their parent samples.
   */
  public final long subsampleOffsetUs;

  // Video specific.

  /**
   * The width of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int width;
  /**
   * The height of the video in pixels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int height;
  /**
   * The frame rate in frames per second, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final float frameRate;
  /**
   * The clockwise rotation that should be applied to the video for it to be rendered in the correct
   * orientation, or 0 if unknown or not applicable. Only 0, 90, 180 and 270 are supported.
   */
  public final int rotationDegrees;
  /** The width to height ratio of pixels in the video, or 1.0 if unknown or not applicable. */
  public final float pixelWidthHeightRatio;
  /**
   * The stereo layout for 360/3D/VR video, or {@link #NO_VALUE} if not applicable. Valid stereo
   * modes are {@link C#STEREO_MODE_MONO}, {@link C#STEREO_MODE_TOP_BOTTOM}, {@link
   * C#STEREO_MODE_LEFT_RIGHT}, {@link C#STEREO_MODE_STEREO_MESH}.
   */
  @C.StereoMode
  public final int stereoMode;
  /** The projection data for 360/VR video, or null if not applicable. */
  public final @Nullable byte[] projectionData;
  /** The color metadata associated with the video, helps with accurate color reproduction. */
  public final @Nullable ColorInfo colorInfo;

  // Audio specific.

  /**
   * The number of audio channels, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int channelCount;
  /**
   * The audio sampling rate in Hz, or {@link #NO_VALUE} if unknown or not applicable.
   */
  public final int sampleRate;
  /**
   * The encoding for PCM audio streams. If {@link #sampleMimeType} is {@link MimeTypes#AUDIO_RAW}
   * then one of {@link C#ENCODING_PCM_8BIT}, {@link C#ENCODING_PCM_16BIT}, {@link
   * C#ENCODING_PCM_24BIT}, {@link C#ENCODING_PCM_32BIT}, {@link C#ENCODING_PCM_FLOAT}, {@link
   * C#ENCODING_PCM_MU_LAW} or {@link C#ENCODING_PCM_A_LAW}. Set to {@link #NO_VALUE} for other
   * media types.
   */
  public final @C.PcmEncoding int pcmEncoding;
  /**
   * The number of frames to trim from the start of the decoded audio stream, or 0 if not
   * applicable.
   */
  public final int encoderDelay;
  /**
   * The number of frames to trim from the end of the decoded audio stream, or 0 if not applicable.
   */
  public final int encoderPadding;

  // Audio and text specific.

  /** The language as an IETF BCP 47 conformant tag, or null if unknown or not applicable. */
  public final @Nullable String language;
  /**
   * The Accessibility channel, or {@link #NO_VALUE} if not known or applicable.
   */
  public final int accessibilityChannel;

  // Lazily initialized hashcode.
  private int hashCode;

  // Video.

  /**
   * @deprecated Use {@link #createVideoContainerFormat(String, String, String, String, String,
   *     Metadata, int, int, int, float, List, int, int)} instead.
   */
  @Deprecated
  public static Format createVideoContainerFormat(
      @Nullable String id,
      @Nullable String containerMimeType,
      String sampleMimeType,
      String codecs,
      int bitrate,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      @C.SelectionFlags int selectionFlags) {
    return createVideoContainerFormat(
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
        initializationData,
        selectionFlags,
        /* roleFlags= */ 0);
  }

  public static Format createVideoContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      String sampleMimeType,
      String codecs,
      @Nullable Metadata metadata,
      int bitrate,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        initializationData,
        /* drmInitData= */ null,
        OFFSET_SAMPLE_RELATIVE,
        width,
        height,
        frameRate,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        /* language= */ null,
        /* accessibilityChannel= */ NO_VALUE);
  }

  public static Format createVideoSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData) {
    return createVideoSampleFormat(
        id,
        sampleMimeType,
        codecs,
        bitrate,
        maxInputSize,
        width,
        height,
        frameRate,
        initializationData,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        drmInitData);
  }

  public static Format createVideoSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      int rotationDegrees,
      float pixelWidthHeightRatio,
      @Nullable DrmInitData drmInitData) {
    return createVideoSampleFormat(
        id,
        sampleMimeType,
        codecs,
        bitrate,
        maxInputSize,
        width,
        height,
        frameRate,
        initializationData,
        rotationDegrees,
        pixelWidthHeightRatio,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        drmInitData);
  }

  public static Format createVideoSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int width,
      int height,
      float frameRate,
      @Nullable List<byte[]> initializationData,
      int rotationDegrees,
      float pixelWidthHeightRatio,
      byte[] projectionData,
      @C.StereoMode int stereoMode,
      @Nullable ColorInfo colorInfo,
      @Nullable DrmInitData drmInitData) {
    return new Format(
        id,
        /* label= */ null,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        bitrate,
        codecs,
        /* metadata= */ null,
        /* containerMimeType= */ null,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        OFFSET_SAMPLE_RELATIVE,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        /* language= */ null,
        /* accessibilityChannel= */ NO_VALUE);
  }

  // Audio.

  /**
   * @deprecated Use {@link #createAudioContainerFormat(String, String, String, String, String,
   *     Metadata, int, int, int, List, int, int, String)} instead.
   */
  @Deprecated
  public static Format createAudioContainerFormat(
      @Nullable String id,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int channelCount,
      int sampleRate,
      @Nullable List<byte[]> initializationData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return createAudioContainerFormat(
        id,
        /* label= */ null,
        containerMimeType,
        sampleMimeType,
        codecs,
        /* metadata= */ null,
        bitrate,
        channelCount,
        sampleRate,
        initializationData,
        selectionFlags,
        /* roleFlags= */ 0,
        language);
  }

  public static Format createAudioContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      @Nullable Metadata metadata,
      int bitrate,
      int channelCount,
      int sampleRate,
      @Nullable List<byte[]> initializationData,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        initializationData,
        /* drmInitData= */ null,
        OFFSET_SAMPLE_RELATIVE,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        channelCount,
        sampleRate,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        language,
        /* accessibilityChannel= */ NO_VALUE);
  }

  public static Format createAudioSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int channelCount,
      int sampleRate,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return createAudioSampleFormat(
        id,
        sampleMimeType,
        codecs,
        bitrate,
        maxInputSize,
        channelCount,
        sampleRate,
        /* pcmEncoding= */ NO_VALUE,
        initializationData,
        drmInitData,
        selectionFlags,
        language);
  }

  public static Format createAudioSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int channelCount,
      int sampleRate,
      @C.PcmEncoding int pcmEncoding,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return createAudioSampleFormat(
        id,
        sampleMimeType,
        codecs,
        bitrate,
        maxInputSize,
        channelCount,
        sampleRate,
        pcmEncoding,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        initializationData,
        drmInitData,
        selectionFlags,
        language,
        /* metadata= */ null);
  }

  public static Format createAudioSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      int maxInputSize,
      int channelCount,
      int sampleRate,
      @C.PcmEncoding int pcmEncoding,
      int encoderDelay,
      int encoderPadding,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      @Nullable Metadata metadata) {
    return new Format(
        id,
        /* label= */ null,
        selectionFlags,
        /* roleFlags= */ 0,
        bitrate,
        codecs,
        metadata,
        /* containerMimeType= */ null,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        OFFSET_SAMPLE_RELATIVE,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        /* accessibilityChannel= */ NO_VALUE);
  }

  // Text.

  public static Format createTextContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language) {
    return createTextContainerFormat(
        id,
        label,
        containerMimeType,
        sampleMimeType,
        codecs,
        bitrate,
        selectionFlags,
        roleFlags,
        language,
        /* accessibilityChannel= */ NO_VALUE);
  }

  public static Format createTextContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language,
      int accessibilityChannel) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        /* metadata= */ null,
        containerMimeType,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        /* initializationData= */ null,
        /* drmInitData= */ null,
        OFFSET_SAMPLE_RELATIVE,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        language,
        accessibilityChannel);
  }

  public static Format createTextSampleFormat(
      @Nullable String id,
      String sampleMimeType,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return createTextSampleFormat(id, sampleMimeType, selectionFlags, language, null);
  }

  public static Format createTextSampleFormat(
      @Nullable String id,
      String sampleMimeType,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      @Nullable DrmInitData drmInitData) {
    return createTextSampleFormat(
        id,
        sampleMimeType,
        /* codecs= */ null,
        /* bitrate= */ NO_VALUE,
        selectionFlags,
        language,
        NO_VALUE,
        drmInitData,
        OFFSET_SAMPLE_RELATIVE,
        Collections.emptyList());
  }

  public static Format createTextSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      int accessibilityChannel,
      @Nullable DrmInitData drmInitData) {
    return createTextSampleFormat(
        id,
        sampleMimeType,
        codecs,
        bitrate,
        selectionFlags,
        language,
        accessibilityChannel,
        drmInitData,
        OFFSET_SAMPLE_RELATIVE,
        Collections.emptyList());
  }

  public static Format createTextSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      @Nullable DrmInitData drmInitData,
      long subsampleOffsetUs) {
    return createTextSampleFormat(
        id,
        sampleMimeType,
        codecs,
        bitrate,
        selectionFlags,
        language,
        /* accessibilityChannel= */ NO_VALUE,
        drmInitData,
        subsampleOffsetUs,
        Collections.emptyList());
  }

  public static Format createTextSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language,
      int accessibilityChannel,
      @Nullable DrmInitData drmInitData,
      long subsampleOffsetUs,
      List<byte[]> initializationData) {
    return new Format(
        id,
        /* label= */ null,
        selectionFlags,
        /* roleFlags= */ 0,
        bitrate,
        codecs,
        /* metadata= */ null,
        /* containerMimeType= */ null,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        language,
        accessibilityChannel);
  }

  // Image.

  public static Format createImageSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @Nullable List<byte[]> initializationData,
      @Nullable String language,
      @Nullable DrmInitData drmInitData) {
    return new Format(
        id,
        /* label= */ null,
        selectionFlags,
        /* roleFlags= */ 0,
        bitrate,
        codecs,
        /* metadata=*/ null,
        /* containerMimeType= */ null,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        initializationData,
        drmInitData,
        OFFSET_SAMPLE_RELATIVE,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        language,
        /* accessibilityChannel= */ NO_VALUE);
  }

  // Generic.

  @Deprecated
  public static Format createContainerFormat(
      @Nullable String id,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {
    return createContainerFormat(
        id,
        /* label= */ null,
        containerMimeType,
        sampleMimeType,
        codecs,
        bitrate,
        selectionFlags,
        /* roleFlags= */ 0,
        language);
  }

  public static Format createContainerFormat(
      @Nullable String id,
      @Nullable String label,
      @Nullable String containerMimeType,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      @Nullable String language) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        /* metadata= */ null,
        containerMimeType,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        /* initializationData= */ null,
        /* drmInitData= */ null,
        OFFSET_SAMPLE_RELATIVE,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        language,
        /* accessibilityChannel= */ NO_VALUE);
  }

  public static Format createSampleFormat(
      @Nullable String id, @Nullable String sampleMimeType, long subsampleOffsetUs) {
    return new Format(
        id,
        /* label= */ null,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        /* bitrate= */ NO_VALUE,
        /* codecs= */ null,
        /* metadata= */ null,
        /* containerMimeType= */ null,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        /* initializationData= */ null,
        /* drmInitData= */ null,
        subsampleOffsetUs,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        /* language= */ null,
        /* accessibilityChannel= */ NO_VALUE);
  }

  public static Format createSampleFormat(
      @Nullable String id,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      int bitrate,
      @Nullable DrmInitData drmInitData) {
    return new Format(
        id,
        /* label= */ null,
        /* selectionFlags= */ 0,
        /* roleFlags= */ 0,
        bitrate,
        codecs,
        /* metadata= */ null,
        /* containerMimeType= */ null,
        sampleMimeType,
        /* maxInputSize= */ NO_VALUE,
        /* initializationData= */ null,
        drmInitData,
        OFFSET_SAMPLE_RELATIVE,
        /* width= */ NO_VALUE,
        /* height= */ NO_VALUE,
        /* frameRate= */ NO_VALUE,
        /* rotationDegrees= */ NO_VALUE,
        /* pixelWidthHeightRatio= */ NO_VALUE,
        /* projectionData= */ null,
        /* stereoMode= */ NO_VALUE,
        /* colorInfo= */ null,
        /* channelCount= */ NO_VALUE,
        /* sampleRate= */ NO_VALUE,
        /* pcmEncoding= */ NO_VALUE,
        /* encoderDelay= */ NO_VALUE,
        /* encoderPadding= */ NO_VALUE,
        /* language= */ null,
        /* accessibilityChannel= */ NO_VALUE);
  }

  /* package */ Format(
      @Nullable String id,
      @Nullable String label,
      @C.SelectionFlags int selectionFlags,
      @C.RoleFlags int roleFlags,
      int bitrate,
      @Nullable String codecs,
      @Nullable Metadata metadata,
      // Container specific.
      @Nullable String containerMimeType,
      // Elementary stream specific.
      @Nullable String sampleMimeType,
      int maxInputSize,
      @Nullable List<byte[]> initializationData,
      @Nullable DrmInitData drmInitData,
      long subsampleOffsetUs,
      // Video specific.
      int width,
      int height,
      float frameRate,
      int rotationDegrees,
      float pixelWidthHeightRatio,
      @Nullable byte[] projectionData,
      @C.StereoMode int stereoMode,
      @Nullable ColorInfo colorInfo,
      // Audio specific.
      int channelCount,
      int sampleRate,
      @C.PcmEncoding int pcmEncoding,
      int encoderDelay,
      int encoderPadding,
      // Audio and text specific.
      @Nullable String language,
      int accessibilityChannel) {
    this.id = id;
    this.label = label;
    this.selectionFlags = selectionFlags;
    this.roleFlags = roleFlags;
    this.bitrate = bitrate;
    this.codecs = codecs;
    this.metadata = metadata;
    // Container specific.
    this.containerMimeType = containerMimeType;
    // Elementary stream specific.
    this.sampleMimeType = sampleMimeType;
    this.maxInputSize = maxInputSize;
    this.initializationData =
        initializationData == null ? Collections.emptyList() : initializationData;
    this.drmInitData = drmInitData;
    this.subsampleOffsetUs = subsampleOffsetUs;
    // Video specific.
    this.width = width;
    this.height = height;
    this.frameRate = frameRate;
    this.rotationDegrees = rotationDegrees == Format.NO_VALUE ? 0 : rotationDegrees;
    this.pixelWidthHeightRatio =
        pixelWidthHeightRatio == Format.NO_VALUE ? 1 : pixelWidthHeightRatio;
    this.projectionData = projectionData;
    this.stereoMode = stereoMode;
    this.colorInfo = colorInfo;
    // Audio specific.
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.pcmEncoding = pcmEncoding;
    this.encoderDelay = encoderDelay == Format.NO_VALUE ? 0 : encoderDelay;
    this.encoderPadding = encoderPadding == Format.NO_VALUE ? 0 : encoderPadding;
    // Audio and text specific.
    this.language = Util.normalizeLanguageCode(language);
    this.accessibilityChannel = accessibilityChannel;
  }

  @SuppressWarnings("ResourceType")
  /* package */ Format(Parcel in) {
    id = in.readString();
    label = in.readString();
    selectionFlags = in.readInt();
    roleFlags = in.readInt();
    bitrate = in.readInt();
    codecs = in.readString();
    metadata = in.readParcelable(Metadata.class.getClassLoader());
    // Container specific.
    containerMimeType = in.readString();
    // Elementary stream specific.
    sampleMimeType = in.readString();
    maxInputSize = in.readInt();
    int initializationDataSize = in.readInt();
    initializationData = new ArrayList<>(initializationDataSize);
    for (int i = 0; i < initializationDataSize; i++) {
      initializationData.add(in.createByteArray());
    }
    drmInitData = in.readParcelable(DrmInitData.class.getClassLoader());
    subsampleOffsetUs = in.readLong();
    // Video specific.
    width = in.readInt();
    height = in.readInt();
    frameRate = in.readFloat();
    rotationDegrees = in.readInt();
    pixelWidthHeightRatio = in.readFloat();
    boolean hasProjectionData = Util.readBoolean(in);
    projectionData = hasProjectionData ? in.createByteArray() : null;
    stereoMode = in.readInt();
    colorInfo = in.readParcelable(ColorInfo.class.getClassLoader());
    // Audio specific.
    channelCount = in.readInt();
    sampleRate = in.readInt();
    pcmEncoding = in.readInt();
    encoderDelay = in.readInt();
    encoderPadding = in.readInt();
    // Audio and text specific.
    language = in.readString();
    accessibilityChannel = in.readInt();
  }

  public Format copyWithMaxInputSize(int maxInputSize) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithSubsampleOffsetUs(long subsampleOffsetUs) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithLabel(@Nullable String label) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithContainerInfo(
      @Nullable String id,
      @Nullable String label,
      @Nullable String sampleMimeType,
      @Nullable String codecs,
      @Nullable Metadata metadata,
      int bitrate,
      int width,
      int height,
      float frameRate, // MOD: add fps to hls format info
      int channelCount,
      @C.SelectionFlags int selectionFlags,
      @Nullable String language) {

    if (this.metadata != null) {
      metadata = this.metadata.copyWithAppendedEntriesFrom(metadata);
    }

    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  @SuppressWarnings("ReferenceEquality")
  public Format copyWithManifestFormatInfo(Format manifestFormat) {
    if (this == manifestFormat) {
      // No need to copy from ourselves.
      return this;
    }

    int trackType = MimeTypes.getTrackType(sampleMimeType);

    // Use manifest value only.
    String id = manifestFormat.id;

    // Prefer manifest values, but fill in from sample format if missing.
    String label = manifestFormat.label != null ? manifestFormat.label : this.label;
    String language = this.language;
    if ((trackType == C.TRACK_TYPE_TEXT || trackType == C.TRACK_TYPE_AUDIO)
        && manifestFormat.language != null) {
      language = manifestFormat.language;
    }

    // Prefer sample format values, but fill in from manifest if missing.
    int bitrate = this.bitrate == NO_VALUE ? manifestFormat.bitrate : this.bitrate;
    String codecs = this.codecs;
    if (codecs == null) {
      // The manifest format may be muxed, so filter only codecs of this format's type. If we still
      // have more than one codec then we're unable to uniquely identify which codec to fill in.
      String codecsOfType = Util.getCodecsOfType(manifestFormat.codecs, trackType);
      if (Util.splitCodecs(codecsOfType).length == 1) {
        codecs = codecsOfType;
      }
    }

    Metadata metadata =
        this.metadata == null
            ? manifestFormat.metadata
            : this.metadata.copyWithAppendedEntriesFrom(manifestFormat.metadata);

    float frameRate = this.frameRate;
    if (frameRate == NO_VALUE && trackType == C.TRACK_TYPE_VIDEO) {
      frameRate = manifestFormat.frameRate;
    }

    // Merge manifest and sample format values.
    @C.SelectionFlags int selectionFlags = this.selectionFlags | manifestFormat.selectionFlags;
    @C.RoleFlags int roleFlags = this.roleFlags | manifestFormat.roleFlags;
    DrmInitData drmInitData =
        DrmInitData.createSessionCreationData(manifestFormat.drmInitData, this.drmInitData);

    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithGaplessInfo(int encoderDelay, int encoderPadding) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithFrameRate(float frameRate) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithDrmInitData(@Nullable DrmInitData drmInitData) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithMetadata(@Nullable Metadata metadata) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithRotationDegrees(int rotationDegrees) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  public Format copyWithBitrate(int bitrate) {
    return new Format(
        id,
        label,
        selectionFlags,
        roleFlags,
        bitrate,
        codecs,
        metadata,
        containerMimeType,
        sampleMimeType,
        maxInputSize,
        initializationData,
        drmInitData,
        subsampleOffsetUs,
        width,
        height,
        frameRate,
        rotationDegrees,
        pixelWidthHeightRatio,
        projectionData,
        stereoMode,
        colorInfo,
        channelCount,
        sampleRate,
        pcmEncoding,
        encoderDelay,
        encoderPadding,
        language,
        accessibilityChannel);
  }

  /**
   * Returns the number of pixels if this is a video format whose {@link #width} and {@link #height}
   * are known, or {@link #NO_VALUE} otherwise
   */
  public int getPixelCount() {
    return width == NO_VALUE || height == NO_VALUE ? NO_VALUE : (width * height);
  }

  @Override
  public String toString() {
    return "Format("
        + id
        + ", "
        + label
        + ", "
        + containerMimeType
        + ", "
        + sampleMimeType
        + ", "
        + codecs
        + ", "
        + bitrate
        + ", "
        + language
        + ", ["
        + width
        + ", "
        + height
        + ", "
        + frameRate
        + "]"
        + ", ["
        + channelCount
        + ", "
        + sampleRate
        + "])";
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      // Some fields for which hashing is expensive are deliberately omitted.
      int result = 17;
      result = 31 * result + (id == null ? 0 : id.hashCode());
      result = 31 * result + (label != null ? label.hashCode() : 0);
      result = 31 * result + selectionFlags;
      result = 31 * result + roleFlags;
      result = 31 * result + bitrate;
      result = 31 * result + (codecs == null ? 0 : codecs.hashCode());
      result = 31 * result + (metadata == null ? 0 : metadata.hashCode());
      // Container specific.
      result = 31 * result + (containerMimeType == null ? 0 : containerMimeType.hashCode());
      // Elementary stream specific.
      result = 31 * result + (sampleMimeType == null ? 0 : sampleMimeType.hashCode());
      result = 31 * result + maxInputSize;
      // [Omitted] initializationData.
      // [Omitted] drmInitData.
      result = 31 * result + (int) subsampleOffsetUs;
      // Video specific.
      result = 31 * result + width;
      result = 31 * result + height;
      result = 31 * result + Float.floatToIntBits(frameRate);
      result = 31 * result + rotationDegrees;
      result = 31 * result + Float.floatToIntBits(pixelWidthHeightRatio);
      // [Omitted] projectionData.
      result = 31 * result + stereoMode;
      // [Omitted] colorInfo.
      // Audio specific.
      result = 31 * result + channelCount;
      result = 31 * result + sampleRate;
      result = 31 * result + pcmEncoding;
      result = 31 * result + encoderDelay;
      result = 31 * result + encoderPadding;
      // Audio and text specific.
      result = 31 * result + (language == null ? 0 : language.hashCode());
      result = 31 * result + accessibilityChannel;
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Format other = (Format) obj;
    if (hashCode != 0 && other.hashCode != 0 && hashCode != other.hashCode) {
      return false;
    }
    // Field equality checks ordered by type, with the cheapest checks first.
    return selectionFlags == other.selectionFlags
        && roleFlags == other.roleFlags
        && bitrate == other.bitrate
        && maxInputSize == other.maxInputSize
        && subsampleOffsetUs == other.subsampleOffsetUs
        && width == other.width
        && height == other.height
        && rotationDegrees == other.rotationDegrees
        && stereoMode == other.stereoMode
        && channelCount == other.channelCount
        && sampleRate == other.sampleRate
        && pcmEncoding == other.pcmEncoding
        && encoderDelay == other.encoderDelay
        && encoderPadding == other.encoderPadding
        && accessibilityChannel == other.accessibilityChannel
        && Float.compare(frameRate, other.frameRate) == 0
        && Float.compare(pixelWidthHeightRatio, other.pixelWidthHeightRatio) == 0
        && Util.areEqual(id, other.id)
        && Util.areEqual(label, other.label)
        && Util.areEqual(codecs, other.codecs)
        && Util.areEqual(containerMimeType, other.containerMimeType)
        && Util.areEqual(sampleMimeType, other.sampleMimeType)
        && Util.areEqual(language, other.language)
        && Arrays.equals(projectionData, other.projectionData)
        && Util.areEqual(metadata, other.metadata)
        && Util.areEqual(colorInfo, other.colorInfo)
        && Util.areEqual(drmInitData, other.drmInitData)
        && initializationDataEquals(other);
  }

  /**
   * Returns whether the {@link #initializationData}s belonging to this format and {@code other} are
   * equal.
   *
   * @param other The other format whose {@link #initializationData} is being compared.
   * @return Whether the {@link #initializationData}s belonging to this format and {@code other} are
   *     equal.
   */
  public boolean initializationDataEquals(Format other) {
    if (initializationData.size() != other.initializationData.size()) {
      return false;
    }
    for (int i = 0; i < initializationData.size(); i++) {
      if (!Arrays.equals(initializationData.get(i), other.initializationData.get(i))) {
        return false;
      }
    }
    return true;
  }

  // Utility methods

  /** Returns a prettier {@link String} than {@link #toString()}, intended for logging. */
  public static String toLogString(@Nullable Format format) {
    if (format == null) {
      return "null";
    }
    StringBuilder builder = new StringBuilder();
    builder.append("id=").append(format.id).append(", mimeType=").append(format.sampleMimeType);
    if (format.bitrate != Format.NO_VALUE) {
      builder.append(", bitrate=").append(format.bitrate);
    }
    if (format.codecs != null) {
      builder.append(", codecs=").append(format.codecs);
    }
    if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE) {
      builder.append(", res=").append(format.width).append("x").append(format.height);
    }
    if (format.frameRate != Format.NO_VALUE) {
      builder.append(", fps=").append(format.frameRate);
    }
    if (format.channelCount != Format.NO_VALUE) {
      builder.append(", channels=").append(format.channelCount);
    }
    if (format.sampleRate != Format.NO_VALUE) {
      builder.append(", sample_rate=").append(format.sampleRate);
    }
    if (format.language != null) {
      builder.append(", language=").append(format.language);
    }
    if (format.label != null) {
      builder.append(", label=").append(format.label);
    }
    return builder.toString();
  }

  // Parcelable implementation.

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(id);
    dest.writeString(label);
    dest.writeInt(selectionFlags);
    dest.writeInt(roleFlags);
    dest.writeInt(bitrate);
    dest.writeString(codecs);
    dest.writeParcelable(metadata, 0);
    // Container specific.
    dest.writeString(containerMimeType);
    // Elementary stream specific.
    dest.writeString(sampleMimeType);
    dest.writeInt(maxInputSize);
    int initializationDataSize = initializationData.size();
    dest.writeInt(initializationDataSize);
    for (int i = 0; i < initializationDataSize; i++) {
      dest.writeByteArray(initializationData.get(i));
    }
    dest.writeParcelable(drmInitData, 0);
    dest.writeLong(subsampleOffsetUs);
    // Video specific.
    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeFloat(frameRate);
    dest.writeInt(rotationDegrees);
    dest.writeFloat(pixelWidthHeightRatio);
    Util.writeBoolean(dest, projectionData != null);
    if (projectionData != null) {
      dest.writeByteArray(projectionData);
    }
    dest.writeInt(stereoMode);
    dest.writeParcelable(colorInfo, flags);
    // Audio specific.
    dest.writeInt(channelCount);
    dest.writeInt(sampleRate);
    dest.writeInt(pcmEncoding);
    dest.writeInt(encoderDelay);
    dest.writeInt(encoderPadding);
    // Audio and text specific.
    dest.writeString(language);
    dest.writeInt(accessibilityChannel);
  }

  public static final Creator<Format> CREATOR = new Creator<Format>() {

    @Override
    public Format createFromParcel(Parcel in) {
      return new Format(in);
    }

    @Override
    public Format[] newArray(int size) {
      return new Format[size];
    }

  };
}
