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
package com.google.android.exoplayer2.extractor.mp3;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.GaplessInfoHolder;
import com.google.android.exoplayer2.extractor.Id3Peeker;
import com.google.android.exoplayer2.extractor.MpegAudioHeader;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.mp3.Seeker.UnseekableSeeker;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder;
import com.google.android.exoplayer2.metadata.id3.Id3Decoder.FramePredicate;
import com.google.android.exoplayer2.metadata.id3.MlltFrame;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Extracts data from the MP3 container format.
 */
public final class Mp3Extractor implements Extractor {

  /** Factory for {@link Mp3Extractor} instances. */
  public static final ExtractorsFactory FACTORY = () -> new Extractor[] {new Mp3Extractor()};

  /**
   * Flags controlling the behavior of the extractor. Possible flag values are {@link
   * #FLAG_ENABLE_CONSTANT_BITRATE_SEEKING} and {@link #FLAG_DISABLE_ID3_METADATA}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      flag = true,
      value = {FLAG_ENABLE_CONSTANT_BITRATE_SEEKING, FLAG_DISABLE_ID3_METADATA})
  public @interface Flags {}
  /**
   * Flag to force enable seeking using a constant bitrate assumption in cases where seeking would
   * otherwise not be possible.
   */
  public static final int FLAG_ENABLE_CONSTANT_BITRATE_SEEKING = 1;
  /**
   * Flag to disable parsing of ID3 metadata. Can be set to save memory if ID3 metadata is not
   * required.
   */
  public static final int FLAG_DISABLE_ID3_METADATA = 2;

  /** Predicate that matches ID3 frames containing only required gapless/seeking metadata. */
  private static final FramePredicate REQUIRED_ID3_FRAME_PREDICATE =
      (majorVersion, id0, id1, id2, id3) ->
          ((id0 == 'C' && id1 == 'O' && id2 == 'M' && (id3 == 'M' || majorVersion == 2))
              || (id0 == 'M' && id1 == 'L' && id2 == 'L' && (id3 == 'T' || majorVersion == 2)));

  /**
   * The maximum number of bytes to search when synchronizing, before giving up.
   */
  private static final int MAX_SYNC_BYTES = 128 * 1024;
  /**
   * The maximum number of bytes to peek when sniffing, excluding the ID3 header, before giving up.
   */
  private static final int MAX_SNIFF_BYTES = 16 * 1024;
  /**
   * Maximum length of data read into {@link #scratch}.
   */
  private static final int SCRATCH_LENGTH = 10;

  /**
   * Mask that includes the audio header values that must match between frames.
   */
  private static final int MPEG_AUDIO_HEADER_MASK = 0xFFFE0C00;

  private static final int SEEK_HEADER_XING = Util.getIntegerCodeForString("Xing");
  private static final int SEEK_HEADER_INFO = Util.getIntegerCodeForString("Info");
  private static final int SEEK_HEADER_VBRI = Util.getIntegerCodeForString("VBRI");
  private static final int SEEK_HEADER_UNSET = 0;

  @Flags private final int flags;
  private final long forcedFirstSampleTimestampUs;
  private final ParsableByteArray scratch;
  private final MpegAudioHeader synchronizedHeader;
  private final GaplessInfoHolder gaplessInfoHolder;
  private final Id3Peeker id3Peeker;

  // Extractor outputs.
  private ExtractorOutput extractorOutput;
  private TrackOutput trackOutput;

  private int synchronizedHeaderData;

  private Metadata metadata;
  @Nullable private Seeker seeker;
  private boolean disableSeeking;
  private long basisTimeUs;
  private long samplesRead;
  private long firstSamplePosition;
  private int sampleBytesRemaining;

  public Mp3Extractor() {
    this(0);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   */
  public Mp3Extractor(@Flags int flags) {
    this(flags, C.TIME_UNSET);
  }

  /**
   * @param flags Flags that control the extractor's behavior.
   * @param forcedFirstSampleTimestampUs A timestamp to force for the first sample, or
   *     {@link C#TIME_UNSET} if forcing is not required.
   */
  public Mp3Extractor(@Flags int flags, long forcedFirstSampleTimestampUs) {
    this.flags = flags;
    this.forcedFirstSampleTimestampUs = forcedFirstSampleTimestampUs;
    scratch = new ParsableByteArray(SCRATCH_LENGTH);
    synchronizedHeader = new MpegAudioHeader();
    gaplessInfoHolder = new GaplessInfoHolder();
    basisTimeUs = C.TIME_UNSET;
    id3Peeker = new Id3Peeker();
  }

  // Extractor implementation.

  @Override
  public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
    return synchronize(input, true);
  }

