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
package com.google.android.exoplayer2.util;

import java.util.Arrays;

/**
 * An append-only, auto-growing {@code long[]}.
 */
public final class LongArray {

  private static final int DEFAULT_INITIAL_CAPACITY = 32;

  private int size;
  private long[] values;

  public LongArray() {
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * @param initialCapacity The initial capacity of the array.
   */
  public LongArray(int initialCapacity) {
    values = new long[initialCapacity];
  }

  /**
   * Appends a value.
   *
   * @param value The value to append.
   */
  public void add(long value) {
    if (size == values.length) {
      values = Arrays.copyOf(values, size * 2);
    }
    values[size++] = value;
  }

  /**
   * Returns the value at a specified index.
   *
   * @param index The index.
   * @return The corresponding value.
   * @throws IndexOutOfBoundsException If the index is less than zero, or greater than or equal to
   *     {@link #size()}.
   */
  public long get(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Invalid index " + index + ", size is " + size);
    }
    return values[index];
  }

  /**
   * Returns the current size of the array.
   */
  public int size() {
    return size;
  }

  /**
   * Copies the current values into a newly allocated primitive array.
   *
   * @return The primitive array containing the copied values.
   */
  public long[] toArray() {
    return Arrays.copyOf(values, size);
  }

}
