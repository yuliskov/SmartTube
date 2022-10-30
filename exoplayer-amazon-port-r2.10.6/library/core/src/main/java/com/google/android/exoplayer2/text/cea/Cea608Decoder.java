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
package com.google.android.exoplayer2.text.cea;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link SubtitleDecoder} for CEA-608 (also known as "line 21 captions" and "EIA-608").
 */
public final class Cea608Decoder extends CeaDecoder {

  private static final String TAG = "Cea608Decoder";

  private static final int CC_VALID_FLAG = 0x04;
  private static final int CC_TYPE_FLAG = 0x02;
  private static final int CC_FIELD_FLAG = 0x01;

  private static final int NTSC_CC_FIELD_1 = 0x00;
  private static final int NTSC_CC_FIELD_2 = 0x01;
  private static final int NTSC_CC_CHANNEL_1 = 0x00;
  private static final int NTSC_CC_CHANNEL_2 = 0x01;

  private static final int CC_MODE_UNKNOWN = 0;
  private static final int CC_MODE_ROLL_UP = 1;
  private static final int CC_MODE_POP_ON = 2;
  private static final int CC_MODE_PAINT_ON = 3;

  private static final int[] ROW_INDICES = new int[] {11, 1, 3, 12, 14, 5, 7, 9};
  private static final int[] COLUMN_INDICES = new int[] {0, 4, 8, 12, 16, 20, 24, 28};

  private static final int[] STYLE_COLORS =
      new int[] {
        Color.WHITE, Color.GREEN, Color.BLUE, Color.CYAN, Color.RED, Color.YELLOW, Color.MAGENTA
      };
  private static final int STYLE_ITALICS = 0x07;
  private static final int STYLE_UNCHANGED = 0x08;

  // The default number of rows to display in roll-up captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;

  // An implied first byte for packets that are only 2 bytes long, consisting of marker bits
  // (0b11111) + valid bit (0b1) + NTSC field 1 type bits (0b00).
  private static final byte CC_IMPLICIT_DATA_HEADER = (byte) 0xFC;

  /**
   * Command initiating pop-on style captioning. Subsequent data should be loaded into a
   * non-displayed memory and held there until the {@link #CTRL_END_OF_CAPTION} command is received,
   * at which point the non-displayed memory becomes the displayed memory (and vice versa).
   */
  private static final byte CTRL_RESUME_CAPTION_LOADING = 0x20;

  private static final byte CTRL_BACKSPACE = 0x21;

  private static final byte CTRL_DELETE_TO_END_OF_ROW = 0x24;

  /**
   * Command initiating roll-up style captioning, with the maximum of 2 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_2_ROWS = 0x25;
  /**
   * Command initiating roll-up style captioning, with the maximum of 3 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_3_ROWS = 0x26;
  /**
   * Command initiating roll-up style captioning, with the maximum of 4 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_4_ROWS = 0x27;

  /**
   * Command initiating paint-on style captioning. Subsequent data should be addressed immediately
   * to displayed memory without need for the {@link #CTRL_RESUME_CAPTION_LOADING} command.
   */
  private static final byte CTRL_RESUME_DIRECT_CAPTIONING = 0x29;
  /**
   * TEXT commands are switching to TEXT service. All consecutive incoming data must be filtered out
   * until a command is received that switches back to the CAPTION service.
   */
  private static final byte CTRL_TEXT_RESTART = 0x2A;

  private static final byte CTRL_RESUME_TEXT_DISPLAY = 0x2B;

  private static final byte CTRL_ERASE_DISPLAYED_MEMORY = 0x2C;
  private static final byte CTRL_CARRIAGE_RETURN = 0x2D;
  private static final byte CTRL_ERASE_NON_DISPLAYED_MEMORY = 0x2E;

  /**
   * Command indicating the end of a pop-on style caption. At this point the caption loaded in
   * non-displayed memory should be swapped with the one in displayed memory. If no {@link
   * #CTRL_RESUME_CAPTION_LOADING} command has been received, this command forces the receiver into
   * pop-on style.
   */
  private static final byte CTRL_END_OF_CAPTION = 0x2F;

