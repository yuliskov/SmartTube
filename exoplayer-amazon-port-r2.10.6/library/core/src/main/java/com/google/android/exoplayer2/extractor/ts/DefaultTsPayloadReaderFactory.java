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
package com.google.android.exoplayer2.extractor.ts;

import androidx.annotation.IntDef;
import android.util.SparseArray;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.EsInfo;
import com.google.android.exoplayer2.text.cea.Cea708InitializationData;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link TsPayloadReader.Factory} implementation.
 */
public final class DefaultTsPayloadReaderFactory implements TsPayloadReader.Factory {

  /**
   * Flags controlling elementary stream readers' behavior. Possible flag values are {@link
   * #FLAG_ALLOW_NON_IDR_KEYFRAMES}, {@link #FLAG_IGNORE_AAC_STREAM}, {@link
   * #FLAG_IGNORE_H264_STREAM}, {@link #FLAG_DETECT_ACCESS_UNITS}, {@link
   * #FLAG_IGNORE_SPLICE_INFO_STREAM}, {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} and {@link
   * #FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {
        FLAG_ALLOW_NON_IDR_KEYFRAMES,
        FLAG_IGNORE_AAC_STREAM,
        FLAG_IGNORE_H264_STREAM,
        FLAG_DETECT_ACCESS_UNITS,
        FLAG_IGNORE_SPLICE_INFO_STREAM,
        FLAG_OVERRIDE_CAPTION_DESCRIPTORS,
        FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
      })
  public @interface Flags {}

  /**
   * When extracting H.264 samples, whether to treat samples consisting of non-IDR I slices as
   * synchronization samples (key-frames).
   */
  public static final int FLAG_ALLOW_NON_IDR_KEYFRAMES = 1;
  /**
   * Prevents the creation of {@link AdtsReader} and {@link LatmReader} instances. This flag should
   * be enabled if the transport stream contains no packets for an AAC elementary stream that is
   * declared in the PMT.
   */
  public static final int FLAG_IGNORE_AAC_STREAM = 1 << 1;
  /**
   * Prevents the creation of {@link H264Reader} instances. This flag should be enabled if the
   * transport stream contains no packets for an H.264 elementary stream that is declared in the
   * PMT.
   */
  public static final int FLAG_IGNORE_H264_STREAM = 1 << 2;
  /**
   * When extracting H.264 samples, whether to split the input stream into access units (samples)
   * based on slice headers. This flag should be disabled if the stream contains access unit
   * delimiters (AUDs).
   */
  public static final int FLAG_DETECT_ACCESS_UNITS = 1 << 3;
  /** Prevents the creation of {@link SpliceInfoSectionReader} instances. */
  public static final int FLAG_IGNORE_SPLICE_INFO_STREAM = 1 << 4;
  /**
   * Whether the list of {@code closedCaptionFormats} passed to {@link
   * DefaultTsPayloadReaderFactory#DefaultTsPayloadReaderFactory(int, List)} should be used in spite
   * of any closed captions service descriptors. If this flag is disabled, {@code
   * closedCaptionFormats} will be ignored if the PMT contains closed captions service descriptors.
   */
  public static final int FLAG_OVERRIDE_CAPTION_DESCRIPTORS = 1 << 5;
  /**
   * Sets whether HDMV DTS audio streams will be handled. If this flag is set, SCTE subtitles will
   * not be detected, as they share the same elementary stream type as HDMV DTS.
   */
  public static final int FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS = 1 << 6;

  private static final int DESCRIPTOR_TAG_CAPTION_SERVICE = 0x86;

  @Flags private final int flags;
  private final List<Format> closedCaptionFormats;

  public DefaultTsPayloadReaderFactory() {
    this(0);
  }

  /**
   * @param flags A combination of {@code FLAG_*} values that control the behavior of the created
   *     readers.
   */
  public DefaultTsPayloadReaderFactory(@Flags int flags) {
    this(
        flags,
        Collections.singletonList(
            Format.createTextSampleFormat(null, MimeTypes.APPLICATION_CEA608, 0, null)));
  }

