/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Parcel;
import android.os.Parcelable;


import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.FormatFlagsConversionMismatchException;
import java.util.Locale;
import java.util.UnknownFormatConversionException;

import ru.yourok.openvpn.R;

/**
 * Created by arne on 24.04.16.
 */
public class LogItem implements Parcelable {
    public static final Creator<LogItem> CREATOR = new Creator<LogItem>() {
        public LogItem createFromParcel(Parcel in) {
            return new LogItem(in);
        }

        public LogItem[] newArray(int size) {
            return new LogItem[size];
        }
    };
    // Default log priority
    VpnStatus.LogLevel mLevel = VpnStatus.LogLevel.INFO;
    private Object[] mArgs = null;
    private String mMessage = null;
    private int mRessourceId;
    private long logtime = System.currentTimeMillis();
    private int mVerbosityLevel = -1;

    private LogItem(int ressourceId, Object[] args) {
        mRessourceId = ressourceId;
        mArgs = args;
    }

    public LogItem(VpnStatus.LogLevel level, int verblevel, String message) {
        mMessage = message;
        mLevel = level;
        mVerbosityLevel = verblevel;
    }

    public LogItem(byte[] in, int length) throws UnsupportedEncodingException {
        ByteBuffer bb = ByteBuffer.wrap(in, 0, length);
        bb.get(); // ignore version
        logtime = bb.getLong();
        mVerbosityLevel = bb.getInt();
        mLevel = VpnStatus.LogLevel.getEnumByValue(bb.getInt());
        mRessourceId = bb.getInt();
        int len = bb.getInt();
        if (len == 0) {
            mMessage = null;
        } else {
            if (len > bb.remaining()) throw new IndexOutOfBoundsException("String length " + len + " is bigger than remaining bytes " + bb.remaining());
            byte[] utf8bytes = new byte[len];
            bb.get(utf8bytes);
            mMessage = new String(utf8bytes, "UTF-8");
        }
        int numArgs = bb.getInt();
        if (numArgs > 30) {
            throw new IndexOutOfBoundsException("Too many arguments for Logitem to unmarschal");
        }
        if (numArgs == 0) {
            mArgs = null;
        } else {
            mArgs = new Object[numArgs];
            for (int i = 0; i < numArgs; i++) {
                char type = bb.getChar();
                switch (type) {
                    case 's':
                        mArgs[i] = unmarschalString(bb);
                        break;
                    case 'i':
                        mArgs[i] = bb.getInt();
                        break;
                    case 'd':
                        mArgs[i] = bb.getDouble();
                        break;
                    case 'f':
                        mArgs[i] = bb.getFloat();
                        break;
                    case 'l':
                        mArgs[i] = bb.getLong();
                        break;
                    case '0':
                        mArgs[i] = null;
                        break;
                    default:
                        throw new UnsupportedEncodingException("Unknown format type: " + type);
                }
            }
        }
        if (bb.hasRemaining()) throw new UnsupportedEncodingException(bb.remaining() + " bytes left after unmarshaling everything");
    }

    public LogItem(Parcel in) {
        mArgs = in.readArray(Object.class.getClassLoader());
        mMessage = in.readString();
        mRessourceId = in.readInt();
        mLevel = VpnStatus.LogLevel.getEnumByValue(in.readInt());
        mVerbosityLevel = in.readInt();
        logtime = in.readLong();
    }

    public LogItem(VpnStatus.LogLevel loglevel, int ressourceId, Object... args) {
        mRessourceId = ressourceId;
        mArgs = args;
        mLevel = loglevel;
    }

    public LogItem(VpnStatus.LogLevel loglevel, String msg) {
        mLevel = loglevel;
        mMessage = msg;
    }

    public LogItem(VpnStatus.LogLevel loglevel, int ressourceId) {
        mRessourceId = ressourceId;
        mLevel = loglevel;
    }

