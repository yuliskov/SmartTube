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

/**
 * An interruptible condition variable whose {@link #open()} and {@link #close()} methods return
 * whether they resulted in a change of state.
 */
public final class ConditionVariable {

  private boolean isOpen;

  /**
   * Opens the condition and releases all threads that are blocked.
   *
   * @return True if the condition variable was opened. False if it was already open.
   */
  public synchronized boolean open() {
    if (isOpen) {
      return false;
    }
    isOpen = true;
    notifyAll();
    return true;
  }

  /**
   * Closes the condition.
   *
   * @return True if the condition variable was closed. False if it was already closed.
   */
  public synchronized boolean close() {
    boolean wasOpen = isOpen;
    isOpen = false;
    return wasOpen;
  }

  /**
   * Blocks until the condition is opened.
   *
   * @throws InterruptedException If the thread is interrupted.
   */
  public synchronized void block() throws InterruptedException {
    while (!isOpen) {
      wait();
    }
  }

  /**
   * Blocks until the condition is opened or until {@code timeout} milliseconds have passed.
   *
   * @param timeout The maximum time to wait in milliseconds.
   * @return True if the condition was opened, false if the call returns because of the timeout.
   * @throws InterruptedException If the thread is interrupted.
   */
  public synchronized boolean block(long timeout) throws InterruptedException {
    long now = android.os.SystemClock.elapsedRealtime();
    long end = now + timeout;
    while (!isOpen && now < end) {
      wait(end - now);
      now = android.os.SystemClock.elapsedRealtime();
    }
    return isOpen;
  }

}
