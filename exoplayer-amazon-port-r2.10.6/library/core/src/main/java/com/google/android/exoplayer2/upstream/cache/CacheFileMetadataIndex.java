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
package com.google.android.exoplayer2.upstream.cache;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.util.Assertions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Maintains an index of cache file metadata. */
/* package */ final class CacheFileMetadataIndex {

  private static final String TABLE_PREFIX = DatabaseProvider.TABLE_PREFIX + "CacheFileMetadata";
  private static final int TABLE_VERSION = 1;

  private static final String COLUMN_NAME = "name";
  private static final String COLUMN_LENGTH = "length";
  private static final String COLUMN_LAST_TOUCH_TIMESTAMP = "last_touch_timestamp";

  private static final int COLUMN_INDEX_NAME = 0;
  private static final int COLUMN_INDEX_LENGTH = 1;
  private static final int COLUMN_INDEX_LAST_TOUCH_TIMESTAMP = 2;

  private static final String WHERE_NAME_EQUALS = COLUMN_INDEX_NAME + " = ?";

  private static final String[] COLUMNS =
      new String[] {
        COLUMN_NAME, COLUMN_LENGTH, COLUMN_LAST_TOUCH_TIMESTAMP,
      };
  private static final String TABLE_SCHEMA =
      "("
          + COLUMN_NAME
          + " TEXT PRIMARY KEY NOT NULL,"
          + COLUMN_LENGTH
          + " INTEGER NOT NULL,"
          + COLUMN_LAST_TOUCH_TIMESTAMP
          + " INTEGER NOT NULL)";

  private final DatabaseProvider databaseProvider;

  @MonotonicNonNull private String tableName;

  /**
   * Deletes index data for the specified cache.
   *
   * @param databaseProvider Provides the database in which the index is stored.
   * @param uid The cache UID.
   * @throws DatabaseIOException If an error occurs deleting the index data.
   */
  public static void delete(DatabaseProvider databaseProvider, long uid)
      throws DatabaseIOException {
    String hexUid = Long.toHexString(uid);
    try {
      String tableName = getTableName(hexUid);
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.beginTransaction();
      try {
        VersionTable.removeVersion(
            writableDatabase, VersionTable.FEATURE_CACHE_FILE_METADATA, hexUid);
        dropTable(writableDatabase, tableName);
        writableDatabase.setTransactionSuccessful();
      } finally {
        writableDatabase.endTransaction();
      }
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /** @param databaseProvider Provides the database in which the index is stored. */
  public CacheFileMetadataIndex(DatabaseProvider databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  /**
   * Initializes the index for the given cache UID.
   *
   * @param uid The cache UID.
   * @throws DatabaseIOException If an error occurs initializing the index.
   */
  public void initialize(long uid) throws DatabaseIOException {
    try {
      String hexUid = Long.toHexString(uid);
      tableName = getTableName(hexUid);
      SQLiteDatabase readableDatabase = databaseProvider.getReadableDatabase();
      int version =
          VersionTable.getVersion(
              readableDatabase, VersionTable.FEATURE_CACHE_FILE_METADATA, hexUid);
      if (version != TABLE_VERSION) {
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          VersionTable.setVersion(
              writableDatabase, VersionTable.FEATURE_CACHE_FILE_METADATA, hexUid, TABLE_VERSION);
          dropTable(writableDatabase, tableName);
          writableDatabase.execSQL("CREATE TABLE " + tableName + " " + TABLE_SCHEMA);
          writableDatabase.setTransactionSuccessful();
        } finally {
          writableDatabase.endTransaction();
        }
      }
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Returns all file metadata keyed by file name. The returned map is mutable and may be modified
   * by the caller.
   *
   * @return The file metadata keyed by file name.
   * @throws DatabaseIOException If an error occurs loading the metadata.
   */
  public Map<String, CacheFileMetadata> getAll() throws DatabaseIOException {
    try (Cursor cursor = getCursor()) {
      Map<String, CacheFileMetadata> fileMetadata = new HashMap<>(cursor.getCount());
      while (cursor.moveToNext()) {
        String name = cursor.getString(COLUMN_INDEX_NAME);
        long length = cursor.getLong(COLUMN_INDEX_LENGTH);
        long lastTouchTimestamp = cursor.getLong(COLUMN_INDEX_LAST_TOUCH_TIMESTAMP);
        fileMetadata.put(name, new CacheFileMetadata(length, lastTouchTimestamp));
      }
      return fileMetadata;
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Sets metadata for a given file.
   *
   * @param name The name of the file.
   * @param length The file length.
   * @param lastTouchTimestamp The file last touch timestamp.
   * @throws DatabaseIOException If an error occurs setting the metadata.
   */
  public void set(String name, long length, long lastTouchTimestamp) throws DatabaseIOException {
    Assertions.checkNotNull(tableName);
    try {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      ContentValues values = new ContentValues();
      values.put(COLUMN_NAME, name);
      values.put(COLUMN_LENGTH, length);
      values.put(COLUMN_LAST_TOUCH_TIMESTAMP, lastTouchTimestamp);
      writableDatabase.replaceOrThrow(tableName, /* nullColumnHack= */ null, values);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Removes metadata.
   *
   * @param name The name of the file whose metadata is to be removed.
   * @throws DatabaseIOException If an error occurs removing the metadata.
   */
  public void remove(String name) throws DatabaseIOException {
    Assertions.checkNotNull(tableName);
    try {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.delete(tableName, WHERE_NAME_EQUALS, new String[] {name});
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Removes metadata.
   *
   * @param names The names of the files whose metadata is to be removed.
   * @throws DatabaseIOException If an error occurs removing the metadata.
   */
  public void removeAll(Set<String> names) throws DatabaseIOException {
    Assertions.checkNotNull(tableName);
    try {
      SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
      writableDatabase.beginTransaction();
      try {
        for (String name : names) {
          writableDatabase.delete(tableName, WHERE_NAME_EQUALS, new String[] {name});
        }
        writableDatabase.setTransactionSuccessful();
      } finally {
        writableDatabase.endTransaction();
      }
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  private Cursor getCursor() {
    Assertions.checkNotNull(tableName);
    return databaseProvider
        .getReadableDatabase()
        .query(
            tableName,
            COLUMNS,
            /* selection */ null,
            /* selectionArgs= */ null,
            /* groupBy= */ null,
            /* having= */ null,
            /* orderBy= */ null);
  }

  private static void dropTable(SQLiteDatabase writableDatabase, String tableName) {
    writableDatabase.execSQL("DROP TABLE IF EXISTS " + tableName);
  }

  private static String getTableName(String hexUid) {
    return TABLE_PREFIX + hexUid;
  }
}
