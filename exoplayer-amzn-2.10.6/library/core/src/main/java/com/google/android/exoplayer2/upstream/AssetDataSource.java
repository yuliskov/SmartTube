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

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** A {@link DataSource} for reading from a local asset. */
public final class AssetDataSource extends BaseDataSource {

  /**
   * Thrown when an {@link IOException} is encountered reading a local asset.
   */
  public static final class AssetDataSourceException extends IOException {

    public AssetDataSourceException(IOException cause) {
      super(cause);
    }

  }

  private final AssetManager assetManager;

  private @Nullable Uri uri;
  private @Nullable InputStream inputStream;
  private long bytesRemaining;
  private boolean opened;

  /** @param context A context. */
  public AssetDataSource(Context context) {
    super(/* isNetwork= */ false);
    this.assetManager = context.getAssets();
  }

  /**
   * @param context A context.
   * @param listener An optional listener.
   * @deprecated Use {@link #AssetDataSource(Context)} and {@link
   *     #addTransferListener(TransferListener)}.
   */
  @Deprecated
  public AssetDataSource(Context context, @Nullable TransferListener listener) {
    this(context);
    if (listener != null) {
      addTransferListener(listener);
    }
  }

  @Override
  public long open(DataSpec dataSpec) throws AssetDataSourceException {
    try {
      uri = dataSpec.uri;
      String path = uri.getPath();
      if (path.startsWith("/android_asset/")) {
        path = path.substring(15);
      } else if (path.startsWith("/")) {
        path = path.substring(1);
      }
      transferInitializing(dataSpec);
      inputStream = assetManager.open(path, AssetManager.ACCESS_RANDOM);
      long skipped = inputStream.skip(dataSpec.position);
      if (skipped < dataSpec.position) {
        // assetManager.open() returns an AssetInputStream, whose skip() implementation only skips
        // fewer bytes than requested if the skip is beyond the end of the asset's data.
        throw new EOFException();
      }
      if (dataSpec.length != C.LENGTH_UNSET) {
        bytesRemaining = dataSpec.length;
      } else {
        bytesRemaining = inputStream.available();
        if (bytesRemaining == Integer.MAX_VALUE) {
          // assetManager.open() returns an AssetInputStream, whose available() implementation
          // returns Integer.MAX_VALUE if the remaining length is greater than (or equal to)
          // Integer.MAX_VALUE. We don't know the true length in this case, so treat as unbounded.
          bytesRemaining = C.LENGTH_UNSET;
        }
      }
    } catch (IOException e) {
      throw new AssetDataSourceException(e);
    }

    opened = true;
    transferStarted(dataSpec);
    return bytesRemaining;
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws AssetDataSourceException {
    if (readLength == 0) {
      return 0;
    } else if (bytesRemaining == 0) {
      return C.RESULT_END_OF_INPUT;
    }

    int bytesRead;
    try {
      int bytesToRead = bytesRemaining == C.LENGTH_UNSET ? readLength
          : (int) Math.min(bytesRemaining, readLength);
      bytesRead = inputStream.read(buffer, offset, bytesToRead);
    } catch (IOException e) {
      throw new AssetDataSourceException(e);
    }

    if (bytesRead == -1) {
      if (bytesRemaining != C.LENGTH_UNSET) {
        // End of stream reached having not read sufficient data.
        throw new AssetDataSourceException(new EOFException());
      }
      return C.RESULT_END_OF_INPUT;
    }
    if (bytesRemaining != C.LENGTH_UNSET) {
      bytesRemaining -= bytesRead;
    }
    bytesTransferred(bytesRead);
    return bytesRead;
  }

  @Override
  public @Nullable Uri getUri() {
    return uri;
  }

  @Override
  public void close() throws AssetDataSourceException {
    uri = null;
    try {
      if (inputStream != null) {
        inputStream.close();
      }
    } catch (IOException e) {
      throw new AssetDataSourceException(e);
    } finally {
      inputStream = null;
      if (opened) {
        opened = false;
        transferEnded();
      }
    }
  }

}
