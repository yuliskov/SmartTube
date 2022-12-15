/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.video;

import com.google.android.exoplayer2.Format;

/** A listener for metadata corresponding to video frame being rendered. */
public interface VideoFrameMetadataListener {
  /**
   * Called when the video frame about to be rendered. This method is called on the playback thread.
   *
   * @param presentationTimeUs The presentation time of the output buffer, in microseconds.
   * @param releaseTimeNs The wallclock time at which the frame should be displayed, in nanoseconds.
   *     If the platform API version of the device is less than 21, then this is the best effort.
   * @param format The format associated with the frame.
   */
  void onVideoFrameAboutToBeRendered(long presentationTimeUs, long releaseTimeNs, Format format);
}
