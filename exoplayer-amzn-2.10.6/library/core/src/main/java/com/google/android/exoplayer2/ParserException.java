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
package com.google.android.exoplayer2;

import java.io.IOException;

/**
 * Thrown when an error occurs parsing media data and metadata.
 */
public class ParserException extends IOException {

  public ParserException() {
    super();
  }

  /**
   * @param message The detail message for the exception.
   */
  public ParserException(String message) {
    super(message);
  }

  /**
   * @param cause The cause for the exception.
   */
  public ParserException(Throwable cause) {
    super(cause);
  }

  /**
   * @param message The detail message for the exception.
   * @param cause The cause for the exception.
   */
  public ParserException(String message, Throwable cause) {
    super(message, cause);
  }

}
