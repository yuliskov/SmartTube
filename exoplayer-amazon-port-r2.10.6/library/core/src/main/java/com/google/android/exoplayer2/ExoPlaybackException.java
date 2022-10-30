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

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Thrown when a non-recoverable playback failure occurs.
 */
public final class ExoPlaybackException extends Exception {

  /**
   * The type of source that produced the error. One of {@link #TYPE_SOURCE}, {@link #TYPE_RENDERER}
   * {@link #TYPE_UNEXPECTED}, {@link #TYPE_REMOTE} or {@link #TYPE_OUT_OF_MEMORY}. Note that new
   * types may be added in the future and error handling should handle unknown type values.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_SOURCE, TYPE_RENDERER, TYPE_UNEXPECTED, TYPE_REMOTE, TYPE_OUT_OF_MEMORY})
  public @interface Type {}
  /**
   * The error occurred loading data from a {@link MediaSource}.
   * <p>
   * Call {@link #getSourceException()} to retrieve the underlying cause.
   */
  public static final int TYPE_SOURCE = 0;
  /**
   * The error occurred in a {@link Renderer}.
   * <p>
   * Call {@link #getRendererException()} to retrieve the underlying cause.
   */
  public static final int TYPE_RENDERER = 1;
  /**
   * The error was an unexpected {@link RuntimeException}.
   * <p>
   * Call {@link #getUnexpectedException()} to retrieve the underlying cause.
   */
  public static final int TYPE_UNEXPECTED = 2;
  /**
   * The error occurred in a remote component.
   *
   * <p>Call {@link #getMessage()} to retrieve the message associated with the error.
   */
  public static final int TYPE_REMOTE = 3;
  /** The error was an {@link OutOfMemoryError}. */
  public static final int TYPE_OUT_OF_MEMORY = 4;

  /** The {@link Type} of the playback failure. */
  @Type public final int type;

  /**
   * If {@link #type} is {@link #TYPE_RENDERER}, this is the index of the renderer.
   */
  public final int rendererIndex;

  @Nullable private final Throwable cause;

  /**
   * Creates an instance of type {@link #TYPE_SOURCE}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static ExoPlaybackException createForSource(IOException cause) {
    return new ExoPlaybackException(TYPE_SOURCE, cause, /* rendererIndex= */ C.INDEX_UNSET);
  }

  /**
   * Creates an instance of type {@link #TYPE_RENDERER}.
   *
   * @param cause The cause of the failure.
   * @param rendererIndex The index of the renderer in which the failure occurred.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRenderer(Exception cause, int rendererIndex) {
    return new ExoPlaybackException(TYPE_RENDERER, cause, rendererIndex);
  }

  /**
   * Creates an instance of type {@link #TYPE_UNEXPECTED}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static ExoPlaybackException createForUnexpected(RuntimeException cause) {
    return new ExoPlaybackException(TYPE_UNEXPECTED, cause, /* rendererIndex= */ C.INDEX_UNSET);
  }

  /**
   * Creates an instance of type {@link #TYPE_REMOTE}.
   *
   * @param message The message associated with the error.
   * @return The created instance.
   */
  public static ExoPlaybackException createForRemote(String message) {
    return new ExoPlaybackException(TYPE_REMOTE, message);
  }

  /**
   * Creates an instance of type {@link #TYPE_OUT_OF_MEMORY}.
   *
   * @param cause The cause of the failure.
   * @return The created instance.
   */
  public static ExoPlaybackException createForOutOfMemoryError(OutOfMemoryError cause) {
    return new ExoPlaybackException(TYPE_OUT_OF_MEMORY, cause, /* rendererIndex= */ C.INDEX_UNSET);
  }

  private ExoPlaybackException(@Type int type, Throwable cause, int rendererIndex) {
    super(cause);
    this.type = type;
    this.cause = cause;
    this.rendererIndex = rendererIndex;
  }

  private ExoPlaybackException(@Type int type, String message) {
    super(message);
    this.type = type;
    rendererIndex = C.INDEX_UNSET;
    cause = null;
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_SOURCE}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_SOURCE}.
   */
  public IOException getSourceException() {
    Assertions.checkState(type == TYPE_SOURCE);
    return (IOException) Assertions.checkNotNull(cause);
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_RENDERER}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_RENDERER}.
   */
  public Exception getRendererException() {
    Assertions.checkState(type == TYPE_RENDERER);
    return (Exception) Assertions.checkNotNull(cause);
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_UNEXPECTED}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_UNEXPECTED}.
   */
  public RuntimeException getUnexpectedException() {
    Assertions.checkState(type == TYPE_UNEXPECTED);
    return (RuntimeException) Assertions.checkNotNull(cause);
  }

  /**
   * Retrieves the underlying error when {@link #type} is {@link #TYPE_OUT_OF_MEMORY}.
   *
   * @throws IllegalStateException If {@link #type} is not {@link #TYPE_OUT_OF_MEMORY}.
   */
  public OutOfMemoryError getOutOfMemoryError() {
    Assertions.checkState(type == TYPE_OUT_OF_MEMORY);
    return (OutOfMemoryError) Assertions.checkNotNull(cause);
  }
}
