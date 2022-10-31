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

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.StereoMode;
import com.google.android.exoplayer2.util.Assertions;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** The projection mesh used with 360/VR videos. */
public final class Projection {

  /** Enforces allowed (sub) mesh draw modes. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({DRAW_MODE_TRIANGLES, DRAW_MODE_TRIANGLES_STRIP, DRAW_MODE_TRIANGLES_FAN})
  public @interface DrawMode {}
  /** Triangle draw mode. */
  public static final int DRAW_MODE_TRIANGLES = 0;
  /** Triangle strip draw mode. */
  public static final int DRAW_MODE_TRIANGLES_STRIP = 1;
  /** Triangle fan draw mode. */
  public static final int DRAW_MODE_TRIANGLES_FAN = 2;

  /** Number of position coordinates per vertex. */
  public static final int TEXTURE_COORDS_PER_VERTEX = 2;
  /** Number of texture coordinates per vertex. */
  public static final int POSITION_COORDS_PER_VERTEX = 3;

  /**
   * Generates a complete sphere equirectangular projection.
   *
   * @param stereoMode A {@link C.StereoMode} value.
   */
  public static Projection createEquirectangular(@C.StereoMode int stereoMode) {
    return createEquirectangular(
        /* radius= */ 50, // Should be large enough that there are no stereo artifacts.
        /* latitudes= */ 36, // Should be large enough to prevent videos looking wavy.
        /* longitudes= */ 72, // Should be large enough to prevent videos looking wavy.
        /* verticalFovDegrees= */ 180,
        /* horizontalFovDegrees= */ 360,
        stereoMode);
  }

  /**
   * Generates an equirectangular projection.
   *
   * @param radius Size of the sphere. Must be &gt; 0.
   * @param latitudes Number of rows that make up the sphere. Must be &gt;= 1.
   * @param longitudes Number of columns that make up the sphere. Must be &gt;= 1.
   * @param verticalFovDegrees Total latitudinal degrees that are covered by the sphere. Must be in
   *     (0, 180].
   * @param horizontalFovDegrees Total longitudinal degrees that are covered by the sphere.Must be
   *     in (0, 360].
   * @param stereoMode A {@link C.StereoMode} value.
   * @return an equirectangular projection.
   */
  public static Projection createEquirectangular(
      float radius,
      int latitudes,
      int longitudes,
      float verticalFovDegrees,
      float horizontalFovDegrees,
      @C.StereoMode int stereoMode) {
    Assertions.checkArgument(radius > 0);
    Assertions.checkArgument(latitudes >= 1);
    Assertions.checkArgument(longitudes >= 1);
    Assertions.checkArgument(verticalFovDegrees > 0 && verticalFovDegrees <= 180);
    Assertions.checkArgument(horizontalFovDegrees > 0 && horizontalFovDegrees <= 360);

    // Compute angular size in radians of each UV quad.
    float verticalFovRads = (float) Math.toRadians(verticalFovDegrees);
    float horizontalFovRads = (float) Math.toRadians(horizontalFovDegrees);
    float quadHeightRads = verticalFovRads / latitudes;
    float quadWidthRads = horizontalFovRads / longitudes;

    // Each latitude strip has 2 * (longitudes quads + extra edge) vertices + 2 degenerate vertices.
    int vertexCount = (2 * (longitudes + 1) + 2) * latitudes;
    // Buffer to return.
    float[] vertexData = new float[vertexCount * POSITION_COORDS_PER_VERTEX];
    float[] textureData = new float[vertexCount * TEXTURE_COORDS_PER_VERTEX];

    // Generate the data for the sphere which is a set of triangle strips representing each
    // latitude band.
    int vOffset = 0; // Offset into the vertexData array.
    int tOffset = 0; // Offset into the textureData array.
    // (i, j) represents a quad in the equirectangular sphere.
    for (int j = 0; j < latitudes; ++j) { // For each horizontal triangle strip.
      // Each latitude band lies between the two phi values. Each vertical edge on a band lies on
      // a theta value.
      float phiLow = quadHeightRads * j - verticalFovRads / 2;
      float phiHigh = quadHeightRads * (j + 1) - verticalFovRads / 2;

      for (int i = 0; i < longitudes + 1; ++i) { // For each vertical edge in the band.
        for (int k = 0; k < 2; ++k) { // For low and high points on an edge.
          // For each point, determine it's position in polar coordinates.
          float phi = k == 0 ? phiLow : phiHigh;
          float theta = quadWidthRads * i + (float) Math.PI - horizontalFovRads / 2;

          // Set vertex position data as Cartesian coordinates.
          vertexData[vOffset++] = -(float) (radius * Math.sin(theta) * Math.cos(phi));
          vertexData[vOffset++] = (float) (radius * Math.sin(phi));
          vertexData[vOffset++] = (float) (radius * Math.cos(theta) * Math.cos(phi));

          textureData[tOffset++] = i * quadWidthRads / horizontalFovRads;
          textureData[tOffset++] = (j + k) * quadHeightRads / verticalFovRads;

          // Break up the triangle strip with degenerate vertices by copying first and last points.
          if ((i == 0 && k == 0) || (i == longitudes && k == 1)) {
            System.arraycopy(
                vertexData,
                vOffset - POSITION_COORDS_PER_VERTEX,
                vertexData,
                vOffset,
                POSITION_COORDS_PER_VERTEX);
            vOffset += POSITION_COORDS_PER_VERTEX;
            System.arraycopy(
                textureData,
                tOffset - TEXTURE_COORDS_PER_VERTEX,
                textureData,
                tOffset,
                TEXTURE_COORDS_PER_VERTEX);
            tOffset += TEXTURE_COORDS_PER_VERTEX;
          }
        }
        // Move on to the next vertical edge in the triangle strip.
      }
      // Move on to the next triangle strip.
    }
    SubMesh subMesh =
        new SubMesh(SubMesh.VIDEO_TEXTURE_ID, vertexData, textureData, DRAW_MODE_TRIANGLES_STRIP);
    return new Projection(new Mesh(subMesh), stereoMode);
  }