  @Override
  public void init(ExtractorOutput output) {
    extractorOutput = output;
    trackOutput = extractorOutput.track(0, C.TRACK_TYPE_AUDIO);
    extractorOutput.endTracks();
  }

  @Override
  public void seek(long position, long timeUs) {
    synchronizedHeaderData = 0;
    basisTimeUs = C.TIME_UNSET;
    samplesRead = 0;
    sampleBytesRemaining = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    if (synchronizedHeaderData == 0) {
      try {
        synchronize(input, false);
      } catch (EOFException e) {
        return RESULT_END_OF_INPUT;
      }
    }
    if (seeker == null) {
      // Read past any seek frame and set the seeker based on metadata or a seek frame. Metadata
      // takes priority as it can provide greater precision.
      Seeker seekFrameSeeker = maybeReadSeekFrame(input);
      Seeker metadataSeeker = maybeHandleSeekMetadata(metadata, input.getPosition());

      if (disableSeeking) {
        seeker = new UnseekableSeeker();
      } else {
        if (metadataSeeker != null) {
          seeker = metadataSeeker;
        } else if (seekFrameSeeker != null) {
          seeker = seekFrameSeeker;
        }
        if (seeker == null
            || (!seeker.isSeekable() && (flags & FLAG_ENABLE_CONSTANT_BITRATE_SEEKING) != 0)) {
          seeker = getConstantBitrateSeeker(input);
        }
      }
      extractorOutput.seekMap(seeker);
      trackOutput.format(
          Format.createAudioSampleFormat(
              /* id= */ null,
              synchronizedHeader.mimeType,
              /* codecs= */ null,
              /* bitrate= */ Format.NO_VALUE,
              MpegAudioHeader.MAX_FRAME_SIZE_BYTES,
              synchronizedHeader.channels,
              synchronizedHeader.sampleRate,
              /* pcmEncoding= */ Format.NO_VALUE,
              gaplessInfoHolder.encoderDelay,
              gaplessInfoHolder.encoderPadding,
              /* initializationData= */ null,
              /* drmInitData= */ null,
              /* selectionFlags= */ 0,
              /* language= */ null,
              (flags & FLAG_DISABLE_ID3_METADATA) != 0 ? null : metadata));
      firstSamplePosition = input.getPosition();
    } else if (firstSamplePosition != 0) {
      long inputPosition = input.getPosition();
      if (inputPosition < firstSamplePosition) {
        // Skip past the seek frame.
        input.skipFully((int) (firstSamplePosition - inputPosition));
      }
    }
    return readSample(input);
  }

  /**
   * Disables the extractor from being able to seek through the media.
   *
   * <p>Please note that this needs to be called before {@link #read}.
   */
  public void disableSeeking() {
    disableSeeking = true;
  }

  // Internal methods.

