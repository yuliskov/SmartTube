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
package com.google.android.exoplayer2.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Utility methods for accessing versions of ExoPlayer database components. This allows them to be
 * versioned independently to the version of the containing database.
 */
public final class VersionTable {

  /** Returned by {@link #getVersion(SQLiteDatabase, int, String)} if the version is unset. */
  public static final int VERSION_UNSET = -1;
  /** Version of tables used for offline functionality. */
  public static final int FEATURE_OFFLINE = 0;
  /** Version of tables used for cache content metadata. */
  public static final int FEATURE_CACHE_CONTENT_METADATA = 1;
  /** Version of tables used for cache file metadata. */
  public static final int FEATURE_CACHE_FILE_METADATA = 2;

  private static final String TABLE_NAME = DatabaseProvider.TABLE_PREFIX + "Versions";

  private static final String COLUMN_FEATURE = "feature";
  private static final String COLUMN_INSTANCE_UID = "instance_uid";
  private static final String COLUMN_VERSION = "version";

  private static final String WHERE_FEATURE_AND_INSTANCE_UID_EQUALS =
      COLUMN_FEATURE + " = ? AND " + COLUMN_INSTANCE_UID + " = ?";

  private static final String PRIMARY_KEY =
      "PRIMARY KEY (" + COLUMN_FEATURE + ", " + COLUMN_INSTANCE_UID + ")";
  private static final String SQL_CREATE_TABLE_IF_NOT_EXISTS =
      "CREATE TABLE IF NOT EXISTS "
          + TABLE_NAME
          + " ("
          + COLUMN_FEATURE
          + " INTEGER NOT NULL,"
          + COLUMN_INSTANCE_UID
          + " TEXT NOT NULL,"
          + COLUMN_VERSION
          + " INTEGER NOT NULL,"
          + PRIMARY_KEY
          + ")";

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FEATURE_OFFLINE, FEATURE_CACHE_CONTENT_METADATA, FEATURE_CACHE_FILE_METADATA})
  private @interface Feature {}

  private VersionTable() {}

  /**
   * Sets the version of a specified instance of a specified feature.
   *
   * @param writableDatabase The database to update.
   * @param feature The feature.
   * @param instanceUid The unique identifier of the instance of the feature.
   * @param version The version.
   * @throws DatabaseIOException If an error occurs executing the SQL.
   */
  public static void setVersion(
      SQLiteDatabase writableDatabase, @Feature int feature, String instanceUid, int version)
      throws DatabaseIOException {
    try {
      writableDatabase.execSQL(SQL_CREATE_TABLE_IF_NOT_EXISTS);
      ContentValues values = new ContentValues();
      values.put(COLUMN_FEATURE, feature);
      values.put(COLUMN_INSTANCE_UID, instanceUid);
      values.put(COLUMN_VERSION, version);
      writableDatabase.replaceOrThrow(TABLE_NAME, /* nullColumnHack= */ null, values);
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Removes the version of a specified instance of a feature.
   *
   * @param writableDatabase The database to update.
   * @param feature The feature.
   * @param instanceUid The unique identifier of the instance of the feature.
   * @throws DatabaseIOException If an error occurs executing the SQL.
   */
  public static void removeVersion(
      SQLiteDatabase writableDatabase, @Feature int feature, String instanceUid)
      throws DatabaseIOException {
    try {
      if (!tableExists(writableDatabase, TABLE_NAME)) {
        return;
      }
      writableDatabase.delete(
          TABLE_NAME,
          WHERE_FEATURE_AND_INSTANCE_UID_EQUALS,
          featureAndInstanceUidArguments(feature, instanceUid));
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  /**
   * Returns the version of a specified instance of a feature, or {@link #VERSION_UNSET} if no
   * version is set.
   *
   * @param database The database to query.
   * @param feature The feature.
   * @param instanceUid The unique identifier of the instance of the feature.
   * @return The version, or {@link #VERSION_UNSET} if no version is set.
   * @throws DatabaseIOException If an error occurs executing the SQL.
   */
  public static int getVersion(SQLiteDatabase database, @Feature int feature, String instanceUid)
      throws DatabaseIOException {
    try {
      if (!tableExists(database, TABLE_NAME)) {
        return VERSION_UNSET;
      }
      try (Cursor cursor =
          database.query(
              TABLE_NAME,
              new String[] {COLUMN_VERSION},
              WHERE_FEATURE_AND_INSTANCE_UID_EQUALS,
              featureAndInstanceUidArguments(feature, instanceUid),
              /* groupBy= */ null,
              /* having= */ null,
              /* orderBy= */ null)) {
        if (cursor.getCount() == 0) {
          return VERSION_UNSET;
        }
        cursor.moveToNext();
        return cursor.getInt(/* COLUMN_VERSION index */ 0);
      }
    } catch (SQLException e) {
      throw new DatabaseIOException(e);
    }
  }

  @VisibleForTesting
  /* package */ static boolean tableExists(SQLiteDatabase readableDatabase, String tableName) {
    long count =
        DatabaseUtils.queryNumEntries(
            readableDatabase, "sqlite_master", "tbl_name = ?", new String[] {tableName});
    return count > 0;
  }

  private static String[] featureAndInstanceUidArguments(int feature, String instance) {
    return new String[] {Integer.toString(feature), instance};
  }
}
