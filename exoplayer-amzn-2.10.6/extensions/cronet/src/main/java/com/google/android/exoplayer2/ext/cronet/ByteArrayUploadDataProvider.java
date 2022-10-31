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
package com.google.android.exoplayer2.ext.cronet;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.chromium.net.UploadDataProvider;
import org.chromium.net.UploadDataSink;

/**
 * A {@link UploadDataProvider} implementation that provides data from a {@code byte[]}.
 */
/* package */ final class ByteArrayUploadDataProvider extends UploadDataProvider {

  private final byte[] data;

  private int position;

  public ByteArrayUploadDataProvider(byte[] data) {
    this.data = data;
  }

  @Override
  public long getLength() {
    return data.length;
  }

  @Override
  public void read(UploadDataSink uploadDataSink, ByteBuffer byteBuffer) throws IOException {
    int readLength = Math.min(byteBuffer.remaining(), data.length - position);
    byteBuffer.put(data, position, readLength);
    position += readLength;
    uploadDataSink.onReadSucceeded(false);
  }

  @Override
  public void rewind(UploadDataSink uploadDataSink) throws IOException {
    position = 0;
    uploadDataSink.onRewindSucceeded();
  }

}
