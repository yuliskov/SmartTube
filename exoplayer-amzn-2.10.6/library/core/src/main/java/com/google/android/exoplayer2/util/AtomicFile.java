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
package com.google.android.exoplayer2.util;

import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A helper class for performing atomic operations on a file by creating a backup file until a write
 * has successfully completed.
 *
 * <p>Atomic file guarantees file integrity by ensuring that a file has been completely written and
 * synced to disk before removing its backup. As long as the backup file exists, the original file
 * is considered to be invalid (left over from a previous attempt to write the file).
 *
 * <p>Atomic file does not confer any file locking semantics. Do not use this class when the file
 * may be accessed or modified concurrently by multiple threads or processes. The caller is
 * responsible for ensuring appropriate mutual exclusion invariants whenever it accesses the file.
 */
public final class AtomicFile {

  private static final String TAG = "AtomicFile";

  private final File baseName;
  private final File backupName;

  /**
   * Create a new AtomicFile for a file located at the given File path. The secondary backup file
   * will be the same file path with ".bak" appended.
   */
  public AtomicFile(File baseName) {
    this.baseName = baseName;
    backupName = new File(baseName.getPath() + ".bak");
  }

  /** Returns whether the file or its backup exists. */
  public boolean exists() {
    return baseName.exists() || backupName.exists();
  }

  /** Delete the atomic file. This deletes both the base and backup files. */
  public void delete() {
    baseName.delete();
    backupName.delete();
  }

  /**
   * Start a new write operation on the file. This returns an {@link OutputStream} to which you can
   * write the new file data. If the whole data is written successfully you <em>must</em> call
   * {@link #endWrite(OutputStream)}. On failure you should call {@link OutputStream#close()}
   * only to free up resources used by it.
   *
   * <p>Example usage:
   *
   * <pre>
   *   DataOutputStream dataOutput = null;
   *   try {
   *     OutputStream outputStream = atomicFile.startWrite();
   *     dataOutput = new DataOutputStream(outputStream); // Wrapper stream
   *     dataOutput.write(data1);
   *     dataOutput.write(data2);
   *     atomicFile.endWrite(dataOutput); // Pass wrapper stream
   *   } finally{
   *     if (dataOutput != null) {
   *       dataOutput.close();
   *     }
   *   }
   * </pre>
   *
   * <p>Note that if another thread is currently performing a write, this will simply replace
   * whatever that thread is writing with the new file being written by this thread, and when the
   * other thread finishes the write the new write operation will no longer be safe (or will be
   * lost). You must do your own threading protection for access to AtomicFile.
   */
  public OutputStream startWrite() throws IOException {
    // Rename the current file so it may be used as a backup during the next read
    if (baseName.exists()) {
      if (!backupName.exists()) {
        if (!baseName.renameTo(backupName)) {
          Log.w(TAG, "Couldn't rename file " + baseName + " to backup file " + backupName);
        }
      } else {
        baseName.delete();
      }
    }
    OutputStream str;
    try {
      str = new AtomicFileOutputStream(baseName);
    } catch (FileNotFoundException e) {
      File parent = baseName.getParentFile();
      if (parent == null || !parent.mkdirs()) {
        throw new IOException("Couldn't create " + baseName, e);
      }
      // Try again now that we've created the parent directory.
      try {
        str = new AtomicFileOutputStream(baseName);
      } catch (FileNotFoundException e2) {
        throw new IOException("Couldn't create " + baseName, e2);
      }
    }
    return str;
  }

  /**
   * Call when you have successfully finished writing to the stream returned by {@link
   * #startWrite()}. This will close, sync, and commit the new data. The next attempt to read the
   * atomic file will return the new file stream.
   *
   * @param str Outer-most wrapper OutputStream used to write to the stream returned by {@link
   *     #startWrite()}.
   * @see #startWrite()
   */
  public void endWrite(OutputStream str) throws IOException {
    str.close();
    // If close() throws exception, the next line is skipped.
    backupName.delete();
  }

  /**
   * Open the atomic file for reading. If there previously was an incomplete write, this will roll
   * back to the last good data before opening for read.
   *
   * <p>Note that if another thread is currently performing a write, this will incorrectly consider
   * it to be in the state of a bad write and roll back, causing the new data currently being
   * written to be dropped. You must do your own threading protection for access to AtomicFile.
   */
  public InputStream openRead() throws FileNotFoundException {
    restoreBackup();
    return new FileInputStream(baseName);
  }

  private void restoreBackup() {
    if (backupName.exists()) {
      baseName.delete();
      backupName.renameTo(baseName);
    }
  }

  private static final class AtomicFileOutputStream extends OutputStream {

    private final FileOutputStream fileOutputStream;
    private boolean closed = false;

    public AtomicFileOutputStream(File file) throws FileNotFoundException {
      fileOutputStream = new FileOutputStream(file);
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      flush();
      try {
        fileOutputStream.getFD().sync();
      } catch (IOException e) {
        Log.w(TAG, "Failed to sync file descriptor:", e);
      }
      fileOutputStream.close();
    }

    @Override
    public void flush() throws IOException {
      fileOutputStream.flush();
    }

    @Override
    public void write(int b) throws IOException {
      fileOutputStream.write(b);
    }

    @Override
    public void write(@NonNull byte[] b) throws IOException {
      fileOutputStream.write(b);
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
      fileOutputStream.write(b, off, len);
    }
  }
}
