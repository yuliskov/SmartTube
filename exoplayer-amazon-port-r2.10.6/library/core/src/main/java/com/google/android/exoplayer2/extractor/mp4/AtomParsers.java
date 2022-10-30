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
package com.google.android.exoplayer2.extractor.mp4;

import static com.google.android.exoplayer2.util.MimeTypes.getMimeTypeFromMp4ObjectType;

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.audio.Ac4Util;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.GaplessInfoHolder;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.AvcConfig;
import com.google.android.exoplayer2.video.DolbyVisionConfig;
import com.google.android.exoplayer2.video.HevcConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Utility methods for parsing MP4 format atom payloads according to ISO 14496-12. */
@SuppressWarnings({"ConstantField", "ConstantCaseForConstants"})
/* package */ final class AtomParsers {

  private static final String TAG = "AtomParsers";

  private static final int TYPE_vide = Util.getIntegerCodeForString("vide");
  private static final int TYPE_soun = Util.getIntegerCodeForString("soun");
  private static final int TYPE_text = Util.getIntegerCodeForString("text");
  private static final int TYPE_sbtl = Util.getIntegerCodeForString("sbtl");
  private static final int TYPE_subt = Util.getIntegerCodeForString("subt");
  private static final int TYPE_clcp = Util.getIntegerCodeForString("clcp");
  private static final int TYPE_meta = Util.getIntegerCodeForString("meta");
  private static final int TYPE_mdta = Util.getIntegerCodeForString("mdta");

  /**
   * The threshold number of samples to trim from the start/end of an audio track when applying an
   * edit below which gapless info can be used (rather than removing samples from the sample table).
   */
  private static final int MAX_GAPLESS_TRIM_SIZE_SAMPLES = 4;

  /** The magic signature for an Opus Identification header, as defined in RFC-7845. */
  private static final byte[] opusMagic = Util.getUtf8Bytes("OpusHead");

  /**
   * Parses a trak atom (defined in 14496-12).
   *
   * @param trak Atom to decode.
   * @param mvhd Movie header atom, used to get the timescale.
   * @param duration The duration in units of the timescale declared in the mvhd atom, or
   *     {@link C#TIME_UNSET} if the duration should be parsed from the tkhd atom.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @param ignoreEditLists Whether to ignore any edit lists in the trak box.
   * @param isQuickTime True for QuickTime media. False otherwise.
   * @return A {@link Track} instance, or {@code null} if the track's type isn't supported.
   */
  public static Track parseTrak(Atom.ContainerAtom trak, Atom.LeafAtom mvhd, long duration,
      DrmInitData drmInitData, boolean ignoreEditLists, boolean isQuickTime)
      throws ParserException {
    Atom.ContainerAtom mdia = trak.getContainerAtomOfType(Atom.TYPE_mdia);
    int trackType = getTrackTypeForHdlr(parseHdlr(mdia.getLeafAtomOfType(Atom.TYPE_hdlr).data));
    if (trackType == C.TRACK_TYPE_UNKNOWN) {
      return null;
    }

    TkhdData tkhdData = parseTkhd(trak.getLeafAtomOfType(Atom.TYPE_tkhd).data);
    if (duration == C.TIME_UNSET) {
      duration = tkhdData.duration;
    }
    long movieTimescale = parseMvhd(mvhd.data);
    long durationUs;
    if (duration == C.TIME_UNSET) {
      durationUs = C.TIME_UNSET;
    } else {
      durationUs = Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, movieTimescale);
    }
    Atom.ContainerAtom stbl = mdia.getContainerAtomOfType(Atom.TYPE_minf)
        .getContainerAtomOfType(Atom.TYPE_stbl);

    Pair<Long, String> mdhdData = parseMdhd(mdia.getLeafAtomOfType(Atom.TYPE_mdhd).data);
    StsdData stsdData = parseStsd(stbl.getLeafAtomOfType(Atom.TYPE_stsd).data, tkhdData.id,
        tkhdData.rotationDegrees, mdhdData.second, drmInitData, isQuickTime);
    long[] editListDurations = null;
    long[] editListMediaTimes = null;
    if (!ignoreEditLists) {
      Pair<long[], long[]> edtsData = parseEdts(trak.getContainerAtomOfType(Atom.TYPE_edts));
      editListDurations = edtsData.first;
      editListMediaTimes = edtsData.second;
    }
    return stsdData.format == null ? null
        : new Track(tkhdData.id, trackType, mdhdData.first, movieTimescale, durationUs,
            stsdData.format, stsdData.requiredSampleTransformation, stsdData.trackEncryptionBoxes,
            stsdData.nalUnitLengthFieldLength, editListDurations, editListMediaTimes);
  }

  /**
   * Parses an stbl atom (defined in 14496-12).
   *
   * @param track Track to which this sample table corresponds.
   * @param stblAtom stbl (sample table) atom to decode.
   * @param gaplessInfoHolder Holder to populate with gapless playback information.
   * @return Sample table described by the stbl atom.
   * @throws ParserException Thrown if the stbl atom can't be parsed.
   */
  public static TrackSampleTable parseStbl(
      Track track, Atom.ContainerAtom stblAtom, GaplessInfoHolder gaplessInfoHolder)
      throws ParserException {
    SampleSizeBox sampleSizeBox;
    Atom.LeafAtom stszAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stsz);
    if (stszAtom != null) {
      sampleSizeBox = new StszSampleSizeBox(stszAtom);
    } else {
      Atom.LeafAtom stz2Atom = stblAtom.getLeafAtomOfType(Atom.TYPE_stz2);
      if (stz2Atom == null) {
        throw new ParserException("Track has no sample table size information");
      }
      sampleSizeBox = new Stz2SampleSizeBox(stz2Atom);
    }

    int sampleCount = sampleSizeBox.getSampleCount();
    if (sampleCount == 0) {
      return new TrackSampleTable(
          track,
          /* offsets= */ new long[0],
          /* sizes= */ new int[0],
          /* maximumSize= */ 0,
          /* timestampsUs= */ new long[0],
          /* flags= */ new int[0],
          /* durationUs= */ C.TIME_UNSET);
    }

    // Entries are byte offsets of chunks.
    boolean chunkOffsetsAreLongs = false;
    Atom.LeafAtom chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stco);
    if (chunkOffsetsAtom == null) {
      chunkOffsetsAreLongs = true;
      chunkOffsetsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_co64);
    }
    ParsableByteArray chunkOffsets = chunkOffsetsAtom.data;
    // Entries are (chunk number, number of samples per chunk, sample description index).
    ParsableByteArray stsc = stblAtom.getLeafAtomOfType(Atom.TYPE_stsc).data;
    // Entries are (number of samples, timestamp delta between those samples).
    ParsableByteArray stts = stblAtom.getLeafAtomOfType(Atom.TYPE_stts).data;
    // Entries are the indices of samples that are synchronization samples.
    Atom.LeafAtom stssAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_stss);
    ParsableByteArray stss = stssAtom != null ? stssAtom.data : null;
    // Entries are (number of samples, timestamp offset).
    Atom.LeafAtom cttsAtom = stblAtom.getLeafAtomOfType(Atom.TYPE_ctts);
    ParsableByteArray ctts = cttsAtom != null ? cttsAtom.data : null;

    // Prepare to read chunk information.
    ChunkIterator chunkIterator = new ChunkIterator(stsc, chunkOffsets, chunkOffsetsAreLongs);

    // Prepare to read sample timestamps.
    stts.setPosition(Atom.FULL_HEADER_SIZE);
    int remainingTimestampDeltaChanges = stts.readUnsignedIntToInt() - 1;
    int remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
    int timestampDeltaInTimeUnits = stts.readUnsignedIntToInt();

    // Prepare to read sample timestamp offsets, if ctts is present.
    int remainingSamplesAtTimestampOffset = 0;
    int remainingTimestampOffsetChanges = 0;
    int timestampOffset = 0;
    if (ctts != null) {
      ctts.setPosition(Atom.FULL_HEADER_SIZE);
      remainingTimestampOffsetChanges = ctts.readUnsignedIntToInt();
    }

    int nextSynchronizationSampleIndex = C.INDEX_UNSET;
    int remainingSynchronizationSamples = 0;
    if (stss != null) {
      stss.setPosition(Atom.FULL_HEADER_SIZE);
      remainingSynchronizationSamples = stss.readUnsignedIntToInt();
      if (remainingSynchronizationSamples > 0) {
        nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
      } else {
        // Ignore empty stss boxes, which causes all samples to be treated as sync samples.
        stss = null;
      }
    }

    // Fixed sample size raw audio may need to be rechunked.
    boolean isFixedSampleSizeRawAudio =
        sampleSizeBox.isFixedSampleSize()
            && MimeTypes.AUDIO_RAW.equals(track.format.sampleMimeType)
            && remainingTimestampDeltaChanges == 0
            && remainingTimestampOffsetChanges == 0
            && remainingSynchronizationSamples == 0;

    long[] offsets;
    int[] sizes;
    int maximumSize = 0;
    long[] timestamps;
    int[] flags;
    long timestampTimeUnits = 0;
    long duration;

    if (!isFixedSampleSizeRawAudio) {
      offsets = new long[sampleCount];
      sizes = new int[sampleCount];
      timestamps = new long[sampleCount];
      flags = new int[sampleCount];
      long offset = 0;
      int remainingSamplesInChunk = 0;

      for (int i = 0; i < sampleCount; i++) {
        // Advance to the next chunk if necessary.
        boolean chunkDataComplete = true;
        while (remainingSamplesInChunk == 0 && (chunkDataComplete = chunkIterator.moveNext())) {
          offset = chunkIterator.offset;
          remainingSamplesInChunk = chunkIterator.numSamples;
        }
        if (!chunkDataComplete) {
          Log.w(TAG, "Unexpected end of chunk data");
          sampleCount = i;
          offsets = Arrays.copyOf(offsets, sampleCount);
          sizes = Arrays.copyOf(sizes, sampleCount);
          timestamps = Arrays.copyOf(timestamps, sampleCount);
          flags = Arrays.copyOf(flags, sampleCount);
          break;
        }

        // Add on the timestamp offset if ctts is present.
        if (ctts != null) {
          while (remainingSamplesAtTimestampOffset == 0 && remainingTimestampOffsetChanges > 0) {
            remainingSamplesAtTimestampOffset = ctts.readUnsignedIntToInt();
            // The BMFF spec (ISO 14496-12) states that sample offsets should be unsigned integers
            // in version 0 ctts boxes, however some streams violate the spec and use signed
            // integers instead. It's safe to always decode sample offsets as signed integers here,
            // because unsigned integers will still be parsed correctly (unless their top bit is
            // set, which is never true in practice because sample offsets are always small).
            timestampOffset = ctts.readInt();
            remainingTimestampOffsetChanges--;
          }
          remainingSamplesAtTimestampOffset--;
        }

        offsets[i] = offset;
        sizes[i] = sampleSizeBox.readNextSampleSize();
        if (sizes[i] > maximumSize) {
          maximumSize = sizes[i];
        }
        timestamps[i] = timestampTimeUnits + timestampOffset;

        // All samples are synchronization samples if the stss is not present.
        flags[i] = stss == null ? C.BUFFER_FLAG_KEY_FRAME : 0;
        if (i == nextSynchronizationSampleIndex) {
          flags[i] = C.BUFFER_FLAG_KEY_FRAME;
          remainingSynchronizationSamples--;
          if (remainingSynchronizationSamples > 0) {
            nextSynchronizationSampleIndex = stss.readUnsignedIntToInt() - 1;
          }
        }

        // Add on the duration of this sample.
        timestampTimeUnits += timestampDeltaInTimeUnits;
        remainingSamplesAtTimestampDelta--;
        if (remainingSamplesAtTimestampDelta == 0 && remainingTimestampDeltaChanges > 0) {
          remainingSamplesAtTimestampDelta = stts.readUnsignedIntToInt();
          // The BMFF spec (ISO 14496-12) states that sample deltas should be unsigned integers
          // in stts boxes, however some streams violate the spec and use signed integers instead.
          // See https://github.com/google/ExoPlayer/issues/3384. It's safe to always decode sample
          // deltas as signed integers here, because unsigned integers will still be parsed
          // correctly (unless their top bit is set, which is never true in practice because sample
          // deltas are always small).
          timestampDeltaInTimeUnits = stts.readInt();
          remainingTimestampDeltaChanges--;
        }

        offset += sizes[i];
        remainingSamplesInChunk--;
      }
      duration = timestampTimeUnits + timestampOffset;

      // If the stbl's child boxes are not consistent the container is malformed, but the stream may
      // still be playable.
      boolean isCttsValid = true;
      while (remainingTimestampOffsetChanges > 0) {
        if (ctts.readUnsignedIntToInt() != 0) {
          isCttsValid = false;
          break;
        }
        ctts.readInt(); // Ignore offset.
        remainingTimestampOffsetChanges--;
      }
      if (remainingSynchronizationSamples != 0
          || remainingSamplesAtTimestampDelta != 0
          || remainingSamplesInChunk != 0
          || remainingTimestampDeltaChanges != 0
          || remainingSamplesAtTimestampOffset != 0
          || !isCttsValid) {
        Log.w(
            TAG,
            "Inconsistent stbl box for track "
                + track.id
                + ": remainingSynchronizationSamples "
                + remainingSynchronizationSamples
                + ", remainingSamplesAtTimestampDelta "
                + remainingSamplesAtTimestampDelta
                + ", remainingSamplesInChunk "
                + remainingSamplesInChunk
                + ", remainingTimestampDeltaChanges "
                + remainingTimestampDeltaChanges
                + ", remainingSamplesAtTimestampOffset "
                + remainingSamplesAtTimestampOffset
                + (!isCttsValid ? ", ctts invalid" : ""));
      }
    } else {
      long[] chunkOffsetsBytes = new long[chunkIterator.length];
      int[] chunkSampleCounts = new int[chunkIterator.length];
      while (chunkIterator.moveNext()) {
        chunkOffsetsBytes[chunkIterator.index] = chunkIterator.offset;
        chunkSampleCounts[chunkIterator.index] = chunkIterator.numSamples;
      }
      int fixedSampleSize =
          Util.getPcmFrameSize(track.format.pcmEncoding, track.format.channelCount);
      FixedSampleSizeRechunker.Results rechunkedResults = FixedSampleSizeRechunker.rechunk(
          fixedSampleSize, chunkOffsetsBytes, chunkSampleCounts, timestampDeltaInTimeUnits);
      offsets = rechunkedResults.offsets;
      sizes = rechunkedResults.sizes;
      maximumSize = rechunkedResults.maximumSize;
      timestamps = rechunkedResults.timestamps;
      flags = rechunkedResults.flags;
      duration = rechunkedResults.duration;
    }
    long durationUs = Util.scaleLargeTimestamp(duration, C.MICROS_PER_SECOND, track.timescale);

    if (track.editListDurations == null || gaplessInfoHolder.hasGaplessInfo()) {
      // There is no edit list, or we are ignoring it as we already have gapless metadata to apply.
      // This implementation does not support applying both gapless metadata and an edit list.
      Util.scaleLargeTimestampsInPlace(timestamps, C.MICROS_PER_SECOND, track.timescale);
      return new TrackSampleTable(
          track, offsets, sizes, maximumSize, timestamps, flags, durationUs);
    }

    // See the BMFF spec (ISO 14496-12) subsection 8.6.6. Edit lists that require prerolling from a
    // sync sample after reordering are not supported. Partial audio sample truncation is only
    // supported in edit lists with one edit that removes less than MAX_GAPLESS_TRIM_SIZE_SAMPLES
    // samples from the start/end of the track. This implementation handles simple
    // discarding/delaying of samples. The extractor may place further restrictions on what edited
    // streams are playable.

    if (track.editListDurations.length == 1
        && track.type == C.TRACK_TYPE_AUDIO
        && timestamps.length >= 2) {
      long editStartTime = track.editListMediaTimes[0];
      long editEndTime = editStartTime + Util.scaleLargeTimestamp(track.editListDurations[0],
          track.timescale, track.movieTimescale);
      if (canApplyEditWithGaplessInfo(timestamps, duration, editStartTime, editEndTime)) {
        long paddingTimeUnits = duration - editEndTime;
        long encoderDelay = Util.scaleLargeTimestamp(editStartTime - timestamps[0],
            track.format.sampleRate, track.timescale);
        long encoderPadding = Util.scaleLargeTimestamp(paddingTimeUnits,
            track.format.sampleRate, track.timescale);
        if ((encoderDelay != 0 || encoderPadding != 0) && encoderDelay <= Integer.MAX_VALUE
            && encoderPadding <= Integer.MAX_VALUE) {
          gaplessInfoHolder.encoderDelay = (int) encoderDelay;
          gaplessInfoHolder.encoderPadding = (int) encoderPadding;
          Util.scaleLargeTimestampsInPlace(timestamps, C.MICROS_PER_SECOND, track.timescale);
          long editedDurationUs =
              Util.scaleLargeTimestamp(
                  track.editListDurations[0], C.MICROS_PER_SECOND, track.movieTimescale);
          return new TrackSampleTable(
              track, offsets, sizes, maximumSize, timestamps, flags, editedDurationUs);
        }
      }
    }

    if (track.editListDurations.length == 1 && track.editListDurations[0] == 0) {
      // The current version of the spec leaves handling of an edit with zero segment_duration in
      // unfragmented files open to interpretation. We handle this as a special case and include all
      // samples in the edit.
      long editStartTime = track.editListMediaTimes[0];
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] =
            Util.scaleLargeTimestamp(
                timestamps[i] - editStartTime, C.MICROS_PER_SECOND, track.timescale);
      }
      durationUs =
          Util.scaleLargeTimestamp(duration - editStartTime, C.MICROS_PER_SECOND, track.timescale);
      return new TrackSampleTable(
          track, offsets, sizes, maximumSize, timestamps, flags, durationUs);
    }

    // Omit any sample at the end point of an edit for audio tracks.
    boolean omitClippedSample = track.type == C.TRACK_TYPE_AUDIO;

    // Count the number of samples after applying edits.
    int editedSampleCount = 0;
    int nextSampleIndex = 0;
    boolean copyMetadata = false;
    int[] startIndices = new int[track.editListDurations.length];
    int[] endIndices = new int[track.editListDurations.length];
    for (int i = 0; i < track.editListDurations.length; i++) {
      long editMediaTime = track.editListMediaTimes[i];
      if (editMediaTime != -1) {
        long editDuration =
            Util.scaleLargeTimestamp(
                track.editListDurations[i], track.timescale, track.movieTimescale);
        startIndices[i] = Util.binarySearchCeil(timestamps, editMediaTime, true, true);
        endIndices[i] =
            Util.binarySearchCeil(
                timestamps, editMediaTime + editDuration, omitClippedSample, false);
        while (startIndices[i] < endIndices[i]
            && (flags[startIndices[i]] & C.BUFFER_FLAG_KEY_FRAME) == 0) {
          // Applying the edit correctly would require prerolling from the previous sync sample. In
          // the current implementation we advance to the next sync sample instead. Only other
          // tracks (i.e. audio) will be rendered until the time of the first sync sample.
          // See https://github.com/google/ExoPlayer/issues/1659.
          startIndices[i]++;
        }
        editedSampleCount += endIndices[i] - startIndices[i];
        copyMetadata |= nextSampleIndex != startIndices[i];
        nextSampleIndex = endIndices[i];
      }
    }
    copyMetadata |= editedSampleCount != sampleCount;

    // Calculate edited sample timestamps and update the corresponding metadata arrays.
    long[] editedOffsets = copyMetadata ? new long[editedSampleCount] : offsets;
    int[] editedSizes = copyMetadata ? new int[editedSampleCount] : sizes;
    int editedMaximumSize = copyMetadata ? 0 : maximumSize;
    int[] editedFlags = copyMetadata ? new int[editedSampleCount] : flags;
    long[] editedTimestamps = new long[editedSampleCount];
    long pts = 0;
    int sampleIndex = 0;
    for (int i = 0; i < track.editListDurations.length; i++) {
      long editMediaTime = track.editListMediaTimes[i];
      int startIndex = startIndices[i];
      int endIndex = endIndices[i];
      if (copyMetadata) {
        int count = endIndex - startIndex;
        System.arraycopy(offsets, startIndex, editedOffsets, sampleIndex, count);
        System.arraycopy(sizes, startIndex, editedSizes, sampleIndex, count);
        System.arraycopy(flags, startIndex, editedFlags, sampleIndex, count);
      }
      for (int j = startIndex; j < endIndex; j++) {
        long ptsUs = Util.scaleLargeTimestamp(pts, C.MICROS_PER_SECOND, track.movieTimescale);
        long timeInSegmentUs =
            Util.scaleLargeTimestamp(
                timestamps[j] - editMediaTime, C.MICROS_PER_SECOND, track.timescale);
        editedTimestamps[sampleIndex] = ptsUs + timeInSegmentUs;
        if (copyMetadata && editedSizes[sampleIndex] > editedMaximumSize) {
          editedMaximumSize = sizes[j];
        }
        sampleIndex++;
      }
      pts += track.editListDurations[i];
    }
    long editedDurationUs =
        Util.scaleLargeTimestamp(pts, C.MICROS_PER_SECOND, track.movieTimescale);
    return new TrackSampleTable(
        track,
        editedOffsets,
        editedSizes,
        editedMaximumSize,
        editedTimestamps,
        editedFlags,
        editedDurationUs);
  }

  /**
   * Parses a udta atom.
   *
   * @param udtaAtom The udta (user data) atom to decode.
   * @param isQuickTime True for QuickTime media. False otherwise.
   * @return Parsed metadata, or null.
   */
  @Nullable
  public static Metadata parseUdta(Atom.LeafAtom udtaAtom, boolean isQuickTime) {
    if (isQuickTime) {
      // Meta boxes are regular boxes rather than full boxes in QuickTime. For now, don't try and
      // decode one.
      return null;
    }
    ParsableByteArray udtaData = udtaAtom.data;
    udtaData.setPosition(Atom.HEADER_SIZE);
    while (udtaData.bytesLeft() >= Atom.HEADER_SIZE) {
      int atomPosition = udtaData.getPosition();
      int atomSize = udtaData.readInt();
      int atomType = udtaData.readInt();
      if (atomType == Atom.TYPE_meta) {
        udtaData.setPosition(atomPosition);
        return parseUdtaMeta(udtaData, atomPosition + atomSize);
      }
      udtaData.setPosition(atomPosition + atomSize);
    }
    return null;
  }

  /**
   * Parses a metadata meta atom if it contains metadata with handler 'mdta'.
   *
   * @param meta The metadata atom to decode.
   * @return Parsed metadata, or null.
   */
  @Nullable
  public static Metadata parseMdtaFromMeta(Atom.ContainerAtom meta) {
    Atom.LeafAtom hdlrAtom = meta.getLeafAtomOfType(Atom.TYPE_hdlr);
    Atom.LeafAtom keysAtom = meta.getLeafAtomOfType(Atom.TYPE_keys);
    Atom.LeafAtom ilstAtom = meta.getLeafAtomOfType(Atom.TYPE_ilst);
    if (hdlrAtom == null
        || keysAtom == null
        || ilstAtom == null
        || AtomParsers.parseHdlr(hdlrAtom.data) != TYPE_mdta) {
      // There isn't enough information to parse the metadata, or the handler type is unexpected.
      return null;
    }

    // Parse metadata keys.
    ParsableByteArray keys = keysAtom.data;
    keys.setPosition(Atom.FULL_HEADER_SIZE);
    int entryCount = keys.readInt();
    String[] keyNames = new String[entryCount];
    for (int i = 0; i < entryCount; i++) {
      int entrySize = keys.readInt();
      keys.skipBytes(4); // keyNamespace
      int keySize = entrySize - 8;
      keyNames[i] = keys.readString(keySize);
    }

    // Parse metadata items.
    ParsableByteArray ilst = ilstAtom.data;
    ilst.setPosition(Atom.HEADER_SIZE);
    ArrayList<Metadata.Entry> entries = new ArrayList<>();
    while (ilst.bytesLeft() > Atom.HEADER_SIZE) {
      int atomPosition = ilst.getPosition();
      int atomSize = ilst.readInt();
      int keyIndex = ilst.readInt() - 1;
      if (keyIndex >= 0 && keyIndex < keyNames.length) {
        String key = keyNames[keyIndex];
        Metadata.Entry entry =
            MetadataUtil.parseMdtaMetadataEntryFromIlst(ilst, atomPosition + atomSize, key);
        if (entry != null) {
          entries.add(entry);
        }
      } else {
        Log.w(TAG, "Skipped metadata with unknown key index: " + keyIndex);
      }
      ilst.setPosition(atomPosition + atomSize);
    }
    return entries.isEmpty() ? null : new Metadata(entries);
  }

  @Nullable
  private static Metadata parseUdtaMeta(ParsableByteArray meta, int limit) {
    meta.skipBytes(Atom.FULL_HEADER_SIZE);
    while (meta.getPosition() < limit) {
      int atomPosition = meta.getPosition();
      int atomSize = meta.readInt();
      int atomType = meta.readInt();
      if (atomType == Atom.TYPE_ilst) {
        meta.setPosition(atomPosition);
        return parseIlst(meta, atomPosition + atomSize);
      }
      meta.setPosition(atomPosition + atomSize);
    }
    return null;
  }

  @Nullable
  private static Metadata parseIlst(ParsableByteArray ilst, int limit) {
    ilst.skipBytes(Atom.HEADER_SIZE);
    ArrayList<Metadata.Entry> entries = new ArrayList<>();
    while (ilst.getPosition() < limit) {
      Metadata.Entry entry = MetadataUtil.parseIlstElement(ilst);
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries.isEmpty() ? null : new Metadata(entries);
  }

  /**
   * Parses a mvhd atom (defined in 14496-12), returning the timescale for the movie.
   *
   * @param mvhd Contents of the mvhd atom to be parsed.
   * @return Timescale for the movie.
   */
  private static long parseMvhd(ParsableByteArray mvhd) {
    mvhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mvhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    mvhd.skipBytes(version == 0 ? 8 : 16);
    return mvhd.readUnsignedInt();
  }

  /**
   * Parses a tkhd atom (defined in 14496-12).
   *
   * @return An object containing the parsed data.
   */
  private static TkhdData parseTkhd(ParsableByteArray tkhd) {
    tkhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = tkhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);

    tkhd.skipBytes(version == 0 ? 8 : 16);
    int trackId = tkhd.readInt();

    tkhd.skipBytes(4);
    boolean durationUnknown = true;
    int durationPosition = tkhd.getPosition();
    int durationByteCount = version == 0 ? 4 : 8;
    for (int i = 0; i < durationByteCount; i++) {
      if (tkhd.data[durationPosition + i] != -1) {
        durationUnknown = false;
        break;
      }
    }
    long duration;
    if (durationUnknown) {
      tkhd.skipBytes(durationByteCount);
      duration = C.TIME_UNSET;
    } else {
      duration = version == 0 ? tkhd.readUnsignedInt() : tkhd.readUnsignedLongToLong();
      if (duration == 0) {
        // 0 duration normally indicates that the file is fully fragmented (i.e. all of the media
        // samples are in fragments). Treat as unknown.
        duration = C.TIME_UNSET;
      }
    }

    tkhd.skipBytes(16);
    int a00 = tkhd.readInt();
    int a01 = tkhd.readInt();
    tkhd.skipBytes(4);
    int a10 = tkhd.readInt();
    int a11 = tkhd.readInt();

    int rotationDegrees;
    int fixedOne = 65536;
    if (a00 == 0 && a01 == fixedOne && a10 == -fixedOne && a11 == 0) {
      rotationDegrees = 90;
    } else if (a00 == 0 && a01 == -fixedOne && a10 == fixedOne && a11 == 0) {
      rotationDegrees = 270;
    } else if (a00 == -fixedOne && a01 == 0 && a10 == 0 && a11 == -fixedOne) {
      rotationDegrees = 180;
    } else {
      // Only 0, 90, 180 and 270 are supported. Treat anything else as 0.
      rotationDegrees = 0;
    }

    return new TkhdData(trackId, duration, rotationDegrees);
  }

  /**
   * Parses an hdlr atom.
   *
   * @param hdlr The hdlr atom to decode.
   * @return The handler value.
   */
  private static int parseHdlr(ParsableByteArray hdlr) {
    hdlr.setPosition(Atom.FULL_HEADER_SIZE + 4);
    return hdlr.readInt();
  }

  /** Returns the track type for a given handler value. */
  private static int getTrackTypeForHdlr(int hdlr) {
    if (hdlr == TYPE_soun) {
      return C.TRACK_TYPE_AUDIO;
    } else if (hdlr == TYPE_vide) {
      return C.TRACK_TYPE_VIDEO;
    } else if (hdlr == TYPE_text || hdlr == TYPE_sbtl || hdlr == TYPE_subt || hdlr == TYPE_clcp) {
      return C.TRACK_TYPE_TEXT;
    } else if (hdlr == TYPE_meta) {
      return C.TRACK_TYPE_METADATA;
    } else {
      return C.TRACK_TYPE_UNKNOWN;
    }
  }

  /**
   * Parses an mdhd atom (defined in 14496-12).
   *
   * @param mdhd The mdhd atom to decode.
   * @return A pair consisting of the media timescale defined as the number of time units that pass
   * in one second, and the language code.
   */
  private static Pair<Long, String> parseMdhd(ParsableByteArray mdhd) {
    mdhd.setPosition(Atom.HEADER_SIZE);
    int fullAtom = mdhd.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    mdhd.skipBytes(version == 0 ? 8 : 16);
    long timescale = mdhd.readUnsignedInt();
    mdhd.skipBytes(version == 0 ? 4 : 8);
    int languageCode = mdhd.readUnsignedShort();
    String language =
        ""
            + (char) (((languageCode >> 10) & 0x1F) + 0x60)
            + (char) (((languageCode >> 5) & 0x1F) + 0x60)
            + (char) ((languageCode & 0x1F) + 0x60);
    return Pair.create(timescale, language);
  }

  /**
   * Parses a stsd atom (defined in 14496-12).
   *
   * @param stsd The stsd atom to decode.
   * @param trackId The track's identifier in its container.
   * @param rotationDegrees The rotation of the track in degrees.
   * @param language The language of the track.
   * @param drmInitData {@link DrmInitData} to be included in the format.
   * @param isQuickTime True for QuickTime media. False otherwise.
   * @return An object containing the parsed data.
   */
  private static StsdData parseStsd(ParsableByteArray stsd, int trackId, int rotationDegrees,
      String language, DrmInitData drmInitData, boolean isQuickTime) throws ParserException {
    stsd.setPosition(Atom.FULL_HEADER_SIZE);
    int numberOfEntries = stsd.readInt();
    StsdData out = new StsdData(numberOfEntries);
    for (int i = 0; i < numberOfEntries; i++) {
      int childStartPosition = stsd.getPosition();
      int childAtomSize = stsd.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = stsd.readInt();
      if (childAtomType == Atom.TYPE_avc1
          || childAtomType == Atom.TYPE_avc3
          || childAtomType == Atom.TYPE_encv
          || childAtomType == Atom.TYPE_mp4v
          || childAtomType == Atom.TYPE_hvc1
          || childAtomType == Atom.TYPE_hev1
          || childAtomType == Atom.TYPE_s263
          || childAtomType == Atom.TYPE_vp08
          || childAtomType == Atom.TYPE_vp09
          || childAtomType == Atom.TYPE_av01
          || childAtomType == Atom.TYPE_dvav
          || childAtomType == Atom.TYPE_dva1
          || childAtomType == Atom.TYPE_dvhe
          || childAtomType == Atom.TYPE_dvh1) {
        parseVideoSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId,
            rotationDegrees, drmInitData, out, i);
      } else if (childAtomType == Atom.TYPE_mp4a
          || childAtomType == Atom.TYPE_enca
          || childAtomType == Atom.TYPE_ac_3
          || childAtomType == Atom.TYPE_ec_3
          || childAtomType == Atom.TYPE_ac_4
          || childAtomType == Atom.TYPE_dtsc
          || childAtomType == Atom.TYPE_dtse
          || childAtomType == Atom.TYPE_dtsh
          || childAtomType == Atom.TYPE_dtsl
          || childAtomType == Atom.TYPE_samr
          || childAtomType == Atom.TYPE_sawb
          || childAtomType == Atom.TYPE_lpcm
          || childAtomType == Atom.TYPE_sowt
          || childAtomType == Atom.TYPE__mp3
          || childAtomType == Atom.TYPE_alac
          || childAtomType == Atom.TYPE_alaw
          || childAtomType == Atom.TYPE_ulaw
          || childAtomType == Atom.TYPE_Opus
          || childAtomType == Atom.TYPE_fLaC) {
        parseAudioSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId,
            language, isQuickTime, drmInitData, out, i);
      } else if (childAtomType == Atom.TYPE_TTML || childAtomType == Atom.TYPE_tx3g
          || childAtomType == Atom.TYPE_wvtt || childAtomType == Atom.TYPE_stpp
          || childAtomType == Atom.TYPE_c608) {
        parseTextSampleEntry(stsd, childAtomType, childStartPosition, childAtomSize, trackId,
            language, out);
      } else if (childAtomType == Atom.TYPE_camm) {
        out.format = Format.createSampleFormat(Integer.toString(trackId),
            MimeTypes.APPLICATION_CAMERA_MOTION, null, Format.NO_VALUE, null);
      }
      stsd.setPosition(childStartPosition + childAtomSize);
    }
    return out;
  }

  private static void parseTextSampleEntry(ParsableByteArray parent, int atomType, int position,
      int atomSize, int trackId, String language, StsdData out) throws ParserException {
    parent.setPosition(position + Atom.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    // Default values.
    List<byte[]> initializationData = null;
    long subsampleOffsetUs = Format.OFFSET_SAMPLE_RELATIVE;

    String mimeType;
    if (atomType == Atom.TYPE_TTML) {
      mimeType = MimeTypes.APPLICATION_TTML;
    } else if (atomType == Atom.TYPE_tx3g) {
      mimeType = MimeTypes.APPLICATION_TX3G;
      int sampleDescriptionLength = atomSize - Atom.HEADER_SIZE - 8;
      byte[] sampleDescriptionData = new byte[sampleDescriptionLength];
      parent.readBytes(sampleDescriptionData, 0, sampleDescriptionLength);
      initializationData = Collections.singletonList(sampleDescriptionData);
    } else if (atomType == Atom.TYPE_wvtt) {
      mimeType = MimeTypes.APPLICATION_MP4VTT;
    } else if (atomType == Atom.TYPE_stpp) {
      mimeType = MimeTypes.APPLICATION_TTML;
      subsampleOffsetUs = 0; // Subsample timing is absolute.
    } else if (atomType == Atom.TYPE_c608) {
      // Defined by the QuickTime File Format specification.
      mimeType = MimeTypes.APPLICATION_MP4CEA608;
      out.requiredSampleTransformation = Track.TRANSFORMATION_CEA608_CDAT;
    } else {
      // Never happens.
      throw new IllegalStateException();
    }

    out.format =
        Format.createTextSampleFormat(
            Integer.toString(trackId),
            mimeType,
            /* codecs= */ null,
            /* bitrate= */ Format.NO_VALUE,
            /* selectionFlags= */ 0,
            language,
            /* accessibilityChannel= */ Format.NO_VALUE,
            /* drmInitData= */ null,
            subsampleOffsetUs,
            initializationData);
  }

  private static void parseVideoSampleEntry(ParsableByteArray parent, int atomType, int position,
      int size, int trackId, int rotationDegrees, DrmInitData drmInitData, StsdData out,
      int entryIndex) throws ParserException {
    parent.setPosition(position + Atom.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    parent.skipBytes(16);
    int width = parent.readUnsignedShort();
    int height = parent.readUnsignedShort();
    boolean pixelWidthHeightRatioFromPasp = false;
    float pixelWidthHeightRatio = 1;
    parent.skipBytes(50);

    int childPosition = parent.getPosition();
    if (atomType == Atom.TYPE_encv) {
      Pair<Integer, TrackEncryptionBox> sampleEntryEncryptionData = parseSampleEntryEncryptionData(
          parent, position, size);
      if (sampleEntryEncryptionData != null) {
        atomType = sampleEntryEncryptionData.first;
        drmInitData = drmInitData == null ? null
            : drmInitData.copyWithSchemeType(sampleEntryEncryptionData.second.schemeType);
        out.trackEncryptionBoxes[entryIndex] = sampleEntryEncryptionData.second;
      }
      parent.setPosition(childPosition);
    }
    // TODO: Uncomment when [Internal: b/63092960] is fixed.
    // else {
    //   drmInitData = null;
    // }

    List<byte[]> initializationData = null;
    String mimeType = null;
    String codecs = null;
    byte[] projectionData = null;
    @C.StereoMode
    int stereoMode = Format.NO_VALUE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childStartPosition = parent.getPosition();
      int childAtomSize = parent.readInt();
      if (childAtomSize == 0 && parent.getPosition() - position == size) {
        // Handle optional terminating four zero bytes in MOV files.
        break;
      }
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_avcC) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_H264;
        parent.setPosition(childStartPosition + Atom.HEADER_SIZE);
        AvcConfig avcConfig = AvcConfig.parse(parent);
        initializationData = avcConfig.initializationData;
        out.nalUnitLengthFieldLength = avcConfig.nalUnitLengthFieldLength;
        if (!pixelWidthHeightRatioFromPasp) {
          pixelWidthHeightRatio = avcConfig.pixelWidthAspectRatio;
        }
      } else if (childAtomType == Atom.TYPE_hvcC) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_H265;
        parent.setPosition(childStartPosition + Atom.HEADER_SIZE);
        HevcConfig hevcConfig = HevcConfig.parse(parent);
        initializationData = hevcConfig.initializationData;
        out.nalUnitLengthFieldLength = hevcConfig.nalUnitLengthFieldLength;
      } else if (childAtomType == Atom.TYPE_dvcC || childAtomType == Atom.TYPE_dvvC) {
        DolbyVisionConfig dolbyVisionConfig = DolbyVisionConfig.parse(parent);
        // TODO: Support profiles 4, 8 and 9 once we have a way to fall back to AVC/HEVC decoding.
        if (dolbyVisionConfig != null && dolbyVisionConfig.profile == 5) {
          codecs = dolbyVisionConfig.codecs;
          mimeType = MimeTypes.VIDEO_DOLBY_VISION;
        }
      } else if (childAtomType == Atom.TYPE_vpcC) {
        Assertions.checkState(mimeType == null);
        mimeType = (atomType == Atom.TYPE_vp08) ? MimeTypes.VIDEO_VP8 : MimeTypes.VIDEO_VP9;
      } else if (childAtomType == Atom.TYPE_av1C) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_AV1;
      } else if (childAtomType == Atom.TYPE_d263) {
        Assertions.checkState(mimeType == null);
        mimeType = MimeTypes.VIDEO_H263;
      } else if (childAtomType == Atom.TYPE_esds) {
        Assertions.checkState(mimeType == null);
        Pair<String, byte[]> mimeTypeAndInitializationData =
            parseEsdsFromParent(parent, childStartPosition);
        mimeType = mimeTypeAndInitializationData.first;
        initializationData = Collections.singletonList(mimeTypeAndInitializationData.second);
      } else if (childAtomType == Atom.TYPE_pasp) {
        pixelWidthHeightRatio = parsePaspFromParent(parent, childStartPosition);
        pixelWidthHeightRatioFromPasp = true;
      } else if (childAtomType == Atom.TYPE_sv3d) {
        projectionData = parseProjFromParent(parent, childStartPosition, childAtomSize);
      } else if (childAtomType == Atom.TYPE_st3d) {
        int version = parent.readUnsignedByte();
        parent.skipBytes(3); // Flags.
        if (version == 0) {
          int layout = parent.readUnsignedByte();
          switch (layout) {
            case 0:
              stereoMode = C.STEREO_MODE_MONO;
              break;
            case 1:
              stereoMode = C.STEREO_MODE_TOP_BOTTOM;
              break;
            case 2:
              stereoMode = C.STEREO_MODE_LEFT_RIGHT;
              break;
            case 3:
              stereoMode = C.STEREO_MODE_STEREO_MESH;
              break;
            default:
              break;
          }
        }
      }
      childPosition += childAtomSize;
    }

    // If the media type was not recognized, ignore the track.
    if (mimeType == null) {
      return;
    }

    out.format =
        Format.createVideoSampleFormat(
            Integer.toString(trackId),
            mimeType,
            codecs,
            /* bitrate= */ Format.NO_VALUE,
            /* maxInputSize= */ Format.NO_VALUE,
            width,
            height,
            /* frameRate= */ Format.NO_VALUE,
            initializationData,
            rotationDegrees,
            pixelWidthHeightRatio,
            projectionData,
            stereoMode,
            /* colorInfo= */ null,
            drmInitData);
  }

  /**
   * Parses the edts atom (defined in 14496-12 subsection 8.6.5).
   *
   * @param edtsAtom edts (edit box) atom to decode.
   * @return Pair of edit list durations and edit list media times, or a pair of nulls if they are
   *     not present.
   */
  private static Pair<long[], long[]> parseEdts(Atom.ContainerAtom edtsAtom) {
    Atom.LeafAtom elst;
    if (edtsAtom == null || (elst = edtsAtom.getLeafAtomOfType(Atom.TYPE_elst)) == null) {
      return Pair.create(null, null);
    }
    ParsableByteArray elstData = elst.data;
    elstData.setPosition(Atom.HEADER_SIZE);
    int fullAtom = elstData.readInt();
    int version = Atom.parseFullAtomVersion(fullAtom);
    int entryCount = elstData.readUnsignedIntToInt();
    long[] editListDurations = new long[entryCount];
    long[] editListMediaTimes = new long[entryCount];
    for (int i = 0; i < entryCount; i++) {
      editListDurations[i] =
          version == 1 ? elstData.readUnsignedLongToLong() : elstData.readUnsignedInt();
      editListMediaTimes[i] = version == 1 ? elstData.readLong() : elstData.readInt();
      int mediaRateInteger = elstData.readShort();
      if (mediaRateInteger != 1) {
        // The extractor does not handle dwell edits (mediaRateInteger == 0).
        throw new IllegalArgumentException("Unsupported media rate.");
      }
      elstData.skipBytes(2);
    }
    return Pair.create(editListDurations, editListMediaTimes);
  }

  private static float parsePaspFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Atom.HEADER_SIZE);
    int hSpacing = parent.readUnsignedIntToInt();
    int vSpacing = parent.readUnsignedIntToInt();
    return (float) hSpacing / vSpacing;
  }

  private static void parseAudioSampleEntry(ParsableByteArray parent, int atomType, int position,
      int size, int trackId, String language, boolean isQuickTime, DrmInitData drmInitData,
      StsdData out, int entryIndex) throws ParserException {
    parent.setPosition(position + Atom.HEADER_SIZE + StsdData.STSD_HEADER_SIZE);

    int quickTimeSoundDescriptionVersion = 0;
    if (isQuickTime) {
      quickTimeSoundDescriptionVersion = parent.readUnsignedShort();
      parent.skipBytes(6);
    } else {
      parent.skipBytes(8);
    }

    int channelCount;
    int sampleRate;

    if (quickTimeSoundDescriptionVersion == 0 || quickTimeSoundDescriptionVersion == 1) {
      channelCount = parent.readUnsignedShort();
      parent.skipBytes(6);  // sampleSize, compressionId, packetSize.
      sampleRate = parent.readUnsignedFixedPoint1616();

      if (quickTimeSoundDescriptionVersion == 1) {
        parent.skipBytes(16);
      }
    } else if (quickTimeSoundDescriptionVersion == 2) {
      parent.skipBytes(16);  // always[3,16,Minus2,0,65536], sizeOfStructOnly

      sampleRate = (int) Math.round(parent.readDouble());
      channelCount = parent.readUnsignedIntToInt();

      // Skip always7F000000, sampleSize, formatSpecificFlags, constBytesPerAudioPacket,
      // constLPCMFramesPerAudioPacket.
      parent.skipBytes(20);
    } else {
      // Unsupported version.
      return;
    }

    int childPosition = parent.getPosition();
    if (atomType == Atom.TYPE_enca) {
      Pair<Integer, TrackEncryptionBox> sampleEntryEncryptionData = parseSampleEntryEncryptionData(
          parent, position, size);
      if (sampleEntryEncryptionData != null) {
        atomType = sampleEntryEncryptionData.first;
        drmInitData = drmInitData == null ? null
            : drmInitData.copyWithSchemeType(sampleEntryEncryptionData.second.schemeType);
        out.trackEncryptionBoxes[entryIndex] = sampleEntryEncryptionData.second;
      }
      parent.setPosition(childPosition);
    }
    // TODO: Uncomment when [Internal: b/63092960] is fixed.
    // else {
    //   drmInitData = null;
    // }

    // If the atom type determines a MIME type, set it immediately.
    String mimeType = null;
    if (atomType == Atom.TYPE_ac_3) {
      mimeType = MimeTypes.AUDIO_AC3;
    } else if (atomType == Atom.TYPE_ec_3) {
      mimeType = MimeTypes.AUDIO_E_AC3;
    } else if (atomType == Atom.TYPE_ac_4) {
      mimeType = MimeTypes.AUDIO_AC4;
    } else if (atomType == Atom.TYPE_dtsc) {
      mimeType = MimeTypes.AUDIO_DTS;
    } else if (atomType == Atom.TYPE_dtsh || atomType == Atom.TYPE_dtsl) {
      mimeType = MimeTypes.AUDIO_DTS_HD;
    } else if (atomType == Atom.TYPE_dtse) {
      mimeType = MimeTypes.AUDIO_DTS_EXPRESS;
    } else if (atomType == Atom.TYPE_samr) {
      mimeType = MimeTypes.AUDIO_AMR_NB;
    } else if (atomType == Atom.TYPE_sawb) {
      mimeType = MimeTypes.AUDIO_AMR_WB;
    } else if (atomType == Atom.TYPE_lpcm || atomType == Atom.TYPE_sowt) {
      mimeType = MimeTypes.AUDIO_RAW;
    } else if (atomType == Atom.TYPE__mp3) {
      mimeType = MimeTypes.AUDIO_MPEG;
    } else if (atomType == Atom.TYPE_alac) {
      mimeType = MimeTypes.AUDIO_ALAC;
    } else if (atomType == Atom.TYPE_alaw) {
      mimeType = MimeTypes.AUDIO_ALAW;
    } else if (atomType == Atom.TYPE_ulaw) {
      mimeType = MimeTypes.AUDIO_MLAW;
    } else if (atomType == Atom.TYPE_Opus) {
      mimeType = MimeTypes.AUDIO_OPUS;
    } else if (atomType == Atom.TYPE_fLaC) {
      mimeType = MimeTypes.AUDIO_FLAC;
    }

    byte[] initializationData = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_esds || (isQuickTime && childAtomType == Atom.TYPE_wave)) {
        int esdsAtomPosition = childAtomType == Atom.TYPE_esds ? childPosition
            : findEsdsPosition(parent, childPosition, childAtomSize);
        if (esdsAtomPosition != C.POSITION_UNSET) {
          Pair<String, byte[]> mimeTypeAndInitializationData =
              parseEsdsFromParent(parent, esdsAtomPosition);
          mimeType = mimeTypeAndInitializationData.first;
          initializationData = mimeTypeAndInitializationData.second;
          if (MimeTypes.AUDIO_AAC.equals(mimeType)) {
            // TODO: Do we really need to do this? See [Internal: b/10903778]
            // Update sampleRate and channelCount from the AudioSpecificConfig initialization data.
            Pair<Integer, Integer> audioSpecificConfig =
                CodecSpecificDataUtil.parseAacAudioSpecificConfig(initializationData);
            sampleRate = audioSpecificConfig.first;
            channelCount = audioSpecificConfig.second;
          }
        }
      } else if (childAtomType == Atom.TYPE_dac3) {
        parent.setPosition(Atom.HEADER_SIZE + childPosition);
        out.format = Ac3Util.parseAc3AnnexFFormat(parent, Integer.toString(trackId), language,
            drmInitData);
      } else if (childAtomType == Atom.TYPE_dec3) {
        parent.setPosition(Atom.HEADER_SIZE + childPosition);
        out.format = Ac3Util.parseEAc3AnnexFFormat(parent, Integer.toString(trackId), language,
            drmInitData);
      } else if (childAtomType == Atom.TYPE_dac4) {
        parent.setPosition(Atom.HEADER_SIZE + childPosition);
        out.format =
            Ac4Util.parseAc4AnnexEFormat(parent, Integer.toString(trackId), language, drmInitData);
      } else if (childAtomType == Atom.TYPE_ddts) {
        out.format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null,
            Format.NO_VALUE, Format.NO_VALUE, channelCount, sampleRate, null, drmInitData, 0,
            language);
      } else if (childAtomType == Atom.TYPE_dOps) {
        // Build an Opus Identification Header (defined in RFC-7845) by concatenating the Opus Magic
        // Signature and the body of the dOps atom.
        int childAtomBodySize = childAtomSize - Atom.HEADER_SIZE;
        initializationData = new byte[opusMagic.length + childAtomBodySize];
        System.arraycopy(opusMagic, 0, initializationData, 0, opusMagic.length);
        parent.setPosition(childPosition + Atom.HEADER_SIZE);
        parent.readBytes(initializationData, opusMagic.length, childAtomBodySize);
      } else if (childAtomType == Atom.TYPE_dfLa) {
        int childAtomBodySize = childAtomSize - Atom.FULL_HEADER_SIZE;
        initializationData = new byte[4 + childAtomBodySize];
        initializationData[0] = 0x66; // f
        initializationData[1] = 0x4C; // L
        initializationData[2] = 0x61; // a
        initializationData[3] = 0x43; // C
        parent.setPosition(childPosition + Atom.FULL_HEADER_SIZE);
        parent.readBytes(initializationData, /* offset= */ 4, childAtomBodySize);
      } else if (childAtomType == Atom.TYPE_alac) {
        int childAtomBodySize = childAtomSize - Atom.FULL_HEADER_SIZE;
        initializationData = new byte[childAtomBodySize];
        parent.setPosition(childPosition + Atom.FULL_HEADER_SIZE);
        parent.readBytes(initializationData, /* offset= */ 0, childAtomBodySize);
      }
      childPosition += childAtomSize;
    }

    if (out.format == null && mimeType != null) {
      // TODO: Determine the correct PCM encoding.
      @C.PcmEncoding int pcmEncoding =
          MimeTypes.AUDIO_RAW.equals(mimeType) ? C.ENCODING_PCM_16BIT : Format.NO_VALUE;
      out.format = Format.createAudioSampleFormat(Integer.toString(trackId), mimeType, null,
          Format.NO_VALUE, Format.NO_VALUE, channelCount, sampleRate, pcmEncoding,
          initializationData == null ? null : Collections.singletonList(initializationData),
          drmInitData, 0, language);
    }
  }

  /**
   * Returns the position of the esds box within a parent, or {@link C#POSITION_UNSET} if no esds
   * box is found
   */
  private static int findEsdsPosition(ParsableByteArray parent, int position, int size) {
    int childAtomPosition = parent.getPosition();
    while (childAtomPosition - position < size) {
      parent.setPosition(childAtomPosition);
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childType = parent.readInt();
      if (childType == Atom.TYPE_esds) {
        return childAtomPosition;
      }
      childAtomPosition += childAtomSize;
    }
    return C.POSITION_UNSET;
  }

  /**
   * Returns codec-specific initialization data contained in an esds box.
   */
  private static Pair<String, byte[]> parseEsdsFromParent(ParsableByteArray parent, int position) {
    parent.setPosition(position + Atom.HEADER_SIZE + 4);
    // Start of the ES_Descriptor (defined in 14496-1)
    parent.skipBytes(1); // ES_Descriptor tag
    parseExpandableClassSize(parent);
    parent.skipBytes(2); // ES_ID

    int flags = parent.readUnsignedByte();
    if ((flags & 0x80 /* streamDependenceFlag */) != 0) {
      parent.skipBytes(2);
    }
    if ((flags & 0x40 /* URL_Flag */) != 0) {
      parent.skipBytes(parent.readUnsignedShort());
    }
    if ((flags & 0x20 /* OCRstreamFlag */) != 0) {
      parent.skipBytes(2);
    }

    // Start of the DecoderConfigDescriptor (defined in 14496-1)
    parent.skipBytes(1); // DecoderConfigDescriptor tag
    parseExpandableClassSize(parent);

    // Set the MIME type based on the object type indication (14496-1 table 5).
    int objectTypeIndication = parent.readUnsignedByte();
    String mimeType = getMimeTypeFromMp4ObjectType(objectTypeIndication);
    if (MimeTypes.AUDIO_MPEG.equals(mimeType)
        || MimeTypes.AUDIO_DTS.equals(mimeType)
        || MimeTypes.AUDIO_DTS_HD.equals(mimeType)) {
      return Pair.create(mimeType, null);
    }

    parent.skipBytes(12);

    // Start of the DecoderSpecificInfo.
    parent.skipBytes(1); // DecoderSpecificInfo tag
    int initializationDataSize = parseExpandableClassSize(parent);
    byte[] initializationData = new byte[initializationDataSize];
    parent.readBytes(initializationData, 0, initializationDataSize);
    return Pair.create(mimeType, initializationData);
  }

  /**
   * Parses encryption data from an audio/video sample entry, returning a pair consisting of the
   * unencrypted atom type and a {@link TrackEncryptionBox}. Null is returned if no common
   * encryption sinf atom was present.
   */
  private static Pair<Integer, TrackEncryptionBox> parseSampleEntryEncryptionData(
      ParsableByteArray parent, int position, int size) {
    int childPosition = parent.getPosition();
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      Assertions.checkArgument(childAtomSize > 0, "childAtomSize should be positive");
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_sinf) {
        Pair<Integer, TrackEncryptionBox> result = parseCommonEncryptionSinfFromParent(parent,
            childPosition, childAtomSize);
        if (result != null) {
          return result;
        }
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /* package */ static Pair<Integer, TrackEncryptionBox> parseCommonEncryptionSinfFromParent(
      ParsableByteArray parent, int position, int size) {
    int childPosition = position + Atom.HEADER_SIZE;
    int schemeInformationBoxPosition = C.POSITION_UNSET;
    int schemeInformationBoxSize = 0;
    String schemeType = null;
    Integer dataFormat = null;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_frma) {
        dataFormat = parent.readInt();
      } else if (childAtomType == Atom.TYPE_schm) {
        parent.skipBytes(4);
        // Common encryption scheme_type values are defined in ISO/IEC 23001-7:2016, section 4.1.
        schemeType = parent.readString(4);
      } else if (childAtomType == Atom.TYPE_schi) {
        schemeInformationBoxPosition = childPosition;
        schemeInformationBoxSize = childAtomSize;
      }
      childPosition += childAtomSize;
    }

    if (C.CENC_TYPE_cenc.equals(schemeType) || C.CENC_TYPE_cbc1.equals(schemeType)
        || C.CENC_TYPE_cens.equals(schemeType) || C.CENC_TYPE_cbcs.equals(schemeType)) {
      Assertions.checkArgument(dataFormat != null, "frma atom is mandatory");
      Assertions.checkArgument(schemeInformationBoxPosition != C.POSITION_UNSET,
          "schi atom is mandatory");
      TrackEncryptionBox encryptionBox = parseSchiFromParent(parent, schemeInformationBoxPosition,
          schemeInformationBoxSize, schemeType);
      Assertions.checkArgument(encryptionBox != null, "tenc atom is mandatory");
      return Pair.create(dataFormat, encryptionBox);
    } else {
      return null;
    }
  }

  private static TrackEncryptionBox parseSchiFromParent(ParsableByteArray parent, int position,
      int size, String schemeType) {
    int childPosition = position + Atom.HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_tenc) {
        int fullAtom = parent.readInt();
        int version = Atom.parseFullAtomVersion(fullAtom);
        parent.skipBytes(1); // reserved = 0.
        int defaultCryptByteBlock = 0;
        int defaultSkipByteBlock = 0;
        if (version == 0) {
          parent.skipBytes(1); // reserved = 0.
        } else /* version 1 or greater */ {
          int patternByte = parent.readUnsignedByte();
          defaultCryptByteBlock = (patternByte & 0xF0) >> 4;
          defaultSkipByteBlock = patternByte & 0x0F;
        }
        boolean defaultIsProtected = parent.readUnsignedByte() == 1;
        int defaultPerSampleIvSize = parent.readUnsignedByte();
        byte[] defaultKeyId = new byte[16];
        parent.readBytes(defaultKeyId, 0, defaultKeyId.length);
        byte[] constantIv = null;
        if (defaultIsProtected && defaultPerSampleIvSize == 0) {
          int constantIvSize = parent.readUnsignedByte();
          constantIv = new byte[constantIvSize];
          parent.readBytes(constantIv, 0, constantIvSize);
        }
        return new TrackEncryptionBox(defaultIsProtected, schemeType, defaultPerSampleIvSize,
            defaultKeyId, defaultCryptByteBlock, defaultSkipByteBlock, constantIv);
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /**
   * Parses the proj box from sv3d box, as specified by https://github.com/google/spatial-media.
   */
  private static byte[] parseProjFromParent(ParsableByteArray parent, int position, int size) {
    int childPosition = position + Atom.HEADER_SIZE;
    while (childPosition - position < size) {
      parent.setPosition(childPosition);
      int childAtomSize = parent.readInt();
      int childAtomType = parent.readInt();
      if (childAtomType == Atom.TYPE_proj) {
        return Arrays.copyOfRange(parent.data, childPosition, childPosition + childAtomSize);
      }
      childPosition += childAtomSize;
    }
    return null;
  }

  /**
   * Parses the size of an expandable class, as specified by ISO 14496-1 subsection 8.3.3.
   */
  private static int parseExpandableClassSize(ParsableByteArray data) {
    int currentByte = data.readUnsignedByte();
    int size = currentByte & 0x7F;
    while ((currentByte & 0x80) == 0x80) {
      currentByte = data.readUnsignedByte();
      size = (size << 7) | (currentByte & 0x7F);
    }
    return size;
  }

  /** Returns whether it's possible to apply the specified edit using gapless playback info. */
  private static boolean canApplyEditWithGaplessInfo(
      long[] timestamps, long duration, long editStartTime, long editEndTime) {
    int lastIndex = timestamps.length - 1;
    int latestDelayIndex = Util.constrainValue(MAX_GAPLESS_TRIM_SIZE_SAMPLES, 0, lastIndex);
    int earliestPaddingIndex =
        Util.constrainValue(timestamps.length - MAX_GAPLESS_TRIM_SIZE_SAMPLES, 0, lastIndex);
    return timestamps[0] <= editStartTime
        && editStartTime < timestamps[latestDelayIndex]
        && timestamps[earliestPaddingIndex] < editEndTime
        && editEndTime <= duration;
  }

  private AtomParsers() {
    // Prevent instantiation.
  }

  private static final class ChunkIterator {

    public final int length;

    public int index;
    public int numSamples;
    public long offset;

    private final boolean chunkOffsetsAreLongs;
    private final ParsableByteArray chunkOffsets;
    private final ParsableByteArray stsc;

    private int nextSamplesPerChunkChangeIndex;
    private int remainingSamplesPerChunkChanges;

    public ChunkIterator(ParsableByteArray stsc, ParsableByteArray chunkOffsets,
        boolean chunkOffsetsAreLongs) {
      this.stsc = stsc;
      this.chunkOffsets = chunkOffsets;
      this.chunkOffsetsAreLongs = chunkOffsetsAreLongs;
      chunkOffsets.setPosition(Atom.FULL_HEADER_SIZE);
      length = chunkOffsets.readUnsignedIntToInt();
      stsc.setPosition(Atom.FULL_HEADER_SIZE);
      remainingSamplesPerChunkChanges = stsc.readUnsignedIntToInt();
      Assertions.checkState(stsc.readInt() == 1, "first_chunk must be 1");
      index = -1;
    }

    public boolean moveNext() {
      if (++index == length) {
        return false;
      }
      offset = chunkOffsetsAreLongs ? chunkOffsets.readUnsignedLongToLong()
          : chunkOffsets.readUnsignedInt();
      if (index == nextSamplesPerChunkChangeIndex) {
        numSamples = stsc.readUnsignedIntToInt();
        stsc.skipBytes(4); // Skip sample_description_index
        nextSamplesPerChunkChangeIndex = --remainingSamplesPerChunkChanges > 0
            ? (stsc.readUnsignedIntToInt() - 1) : C.INDEX_UNSET;
      }
      return true;
    }

  }

  /**
   * Holds data parsed from a tkhd atom.
   */
  private static final class TkhdData {

    private final int id;
    private final long duration;
    private final int rotationDegrees;

    public TkhdData(int id, long duration, int rotationDegrees) {
      this.id = id;
      this.duration = duration;
      this.rotationDegrees = rotationDegrees;
    }

  }

  /**
   * Holds data parsed from an stsd atom and its children.
   */
  private static final class StsdData {

    public static final int STSD_HEADER_SIZE = 8;

    public final TrackEncryptionBox[] trackEncryptionBoxes;

    public Format format;
    public int nalUnitLengthFieldLength;
    @Track.Transformation
    public int requiredSampleTransformation;

    public StsdData(int numberOfEntries) {
      trackEncryptionBoxes = new TrackEncryptionBox[numberOfEntries];
      requiredSampleTransformation = Track.TRANSFORMATION_NONE;
    }

  }

  /**
   * A box containing sample sizes (e.g. stsz, stz2).
   */
  private interface SampleSizeBox {

    /**
     * Returns the number of samples.
     */
    int getSampleCount();

    /**
     * Returns the size for the next sample.
     */
    int readNextSampleSize();

    /**
     * Returns whether samples have a fixed size.
     */
    boolean isFixedSampleSize();

  }

  /**
   * An stsz sample size box.
   */
  /* package */ static final class StszSampleSizeBox implements SampleSizeBox {

    private final int fixedSampleSize;
    private final int sampleCount;
    private final ParsableByteArray data;

    public StszSampleSizeBox(Atom.LeafAtom stszAtom) {
      data = stszAtom.data;
      data.setPosition(Atom.FULL_HEADER_SIZE);
      fixedSampleSize = data.readUnsignedIntToInt();
      sampleCount = data.readUnsignedIntToInt();
    }

    @Override
    public int getSampleCount() {
      return sampleCount;
    }

    @Override
    public int readNextSampleSize() {
      return fixedSampleSize == 0 ? data.readUnsignedIntToInt() : fixedSampleSize;
    }

    @Override
    public boolean isFixedSampleSize() {
      return fixedSampleSize != 0;
    }

  }

  /**
   * An stz2 sample size box.
   */
  /* package */ static final class Stz2SampleSizeBox implements SampleSizeBox {

    private final ParsableByteArray data;
    private final int sampleCount;
    private final int fieldSize; // Can be 4, 8, or 16.

    // Used only if fieldSize == 4.
    private int sampleIndex;
    private int currentByte;

    public Stz2SampleSizeBox(Atom.LeafAtom stz2Atom) {
      data = stz2Atom.data;
      data.setPosition(Atom.FULL_HEADER_SIZE);
      fieldSize = data.readUnsignedIntToInt() & 0x000000FF;
      sampleCount = data.readUnsignedIntToInt();
    }

    @Override
    public int getSampleCount() {
      return sampleCount;
    }

    @Override
    public int readNextSampleSize() {
      if (fieldSize == 8) {
        return data.readUnsignedByte();
      } else if (fieldSize == 16) {
        return data.readUnsignedShort();
      } else {
        // fieldSize == 4.
        if ((sampleIndex++ % 2) == 0) {
          // Read the next byte into our cached byte when we are reading the upper bits.
          currentByte = data.readUnsignedByte();
          // Read the upper bits from the byte and shift them to the lower 4 bits.
          return (currentByte & 0xF0) >> 4;
        } else {
          // Mask out the upper 4 bits of the last byte we read.
          return currentByte & 0x0F;
        }
      }
    }

    @Override
    public boolean isFixedSampleSize() {
      return false;
    }

  }

}
