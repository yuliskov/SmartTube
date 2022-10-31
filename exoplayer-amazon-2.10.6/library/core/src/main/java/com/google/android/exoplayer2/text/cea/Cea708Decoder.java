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
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.Cue.AnchorType;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoder;
import com.google.android.exoplayer2.text.SubtitleInputBuffer;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link SubtitleDecoder} for CEA-708 (also known as "EIA-708").
 */
public final class Cea708Decoder extends CeaDecoder {

  private static final String TAG = "Cea708Decoder";

  private static final int NUM_WINDOWS = 8;

  private static final int DTVCC_PACKET_DATA = 0x02;
  private static final int DTVCC_PACKET_START = 0x03;
  private static final int CC_VALID_FLAG = 0x04;

  // Base Commands
  private static final int GROUP_C0_END = 0x1F;  // Miscellaneous Control Codes
  private static final int GROUP_G0_END = 0x7F;  // ASCII Printable Characters
  private static final int GROUP_C1_END = 0x9F;  // Captioning Command Control Codes
  private static final int GROUP_G1_END = 0xFF;  // ISO 8859-1 LATIN-1 Character Set

  // Extended Commands
  private static final int GROUP_C2_END = 0x1F;  // Extended Control Code Set 1
  private static final int GROUP_G2_END = 0x7F;  // Extended Miscellaneous Characters
  private static final int GROUP_C3_END = 0x9F;  // Extended Control Code Set 2
  private static final int GROUP_G3_END = 0xFF;  // Future Expansion

  // Group C0 Commands
  private static final int COMMAND_NUL = 0x00;        // Nul
  private static final int COMMAND_ETX = 0x03;        // EndOfText
  private static final int COMMAND_BS = 0x08;         // Backspace
  private static final int COMMAND_FF = 0x0C;         // FormFeed (Flush)
  private static final int COMMAND_CR = 0x0D;         // CarriageReturn
  private static final int COMMAND_HCR = 0x0E;        // ClearLine
  private static final int COMMAND_EXT1 = 0x10;       // Extended Control Code Flag
  private static final int COMMAND_EXT1_START = 0x11;
  private static final int COMMAND_EXT1_END = 0x17;
  private static final int COMMAND_P16_START = 0x18;
  private static final int COMMAND_P16_END = 0x1F;

  // Group C1 Commands
  private static final int COMMAND_CW0 = 0x80;  // SetCurrentWindow to 0
  private static final int COMMAND_CW1 = 0x81;  // SetCurrentWindow to 1
  private static final int COMMAND_CW2 = 0x82;  // SetCurrentWindow to 2
  private static final int COMMAND_CW3 = 0x83;  // SetCurrentWindow to 3
  private static final int COMMAND_CW4 = 0x84;  // SetCurrentWindow to 4
  private static final int COMMAND_CW5 = 0x85;  // SetCurrentWindow to 5
  private static final int COMMAND_CW6 = 0x86;  // SetCurrentWindow to 6
  private static final int COMMAND_CW7 = 0x87;  // SetCurrentWindow to 7
  private static final int COMMAND_CLW = 0x88;  // ClearWindows (+1 byte)
  private static final int COMMAND_DSW = 0x89;  // DisplayWindows (+1 byte)
  private static final int COMMAND_HDW = 0x8A;  // HideWindows (+1 byte)
  private static final int COMMAND_TGW = 0x8B;  // ToggleWindows (+1 byte)
  private static final int COMMAND_DLW = 0x8C;  // DeleteWindows (+1 byte)
  private static final int COMMAND_DLY = 0x8D;  // Delay (+1 byte)
  private static final int COMMAND_DLC = 0x8E;  // DelayCancel
  private static final int COMMAND_RST = 0x8F;  // Reset
  private static final int COMMAND_SPA = 0x90;  // SetPenAttributes (+2 bytes)
  private static final int COMMAND_SPC = 0x91;  // SetPenColor (+3 bytes)
  private static final int COMMAND_SPL = 0x92;  // SetPenLocation (+2 bytes)
  private static final int COMMAND_SWA = 0x97;  // SetWindowAttributes (+4 bytes)
  private static final int COMMAND_DF0 = 0x98;  // DefineWindow 0 (+6 bytes)
  private static final int COMMAND_DF1 = 0x99;  // DefineWindow 1 (+6 bytes)
  private static final int COMMAND_DF2 = 0x9A;  // DefineWindow 2 (+6 bytes)
  private static final int COMMAND_DF3 = 0x9B;  // DefineWindow 3 (+6 bytes)
  private static final int COMMAND_DF4 = 0x9C; // DefineWindow 4 (+6 bytes)
  private static final int COMMAND_DF5 = 0x9D;  // DefineWindow 5 (+6 bytes)
  private static final int COMMAND_DF6 = 0x9E;  // DefineWindow 6 (+6 bytes)
  private static final int COMMAND_DF7 = 0x9F;  // DefineWindow 7 (+6 bytes)

  // G0 Table Special Chars
  private static final int CHARACTER_MN = 0x7F;  // MusicNote

