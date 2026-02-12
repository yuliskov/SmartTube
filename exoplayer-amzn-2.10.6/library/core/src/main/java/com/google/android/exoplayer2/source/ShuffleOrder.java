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
package com.google.android.exoplayer2.source;

import com.google.android.exoplayer2.C;
import java.util.Arrays;
import java.util.Random;

/**
 * Shuffled order of indices.
 *
 * <p>The shuffle order must be immutable to ensure thread safety.
 */
public interface ShuffleOrder {

  /**
   * The default {@link ShuffleOrder} implementation for random shuffle order.
   */
  class DefaultShuffleOrder implements ShuffleOrder {

    private final Random random;
    private final int[] shuffled;
    private final int[] indexInShuffled;

    /**
     * Creates an instance with a specified length.
     *
     * @param length The length of the shuffle order.
     */
    public DefaultShuffleOrder(int length) {
      this(length, new Random());
    }

    /**
     * Creates an instance with a specified length and the specified random seed. Shuffle orders of
     * the same length initialized with the same random seed are guaranteed to be equal.
     *
     * @param length The length of the shuffle order.
     * @param randomSeed A random seed.
     */
    public DefaultShuffleOrder(int length, long randomSeed) {
      this(length, new Random(randomSeed));
    }

    /**
     * Creates an instance with a specified shuffle order and the specified random seed. The random
     * seed is used for {@link #cloneAndInsert(int, int)} invocations.
     *
     * @param shuffledIndices The shuffled indices to use as order.
     * @param randomSeed A random seed.
     */
    public DefaultShuffleOrder(int[] shuffledIndices, long randomSeed) {
      this(Arrays.copyOf(shuffledIndices, shuffledIndices.length), new Random(randomSeed));
    }

    private DefaultShuffleOrder(int length, Random random) {
      this(createShuffledList(length, random), random);
    }

    private DefaultShuffleOrder(int[] shuffled, Random random) {
      this.shuffled = shuffled;
      this.random = random;
      this.indexInShuffled = new int[shuffled.length];
      for (int i = 0; i < shuffled.length; i++) {
        indexInShuffled[shuffled[i]] = i;
      }
    }

    @Override
    public int getLength() {
      return shuffled.length;
    }

    @Override
    public int getNextIndex(int index) {
      int shuffledIndex = indexInShuffled[index];
      return ++shuffledIndex < shuffled.length ? shuffled[shuffledIndex] : C.INDEX_UNSET;
    }

    @Override
    public int getPreviousIndex(int index) {
      int shuffledIndex = indexInShuffled[index];
      return --shuffledIndex >= 0 ? shuffled[shuffledIndex] : C.INDEX_UNSET;
    }

    @Override
    public int getLastIndex() {
      return shuffled.length > 0 ? shuffled[shuffled.length - 1] : C.INDEX_UNSET;
    }

    @Override
    public int getFirstIndex() {
      return shuffled.length > 0 ? shuffled[0] : C.INDEX_UNSET;
    }

    @Override
    public ShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount) {
      int[] insertionPoints = new int[insertionCount];
      int[] insertionValues = new int[insertionCount];
      for (int i = 0; i < insertionCount; i++) {
        insertionPoints[i] = random.nextInt(shuffled.length + 1);
        int swapIndex = random.nextInt(i + 1);
        insertionValues[i] = insertionValues[swapIndex];
        insertionValues[swapIndex] = i + insertionIndex;
      }
      Arrays.sort(insertionPoints);
      int[] newShuffled = new int[shuffled.length + insertionCount];
      int indexInOldShuffled = 0;
      int indexInInsertionList = 0;
      for (int i = 0; i < shuffled.length + insertionCount; i++) {
        if (indexInInsertionList < insertionCount
            && indexInOldShuffled == insertionPoints[indexInInsertionList]) {
          newShuffled[i] = insertionValues[indexInInsertionList++];
        } else {
          newShuffled[i] = shuffled[indexInOldShuffled++];
          if (newShuffled[i] >= insertionIndex) {
            newShuffled[i] += insertionCount;
          }
        }
      }
      return new DefaultShuffleOrder(newShuffled, new Random(random.nextLong()));
    }

