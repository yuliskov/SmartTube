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
package com.google.android.exoplayer2.extractor.mp4;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Utility methods for handling PSSH atoms.
 */
public final class PsshAtomUtil {

  private static final String TAG = "PsshAtomUtil";

  private PsshAtomUtil() {}

  /**
   * Builds a version 0 PSSH atom for a given system id, containing the given data.
   *
   * @param systemId The system id of the scheme.
   * @param data The scheme specific data.
   * @return The PSSH atom.
   */
  public static byte[] buildPsshAtom(UUID systemId, @Nullable byte[] data) {
    return buildPsshAtom(systemId, null, data);
  }

  /**
   * Builds a PSSH atom for the given system id, containing the given key ids and data.
   *
   * @param systemId The system id of the scheme.
   * @param keyIds The key ids for a version 1 PSSH atom, or null for a version 0 PSSH atom.
   * @param data The scheme specific data.
   * @return The PSSH atom.
   */
  @SuppressWarnings("ParameterNotNullable")
  public static byte[] buildPsshAtom(
      UUID systemId, @Nullable UUID[] keyIds, @Nullable byte[] data) {
    int dataLength = data != null ? data.length : 0;
    int psshBoxLength = Atom.FULL_HEADER_SIZE + 16 /* SystemId */ + 4 /* DataSize */ + dataLength;
    if (keyIds != null) {
      psshBoxLength += 4 /* KID_count */ + (keyIds.length * 16) /* KIDs */;
    }
    ByteBuffer psshBox = ByteBuffer.allocate(psshBoxLength);
    psshBox.putInt(psshBoxLength);
    psshBox.putInt(Atom.TYPE_pssh);
    psshBox.putInt(keyIds != null ? 0x01000000 : 0 /* version=(buildV1Atom ? 1 : 0), flags=0 */);
    psshBox.putLong(systemId.getMostSignificantBits());
    psshBox.putLong(systemId.getLeastSignificantBits());
    if (keyIds != null) {
      psshBox.putInt(keyIds.length);
      for (UUID keyId : keyIds) {
        psshBox.putLong(keyId.getMostSignificantBits());
        psshBox.putLong(keyId.getLeastSignificantBits());
      }
    }
    if (data != null && data.length != 0) {
      psshBox.putInt(data.length);
      psshBox.put(data);
    } // Else the last 4 bytes are a 0 DataSize.
    return psshBox.array();
  }

  /**
   * Returns whether the data is a valid PSSH atom.
   *
   * @param data The data to parse.
   * @return Whether the data is a valid PSSH atom.
   */
  public static boolean isPsshAtom(byte[] data) {
    return parsePsshAtom(data) != null;
  }

  /**
   * Parses the UUID from a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * <p>The UUID is only parsed if the data is a valid PSSH atom.
   *
   * @param atom The atom to parse.
   * @return The parsed UUID. Null if the input is not a valid PSSH atom, or if the PSSH atom has an
   *     unsupported version.
   */
  public static @Nullable UUID parseUuid(byte[] atom) {
    PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return null;
    }
    return parsedAtom.uuid;
  }

  /**
   * Parses the version from a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   * <p>
   * The version is only parsed if the data is a valid PSSH atom.
   *
   * @param atom The atom to parse.
   * @return The parsed version. -1 if the input is not a valid PSSH atom, or if the PSSH atom has
   *     an unsupported version.
   */
  public static int parseVersion(byte[] atom) {
    PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return -1;
    }
    return parsedAtom.version;
  }

  /**
   * Parses the scheme specific data from a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * <p>The scheme specific data is only parsed if the data is a valid PSSH atom matching the given
   * UUID, or if the data is a valid PSSH atom of any type in the case that the passed UUID is null.
   *
   * @param atom The atom to parse.
   * @param uuid The required UUID of the PSSH atom, or null to accept any UUID.
   * @return The parsed scheme specific data. Null if the input is not a valid PSSH atom, or if the
   *     PSSH atom has an unsupported version, or if the PSSH atom does not match the passed UUID.
   */
  public static @Nullable byte[] parseSchemeSpecificData(byte[] atom, UUID uuid) {
    PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return null;
    }
    if (uuid != null && !uuid.equals(parsedAtom.uuid)) {
      Log.w(TAG, "UUID mismatch. Expected: " + uuid + ", got: " + parsedAtom.uuid + ".");
      return null;
    }
    return parsedAtom.schemeData;
  }

  /**
   * Parses a PSSH atom. Version 0 and 1 PSSH atoms are supported.
   *
   * @param atom The atom to parse.
   * @return The parsed PSSH atom. Null if the input is not a valid PSSH atom, or if the PSSH atom
   *     has an unsupported version.
   */
  // TODO: Support parsing of the key ids for version 1 PSSH atoms.
  private static @Nullable PsshAtom parsePsshAtom(byte[] atom) {
    ParsableByteArray atomData = new ParsableByteArray(atom);
    if (atomData.limit() < Atom.FULL_HEADER_SIZE + 16 /* UUID */ + 4 /* DataSize */) {
      // Data too short.
      return null;
    }
    atomData.setPosition(0);
    int atomSize = atomData.readInt();
    if (atomSize != atomData.bytesLeft() + 4) {
      // Not an atom, or incorrect atom size.
      return null;
    }
    int atomType = atomData.readInt();
    if (atomType != Atom.TYPE_pssh) {
      // Not an atom, or incorrect atom type.
      return null;
    }
    int atomVersion = Atom.parseFullAtomVersion(atomData.readInt());
    if (atomVersion > 1) {
      Log.w(TAG, "Unsupported pssh version: " + atomVersion);
      return null;
    }
    UUID uuid = new UUID(atomData.readLong(), atomData.readLong());
    if (atomVersion == 1) {
      int keyIdCount = atomData.readUnsignedIntToInt();
      atomData.skipBytes(16 * keyIdCount);
    }
    int dataSize = atomData.readUnsignedIntToInt();
    if (dataSize != atomData.bytesLeft()) {
      // Incorrect dataSize.
      return null;
    }
    byte[] data = new byte[dataSize];
    atomData.readBytes(data, 0, dataSize);
    return new PsshAtom(uuid, atomVersion, data);
  }

  // TODO: Consider exposing this and making parsePsshAtom public.
  private static class PsshAtom {

    private final UUID uuid;
    private final int version;
    private final byte[] schemeData;

    public PsshAtom(UUID uuid, int version, byte[] schemeData) {
      this.uuid = uuid;
      this.version = version;
      this.schemeData = schemeData;
    }

  }

}