  // Basic North American 608 CC char set, mostly ASCII. Indexed by (char-0x20).
  private static final int[] BASIC_CHARACTER_SET = new int[] {
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,     //   ! " # $ % & '
    0x28, 0x29,                                         // ( )
    0xE1,       // 2A: 225 'á' "Latin small letter A with acute"
    0x2B, 0x2C, 0x2D, 0x2E, 0x2F,                       //       + , - . /
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,     // 0 1 2 3 4 5 6 7
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,     // 8 9 : ; < = > ?
    0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,     // @ A B C D E F G
    0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F,     // H I J K L M N O
    0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,     // P Q R S T U V W
    0x58, 0x59, 0x5A, 0x5B,                             // X Y Z [
    0xE9,       // 5C: 233 'é' "Latin small letter E with acute"
    0x5D,                                               //           ]
    0xED,       // 5E: 237 'í' "Latin small letter I with acute"
    0xF3,       // 5F: 243 'ó' "Latin small letter O with acute"
    0xFA,       // 60: 250 'ú' "Latin small letter U with acute"
    0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,           //   a b c d e f g
    0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,     // h i j k l m n o
    0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,     // p q r s t u v w
    0x78, 0x79, 0x7A,                                   // x y z
    0xE7,       // 7B: 231 'ç' "Latin small letter C with cedilla"
    0xF7,       // 7C: 247 '÷' "Division sign"
    0xD1,       // 7D: 209 'Ñ' "Latin capital letter N with tilde"
    0xF1,       // 7E: 241 'ñ' "Latin small letter N with tilde"
    0x25A0      // 7F:         "Black Square" (NB: 2588 = Full Block)
  };

  // Special North American 608 CC char set.
  private static final int[] SPECIAL_CHARACTER_SET = new int[] {
    0xAE,    // 30: 174 '®' "Registered Sign" - registered trademark symbol
    0xB0,    // 31: 176 '°' "Degree Sign"
    0xBD,    // 32: 189 '½' "Vulgar Fraction One Half" (1/2 symbol)
    0xBF,    // 33: 191 '¿' "Inverted Question Mark"
    0x2122,  // 34:         "Trade Mark Sign" (tm superscript)
    0xA2,    // 35: 162 '¢' "Cent Sign"
    0xA3,    // 36: 163 '£' "Pound Sign" - pounds sterling
    0x266A,  // 37:         "Eighth Note" - music note
    0xE0,    // 38: 224 'à' "Latin small letter A with grave"
    0x20,    // 39:         TRANSPARENT SPACE - for now use ordinary space
    0xE8,    // 3A: 232 'è' "Latin small letter E with grave"
    0xE2,    // 3B: 226 'â' "Latin small letter A with circumflex"
    0xEA,    // 3C: 234 'ê' "Latin small letter E with circumflex"
    0xEE,    // 3D: 238 'î' "Latin small letter I with circumflex"
    0xF4,    // 3E: 244 'ô' "Latin small letter O with circumflex"
    0xFB     // 3F: 251 'û' "Latin small letter U with circumflex"
  };

  // Extended Spanish/Miscellaneous and French char set.
  private static final int[] SPECIAL_ES_FR_CHARACTER_SET = new int[] {
    // Spanish and misc.
    0xC1, 0xC9, 0xD3, 0xDA, 0xDC, 0xFC, 0x2018, 0xA1,
    0x2A, 0x27, 0x2014, 0xA9, 0x2120, 0x2022, 0x201C, 0x201D,
    // French.
    0xC0, 0xC2, 0xC7, 0xC8, 0xCA, 0xCB, 0xEB, 0xCE,
    0xCF, 0xEF, 0xD4, 0xD9, 0xF9, 0xDB, 0xAB, 0xBB
  };

  //Extended Portuguese and German/Danish char set.
  private static final int[] SPECIAL_PT_DE_CHARACTER_SET = new int[] {
    // Portuguese.
    0xC3, 0xE3, 0xCD, 0xCC, 0xEC, 0xD2, 0xF2, 0xD5,
    0xF5, 0x7B, 0x7D, 0x5C, 0x5E, 0x5F, 0x7C, 0x7E,
    // German/Danish.
    0xC4, 0xE4, 0xD6, 0xF6, 0xDF, 0xA5, 0xA4, 0x2502,
    0xC5, 0xE5, 0xD8, 0xF8, 0x250C, 0x2510, 0x2514, 0x2518
  };

