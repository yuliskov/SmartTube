/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.offline.DownloadRequest.UnsupportedRequestException;
import com.google.android.exoplayer2.util.AtomicFile;
import com.google.android.exoplayer2.util.Util;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads {@link DownloadRequest DownloadRequests} from legacy action files.
 *
 * @deprecated Legacy action files should be merged into download indices using {@link
 *     ActionFileUpgradeUtil}.
 */
@Deprecated
/* package */ final class ActionFile {

  private static final int VERSION = 0;

  private final AtomicFile atomicFile;

  /**
   * @param actionFile The file from which {@link DownloadRequest DownloadRequests} will be loaded.
   */
  public ActionFile(File actionFile) {
    atomicFile = new AtomicFile(actionFile);
  }

  /** Returns whether the file or its backup exists. */
  public boolean exists() {
    return atomicFile.exists();
  }

  /** Deletes the action file and its backup. */
  public void delete() {
    atomicFile.delete();
  }

  /**
   * Loads {@link DownloadRequest DownloadRequests} from the file.
   *
   * @return The loaded {@link DownloadRequest DownloadRequests}, or an empty array if the file does
   *     not exist.
   * @throws IOException If there is an error reading the file.
   */
  public DownloadRequest[] load() throws IOException {
    if (!exists()) {
      return new DownloadRequest[0];
    }
    InputStream inputStream = null;
    try {
      inputStream = atomicFile.openRead();
      DataInputStream dataInputStream = new DataInputStream(inputStream);
      int version = dataInputStream.readInt();
      if (version > VERSION) {
        throw new IOException("Unsupported action file version: " + version);
      }
      int actionCount = dataInputStream.readInt();
      ArrayList<DownloadRequest> actions = new ArrayList<>();
      for (int i = 0; i < actionCount; i++) {
        try {
          actions.add(readDownloadRequest(dataInputStream));
        } catch (UnsupportedRequestException e) {
          // remove DownloadRequest is not supported. Ignore and continue loading rest.
        }
      }
      return actions.toArray(new DownloadRequest[0]);
    } finally {
      Util.closeQuietly(inputStream);
    }
  }

  private static DownloadRequest readDownloadRequest(DataInputStream input) throws IOException {
    String type = input.readUTF();
    int version = input.readInt();

    Uri uri = Uri.parse(input.readUTF());
    boolean isRemoveAction = input.readBoolean();

    int dataLength = input.readInt();
    byte[] data;
    if (dataLength != 0) {
      data = new byte[dataLength];
      input.readFully(data);
    } else {
      data = null;
    }

    // Serialized version 0 progressive actions did not contain keys.
    boolean isLegacyProgressive = version == 0 && DownloadRequest.TYPE_PROGRESSIVE.equals(type);
    List<StreamKey> keys = new ArrayList<>();
    if (!isLegacyProgressive) {
      int keyCount = input.readInt();
      for (int i = 0; i < keyCount; i++) {
        keys.add(readKey(type, version, input));
      }
    }

    // Serialized version 0 and 1 DASH/HLS/SS actions did not contain a custom cache key.
    boolean isLegacySegmented =
        version < 2
            && (DownloadRequest.TYPE_DASH.equals(type)
                || DownloadRequest.TYPE_HLS.equals(type)
                || DownloadRequest.TYPE_SS.equals(type));
    String customCacheKey = null;
    if (!isLegacySegmented) {
      customCacheKey = input.readBoolean() ? input.readUTF() : null;
    }

    // Serialized version 0, 1 and 2 did not contain an id. We need to generate one.
    String id = version < 3 ? generateDownloadId(uri, customCacheKey) : input.readUTF();

    if (isRemoveAction) {
      // Remove actions are not supported anymore.
      throw new UnsupportedRequestException();
    }
    return new DownloadRequest(id, type, uri, keys, customCacheKey, data);
  }

  private static StreamKey readKey(String type, int version, DataInputStream input)
      throws IOException {
    int periodIndex;
    int groupIndex;
    int trackIndex;

    // Serialized version 0 HLS/SS actions did not contain a period index.
    if ((DownloadRequest.TYPE_HLS.equals(type) || DownloadRequest.TYPE_SS.equals(type))
        && version == 0) {
      periodIndex = 0;
      groupIndex = input.readInt();
      trackIndex = input.readInt();
    } else {
      periodIndex = input.readInt();
      groupIndex = input.readInt();
      trackIndex = input.readInt();
    }
    return new StreamKey(periodIndex, groupIndex, trackIndex);
  }

  private static String generateDownloadId(Uri uri, @Nullable String customCacheKey) {
    return customCacheKey != null ? customCacheKey : uri.toString();
  }
}
