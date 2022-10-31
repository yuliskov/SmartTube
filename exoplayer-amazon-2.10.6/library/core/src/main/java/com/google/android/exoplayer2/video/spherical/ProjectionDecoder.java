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
package com.google.android.exoplayer2.video.spherical;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.spherical.Projection.Mesh;
import com.google.android.exoplayer2.video.spherical.Projection.SubMesh;
import java.util.ArrayList;
import java.util.zip.Inflater;

/**
 * A decoder for the projection mesh.
 *
 * <p>The mesh boxes parsed are described at <a
 * href="https://github.com/google/spatial-media/blob/master/docs/spherical-video-v2-rfc.md">
 * Spherical Video V2 RFC</a>.
 *
 * <p>The decoder does not perform CRC checks at the moment.
 */
public final class ProjectionDecoder {

  private static final int TYPE_YTMP = Util.getIntegerCodeForString("ytmp");
  private static final int TYPE_MSHP = Util.getIntegerCodeForString("mshp");
  private static final int TYPE_RAW = Util.getIntegerCodeForString("raw ");
  private static final int TYPE_DFL8 = Util.getIntegerCodeForString("dfl8");
  private static final int TYPE_MESH = Util.getIntegerCodeForString("mesh");
  private static final int TYPE_PROJ = Util.getIntegerCodeForString("proj");

  // Sanity limits to prevent a bad file from creating an OOM situation. We don't expect a mesh to
  // exceed these limits.
  private static final int MAX_COORDINATE_COUNT = 10000;
  private static final int MAX_VERTEX_COUNT = 32 * 1000;
  private static final int MAX_TRIANGLE_INDICES = 128 * 1000;

  private ProjectionDecoder() {}

  /*
   * Decodes the projection data.
   *
   * @param projectionData The projection data.
   * @param stereoMode A {@link C.StereoMode} value.
   * @return The projection or null if the data can't be decoded.
   */
  public static @Nullable Projection decode(byte[] projectionData, @C.StereoMode int stereoMode) {
    ParsableByteArray input = new ParsableByteArray(projectionData);
    // MP4 containers include the proj box but webm containers do not.
    // Both containers use mshp.
    ArrayList<Mesh> meshes = null;
    try {
      meshes = isProj(input) ? parseProj(input) : parseMshp(input);
    } catch (ArrayIndexOutOfBoundsException ignored) {
      // Do nothing.
    }
    if (meshes == null) {
      return null;
    } else {
      switch (meshes.size()) {
        case 1:
          return new Projection(meshes.get(0), stereoMode);
        case 2:
          return new Projection(meshes.get(0), meshes.get(1), stereoMode);
        case 0:
        default:
          return null;
      }
    }
  }

  /** Returns true if the input contains a proj box. Indicates MP4 container. */
  private static boolean isProj(ParsableByteArray input) {
    input.skipBytes(4); // size
    int type = input.readInt();
    input.setPosition(0);
    return type == TYPE_PROJ;
  }

  private static @Nullable ArrayList<Mesh> parseProj(ParsableByteArray input) {
    input.skipBytes(8); // size and type.
    int position = input.getPosition();
    int limit = input.limit();
    while (position < limit) {
      int childEnd = position + input.readInt();
      if (childEnd <= position || childEnd > limit) {
        return null;
      }
      int childAtomType = input.readInt();
      // Some early files named the atom ytmp rather than mshp.
      if (childAtomType == TYPE_YTMP || childAtomType == TYPE_MSHP) {
        input.setLimit(childEnd);
        return parseMshp(input);
      }
      position = childEnd;
      input.setPosition(position);
    }
    return null;
  }

  private static @Nullable ArrayList<Mesh> parseMshp(ParsableByteArray input) {
    int version = input.readUnsignedByte();
    if (version != 0) {
      return null;
    }
    input.skipBytes(7); // flags + crc.
    int encoding = input.readInt();
    if (encoding == TYPE_DFL8) {
      ParsableByteArray output = new ParsableByteArray();
      Inflater inflater = new Inflater(true);
      try {
        if (!Util.inflate(input, output, inflater)) {
          return null;
        }
      } finally {
        inflater.end();
      }
      input = output;
    } else if (encoding != TYPE_RAW) {
      return null;
    }
    return parseRawMshpData(input);
  }

