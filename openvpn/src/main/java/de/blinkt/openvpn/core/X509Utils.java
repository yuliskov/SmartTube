/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import ru.yourok.openvpn.R;

import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Hashtable;
import java.util.Vector;

import javax.security.auth.x500.X500Principal;

import de.blinkt.openvpn.VpnProfile;

public class X509Utils {
    public static Certificate[] getCertificatesFromFile(String certfilename) throws FileNotFoundException, CertificateException {
        CertificateFactory certFact = CertificateFactory.getInstance("X.509");
        Vector<Certificate> certificates = new Vector<>();
        if (VpnProfile.isEmbedded(certfilename)) {
            int subIndex = certfilename.indexOf("-----BEGIN CERTIFICATE-----");
            do {
                // The java certifcate reader is ... kind of stupid
                // It does NOT ignore chars before the --BEGIN ...
                subIndex = Math.max(0, subIndex);
                InputStream inStream = new ByteArrayInputStream(certfilename.substring(subIndex).getBytes());
                certificates.add(certFact.generateCertificate(inStream));
                subIndex = certfilename.indexOf("-----BEGIN CERTIFICATE-----", subIndex + 1);
            } while (subIndex > 0);
            return certificates.toArray(new Certificate[certificates.size()]);
        } else {
            InputStream inStream = new FileInputStream(certfilename);
            return new Certificate[]{certFact.generateCertificate(inStream)};
        }
    }
    public static PemObject readPemObjectFromFile(String keyfilename) throws IOException {
        Reader inStream;
        if (VpnProfile.isEmbedded(keyfilename))
            inStream = new StringReader(VpnProfile.getEmbeddedContent(keyfilename));
        else
            inStream = new FileReader(new File(keyfilename));
        PemReader pr = new PemReader(inStream);
        PemObject r = pr.readPemObject();
        pr.close();
        return r;
    }
    public static String getCertificateFriendlyName(Context c, String filename) {
        if (!TextUtils.isEmpty(filename)) {
            try {
                X509Certificate cert = (X509Certificate) getCertificatesFromFile(filename)[0];
                String friendlycn = getCertificateFriendlyName(cert);
                friendlycn = getCertificateValidityString(cert, c.getResources()) + friendlycn;
                return friendlycn;
            } catch (Exception e) {
                VpnStatus.logError("Could not read certificate" + e.getLocalizedMessage());
            }
        }
        return c.getString(R.string.cannotparsecert);
    }
    public static String getCertificateValidityString(X509Certificate cert, Resources res) {
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException ce) {
            return "EXPIRED: ";
        } catch (CertificateNotYetValidException cny) {
            return "NOT YET VALID: ";
        }
        Date certNotAfter = cert.getNotAfter();
        Date now = new Date();
        long timeLeft = certNotAfter.getTime() - now.getTime(); // Time left in ms
        // More than 72h left, display days
        // More than 3 months display months
        if (timeLeft > 90l * 24 * 3600 * 1000) {
            long months = getMonthsDifference(now, certNotAfter);
            return res.getQuantityString(R.plurals.months_left, (int) months, months);
        } else if (timeLeft > 72 * 3600 * 1000) {
            long days = timeLeft / (24 * 3600 * 1000);
            return res.getQuantityString(R.plurals.days_left, (int) days, days);
        } else {
            long hours = timeLeft / (3600 * 1000);
            return res.getQuantityString(R.plurals.hours_left, (int) hours, hours);
        }
    }
    public static int getMonthsDifference(Date date1, Date date2) {
        int m1 = date1.getYear() * 12 + date1.getMonth();
        int m2 = date2.getYear() * 12 + date2.getMonth();
        return m2 - m1 + 1;
    }
    public static String getCertificateFriendlyName(X509Certificate cert) {
        X500Principal principal = cert.getSubjectX500Principal();
        byte[] encodedSubject = principal.getEncoded();
        String friendlyName = null;
        /* Hack so we do not have to ship a whole Spongy/bouncycastle */
        Exception exp = null;
        try {
            @SuppressLint("PrivateApi") Class X509NameClass = Class.forName("com.android.org.bouncycastle.asn1.x509.X509Name");
            Method getInstance = X509NameClass.getMethod("getInstance", Object.class);
            Hashtable defaultSymbols = (Hashtable) X509NameClass.getField("DefaultSymbols").get(X509NameClass);
            if (!defaultSymbols.containsKey("1.2.840.113549.1.9.1"))
                defaultSymbols.put("1.2.840.113549.1.9.1", "eMail");
            Object subjectName = getInstance.invoke(X509NameClass, encodedSubject);
            Method toString = X509NameClass.getMethod("toString", boolean.class, Hashtable.class);
            friendlyName = (String) toString.invoke(subjectName, true, defaultSymbols);
        } catch (ClassNotFoundException e) {
            exp = e;
        } catch (NoSuchMethodException e) {
            exp = e;
        } catch (InvocationTargetException e) {
            exp = e;
        } catch (IllegalAccessException e) {
            exp = e;
        } catch (NoSuchFieldException e) {
            exp = e;
        }
        if (exp != null)
            VpnStatus.logException("Getting X509 Name from certificate", exp);
        /* Fallback if the reflection method did not work */
        if (friendlyName == null)
            friendlyName = principal.getName();
        // Really evil hack to decode email address
        // See: http://code.google.com/p/android/issues/detail?id=21531
        String[] parts = friendlyName.split(",");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("1.2.840.113549.1.9.1=#16")) {
                parts[i] = "email=" + ia5decode(part.replace("1.2.840.113549.1.9.1=#16", ""));
            }
        }
        friendlyName = TextUtils.join(",", parts);
        return friendlyName;
    }
    public static boolean isPrintableChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return (!Character.isISOControl(c)) &&
                block != null &&
                block != Character.UnicodeBlock.SPECIALS;
    }
    private static String ia5decode(String ia5string) {
        String d = "";
        for (int i = 1; i < ia5string.length(); i = i + 2) {
            String hexstr = ia5string.substring(i - 1, i + 1);
            char c = (char) Integer.parseInt(hexstr, 16);
            if (isPrintableChar(c)) {
                d += c;
            } else if (i == 1 && (c == 0x12 || c == 0x1b)) {
                // ignore
            } else {
                d += "\\x" + hexstr;
            }
        }
        return d;
    }
}
