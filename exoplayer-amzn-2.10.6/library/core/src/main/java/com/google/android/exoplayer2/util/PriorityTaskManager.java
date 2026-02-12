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

import java.io.IOException;
import java.util.Collections;
import java.util.PriorityQueue;

/**
 * Allows tasks with associated priorities to control how they proceed relative to one another.
 * <p>
 * A task should call {@link #add(int)} to register with the manager and {@link #remove(int)} to
 * unregister. A registered task will prevent tasks of lower priority from proceeding, and should
 * call {@link #proceed(int)}, {@link #proceedNonBlocking(int)} or {@link #proceedOrThrow(int)} each
 * time it wishes to check whether it is itself allowed to proceed.
 */
public final class PriorityTaskManager {

  /**
   * Thrown when task attempts to proceed when another registered task has a higher priority.
   */
  public static class PriorityTooLowException extends IOException {

    public PriorityTooLowException(int priority, int highestPriority) {
      super("Priority too low [priority=" + priority + ", highest=" + highestPriority + "]");
    }

  }

  private final Object lock = new Object();

  // Guarded by lock.
  private final PriorityQueue<Integer> queue;
  private int highestPriority;

  public PriorityTaskManager() {
    queue = new PriorityQueue<>(10, Collections.reverseOrder());
    highestPriority = Integer.MIN_VALUE;
  }

  /**
   * Register a new task. The task must call {@link #remove(int)} when done.
   *
   * @param priority The priority of the task. Larger values indicate higher priorities.
   */
  public void add(int priority) {
    synchronized (lock) {
      queue.add(priority);
      highestPriority = Math.max(highestPriority, priority);
    }
  }

  /**
   * Blocks until the task is allowed to proceed.
   *
   * @param priority The priority of the task.
   * @throws InterruptedException If the thread is interrupted.
   */
  public void proceed(int priority) throws InterruptedException {
    synchronized (lock) {
      while (highestPriority != priority) {
        lock.wait();
      }
    }
  }

  /**
   * A non-blocking variant of {@link #proceed(int)}.
   *
   * @param priority The priority of the task.
   * @return Whether the task is allowed to proceed.
   */
  public boolean proceedNonBlocking(int priority) {
    synchronized (lock) {
      return highestPriority == priority;
    }
  }

  /**
   * A throwing variant of {@link #proceed(int)}.
   *
   * @param priority The priority of the task.
   * @throws PriorityTooLowException If the task is not allowed to proceed.
   */
  public void proceedOrThrow(int priority) throws PriorityTooLowException {
    synchronized (lock) {
      if (highestPriority != priority) {
        throw new PriorityTooLowException(priority, highestPriority);
      }
    }
  }

  /**
   * Unregister a task.
   *
   * @param priority The priority of the task.
   */
  public void remove(int priority) {
    synchronized (lock) {
      queue.remove(priority);
      highestPriority = queue.isEmpty() ? Integer.MIN_VALUE : Util.castNonNull(queue.peek());
      lock.notifyAll();
    }
  }

}