  /**
   * @param flags A combination of {@code FLAG_*} values that control the behavior of the created
   *     readers.
   * @param closedCaptionFormats {@link Format}s to be exposed by payload readers for streams with
   *     embedded closed captions when no caption service descriptors are provided. If
   *     {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} is set, {@code closedCaptionFormats} overrides
   *     any descriptor information. If not set, and {@code closedCaptionFormats} is empty, a
   *     closed caption track with {@link Format#accessibilityChannel} {@link Format#NO_VALUE} will
   *     be exposed.
   */
  public DefaultTsPayloadReaderFactory(@Flags int flags, List<Format> closedCaptionFormats) {
    this.flags = flags;
    this.closedCaptionFormats = closedCaptionFormats;
  }

  @Override
  public SparseArray<TsPayloadReader> createInitialPayloadReaders() {
    return new SparseArray<>();
  }

  @Override
  public TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo) {
    switch (streamType) {
      case TsExtractor.TS_STREAM_TYPE_MPA:
      case TsExtractor.TS_STREAM_TYPE_MPA_LSF:
        return new PesReader(new MpegAudioReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AAC_ADTS:
        return isSet(FLAG_IGNORE_AAC_STREAM)
            ? null : new PesReader(new AdtsReader(false, esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AAC_LATM:
        return isSet(FLAG_IGNORE_AAC_STREAM)
            ? null : new PesReader(new LatmReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AC3:
      case TsExtractor.TS_STREAM_TYPE_E_AC3:
        return new PesReader(new Ac3Reader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_AC4:
        return new PesReader(new Ac4Reader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_HDMV_DTS:
        if (!isSet(FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)) {
          return null;
        }
        // Fall through.
      case TsExtractor.TS_STREAM_TYPE_DTS:
        return new PesReader(new DtsReader(esInfo.language));
      case TsExtractor.TS_STREAM_TYPE_H262:
        return new PesReader(new H262Reader(buildUserDataReader(esInfo)));
      case TsExtractor.TS_STREAM_TYPE_H264:
        return isSet(FLAG_IGNORE_H264_STREAM) ? null
            : new PesReader(new H264Reader(buildSeiReader(esInfo),
                isSet(FLAG_ALLOW_NON_IDR_KEYFRAMES), isSet(FLAG_DETECT_ACCESS_UNITS)));
      case TsExtractor.TS_STREAM_TYPE_H265:
        return new PesReader(new H265Reader(buildSeiReader(esInfo)));
      case TsExtractor.TS_STREAM_TYPE_SPLICE_INFO:
        return isSet(FLAG_IGNORE_SPLICE_INFO_STREAM)
            ? null : new SectionReader(new SpliceInfoSectionReader());
      case TsExtractor.TS_STREAM_TYPE_ID3:
        return new PesReader(new Id3Reader());
      case TsExtractor.TS_STREAM_TYPE_DVBSUBS:
        return new PesReader(
            new DvbSubtitleReader(esInfo.dvbSubtitleInfos));
      default:
        return null;
    }
  }

  /**
   * If {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} is set, returns a {@link SeiReader} for
   * {@link #closedCaptionFormats}. If unset, parses the PMT descriptor information and returns a
   * {@link SeiReader} for the declared formats, or {@link #closedCaptionFormats} if the descriptor
   * is not present.
   *
   * @param esInfo The {@link EsInfo} passed to {@link #createPayloadReader(int, EsInfo)}.
   * @return A {@link SeiReader} for closed caption tracks.
   */
  private SeiReader buildSeiReader(EsInfo esInfo) {
    return new SeiReader(getClosedCaptionFormats(esInfo));
  }

  /**
   * If {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} is set, returns a {@link UserDataReader} for
   * {@link #closedCaptionFormats}. If unset, parses the PMT descriptor information and returns a
   * {@link UserDataReader} for the declared formats, or {@link #closedCaptionFormats} if the
   * descriptor is not present.
   *
   * @param esInfo The {@link EsInfo} passed to {@link #createPayloadReader(int, EsInfo)}.
   * @return A {@link UserDataReader} for closed caption tracks.
   */
  private UserDataReader buildUserDataReader(EsInfo esInfo) {
    return new UserDataReader(getClosedCaptionFormats(esInfo));
  }

  /**
   * If {@link #FLAG_OVERRIDE_CAPTION_DESCRIPTORS} is set, returns a {@link List<Format>} of {@link
   * #closedCaptionFormats}. If unset, parses the PMT descriptor information and returns a {@link
   * List<Format>} for the declared formats, or {@link #closedCaptionFormats} if the descriptor is
   * not present.
   *
   * @param esInfo The {@link EsInfo} passed to {@link #createPayloadReader(int, EsInfo)}.
   * @return A {@link List<Format>} containing list of closed caption formats.
   */
  private List<Format> getClosedCaptionFormats(EsInfo esInfo) {
    if (isSet(FLAG_OVERRIDE_CAPTION_DESCRIPTORS)) {
      return closedCaptionFormats;
    }
    ParsableByteArray scratchDescriptorData = new ParsableByteArray(esInfo.descriptorBytes);
    List<Format> closedCaptionFormats = this.closedCaptionFormats;
    while (scratchDescriptorData.bytesLeft() > 0) {
      int descriptorTag = scratchDescriptorData.readUnsignedByte();
      int descriptorLength = scratchDescriptorData.readUnsignedByte();
      int nextDescriptorPosition = scratchDescriptorData.getPosition() + descriptorLength;
      if (descriptorTag == DESCRIPTOR_TAG_CAPTION_SERVICE) {
        // Note: see ATSC A/65 for detailed information about the caption service descriptor.
        closedCaptionFormats = new ArrayList<>();
        int numberOfServices = scratchDescriptorData.readUnsignedByte() & 0x1F;
        for (int i = 0; i < numberOfServices; i++) {
          String language = scratchDescriptorData.readString(3);
          int captionTypeByte = scratchDescriptorData.readUnsignedByte();
          boolean isDigital = (captionTypeByte & 0x80) != 0;
          String mimeType;
          int accessibilityChannel;
          if (isDigital) {
            mimeType = MimeTypes.APPLICATION_CEA708;
            accessibilityChannel = captionTypeByte & 0x3F;
          } else {
            mimeType = MimeTypes.APPLICATION_CEA608;
            accessibilityChannel = 1;
          }

          // easy_reader(1), wide_aspect_ratio(1), reserved(6).
          byte flags = (byte) scratchDescriptorData.readUnsignedByte();
          // Skip reserved (8).
          scratchDescriptorData.skipBytes(1);

          List<byte[]> initializationData = null;
          // The wide_aspect_ratio flag only has meaning for CEA-708.
          if (isDigital) {
            boolean isWideAspectRatio = (flags & 0x40) != 0;
            initializationData = Cea708InitializationData.buildData(isWideAspectRatio);
          }

          closedCaptionFormats.add(
              Format.createTextSampleFormat(
                  /* id= */ null,
                  mimeType,
                  /* codecs= */ null,
                  /* bitrate= */ Format.NO_VALUE,
                  /* selectionFlags= */ 0,
                  language,
                  accessibilityChannel,
                  /* drmInitData= */ null,
                  Format.OFFSET_SAMPLE_RELATIVE,
                  initializationData));
        }
      } else {
        // Unknown descriptor. Ignore.
      }
      scratchDescriptorData.setPosition(nextDescriptorPosition);
    }

    return closedCaptionFormats;
  }

  private boolean isSet(@Flags int flag) {
    return (flags & flag) != 0;
  }
}