  // G2 Table Special Chars
  private static final int CHARACTER_TSP = 0x20;
  private static final int CHARACTER_NBTSP = 0x21;
  private static final int CHARACTER_ELLIPSIS = 0x25;
  private static final int CHARACTER_BIG_CARONS = 0x2A;
  private static final int CHARACTER_BIG_OE = 0x2C;
  private static final int CHARACTER_SOLID_BLOCK = 0x30;
  private static final int CHARACTER_OPEN_SINGLE_QUOTE = 0x31;
  private static final int CHARACTER_CLOSE_SINGLE_QUOTE = 0x32;
  private static final int CHARACTER_OPEN_DOUBLE_QUOTE = 0x33;
  private static final int CHARACTER_CLOSE_DOUBLE_QUOTE = 0x34;
  private static final int CHARACTER_BOLD_BULLET = 0x35;
  private static final int CHARACTER_TM = 0x39;
  private static final int CHARACTER_SMALL_CARONS = 0x3A;
  private static final int CHARACTER_SMALL_OE = 0x3C;
  private static final int CHARACTER_SM = 0x3D;
  private static final int CHARACTER_DIAERESIS_Y = 0x3F;
  private static final int CHARACTER_ONE_EIGHTH = 0x76;
  private static final int CHARACTER_THREE_EIGHTHS = 0x77;
  private static final int CHARACTER_FIVE_EIGHTHS = 0x78;
  private static final int CHARACTER_SEVEN_EIGHTHS = 0x79;
  private static final int CHARACTER_VERTICAL_BORDER = 0x7A;
  private static final int CHARACTER_UPPER_RIGHT_BORDER = 0x7B;
  private static final int CHARACTER_LOWER_LEFT_BORDER = 0x7C;
  private static final int CHARACTER_HORIZONTAL_BORDER = 0x7D;
  private static final int CHARACTER_LOWER_RIGHT_BORDER = 0x7E;
  private static final int CHARACTER_UPPER_LEFT_BORDER = 0x7F;

  private final ParsableByteArray ccData;
  private final ParsableBitArray serviceBlockPacket;

  private final int selectedServiceNumber;
  private final CueBuilder[] cueBuilders;

  private CueBuilder currentCueBuilder;
  private List<Cue> cues;
  private List<Cue> lastCues;

  private DtvCcPacket currentDtvCcPacket;
  private int currentWindow;

  public Cea708Decoder(int accessibilityChannel, List<byte[]> initializationData) {
    ccData = new ParsableByteArray();
    serviceBlockPacket = new ParsableBitArray();
    selectedServiceNumber = accessibilityChannel == Format.NO_VALUE ? 1 : accessibilityChannel;

    cueBuilders = new CueBuilder[NUM_WINDOWS];
    for (int i = 0; i < NUM_WINDOWS; i++) {
      cueBuilders[i] = new CueBuilder();
    }

    currentCueBuilder = cueBuilders[0];
    resetCueBuilders();
  }

  @Override
  public String getName() {
    return "Cea708Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    cues = null;
    lastCues = null;
    currentWindow = 0;
    currentCueBuilder = cueBuilders[currentWindow];
    resetCueBuilders();
    currentDtvCcPacket = null;
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

  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    // Subtitle input buffers are non-direct and the position is zero, so calling array() is safe.
    @SuppressWarnings("ByteBufferBackingArray")
    byte[] inputBufferData = inputBuffer.data.array();
    ccData.reset(inputBufferData, inputBuffer.data.limit());
    while (ccData.bytesLeft() >= 3) {
      int ccTypeAndValid = (ccData.readUnsignedByte() & 0x07);

      int ccType = ccTypeAndValid & (DTVCC_PACKET_DATA | DTVCC_PACKET_START);
      boolean ccValid = (ccTypeAndValid & CC_VALID_FLAG) == CC_VALID_FLAG;
      byte ccData1 = (byte) ccData.readUnsignedByte();
      byte ccData2 = (byte) ccData.readUnsignedByte();

      // Ignore any non-CEA-708 data
      if (ccType != DTVCC_PACKET_DATA && ccType != DTVCC_PACKET_START) {
        continue;
      }

      if (!ccValid) {
        // This byte-pair isn't valid, ignore it and continue.
        continue;
      }

      if (ccType == DTVCC_PACKET_START) {
        finalizeCurrentPacket();

        int sequenceNumber = (ccData1 & 0xC0) >> 6; // first 2 bits
        int packetSize = ccData1 & 0x3F; // last 6 bits
        if (packetSize == 0) {
          packetSize = 64;
        }

        currentDtvCcPacket = new DtvCcPacket(sequenceNumber, packetSize);
        currentDtvCcPacket.packetData[currentDtvCcPacket.currentIndex++] = ccData2;
      } else {
        // The only remaining valid packet type is DTVCC_PACKET_DATA
        Assertions.checkArgument(ccType == DTVCC_PACKET_DATA);

        if (currentDtvCcPacket == null) {
          Log.e(TAG, "Encountered DTVCC_PACKET_DATA before DTVCC_PACKET_START");
          continue;
        }

        currentDtvCcPacket.packetData[currentDtvCcPacket.currentIndex++] = ccData1;
        currentDtvCcPacket.packetData[currentDtvCcPacket.currentIndex++] = ccData2;
      }

      if (currentDtvCcPacket.currentIndex == (currentDtvCcPacket.packetSize * 2 - 1)) {
        finalizeCurrentPacket();
      }
    }
  }

  private void finalizeCurrentPacket() {
    if (currentDtvCcPacket == null) {
      // No packet to finalize;
      return;
    }

    processCurrentPacket();
    currentDtvCcPacket = null;
  }

