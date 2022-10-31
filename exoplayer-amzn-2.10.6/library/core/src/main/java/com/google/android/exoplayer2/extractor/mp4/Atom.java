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
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("ConstantField")
/* package */ abstract class Atom {

  /**
   * Size of an atom header, in bytes.
   */
  public static final int HEADER_SIZE = 8;

  /**
   * Size of a full atom header, in bytes.
   */
  public static final int FULL_HEADER_SIZE = 12;

  /**
   * Size of a long atom header, in bytes.
   */
  public static final int LONG_HEADER_SIZE = 16;

  /**
   * Value for the size field in an atom that defines its size in the largesize field.
   */
  public static final int DEFINES_LARGE_SIZE = 1;

  /**
   * Value for the size field in an atom that extends to the end of the file.
   */
  public static final int EXTENDS_TO_END_SIZE = 0;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ftyp = Util.getIntegerCodeForString("ftyp");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_avc1 = Util.getIntegerCodeForString("avc1");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_avc3 = Util.getIntegerCodeForString("avc3");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_avcC = Util.getIntegerCodeForString("avcC");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hvc1 = Util.getIntegerCodeForString("hvc1");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hev1 = Util.getIntegerCodeForString("hev1");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hvcC = Util.getIntegerCodeForString("hvcC");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vp08 = Util.getIntegerCodeForString("vp08");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vp09 = Util.getIntegerCodeForString("vp09");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vpcC = Util.getIntegerCodeForString("vpcC");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_av01 = Util.getIntegerCodeForString("av01");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_av1C = Util.getIntegerCodeForString("av1C");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvav = Util.getIntegerCodeForString("dvav");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dva1 = Util.getIntegerCodeForString("dva1");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvhe = Util.getIntegerCodeForString("dvhe");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvh1 = Util.getIntegerCodeForString("dvh1");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvcC = Util.getIntegerCodeForString("dvcC");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvvC = Util.getIntegerCodeForString("dvvC");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_s263 = Util.getIntegerCodeForString("s263");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_d263 = Util.getIntegerCodeForString("d263");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mdat = Util.getIntegerCodeForString("mdat");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mp4a = Util.getIntegerCodeForString("mp4a");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE__mp3 = Util.getIntegerCodeForString(".mp3");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_wave = Util.getIntegerCodeForString("wave");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_lpcm = Util.getIntegerCodeForString("lpcm");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sowt = Util.getIntegerCodeForString("sowt");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ac_3 = Util.getIntegerCodeForString("ac-3");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dac3 = Util.getIntegerCodeForString("dac3");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ec_3 = Util.getIntegerCodeForString("ec-3");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dec3 = Util.getIntegerCodeForString("dec3");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ac_4 = Util.getIntegerCodeForString("ac-4");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dac4 = Util.getIntegerCodeForString("dac4");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtsc = Util.getIntegerCodeForString("dtsc");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtsh = Util.getIntegerCodeForString("dtsh");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtsl = Util.getIntegerCodeForString("dtsl");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtse = Util.getIntegerCodeForString("dtse");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ddts = Util.getIntegerCodeForString("ddts");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tfdt = Util.getIntegerCodeForString("tfdt");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tfhd = Util.getIntegerCodeForString("tfhd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_trex = Util.getIntegerCodeForString("trex");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_trun = Util.getIntegerCodeForString("trun");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sidx = Util.getIntegerCodeForString("sidx");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_moov = Util.getIntegerCodeForString("moov");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mvhd = Util.getIntegerCodeForString("mvhd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_trak = Util.getIntegerCodeForString("trak");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mdia = Util.getIntegerCodeForString("mdia");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_minf = Util.getIntegerCodeForString("minf");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stbl = Util.getIntegerCodeForString("stbl");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_esds = Util.getIntegerCodeForString("esds");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_moof = Util.getIntegerCodeForString("moof");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_traf = Util.getIntegerCodeForString("traf");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mvex = Util.getIntegerCodeForString("mvex");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mehd = Util.getIntegerCodeForString("mehd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tkhd = Util.getIntegerCodeForString("tkhd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_edts = Util.getIntegerCodeForString("edts");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_elst = Util.getIntegerCodeForString("elst");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mdhd = Util.getIntegerCodeForString("mdhd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hdlr = Util.getIntegerCodeForString("hdlr");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stsd = Util.getIntegerCodeForString("stsd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_pssh = Util.getIntegerCodeForString("pssh");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sinf = Util.getIntegerCodeForString("sinf");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_schm = Util.getIntegerCodeForString("schm");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_schi = Util.getIntegerCodeForString("schi");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tenc = Util.getIntegerCodeForString("tenc");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_encv = Util.getIntegerCodeForString("encv");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_enca = Util.getIntegerCodeForString("enca");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_frma = Util.getIntegerCodeForString("frma");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_saiz = Util.getIntegerCodeForString("saiz");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_saio = Util.getIntegerCodeForString("saio");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sbgp = Util.getIntegerCodeForString("sbgp");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sgpd = Util.getIntegerCodeForString("sgpd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_uuid = Util.getIntegerCodeForString("uuid");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_senc = Util.getIntegerCodeForString("senc");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_pasp = Util.getIntegerCodeForString("pasp");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_TTML = Util.getIntegerCodeForString("TTML");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vmhd = Util.getIntegerCodeForString("vmhd");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mp4v = Util.getIntegerCodeForString("mp4v");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stts = Util.getIntegerCodeForString("stts");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stss = Util.getIntegerCodeForString("stss");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ctts = Util.getIntegerCodeForString("ctts");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stsc = Util.getIntegerCodeForString("stsc");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stsz = Util.getIntegerCodeForString("stsz");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stz2 = Util.getIntegerCodeForString("stz2");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stco = Util.getIntegerCodeForString("stco");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_co64 = Util.getIntegerCodeForString("co64");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tx3g = Util.getIntegerCodeForString("tx3g");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_wvtt = Util.getIntegerCodeForString("wvtt");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stpp = Util.getIntegerCodeForString("stpp");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_c608 = Util.getIntegerCodeForString("c608");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_samr = Util.getIntegerCodeForString("samr");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sawb = Util.getIntegerCodeForString("sawb");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_udta = Util.getIntegerCodeForString("udta");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_meta = Util.getIntegerCodeForString("meta");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_keys = Util.getIntegerCodeForString("keys");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ilst = Util.getIntegerCodeForString("ilst");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mean = Util.getIntegerCodeForString("mean");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_name = Util.getIntegerCodeForString("name");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_data = Util.getIntegerCodeForString("data");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_emsg = Util.getIntegerCodeForString("emsg");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_st3d = Util.getIntegerCodeForString("st3d");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sv3d = Util.getIntegerCodeForString("sv3d");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_proj = Util.getIntegerCodeForString("proj");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_camm = Util.getIntegerCodeForString("camm");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_alac = Util.getIntegerCodeForString("alac");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_alaw = Util.getIntegerCodeForString("alaw");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ulaw = Util.getIntegerCodeForString("ulaw");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_Opus = Util.getIntegerCodeForString("Opus");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dOps = Util.getIntegerCodeForString("dOps");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_fLaC = Util.getIntegerCodeForString("fLaC");

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dfLa = Util.getIntegerCodeForString("dfLa");

  public final int type;

  public Atom(int type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return getAtomTypeString(type);
  }

  /**
   * An MP4 atom that is a leaf.
   */
  /* package */ static final class LeafAtom extends Atom {

    /**
     * The atom data.
     */
    public final ParsableByteArray data;

    /**
     * @param type The type of the atom.
     * @param data The atom data.
     */
    public LeafAtom(int type, ParsableByteArray data) {
      super(type);
      this.data = data;
    }

  }

  /**
   * An MP4 atom that has child atoms.
   */
  /* package */ static final class ContainerAtom extends Atom {

    public final long endPosition;
    public final List<LeafAtom> leafChildren;
    public final List<ContainerAtom> containerChildren;

    /**
     * @param type The type of the atom.
     * @param endPosition The position of the first byte after the end of the atom.
     */
    public ContainerAtom(int type, long endPosition) {
      super(type);
      this.endPosition = endPosition;
      leafChildren = new ArrayList<>();
      containerChildren = new ArrayList<>();
    }

    /**
     * Adds a child leaf to this container.
     *
     * @param atom The child to add.
     */
    public void add(LeafAtom atom) {
      leafChildren.add(atom);
    }

    /**
     * Adds a child container to this container.
     *
     * @param atom The child to add.
     */
    public void add(ContainerAtom atom) {
      containerChildren.add(atom);
    }

    /**
     * Returns the child leaf of the given type.
     *
     * <p>If no child exists with the given type then null is returned. If multiple children exist
     * with the given type then the first one to have been added is returned.
     *
     * @param type The leaf type.
     * @return The child leaf of the given type, or null if no such child exists.
     */
    public @Nullable LeafAtom getLeafAtomOfType(int type) {
      int childrenSize = leafChildren.size();
      for (int i = 0; i < childrenSize; i++) {
        LeafAtom atom = leafChildren.get(i);
        if (atom.type == type) {
          return atom;
        }
      }
      return null;
    }

    /**
     * Returns the child container of the given type.
     *
     * <p>If no child exists with the given type then null is returned. If multiple children exist
     * with the given type then the first one to have been added is returned.
     *
     * @param type The container type.
     * @return The child container of the given type, or null if no such child exists.
     */
    public @Nullable ContainerAtom getContainerAtomOfType(int type) {
      int childrenSize = containerChildren.size();
      for (int i = 0; i < childrenSize; i++) {
        ContainerAtom atom = containerChildren.get(i);
        if (atom.type == type) {
          return atom;
        }
      }
      return null;
    }

    /**
     * Returns the total number of leaf/container children of this atom with the given type.
     *
     * @param type The type of child atoms to count.
     * @return The total number of leaf/container children of this atom with the given type.
     */
    public int getChildAtomOfTypeCount(int type) {
      int count = 0;
      int size = leafChildren.size();
      for (int i = 0; i < size; i++) {
        LeafAtom atom = leafChildren.get(i);
        if (atom.type == type) {
          count++;
        }
      }
      size = containerChildren.size();
      for (int i = 0; i < size; i++) {
        ContainerAtom atom = containerChildren.get(i);
        if (atom.type == type) {
          count++;
        }
      }
      return count;
    }

    @Override
    public String toString() {
      return getAtomTypeString(type)
          + " leaves: " + Arrays.toString(leafChildren.toArray())
          + " containers: " + Arrays.toString(containerChildren.toArray());
    }

  }

  /**
   * Parses the version number out of the additional integer component of a full atom.
   */
  public static int parseFullAtomVersion(int fullAtomInt) {
    return 0x000000FF & (fullAtomInt >> 24);
  }

  /**
   * Parses the atom flags out of the additional integer component of a full atom.
   */
  public static int parseFullAtomFlags(int fullAtomInt) {
    return 0x00FFFFFF & fullAtomInt;
  }

  /**
   * Converts a numeric atom type to the corresponding four character string.
   *
   * @param type The numeric atom type.
   * @return The corresponding four character string.
   */
  public static String getAtomTypeString(int type) {
    return "" + (char) ((type >> 24) & 0xFF)
        + (char) ((type >> 16) & 0xFF)
        + (char) ((type >> 8) & 0xFF)
        + (char) (type & 0xFF);
  }

}
