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
package com.google.android.exoplayer2.source.dash.manifest;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;

/** A parsed program information element. */
public class ProgramInformation {
  /** The title for the media presentation. */
  public final String title;

  /** Information about the original source of the media presentation. */
  public final String source;

  /** A copyright statement for the media presentation. */
  public final String copyright;

  /** A URL that provides more information about the media presentation. */
  public final String moreInformationURL;

  /** Declares the language code(s) for this ProgramInformation. */
  public final String lang;

  public ProgramInformation(
      String title, String source, String copyright, String moreInformationURL, String lang) {
    this.title = title;
    this.source = source;
    this.copyright = copyright;
    this.moreInformationURL = moreInformationURL;
    this.lang = lang;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ProgramInformation other = (ProgramInformation) obj;
    return Util.areEqual(this.title, other.title)
        && Util.areEqual(this.source, other.source)
        && Util.areEqual(this.copyright, other.copyright)
        && Util.areEqual(this.moreInformationURL, other.moreInformationURL)
        && Util.areEqual(this.lang, other.lang);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + (title != null ? title.hashCode() : 0);
    result = 31 * result + (source != null ? source.hashCode() : 0);
    result = 31 * result + (copyright != null ? copyright.hashCode() : 0);
    result = 31 * result + (moreInformationURL != null ? moreInformationURL.hashCode() : 0);
    result = 31 * result + (lang != null ? lang.hashCode() : 0);
    return result;
  }
}
