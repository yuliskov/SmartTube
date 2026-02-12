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

import static com.google.common.truth.Truth.assertThat;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link VersionTable}. */
@RunWith(AndroidJUnit4.class)
public class VersionTableTest {

  private static final int FEATURE_1 = 1;
  private static final int FEATURE_2 = 2;
  private static final String INSTANCE_1 = "1";
  private static final String INSTANCE_2 = "2";

  private DatabaseProvider databaseProvider;
  private SQLiteDatabase database;

  @Before
  public void setUp() {
    databaseProvider = TestUtil.getTestDatabaseProvider();
    database = databaseProvider.getWritableDatabase();
  }

  @Test
  public void getVersion_unsetFeature_returnsVersionUnset() throws DatabaseIOException {
    int version = VersionTable.getVersion(database, FEATURE_1, INSTANCE_1);
    assertThat(version).isEqualTo(VersionTable.VERSION_UNSET);
  }

  @Test
  public void getVersion_unsetVersion_returnsVersionUnset() throws DatabaseIOException {
    VersionTable.setVersion(database, FEATURE_1, INSTANCE_1, 1);
    int version = VersionTable.getVersion(database, FEATURE_1, INSTANCE_2);
    assertThat(version).isEqualTo(VersionTable.VERSION_UNSET);
  }

  @Test
  public void getVersion_returnsSetVersion() throws DatabaseIOException {
    VersionTable.setVersion(database, FEATURE_1, INSTANCE_1, 1);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_1)).isEqualTo(1);

    VersionTable.setVersion(database, FEATURE_1, INSTANCE_1, 2);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_1)).isEqualTo(2);

    VersionTable.setVersion(database, FEATURE_2, INSTANCE_1, 3);
    assertThat(VersionTable.getVersion(database, FEATURE_2, INSTANCE_1)).isEqualTo(3);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_1)).isEqualTo(2);

    VersionTable.setVersion(database, FEATURE_2, INSTANCE_2, 4);
    assertThat(VersionTable.getVersion(database, FEATURE_2, INSTANCE_2)).isEqualTo(4);
    assertThat(VersionTable.getVersion(database, FEATURE_2, INSTANCE_1)).isEqualTo(3);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_1)).isEqualTo(2);
  }

  @Test
  public void removeVersion_removesSetVersion() throws DatabaseIOException {
    VersionTable.setVersion(database, FEATURE_1, INSTANCE_1, 1);
    VersionTable.setVersion(database, FEATURE_1, INSTANCE_2, 2);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_1)).isEqualTo(1);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_2)).isEqualTo(2);

    VersionTable.removeVersion(database, FEATURE_1, INSTANCE_1);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_1))
        .isEqualTo(VersionTable.VERSION_UNSET);
    assertThat(VersionTable.getVersion(database, FEATURE_1, INSTANCE_2)).isEqualTo(2);
  }

  @Test
  public void doesTableExist_nonExistingTable_returnsFalse() {
    assertThat(VersionTable.tableExists(database, "NonExistingTable")).isFalse();
  }

  @Test
  public void doesTableExist_existingTable_returnsTrue() {
    String table = "TestTable";
    databaseProvider.getWritableDatabase().execSQL("CREATE TABLE " + table + " (dummy INTEGER)");
    assertThat(VersionTable.tableExists(database, table)).isTrue();
  }
}