  /** The Mesh corresponding to the left eye. */
  public final Mesh leftMesh;
  /**
   * The Mesh corresponding to the right eye. If {@code singleMesh} is true then this mesh is
   * identical to {@link #leftMesh}.
   */
  public final Mesh rightMesh;
  /** The stereo mode. */
  public final @StereoMode int stereoMode;
  /** Whether the left and right mesh are identical. */
  public final boolean singleMesh;

  /**
   * Creates a Projection with single mesh.
   *
   * @param mesh the Mesh for both eyes.
   * @param stereoMode A {@link StereoMode} value.
   */
  public Projection(Mesh mesh, int stereoMode) {
    this(mesh, mesh, stereoMode);
  }

  /**
   * Creates a Projection with dual mesh. Use {@link #Projection(Mesh, int)} if there is single mesh
   * for both eyes.
   *
   * @param leftMesh the Mesh corresponding to the left eye.
   * @param rightMesh the Mesh corresponding to the right eye.
   * @param stereoMode A {@link C.StereoMode} value.
   */
  public Projection(Mesh leftMesh, Mesh rightMesh, int stereoMode) {
    this.leftMesh = leftMesh;
    this.rightMesh = rightMesh;
    this.stereoMode = stereoMode;
    this.singleMesh = leftMesh == rightMesh;
  }

  /** The sub mesh associated with the {@link Mesh}. */
  public static final class SubMesh {
    /** Texture ID for video frames. */
    public static final int VIDEO_TEXTURE_ID = 0;

    /** Texture ID. */
    public final int textureId;
    /** The drawing mode. One of {@link DrawMode}. */
    public final @DrawMode int mode;
    /** The SubMesh vertices. */
    public final float[] vertices;
    /** The SubMesh texture coordinates. */
    public final float[] textureCoords;

    public SubMesh(int textureId, float[] vertices, float[] textureCoords, @DrawMode int mode) {
      this.textureId = textureId;
      Assertions.checkArgument(
          vertices.length * (long) TEXTURE_COORDS_PER_VERTEX
              == textureCoords.length * (long) POSITION_COORDS_PER_VERTEX);
      this.vertices = vertices;
      this.textureCoords = textureCoords;
      this.mode = mode;
    }

    /** Returns the SubMesh vertex count. */
    public int getVertexCount() {
      return vertices.length / POSITION_COORDS_PER_VERTEX;
    }
  }

  /** A Mesh associated with the projection scene. */
  public static final class Mesh {
    private final SubMesh[] subMeshes;

    public Mesh(SubMesh... subMeshes) {
      this.subMeshes = subMeshes;
    }

    /** Returns the number of sub meshes. */
    public int getSubMeshCount() {
      return subMeshes.length;
    }

    /** Returns the SubMesh for the given index. */
    public SubMesh getSubMesh(int index) {
      return subMeshes[index];
    }
  }
}
