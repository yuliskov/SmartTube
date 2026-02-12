/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Assertions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Collection of {@link FakeData} to be served by a {@link FakeDataSource}.
 *
 * <p>Multiple fake data can be defined by {@link FakeDataSet#setData(Uri, byte[])} and {@link
 * FakeDataSet#newData(Uri)} methods. It's also possible to define a default data by {@link
 * FakeDataSet#newDefaultData()}.
 *
 * <p>{@link FakeDataSet#newData(Uri)} and {@link FakeDataSet#newDefaultData()} return a {@link
 * FakeData} instance which can be used to define specific results during
 * {@link FakeDataSource#read(byte[], int, int)} calls.
 *
 * <p>The data that will be read from the source can be constructed by calling {@link
 * FakeData#appendReadData(byte[])} Calls to {@link FakeDataSource#read(byte[], int, int)} will not
 * span the boundaries between arrays passed to successive calls, and hence the boundaries control
 * the positions at which read requests to the source may only be partially satisfied.
 *
 * <p>Errors can be inserted by calling {@link FakeData#appendReadError(IOException)}. An inserted
 * error will be thrown from the first call to {@link FakeDataSource#read(byte[], int, int)} that
 * attempts to read from the corresponding position, and from all subsequent calls to
 * {@link FakeDataSource#read(byte[], int, int)} until the source is closed. If the source is closed
 * and re-opened having encountered an error, that error will not be thrown again.
 *
 * <p>Actions are inserted by calling {@link FakeData#appendReadAction(Runnable)}. An actions is
 * triggered when the reading reaches action's position. This can be used to make sure the code is
 * in a certain state while testing.
 *
 * <p>Example usage:
 *
 * <pre>
 *   // Create a FakeDataSource then add default data and two FakeData
 *   // "test_file" throws an IOException when tried to be read until closed and reopened.
 *   FakeDataSource fakeDataSource = new FakeDataSource();
 *   fakeDataSource.getDataSet()
 *       .newDefaultData()
 *         .appendReadData(defaultData)
 *         .endData()
 *       .setData("http://1", data1)
 *       .newData("test_file")
 *         .appendReadError(new IOException())
 *         .appendReadData(data2)
 *         .endData();
 * </pre>
 */
public class FakeDataSet {

  /** Container of fake data to be served by a {@link FakeDataSource}. */
  public static final class FakeData {

    /**
     * A segment of {@link FakeData}. May consist of an action or exception instead of actual data.
     */
    public static final class Segment {

      public @Nullable final IOException exception;
      public @Nullable final byte[] data;
      public final int length;
      public final long byteOffset;
      public @Nullable final Runnable action;

      public boolean exceptionThrown;
      public boolean exceptionCleared;
      public int bytesRead;

      private Segment(byte[] data, Segment previousSegment) {
        this(data, data.length, null, null, previousSegment);
      }

      private Segment(int length, Segment previousSegment) {
        this(null, length, null, null, previousSegment);
      }

      private Segment(IOException exception, Segment previousSegment) {
        this(null, 0, exception, null, previousSegment);
      }

      private Segment(Runnable action, Segment previousSegment) {
        this(null, 0, null, action, previousSegment);
      }

      private Segment(@Nullable byte[] data, int length, @Nullable IOException exception,
          @Nullable Runnable action, Segment previousSegment) {
        this.exception = exception;
        this.action = action;
        this.data = data;
        this.length = length;
        this.byteOffset = previousSegment == null ? 0
            : previousSegment.byteOffset + previousSegment.length;
      }

      public boolean isErrorSegment() {
        return exception != null;
      }

      public boolean isActionSegment() {
        return action != null;
      }

    }

    /** Uri of the data or null if this is the default FakeData. */
    public final Uri uri;
    private final ArrayList<Segment> segments;
    private final FakeDataSet dataSet;
    private boolean simulateUnknownLength;

    private FakeData(FakeDataSet dataSet, Uri uri) {
      this.uri = uri;
      this.segments = new ArrayList<>();
      this.dataSet = dataSet;
    }

    /** Returns the {@link FakeDataSet} this FakeData belongs to. */
    public FakeDataSet endData() {
      return dataSet;
    }

    /**
     * When set, {@link FakeDataSource#open(DataSpec)} will behave as though the source is unable to
     * determine the length of the underlying data. Hence the return value will always be equal to
     * the {@link DataSpec#length} of the argument, including the case where the length is equal to
     * {@link C#LENGTH_UNSET}.
     */
    public FakeData setSimulateUnknownLength(boolean simulateUnknownLength) {
      this.simulateUnknownLength = simulateUnknownLength;
      return this;
    }

    /**
     * Appends to the underlying data.
     */
    public FakeData appendReadData(byte[] data) {
      Assertions.checkState(data != null && data.length > 0);
      segments.add(new Segment(data, getLastSegment()));
      return this;
    }

    /**
     * Appends a data segment of the specified length. No actual data is available and the
     * {@link FakeDataSource} will perform no copy operations when this data is read.
     */
    public FakeData appendReadData(int length) {
      Assertions.checkState(length > 0);
      segments.add(new Segment(length, getLastSegment()));
      return this;
    }

    /**
     * Appends an error in the underlying data.
     */
    public FakeData appendReadError(IOException exception) {
      segments.add(new Segment(exception, getLastSegment()));
      return this;
    }

    /**
     * Appends an action.
     */
    public FakeData appendReadAction(Runnable action) {
      segments.add(new Segment(action, getLastSegment()));
      return this;
    }

    /** Returns the whole data added by {@link #appendReadData(byte[])}. */
    public byte[] getData() {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      for (Segment segment : segments) {
        if (segment.data != null) {
          try {
            outputStream.write(segment.data);
          } catch (IOException e) {
            throw new IllegalStateException(e);
          }
        }
      }
      return outputStream.toByteArray();
    }

    /** Returns the list of {@link Segment}s. */
    public List<Segment> getSegments() {
      return segments;
    }

    /** Retuns whether unknown length is simulated */
    public boolean isSimulatingUnknownLength() {
      return simulateUnknownLength;
    }

    private Segment getLastSegment() {
      int count = segments.size();
      return count > 0 ? segments.get(count - 1) : null;
    }

  }

  private final HashMap<Uri, FakeData> dataMap;
  private FakeData defaultData;

  public FakeDataSet() {
    dataMap = new HashMap<>();
  }

  /** Sets the default data, overwrites if there is one already. */
  public FakeData newDefaultData() {
    defaultData = new FakeData(this, null);
    return defaultData;
  }

  /** Sets random data with the given {@code length} for the given {@code uri}. */
  public FakeDataSet setRandomData(String uri, int length) {
    return setRandomData(Uri.parse(uri), length);
  }

  /** Sets random data with the given {@code length} for the given {@code uri}. */
  public FakeDataSet setRandomData(Uri uri, int length) {
    return setData(uri, TestUtil.buildTestData(length));
  }

  /** Sets the given {@code data} for the given {@code uri}. */
  public FakeDataSet setData(String uri, byte[] data) {
    return setData(Uri.parse(uri), data);
  }

  /** Sets the given {@code data} for the given {@code uri}. */
  public FakeDataSet setData(Uri uri, byte[] data) {
    return newData(uri).appendReadData(data).endData();
  }

  /** Returns a new {@link FakeData} with the given {@code uri}. */
  public FakeData newData(String uri) {
    return newData(Uri.parse(uri));
  }

  /** Returns a new {@link FakeData} with the given {@code uri}. */
  public FakeData newData(Uri uri) {
    FakeData data = new FakeData(this, uri);
    dataMap.put(uri, data);
    return data;
  }

  /** Returns the data for the given {@code uri}, or {@code defaultData} if no data is set. */
  public FakeData getData(String uri) {
    return getData(Uri.parse(uri));
  }

  /** Returns the data for the given {@code uri}, or {@code defaultData} if no data is set. */
  public FakeData getData(Uri uri) {
    FakeData data = dataMap.get(uri);
    return data != null ? data : defaultData;
  }

  /** Returns a list of all data including {@code defaultData}. */
  public ArrayList<FakeData> getAllData() {
    ArrayList<FakeData> fakeDatas = new ArrayList<>(dataMap.values());
    if (defaultData != null) {
      fakeDatas.add(defaultData);
    }
    return fakeDatas;
  }

}
