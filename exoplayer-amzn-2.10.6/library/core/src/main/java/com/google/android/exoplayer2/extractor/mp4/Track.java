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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Encapsulates information describing an MP4 track.
 */
public final class Track {

  /**
   * The transformation to apply to samples in the track, if any. One of {@link
   * #TRANSFORMATION_NONE} or {@link #TRANSFORMATION_CEA608_CDAT}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TRANSFORMATION_NONE, TRANSFORMATION_CEA608_CDAT})
  public @interface Transformation {}
  /**
   * A no-op sample transformation.
   */
  public static final int TRANSFORMATION_NONE = 0;
  /**
   * A transformation for caption samples in cdat atoms.
   */
  public static final int TRANSFORMATION_CEA608_CDAT = 1;

  /**
   * The track identifier.
   */
  public final int id;

  /**
   * One of {@link C#TRACK_TYPE_AUDIO}, {@link C#TRACK_TYPE_VIDEO} and {@link C#TRACK_TYPE_TEXT}.
   */
  public final int type;

  /**
   * The track timescale, defined as the number of time units that pass in one second.
   */
  public final long timescale;

  /**
   * The movie timescale.
   */
  public final long movieTimescale;

  /**
   * The duration of the track in microseconds, or {@link C#TIME_UNSET} if unknown.
   */
  public final long durationUs;

  /**
   * The format.
   */
  public final Format format;

  /**
   * One of {@code TRANSFORMATION_*}. Defines the transformation to apply before outputting each
   * sample.
   */
  @Transformation public final int sampleTransformation;

  /**
   * Durations of edit list segments in the movie timescale. Null if there is no edit list.
   */
  @Nullable public final long[] editListDurations;

  /**
   * Media times for edit list segments in the track timescale. Null if there is no edit list.
   */
  @Nullable public final long[] editListMediaTimes;

  /**
   * For H264 video tracks, the length in bytes of the NALUnitLength field in each sample. 0 for
   * other track types.
   */
  public final int nalUnitLengthFieldLength;

  @Nullable private final TrackEncryptionBox[] sampleDescriptionEncryptionBoxes;

  public Track(int id, int type, long timescale, long movieTimescale, long durationUs,
      Format format, @Transformation int sampleTransformation,
      @Nullable TrackEncryptionBox[] sampleDescriptionEncryptionBoxes, int nalUnitLengthFieldLength,
      @Nullable long[] editListDurations, @Nullable long[] editListMediaTimes) {
    this.id = id;
    this.type = type;
    this.timescale = timescale;
    this.movieTimescale = movieTimescale;
    this.durationUs = durationUs;
    this.format = format;
    this.sampleTransformation = sampleTransformation;
    this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
    this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
    this.editListDurations = editListDurations;
    this.editListMediaTimes = editListMediaTimes;
  }

  /**
   * Returns the {@link TrackEncryptionBox} for the given sample description index.
   *
   * @param sampleDescriptionIndex The given sample description index
   * @return The {@link TrackEncryptionBox} for the given sample description index. Maybe null if no
   *     such entry exists.
   */
  @Nullable
  public TrackEncryptionBox getSampleDescriptionEncryptionBox(int sampleDescriptionIndex) {
    return sampleDescriptionEncryptionBoxes == null ? null
        : sampleDescriptionEncryptionBoxes[sampleDescriptionIndex];
  }

  public Track copyWithFormat(Format format) {
    return new Track(
        id,
        type,
        timescale,
        movieTimescale,
        durationUs,
        format,
        sampleTransformation,
        sampleDescriptionEncryptionBoxes,
        nalUnitLengthFieldLength,
        editListDurations,
        editListMediaTimes);
  }
}