    @Override
    public ShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive) {
      int numberOfElementsToRemove = indexToExclusive - indexFrom;
      int[] newShuffled = new int[shuffled.length - numberOfElementsToRemove];
      int foundElementsCount = 0;
      for (int i = 0; i < shuffled.length; i++) {
        if (shuffled[i] >= indexFrom && shuffled[i] < indexToExclusive) {
          foundElementsCount++;
        } else {
          newShuffled[i - foundElementsCount] =
              shuffled[i] >= indexFrom ? shuffled[i] - numberOfElementsToRemove : shuffled[i];
        }
      }
      return new DefaultShuffleOrder(newShuffled, new Random(random.nextLong()));
    }

    @Override
    public ShuffleOrder cloneAndClear() {
      return new DefaultShuffleOrder(/* length= */ 0, new Random(random.nextLong()));
    }

    private static int[] createShuffledList(int length, Random random) {
      int[] shuffled = new int[length];
      for (int i = 0; i < length; i++) {
        int swapIndex = random.nextInt(i + 1);
        shuffled[i] = shuffled[swapIndex];
        shuffled[swapIndex] = i;
      }
      return shuffled;
    }

  }

  /**
   * A {@link ShuffleOrder} implementation which does not shuffle.
   */
  final class UnshuffledShuffleOrder implements ShuffleOrder {

    private final int length;

    /**
     * Creates an instance with a specified length.
     *
     * @param length The length of the shuffle order.
     */
    public UnshuffledShuffleOrder(int length) {
      this.length = length;
    }

    @Override
    public int getLength() {
      return length;
    }

    @Override
    public int getNextIndex(int index) {
      return ++index < length ? index : C.INDEX_UNSET;
    }

    @Override
    public int getPreviousIndex(int index) {
      return --index >= 0 ? index : C.INDEX_UNSET;
    }

    @Override
    public int getLastIndex() {
      return length > 0 ? length - 1 : C.INDEX_UNSET;
    }

    @Override
    public int getFirstIndex() {
      return length > 0 ? 0 : C.INDEX_UNSET;
    }

    @Override
    public ShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount) {
      return new UnshuffledShuffleOrder(length + insertionCount);
    }

    @Override
    public ShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive) {
      return new UnshuffledShuffleOrder(length - indexToExclusive + indexFrom);
    }

    @Override
    public ShuffleOrder cloneAndClear() {
      return new UnshuffledShuffleOrder(/* length= */ 0);
    }
  }

  /**
   * Returns length of shuffle order.
   */
  int getLength();

  /**
   * Returns the next index in the shuffle order.
   *
   * @param index An index.
   * @return The index after {@code index}, or {@link C#INDEX_UNSET} if {@code index} is the last
   *     element.
   */
  int getNextIndex(int index);

  /**
   * Returns the previous index in the shuffle order.
   *
   * @param index An index.
   * @return The index before {@code index}, or {@link C#INDEX_UNSET} if {@code index} is the first
   *     element.
   */
  int getPreviousIndex(int index);

  /**
   * Returns the last index in the shuffle order, or {@link C#INDEX_UNSET} if the shuffle order is
   * empty.
   */
  int getLastIndex();

  /**
   * Returns the first index in the shuffle order, or {@link C#INDEX_UNSET} if the shuffle order is
   * empty.
   */
  int getFirstIndex();

  /**
   * Returns a copy of the shuffle order with newly inserted elements.
   *
   * @param insertionIndex The index in the unshuffled order at which elements are inserted.
   * @param insertionCount The number of elements inserted at {@code insertionIndex}.
   * @return A copy of this {@link ShuffleOrder} with newly inserted elements.
   */
  ShuffleOrder cloneAndInsert(int insertionIndex, int insertionCount);

  /**
   * Returns a copy of the shuffle order with a range of elements removed.
   *
   * @param indexFrom The starting index in the unshuffled order of the range to remove.
   * @param indexToExclusive The smallest index (must be greater or equal to {@code indexFrom}) that
   *     will not be removed.
   * @return A copy of this {@link ShuffleOrder} without the elements in the removed range.
   */
  ShuffleOrder cloneAndRemove(int indexFrom, int indexToExclusive);

  /** Returns a copy of the shuffle order with all elements removed. */
  ShuffleOrder cloneAndClear();
}
