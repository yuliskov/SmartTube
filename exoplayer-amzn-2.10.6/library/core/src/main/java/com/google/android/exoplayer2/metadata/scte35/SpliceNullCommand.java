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

/**
 * Represents a splice null command as defined in SCTE35, Section 9.3.1.
 */
public final class SpliceNullCommand extends SpliceCommand {

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    // Do nothing.
  }

  public static final Creator<SpliceNullCommand> CREATOR =
      new Creator<SpliceNullCommand>() {

    @Override
    public SpliceNullCommand createFromParcel(Parcel in) {
      return new SpliceNullCommand();
    }

    @Override
    public SpliceNullCommand[] newArray(int size) {
      return new SpliceNullCommand[size];
    }

  };

}
