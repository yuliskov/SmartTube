/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.mediacodec;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

/**
 * Unit test for the transient-MediaCodec-error classification used by the in-place codec recovery
 * (SmartTube fix). {@link MediaCodecRenderer#isMediaCodecException} is the gate that decides
 * whether a render-loop {@link IllegalStateException} is treated as a recoverable decoder error, so
 * these tests pin the two real-world crash signatures we observed (a native {@code MediaCodec} frame
 * at the top of the stack) and guard against unrelated exceptions being swallowed.
 *
 * <p>Deliberately a plain JUnit test (no Robolectric): it exercises only stack-trace inspection, and
 * the 2019-era Robolectric bundled with this ExoPlayer fork cannot run under the JDK 17 required by
 * the app's Android Gradle Plugin. Under a plain JVM {@code Util.SDK_INT} reads as 0, so this
 * exercises the stack-frame branch — which is exactly the path that fired on the real crashes (both
 * were plain {@link IllegalStateException}s, not {@code MediaCodec.CodecException}s).
 */
public class MediaCodecRendererTest {

  @Test
  public void isMediaCodecException_withNativeDequeueInputBufferFrame_returnsTrue() {
    // Observed crash: MediaCodec.native_dequeueInputBuffer on VP9 1080p60.
    IllegalStateException error =
        illegalStateExceptionWithTopFrame(
            "android.media.MediaCodec", "native_dequeueInputBuffer");

    assertThat(MediaCodecRenderer.isMediaCodecException(error)).isTrue();
  }

  @Test
  public void isMediaCodecException_withReleaseOutputBufferFrame_returnsTrue() {
    // Observed crash: MediaCodec.releaseOutputBuffer on VP9 1920x960.
    IllegalStateException error =
        illegalStateExceptionWithTopFrame("android.media.MediaCodec", "releaseOutputBuffer");

    assertThat(MediaCodecRenderer.isMediaCodecException(error)).isTrue();
  }

  @Test
  public void isMediaCodecException_withUnrelatedFrame_returnsFalse() {
    // A logic-bug ISE originating elsewhere must still propagate untouched.
    IllegalStateException error =
        illegalStateExceptionWithTopFrame(
            "com.google.android.exoplayer2.SomeOtherComponent", "doWork");

    assertThat(MediaCodecRenderer.isMediaCodecException(error)).isFalse();
  }

  @Test
  public void isMediaCodecException_withEmptyStackTrace_returnsFalse() {
    IllegalStateException error = new IllegalStateException();
    error.setStackTrace(new StackTraceElement[0]);

    assertThat(MediaCodecRenderer.isMediaCodecException(error)).isFalse();
  }

  private static IllegalStateException illegalStateExceptionWithTopFrame(
      String className, String methodName) {
    IllegalStateException error = new IllegalStateException();
    error.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement(className, methodName, /* fileName= */ null, /* lineNumber= */ -2),
          new StackTraceElement(
              "com.google.android.exoplayer2.mediacodec.MediaCodecRenderer",
              "feedInputBuffer",
              "MediaCodecRenderer.java",
              994)
        });
    return error;
  }
}
