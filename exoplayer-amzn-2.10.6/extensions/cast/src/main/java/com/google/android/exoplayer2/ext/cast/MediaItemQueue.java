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
package com.google.android.exoplayer2.ext.cast;

/** Represents a sequence of {@link MediaItem MediaItems}. */
public interface MediaItemQueue {

  /**
   * Returns the item at the given index.
   *
   * @param index The index of the item to retrieve.
   * @return The item at the given index.
   * @throws IndexOutOfBoundsException If {@code index < 0 || index >= getSize()}.
   */
  MediaItem get(int index);

  /** Returns the number of items in this queue. */
  int getSize();

  /**
   * Appends the given sequence of items to the queue.
   *
   * @param items The sequence of items to append.
   */
  void add(MediaItem... items);

  /**
   * Adds the given sequence of items to the queue at the given position, so that the first of
   * {@code items} is placed at the given index.
   *
   * @param index The index at which {@code items} will be inserted.
   * @param items The sequence of items to append.
   * @throws IndexOutOfBoundsException If {@code index < 0 || index > getSize()}.
   */
  void add(int index, MediaItem... items);

  /**
   * Moves an existing item within the playlist.
   *
   * <p>Calling this method is equivalent to removing the item at position {@code indexFrom} and
   * immediately inserting it at position {@code indexTo}. If the moved item is being played at the
   * moment of the invocation, playback will stick with the moved item.
   *
   * @param indexFrom The index of the item to move.
   * @param indexTo The index at which the item will be placed after this operation.
   * @throws IndexOutOfBoundsException If for either index, {@code index < 0 || index >= getSize()}.
   */
  void move(int indexFrom, int indexTo);

  /**
   * Removes an item from the queue.
   *
   * @param index The index of the item to remove from the queue.
   * @throws IndexOutOfBoundsException If {@code index < 0 || index >= getSize()}.
   */
  void remove(int index);

  /**
   * Removes a range of items from the queue.
   *
   * <p>Does nothing if an empty range ({@code from == exclusiveTo}) is passed.
   *
   * @param from The inclusive index at which the range to remove starts.
   * @param exclusiveTo The exclusive index at which the range to remove ends.
   * @throws IndexOutOfBoundsException If {@code from < 0 || exclusiveTo > getSize() || from >
   *     exclusiveTo}.
   */
  void removeRange(int from, int exclusiveTo);

  /** Removes all items in the queue. */
  void clear();
}
