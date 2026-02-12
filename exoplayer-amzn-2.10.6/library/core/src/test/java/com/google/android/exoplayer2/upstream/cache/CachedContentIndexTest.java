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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.net.Uri;
import androidx.annotation.Nullable;
import android.util.SparseArray;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests {@link CachedContentIndex}. */
@RunWith(AndroidJUnit4.class)
public class CachedContentIndexTest {

  private final byte[] testIndexV1File = {
      0, 0, 0, 1, // version
      0, 0, 0, 0, // flags
      0, 0, 0, 2, // number_of_CachedContent
      0, 0, 0, 5, // cache_id 5
      0, 5, 65, 66, 67, 68, 69, // cache_key "ABCDE"
      0, 0, 0, 0, 0, 0, 0, 10, // original_content_length
      0, 0, 0, 2, // cache_id 2
      0, 5, 75, 76, 77, 78, 79, // cache_key "KLMNO"
      0, 0, 0, 0, 0, 0, 10, 0, // original_content_length
      (byte) 0xF6, (byte) 0xFB, 0x50, 0x41 // hashcode_of_CachedContent_array
  };

  private final byte[] testIndexV2File = {
      0, 0, 0, 2, // version
      0, 0, 0, 0, // flags
      0, 0, 0, 2, // number_of_CachedContent
      0, 0, 0, 5, // cache_id 5
      0, 5, 65, 66, 67, 68, 69, // cache_key "ABCDE"
      0, 0, 0, 2, // metadata count
      0, 9, 101, 120, 111, 95, 114, 101, 100, 105, 114, // "exo_redir"
      0, 0, 0, 5, // value length
      97, 98, 99, 100, 101, // Redirected Uri "abcde"
      0, 7, 101, 120, 111, 95, 108, 101, 110, // "exo_len"
      0, 0, 0, 8, // value length
      0, 0, 0, 0, 0, 0, 0, 10, // original_content_length
      0, 0, 0, 2, // cache_id 2
      0, 5, 75, 76, 77, 78, 79, // cache_key "KLMNO"
      0, 0, 0, 1, // metadata count
      0, 7, 101, 120, 111, 95, 108, 101, 110, // "exo_len"
      0, 0, 0, 8, // value length
      0, 0, 0, 0, 0, 0, 10, 0, // original_content_length
      0x12, 0x15, 0x66, (byte) 0x8A // hashcode_of_CachedContent_array
  };
  private File cacheDir;

  @Before
  public void setUp() throws Exception {
    cacheDir =
        Util.createTempDirectory(ApplicationProvider.getApplicationContext(), "ExoPlayerTest");
  }

  @After
  public void tearDown() {
    Util.recursiveDelete(cacheDir);
  }

  @Test
  public void testAddGetRemove() throws Exception {
    final String key1 = "key1";
    final String key2 = "key2";
    final String key3 = "key3";

    CachedContentIndex index = newInstance();

    // Add two CachedContents with add methods
    CachedContent cachedContent1 = index.getOrAdd(key1);
    CachedContent cachedContent2 = index.getOrAdd(key2);
    assertThat(cachedContent1.id != cachedContent2.id).isTrue();

    // add a span
    int cacheFileLength = 20;
    File cacheSpanFile =
        SimpleCacheSpanTest.createCacheSpanFile(
            cacheDir,
            cachedContent1.id,
            /* offset= */ 10,
            cacheFileLength,
            /* lastTouchTimestamp= */ 30);
    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(cacheSpanFile, cacheFileLength, index);
    assertThat(span).isNotNull();
    cachedContent1.addSpan(span);

    // Check if they are added and get method returns null if the key isn't found
    assertThat(index.get(key1)).isEqualTo(cachedContent1);
    assertThat(index.get(key2)).isEqualTo(cachedContent2);
    assertThat(index.get(key3)).isNull();

    // test getAll()
    Collection<CachedContent> cachedContents = index.getAll();
    assertThat(cachedContents).containsExactly(cachedContent1, cachedContent2);

    // test getKeys()
    Set<String> keys = index.getKeys();
    assertThat(keys).containsExactly(key1, key2);

    // test getKeyForId()
    assertThat(index.getKeyForId(cachedContent1.id)).isEqualTo(key1);
    assertThat(index.getKeyForId(cachedContent2.id)).isEqualTo(key2);

    // test remove()
    index.maybeRemove(key2);
    index.maybeRemove(key3);
    assertThat(index.get(key1)).isEqualTo(cachedContent1);
    assertThat(index.get(key2)).isNull();
    assertThat(cacheSpanFile.exists()).isTrue();

    // test removeEmpty()
    index.getOrAdd(key2);
    index.removeEmpty();
    assertThat(index.get(key1)).isEqualTo(cachedContent1);
    assertThat(index.get(key2)).isNull();
    assertThat(cacheSpanFile.exists()).isTrue();
  }

