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
package com.google.android.exoplayer2.source.chunk;

import java.util.NoSuchElementException;

/**
 * Base class for {@link MediaChunkIterator}s. Handles {@link #next()} and {@link #isEnded()}, and
 * provides a bounds check for child classes.
 */
public abstract class BaseMediaChunkIterator implements MediaChunkIterator {

  private final long fromIndex;
  private final long toIndex;

  private long currentIndex;

  /**
   * Creates base iterator.
   *
   * @param fromIndex The first available index.
   * @param toIndex The last available index.
   */
  @SuppressWarnings("method.invocation.invalid")
  public BaseMediaChunkIterator(long fromIndex, long toIndex) {
    this.fromIndex = fromIndex;
    this.toIndex = toIndex;
    reset();
  }

  @Override
  public boolean isEnded() {
    return currentIndex > toIndex;
  }

  @Override
  public boolean next() {
    currentIndex++;
    return !isEnded();
  }

  @Override
  public void reset() {
    currentIndex = fromIndex - 1;
  }

  /**
   * Verifies that the iterator points to a valid element.
   *
   * @throws NoSuchElementException If the iterator does not point to a valid element.
   */
  protected final void checkInBounds() {
    if (currentIndex < fromIndex || currentIndex > toIndex) {
      throw new NoSuchElementException();
    }
  }

  /** Returns the current index this iterator is pointing to. */
  protected final long getCurrentIndex() {
    return currentIndex;
  }
}
