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
package com.google.android.exoplayer2.upstream.cache;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import com.google.android.exoplayer2.database.DatabaseIOException;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.AtomicFile;
import com.google.android.exoplayer2.util.ReusableBufferedOutputStream;
import com.google.android.exoplayer2.util.Util;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Maintains the index of cached content. */
/* package */ class CachedContentIndex {

  /* package */ static final String FILE_NAME_ATOMIC = "cached_content_index.exi";

  private static final int INCREMENTAL_METADATA_READ_LENGTH = 10 * 1024 * 1024;

  private final HashMap<String, CachedContent> keyToContent;
  /**
   * Maps assigned ids to their corresponding keys. Also contains (id -> null) entries for ids that
   * have been removed from the index since it was last stored. This prevents reuse of these ids,
   * which is necessary to avoid clashes that could otherwise occur as a result of the sequence:
   *
   * <p>[1] (key1, id1) is removed from the in-memory index ... the index is not stored to disk ...
   * [2] id1 is reused for a different key2 ... the index is not stored to disk ... [3] A file for
   * key2 is partially written using a path corresponding to id1 ... the process is killed before
   * the index is stored to disk ... [4] The index is read from disk, causing the partially written
   * file to be incorrectly associated to key1
   *
   * <p>By avoiding id reuse in step [2], a new id2 will be used instead. Step [4] will then delete
   * the partially written file because the index does not contain an entry for id2.
   *
   * <p>When the index is next stored (id -> null) entries are removed, making the ids eligible for
   * reuse.
   */
  private final SparseArray<@NullableType String> idToKey;
  /**
   * Tracks ids for which (id -> null) entries are present in idToKey, so that they can be removed
   * efficiently when the index is next stored.
   */
  private final SparseBooleanArray removedIds;
  /** Tracks ids that are new since the index was last stored. */
  private final SparseBooleanArray newIds;

  private Storage storage;
  @Nullable private Storage previousStorage;

  /** Returns whether the file is an index file. */
  public static final boolean isIndexFile(String fileName) {
    // Atomic file backups add additional suffixes to the file name.
    return fileName.startsWith(FILE_NAME_ATOMIC);
  }

  /**
   * Deletes index data for the specified cache.
   *
   * @param databaseProvider Provides the database in which the index is stored.
   * @param uid The cache UID.
   * @throws DatabaseIOException If an error occurs deleting the index data.
   */
  public static void delete(DatabaseProvider databaseProvider, long uid)
      throws DatabaseIOException {
    DatabaseStorage.delete(databaseProvider, uid);
  }

  /**
   * Creates an instance supporting database storage only.
   *
   * @param databaseProvider Provides the database in which the index is stored.
   */
  public CachedContentIndex(DatabaseProvider databaseProvider) {
    this(
        databaseProvider,
        /* legacyStorageDir= */ null,
        /* legacyStorageSecretKey= */ null,
        /* legacyStorageEncrypt= */ false,
        /* preferLegacyStorage= */ false);
  }

  /**
   * Creates an instance supporting either or both of database and legacy storage.
   *
   * @param databaseProvider Provides the database in which the index is stored, or {@code null} to
   *     use only legacy storage.
   * @param legacyStorageDir The directory in which any legacy storage is stored, or {@code null} to
   *     use only database storage.
   * @param legacyStorageSecretKey A 16 byte AES key for reading, and optionally writing, legacy
   *     storage.
   * @param legacyStorageEncrypt Whether to encrypt when writing to legacy storage. Must be false if
   *     {@code legacyStorageSecretKey} is null.
   * @param preferLegacyStorage Whether to use prefer legacy storage if both storage types are
   *     enabled. This option is only useful for downgrading from database storage back to legacy
   *     storage.
   */
  public CachedContentIndex(
      @Nullable DatabaseProvider databaseProvider,
      @Nullable File legacyStorageDir,
      @Nullable byte[] legacyStorageSecretKey,
      boolean legacyStorageEncrypt,
      boolean preferLegacyStorage) {
    Assertions.checkState(databaseProvider != null || legacyStorageDir != null);
    keyToContent = new HashMap<>();
    idToKey = new SparseArray<>();
    removedIds = new SparseBooleanArray();
    newIds = new SparseBooleanArray();
    Storage databaseStorage =
        databaseProvider != null ? new DatabaseStorage(databaseProvider) : null;
    Storage legacyStorage =
        legacyStorageDir != null
            ? new LegacyStorage(
                new File(legacyStorageDir, FILE_NAME_ATOMIC),
                legacyStorageSecretKey,
                legacyStorageEncrypt)
            : null;
    if (databaseStorage == null || (legacyStorage != null && preferLegacyStorage)) {
      storage = legacyStorage;
      previousStorage = databaseStorage;
    } else {
      storage = databaseStorage;
      previousStorage = legacyStorage;
    }
  }

  /**
   * Loads the index data for the given cache UID.
   *
   * @param uid The UID of the cache whose index is to be loaded.
   * @throws IOException If an error occurs initializing the index data.
   */
  public void initialize(long uid) throws IOException {
    storage.initialize(uid);
    if (previousStorage != null) {
      previousStorage.initialize(uid);
    }
    if (!storage.exists() && previousStorage != null && previousStorage.exists()) {
      // Copy from previous storage into current storage.
      previousStorage.load(keyToContent, idToKey);
      storage.storeFully(keyToContent);
    } else {
      // Load from the current storage.
      storage.load(keyToContent, idToKey);
    }
    if (previousStorage != null) {
      previousStorage.delete();
      previousStorage = null;
    }
  }

  /**
   * Stores the index data to index file if there is a change.
   *
   * @throws IOException If an error occurs storing the index data.
   */
  public void store() throws IOException {
    storage.storeIncremental(keyToContent);
    // Make ids that were removed since the index was last stored eligible for re-use.
    int removedIdCount = removedIds.size();
    for (int i = 0; i < removedIdCount; i++) {
      idToKey.remove(removedIds.keyAt(i));
    }
    removedIds.clear();
    newIds.clear();
  }

  /**
   * Adds the given key to the index if it isn't there already.
   *
   * @param key The cache key that uniquely identifies the original stream.
   * @return A new or existing CachedContent instance with the given key.
   */
  public CachedContent getOrAdd(String key) {
    CachedContent cachedContent = keyToContent.get(key);
    return cachedContent == null ? addNew(key) : cachedContent;
  }

  /** Returns a CachedContent instance with the given key or null if there isn't one. */
  public CachedContent get(String key) {
    return keyToContent.get(key);
  }

  /**
   * Returns a Collection of all CachedContent instances in the index. The collection is backed by
   * the {@code keyToContent} map, so changes to the map are reflected in the collection, and
   * vice-versa. If the map is modified while an iteration over the collection is in progress
   * (except through the iterator's own remove operation), the results of the iteration are
   * undefined.
   */
  public Collection<CachedContent> getAll() {
    return keyToContent.values();
  }

  /** Returns an existing or new id assigned to the given key. */
  public int assignIdForKey(String key) {
    return getOrAdd(key).id;
  }

  /** Returns the key which has the given id assigned. */
  public String getKeyForId(int id) {
    return idToKey.get(id);
  }

  /** Removes {@link CachedContent} with the given key from index if it's empty and not locked. */
  public void maybeRemove(String key) {
    CachedContent cachedContent = keyToContent.get(key);
    if (cachedContent != null && cachedContent.isEmpty() && !cachedContent.isLocked()) {
      keyToContent.remove(key);
      int id = cachedContent.id;
      boolean neverStored = newIds.get(id);
      storage.onRemove(cachedContent, neverStored);
      if (neverStored) {
        // The id can be reused immediately.
        idToKey.remove(id);
        newIds.delete(id);
      } else {
        // Keep an entry in idToKey to stop the id from being reused until the index is next stored,
        // and add an entry to removedIds to track that it should be removed when this does happen.
        idToKey.put(id, /* value= */ null);
        removedIds.put(id, /* value= */ true);
      }
    }
  }

  /** Removes empty and not locked {@link CachedContent} instances from index. */
  public void removeEmpty() {
    String[] keys = new String[keyToContent.size()];
    keyToContent.keySet().toArray(keys);
    for (String key : keys) {
      maybeRemove(key);
    }
  }

  /**
   * Returns a set of all content keys. The set is backed by the {@code keyToContent} map, so
   * changes to the map are reflected in the set, and vice-versa. If the map is modified while an
   * iteration over the set is in progress (except through the iterator's own remove operation), the
   * results of the iteration are undefined.
   */
  public Set<String> getKeys() {
    return keyToContent.keySet();
  }

  /**
   * Applies {@code mutations} to the {@link ContentMetadata} for the given key. A new {@link
   * CachedContent} is added if there isn't one already with the given key.
   */
  public void applyContentMetadataMutations(String key, ContentMetadataMutations mutations) {
    CachedContent cachedContent = getOrAdd(key);
    if (cachedContent.applyMetadataMutations(mutations)) {
      storage.onUpdate(cachedContent);
    }
  }

  /** Returns a {@link ContentMetadata} for the given key. */
  public ContentMetadata getContentMetadata(String key) {
    CachedContent cachedContent = get(key);
    return cachedContent != null ? cachedContent.getMetadata() : DefaultContentMetadata.EMPTY;
  }

  private CachedContent addNew(String key) {
    int id = getNewId(idToKey);
    CachedContent cachedContent = new CachedContent(id, key);
    keyToContent.put(key, cachedContent);
    idToKey.put(id, key);
    newIds.put(id, true);
    storage.onUpdate(cachedContent);
    return cachedContent;
  }

  @SuppressLint("GetInstance") // Suppress warning about specifying "BC" as an explicit provider.
  private static Cipher getCipher() throws NoSuchPaddingException, NoSuchAlgorithmException {
    // Workaround for https://issuetracker.google.com/issues/36976726
    if (Util.SDK_INT == 18) {
      try {
        return Cipher.getInstance("AES/CBC/PKCS5PADDING", "BC");
      } catch (Throwable ignored) {
        // ignored
      }
    }
    return Cipher.getInstance("AES/CBC/PKCS5PADDING");
  }

  /**
   * Returns an id which isn't used in the given array. If the maximum id in the array is smaller
   * than {@link java.lang.Integer#MAX_VALUE} it just returns the next bigger integer. Otherwise it
   * returns the smallest unused non-negative integer.
   */
  @VisibleForTesting
  /* package */ static int getNewId(SparseArray<String> idToKey) {
    int size = idToKey.size();
    int id = size == 0 ? 0 : (idToKey.keyAt(size - 1) + 1);
    if (id < 0) { // In case if we pass max int value.
      // TODO optimization: defragmentation or binary search?
      for (id = 0; id < size; id++) {
        if (id != idToKey.keyAt(id)) {
          break;
        }
      }
    }
    return id;
  }

  /**
   * Deserializes a {@link DefaultContentMetadata} from the given input stream.
   *
   * @param input Input stream to read from.
   * @return a {@link DefaultContentMetadata} instance.
   * @throws IOException If an error occurs during reading from the input.
   */
  private static DefaultContentMetadata readContentMetadata(DataInputStream input)
      throws IOException {
    int size = input.readInt();
    HashMap<String, byte[]> metadata = new HashMap<>();
    for (int i = 0; i < size; i++) {
      String name = input.readUTF();
      int valueSize = input.readInt();
      if (valueSize < 0) {
        throw new IOException("Invalid value size: " + valueSize);
      }
      // Grow the array incrementally to avoid OutOfMemoryError in the case that a corrupt (and very
      // large) valueSize was read. In such cases the implementation below is expected to throw
      // IOException from one of the readFully calls, due to the end of the input being reached.
      int bytesRead = 0;
      int nextBytesToRead = Math.min(valueSize, INCREMENTAL_METADATA_READ_LENGTH);
      byte[] value = Util.EMPTY_BYTE_ARRAY;
      while (bytesRead != valueSize) {
        value = Arrays.copyOf(value, bytesRead + nextBytesToRead);
        input.readFully(value, bytesRead, nextBytesToRead);
        bytesRead += nextBytesToRead;
        nextBytesToRead = Math.min(valueSize - bytesRead, INCREMENTAL_METADATA_READ_LENGTH);
      }
      metadata.put(name, value);
    }
    return new DefaultContentMetadata(metadata);
  }

  /**
   * Serializes itself to a {@link DataOutputStream}.
   *
   * @param output Output stream to store the values.
   * @throws IOException If an error occurs writing to the output.
   */
  private static void writeContentMetadata(DefaultContentMetadata metadata, DataOutputStream output)
      throws IOException {
    Set<Map.Entry<String, byte[]>> entrySet = metadata.entrySet();
    output.writeInt(entrySet.size());
    for (Map.Entry<String, byte[]> entry : entrySet) {
      output.writeUTF(entry.getKey());
      byte[] value = entry.getValue();
      output.writeInt(value.length);
      output.write(value);
    }
  }

  /** Interface for the persistent index. */
  private interface Storage {

    /** Initializes the storage for the given cache UID. */
    void initialize(long uid);

    /**
     * Returns whether the persisted index exists.
     *
     * @throws IOException If an error occurs determining whether the persisted index exists.
     */
    boolean exists() throws IOException;

    /**
     * Deletes the persisted index.
     *
     * @throws IOException If an error occurs deleting the index.
     */
    void delete() throws IOException;

    /**
     * Loads the persisted index into {@code content} and {@code idToKey}, creating it if it doesn't
     * already exist.
     *
     * <p>If the persisted index is in a permanently bad state (i.e. all further attempts to load it
     * are also expected to fail) then it will be deleted and the call will return successfully. For
     * transient failures, {@link IOException} will be thrown.
     *
     * @param content The key to content map to populate with persisted data.
     * @param idToKey The id to key map to populate with persisted data.
     * @throws IOException If an error occurs loading the index.
     */
    void load(HashMap<String, CachedContent> content, SparseArray<@NullableType String> idToKey)
        throws IOException;

    /**
     * Writes the persisted index, creating it if it doesn't already exist and replacing any
     * existing content if it does.
     *
     * @param content The key to content map to persist.
     * @throws IOException If an error occurs persisting the index.
     */
    void storeFully(HashMap<String, CachedContent> content) throws IOException;

    /**
     * Ensures incremental changes to the index since the initial {@link #initialize(long)} or last
     * {@link #storeFully(HashMap)} are persisted. The storage will have been notified of all such
     * changes via {@link #onUpdate(CachedContent)} and {@link #onRemove(CachedContent, boolean)}.
     *
     * @param content The key to content map to persist.
     * @throws IOException If an error occurs persisting the index.
     */
    void storeIncremental(HashMap<String, CachedContent> content) throws IOException;

    /**
     * Called when a {@link CachedContent} is added or updated.
     *
     * @param cachedContent The updated {@link CachedContent}.
     */
    void onUpdate(CachedContent cachedContent);

    /**
     * Called when a {@link CachedContent} is removed.
     *
     * @param cachedContent The removed {@link CachedContent}.
     * @param neverStored True if the {@link CachedContent} was added more recently than when the
     *     index was last stored.
     */
    void onRemove(CachedContent cachedContent, boolean neverStored);
  }

  /** {@link Storage} implementation that uses an {@link AtomicFile}. */
  private static class LegacyStorage implements Storage {

    private static final int VERSION = 2;
    private static final int VERSION_METADATA_INTRODUCED = 2;
    private static final int FLAG_ENCRYPTED_INDEX = 1;

    private final boolean encrypt;
    @Nullable private final Cipher cipher;
    @Nullable private final SecretKeySpec secretKeySpec;
    @Nullable private final Random random;
    private final AtomicFile atomicFile;

    private boolean changed;
    @Nullable private ReusableBufferedOutputStream bufferedOutputStream;

    public LegacyStorage(File file, @Nullable byte[] secretKey, boolean encrypt) {
      Cipher cipher = null;
      SecretKeySpec secretKeySpec = null;
      if (secretKey != null) {
        Assertions.checkArgument(secretKey.length == 16);
        try {
          cipher = getCipher();
          secretKeySpec = new SecretKeySpec(secretKey, "AES");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
          throw new IllegalStateException(e); // Should never happen.
        }
      } else {
        Assertions.checkArgument(!encrypt);
      }
      this.encrypt = encrypt;
      this.cipher = cipher;
      this.secretKeySpec = secretKeySpec;
      random = encrypt ? new Random() : null;
      atomicFile = new AtomicFile(file);
    }

    @Override
    public void initialize(long uid) {
      // Do nothing. Legacy storage uses a separate file for each cache.
    }

    @Override
    public boolean exists() {
      return atomicFile.exists();
    }

    @Override
    public void delete() {
      atomicFile.delete();
    }

    @Override
    public void load(
        HashMap<String, CachedContent> content, SparseArray<@NullableType String> idToKey) {
      Assertions.checkState(!changed);
      if (!readFile(content, idToKey)) {
        content.clear();
        idToKey.clear();
        atomicFile.delete();
      }
    }

    @Override
    public void storeFully(HashMap<String, CachedContent> content) throws IOException {
      writeFile(content);
      changed = false;
    }

    @Override
    public void storeIncremental(HashMap<String, CachedContent> content) throws IOException {
      if (!changed) {
        return;
      }
      storeFully(content);
    }

    @Override
    public void onUpdate(CachedContent cachedContent) {
      changed = true;
    }

    @Override
    public void onRemove(CachedContent cachedContent, boolean neverStored) {
      changed = true;
    }

    private boolean readFile(
        HashMap<String, CachedContent> content, SparseArray<@NullableType String> idToKey) {
      if (!atomicFile.exists()) {
        return true;
      }

      DataInputStream input = null;
      try {
        InputStream inputStream = new BufferedInputStream(atomicFile.openRead());
        input = new DataInputStream(inputStream);
        int version = input.readInt();
        if (version < 0 || version > VERSION) {
          return false;
        }

        int flags = input.readInt();
        if ((flags & FLAG_ENCRYPTED_INDEX) != 0) {
          if (cipher == null) {
            return false;
          }
          byte[] initializationVector = new byte[16];
          input.readFully(initializationVector);
          IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);
          try {
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
          } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(e);
          }
          input = new DataInputStream(new CipherInputStream(inputStream, cipher));
        } else if (encrypt) {
          changed = true; // Force index to be rewritten encrypted after read.
        }

        int count = input.readInt();
        int hashCode = 0;
        for (int i = 0; i < count; i++) {
          CachedContent cachedContent = readCachedContent(version, input);
          content.put(cachedContent.key, cachedContent);
          idToKey.put(cachedContent.id, cachedContent.key);
          hashCode += hashCachedContent(cachedContent, version);
        }
        int fileHashCode = input.readInt();
        boolean isEOF = input.read() == -1;
        if (fileHashCode != hashCode || !isEOF) {
          return false;
        }
      } catch (IOException e) {
        return false;
      } finally {
        if (input != null) {
          Util.closeQuietly(input);
        }
      }
      return true;
    }

    private void writeFile(HashMap<String, CachedContent> content) throws IOException {
      DataOutputStream output = null;
      try {
        OutputStream outputStream = atomicFile.startWrite();
        if (bufferedOutputStream == null) {
          bufferedOutputStream = new ReusableBufferedOutputStream(outputStream);
        } else {
          bufferedOutputStream.reset(outputStream);
        }
        output = new DataOutputStream(bufferedOutputStream);
        output.writeInt(VERSION);

        int flags = encrypt ? FLAG_ENCRYPTED_INDEX : 0;
        output.writeInt(flags);

        if (encrypt) {
          byte[] initializationVector = new byte[16];
          random.nextBytes(initializationVector);
          output.write(initializationVector);
          IvParameterSpec ivParameterSpec = new IvParameterSpec(initializationVector);
          try {
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
          } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IllegalStateException(e); // Should never happen.
          }
          output.flush();
          output = new DataOutputStream(new CipherOutputStream(bufferedOutputStream, cipher));
        }

        output.writeInt(content.size());
        int hashCode = 0;
        for (CachedContent cachedContent : content.values()) {
          writeCachedContent(cachedContent, output);
          hashCode += hashCachedContent(cachedContent, VERSION);
        }
        output.writeInt(hashCode);
        atomicFile.endWrite(output);
        // Avoid calling close twice. Duplicate CipherOutputStream.close calls did
        // not used to be no-ops: https://android-review.googlesource.com/#/c/272799/
        output = null;
      } finally {
        Util.closeQuietly(output);
      }
    }

    /**
     * Calculates a hash code for a {@link CachedContent} which is compatible with a particular
     * index version.
     */
    private int hashCachedContent(CachedContent cachedContent, int version) {
      int result = cachedContent.id;
      result = 31 * result + cachedContent.key.hashCode();
      if (version < VERSION_METADATA_INTRODUCED) {
        long length = ContentMetadata.getContentLength(cachedContent.getMetadata());
        result = 31 * result + (int) (length ^ (length >>> 32));
      } else {
        result = 31 * result + cachedContent.getMetadata().hashCode();
      }
      return result;
    }

    /**
     * Reads a {@link CachedContent} from a {@link DataInputStream}.
     *
     * @param version Version of the encoded data.
     * @param input Input stream containing values needed to initialize CachedContent instance.
     * @throws IOException If an error occurs during reading values.
     */
    private CachedContent readCachedContent(int version, DataInputStream input) throws IOException {
      int id = input.readInt();
      String key = input.readUTF();
      DefaultContentMetadata metadata;
      if (version < VERSION_METADATA_INTRODUCED) {
        long length = input.readLong();
        ContentMetadataMutations mutations = new ContentMetadataMutations();
        ContentMetadataMutations.setContentLength(mutations, length);
        metadata = DefaultContentMetadata.EMPTY.copyWithMutationsApplied(mutations);
      } else {
        metadata = readContentMetadata(input);
      }
      return new CachedContent(id, key, metadata);
    }

    /**
     * Writes a {@link CachedContent} to a {@link DataOutputStream}.
     *
     * @param output Output stream to store the values.
     * @throws IOException If an error occurs during writing values to output.
     */
    private void writeCachedContent(CachedContent cachedContent, DataOutputStream output)
        throws IOException {
      output.writeInt(cachedContent.id);
      output.writeUTF(cachedContent.key);
      writeContentMetadata(cachedContent.getMetadata(), output);
    }
  }

  /** {@link Storage} implementation that uses an SQL database. */
  private static final class DatabaseStorage implements Storage {

    private static final String TABLE_PREFIX = DatabaseProvider.TABLE_PREFIX + "CacheIndex";
    private static final int TABLE_VERSION = 1;

    private static final String COLUMN_ID = "id";
    private static final String COLUMN_KEY = "key";
    private static final String COLUMN_METADATA = "metadata";

    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_KEY = 1;
    private static final int COLUMN_INDEX_METADATA = 2;

    private static final String WHERE_ID_EQUALS = COLUMN_ID + " = ?";

    private static final String[] COLUMNS = new String[] {COLUMN_ID, COLUMN_KEY, COLUMN_METADATA};
    private static final String TABLE_SCHEMA =
        "("
            + COLUMN_ID
            + " INTEGER PRIMARY KEY NOT NULL,"
            + COLUMN_KEY
            + " TEXT NOT NULL,"
            + COLUMN_METADATA
            + " BLOB NOT NULL)";

    private final DatabaseProvider databaseProvider;
    private final SparseArray<CachedContent> pendingUpdates;

    private String hexUid;
    private String tableName;

    public static void delete(DatabaseProvider databaseProvider, long uid)
        throws DatabaseIOException {
      delete(databaseProvider, Long.toHexString(uid));
    }

    public DatabaseStorage(DatabaseProvider databaseProvider) {
      this.databaseProvider = databaseProvider;
      pendingUpdates = new SparseArray<>();
    }

    @Override
    public void initialize(long uid) {
      hexUid = Long.toHexString(uid);
      tableName = getTableName(hexUid);
    }

    @Override
    public boolean exists() throws DatabaseIOException {
      return VersionTable.getVersion(
              databaseProvider.getReadableDatabase(),
              VersionTable.FEATURE_CACHE_CONTENT_METADATA,
              hexUid)
          != VersionTable.VERSION_UNSET;
    }

    @Override
    public void delete() throws DatabaseIOException {
      delete(databaseProvider, hexUid);
    }

    @Override
    public void load(
        HashMap<String, CachedContent> content, SparseArray<@NullableType String> idToKey)
        throws IOException {
      Assertions.checkState(pendingUpdates.size() == 0);
      try {
        int version =
            VersionTable.getVersion(
                databaseProvider.getReadableDatabase(),
                VersionTable.FEATURE_CACHE_CONTENT_METADATA,
                hexUid);
        if (version != TABLE_VERSION) {
          SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
          writableDatabase.beginTransaction();
          try {
            initializeTable(writableDatabase);
            writableDatabase.setTransactionSuccessful();
          } finally {
            writableDatabase.endTransaction();
          }
        }

        try (Cursor cursor = getCursor()) {
          while (cursor.moveToNext()) {
            int id = cursor.getInt(COLUMN_INDEX_ID);
            String key = cursor.getString(COLUMN_INDEX_KEY);
            byte[] metadataBytes = cursor.getBlob(COLUMN_INDEX_METADATA);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(metadataBytes);
            DataInputStream input = new DataInputStream(inputStream);
            DefaultContentMetadata metadata = readContentMetadata(input);

            CachedContent cachedContent = new CachedContent(id, key, metadata);
            content.put(cachedContent.key, cachedContent);
            idToKey.put(cachedContent.id, cachedContent.key);
          }
        }
      } catch (SQLiteException e) {
        content.clear();
        idToKey.clear();
        throw new DatabaseIOException(e);
      }
    }

    @Override
    public void storeFully(HashMap<String, CachedContent> content) throws IOException {
      try {
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          initializeTable(writableDatabase);
          for (CachedContent cachedContent : content.values()) {
            addOrUpdateRow(writableDatabase, cachedContent);
          }
          writableDatabase.setTransactionSuccessful();
          pendingUpdates.clear();
        } finally {
          writableDatabase.endTransaction();
        }
      } catch (SQLException e) {
        throw new DatabaseIOException(e);
      }
    }

    @Override
    public void storeIncremental(HashMap<String, CachedContent> content) throws IOException {
      if (pendingUpdates.size() == 0) {
        return;
      }
      try {
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          for (int i = 0; i < pendingUpdates.size(); i++) {
            CachedContent cachedContent = pendingUpdates.valueAt(i);
            if (cachedContent == null) {
              deleteRow(writableDatabase, pendingUpdates.keyAt(i));
            } else {
              addOrUpdateRow(writableDatabase, cachedContent);
            }
          }
          writableDatabase.setTransactionSuccessful();
          pendingUpdates.clear();
        } finally {
          writableDatabase.endTransaction();
        }
      } catch (SQLException e) {
        throw new DatabaseIOException(e);
      }
    }

    @Override
    public void onUpdate(CachedContent cachedContent) {
      pendingUpdates.put(cachedContent.id, cachedContent);
    }

    @Override
    public void onRemove(CachedContent cachedContent, boolean neverStored) {
      if (neverStored) {
        pendingUpdates.delete(cachedContent.id);
      } else {
        pendingUpdates.put(cachedContent.id, null);
      }
    }

    private Cursor getCursor() {
      return databaseProvider
          .getReadableDatabase()
          .query(
              tableName,
              COLUMNS,
              /* selection= */ null,
              /* selectionArgs= */ null,
              /* groupBy= */ null,
              /* having= */ null,
              /* orderBy= */ null);
    }

    private void initializeTable(SQLiteDatabase writableDatabase) throws DatabaseIOException {
      VersionTable.setVersion(
          writableDatabase, VersionTable.FEATURE_CACHE_CONTENT_METADATA, hexUid, TABLE_VERSION);
      dropTable(writableDatabase, tableName);
      writableDatabase.execSQL("CREATE TABLE " + tableName + " " + TABLE_SCHEMA);
    }

    private void deleteRow(SQLiteDatabase writableDatabase, int key) {
      writableDatabase.delete(tableName, WHERE_ID_EQUALS, new String[] {Integer.toString(key)});
    }

    private void addOrUpdateRow(SQLiteDatabase writableDatabase, CachedContent cachedContent)
        throws IOException {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      writeContentMetadata(cachedContent.getMetadata(), new DataOutputStream(outputStream));
      byte[] data = outputStream.toByteArray();

      ContentValues values = new ContentValues();
      values.put(COLUMN_ID, cachedContent.id);
      values.put(COLUMN_KEY, cachedContent.key);
      values.put(COLUMN_METADATA, data);
      writableDatabase.replaceOrThrow(tableName, /* nullColumnHack= */ null, values);
    }

    private static void delete(DatabaseProvider databaseProvider, String hexUid)
        throws DatabaseIOException {
      try {
        String tableName = getTableName(hexUid);
        SQLiteDatabase writableDatabase = databaseProvider.getWritableDatabase();
        writableDatabase.beginTransaction();
        try {
          VersionTable.removeVersion(
              writableDatabase, VersionTable.FEATURE_CACHE_CONTENT_METADATA, hexUid);
          dropTable(writableDatabase, tableName);
          writableDatabase.setTransactionSuccessful();
        } finally {
          writableDatabase.endTransaction();
        }
      } catch (SQLException e) {
        throw new DatabaseIOException(e);
      }
    }

    private static void dropTable(SQLiteDatabase writableDatabase, String tableName) {
      writableDatabase.execSQL("DROP TABLE IF EXISTS " + tableName);
    }

    private static String getTableName(String hexUid) {
      return TABLE_PREFIX + hexUid;
    }
  }
}