  @Test
  public void testLegacyStoreAndLoad() throws Exception {
    assertStoredAndLoadedEqual(newLegacyInstance(), newLegacyInstance());
  }

  @Test
  public void testLegacyLoadV1() throws Exception {
    CachedContentIndex index = newLegacyInstance();

    FileOutputStream fos =
        new FileOutputStream(new File(cacheDir, CachedContentIndex.FILE_NAME_ATOMIC));
    fos.write(testIndexV1File);
    fos.close();

    index.initialize(/* uid= */ 0);
    assertThat(index.getAll()).hasSize(2);

    assertThat(index.assignIdForKey("ABCDE")).isEqualTo(5);
    ContentMetadata metadata = index.get("ABCDE").getMetadata();
    assertThat(ContentMetadata.getContentLength(metadata)).isEqualTo(10);

    assertThat(index.assignIdForKey("KLMNO")).isEqualTo(2);
    ContentMetadata metadata2 = index.get("KLMNO").getMetadata();
    assertThat(ContentMetadata.getContentLength(metadata2)).isEqualTo(2560);
  }

  @Test
  public void testLegacyLoadV2() throws Exception {
    CachedContentIndex index = newLegacyInstance();

    FileOutputStream fos =
        new FileOutputStream(new File(cacheDir, CachedContentIndex.FILE_NAME_ATOMIC));
    fos.write(testIndexV2File);
    fos.close();

    index.initialize(/* uid= */ 0);
    assertThat(index.getAll()).hasSize(2);

    assertThat(index.assignIdForKey("ABCDE")).isEqualTo(5);
    ContentMetadata metadata = index.get("ABCDE").getMetadata();
    assertThat(ContentMetadata.getContentLength(metadata)).isEqualTo(10);
    assertThat(ContentMetadata.getRedirectedUri(metadata)).isEqualTo(Uri.parse("abcde"));

    assertThat(index.assignIdForKey("KLMNO")).isEqualTo(2);
    ContentMetadata metadata2 = index.get("KLMNO").getMetadata();
    assertThat(ContentMetadata.getContentLength(metadata2)).isEqualTo(2560);
  }

  @Test
  public void testAssignIdForKeyAndGetKeyForId() {
    CachedContentIndex index = newInstance();
    final String key1 = "key1";
    final String key2 = "key2";
    int id1 = index.assignIdForKey(key1);
    int id2 = index.assignIdForKey(key2);
    assertThat(index.getKeyForId(id1)).isEqualTo(key1);
    assertThat(index.getKeyForId(id2)).isEqualTo(key2);
    assertThat(id1 != id2).isTrue();
    assertThat(index.assignIdForKey(key1)).isEqualTo(id1);
    assertThat(index.assignIdForKey(key2)).isEqualTo(id2);
  }

  @Test
  public void testGetNewId() {
    SparseArray<String> idToKey = new SparseArray<>();
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(0);
    idToKey.put(10, "");
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(11);
    idToKey.put(Integer.MAX_VALUE, "");
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(0);
    idToKey.put(0, "");
    assertThat(CachedContentIndex.getNewId(idToKey)).isEqualTo(1);
  }