  private void processCurrentPacket() {
    if (currentDtvCcPacket.currentIndex != (currentDtvCcPacket.packetSize * 2 - 1)) {
      Log.w(TAG, "DtvCcPacket ended prematurely; size is " + (currentDtvCcPacket.packetSize * 2 - 1)
          + ", but current index is " + currentDtvCcPacket.currentIndex + " (sequence number "
          + currentDtvCcPacket.sequenceNumber + "); ignoring packet");
      return;
    }

    serviceBlockPacket.reset(currentDtvCcPacket.packetData, currentDtvCcPacket.currentIndex);

    int serviceNumber = serviceBlockPacket.readBits(3);
    int blockSize = serviceBlockPacket.readBits(5);
    if (serviceNumber == 7) {
      // extended service numbers
      serviceBlockPacket.skipBits(2);
      serviceNumber = serviceBlockPacket.readBits(6);
      if (serviceNumber < 7) {
        Log.w(TAG, "Invalid extended service number: " + serviceNumber);
      }
    }

    // Ignore packets in which blockSize is 0
    if (blockSize == 0) {
      if (serviceNumber != 0) {
        Log.w(TAG, "serviceNumber is non-zero (" + serviceNumber + ") when blockSize is 0");
      }
      return;
    }

    if (serviceNumber != selectedServiceNumber) {
      return;
    }

    // The cues should be updated if we receive a C0 ETX command, any C1 command, or if after
    // processing the service block any text has been added to the buffer. See CEA-708-B Section
    // 8.10.4 for more details.
    boolean cuesNeedUpdate = false;

    while (serviceBlockPacket.bitsLeft() > 0) {
      int command = serviceBlockPacket.readBits(8);
      if (command != COMMAND_EXT1) {
        if (command <= GROUP_C0_END) {
          handleC0Command(command);
          // If the C0 command was an ETX command, the cues are updated in handleC0Command.
        } else if (command <= GROUP_G0_END) {
          handleG0Character(command);
          cuesNeedUpdate = true;
        } else if (command <= GROUP_C1_END) {
          handleC1Command(command);
          cuesNeedUpdate = true;
        } else if (command <= GROUP_G1_END) {
          handleG1Character(command);
          cuesNeedUpdate = true;
        } else {
          Log.w(TAG, "Invalid base command: " + command);
        }
      } else {
        // Read the extended command
        command = serviceBlockPacket.readBits(8);
        if (command <= GROUP_C2_END) {
          handleC2Command(command);
        } else if (command <= GROUP_G2_END) {
          handleG2Character(command);
          cuesNeedUpdate = true;
        } else if (command <= GROUP_C3_END) {
          handleC3Command(command);
        } else if (command <= GROUP_G3_END) {
          handleG3Character(command);
          cuesNeedUpdate = true;
        } else {
          Log.w(TAG, "Invalid extended command: " + command);
        }
      }
    }

    if (cuesNeedUpdate) {
      cues = getDisplayCues();
    }
  }

  private void handleC0Command(int command) {
    switch (command) {
      case COMMAND_NUL:
        // Do nothing.
        break;
      case COMMAND_ETX:
        cues = getDisplayCues();
        break;
      case COMMAND_BS:
        currentCueBuilder.backspace();
        break;
      case COMMAND_FF:
        resetCueBuilders();
        break;
      case COMMAND_CR:
        currentCueBuilder.append('\n');
        break;
      case COMMAND_HCR:
        // TODO: Add support for this command.
        break;
      default:
        if (command >= COMMAND_EXT1_START && command <= COMMAND_EXT1_END) {
          Log.w(TAG, "Currently unsupported COMMAND_EXT1 Command: " + command);
          serviceBlockPacket.skipBits(8);
        } else if (command >= COMMAND_P16_START && command <= COMMAND_P16_END) {
          Log.w(TAG, "Currently unsupported COMMAND_P16 Command: " + command);
          serviceBlockPacket.skipBits(16);
        } else {
          Log.w(TAG, "Invalid C0 command: " + command);
        }
    }
  }

