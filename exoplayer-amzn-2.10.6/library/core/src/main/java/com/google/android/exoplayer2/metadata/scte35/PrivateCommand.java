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
package com.google.android.exoplayer2.metadata.scte35;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.exoplayer2.util.ParsableByteArray;

/**
 * Represents a private command as defined in SCTE35, Section 9.3.6.
 */
public final class PrivateCommand extends SpliceCommand {

  /**
   * The {@code pts_adjustment} as defined in SCTE35, Section 9.2.
   */
  public final long ptsAdjustment;
  /**
   * The identifier as defined in SCTE35, Section 9.3.6.
   */
  public final long identifier;
  /**
   * The private bytes as defined in SCTE35, Section 9.3.6.
   */
  public final byte[] commandBytes;

  private PrivateCommand(long identifier, byte[] commandBytes, long ptsAdjustment) {
    this.ptsAdjustment = ptsAdjustment;
    this.identifier = identifier;
    this.commandBytes = commandBytes;
  }

  private PrivateCommand(Parcel in) {
    ptsAdjustment = in.readLong();
    identifier = in.readLong();
    commandBytes = new byte[in.readInt()];
    in.readByteArray(commandBytes);
  }

  /* package */ static PrivateCommand parseFromSection(ParsableByteArray sectionData,
      int commandLength, long ptsAdjustment) {
    long identifier = sectionData.readUnsignedInt();
    byte[] privateBytes = new byte[commandLength - 4 /* identifier size */];
    sectionData.readBytes(privateBytes, 0, privateBytes.length);
    return new PrivateCommand(identifier, privateBytes, ptsAdjustment);
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(ptsAdjustment);
    dest.writeLong(identifier);
    dest.writeInt(commandBytes.length);
    dest.writeByteArray(commandBytes);
  }

  public static final Parcelable.Creator<PrivateCommand> CREATOR =
      new Parcelable.Creator<PrivateCommand>() {

    @Override
    public PrivateCommand createFromParcel(Parcel in) {
      return new PrivateCommand(in);
    }

    @Override
    public PrivateCommand[] newArray(int size) {
      return new PrivateCommand[size];
    }

  };

}
