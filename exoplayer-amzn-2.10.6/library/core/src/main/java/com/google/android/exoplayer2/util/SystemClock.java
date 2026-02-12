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
import android.os.Handler.Callback;
import android.os.Looper;
import androidx.annotation.Nullable;

/**
 * The standard implementation of {@link Clock}.
 */
/* package */ final class SystemClock implements Clock {

  @Override
  public long elapsedRealtime() {
    return android.os.SystemClock.elapsedRealtime();
  }

  @Override
  public long uptimeMillis() {
    return android.os.SystemClock.uptimeMillis();
  }

  @Override
  public void sleep(long sleepTimeMs) {
    android.os.SystemClock.sleep(sleepTimeMs);
  }

  @Override
  public HandlerWrapper createHandler(Looper looper, @Nullable Callback callback) {
    return new SystemHandlerWrapper(new Handler(looper, callback));
  }
}