  @Test
  public void testLegacyEncryption() throws Exception {
    byte[] key = Util.getUtf8Bytes("Bar12345Bar12345"); // 128 bit key
    byte[] key2 = Util.getUtf8Bytes("Foo12345Foo12345"); // 128 bit key

    assertStoredAndLoadedEqual(newLegacyInstance(key), newLegacyInstance(key));

    // Rename the index file from the test above
    File file1 = new File(cacheDir, CachedContentIndex.FILE_NAME_ATOMIC);
    File file2 = new File(cacheDir, "file2compare");
    assertThat(file1.renameTo(file2)).isTrue();

    // Write a new index file
    assertStoredAndLoadedEqual(newLegacyInstance(key), newLegacyInstance(key));

    assertThat(file1.length()).isEqualTo(file2.length());
    // Assert file content is different
    FileInputStream fis1 = new FileInputStream(file1);
    FileInputStream fis2 = new FileInputStream(file2);
    for (int b; (b = fis1.read()) == fis2.read(); ) {
      assertThat(b != -1).isTrue();
    }

    boolean threw = false;
    try {
      assertStoredAndLoadedEqual(newLegacyInstance(key), newLegacyInstance(key2));
    } catch (AssertionError e) {
      threw = true;
    }
    assertWithMessage("Encrypted index file can not be read with different encryption key")
        .that(threw)
        .isTrue();

    try {
      assertStoredAndLoadedEqual(newLegacyInstance(key), newLegacyInstance());
    } catch (AssertionError e) {
      threw = true;
    }
    assertWithMessage("Encrypted index file can not be read without encryption key")
        .that(threw)
        .isTrue();

    // Non encrypted index file can be read even when encryption key provided.
    assertStoredAndLoadedEqual(newLegacyInstance(), newLegacyInstance(key));

    // Test multiple store() calls
    CachedContentIndex index = newLegacyInstance(key);
    index.getOrAdd("key3");
    index.store();
    assertStoredAndLoadedEqual(index, newLegacyInstance(key));
  }

  @Test
  public void testRemoveEmptyNotLockedCachedContent() {
    CachedContentIndex index = newInstance();
    CachedContent cachedContent = index.getOrAdd("key1");

    index.maybeRemove(cachedContent.key);

    assertThat(index.get(cachedContent.key)).isNull();
  }

  @Test
  public void testCantRemoveNotEmptyCachedContent() throws Exception {
    CachedContentIndex index = newInstance();

    CachedContent cachedContent = index.getOrAdd("key1");
    long cacheFileLength = 20;
    File cacheFile =
        SimpleCacheSpanTest.createCacheSpanFile(
            cacheDir,
            cachedContent.id,
            /* offset= */ 10,
            cacheFileLength,
            /* lastTouchTimestamp= */ 30);
    SimpleCacheSpan span = SimpleCacheSpan.createCacheEntry(cacheFile, cacheFileLength, index);
    cachedContent.addSpan(span);

    index.maybeRemove(cachedContent.key);

    assertThat(index.get(cachedContent.key)).isNotNull();
  }

  @Test
  public void testCantRemoveLockedCachedContent() {
    CachedContentIndex index = newInstance();
    CachedContent cachedContent = index.getOrAdd("key1");
    cachedContent.setLocked(true);

    index.maybeRemove(cachedContent.key);

    assertThat(index.get(cachedContent.key)).isNotNull();
  }

  private void assertStoredAndLoadedEqual(CachedContentIndex index, CachedContentIndex index2)
      throws IOException {
    ContentMetadataMutations mutations1 = new ContentMetadataMutations();
    ContentMetadataMutations.setContentLength(mutations1, 2560);
    index.getOrAdd("KLMNO").applyMetadataMutations(mutations1);
    ContentMetadataMutations mutations2 = new ContentMetadataMutations();
    ContentMetadataMutations.setContentLength(mutations2, 10);
    ContentMetadataMutations.setRedirectedUri(mutations2, Uri.parse("abcde"));
    index.getOrAdd("ABCDE").applyMetadataMutations(mutations2);
    index.store();

    index2.initialize(/* uid= */ 0);
    Set<String> keys = index.getKeys();
    Set<String> keys2 = index2.getKeys();
    assertThat(keys2).isEqualTo(keys);
    for (String key : keys) {
      assertThat(index2.get(key)).isEqualTo(index.get(key));
    }
  }

  private CachedContentIndex newInstance() {
    return new CachedContentIndex(TestUtil.getTestDatabaseProvider());
  }

  private CachedContentIndex newLegacyInstance() {
    return newLegacyInstance(null);
  }

  private CachedContentIndex newLegacyInstance(@Nullable byte[] key) {
    return new CachedContentIndex(
        /* databaseProvider= */ null,
        cacheDir,
        /* legacyStorageSecretKey= */ key,
        /* legacyStorageEncrypt= */ key != null,
        /* preferLegacyStorage= */ true);
  }
}