  /** Parses MSHP data after the encoding_four_cc field. */
  private static @Nullable ArrayList<Mesh> parseRawMshpData(ParsableByteArray input) {
    ArrayList<Mesh> meshes = new ArrayList<>();
    int position = input.getPosition();
    int limit = input.limit();
    while (position < limit) {
      int childEnd = position + input.readInt();
      if (childEnd <= position || childEnd > limit) {
        return null;
      }
      int childAtomType = input.readInt();
      if (childAtomType == TYPE_MESH) {
        Mesh mesh = parseMesh(input);
        if (mesh == null) {
          return null;
        }
        meshes.add(mesh);
      }
      position = childEnd;
      input.setPosition(position);
    }
    return meshes;
  }

  private static @Nullable Mesh parseMesh(ParsableByteArray input) {
    // Read the coordinates.
    int coordinateCount = input.readInt();
    if (coordinateCount > MAX_COORDINATE_COUNT) {
      return null;
    }
    float[] coordinates = new float[coordinateCount];
    for (int coordinate = 0; coordinate < coordinateCount; coordinate++) {
      coordinates[coordinate] = input.readFloat();
    }
    // Read the vertices.
    int vertexCount = input.readInt();
    if (vertexCount > MAX_VERTEX_COUNT) {
      return null;
    }

    final double log2 = Math.log(2.0);
    int coordinateCountSizeBits = (int) Math.ceil(Math.log(2.0 * coordinateCount) / log2);

    ParsableBitArray bitInput = new ParsableBitArray(input.data);
    bitInput.setPosition(input.getPosition() * 8);
    float[] vertices = new float[vertexCount * 5];
    int[] coordinateIndices = new int[5];
    int vertexIndex = 0;
    for (int vertex = 0; vertex < vertexCount; vertex++) {
      for (int i = 0; i < 5; i++) {
        int coordinateIndex =
            coordinateIndices[i] + decodeZigZag(bitInput.readBits(coordinateCountSizeBits));
        if (coordinateIndex >= coordinateCount || coordinateIndex < 0) {
          return null;
        }
        vertices[vertexIndex++] = coordinates[coordinateIndex];
        coordinateIndices[i] = coordinateIndex;
      }
    }

    // Pad to next byte boundary
    bitInput.setPosition(((bitInput.getPosition() + 7) & ~7));

    int subMeshCount = bitInput.readBits(32);
    SubMesh[] subMeshes = new SubMesh[subMeshCount];
    for (int i = 0; i < subMeshCount; i++) {
      int textureId = bitInput.readBits(8);
      int drawMode = bitInput.readBits(8);
      int triangleIndexCount = bitInput.readBits(32);
      if (triangleIndexCount > MAX_TRIANGLE_INDICES) {
        return null;
      }
      int vertexCountSizeBits = (int) Math.ceil(Math.log(2.0 * vertexCount) / log2);
      int index = 0;
      float[] triangleVertices = new float[triangleIndexCount * 3];
      float[] textureCoords = new float[triangleIndexCount * 2];
      for (int counter = 0; counter < triangleIndexCount; counter++) {
        index += decodeZigZag(bitInput.readBits(vertexCountSizeBits));
        if (index < 0 || index >= vertexCount) {
          return null;
        }
        triangleVertices[counter * 3] = vertices[index * 5];
        triangleVertices[counter * 3 + 1] = vertices[index * 5 + 1];
        triangleVertices[counter * 3 + 2] = vertices[index * 5 + 2];
        textureCoords[counter * 2] = vertices[index * 5 + 3];
        textureCoords[counter * 2 + 1] = vertices[index * 5 + 4];
      }
      subMeshes[i] = new SubMesh(textureId, triangleVertices, textureCoords, drawMode);
    }
    return new Mesh(subMeshes);
  }

  /**
   * Decodes Zigzag encoding as described in
   * https://developers.google.com/protocol-buffers/docs/encoding#signed-integers
   */
  private static int decodeZigZag(int n) {
    return (n >> 1) ^ -(n & 1);
  }
}
