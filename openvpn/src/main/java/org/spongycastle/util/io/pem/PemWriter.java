/*
 * Copyright (c) 2012-2014 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package org.spongycastle.util.io.pem;

import org.spongycastle.util.encoders.Base64;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;

/**
 * A generic PEM writer, based on RFC 1421
 */
@SuppressWarnings("all")
public class PemWriter extends BufferedWriter {
    private static final int LINE_LENGTH = 64;
    private final int nlLength;
    private char[] buf = new char[LINE_LENGTH];

    /**
     * Base constructor.
     *
     * @param out output stream to use.
     */
    public PemWriter(Writer out) {
        super(out);
        String nl = System.getProperty("line.separator");
        if (nl != null) {
            nlLength = nl.length();
        } else {
            nlLength = 2;
        }
    }

    /**
     * Return the number of bytes or characters required to contain the
     * passed in object if it is PEM encoded.
     *
     * @param obj pem object to be output
     * @return an estimate of the number of bytes
     */
    public int getOutputSize(PemObject obj) {
        // BEGIN and END boundaries.
        int size = (2 * (obj.getType().length() + 10 + nlLength)) + 6 + 4;
        if (!obj.getHeaders().isEmpty()) {
            for (Iterator it = obj.getHeaders().iterator(); it.hasNext(); ) {
                PemHeader hdr = (PemHeader) it.next();
                size += hdr.getName().length() + ": ".length() + hdr.getValue().length() + nlLength;
            }
            size += nlLength;
        }
        // base64 encoding
        int dataLen = ((obj.getContent().length + 2) / 3) * 4;
        size += dataLen + (((dataLen + LINE_LENGTH - 1) / LINE_LENGTH) * nlLength);
        return size;
    }

    public void writeObject(PemObjectGenerator objGen) throws IOException {
        PemObject obj = objGen.generate();
        writePreEncapsulationBoundary(obj.getType());
        if (!obj.getHeaders().isEmpty()) {
            for (Iterator it = obj.getHeaders().iterator(); it.hasNext(); ) {
                PemHeader hdr = (PemHeader) it.next();
                this.write(hdr.getName());
                this.write(": ");
                this.write(hdr.getValue());
                this.newLine();
            }
            this.newLine();
        }
        writeEncoded(obj.getContent());
        writePostEncapsulationBoundary(obj.getType());
    }

    private void writeEncoded(byte[] bytes) throws IOException {
        bytes = Base64.encode(bytes);
        for (int i = 0; i < bytes.length; i += buf.length) {
            int index = 0;
            while (index != buf.length) {
                if ((i + index) >= bytes.length) {
                    break;
                }
                buf[index] = (char) bytes[i + index];
                index++;
            }
            this.write(buf, 0, index);
            this.newLine();
        }
    }

    private void writePreEncapsulationBoundary(String type) throws IOException {
        this.write("-----BEGIN " + type + "-----");
        this.newLine();
    }

    private void writePostEncapsulationBoundary(String type) throws IOException {
        this.write("-----END " + type + "-----");
        this.newLine();
    }
}