  private static final boolean[] ODD_PARITY_BYTE_TABLE = {
    false, true, true, false, true, false, false, true, // 0
    true, false, false, true, false, true, true, false, // 8
    true, false, false, true, false, true, true, false, // 16
    false, true, true, false, true, false, false, true, // 24
    true, false, false, true, false, true, true, false, // 32
    false, true, true, false, true, false, false, true, // 40
    false, true, true, false, true, false, false, true, // 48
    true, false, false, true, false, true, true, false, // 56
    true, false, false, true, false, true, true, false, // 64
    false, true, true, false, true, false, false, true, // 72
    false, true, true, false, true, false, false, true, // 80
    true, false, false, true, false, true, true, false, // 88
    false, true, true, false, true, false, false, true, // 96
    true, false, false, true, false, true, true, false, // 104
    true, false, false, true, false, true, true, false, // 112
    false, true, true, false, true, false, false, true, // 120
    true, false, false, true, false, true, true, false, // 128
    false, true, true, false, true, false, false, true, // 136
    false, true, true, false, true, false, false, true, // 144
    true, false, false, true, false, true, true, false, // 152
    false, true, true, false, true, false, false, true, // 160
    true, false, false, true, false, true, true, false, // 168
    true, false, false, true, false, true, true, false, // 176
    false, true, true, false, true, false, false, true, // 184
    false, true, true, false, true, false, false, true, // 192
    true, false, false, true, false, true, true, false, // 200
    true, false, false, true, false, true, true, false, // 208
    false, true, true, false, true, false, false, true, // 216
    true, false, false, true, false, true, true, false, // 224
    false, true, true, false, true, false, false, true, // 232
    false, true, true, false, true, false, false, true, // 240
    true, false, false, true, false, true, true, false, // 248
  };

  private final ParsableByteArray ccData;
  private final int packetLength;
  private final int selectedField;
  private final int selectedChannel;
  private final ArrayList<CueBuilder> cueBuilders;

  private CueBuilder currentCueBuilder;
  private List<Cue> cues;
  private List<Cue> lastCues;

  private int captionMode;
  private int captionRowCount;

  private boolean isCaptionValid;
  private boolean repeatableControlSet;
  private byte repeatableControlCc1;
  private byte repeatableControlCc2;
  private int currentChannel;

  // The incoming characters may belong to 3 different services based on the last received control
  // codes. The 3 services are Captioning, Text and XDS. The decoder only processes Captioning
  // service bytes and drops the rest.
  private boolean isInCaptionService;

  public Cea608Decoder(String mimeType, int accessibilityChannel) {
    ccData = new ParsableByteArray();
    cueBuilders = new ArrayList<>();
    currentCueBuilder = new CueBuilder(CC_MODE_UNKNOWN, DEFAULT_CAPTIONS_ROW_COUNT);
    currentChannel = NTSC_CC_CHANNEL_1;
    packetLength = MimeTypes.APPLICATION_MP4CEA608.equals(mimeType) ? 2 : 3;
    switch (accessibilityChannel) {
      case 1:
        selectedChannel = NTSC_CC_CHANNEL_1;
        selectedField = NTSC_CC_FIELD_1;
        break;
      case 2:
        selectedChannel = NTSC_CC_CHANNEL_2;
        selectedField = NTSC_CC_FIELD_1;
        break;
      case 3:
        selectedChannel = NTSC_CC_CHANNEL_1;
        selectedField = NTSC_CC_FIELD_2;
        break;
      case 4:
        selectedChannel = NTSC_CC_CHANNEL_2;
        selectedField = NTSC_CC_FIELD_2;
        break;
      default:
        Log.w(TAG, "Invalid channel. Defaulting to CC1.");
        selectedChannel = NTSC_CC_CHANNEL_1;
        selectedField = NTSC_CC_FIELD_1;
    }

    setCaptionMode(CC_MODE_UNKNOWN);
    resetCueBuilders();
    isInCaptionService = true;
  }

