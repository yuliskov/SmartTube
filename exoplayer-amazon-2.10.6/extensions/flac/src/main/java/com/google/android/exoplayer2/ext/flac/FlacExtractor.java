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
package com.google.android.exoplayer2.ext.flac;

import static com.google.android.exoplayer2.util.Util.getPcmEncoding;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.BinarySearchSeeker.OutputFrameHolder;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.Id3Peeker;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.SeekPoint;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.FlacStreamMetadata;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Facilitates the extraction of data from the FLAC container format.
 */
public final class FlacExtractor implements Extractor {

  /** Factory that returns one extractor which is a {@link FlacExtractor}. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new FlacExtractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag value is {@link
   * #FLAG_DISABLE_ID3_METADATA}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {FLAG_DISABLE_ID3_METADATA})
  public @interface Flags {}

  /**
   * Flag to disable parsing of ID3 metadata. Can be set to save memory if ID3 metadata is not
   * required.
   */
  public static final int FLAG_DISABLE_ID3_METADATA = 1;

  /**
   * FLAC signature: first 4 is the signature word, second 4 is the sizeof STREAMINFO. 0x22 is the
   * mandatory STREAMINFO.
   */
  private static final byte[] FLAC_SIGNATURE = {'f', 'L', 'a', 'C', 0, 0, 0, 0x22};

  private final ParsableByteArray outputBuffer;
  private final Id3Peeker id3Peeker;
  private final boolean id3MetadataDisabled;

  @Nullable private FlacDecoderJni decoderJni;
  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;

  private boolean streamMetadataDecoded;
  private @MonotonicNonNull FlacStreamMetadata streamMetadata;
  private @MonotonicNonNull OutputFrameHolder outputFrameHolder;

  @Nullable private Metadata id3Metadata;
  @Nullable private FlacBinarySearchSeeker binarySearchSeeker;

  /** Constructs an instance with flags = 0. */
  public FlacExtractor() {
    this(/* flags= */ 0);
  }

