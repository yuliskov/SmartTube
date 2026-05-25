package com.liskovsoft.smartyoutubetv2.common.vot;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class VotWireWriter {
    private final ByteArrayOutputStream mOut = new ByteArrayOutputStream();

    byte[] toByteArray() {
        return mOut.toByteArray();
    }

    void writeString(int fieldNumber, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeTag(fieldNumber, 2);
        writeVarint(bytes.length);
        mOut.write(bytes, 0, bytes.length);
    }

    void writeInt32(int fieldNumber, int value) {
        if (value == 0) {
            return;
        }
        writeTag(fieldNumber, 0);
        writeVarint(value);
    }

    void writeBool(int fieldNumber, boolean value) {
        if (!value) {
            return;
        }
        writeTag(fieldNumber, 0);
        writeVarint(1);
    }

    void writeDouble(int fieldNumber, double value) {
        if (value == 0) {
            return;
        }
        writeTag(fieldNumber, 1);
        long bits = Double.doubleToLongBits(value);
        for (int i = 0; i < 8; i++) {
            mOut.write((int) (bits >> (i * 8)) & 0xFF);
        }
    }

    void writeBytes(int fieldNumber, byte[] value) {
        if (value == null || value.length == 0) {
            return;
        }
        writeTag(fieldNumber, 2);
        writeVarint(value.length);
        mOut.write(value, 0, value.length);
    }

    void writeEmbedded(int fieldNumber, byte[] embedded) {
        if (embedded == null || embedded.length == 0) {
            return;
        }
        writeTag(fieldNumber, 2);
        writeVarint(embedded.length);
        mOut.write(embedded, 0, embedded.length);
    }

    private void writeTag(int fieldNumber, int wireType) {
        writeVarint((fieldNumber << 3) | wireType);
    }

    private void writeVarint(int value) {
        while ((value & ~0x7F) != 0) {
            mOut.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        mOut.write(value);
    }
}