  @Override
  public String getName() {
    return "Cea608Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    cues = null;
    lastCues = null;
    setCaptionMode(CC_MODE_UNKNOWN);
    setCaptionRowCount(DEFAULT_CAPTIONS_ROW_COUNT);
    resetCueBuilders();
    isCaptionValid = false;
    repeatableControlSet = false;
    repeatableControlCc1 = 0;
    repeatableControlCc2 = 0;
    currentChannel = NTSC_CC_CHANNEL_1;
    isInCaptionService = true;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  protected boolean isNewSubtitleDataAvailable() {
    return cues != lastCues;
  }

  @Override
  protected Subtitle createSubtitle() {
    lastCues = cues;
    return new CeaSubtitle(cues);
  }

  @SuppressWarnings("ByteBufferBackingArray")
  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    ccData.reset(inputBuffer.data.array(), inputBuffer.data.limit());
    boolean captionDataProcessed = false;
    while (ccData.bytesLeft() >= packetLength) {
      byte ccHeader = packetLength == 2 ? CC_IMPLICIT_DATA_HEADER
          : (byte) ccData.readUnsignedByte();
      int ccByte1 = ccData.readUnsignedByte();
      int ccByte2 = ccData.readUnsignedByte();

      // TODO: We're currently ignoring the top 5 marker bits, which should all be 1s according
      // to the CEA-608 specification. We need to determine if the data should be handled
      // differently when that is not the case.

      if ((ccHeader & CC_TYPE_FLAG) != 0) {
        // Do not process anything that is not part of the 608 byte stream.
        continue;
      }

      if ((ccHeader & CC_FIELD_FLAG) != selectedField) {
        // Do not process packets not within the selected field.
        continue;
      }

      // Strip the parity bit from each byte to get CC data.
      byte ccData1 = (byte) (ccByte1 & 0x7F);
      byte ccData2 = (byte) (ccByte2 & 0x7F);

      if (ccData1 == 0 && ccData2 == 0) {
        // Ignore empty captions.
        continue;
      }

      boolean previousIsCaptionValid = isCaptionValid;
      isCaptionValid =
          (ccHeader & CC_VALID_FLAG) == CC_VALID_FLAG
              && ODD_PARITY_BYTE_TABLE[ccByte1]
              && ODD_PARITY_BYTE_TABLE[ccByte2];

      if (isRepeatedCommand(isCaptionValid, ccData1, ccData2)) {
        // Ignore repeated valid commands.
        continue;
      }

      if (!isCaptionValid) {
        if (previousIsCaptionValid) {
          // The encoder has flipped the validity bit to indicate captions are being turned off.
          resetCueBuilders();
          captionDataProcessed = true;
        }
        continue;
      }

      maybeUpdateIsInCaptionService(ccData1, ccData2);
      if (!isInCaptionService) {
        // Only the Captioning service is supported. Drop all other bytes.
        continue;
      }

      if (!updateAndVerifyCurrentChannel(ccData1)) {
        // Wrong channel.
        continue;
      }

      if (isCtrlCode(ccData1)) {
        if (isSpecialNorthAmericanChar(ccData1, ccData2)) {
          currentCueBuilder.append(getSpecialNorthAmericanChar(ccData2));
        } else if (isExtendedWestEuropeanChar(ccData1, ccData2)) {
          // Remove standard equivalent of the special extended char before appending new one.
          currentCueBuilder.backspace();
          currentCueBuilder.append(getExtendedWestEuropeanChar(ccData1, ccData2));
        } else if (isMidrowCtrlCode(ccData1, ccData2)) {
          handleMidrowCtrl(ccData2);
        } else if (isPreambleAddressCode(ccData1, ccData2)) {
          handlePreambleAddressCode(ccData1, ccData2);
        } else if (isTabCtrlCode(ccData1, ccData2)) {
          currentCueBuilder.tabOffset = ccData2 - 0x20;
        } else if (isMiscCode(ccData1, ccData2)) {
          handleMiscCode(ccData2);
        }
      } else {
        // Basic North American character set.
        currentCueBuilder.append(getBasicChar(ccData1));
        if ((ccData2 & 0xE0) != 0x00) {
          currentCueBuilder.append(getBasicChar(ccData2));
        }
      }
      captionDataProcessed = true;
    }

    if (captionDataProcessed) {
      if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
        cues = getDisplayCues();
      }
    }
  }

  private boolean updateAndVerifyCurrentChannel(byte cc1) {
    if (isCtrlCode(cc1)) {
      currentChannel = getChannel(cc1);
    }
    return currentChannel == selectedChannel;
  }

  private boolean isRepeatedCommand(boolean captionValid, byte cc1, byte cc2) {
    // Most control commands are sent twice in succession to ensure they are received properly. We
    // don't want to process duplicate commands, so if we see the same repeatable command twice in a
    // row then we ignore the second one.
    if (captionValid && isRepeatable(cc1)) {
      if (repeatableControlSet && repeatableControlCc1 == cc1 && repeatableControlCc2 == cc2) {
        // This is a repeated command, so we ignore it.
        repeatableControlSet = false;
        return true;
      } else {
        // This is the first occurrence of a repeatable command. Set the repeatable control
        // variables so that we can recognize and ignore a duplicate (if there is one), and then
        // continue to process the command below.
        repeatableControlSet = true;
        repeatableControlCc1 = cc1;
        repeatableControlCc2 = cc2;
      }
    } else {
      // This command is not repeatable.
      repeatableControlSet = false;
    }
    return false;
  }

  private void handleMidrowCtrl(byte cc2) {
    // TODO: support the extended styles (i.e. backgrounds and transparencies)

    // A midrow control code advances the cursor.
    currentCueBuilder.append(' ');

    // cc2 - 0|0|1|0|STYLE|U
    boolean underline = (cc2 & 0x01) == 0x01;
    int style = (cc2 >> 1) & 0x07;
    currentCueBuilder.setStyle(style, underline);
  }

  private void handlePreambleAddressCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|E|ROW
    // C is the channel toggle, E is the extended flag, and ROW is the encoded row
    int row = ROW_INDICES[cc1 & 0x07];
    // TODO: support the extended address and style

    // cc2 - 0|1|N|ATTRBTE|U
    // N is the next row down toggle, ATTRBTE is the 4-byte encoded attribute, and U is the
    // underline toggle.
    boolean nextRowDown = (cc2 & 0x20) != 0;
    if (nextRowDown) {
      row++;
    }

    if (row != currentCueBuilder.row) {
      if (captionMode != CC_MODE_ROLL_UP && !currentCueBuilder.isEmpty()) {
        currentCueBuilder = new CueBuilder(captionMode, captionRowCount);
        cueBuilders.add(currentCueBuilder);
      }
      currentCueBuilder.row = row;
    }

    // cc2 - 0|1|N|0|STYLE|U
    // cc2 - 0|1|N|1|CURSR|U
    boolean isCursor = (cc2 & 0x10) == 0x10;
    boolean underline = (cc2 & 0x01) == 0x01;
    int cursorOrStyle = (cc2 >> 1) & 0x07;

    // We need to call setStyle even for the isCursor case, to update the underline bit.
    // STYLE_UNCHANGED is used for this case.
    currentCueBuilder.setStyle(isCursor ? STYLE_UNCHANGED : cursorOrStyle, underline);

    if (isCursor) {
      currentCueBuilder.indent = COLUMN_INDICES[cursorOrStyle];
    }
  }

  private void handleMiscCode(byte cc2) {
    switch (cc2) {
      case CTRL_ROLL_UP_CAPTIONS_2_ROWS:
        setCaptionMode(CC_MODE_ROLL_UP);
        setCaptionRowCount(2);
        return;
      case CTRL_ROLL_UP_CAPTIONS_3_ROWS:
        setCaptionMode(CC_MODE_ROLL_UP);
        setCaptionRowCount(3);
        return;
      case CTRL_ROLL_UP_CAPTIONS_4_ROWS:
        setCaptionMode(CC_MODE_ROLL_UP);
        setCaptionRowCount(4);
        return;
      case CTRL_RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case CTRL_RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
      default:
        // Fall through.
        break;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (cc2) {
      case CTRL_ERASE_DISPLAYED_MEMORY:
        cues = Collections.emptyList();
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          resetCueBuilders();
        }
        break;
      case CTRL_ERASE_NON_DISPLAYED_MEMORY:
        resetCueBuilders();
        break;
      case CTRL_END_OF_CAPTION:
        cues = getDisplayCues();
        resetCueBuilders();
        break;
      case CTRL_CARRIAGE_RETURN:
        // carriage returns only apply to rollup captions; don't bother if we don't have anything
        // to add a carriage return to
        if (captionMode == CC_MODE_ROLL_UP && !currentCueBuilder.isEmpty()) {
          currentCueBuilder.rollUp();
        }
        break;
      case CTRL_BACKSPACE:
        currentCueBuilder.backspace();
        break;
      case CTRL_DELETE_TO_END_OF_ROW:
        // TODO: implement
        break;
      default:
        // Fall through.
        break;
    }
  }

  private List<Cue> getDisplayCues() {
    // CEA-608 does not define middle and end alignment, however content providers artificially
    // introduce them using whitespace. When each cue is built, we try and infer the alignment based
    // on the amount of whitespace either side of the text. To avoid consecutive cues being aligned
    // differently, we force all cues to have the same alignment, with start alignment given
    // preference, then middle alignment, then end alignment.
    @Cue.AnchorType int positionAnchor = Cue.ANCHOR_TYPE_END;
    int cueBuilderCount = cueBuilders.size();
    List<Cue> cueBuilderCues = new ArrayList<>(cueBuilderCount);
    for (int i = 0; i < cueBuilderCount; i++) {
      Cue cue = cueBuilders.get(i).build(/* forcedPositionAnchor= */ Cue.TYPE_UNSET);
      cueBuilderCues.add(cue);
      if (cue != null) {
        positionAnchor = Math.min(positionAnchor, cue.positionAnchor);
      }
    }

    // Skip null cues and rebuild any that don't have the preferred alignment.
    List<Cue> displayCues = new ArrayList<>(cueBuilderCount);
    for (int i = 0; i < cueBuilderCount; i++) {
      Cue cue = cueBuilderCues.get(i);
      if (cue != null) {
        if (cue.positionAnchor != positionAnchor) {
          cue = cueBuilders.get(i).build(positionAnchor);
        }
        displayCues.add(cue);
      }
    }

    return displayCues;
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    int oldCaptionMode = this.captionMode;
    this.captionMode = captionMode;

    if (captionMode == CC_MODE_PAINT_ON) {
      // Switching to paint-on mode should have no effect except to select the mode.
      for (int i = 0; i < cueBuilders.size(); i++) {
        cueBuilders.get(i).setCaptionMode(captionMode);
      }
      return;
    }

    // Clear the working memory.
    resetCueBuilders();
    if (oldCaptionMode == CC_MODE_PAINT_ON || captionMode == CC_MODE_ROLL_UP
        || captionMode == CC_MODE_UNKNOWN) {
      // When switching from paint-on or to roll-up or unknown, we also need to clear the caption.
      cues = Collections.emptyList();
    }
  }

  private void setCaptionRowCount(int captionRowCount) {
    this.captionRowCount = captionRowCount;
    currentCueBuilder.setCaptionRowCount(captionRowCount);
  }

  private void resetCueBuilders() {
    currentCueBuilder.reset(captionMode);
    cueBuilders.clear();
    cueBuilders.add(currentCueBuilder);
  }

  private void maybeUpdateIsInCaptionService(byte cc1, byte cc2) {
    if (isXdsControlCode(cc1)) {
      isInCaptionService = false;
    } else if (isServiceSwitchCommand(cc1)) {
      switch (cc2) {
        case CTRL_TEXT_RESTART:
        case CTRL_RESUME_TEXT_DISPLAY:
          isInCaptionService = false;
          break;
        case CTRL_END_OF_CAPTION:
        case CTRL_RESUME_CAPTION_LOADING:
        case CTRL_RESUME_DIRECT_CAPTIONING:
        case CTRL_ROLL_UP_CAPTIONS_2_ROWS:
        case CTRL_ROLL_UP_CAPTIONS_3_ROWS:
        case CTRL_ROLL_UP_CAPTIONS_4_ROWS:
          isInCaptionService = true;
          break;
        default:
          // No update.
      }
    }
  }

  private static char getBasicChar(byte ccData) {
    int index = (ccData & 0x7F) - 0x20;
    return (char) BASIC_CHARACTER_SET[index];
  }

  private static boolean isSpecialNorthAmericanChar(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|0|0|1
    // cc2 - 0|0|1|1|X|X|X|X
    return ((cc1 & 0xF7) == 0x11) && ((cc2 & 0xF0) == 0x30);
  }

  private static char getSpecialNorthAmericanChar(byte ccData) {
    int index = ccData & 0x0F;
    return (char) SPECIAL_CHARACTER_SET[index];
  }

  private static boolean isExtendedWestEuropeanChar(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|0|1|S
    // cc2 - 0|0|1|X|X|X|X|X
    return ((cc1 & 0xF6) == 0x12) && ((cc2 & 0xE0) == 0x20);
  }

  private static char getExtendedWestEuropeanChar(byte cc1, byte cc2) {
    if ((cc1 & 0x01) == 0x00) {
      // Extended Spanish/Miscellaneous and French character set (S = 0).
      return getExtendedEsFrChar(cc2);
    } else {
      // Extended Portuguese and German/Danish character set (S = 1).
      return getExtendedPtDeChar(cc2);
    }
  }

  private static char getExtendedEsFrChar(byte ccData) {
    int index = ccData & 0x1F;
    return (char) SPECIAL_ES_FR_CHARACTER_SET[index];
  }

  private static char getExtendedPtDeChar(byte ccData) {
    int index = ccData & 0x1F;
    return (char) SPECIAL_PT_DE_CHARACTER_SET[index];
  }

  private static boolean isCtrlCode(byte cc1) {
    // cc1 - 0|0|0|X|X|X|X|X
    return (cc1 & 0xE0) == 0x00;
  }

  private static int getChannel(byte cc1) {
    // cc1 - X|X|X|X|C|X|X|X
    return (cc1 >> 3) & 0x1;
  }

  private static boolean isMidrowCtrlCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|0|0|1
    // cc2 - 0|0|1|0|X|X|X|X
    return ((cc1 & 0xF7) == 0x11) && ((cc2 & 0xF0) == 0x20);
  }

  private static boolean isPreambleAddressCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|X|X|X
    // cc2 - 0|1|X|X|X|X|X|X
    return ((cc1 & 0xF0) == 0x10) && ((cc2 & 0xC0) == 0x40);
  }

  private static boolean isTabCtrlCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|1|1|1
    // cc2 - 0|0|1|0|0|0|0|1 to 0|0|1|0|0|0|1|1
    return ((cc1 & 0xF7) == 0x17) && (cc2 >= 0x21 && cc2 <= 0x23);
  }

  private static boolean isMiscCode(byte cc1, byte cc2) {
    // cc1 - 0|0|0|1|C|1|0|F
    // cc2 - 0|0|1|0|X|X|X|X
    return ((cc1 & 0xF6) == 0x14) && ((cc2 & 0xF0) == 0x20);
  }

  private static boolean isRepeatable(byte cc1) {
    // cc1 - 0|0|0|1|X|X|X|X
    return (cc1 & 0xF0) == 0x10;
  }

  private static boolean isXdsControlCode(byte cc1) {
    return 0x01 <= cc1 && cc1 <= 0x0F;
  }

  private static boolean isServiceSwitchCommand(byte cc1) {
    // cc1 - 0|0|0|1|C|1|0|0
    return (cc1 & 0xF7) == 0x14;
  }

  private static class CueBuilder {

    // 608 captions define a 15 row by 32 column screen grid. These constants convert from 608
    // positions to normalized screen position.
    private static final int SCREEN_CHARWIDTH = 32;
    private static final int BASE_ROW = 15;

    private final List<CueStyle> cueStyles;
    private final List<SpannableString> rolledUpCaptions;
    private final StringBuilder captionStringBuilder;

    private int row;
    private int indent;
    private int tabOffset;
    private int captionMode;
    private int captionRowCount;

    public CueBuilder(int captionMode, int captionRowCount) {
      cueStyles = new ArrayList<>();
      rolledUpCaptions = new ArrayList<>();
      captionStringBuilder = new StringBuilder();
      reset(captionMode);
      setCaptionRowCount(captionRowCount);
    }

    public void reset(int captionMode) {
      this.captionMode = captionMode;
      cueStyles.clear();
      rolledUpCaptions.clear();
      captionStringBuilder.setLength(0);
      row = BASE_ROW;
      indent = 0;
      tabOffset = 0;
    }

    public boolean isEmpty() {
      return cueStyles.isEmpty()
          && rolledUpCaptions.isEmpty()
          && captionStringBuilder.length() == 0;
    }

    public void setCaptionMode(int captionMode) {
      this.captionMode = captionMode;
    }

    public void setCaptionRowCount(int captionRowCount) {
      this.captionRowCount = captionRowCount;
    }

    public void setStyle(int style, boolean underline) {
      cueStyles.add(new CueStyle(style, underline, captionStringBuilder.length()));
    }

    public void backspace() {
      int length = captionStringBuilder.length();
      if (length > 0) {
        captionStringBuilder.delete(length - 1, length);
        // Decrement style start positions if necessary.
        for (int i = cueStyles.size() - 1; i >= 0; i--) {
          CueStyle style = cueStyles.get(i);
          if (style.start == length) {
            style.start--;
          } else {
            // All earlier cues must have style.start < length.
            break;
          }
        }
      }
    }

    public void append(char text) {
      captionStringBuilder.append(text);
    }

    public void rollUp() {
      rolledUpCaptions.add(buildCurrentLine());
      captionStringBuilder.setLength(0);
      cueStyles.clear();
      int numRows = Math.min(captionRowCount, row);
      while (rolledUpCaptions.size() >= numRows) {
        rolledUpCaptions.remove(0);
      }
    }

    public Cue build(@Cue.AnchorType int forcedPositionAnchor) {
      SpannableStringBuilder cueString = new SpannableStringBuilder();
      // Add any rolled up captions, separated by new lines.
      for (int i = 0; i < rolledUpCaptions.size(); i++) {
        cueString.append(rolledUpCaptions.get(i));
        cueString.append('\n');
      }
      // Add the current line.
      cueString.append(buildCurrentLine());

      if (cueString.length() == 0) {
        // The cue is empty.
        return null;
      }

      int positionAnchor;
      // The number of empty columns before the start of the text, in the range [0-31].
      int startPadding = indent + tabOffset;
      // The number of empty columns after the end of the text, in the same range.
      int endPadding = SCREEN_CHARWIDTH - startPadding - cueString.length();
      int startEndPaddingDelta = startPadding - endPadding;
      if (forcedPositionAnchor != Cue.TYPE_UNSET) {
        positionAnchor = forcedPositionAnchor;
      } else if (captionMode == CC_MODE_POP_ON
          && (Math.abs(startEndPaddingDelta) < 3 || endPadding < 0)) {
        // Treat approximately centered pop-on captions as middle aligned. We also treat captions
        // that are wider than they should be in this way. See
        // https://github.com/google/ExoPlayer/issues/3534.
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
      } else if (captionMode == CC_MODE_POP_ON && startEndPaddingDelta > 0) {
        // Treat pop-on captions with less padding at the end than the start as end aligned.
        positionAnchor = Cue.ANCHOR_TYPE_END;
      } else {
        // For all other cases assume start aligned.
        positionAnchor = Cue.ANCHOR_TYPE_START;
      }

      float position;
      switch (positionAnchor) {
        case Cue.ANCHOR_TYPE_MIDDLE:
          position = 0.5f;
          break;
        case Cue.ANCHOR_TYPE_END:
          position = (float) (SCREEN_CHARWIDTH - endPadding) / SCREEN_CHARWIDTH;
          // Adjust the position to fit within the safe area.
          position = position * 0.8f + 0.1f;
          break;
        case Cue.ANCHOR_TYPE_START:
        default:
          position = (float) startPadding / SCREEN_CHARWIDTH;
          // Adjust the position to fit within the safe area.
          position = position * 0.8f + 0.1f;
          break;
      }

      int lineAnchor;
      int line;
      // Note: Row indices are in the range [1-15].
      if (captionMode == CC_MODE_ROLL_UP || row > (BASE_ROW / 2)) {
        lineAnchor = Cue.ANCHOR_TYPE_END;
        line = row - BASE_ROW;
        // Two line adjustments. The first is because line indices from the bottom of the window
        // start from -1 rather than 0. The second is a blank row to act as the safe area.
        line -= 2;
      } else {
        lineAnchor = Cue.ANCHOR_TYPE_START;
        // Line indices from the top of the window start from 0, but we want a blank row to act as
        // the safe area. As a result no adjustment is necessary.
        line = row;
      }

      return new Cue(
          cueString,
          Alignment.ALIGN_NORMAL,
          line,
          Cue.LINE_TYPE_NUMBER,
          lineAnchor,
          position,
          positionAnchor,
          Cue.DIMEN_UNSET);
    }

    private SpannableString buildCurrentLine() {
      SpannableStringBuilder builder = new SpannableStringBuilder(captionStringBuilder);
      int length = builder.length();

      int underlineStartPosition = C.INDEX_UNSET;
      int italicStartPosition = C.INDEX_UNSET;
      int colorStartPosition = 0;
      int color = Color.WHITE;

      boolean nextItalic = false;
      int nextColor = Color.WHITE;

      for (int i = 0; i < cueStyles.size(); i++) {
        CueStyle cueStyle = cueStyles.get(i);
        boolean underline = cueStyle.underline;
        int style = cueStyle.style;
        if (style != STYLE_UNCHANGED) {
          // If the style is a color then italic is cleared.
          nextItalic = style == STYLE_ITALICS;
          // If the style is italic then the color is left unchanged.
          nextColor = style == STYLE_ITALICS ? nextColor : STYLE_COLORS[style];
        }

        int position = cueStyle.start;
        int nextPosition = (i + 1) < cueStyles.size() ? cueStyles.get(i + 1).start : length;
        if (position == nextPosition) {
          // There are more cueStyles to process at the current position.
          continue;
        }

        // Process changes to underline up to the current position.
        if (underlineStartPosition != C.INDEX_UNSET && !underline) {
          setUnderlineSpan(builder, underlineStartPosition, position);
          underlineStartPosition = C.INDEX_UNSET;
        } else if (underlineStartPosition == C.INDEX_UNSET && underline) {
          underlineStartPosition = position;
        }
        // Process changes to italic up to the current position.
        if (italicStartPosition != C.INDEX_UNSET && !nextItalic) {
          setItalicSpan(builder, italicStartPosition, position);
          italicStartPosition = C.INDEX_UNSET;
        } else if (italicStartPosition == C.INDEX_UNSET && nextItalic) {
          italicStartPosition = position;
        }
        // Process changes to color up to the current position.
        if (nextColor != color) {
          setColorSpan(builder, colorStartPosition, position, color);
          color = nextColor;
          colorStartPosition = position;
        }
      }

      // Add any final spans.
      if (underlineStartPosition != C.INDEX_UNSET && underlineStartPosition != length) {
        setUnderlineSpan(builder, underlineStartPosition, length);
      }
      if (italicStartPosition != C.INDEX_UNSET && italicStartPosition != length) {
        setItalicSpan(builder, italicStartPosition, length);
      }
      if (colorStartPosition != length) {
        setColorSpan(builder, colorStartPosition, length, color);
      }

      return new SpannableString(builder);
    }

    private static void setUnderlineSpan(SpannableStringBuilder builder, int start, int end) {
      builder.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void setItalicSpan(SpannableStringBuilder builder, int start, int end) {
      builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void setColorSpan(
        SpannableStringBuilder builder, int start, int end, int color) {
      if (color == Color.WHITE) {
        // White is treated as the default color (i.e. no span is attached).
        return;
      }
      builder.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static class CueStyle {

      public final int style;
      public final boolean underline;

      public int start;

      public CueStyle(int style, boolean underline, int start) {
        this.style = style;
        this.underline = underline;
        this.start = start;
      }

    }

  }

}