  private int readSample(ExtractorInput extractorInput) throws IOException, InterruptedException {
    if (sampleBytesRemaining == 0) {
      extractorInput.resetPeekPosition();
      if (peekEndOfStreamOrHeader(extractorInput)) {
        return RESULT_END_OF_INPUT;
      }
      scratch.setPosition(0);
      int sampleHeaderData = scratch.readInt();
      if (!headersMatch(sampleHeaderData, synchronizedHeaderData)
          || MpegAudioHeader.getFrameSize(sampleHeaderData) == C.LENGTH_UNSET) {
        // We have lost synchronization, so attempt to resynchronize starting at the next byte.
        extractorInput.skipFully(1);
        synchronizedHeaderData = 0;
        return RESULT_CONTINUE;
      }
      MpegAudioHeader.populateHeader(sampleHeaderData, synchronizedHeader);
      if (basisTimeUs == C.TIME_UNSET) {
        basisTimeUs = seeker.getTimeUs(extractorInput.getPosition());
        if (forcedFirstSampleTimestampUs != C.TIME_UNSET) {
          long embeddedFirstSampleTimestampUs = seeker.getTimeUs(0);
          basisTimeUs += forcedFirstSampleTimestampUs - embeddedFirstSampleTimestampUs;
        }
      }
      sampleBytesRemaining = synchronizedHeader.frameSize;
    }
    int bytesAppended = trackOutput.sampleData(extractorInput, sampleBytesRemaining, true);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      return RESULT_END_OF_INPUT;
    }
    sampleBytesRemaining -= bytesAppended;
    if (sampleBytesRemaining > 0) {
      return RESULT_CONTINUE;
    }
    long timeUs = basisTimeUs + (samplesRead * C.MICROS_PER_SECOND / synchronizedHeader.sampleRate);
    trackOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, synchronizedHeader.frameSize, 0,
        null);
    samplesRead += synchronizedHeader.samplesPerFrame;
    sampleBytesRemaining = 0;
    return RESULT_CONTINUE;
  }

  private boolean synchronize(ExtractorInput input, boolean sniffing)
      throws IOException, InterruptedException {
    int validFrameCount = 0;
    int candidateSynchronizedHeaderData = 0;
    int peekedId3Bytes = 0;
    int searchedBytes = 0;
    int searchLimitBytes = sniffing ? MAX_SNIFF_BYTES : MAX_SYNC_BYTES;
    input.resetPeekPosition();
    if (input.getPosition() == 0) {
      // We need to parse enough ID3 metadata to retrieve any gapless/seeking playback information
      // even if ID3 metadata parsing is disabled.
      boolean parseAllId3Frames = (flags & FLAG_DISABLE_ID3_METADATA) == 0;
      Id3Decoder.FramePredicate id3FramePredicate =
          parseAllId3Frames ? null : REQUIRED_ID3_FRAME_PREDICATE;
      metadata = id3Peeker.peekId3Data(input, id3FramePredicate);
      if (metadata != null) {
        gaplessInfoHolder.setFromMetadata(metadata);
      }
      peekedId3Bytes = (int) input.getPeekPosition();
      if (!sniffing) {
        input.skipFully(peekedId3Bytes);
      }
    }
    while (true) {
      if (peekEndOfStreamOrHeader(input)) {
        if (validFrameCount > 0) {
          // We reached the end of the stream but found at least one valid frame.
          break;
        }
        throw new EOFException();
      }
      scratch.setPosition(0);
      int headerData = scratch.readInt();
      int frameSize;
      if ((candidateSynchronizedHeaderData != 0
          && !headersMatch(headerData, candidateSynchronizedHeaderData))
          || (frameSize = MpegAudioHeader.getFrameSize(headerData)) == C.LENGTH_UNSET) {
        // The header doesn't match the candidate header or is invalid. Try the next byte offset.
        if (searchedBytes++ == searchLimitBytes) {
          if (!sniffing) {
            throw new ParserException("Searched too many bytes.");
          }
          return false;
        }
        validFrameCount = 0;
        candidateSynchronizedHeaderData = 0;
        if (sniffing) {
          input.resetPeekPosition();
          input.advancePeekPosition(peekedId3Bytes + searchedBytes);
        } else {
          input.skipFully(1);
        }
      } else {
        // The header matches the candidate header and/or is valid.
        validFrameCount++;
        if (validFrameCount == 1) {
          MpegAudioHeader.populateHeader(headerData, synchronizedHeader);
          candidateSynchronizedHeaderData = headerData;
        } else if (validFrameCount == 4) {
          break;
        }
        input.advancePeekPosition(frameSize - 4);
      }
    }
    // Prepare to read the synchronized frame.
    if (sniffing) {
      input.skipFully(peekedId3Bytes + searchedBytes);
    } else {
      input.resetPeekPosition();
    }
    synchronizedHeaderData = candidateSynchronizedHeaderData;
    return true;
  }

  /**
   * Returns whether the extractor input is peeking the end of the stream. If {@code false},
   * populates the scratch buffer with the next four bytes.
   */
  private boolean peekEndOfStreamOrHeader(ExtractorInput extractorInput)
      throws IOException, InterruptedException {
    if (seeker != null) {
      long dataEndPosition = seeker.getDataEndPosition();
      if (dataEndPosition != C.POSITION_UNSET
          && extractorInput.getPeekPosition() > dataEndPosition - 4) {
        return true;
      }
    }
    try {
      return !extractorInput.peekFully(
          scratch.data, /* offset= */ 0, /* length= */ 4, /* allowEndOfInput= */ true);
    } catch (EOFException e) {
      return true;
    }
  }

  /**
   * Consumes the next frame from the {@code input} if it contains VBRI or Xing seeking metadata,
   * returning a {@link Seeker} if the metadata was present and valid, or {@code null} otherwise.
   * After this method returns, the input position is the start of the first frame of audio.
   *
   * @param input The {@link ExtractorInput} from which to read.
   * @return A {@link Seeker} if seeking metadata was present and valid, or {@code null} otherwise.
   * @throws IOException Thrown if there was an error reading from the stream. Not expected if the
   *     next two frames were already peeked during synchronization.
   * @throws InterruptedException Thrown if reading from the stream was interrupted. Not expected if
   *     the next two frames were already peeked during synchronization.
   */
  private Seeker maybeReadSeekFrame(ExtractorInput input) throws IOException, InterruptedException {
    ParsableByteArray frame = new ParsableByteArray(synchronizedHeader.frameSize);
    input.peekFully(frame.data, 0, synchronizedHeader.frameSize);
    int xingBase = (synchronizedHeader.version & 1) != 0
        ? (synchronizedHeader.channels != 1 ? 36 : 21) // MPEG 1
        : (synchronizedHeader.channels != 1 ? 21 : 13); // MPEG 2 or 2.5
    int seekHeader = getSeekFrameHeader(frame, xingBase);
    Seeker seeker;
    if (seekHeader == SEEK_HEADER_XING || seekHeader == SEEK_HEADER_INFO) {
      seeker = XingSeeker.create(input.getLength(), input.getPosition(), synchronizedHeader, frame);
      if (seeker != null && !gaplessInfoHolder.hasGaplessInfo()) {
        // If there is a Xing header, read gapless playback metadata at a fixed offset.
        input.resetPeekPosition();
        input.advancePeekPosition(xingBase + 141);
        input.peekFully(scratch.data, 0, 3);
        scratch.setPosition(0);
        gaplessInfoHolder.setFromXingHeaderValue(scratch.readUnsignedInt24());
      }
      input.skipFully(synchronizedHeader.frameSize);
      if (seeker != null && !seeker.isSeekable() && seekHeader == SEEK_HEADER_INFO) {
        // Fall back to constant bitrate seeking for Info headers missing a table of contents.
        return getConstantBitrateSeeker(input);
      }
    } else if (seekHeader == SEEK_HEADER_VBRI) {
      seeker = VbriSeeker.create(input.getLength(), input.getPosition(), synchronizedHeader, frame);
      input.skipFully(synchronizedHeader.frameSize);
    } else { // seekerHeader == SEEK_HEADER_UNSET
      // This frame doesn't contain seeking information, so reset the peek position.
      seeker = null;
      input.resetPeekPosition();
    }
    return seeker;
  }

  /**
   * Peeks the next frame and returns a {@link ConstantBitrateSeeker} based on its bitrate.
   */
  private Seeker getConstantBitrateSeeker(ExtractorInput input)
      throws IOException, InterruptedException {
    input.peekFully(scratch.data, 0, 4);
    scratch.setPosition(0);
    MpegAudioHeader.populateHeader(scratch.readInt(), synchronizedHeader);
    return new ConstantBitrateSeeker(input.getLength(), input.getPosition(), synchronizedHeader);
  }

  /**
   * Returns whether the headers match in those bits masked by {@link #MPEG_AUDIO_HEADER_MASK}.
   */
  private static boolean headersMatch(int headerA, long headerB) {
    return (headerA & MPEG_AUDIO_HEADER_MASK) == (headerB & MPEG_AUDIO_HEADER_MASK);
  }

  /**
   * Returns {@link #SEEK_HEADER_XING}, {@link #SEEK_HEADER_INFO} or {@link #SEEK_HEADER_VBRI} if
   * the provided {@code frame} may have seeking metadata, or {@link #SEEK_HEADER_UNSET} otherwise.
   * If seeking metadata is present, {@code frame}'s position is advanced past the header.
   */
  private static int getSeekFrameHeader(ParsableByteArray frame, int xingBase) {
    if (frame.limit() >= xingBase + 4) {
      frame.setPosition(xingBase);
      int headerData = frame.readInt();
      if (headerData == SEEK_HEADER_XING || headerData == SEEK_HEADER_INFO) {
        return headerData;
      }
    }
    if (frame.limit() >= 40) {
      frame.setPosition(36); // MPEG audio header (4 bytes) + 32 bytes.
      if (frame.readInt() == SEEK_HEADER_VBRI) {
        return SEEK_HEADER_VBRI;
      }
    }
    return SEEK_HEADER_UNSET;
  }

  @Nullable
  private static MlltSeeker maybeHandleSeekMetadata(Metadata metadata, long firstFramePosition) {
    if (metadata != null) {
      int length = metadata.length();
      for (int i = 0; i < length; i++) {
        Metadata.Entry entry = metadata.get(i);
        if (entry instanceof MlltFrame) {
          return MlltSeeker.create(firstFramePosition, (MlltFrame) entry);
        }
      }
    }
    return null;
  }


}
