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
package com.google.android.exoplayer2.extractor.flv;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Script Data tags from an FLV stream and extracts metadata information.
 */
/* package */ final class ScriptTagPayloadReader extends TagPayloadReader {

  private static final String NAME_METADATA = "onMetaData";
  private static final String KEY_DURATION = "duration";

  // AMF object types
  private static final int AMF_TYPE_NUMBER = 0;
  private static final int AMF_TYPE_BOOLEAN = 1;
  private static final int AMF_TYPE_STRING = 2;
  private static final int AMF_TYPE_OBJECT = 3;
  private static final int AMF_TYPE_ECMA_ARRAY = 8;
  private static final int AMF_TYPE_END_MARKER = 9;
  private static final int AMF_TYPE_STRICT_ARRAY = 10;
  private static final int AMF_TYPE_DATE = 11;

  private long durationUs;

  public ScriptTagPayloadReader() {
    super(new DummyTrackOutput());
    durationUs = C.TIME_UNSET;
  }

  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public void seek() {
    // Do nothing.
  }

  @Override
  protected boolean parseHeader(ParsableByteArray data) {
    return true;
  }

  @Override
  protected boolean parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
    int nameType = readAmfType(data);
    if (nameType != AMF_TYPE_STRING) {
      // Should never happen.
      throw new ParserException();
    }
    String name = readAmfString(data);
    if (!NAME_METADATA.equals(name)) {
      // We're only interested in metadata.
      return false;
    }
    int type = readAmfType(data);
    if (type != AMF_TYPE_ECMA_ARRAY) {
      // We're not interested in this metadata.
      return false;
    }
    // Set the duration to the value contained in the metadata, if present.
    Map<String, Object> metadata = readAmfEcmaArray(data);
    if (metadata.containsKey(KEY_DURATION)) {
      double durationSeconds = (double) metadata.get(KEY_DURATION);
      if (durationSeconds > 0.0) {
        durationUs = (long) (durationSeconds * C.MICROS_PER_SECOND);
      }
    }
    return false;
  }

  private static int readAmfType(ParsableByteArray data) {
    return data.readUnsignedByte();
  }

  /**
   * Read a boolean from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static Boolean readAmfBoolean(ParsableByteArray data) {
    return data.readUnsignedByte() == 1;
  }

  /**
   * Read a double number from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static Double readAmfDouble(ParsableByteArray data) {
    return Double.longBitsToDouble(data.readLong());
  }

  /**
   * Read a string from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static String readAmfString(ParsableByteArray data) {
    int size = data.readUnsignedShort();
    int position = data.getPosition();
    data.skipBytes(size);
    return new String(data.data, position, size);
  }

  /**
   * Read an array from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static ArrayList<Object> readAmfStrictArray(ParsableByteArray data) {
    int count = data.readUnsignedIntToInt();
    ArrayList<Object> list = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      int type = readAmfType(data);
      Object value = readAmfData(data, type);
      if (value != null) {
        list.add(value);
      }
    }
    return list;
  }

  /**
   * Read an object from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static HashMap<String, Object> readAmfObject(ParsableByteArray data) {
    HashMap<String, Object> array = new HashMap<>();
    while (true) {
      String key = readAmfString(data);
      int type = readAmfType(data);
      if (type == AMF_TYPE_END_MARKER) {
        break;
      }
      Object value = readAmfData(data, type);
      if (value != null) {
        array.put(key, value);
      }
    }
    return array;
  }

  /**
   * Read an ECMA array from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static HashMap<String, Object> readAmfEcmaArray(ParsableByteArray data) {
    int count = data.readUnsignedIntToInt();
    HashMap<String, Object> array = new HashMap<>(count);
    for (int i = 0; i < count; i++) {
      String key = readAmfString(data);
      int type = readAmfType(data);
      Object value = readAmfData(data, type);
      if (value != null) {
        array.put(key, value);
      }
    }
    return array;
  }

  /**
   * Read a date from an AMF encoded buffer.
   *
   * @param data The buffer from which to read.
   * @return The value read from the buffer.
   */
  private static Date readAmfDate(ParsableByteArray data) {
    Date date = new Date((long) readAmfDouble(data).doubleValue());
    data.skipBytes(2); // Skip reserved bytes.
    return date;
  }

  @Nullable
  private static Object readAmfData(ParsableByteArray data, int type) {
    switch (type) {
      case AMF_TYPE_NUMBER:
        return readAmfDouble(data);
      case AMF_TYPE_BOOLEAN:
        return readAmfBoolean(data);
      case AMF_TYPE_STRING:
        return readAmfString(data);
      case AMF_TYPE_OBJECT:
        return readAmfObject(data);
      case AMF_TYPE_ECMA_ARRAY:
        return readAmfEcmaArray(data);
      case AMF_TYPE_STRICT_ARRAY:
        return readAmfStrictArray(data);
      case AMF_TYPE_DATE:
        return readAmfDate(data);
      default:
        // We don't log a warning because there are types that we knowingly don't support.
        return null;
    }
  }
}
