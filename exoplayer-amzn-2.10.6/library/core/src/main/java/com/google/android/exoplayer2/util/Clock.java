/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

/**
 * An interface through which system clocks can be read and {@link HandlerWrapper}s created. The
 * {@link #DEFAULT} implementation must be used for all non-test cases.
 */
public interface Clock {

  /**
   * Default {@link Clock} to use for all non-test cases.
   */
  Clock DEFAULT = new SystemClock();

  /** @see android.os.SystemClock#elapsedRealtime() */
  long elapsedRealtime();

  /** @see android.os.SystemClock#uptimeMillis() */
  long uptimeMillis();

  /** @see android.os.SystemClock#sleep(long) */
  void sleep(long sleepTimeMs);

  /**
   * Creates a {@link HandlerWrapper} using a specified looper and a specified callback for handling
   * messages.
   *
   * @see Handler#Handler(Looper, Handler.Callback)
   */
  HandlerWrapper createHandler(Looper looper, @Nullable Handler.Callback callback);
}