    // TextUtils.join will cause not macked exeception in tests ....
    public static String join(CharSequence delimiter, Object[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeArray(mArgs);
        dest.writeString(mMessage);
        dest.writeInt(mRessourceId);
        dest.writeInt(mLevel.getInt());
        dest.writeInt(mVerbosityLevel);
        dest.writeLong(logtime);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LogItem)) return obj.equals(this);
        LogItem other = (LogItem) obj;
        return Arrays.equals(mArgs, other.mArgs) && ((other.mMessage == null && mMessage == other.mMessage) || mMessage.equals(other.mMessage)) && mRessourceId == other.mRessourceId && ((mLevel == null && other.mLevel == mLevel) || other.mLevel.equals(mLevel)) && mVerbosityLevel == other.mVerbosityLevel && logtime == other.logtime;
    }

    public byte[] getMarschaledBytes() throws UnsupportedEncodingException, BufferOverflowException {
        ByteBuffer bb = ByteBuffer.allocate(16384);
        bb.put((byte) 0x0);               //version
        bb.putLong(logtime);              //8
        bb.putInt(mVerbosityLevel);      //4
        bb.putInt(mLevel.getInt());
        bb.putInt(mRessourceId);
        if (mMessage == null || mMessage.length() == 0) {
            bb.putInt(0);
        } else {
            marschalString(mMessage, bb);
        }
        if (mArgs == null || mArgs.length == 0) {
            bb.putInt(0);
        } else {
            bb.putInt(mArgs.length);
            for (Object o : mArgs) {
                if (o instanceof String) {
                    bb.putChar('s');
                    marschalString((String) o, bb);
                } else if (o instanceof Integer) {
                    bb.putChar('i');
                    bb.putInt((Integer) o);
                } else if (o instanceof Float) {
                    bb.putChar('f');
                    bb.putFloat((Float) o);
                } else if (o instanceof Double) {
                    bb.putChar('d');
                    bb.putDouble((Double) o);
                } else if (o instanceof Long) {
                    bb.putChar('l');
                    bb.putLong((Long) o);
                } else if (o == null) {
                    bb.putChar('0');
                } else {
                    VpnStatus.logDebug("Unknown object for LogItem marschaling " + o);
                    bb.putChar('s');
                    marschalString(o.toString(), bb);
                }
            }
        }
        int pos = bb.position();
        bb.rewind();
        return Arrays.copyOf(bb.array(), pos);
    }

    private void marschalString(String str, ByteBuffer bb) throws UnsupportedEncodingException {
        byte[] utf8bytes = str.getBytes("UTF-8");
        bb.putInt(utf8bytes.length);
        bb.put(utf8bytes);
    }

    private String unmarschalString(ByteBuffer bb) throws UnsupportedEncodingException {
        int len = bb.getInt();
        byte[] utf8bytes = new byte[len];
        bb.get(utf8bytes);
        return new String(utf8bytes, "UTF-8");
    }

    public String getString(Context c) {
        try {
            if (mMessage != null) {
                return mMessage;
            } else {
                if (c != null) {
                    if (mRessourceId == R.string.mobile_info) return getMobileInfoString(c);
                    if (mArgs == null) return c.getString(mRessourceId);
                    else return c.getString(mRessourceId, mArgs);
                } else {
                    String str = String.format(Locale.ENGLISH, "Log (no context) resid %d", mRessourceId);
                    if (mArgs != null) str += join("|", mArgs);
                    return str;
                }
            }
        } catch (UnknownFormatConversionException e) {
            if (c != null) throw new UnknownFormatConversionException(e.getLocalizedMessage() + getString(null));
            else throw e;
        } catch (FormatFlagsConversionMismatchException e) {
            if (c != null) throw new FormatFlagsConversionMismatchException(e.getLocalizedMessage() + getString(null), e.getConversion());
            else throw e;
        }
    }

    public VpnStatus.LogLevel getLogLevel() {
        return mLevel;
    }

    @Override
    public String toString() {
        return getString(null);
    }

    // The lint is wrong here
    @SuppressLint("StringFormatMatches")
    private String getMobileInfoString(Context c) {
        c.getPackageManager();
        String apksign = "error getting package signature";
        String version = "error getting version";
        try {
            @SuppressLint("PackageManagerGetSignatures") Signature raw = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0];
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(raw.toByteArray()));
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] der = cert.getEncoded();
            md.update(der);
            byte[] digest = md.digest();
            if (Arrays.equals(digest, VpnStatus.officalkey)) apksign = c.getString(R.string.official_build);
            else if (Arrays.equals(digest, VpnStatus.officaldebugkey)) apksign = c.getString(R.string.debug_build);
            else if (Arrays.equals(digest, VpnStatus.amazonkey)) apksign = "amazon version";
            else if (Arrays.equals(digest, VpnStatus.fdroidkey)) apksign = "F-Droid built and signed version";
            else apksign = c.getString(R.string.built_by, cert.getSubjectX500Principal().getName());
            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = packageinfo.versionName;
        } catch (PackageManager.NameNotFoundException | CertificateException | NoSuchAlgorithmException ignored) {
        }
        Object[] argsext = Arrays.copyOf(mArgs, mArgs.length);
        argsext[argsext.length - 1] = apksign;
        argsext[argsext.length - 2] = version;
        return c.getString(R.string.mobile_info, argsext);
    }

    public long getLogtime() {
        return logtime;
    }

    public int getVerbosityLevel() {
        if (mVerbosityLevel == -1) {
            // Hack:
            // For message not from OpenVPN, report the status level as log level
            return mLevel.getInt();
        }
        return mVerbosityLevel;
    }

    public boolean verify() {
        if (mLevel == null) return false;
        return !(mMessage == null && mRessourceId == 0);
    }
}
