/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Locale;

import ru.yourok.openvpn.R;

/**
 * Created by arne on 23.01.16.
 */
class LogFileHandler extends Handler {
    public static final int LOG_MESSAGE = 103;
    public static final int MAGIC_BYTE = 0x55;
    public static final String LOGFILE_NAME = "logcache.dat";
    static final int TRIM_LOG_FILE = 100;
    static final int FLUSH_TO_DISK = 101;
    static final int LOG_INIT = 102;
    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    protected OutputStream mLogFile;
    public LogFileHandler(Looper looper) {
        super(looper);
    }
    public static String bytesToHex(byte[] bytes, int len) {
        len = Math.min(bytes.length, len);
        char[] hexChars = new char[len * 2];
        for (int j = 0; j < len; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    @Override
    public void handleMessage(Message msg) {
        try {
            if (msg.what == LOG_INIT) {
                if (mLogFile != null)
                    throw new RuntimeException("mLogFile not null");
                readLogCache((File) msg.obj);
                openLogFile((File) msg.obj);
            } else if (msg.what == LOG_MESSAGE && msg.obj instanceof LogItem) {
                // Ignore log messages if not yet initialized
                if (mLogFile == null)
                    return;
                writeLogItemToDisk((LogItem) msg.obj);
            } else if (msg.what == TRIM_LOG_FILE) {
                trimLogFile();
                for (LogItem li : VpnStatus.getlogbuffer())
                    writeLogItemToDisk(li);
            } else if (msg.what == FLUSH_TO_DISK) {
                flushToDisk();
            }
        } catch (IOException | BufferOverflowException e) {
            e.printStackTrace();
            VpnStatus.logError("Error during log cache: " + msg.what);
            VpnStatus.logException(e);
        }
    }
    private void flushToDisk() throws IOException {
        mLogFile.flush();
    }
    private void trimLogFile() {
        try {
            mLogFile.flush();
            ((FileOutputStream) mLogFile).getChannel().truncate(0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void writeLogItemToDisk(LogItem li) throws IOException {
        // We do not really care if the log cache breaks between Android upgrades,
        // write binary format to disc
        byte[] liBytes = li.getMarschaledBytes();
        writeEscapedBytes(liBytes);
    }
    public void writeEscapedBytes(byte[] bytes) throws IOException {
        int magic = 0;
        for (byte b : bytes)
            if (b == MAGIC_BYTE || b == MAGIC_BYTE + 1)
                magic++;
        byte eBytes[] = new byte[bytes.length + magic];
        int i = 0;
        for (byte b : bytes) {
            if (b == MAGIC_BYTE || b == MAGIC_BYTE + 1) {
                eBytes[i++] = MAGIC_BYTE + 1;
                eBytes[i++] = (byte) (b - MAGIC_BYTE);
            } else {
                eBytes[i++] = b;
            }
        }
        byte[] lenBytes = ByteBuffer.allocate(4).putInt(bytes.length).array();
        synchronized (mLogFile) {
            mLogFile.write(MAGIC_BYTE);
            mLogFile.write(lenBytes);
            mLogFile.write(eBytes);
        }
    }
    private void openLogFile(File cacheDir) throws FileNotFoundException {
        File logfile = new File(cacheDir, LOGFILE_NAME);
        mLogFile = new FileOutputStream(logfile);
    }
    private void readLogCache(File cacheDir) {
        try {
            File logfile = new File(cacheDir, LOGFILE_NAME);
            if (!logfile.exists() || !logfile.canRead())
                return;
            readCacheContents(new FileInputStream(logfile));
        } catch (IOException | RuntimeException e) {
            VpnStatus.logError("Reading cached logfile failed");
            VpnStatus.logException(e);
            e.printStackTrace();
            // ignore reading file error
        } finally {
            synchronized (VpnStatus.readFileLock) {
                VpnStatus.readFileLog = true;
                VpnStatus.readFileLock.notifyAll();
            }
        }
    }
    protected void readCacheContents(InputStream in) throws IOException {
        BufferedInputStream logFile = new BufferedInputStream(in);
        byte[] buf = new byte[16384];
        int read = logFile.read(buf, 0, 5);
        int itemsRead = 0;
        readloop:
        while (read >= 5) {
            int skipped = 0;
            while (buf[skipped] != MAGIC_BYTE) {
                skipped++;
                if (!(logFile.read(buf, skipped + 4, 1) == 1) || skipped + 10 > buf.length) {
                    VpnStatus.logDebug(String.format(Locale.US, "Skipped %d bytes and no a magic byte found", skipped));
                    break readloop;
                }
            }
            if (skipped > 0)
                VpnStatus.logDebug(String.format(Locale.US, "Skipped %d bytes before finding a magic byte", skipped));
            int len = ByteBuffer.wrap(buf, skipped + 1, 4).asIntBuffer().get();
            // Marshalled LogItem
            int pos = 0;
            byte buf2[] = new byte[buf.length];
            while (pos < len) {
                byte b = (byte) logFile.read();
                if (b == MAGIC_BYTE) {
                    VpnStatus.logDebug(String.format(Locale.US, "Unexpected magic byte found at pos %d, abort current log item", pos));
                    read = logFile.read(buf, 1, 4) + 1;
                    continue readloop;
                } else if (b == MAGIC_BYTE + 1) {
                    b = (byte) logFile.read();
                    if (b == 0)
                        b = MAGIC_BYTE;
                    else if (b == 1)
                        b = MAGIC_BYTE + 1;
                    else {
                        VpnStatus.logDebug(String.format(Locale.US, "Escaped byte not 0 or 1: %d", b));
                        read = logFile.read(buf, 1, 4) + 1;
                        continue readloop;
                    }
                }
                buf2[pos++] = b;
            }
            restoreLogItem(buf2, len);
            //Next item
            read = logFile.read(buf, 0, 5);
            itemsRead++;
            if (itemsRead > 2 * VpnStatus.MAXLOGENTRIES) {
                VpnStatus.logError("Too many logentries read from cache, aborting.");
                read = 0;
            }
        }
        VpnStatus.logDebug(R.string.reread_log, itemsRead);
    }
    protected void restoreLogItem(byte[] buf, int len) throws UnsupportedEncodingException {
        LogItem li = new LogItem(buf, len);
        if (li.verify()) {
            VpnStatus.newLogItem(li, true);
        } else {
            VpnStatus.logError(String.format(Locale.getDefault(),
                    "Could not read log item from file: %d: %s",
                    len, bytesToHex(buf, Math.max(len, 80))));
        }
    }
}
