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

import com.google.android.exoplayer2.upstream.Loader.Loadable;
import java.io.IOException;

/**
 * Conditionally throws errors affecting a {@link Loader}.
 */
public interface LoaderErrorThrower {

  /**
   * Throws a fatal error, or a non-fatal error if loading is currently backed off and the current
   * {@link Loadable} has incurred a number of errors greater than the {@link Loader}s default
   * minimum number of retries. Else does nothing.
   *
   * @throws IOException The error.
   */
  void maybeThrowError() throws IOException;

  /**
   * Throws a fatal error, or a non-fatal error if loading is currently backed off and the current
   * {@link Loadable} has incurred a number of errors greater than the specified minimum number
   * of retries. Else does nothing.
   *
   * @param minRetryCount A minimum retry count that must be exceeded for a non-fatal error to be
   *     thrown. Should be non-negative.
   * @throws IOException The error.
   */
  void maybeThrowError(int minRetryCount) throws IOException;

  /**
   * A {@link LoaderErrorThrower} that never throws.
   */
  final class Dummy implements LoaderErrorThrower {

    @Override
    public void maybeThrowError() throws IOException {
      // Do nothing.
    }

    @Override
    public void maybeThrowError(int minRetryCount) throws IOException {
      // Do nothing.
    }

  }

}