  private void handleC1Command(int command) {
    int window;
    switch (command) {
      case COMMAND_CW0:
      case COMMAND_CW1:
      case COMMAND_CW2:
      case COMMAND_CW3:
      case COMMAND_CW4:
      case COMMAND_CW5:
      case COMMAND_CW6:
      case COMMAND_CW7:
        window = (command - COMMAND_CW0);
        if (currentWindow != window) {
          currentWindow = window;
          currentCueBuilder = cueBuilders[window];
        }
        break;
      case COMMAND_CLW:
        for (int i = 1; i <= NUM_WINDOWS; i++) {
          if (serviceBlockPacket.readBit()) {
            cueBuilders[NUM_WINDOWS - i].clear();
          }
        }
        break;
      case COMMAND_DSW:
        for (int i = 1; i <= NUM_WINDOWS; i++) {
          if (serviceBlockPacket.readBit()) {
            cueBuilders[NUM_WINDOWS - i].setVisibility(true);
          }
        }
        break;
      case COMMAND_HDW:
        for (int i = 1; i <= NUM_WINDOWS; i++) {
          if (serviceBlockPacket.readBit()) {
            cueBuilders[NUM_WINDOWS - i].setVisibility(false);
          }
        }
        break;
      case COMMAND_TGW:
        for (int i = 1; i <= NUM_WINDOWS; i++) {
          if (serviceBlockPacket.readBit()) {
            CueBuilder cueBuilder = cueBuilders[NUM_WINDOWS - i];
            cueBuilder.setVisibility(!cueBuilder.isVisible());
          }
        }
        break;
      case COMMAND_DLW:
        for (int i = 1; i <= NUM_WINDOWS; i++) {
          if (serviceBlockPacket.readBit()) {
            cueBuilders[NUM_WINDOWS - i].reset();
          }
        }
        break;
      case COMMAND_DLY:
        // TODO: Add support for delay commands.
        serviceBlockPacket.skipBits(8);
        break;
      case COMMAND_DLC:
        // TODO: Add support for delay commands.
        break;
      case COMMAND_RST:
        resetCueBuilders();
        break;
      case COMMAND_SPA:
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          serviceBlockPacket.skipBits(16);
        } else {
          handleSetPenAttributes();
        }
        break;
      case COMMAND_SPC:
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          serviceBlockPacket.skipBits(24);
        } else {
          handleSetPenColor();
        }
        break;
      case COMMAND_SPL:
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          serviceBlockPacket.skipBits(16);
        } else {
          handleSetPenLocation();
        }
        break;
      case COMMAND_SWA:
        if (!currentCueBuilder.isDefined()) {
          // ignore this command if the current window/cue isn't defined
          serviceBlockPacket.skipBits(32);
        } else {
          handleSetWindowAttributes();
        }
        break;
      case COMMAND_DF0:
      case COMMAND_DF1:
      case COMMAND_DF2:
      case COMMAND_DF3:
      case COMMAND_DF4:
      case COMMAND_DF5:
      case COMMAND_DF6:
      case COMMAND_DF7:
        window = (command - COMMAND_DF0);
        handleDefineWindow(window);
        // We also set the current window to the newly defined window.
        if (currentWindow != window) {
          currentWindow = window;
          currentCueBuilder = cueBuilders[window];
        }
        break;
      default:
        Log.w(TAG, "Invalid C1 command: " + command);
    }
  }

  private void handleC2Command(int command) {
    // C2 Table doesn't contain any commands in CEA-708-B, but we do need to skip bytes
    if (command <= 0x07) {
      // Do nothing.
    } else if (command <= 0x0F) {
      serviceBlockPacket.skipBits(8);
    } else if (command <= 0x17) {
      serviceBlockPacket.skipBits(16);
    } else if (command <= 0x1F) {
      serviceBlockPacket.skipBits(24);
    }
  }

  private void handleC3Command(int command) {
    // C3 Table doesn't contain any commands in CEA-708-B, but we do need to skip bytes
    if (command <= 0x87) {
      serviceBlockPacket.skipBits(32);
    } else if (command <= 0x8F) {
      serviceBlockPacket.skipBits(40);
    } else if (command <= 0x9F) {
      // 90-9F are variable length codes; the first byte defines the header with the first
      // 2 bits specifying the type and the last 6 bits specifying the remaining length of the
      // command in bytes
      serviceBlockPacket.skipBits(2);
      int length = serviceBlockPacket.readBits(6);
      serviceBlockPacket.skipBits(8 * length);
    }
  }

  private void handleG0Character(int characterCode) {
    if (characterCode == CHARACTER_MN) {
      currentCueBuilder.append('\u266B');
    } else {
      currentCueBuilder.append((char) (characterCode & 0xFF));
    }
  }

  private void handleG1Character(int characterCode) {
    currentCueBuilder.append((char) (characterCode & 0xFF));
  }

  private void handleG2Character(int characterCode) {
    switch (characterCode) {
      case CHARACTER_TSP:
        currentCueBuilder.append('\u0020');
        break;
      case CHARACTER_NBTSP:
        currentCueBuilder.append('\u00A0');
        break;
      case CHARACTER_ELLIPSIS:
        currentCueBuilder.append('\u2026');
        break;
      case CHARACTER_BIG_CARONS:
        currentCueBuilder.append('\u0160');
        break;
      case CHARACTER_BIG_OE:
        currentCueBuilder.append('\u0152');
        break;
      case CHARACTER_SOLID_BLOCK:
        currentCueBuilder.append('\u2588');
        break;
      case CHARACTER_OPEN_SINGLE_QUOTE:
        currentCueBuilder.append('\u2018');
        break;
      case CHARACTER_CLOSE_SINGLE_QUOTE:
        currentCueBuilder.append('\u2019');
        break;
      case CHARACTER_OPEN_DOUBLE_QUOTE:
        currentCueBuilder.append('\u201C');
        break;
      case CHARACTER_CLOSE_DOUBLE_QUOTE:
        currentCueBuilder.append('\u201D');
        break;
      case CHARACTER_BOLD_BULLET:
        currentCueBuilder.append('\u2022');
        break;
      case CHARACTER_TM:
        currentCueBuilder.append('\u2122');
        break;
      case CHARACTER_SMALL_CARONS:
        currentCueBuilder.append('\u0161');
        break;
      case CHARACTER_SMALL_OE:
        currentCueBuilder.append('\u0153');
        break;
      case CHARACTER_SM:
        currentCueBuilder.append('\u2120');
        break;
      case CHARACTER_DIAERESIS_Y:
        currentCueBuilder.append('\u0178');
        break;
      case CHARACTER_ONE_EIGHTH:
        currentCueBuilder.append('\u215B');
        break;
      case CHARACTER_THREE_EIGHTHS:
        currentCueBuilder.append('\u215C');
        break;
      case CHARACTER_FIVE_EIGHTHS:
        currentCueBuilder.append('\u215D');
        break;
      case CHARACTER_SEVEN_EIGHTHS:
        currentCueBuilder.append('\u215E');
        break;
      case CHARACTER_VERTICAL_BORDER:
        currentCueBuilder.append('\u2502');
        break;
      case CHARACTER_UPPER_RIGHT_BORDER:
        currentCueBuilder.append('\u2510');
        break;
      case CHARACTER_LOWER_LEFT_BORDER:
        currentCueBuilder.append('\u2514');
        break;
      case CHARACTER_HORIZONTAL_BORDER:
        currentCueBuilder.append('\u2500');
        break;
      case CHARACTER_LOWER_RIGHT_BORDER:
        currentCueBuilder.append('\u2518');
        break;
      case CHARACTER_UPPER_LEFT_BORDER:
        currentCueBuilder.append('\u250C');
        break;
      default:
        Log.w(TAG, "Invalid G2 character: " + characterCode);
        // The CEA-708 specification doesn't specify what to do in the case of an unexpected
        // value in the G2 character range, so we ignore it.
    }
  }

  private void handleG3Character(int characterCode) {
    if (characterCode == 0xA0) {
      currentCueBuilder.append('\u33C4');
    } else {
      Log.w(TAG, "Invalid G3 character: " + characterCode);
      // Substitute any unsupported G3 character with an underscore as per CEA-708 specification.
      currentCueBuilder.append('_');
    }
  }

  private void handleSetPenAttributes() {
    // the SetPenAttributes command contains 2 bytes of data
    // first byte
    int textTag = serviceBlockPacket.readBits(4);
    int offset = serviceBlockPacket.readBits(2);
    int penSize = serviceBlockPacket.readBits(2);
    // second byte
    boolean italicsToggle = serviceBlockPacket.readBit();
    boolean underlineToggle = serviceBlockPacket.readBit();
    int edgeType = serviceBlockPacket.readBits(3);
    int fontStyle = serviceBlockPacket.readBits(3);

    currentCueBuilder.setPenAttributes(textTag, offset, penSize, italicsToggle, underlineToggle,
        edgeType, fontStyle);
  }

  private void handleSetPenColor() {
    // the SetPenColor command contains 3 bytes of data
    // first byte
    int foregroundO = serviceBlockPacket.readBits(2);
    int foregroundR = serviceBlockPacket.readBits(2);
    int foregroundG = serviceBlockPacket.readBits(2);
    int foregroundB = serviceBlockPacket.readBits(2);
    int foregroundColor = CueBuilder.getArgbColorFromCeaColor(foregroundR, foregroundG, foregroundB,
        foregroundO);
    // second byte
    int backgroundO = serviceBlockPacket.readBits(2);
    int backgroundR = serviceBlockPacket.readBits(2);
    int backgroundG = serviceBlockPacket.readBits(2);
    int backgroundB = serviceBlockPacket.readBits(2);
    int backgroundColor = CueBuilder.getArgbColorFromCeaColor(backgroundR, backgroundG, backgroundB,
        backgroundO);
    // third byte
    serviceBlockPacket.skipBits(2); // null padding
    int edgeR = serviceBlockPacket.readBits(2);
    int edgeG = serviceBlockPacket.readBits(2);
    int edgeB = serviceBlockPacket.readBits(2);
    int edgeColor = CueBuilder.getArgbColorFromCeaColor(edgeR, edgeG, edgeB);

    currentCueBuilder.setPenColor(foregroundColor, backgroundColor, edgeColor);
  }

  private void handleSetPenLocation() {
    // the SetPenLocation command contains 2 bytes of data
    // first byte
    serviceBlockPacket.skipBits(4);
    int row = serviceBlockPacket.readBits(4);
    // second byte
    serviceBlockPacket.skipBits(2);
    int column = serviceBlockPacket.readBits(6);

    currentCueBuilder.setPenLocation(row, column);
  }

  private void handleSetWindowAttributes() {
    // the SetWindowAttributes command contains 4 bytes of data
    // first byte
    int fillO = serviceBlockPacket.readBits(2);
    int fillR = serviceBlockPacket.readBits(2);
    int fillG = serviceBlockPacket.readBits(2);
    int fillB = serviceBlockPacket.readBits(2);
    int fillColor = CueBuilder.getArgbColorFromCeaColor(fillR, fillG, fillB, fillO);
    // second byte
    int borderType = serviceBlockPacket.readBits(2); // only the lower 2 bits of borderType
    int borderR = serviceBlockPacket.readBits(2);
    int borderG = serviceBlockPacket.readBits(2);
    int borderB = serviceBlockPacket.readBits(2);
    int borderColor = CueBuilder.getArgbColorFromCeaColor(borderR, borderG, borderB);
    // third byte
    if (serviceBlockPacket.readBit()) {
      borderType |= 0x04; // set the top bit of the 3-bit borderType
    }
    boolean wordWrapToggle = serviceBlockPacket.readBit();
    int printDirection = serviceBlockPacket.readBits(2);
    int scrollDirection = serviceBlockPacket.readBits(2);
    int justification = serviceBlockPacket.readBits(2);
    // fourth byte
    // Note that we don't intend to support display effects
    serviceBlockPacket.skipBits(8); // effectSpeed(4), effectDirection(2), displayEffect(2)

    currentCueBuilder.setWindowAttributes(fillColor, borderColor, wordWrapToggle, borderType,
        printDirection, scrollDirection, justification);
  }

  private void handleDefineWindow(int window) {
    CueBuilder cueBuilder = cueBuilders[window];

    // the DefineWindow command contains 6 bytes of data
    // first byte
    serviceBlockPacket.skipBits(2); // null padding
    boolean visible = serviceBlockPacket.readBit();
    boolean rowLock = serviceBlockPacket.readBit();
    boolean columnLock = serviceBlockPacket.readBit();
    int priority = serviceBlockPacket.readBits(3);
    // second byte
    boolean relativePositioning = serviceBlockPacket.readBit();
    int verticalAnchor = serviceBlockPacket.readBits(7);
    // third byte
    int horizontalAnchor = serviceBlockPacket.readBits(8);
    // fourth byte
    int anchorId = serviceBlockPacket.readBits(4);
    int rowCount = serviceBlockPacket.readBits(4);
    // fifth byte
    serviceBlockPacket.skipBits(2); // null padding
    int columnCount = serviceBlockPacket.readBits(6);
    // sixth byte
    serviceBlockPacket.skipBits(2); // null padding
    int windowStyle = serviceBlockPacket.readBits(3);
    int penStyle = serviceBlockPacket.readBits(3);

    cueBuilder.defineWindow(visible, rowLock, columnLock, priority, relativePositioning,
        verticalAnchor, horizontalAnchor, rowCount, columnCount, anchorId, windowStyle, penStyle);
  }

  private List<Cue> getDisplayCues() {
    List<Cea708Cue> displayCues = new ArrayList<>();
    for (int i = 0; i < NUM_WINDOWS; i++) {
      if (!cueBuilders[i].isEmpty() && cueBuilders[i].isVisible()) {
        displayCues.add(cueBuilders[i].build());
      }
    }
    Collections.sort(displayCues);
    return Collections.unmodifiableList(displayCues);
  }

  private void resetCueBuilders() {
    for (int i = 0; i < NUM_WINDOWS; i++) {
      cueBuilders[i].reset();
    }
  }

  private static final class DtvCcPacket {

    public final int sequenceNumber;
    public final int packetSize;
    public final byte[] packetData;

    int currentIndex;

    public DtvCcPacket(int sequenceNumber, int packetSize) {
      this.sequenceNumber = sequenceNumber;
      this.packetSize = packetSize;
      packetData = new byte[2 * packetSize - 1];
      currentIndex = 0;
    }

  }

  // TODO: There is a lot of overlap between Cea708Decoder.CueBuilder and Cea608Decoder.CueBuilder
  // which could be refactored into a separate class.
  private static final class CueBuilder {

    private static final int RELATIVE_CUE_SIZE = 99;
    private static final int VERTICAL_SIZE = 74;
    private static final int HORIZONTAL_SIZE = 209;

    private static final int DEFAULT_PRIORITY = 4;

    private static final int MAXIMUM_ROW_COUNT = 15;

    private static final int JUSTIFICATION_LEFT = 0;
    private static final int JUSTIFICATION_RIGHT = 1;
    private static final int JUSTIFICATION_CENTER = 2;
    private static final int JUSTIFICATION_FULL = 3;

    private static final int DIRECTION_LEFT_TO_RIGHT = 0;
    private static final int DIRECTION_RIGHT_TO_LEFT = 1;
    private static final int DIRECTION_TOP_TO_BOTTOM = 2;
    private static final int DIRECTION_BOTTOM_TO_TOP = 3;

    // TODO: Add other border/edge types when utilized.
    private static final int BORDER_AND_EDGE_TYPE_NONE = 0;
    private static final int BORDER_AND_EDGE_TYPE_UNIFORM = 3;

    public static final int COLOR_SOLID_WHITE = getArgbColorFromCeaColor(2, 2, 2, 0);
    public static final int COLOR_SOLID_BLACK = getArgbColorFromCeaColor(0, 0, 0, 0);
    public static final int COLOR_TRANSPARENT = getArgbColorFromCeaColor(0, 0, 0, 3);

    // TODO: Add other sizes when utilized.
    private static final int PEN_SIZE_STANDARD = 1;

    // TODO: Add other pen font styles when utilized.
    private static final int PEN_FONT_STYLE_DEFAULT = 0;
    private static final int PEN_FONT_STYLE_MONOSPACED_WITH_SERIFS = 1;
    private static final int PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITH_SERIFS = 2;
    private static final int PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS = 3;
    private static final int PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS = 4;

    // TODO: Add other pen offsets when utilized.
    private static final int PEN_OFFSET_NORMAL = 1;

    // The window style properties are specified in the CEA-708 specification.
    private static final int[] WINDOW_STYLE_JUSTIFICATION = new int[] {
        JUSTIFICATION_LEFT, JUSTIFICATION_LEFT, JUSTIFICATION_LEFT,
        JUSTIFICATION_LEFT, JUSTIFICATION_LEFT, JUSTIFICATION_CENTER,
        JUSTIFICATION_LEFT
    };
    private static final int[] WINDOW_STYLE_PRINT_DIRECTION = new int[] {
        DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT,
        DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT, DIRECTION_LEFT_TO_RIGHT,
        DIRECTION_TOP_TO_BOTTOM
    };
    private static final int[] WINDOW_STYLE_SCROLL_DIRECTION = new int[] {
        DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP,
        DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP, DIRECTION_BOTTOM_TO_TOP,
        DIRECTION_RIGHT_TO_LEFT
    };
    private static final boolean[] WINDOW_STYLE_WORD_WRAP = new boolean[] {
        false, false, false, true, true, true, false
    };
    private static final int[] WINDOW_STYLE_FILL = new int[] {
        COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK,
        COLOR_TRANSPARENT, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK
    };

    // The pen style properties are specified in the CEA-708 specification.
    private static final int[] PEN_STYLE_FONT_STYLE = new int[] {
        PEN_FONT_STYLE_DEFAULT, PEN_FONT_STYLE_MONOSPACED_WITH_SERIFS,
        PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITH_SERIFS, PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS,
        PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS,
        PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS,
        PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS
    };
    private static final int[] PEN_STYLE_EDGE_TYPE = new int[] {
        BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_NONE,
        BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_NONE, BORDER_AND_EDGE_TYPE_UNIFORM,
        BORDER_AND_EDGE_TYPE_UNIFORM
    };
    private static final int[] PEN_STYLE_BACKGROUND = new int[] {
        COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK,
        COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_TRANSPARENT};

    private final List<SpannableString> rolledUpCaptions;
    private final SpannableStringBuilder captionStringBuilder;

    // Window/Cue properties
    private boolean defined;
    private boolean visible;
    private int priority;
    private boolean relativePositioning;
    private int verticalAnchor;
    private int horizontalAnchor;
    private int anchorId;
    private int rowCount;
    private boolean rowLock;
    private int justification;
    private int windowStyleId;
    private int penStyleId;
    private int windowFillColor;

    // Pen/Text properties
    private int italicsStartPosition;
    private int underlineStartPosition;
    private int foregroundColorStartPosition;
    private int foregroundColor;
    private int backgroundColorStartPosition;
    private int backgroundColor;
    private int row;

    public CueBuilder() {
      rolledUpCaptions = new ArrayList<>();
      captionStringBuilder = new SpannableStringBuilder();
      reset();
    }

    public boolean isEmpty() {
      return !isDefined() || (rolledUpCaptions.isEmpty() && captionStringBuilder.length() == 0);
    }

    public void reset() {
      clear();

      defined = false;
      visible = false;
      priority = DEFAULT_PRIORITY;
      relativePositioning = false;
      verticalAnchor = 0;
      horizontalAnchor = 0;
      anchorId = 0;
      rowCount = MAXIMUM_ROW_COUNT;
      rowLock = true;
      justification = JUSTIFICATION_LEFT;
      windowStyleId = 0;
      penStyleId = 0;
      windowFillColor = COLOR_SOLID_BLACK;

      foregroundColor = COLOR_SOLID_WHITE;
      backgroundColor = COLOR_SOLID_BLACK;
    }

    public void clear() {
      rolledUpCaptions.clear();
      captionStringBuilder.clear();
      italicsStartPosition = C.POSITION_UNSET;
      underlineStartPosition = C.POSITION_UNSET;
      foregroundColorStartPosition = C.POSITION_UNSET;
      backgroundColorStartPosition = C.POSITION_UNSET;
      row = 0;
    }

    public boolean isDefined() {
      return defined;
    }

    public void setVisibility(boolean visible) {
      this.visible = visible;
    }

    public boolean isVisible() {
      return visible;
    }

    public void defineWindow(boolean visible, boolean rowLock, boolean columnLock, int priority,
        boolean relativePositioning, int verticalAnchor, int horizontalAnchor, int rowCount,
        int columnCount, int anchorId, int windowStyleId, int penStyleId) {
      this.defined = true;
      this.visible = visible;
      this.rowLock = rowLock;
      this.priority = priority;
      this.relativePositioning = relativePositioning;
      this.verticalAnchor = verticalAnchor;
      this.horizontalAnchor = horizontalAnchor;
      this.anchorId = anchorId;

      // Decoders must add one to rowCount to get the desired number of rows.
      if (this.rowCount != rowCount + 1) {
        this.rowCount = rowCount + 1;

        // Trim any rolled up captions that are no longer valid, if applicable.
        while ((rowLock && (rolledUpCaptions.size() >= this.rowCount))
            || (rolledUpCaptions.size() >= MAXIMUM_ROW_COUNT)) {
          rolledUpCaptions.remove(0);
        }
      }

      // TODO: Add support for column lock and count.

      if (windowStyleId != 0 && this.windowStyleId != windowStyleId) {
        this.windowStyleId = windowStyleId;
        // windowStyleId is 1-based.
        int windowStyleIdIndex = windowStyleId - 1;
        // Note that Border type and border color are the same for all window styles.
        setWindowAttributes(WINDOW_STYLE_FILL[windowStyleIdIndex], COLOR_TRANSPARENT,
            WINDOW_STYLE_WORD_WRAP[windowStyleIdIndex], BORDER_AND_EDGE_TYPE_NONE,
            WINDOW_STYLE_PRINT_DIRECTION[windowStyleIdIndex],
            WINDOW_STYLE_SCROLL_DIRECTION[windowStyleIdIndex],
            WINDOW_STYLE_JUSTIFICATION[windowStyleIdIndex]);
      }

      if (penStyleId != 0 && this.penStyleId != penStyleId) {
        this.penStyleId = penStyleId;
        // penStyleId is 1-based.
        int penStyleIdIndex = penStyleId - 1;
        // Note that pen size, offset, italics, underline, foreground color, and foreground
        // opacity are the same for all pen styles.
        setPenAttributes(0, PEN_OFFSET_NORMAL, PEN_SIZE_STANDARD, false, false,
            PEN_STYLE_EDGE_TYPE[penStyleIdIndex], PEN_STYLE_FONT_STYLE[penStyleIdIndex]);
        setPenColor(COLOR_SOLID_WHITE, PEN_STYLE_BACKGROUND[penStyleIdIndex], COLOR_SOLID_BLACK);
      }
    }


    public void setWindowAttributes(int fillColor, int borderColor, boolean wordWrapToggle,
        int borderType, int printDirection, int scrollDirection, int justification) {
      this.windowFillColor = fillColor;
      // TODO: Add support for border color and types.
      // TODO: Add support for word wrap.
      // TODO: Add support for other scroll directions.
      // TODO: Add support for other print directions.
      this.justification = justification;

    }

    public void setPenAttributes(int textTag, int offset, int penSize, boolean italicsToggle,
        boolean underlineToggle, int edgeType, int fontStyle) {
      // TODO: Add support for text tags.
      // TODO: Add support for other offsets.
      // TODO: Add support for other pen sizes.

      if (italicsStartPosition != C.POSITION_UNSET) {
        if (!italicsToggle) {
          captionStringBuilder.setSpan(new StyleSpan(Typeface.ITALIC), italicsStartPosition,
              captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          italicsStartPosition = C.POSITION_UNSET;
        }
      } else if (italicsToggle) {
        italicsStartPosition = captionStringBuilder.length();
      }

      if (underlineStartPosition != C.POSITION_UNSET) {
        if (!underlineToggle) {
          captionStringBuilder.setSpan(new UnderlineSpan(), underlineStartPosition,
              captionStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          underlineStartPosition = C.POSITION_UNSET;
        }
      } else if (underlineToggle) {
        underlineStartPosition = captionStringBuilder.length();
      }

      // TODO: Add support for edge types.
      // TODO: Add support for other font styles.
    }

    public void setPenColor(int foregroundColor, int backgroundColor, int edgeColor) {
      if (foregroundColorStartPosition != C.POSITION_UNSET) {
        if (this.foregroundColor != foregroundColor) {
          captionStringBuilder.setSpan(new ForegroundColorSpan(this.foregroundColor),
              foregroundColorStartPosition, captionStringBuilder.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      if (foregroundColor != COLOR_SOLID_WHITE) {
        foregroundColorStartPosition = captionStringBuilder.length();
        this.foregroundColor = foregroundColor;
      }

      if (backgroundColorStartPosition != C.POSITION_UNSET) {
        if (this.backgroundColor != backgroundColor) {
          captionStringBuilder.setSpan(new BackgroundColorSpan(this.backgroundColor),
              backgroundColorStartPosition, captionStringBuilder.length(),
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      if (backgroundColor != COLOR_SOLID_BLACK) {
        backgroundColorStartPosition = captionStringBuilder.length();
        this.backgroundColor = backgroundColor;
      }

      // TODO: Add support for edge color.
    }

    public void setPenLocation(int row, int column) {
      // TODO: Support moving the pen location with a window properly.

      // Until we support proper pen locations, if we encounter a row that's different from the
      // previous one, we should append a new line. Otherwise, we'll see strings that should be
      // on new lines concatenated with the previous, resulting in 2 words being combined, as
      // well as potentially drawing beyond the width of the window/screen.
      if (this.row != row) {
        append('\n');
      }
      this.row = row;
    }

    public void backspace() {
      int length = captionStringBuilder.length();
      if (length > 0) {
        captionStringBuilder.delete(length - 1, length);
      }
    }

    public void append(char text) {
      if (text == '\n') {
        rolledUpCaptions.add(buildSpannableString());
        captionStringBuilder.clear();

        if (italicsStartPosition != C.POSITION_UNSET) {
          italicsStartPosition = 0;
        }
        if (underlineStartPosition != C.POSITION_UNSET) {
          underlineStartPosition = 0;
        }
        if (foregroundColorStartPosition != C.POSITION_UNSET) {
          foregroundColorStartPosition = 0;
        }
        if (backgroundColorStartPosition != C.POSITION_UNSET) {
          backgroundColorStartPosition = 0;
        }

        while ((rowLock && (rolledUpCaptions.size() >= rowCount))
            || (rolledUpCaptions.size() >= MAXIMUM_ROW_COUNT)) {
          rolledUpCaptions.remove(0);
        }
      } else {
        captionStringBuilder.append(text);
      }
    }

    public SpannableString buildSpannableString() {
      SpannableStringBuilder spannableStringBuilder =
          new SpannableStringBuilder(captionStringBuilder);
      int length = spannableStringBuilder.length();

      if (length > 0) {
        if (italicsStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new StyleSpan(Typeface.ITALIC), italicsStartPosition,
              length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (underlineStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new UnderlineSpan(), underlineStartPosition,
              length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (foregroundColorStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new ForegroundColorSpan(foregroundColor),
              foregroundColorStartPosition, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (backgroundColorStartPosition != C.POSITION_UNSET) {
          spannableStringBuilder.setSpan(new BackgroundColorSpan(backgroundColor),
              backgroundColorStartPosition, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }

      return new SpannableString(spannableStringBuilder);
    }

    public Cea708Cue build() {
      if (isEmpty()) {
        // The cue is empty.
        return null;
      }

      SpannableStringBuilder cueString = new SpannableStringBuilder();

      // Add any rolled up captions, separated by new lines.
      for (int i = 0; i < rolledUpCaptions.size(); i++) {
        cueString.append(rolledUpCaptions.get(i));
        cueString.append('\n');
      }
      // Add the current line.
      cueString.append(buildSpannableString());

      // TODO: Add support for right-to-left languages (i.e. where right would correspond to normal
      // alignment).
      Alignment alignment;
      switch (justification) {
        case JUSTIFICATION_FULL:
          // TODO: Add support for full justification.
        case JUSTIFICATION_LEFT:
          alignment = Alignment.ALIGN_NORMAL;
          break;
        case JUSTIFICATION_RIGHT:
          alignment = Alignment.ALIGN_OPPOSITE;
          break;
        case JUSTIFICATION_CENTER:
          alignment = Alignment.ALIGN_CENTER;
          break;
        default:
          throw new IllegalArgumentException("Unexpected justification value: " + justification);
      }

      float position;
      float line;
      if (relativePositioning) {
        position = (float) horizontalAnchor / RELATIVE_CUE_SIZE;
        line = (float) verticalAnchor / RELATIVE_CUE_SIZE;
      } else {
        position = (float) horizontalAnchor / HORIZONTAL_SIZE;
        line = (float) verticalAnchor / VERTICAL_SIZE;
      }
      // Apply screen-edge padding to the line and position.
      position = (position * 0.9f) + 0.05f;
      line = (line * 0.9f) + 0.05f;

      // anchorId specifies where the anchor should be placed on the caption cue/window. The 9
      // possible configurations are as follows:
      //   0-----1-----2
      //   |           |
      //   3     4     5
      //   |           |
      //   6-----7-----8
      @AnchorType int verticalAnchorType;
      if (anchorId % 3 == 0) {
        verticalAnchorType = Cue.ANCHOR_TYPE_START;
      } else if (anchorId % 3 == 1) {
        verticalAnchorType = Cue.ANCHOR_TYPE_MIDDLE;
      } else {
        verticalAnchorType = Cue.ANCHOR_TYPE_END;
      }
      // TODO: Add support for right-to-left languages (i.e. where start is on the right).
      @AnchorType int horizontalAnchorType;
      if (anchorId / 3 == 0) {
        horizontalAnchorType = Cue.ANCHOR_TYPE_START;
      } else if (anchorId / 3 == 1) {
        horizontalAnchorType = Cue.ANCHOR_TYPE_MIDDLE;
      } else {
        horizontalAnchorType = Cue.ANCHOR_TYPE_END;
      }

      boolean windowColorSet = (windowFillColor != COLOR_SOLID_BLACK);

      return new Cea708Cue(cueString, alignment, line, Cue.LINE_TYPE_FRACTION, verticalAnchorType,
          position, horizontalAnchorType, Cue.DIMEN_UNSET, windowColorSet, windowFillColor,
          priority);
    }

    public static int getArgbColorFromCeaColor(int red, int green, int blue) {
      return getArgbColorFromCeaColor(red, green, blue, 0);
    }

    public static int getArgbColorFromCeaColor(int red, int green, int blue, int opacity) {
      Assertions.checkIndex(red, 0, 4);
      Assertions.checkIndex(green, 0, 4);
      Assertions.checkIndex(blue, 0, 4);
      Assertions.checkIndex(opacity, 0, 4);

      int alpha;
      switch (opacity) {
        case 0:
        case 1:
          // Note the value of '1' is actually FLASH, but we don't support that.
          alpha = 255;
          break;
        case 2:
          alpha = 127;
          break;
        case 3:
          alpha = 0;
          break;
        default:
          alpha = 255;
      }

      // TODO: Add support for the Alternative Minimum Color List or the full 64 RGB combinations.

      // Return values based on the Minimum Color List
      return Color.argb(alpha,
          (red > 1 ? 255 : 0),
          (green > 1 ? 255 : 0),
          (blue > 1 ? 255 : 0));
    }

  }

}
