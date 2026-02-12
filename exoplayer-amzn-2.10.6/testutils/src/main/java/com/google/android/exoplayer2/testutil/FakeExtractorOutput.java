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
package com.google.android.exoplayer2.testutil;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.util.SparseArray;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.SeekMap;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A fake {@link ExtractorOutput}.
 */
public final class FakeExtractorOutput implements ExtractorOutput, Dumper.Dumpable {

  /**
   * If true, makes {@link #assertOutput(Context, String)} method write dump result to {@code
   * /sdcard/Android/data/apk_package/ + dumpfile} file instead of comparing it with an existing
   * file.
   */
  private static final boolean WRITE_DUMP = false;

  public final SparseArray<FakeTrackOutput> trackOutputs;

  public int numberOfTracks;
  public boolean tracksEnded;
  public SeekMap seekMap;

  public FakeExtractorOutput() {
    trackOutputs = new SparseArray<>();
  }

  @Override
  public FakeTrackOutput track(int id, int type) {
    FakeTrackOutput output = trackOutputs.get(id);
    if (output == null) {
      assertThat(tracksEnded).isFalse();
      numberOfTracks++;
      output = new FakeTrackOutput();
      trackOutputs.put(id, output);
    }
    return output;
  }

  @Override
  public void endTracks() {
    tracksEnded = true;
  }

  @Override
  public void seekMap(SeekMap seekMap) {
    this.seekMap = seekMap;
  }

  public void clearTrackOutputs() {
    for (int i = 0; i < numberOfTracks; i++) {
      trackOutputs.valueAt(i).clear();
    }
  }

  public void assertEquals(FakeExtractorOutput expected) {
    assertThat(numberOfTracks).isEqualTo(expected.numberOfTracks);
    assertThat(tracksEnded).isEqualTo(expected.tracksEnded);
    if (expected.seekMap == null) {
      assertThat(seekMap).isNull();
    } else {
      // TODO: Bulk up this check if possible.
      assertThat(seekMap).isNotNull();
      assertThat(seekMap.getClass()).isEqualTo(expected.seekMap.getClass());
      assertThat(seekMap.isSeekable()).isEqualTo(expected.seekMap.isSeekable());
      assertThat(seekMap.getSeekPoints(0)).isEqualTo(expected.seekMap.getSeekPoints(0));
    }
    for (int i = 0; i < numberOfTracks; i++) {
      assertThat(trackOutputs.keyAt(i)).isEqualTo(expected.trackOutputs.keyAt(i));
      trackOutputs.valueAt(i).assertEquals(expected.trackOutputs.valueAt(i));
    }
  }

  /**
   * Asserts that dump of this {@link FakeExtractorOutput} is equal to expected dump which is read
   * from {@code dumpFile}.
   *
   * <p>If assertion fails because of an intended change in the output or a new dump file needs to
   * be created, set {@link #WRITE_DUMP} flag to true and run the test again. Instead of assertion,
   * actual dump will be written to {@code dumpFile}. This new dump file needs to be copied to the
   * project, {@code library/src/androidTest/assets} folder manually.
   */
  public void assertOutput(Context context, String dumpFile) throws IOException {
    String actual = new Dumper().add(this).toString();

    if (WRITE_DUMP) {
      File directory = context.getExternalFilesDir(null);
      File file = new File(directory, dumpFile);
      file.getParentFile().mkdirs();
      PrintWriter out = new PrintWriter(file);
      out.print(actual);
      out.close();
    } else {
      String expected = TestUtil.getString(context, dumpFile);
      assertWithMessage(dumpFile).that(actual).isEqualTo(expected);
    }
  }

  @Override
  public void dump(Dumper dumper) {
    if (seekMap != null) {
      dumper
          .startBlock("seekMap")
          .add("isSeekable", seekMap.isSeekable())
          .addTime("duration", seekMap.getDurationUs())
          .add("getPosition(0)", seekMap.getSeekPoints(0))
          .endBlock();
    }
    dumper.add("numberOfTracks", numberOfTracks);
    for (int i = 0; i < numberOfTracks; i++) {
      dumper.startBlock("track " + trackOutputs.keyAt(i))
          .add(trackOutputs.valueAt(i))
          .endBlock();
    }
    dumper.add("tracksEnded", tracksEnded);
  }

}
