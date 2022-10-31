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
package com.google.android.exoplayer2.upstream;

/**
 * A source of allocations.
 */
public interface Allocator {

  /**
   * Obtain an {@link Allocation}.
   * <p>
   * When the caller has finished with the {@link Allocation}, it should be returned by calling
   * {@link #release(Allocation)}.
   *
   * @return The {@link Allocation}.
   */
  Allocation allocate();

  /**
   * Releases an {@link Allocation} back to the allocator.
   *
   * @param allocation The {@link Allocation} being released.
   */
  void release(Allocation allocation);

  /**
   * Releases an array of {@link Allocation}s back to the allocator.
   *
   * @param allocations The array of {@link Allocation}s being released.
   */
  void release(Allocation[] allocations);

  /**
   * Hints to the allocator that it should make a best effort to release any excess
   * {@link Allocation}s.
   */
  void trim();

  /**
   * Returns the total number of bytes currently allocated.
   */
  int getTotalBytesAllocated();

  /**
   * Returns the length of each individual {@link Allocation}.
   */
  int getIndividualAllocationLength();

}