  /**
   * Constructs an instance.
   *
   * @param flags Flags that control the extractor's behavior.
   */
  public FlacExtractor(int flags) {
    outputBuffer = new ParsableByteArray();
    id3Peeker = new Id3Peeker();
    id3MetadataDisabled = (flags & FLAG_DISABLE_ID3_METADATA) != 0;
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = extractorOutput.track(0, C.TRACK_TYPE_AUDIO);
    extractorOutput.endTracks();
    try {
      decoderJni = new FlacDecoderJni();
    } catch (FlacDecoderException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    if (input.getPosition() == 0) {
      id3Metadata = peekId3Data(input);
    }
    return peekFlacSignature(input);
  }

  @Override
  public int read(final ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (input.getPosition() == 0 && !id3MetadataDisabled && id3Metadata == null) {
      id3Metadata = peekId3Data(input);
    }

    FlacDecoderJni decoderJni = initDecoderJni(input);
    try {
      decodeStreamMetadata(input);

      if (binarySearchSeeker != null && binarySearchSeeker.isSeeking()) {
        return handlePendingSeek(input, seekPosition, outputBuffer, outputFrameHolder, trackOutput);
      }

      ByteBuffer outputByteBuffer = outputFrameHolder.byteBuffer;
      long lastDecodePosition = decoderJni.getDecodePosition();
      try {
        decoderJni.decodeSampleWithBacktrackPosition(outputByteBuffer, lastDecodePosition);
      } catch (FlacDecoderJni.FlacFrameDecodeException e) {
        throw new IOException("Cannot read frame at position " + lastDecodePosition, e);
      }
      int outputSize = outputByteBuffer.limit();
      if (outputSize == 0) {
        return RESULT_END_OF_INPUT;
      }

      outputSample(outputBuffer, outputSize, decoderJni.getLastFrameTimestamp(), trackOutput);
      return decoderJni.isEndOfData() ? RESULT_END_OF_INPUT : RESULT_CONTINUE;
    } finally {
      decoderJni.clearData();
    }
  }

  @Override
  public void seek(long position, long timeUs) {
    if (position == 0) {
      streamMetadataDecoded = false;
    }
    if (decoderJni != null) {
      decoderJni.reset(position);
    }
    if (binarySearchSeeker != null) {
      binarySearchSeeker.setSeekTargetUs(timeUs);
    }
  }

  @Override
  public void release() {
    binarySearchSeeker = null;
    if (decoderJni != null) {
      decoderJni.release();
      decoderJni = null;
    }
  }

  /**
   * Peeks ID3 tag data at the beginning of the input.
   *
   * @return The first ID3 tag {@link Metadata}, or null if an ID3 tag is not present in the input.
   */
  @Nullable
  private Metadata peekId3Data(ExtractorInput input) throws IOException, InterruptedException {
    input.resetPeekPosition();
    Id3Decoder.FramePredicate id3FramePredicate =
        id3MetadataDisabled ? Id3Decoder.NO_FRAMES_PREDICATE : null;
    return id3Peeker.peekId3Data(input, id3FramePredicate);
  }

  @EnsuresNonNull({"decoderJni", "extractorOutput", "trackOutput"}) // Ensures initialized.
  @SuppressWarnings({"contracts.postcondition.not.satisfied"})
  private FlacDecoderJni initDecoderJni(ExtractorInput input) {
    FlacDecoderJni decoderJni = Assertions.checkNotNull(this.decoderJni);
    decoderJni.setData(input);
    return decoderJni;
  }

  @RequiresNonNull({"decoderJni", "extractorOutput", "trackOutput"}) // Requires initialized.
  @EnsuresNonNull({"streamMetadata", "outputFrameHolder"}) // Ensures stream metadata decoded.
  @SuppressWarnings({"contracts.postcondition.not.satisfied"})
  private void decodeStreamMetadata(ExtractorInput input) throws InterruptedException, IOException {
    if (streamMetadataDecoded) {
      return;
    }

    FlacStreamMetadata streamMetadata;
    try {
      streamMetadata = decoderJni.decodeStreamMetadata();
    } catch (IOException e) {
      decoderJni.reset(/* newPosition= */ 0);
      input.setRetryPosition(/* position= */ 0, e);
      throw e;
    }

    streamMetadataDecoded = true;
    if (this.streamMetadata == null) {
      this.streamMetadata = streamMetadata;
      binarySearchSeeker =
          outputSeekMap(decoderJni, streamMetadata, input.getLength(), extractorOutput);
      Metadata metadata = id3MetadataDisabled ? null : id3Metadata;
      if (streamMetadata.metadata != null) {
        metadata = streamMetadata.metadata.copyWithAppendedEntriesFrom(metadata);
      }
      outputFormat(streamMetadata, metadata, trackOutput);
      outputBuffer.reset(streamMetadata.maxDecodedFrameSize());
      outputFrameHolder = new OutputFrameHolder(ByteBuffer.wrap(outputBuffer.data));
    }
  }

  @RequiresNonNull("binarySearchSeeker")
  private int handlePendingSeek(
      ExtractorInput input,
      PositionHolder seekPosition,
      ParsableByteArray outputBuffer,
      OutputFrameHolder outputFrameHolder,
      TrackOutput trackOutput)
      throws InterruptedException, IOException {
    int seekResult = binarySearchSeeker.handlePendingSeek(input, seekPosition, outputFrameHolder);
    ByteBuffer outputByteBuffer = outputFrameHolder.byteBuffer;
    if (seekResult == RESULT_CONTINUE && outputByteBuffer.limit() > 0) {
      outputSample(outputBuffer, outputByteBuffer.limit(), outputFrameHolder.timeUs, trackOutput);
    }
    return seekResult;
  }

  /**
   * Peeks from the beginning of the input to see if {@link #FLAC_SIGNATURE} is present.
   *
   * @return Whether the input begins with {@link #FLAC_SIGNATURE}.
   */
  private static boolean peekFlacSignature(ExtractorInput input)
      throws IOException, InterruptedException {
    byte[] header = new byte[FLAC_SIGNATURE.length];
    input.peekFully(header, /* offset= */ 0, FLAC_SIGNATURE.length);
    return Arrays.equals(header, FLAC_SIGNATURE);
  }

  /**
   * Outputs a {@link SeekMap} and returns a {@link FlacBinarySearchSeeker} if one is required to
   * handle seeks.
   */
  @Nullable
  private static FlacBinarySearchSeeker outputSeekMap(
      FlacDecoderJni decoderJni,
      FlacStreamMetadata streamMetadata,
      long streamLength,
      ExtractorOutput output) {
    boolean haveSeekTable = decoderJni.getSeekPoints(/* timeUs= */ 0) != null;
    FlacBinarySearchSeeker binarySearchSeeker = null;
    SeekMap seekMap;
    if (haveSeekTable) {
      seekMap = new FlacSeekMap(streamMetadata.durationUs(), decoderJni);
    } else if (streamLength != C.LENGTH_UNSET) {
      long firstFramePosition = decoderJni.getDecodePosition();
      binarySearchSeeker =
          new FlacBinarySearchSeeker(streamMetadata, firstFramePosition, streamLength, decoderJni);
      seekMap = binarySearchSeeker.getSeekMap();
    } else {
      seekMap = new SeekMap.Unseekable(streamMetadata.durationUs());
    }
    output.seekMap(seekMap);
    return binarySearchSeeker;
  }

  private static void outputFormat(
      FlacStreamMetadata streamMetadata, @Nullable Metadata metadata, TrackOutput output) {
    Format mediaFormat =
        Format.createAudioSampleFormat(
            /* id= */ null,
            MimeTypes.AUDIO_RAW,
            /* codecs= */ null,
            streamMetadata.bitRate(),
            streamMetadata.maxDecodedFrameSize(),
            streamMetadata.channels,
            streamMetadata.sampleRate,
            getPcmEncoding(streamMetadata.bitsPerSample),
            /* encoderDelay= */ 0,
            /* encoderPadding= */ 0,
            /* initializationData= */ null,
            /* drmInitData= */ null,
            /* selectionFlags= */ 0,
            /* language= */ null,
            metadata);
    output.format(mediaFormat);
  }

  private static void outputSample(
      ParsableByteArray sampleData, int size, long timeUs, TrackOutput output) {
    sampleData.setPosition(0);
    output.sampleData(sampleData, size);
    output.sampleMetadata(
        timeUs, C.BUFFER_FLAG_KEY_FRAME, size, /* offset= */ 0, /* encryptionData= */ null);
  }

  /** A {@link SeekMap} implementation using a SeekTable within the Flac stream. */
  private static final class FlacSeekMap implements SeekMap {

    private final long durationUs;
    private final FlacDecoderJni decoderJni;

    public FlacSeekMap(long durationUs, FlacDecoderJni decoderJni) {
      this.durationUs = durationUs;
      this.decoderJni = decoderJni;
    }

    @Override
    public boolean isSeekable() {
      return true;
    }

    @Override
    public SeekPoints getSeekPoints(long timeUs) {
      @Nullable SeekPoints seekPoints = decoderJni.getSeekPoints(timeUs);
      return seekPoints == null ? new SeekPoints(SeekPoint.START) : seekPoints;
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }
  }
}
