/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.util;

import androidx.annotation.Nullable;
import java.util.Arrays;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** A utility class to keep a queue of values with timestamps. This class is thread safe. */
public final class TimedValueQueue<V> {
  private static final int INITIAL_BUFFER_SIZE = 10;

  // Looping buffer for timestamps and values
  private long[] timestamps;
  private @NullableType V[] values;
  private int first;
  private int size;

  public TimedValueQueue() {
    this(INITIAL_BUFFER_SIZE);
  }

  /** Creates a TimedValueBuffer with the given initial buffer size. */
  public TimedValueQueue(int initialBufferSize) {
    timestamps = new long[initialBufferSize];
    values = newArray(initialBufferSize);
  }

  /**
   * Associates the specified value with the specified timestamp. All new values should have a
   * greater timestamp than the previously added values. Otherwise all values are removed before
   * adding the new one.
   */
  public synchronized void add(long timestamp, V value) {
    clearBufferOnTimeDiscontinuity(timestamp);
    doubleCapacityIfFull();
    addUnchecked(timestamp, value);
  }

  /** Removes all of the values. */
  public synchronized void clear() {
    first = 0;
    size = 0;
    Arrays.fill(values, null);
  }

  /** Returns number of the values buffered. */
  public synchronized int size() {
    return size;
  }

  /**
   * Returns the value with the greatest timestamp which is less than or equal to the given
   * timestamp. Removes all older values and the returned one from the buffer.
   *
   * @param timestamp The timestamp value.
   * @return The value with the greatest timestamp which is less than or equal to the given
   *     timestamp or null if there is no such value.
   * @see #poll(long)
   */
  public synchronized @Nullable V pollFloor(long timestamp) {
    return poll(timestamp, /* onlyOlder= */ true);
  }

  /**
   * Returns the value with the closest timestamp to the given timestamp. Removes all older values
   * including the returned one from the buffer.
   *
   * @param timestamp The timestamp value.
   * @return The value with the closest timestamp or null if the buffer is empty.
   * @see #pollFloor(long)
   */
  public synchronized @Nullable V poll(long timestamp) {
    return poll(timestamp, /* onlyOlder= */ false);
  }

  /**
   * Returns the value with the closest timestamp to the given timestamp. Removes all older values
   * including the returned one from the buffer.
   *
   * @param timestamp The timestamp value.
   * @param onlyOlder Whether this method can return a new value in case its timestamp value is
   *     closest to {@code timestamp}.
   * @return The value with the closest timestamp or null if the buffer is empty or there is no
   *     older value and {@code onlyOlder} is true.
   */
  private @Nullable V poll(long timestamp, boolean onlyOlder) {
    V value = null;
    long previousTimeDiff = Long.MAX_VALUE;
    while (size > 0) {
      long timeDiff = timestamp - timestamps[first];
      if (timeDiff < 0 && (onlyOlder || -timeDiff >= previousTimeDiff)) {
        break;
      }
      previousTimeDiff = timeDiff;
      value = values[first];
      values[first] = null;
      first = (first + 1) % values.length;
      size--;
    }
    return value;
  }

  private void clearBufferOnTimeDiscontinuity(long timestamp) {
    if (size > 0) {
      int last = (first + size - 1) % values.length;
      if (timestamp <= timestamps[last]) {
        clear();
      }
    }
  }

  private void doubleCapacityIfFull() {
    int capacity = values.length;
    if (size < capacity) {
      return;
    }
    int newCapacity = capacity * 2;
    long[] newTimestamps = new long[newCapacity];
    V[] newValues = newArray(newCapacity);
    // Reset the loop starting index to 0 while coping to the new buffer.
    // First copy the values from 'first' index to the end of original array.
    int length = capacity - first;
    System.arraycopy(timestamps, first, newTimestamps, 0, length);
    System.arraycopy(values, first, newValues, 0, length);
    // Then the values from index 0 to 'first' index.
    if (first > 0) {
      System.arraycopy(timestamps, 0, newTimestamps, length, first);
      System.arraycopy(values, 0, newValues, length, first);
    }
    timestamps = newTimestamps;
    values = newValues;
    first = 0;
  }

  private void addUnchecked(long timestamp, V value) {
    int next = (first + size) % values.length;
    timestamps[next] = timestamp;
    values[next] = value;
    size++;
  }

  @SuppressWarnings("unchecked")
  private static <V> V[] newArray(int length) {
    return (V[]) new Object[length];
  }
}
