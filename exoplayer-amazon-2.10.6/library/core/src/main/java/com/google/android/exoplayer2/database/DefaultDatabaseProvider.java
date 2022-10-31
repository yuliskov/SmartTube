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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** A {@link DatabaseProvider} that provides instances obtained from a {@link SQLiteOpenHelper}. */
public final class DefaultDatabaseProvider implements DatabaseProvider {

  private final SQLiteOpenHelper sqliteOpenHelper;

  /**
   * @param sqliteOpenHelper An {@link SQLiteOpenHelper} from which to obtain database instances.
   */
  public DefaultDatabaseProvider(SQLiteOpenHelper sqliteOpenHelper) {
    this.sqliteOpenHelper = sqliteOpenHelper;
  }

  @Override
  public SQLiteDatabase getWritableDatabase() {
    return sqliteOpenHelper.getWritableDatabase();
  }

  @Override
  public SQLiteDatabase getReadableDatabase() {
    return sqliteOpenHelper.getReadableDatabase();
  }
}
