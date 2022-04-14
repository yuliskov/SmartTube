/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.liskovsoft.openvpn.R;

import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import de.blinkt.openvpn.core.Connection;
import de.blinkt.openvpn.core.NativeUtils;
import de.blinkt.openvpn.core.OpenVPNService;
import de.blinkt.openvpn.core.PasswordCache;
import de.blinkt.openvpn.core.VPNLaunchHelper;
import de.blinkt.openvpn.core.VpnStatus;
import de.blinkt.openvpn.core.X509Utils;

public class VpnProfile implements Serializable, Cloneable {
    // Note that this class cannot be moved to core where it belongs since
    // the profile loading depends on it being here
    // The Serializable documentation mentions that class name change are possible
    // but the how is unclear
    //
    transient public static final long MAX_EMBED_FILE_SIZE = 2048 * 1024; // 2048kB
    // Don't change this, not all parts of the program use this constant
    public static final String EXTRA_PROFILEUUID = "de.blinkt.openvpn.profileUUID";
    public static final String INLINE_TAG = "[[INLINE]]";
    public static final String DISPLAYNAME_TAG = "[[NAME]]";
    public static final int MAXLOGLEVEL = 4;
    public static final int CURRENT_PROFILE_VERSION = 6;
    public static final int DEFAULT_MSSFIX_SIZE = 1280;
    public static final int TYPE_CERTIFICATES = 0;
    public static final int TYPE_PKCS12 = 1;
    public static final int TYPE_KEYSTORE = 2;
    public static final int TYPE_USERPASS = 3;
    public static final int TYPE_STATICKEYS = 4;
    public static final int TYPE_USERPASS_CERTIFICATES = 5;
    public static final int TYPE_USERPASS_PKCS12 = 6;
    public static final int TYPE_USERPASS_KEYSTORE = 7;
    public static final int X509_VERIFY_TLSREMOTE = 0;
    public static final int X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING = 1;
    public static final int X509_VERIFY_TLSREMOTE_DN = 2;
    public static final int X509_VERIFY_TLSREMOTE_RDN = 3;
    public static final int X509_VERIFY_TLSREMOTE_RDN_PREFIX = 4;
    public static final int AUTH_RETRY_NONE_FORGET = 0;
    public static final int AUTH_RETRY_NOINTERACT = 2;
    public static final boolean mIsOpenVPN22 = false;
    private static final long serialVersionUID = 7085688938959334563L;
    private static final int AUTH_RETRY_NONE_KEEP = 1;
    private static final int AUTH_RETRY_INTERACT = 3;
    public static String DEFAULT_DNS1 = "8.8.8.8";
    public static String DEFAULT_DNS2 = "1.1.1.1";
    // variable named wrong and should haven beeen transient
    // but needs to keep wrong name to guarante loading of old
    // profiles
    public transient boolean profileDeleted = false;
    public int mAuthenticationType = TYPE_KEYSTORE;
    public String mName;
    public String mAlias;
    public String mClientCertFilename;
    public String mTLSAuthDirection = "";
    public String mTLSAuthFilename;
    public String mClientKeyFilename;
    public String mCaFilename;
    public boolean mUseLzo = true;
    public String mPKCS12Filename;
    public String mPKCS12Password;
    public boolean mUseTLSAuth = false;
    public String mDNS1 = DEFAULT_DNS1;
    public String mDNS2 = DEFAULT_DNS2;
    public String mIPv4Address;
    public String mIPv6Address;
    public boolean mOverrideDNS = false;
    public String mSearchDomain = "blinkt.de";
    public boolean mUseDefaultRoute = true;
    public boolean mUsePull = true;
    public String mCustomRoutes;
    public boolean mCheckRemoteCN = true;
    public boolean mExpectTLSCert = false;
    public String mRemoteCN = "";
    public String mPassword = "";
    public String mUsername = "";
    public boolean mRoutenopull = false;
    public boolean mUseRandomHostname = false;
    public boolean mUseFloat = false;
    public boolean mUseCustomConfig = false;
    public String mCustomConfigOptions = "";
    public String mVerb = "1";  //ignored
    public String mCipher = "";
    public boolean mNobind = false;
    public boolean mUseDefaultRoutev6 = true;
    public String mCustomRoutesv6 = "";
    public String mKeyPassword = "";
    public boolean mPersistTun = false;
    public String mConnectRetryMax = "-1";
    public String mConnectRetry = "2";
    public String mConnectRetryMaxTime = "300";
    public boolean mUserEditable = true;
    public String mAuth = "";
    public int mX509AuthType = X509_VERIFY_TLSREMOTE_RDN;
    public String mx509UsernameField = null;
    public boolean mAllowLocalLAN;
    public String mExcludedRoutes;
    public String mExcludedRoutesv6;
    public int mMssFix = 0; // -1 is default,
    public Connection[] mConnections = new Connection[0];
    public boolean mRemoteRandom = false;
    public HashSet<String> mAllowedAppsVpn = new HashSet<>();
    public boolean mAllowedAppsVpnAreDisallowed = true;
    public String mCrlFilename;
    public String mProfileCreator;
    public int mAuthRetry = AUTH_RETRY_NONE_FORGET;
    public int mTunMtu;
    public boolean mPushPeerInfo = false;
    public int mVersion = 0;
    // timestamp when the profile was last used
    public long mLastUsed;
    /* Options no longer used in new profiles */
    public String mServerName = "openvpn.example.com";
    public String mServerPort = "11940";
    public boolean mUseUdp = true;
    private transient PrivateKey mPrivateKey;
    // Public attributes, since I got mad with getter/setter
    // set members to default values
    private UUID mUuid;
    private int mProfileVersion;


    public VpnProfile(String name) {
        mUuid = UUID.randomUUID();
        mName = name;
        mProfileVersion = CURRENT_PROFILE_VERSION;
        mConnections = new Connection[1];
        mConnections[0] = new Connection();
        mLastUsed = System.currentTimeMillis();
    }

    public static String openVpnEscape(String unescaped) {
        if (unescaped == null) return null;
        String escapedString = unescaped.replace("\\", "\\\\");
        escapedString = escapedString.replace("\"", "\\\"");
        escapedString = escapedString.replace("\n", "\\n");
        if (escapedString.equals(unescaped) && !escapedString.contains(" ") && !escapedString.contains("#") && !escapedString.contains(";") && !escapedString.equals("")) return unescaped;
        else return '"' + escapedString + '"';
    }

    //! Put inline data inline and other data as normal escaped filename
    public static String insertFileData(String cfgentry, String filedata) {
        if (filedata == null) {
            return String.format("%s %s\n", cfgentry, "file missing in config profile");
        } else if (isEmbedded(filedata)) {
            String dataWithOutHeader = getEmbeddedContent(filedata);
            return String.format(Locale.ENGLISH, "<%s>\n%s\n</%s>\n", cfgentry, dataWithOutHeader, cfgentry);
        } else {
            return String.format(Locale.ENGLISH, "%s %s\n", cfgentry, openVpnEscape(filedata));
        }
    }

    public static String getDisplayName(String embeddedFile) {
        int start = DISPLAYNAME_TAG.length();
        int end = embeddedFile.indexOf(INLINE_TAG);
        return embeddedFile.substring(start, end);
    }

    public static String getEmbeddedContent(String data) {
        if (!data.contains(INLINE_TAG)) return data;
        int start = data.indexOf(INLINE_TAG) + INLINE_TAG.length();
        return data.substring(start);
    }

    public static boolean isEmbedded(String data) {
        if (data == null) return false;
        return data.startsWith(INLINE_TAG) || data.startsWith(DISPLAYNAME_TAG);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VpnProfile) {
            VpnProfile vpnProfile = (VpnProfile) obj;
            return mUuid.equals(vpnProfile.mUuid);
        } else {
            return false;
        }
    }

    public void clearDefaults() {
        mServerName = "unknown";
        mUsePull = false;
        mUseLzo = false;
        mUseDefaultRoute = false;
        mUseDefaultRoutev6 = false;
        mExpectTLSCert = false;
        mCheckRemoteCN = false;
        mPersistTun = false;
        mAllowLocalLAN = true;
        mPushPeerInfo = false;
        mMssFix = 0;
    }

    public UUID getUUID() {
        return mUuid;
    }

    public String getName() {
        if (TextUtils.isEmpty(mName)) return "No profile name";
        return mName;
    }

    public void upgradeProfile() {
        if (mProfileVersion < 2) {
            /* default to the behaviour the OS used */
            mAllowLocalLAN = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT;
        }
        if (mProfileVersion < 4) {
            moveOptionsToConnection();
            mAllowedAppsVpnAreDisallowed = true;
        }
        if (mAllowedAppsVpn == null) mAllowedAppsVpn = new HashSet<>();
        if (mConnections == null) mConnections = new Connection[0];
        if (mProfileVersion < 6) {
            if (TextUtils.isEmpty(mProfileCreator)) mUserEditable = true;
        }
        mProfileVersion = CURRENT_PROFILE_VERSION;
    }

    private void moveOptionsToConnection() {
        mConnections = new Connection[1];
        Connection conn = new Connection();
        conn.mServerName = mServerName;
        conn.mServerPort = mServerPort;
        conn.mUseUdp = mUseUdp;
        conn.mCustomConfiguration = "";
        mConnections[0] = conn;
    }

    public String getConfigFile(Context context, boolean configForOvpn3) {
        File cacheDir = context.getCacheDir();
        String cfg = "";
        // Enable management interface
        cfg += "# Enables connection to GUI\n";
        cfg += "management ";
        cfg += cacheDir.getAbsolutePath() + "/" + "mgmtsocket";
        cfg += " unix\n";
        cfg += "management-client\n";
        // Not needed, see updated man page in 2.3
        //cfg += "management-signal\n";
        cfg += "management-query-passwords\n";
        cfg += "management-hold\n\n";
        if (!configForOvpn3) {
            cfg += String.format("setenv IV_GUI_VER %s \n", openVpnEscape(getVersionEnvString(context)));
            String versionString = String.format(Locale.US, "%d %s %s %s %s %s", Build.VERSION.SDK_INT, Build.VERSION.RELEASE, NativeUtils.getNativeAPI(), Build.BRAND, Build.BOARD, Build.MODEL);
            cfg += String.format("setenv IV_PLAT_VER %s\n", openVpnEscape(versionString));
        }
        cfg += "machine-readable-output\n";
        cfg += "allow-recursive-routing\n";
        // Users are confused by warnings that are misleading...
        cfg += "ifconfig-nowarn\n";
        boolean useTLSClient = (mAuthenticationType != TYPE_STATICKEYS);
        if (useTLSClient && mUsePull) cfg += "client\n";
        else if (mUsePull) cfg += "pull\n";
        else if (useTLSClient) cfg += "tls-client\n";
        //cfg += "verb " + mVerb + "\n";
        cfg += "verb " + MAXLOGLEVEL + "\n";
        if (mConnectRetryMax == null) {
            mConnectRetryMax = "-1";
        }
        if (!mConnectRetryMax.equals("-1")) cfg += "connect-retry-max " + mConnectRetryMax + "\n";
        if (TextUtils.isEmpty(mConnectRetry)) mConnectRetry = "2";
        if (TextUtils.isEmpty(mConnectRetryMaxTime)) mConnectRetryMaxTime = "300";
        if (!mIsOpenVPN22) cfg += "connect-retry " + mConnectRetry + " " + mConnectRetryMaxTime + "\n";
        else if (mIsOpenVPN22 && mUseUdp) cfg += "connect-retry " + mConnectRetry + "\n";
        cfg += "resolv-retry 60\n";
        // We cannot use anything else than tun
        cfg += "dev tun\n";
        boolean canUsePlainRemotes = true;
        if (mConnections.length == 1) {
            cfg += mConnections[0].getConnectionBlock();
        } else {
            for (Connection conn : mConnections) {
                canUsePlainRemotes = canUsePlainRemotes && conn.isOnlyRemote();
            }
            if (mRemoteRandom) cfg += "remote-random\n";
            if (canUsePlainRemotes) {
                for (Connection conn : mConnections) {
                    if (conn.mEnabled) {
                        cfg += conn.getConnectionBlock();
                    }
                }
            }
        }
        switch (mAuthenticationType) {
            case VpnProfile.TYPE_USERPASS_CERTIFICATES:
                cfg += "auth-user-pass\n";
            case VpnProfile.TYPE_CERTIFICATES:
                // Ca
                cfg += insertFileData("ca", mCaFilename);
                // Client Cert + Key
                cfg += insertFileData("key", mClientKeyFilename);
                cfg += insertFileData("cert", mClientCertFilename);
                break;
            case VpnProfile.TYPE_USERPASS_PKCS12:
                cfg += "auth-user-pass\n";
            case VpnProfile.TYPE_PKCS12:
                cfg += insertFileData("pkcs12", mPKCS12Filename);
                break;
            case VpnProfile.TYPE_USERPASS_KEYSTORE:
                cfg += "auth-user-pass\n";
            case VpnProfile.TYPE_KEYSTORE:
                if (!configForOvpn3) {
                    String[] ks = getKeyStoreCertificates(context);
                    cfg += "### From Keystore ####\n";
                    if (ks != null) {
                        cfg += "<ca>\n" + ks[0] + "\n</ca>\n";
                        if (ks[1] != null) cfg += "<extra-certs>\n" + ks[1] + "\n</extra-certs>\n";
                        cfg += "<cert>\n" + ks[2] + "\n</cert>\n";
                        cfg += "management-external-key\n";
                    } else {
                        cfg += context.getString(R.string.keychain_access) + "\n";
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) if (!mAlias.matches("^[a-zA-Z0-9]$")) cfg += context.getString(R.string.jelly_keystore_alphanumeric_bug) + "\n";
                    }
                }
                break;
            case VpnProfile.TYPE_USERPASS:
                cfg += "auth-user-pass\n";
                cfg += insertFileData("ca", mCaFilename);
        }
        if (isUserPWAuth()) {
            if (mAuthenticationType == AUTH_RETRY_NOINTERACT) cfg += "auth-retry nointeract";
        }
        if (!TextUtils.isEmpty(mCrlFilename)) cfg += insertFileData("crl-verify", mCrlFilename);
        if (mUseLzo) {
            cfg += "comp-lzo\n";
        }
        if (mUseTLSAuth) {
            boolean useTlsCrypt = mTLSAuthDirection.equals("tls-crypt");
            if (mAuthenticationType == TYPE_STATICKEYS) cfg += insertFileData("secret", mTLSAuthFilename);
            else if (useTlsCrypt) cfg += insertFileData("tls-crypt", mTLSAuthFilename);
            else cfg += insertFileData("tls-auth", mTLSAuthFilename);
            if (!TextUtils.isEmpty(mTLSAuthDirection) && !useTlsCrypt) {
                cfg += "key-direction ";
                cfg += mTLSAuthDirection;
                cfg += "\n";
            }
        }
        if (!mUsePull) {
            if (!TextUtils.isEmpty(mIPv4Address)) cfg += "ifconfig " + cidrToIPAndNetmask(mIPv4Address) + "\n";
            if (!TextUtils.isEmpty(mIPv6Address)) cfg += "ifconfig-ipv6 " + mIPv6Address + "\n";
        }
        if (mUsePull && mRoutenopull) cfg += "route-nopull\n";
        String routes = "";
        if (mUseDefaultRoute) routes += "route 0.0.0.0 0.0.0.0 vpn_gateway\n";
        else {
            for (String route : getCustomRoutes(mCustomRoutes)) {
                routes += "route " + route + " vpn_gateway\n";
            }
            for (String route : getCustomRoutes(mExcludedRoutes)) {
                routes += "route " + route + " net_gateway\n";
            }
        }
        if (mUseDefaultRoutev6) cfg += "route-ipv6 ::/0\n";
        else for (String route : getCustomRoutesv6(mCustomRoutesv6)) {
            routes += "route-ipv6 " + route + "\n";
        }
        cfg += routes;
        if (mOverrideDNS || !mUsePull) {
            if (!TextUtils.isEmpty(mDNS1)) {
                if (mDNS1.contains(":")) cfg += "dhcp-option DNS6 " + mDNS1 + "\n";
                else cfg += "dhcp-option DNS " + mDNS1 + "\n";
            }
            if (!TextUtils.isEmpty(mDNS2)) {
                if (mDNS2.contains(":")) cfg += "dhcp-option DNS6 " + mDNS2 + "\n";
                else cfg += "dhcp-option DNS " + mDNS2 + "\n";
            }
            if (!TextUtils.isEmpty(mSearchDomain)) cfg += "dhcp-option DOMAIN " + mSearchDomain + "\n";
        }
        if (mMssFix != 0) {
            if (mMssFix != 1450) {
                cfg += String.format(Locale.US, "mssfix %d\n", mMssFix);
            } else cfg += "mssfix\n";
        }
        if (mTunMtu >= 48 && mTunMtu != 1500) {
            cfg += String.format(Locale.US, "tun-mtu %d\n", mTunMtu);
        }
        if (mNobind) cfg += "nobind\n";
        // Authentication
        if (mAuthenticationType != TYPE_STATICKEYS) {
            if (mCheckRemoteCN) {
                if (mRemoteCN == null || mRemoteCN.equals("")) cfg += "verify-x509-name " + openVpnEscape(mConnections[0].mServerName) + " name\n";
                else switch (mX509AuthType) {
                    // 2.2 style x509 checks
                    case X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING:
                        cfg += "compat-names no-remapping\n";
                    case X509_VERIFY_TLSREMOTE:
                        cfg += "tls-remote " + openVpnEscape(mRemoteCN) + "\n";
                        break;
                    case X509_VERIFY_TLSREMOTE_RDN:
                        cfg += "verify-x509-name " + openVpnEscape(mRemoteCN) + " name\n";
                        break;
                    case X509_VERIFY_TLSREMOTE_RDN_PREFIX:
                        cfg += "verify-x509-name " + openVpnEscape(mRemoteCN) + " name-prefix\n";
                        break;
                    case X509_VERIFY_TLSREMOTE_DN:
                        cfg += "verify-x509-name " + openVpnEscape(mRemoteCN) + "\n";
                        break;
                }
                if (!TextUtils.isEmpty(mx509UsernameField)) cfg += "x509-username-field " + openVpnEscape(mx509UsernameField) + "\n";
            }
            if (mExpectTLSCert) cfg += "remote-cert-tls server\n";
        }
        if (!TextUtils.isEmpty(mCipher)) {
            cfg += "cipher " + mCipher + "\n";
        }
        if (!TextUtils.isEmpty(mAuth)) {
            cfg += "auth " + mAuth + "\n";
        }
        // Obscure Settings dialog
        if (mUseRandomHostname) cfg += "#my favorite options :)\nremote-random-hostname\n";
        if (mUseFloat) cfg += "float\n";
        if (mPersistTun) {
            cfg += "persist-tun\n";
            cfg += "# persist-tun also enables pre resolving to avoid DNS resolve problem\n";
            cfg += "preresolve\n";
        }
        if (mPushPeerInfo) cfg += "push-peer-info\n";
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean usesystemproxy = prefs.getBoolean("usesystemproxy", true);
        if (usesystemproxy && !mIsOpenVPN22) {
            cfg += "# Use system proxy setting\n";
            cfg += "management-query-proxy\n";
        }
        if (mUseCustomConfig) {
            cfg += "# Custom configuration options\n";
            cfg += "# You are on your on own here :)\n";
            cfg += mCustomConfigOptions;
            cfg += "\n";
        }
        if (!canUsePlainRemotes) {
            cfg += "# Connection Options are at the end to allow global options (and global custom options) to influence connection blocks\n";
            for (Connection conn : mConnections) {
                if (conn.mEnabled) {
                    cfg += "<connection>\n";
                    cfg += conn.getConnectionBlock();
                    cfg += "</connection>\n";
                }
            }
        }
        return cfg;
    }

    public String getVersionEnvString(Context c) {
        String version = "unknown";
        try {
            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = packageinfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            VpnStatus.logException(e);
        }
        return String.format(Locale.US, "%s %s", c.getPackageName(), version);
    }

    @NonNull
    private Collection<String> getCustomRoutes(String routes) {
        Vector<String> cidrRoutes = new Vector<>();
        if (routes == null) {
            // No routes set, return empty vector
            return cidrRoutes;
        }
        for (String route : routes.split("[\n \t]")) {
            if (!route.equals("")) {
                String cidrroute = cidrToIPAndNetmask(route);
                if (cidrroute == null) return cidrRoutes;
                cidrRoutes.add(cidrroute);
            }
        }
        return cidrRoutes;
    }

    private Collection<String> getCustomRoutesv6(String routes) {
        Vector<String> cidrRoutes = new Vector<>();
        if (routes == null) {
            // No routes set, return empty vector
            return cidrRoutes;
        }
        for (String route : routes.split("[\n \t]")) {
            if (!route.equals("")) {
                cidrRoutes.add(route);
            }
        }
        return cidrRoutes;
    }

    private String cidrToIPAndNetmask(String route) {
        String[] parts = route.split("/");
        // No /xx, assume /32 as netmask
        if (parts.length == 1) parts = (route + "/32").split("/");
        if (parts.length != 2) return null;
        int len;
        try {
            len = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ne) {
            return null;
        }
        if (len < 0 || len > 32) return null;
        long nm = 0xffffffffL;
        nm = (nm << (32 - len)) & 0xffffffffL;
        String netmask = String.format(Locale.ENGLISH, "%d.%d.%d.%d", (nm & 0xff000000) >> 24, (nm & 0xff0000) >> 16, (nm & 0xff00) >> 8, nm & 0xff);
        return parts[0] + "  " + netmask;
    }

    public Intent prepareStartService(Context context) {
        Intent intent = getStartServiceIntent(context);
        // TODO: Handle this?!
//        if (mAuthenticationType == VpnProfile.TYPE_KEYSTORE || mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE) {
//            if (getKeyStoreCertificates(context) == null)
//                return null;
//        }
        return intent;
    }

    public void writeConfigFile(Context context) throws IOException {
        FileWriter cfg = new FileWriter(VPNLaunchHelper.getConfigFilePath(context));
        cfg.write(getConfigFile(context, false));
        cfg.flush();
        cfg.close();
    }

    public Intent getStartServiceIntent(Context context) {
        String prefix = context.getPackageName();
        Intent intent = new Intent(context, OpenVPNService.class);
        intent.putExtra(prefix + ".profileUUID", mUuid.toString());
        intent.putExtra(prefix + ".profileVersion", mVersion);
        return intent;
    }

    public String[] getKeyStoreCertificates(Context context) {
        return getKeyStoreCertificates(context, 5);
    }

    public void checkForRestart(final Context context) {
        /* This method is called when OpenVPNService is restarted */
        if ((mAuthenticationType == VpnProfile.TYPE_KEYSTORE || mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE) && mPrivateKey == null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    getKeyStoreCertificates(context);
                }
            }).start();
        }
    }

    @Override
    protected VpnProfile clone() throws CloneNotSupportedException {
        VpnProfile copy = (VpnProfile) super.clone();
        copy.mUuid = UUID.randomUUID();
        copy.mConnections = new Connection[mConnections.length];
        int i = 0;
        for (Connection conn : mConnections) {
            copy.mConnections[i++] = conn.clone();
        }
        copy.mAllowedAppsVpn = (HashSet<String>) mAllowedAppsVpn.clone();
        return copy;
    }

    public VpnProfile copy(String name) {
        try {
            VpnProfile copy = clone();
            copy.mName = name;
            return copy;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void pwDidFail(Context c) {
    }

    synchronized String[] getKeyStoreCertificates(Context context, int tries) {
        // Force application context- KeyChain methods will block long enough that by the time they
        // are finished and try to unbind, the original activity context might have been destroyed.
        context = context.getApplicationContext();
        try {
            PrivateKey privateKey = KeyChain.getPrivateKey(context, mAlias);
            mPrivateKey = privateKey;
            String keystoreChain = null;
            X509Certificate[] caChain = KeyChain.getCertificateChain(context, mAlias);
            if (caChain == null) throw new NoCertReturnedException("No certificate returned from Keystore");
            if (caChain.length <= 1 && TextUtils.isEmpty(mCaFilename)) {
                VpnStatus.logMessage(VpnStatus.LogLevel.ERROR, "", context.getString(R.string.keychain_nocacert));
            } else {
                StringWriter ksStringWriter = new StringWriter();
                PemWriter pw = new PemWriter(ksStringWriter);
                for (int i = 1; i < caChain.length; i++) {
                    X509Certificate cert = caChain[i];
                    pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
                }
                pw.close();
                keystoreChain = ksStringWriter.toString();
            }
            String caout = null;
            if (!TextUtils.isEmpty(mCaFilename)) {
                try {
                    Certificate[] cacerts = X509Utils.getCertificatesFromFile(mCaFilename);
                    StringWriter caoutWriter = new StringWriter();
                    PemWriter pw = new PemWriter(caoutWriter);
                    for (Certificate cert : cacerts)
                        pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
                    pw.close();
                    caout = caoutWriter.toString();
                } catch (Exception e) {
                    VpnStatus.logError("Could not read CA certificate" + e.getLocalizedMessage());
                }
            }
            StringWriter certout = new StringWriter();
            if (caChain.length >= 1) {
                X509Certificate usercert = caChain[0];
                PemWriter upw = new PemWriter(certout);
                upw.writeObject(new PemObject("CERTIFICATE", usercert.getEncoded()));
                upw.close();
            }
            String user = certout.toString();
            String ca, extra;
            if (caout == null) {
                ca = keystoreChain;
                extra = null;
            } else {
                ca = caout;
                extra = keystoreChain;
            }
            return new String[]{ca, extra, user};
        } catch (InterruptedException | IOException | KeyChainException | NoCertReturnedException | IllegalArgumentException | CertificateException e) {
            e.printStackTrace();
            VpnStatus.logError(R.string.keyChainAccessError, e.getLocalizedMessage());
            VpnStatus.logError(R.string.keychain_access);
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                if (!mAlias.matches("^[a-zA-Z0-9]$")) {
                    VpnStatus.logError(R.string.jelly_keystore_alphanumeric_bug);
                }
            }
            return null;
        } catch (AssertionError e) {
            if (tries == 0) return null;
            VpnStatus.logError(String.format("Failure getting Keystore Keys (%s), retrying", e.getLocalizedMessage()));
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e1) {
                VpnStatus.logException(e1);
            }
            return getKeyStoreCertificates(context, tries - 1);
        }
    }

    //! Return an error if something is wrong
    public int checkProfile(Context context) {
        if (mAuthenticationType == TYPE_KEYSTORE || mAuthenticationType == TYPE_USERPASS_KEYSTORE) {
            if (mAlias == null) return R.string.no_keystore_cert_selected;
        } else if (mAuthenticationType == TYPE_CERTIFICATES || mAuthenticationType == TYPE_USERPASS_CERTIFICATES) {
            if (TextUtils.isEmpty(mCaFilename)) return R.string.no_ca_cert_selected;
        }
        if (mCheckRemoteCN && mX509AuthType == X509_VERIFY_TLSREMOTE) return R.string.deprecated_tls_remote;
        if (!mUsePull || mAuthenticationType == TYPE_STATICKEYS) {
            if (mIPv4Address == null || cidrToIPAndNetmask(mIPv4Address) == null) return R.string.ipv4_format_error;
        }
        if (!mUseDefaultRoute) {
            if (!TextUtils.isEmpty(mCustomRoutes) && getCustomRoutes(mCustomRoutes).size() == 0) return R.string.custom_route_format_error;
            if (!TextUtils.isEmpty(mExcludedRoutes) && getCustomRoutes(mExcludedRoutes).size() == 0) return R.string.custom_route_format_error;
        }
        if (mUseTLSAuth && TextUtils.isEmpty(mTLSAuthFilename)) return R.string.missing_tlsauth;
        if ((mAuthenticationType == TYPE_USERPASS_CERTIFICATES || mAuthenticationType == TYPE_CERTIFICATES) && (TextUtils.isEmpty(mClientCertFilename) || TextUtils.isEmpty(mClientKeyFilename)))
            return R.string.missing_certificates;
        if ((mAuthenticationType == TYPE_CERTIFICATES || mAuthenticationType == TYPE_USERPASS_CERTIFICATES) && TextUtils.isEmpty(mCaFilename)) return R.string.missing_ca_certificate;
        boolean noRemoteEnabled = true;
        for (Connection c : mConnections)
            if (c.mEnabled) noRemoteEnabled = false;
        if (noRemoteEnabled) return R.string.remote_no_server_selected;
        // Everything okay
        return R.string.no_error_found;
    }

    //! Openvpn asks for a "Private Key", this should be pkcs12 key
    //
    public String getPasswordPrivateKey() {
        String cachedPw = PasswordCache.getPKCS12orCertificatePassword(mUuid, true);
        if (cachedPw != null) {
            return cachedPw;
        }
        switch (mAuthenticationType) {
            case TYPE_PKCS12:
            case TYPE_USERPASS_PKCS12:
                return mPKCS12Password;
            case TYPE_CERTIFICATES:
            case TYPE_USERPASS_CERTIFICATES:
                return mKeyPassword;
            case TYPE_USERPASS:
            case TYPE_STATICKEYS:
            default:
                return null;
        }
    }

    public boolean isUserPWAuth() {
        switch (mAuthenticationType) {
            case TYPE_USERPASS:
            case TYPE_USERPASS_CERTIFICATES:
            case TYPE_USERPASS_KEYSTORE:
            case TYPE_USERPASS_PKCS12:
                return true;
            default:
                return false;
        }
    }

    public boolean requireTLSKeyPassword() {
        if (TextUtils.isEmpty(mClientKeyFilename)) return false;
        String data = "";
        if (isEmbedded(mClientKeyFilename)) data = mClientKeyFilename;
        else {
            char[] buf = new char[2048];
            FileReader fr;
            try {
                fr = new FileReader(mClientKeyFilename);
                int len = fr.read(buf);
                while (len > 0) {
                    data += new String(buf, 0, len);
                    len = fr.read(buf);
                }
                fr.close();
            } catch (FileNotFoundException e) {
                return false;
            } catch (IOException e) {
                return false;
            }
        }
        if (data.contains("Proc-Type: 4,ENCRYPTED")) return true;
        else return data.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----");
    }

    public int needUserPWInput(String transientCertOrPkcs12PW, String mTransientAuthPW) {
        if ((mAuthenticationType == TYPE_PKCS12 || mAuthenticationType == TYPE_USERPASS_PKCS12) && (mPKCS12Password == null || mPKCS12Password.equals(""))) {
            if (transientCertOrPkcs12PW == null) return R.string.pkcs12_file_encryption_key;
        }
        if (mAuthenticationType == TYPE_CERTIFICATES || mAuthenticationType == TYPE_USERPASS_CERTIFICATES) {
            if (requireTLSKeyPassword() && TextUtils.isEmpty(mKeyPassword)) if (transientCertOrPkcs12PW == null) {
                return R.string.private_key_password;
            }
        }
        if (isUserPWAuth() && (TextUtils.isEmpty(mUsername) || (TextUtils.isEmpty(mPassword) && mTransientAuthPW == null))) {
            return R.string.password;
        }
        return 0;
    }

    public String getPasswordAuth() {
        String cachedPw = PasswordCache.getAuthPassword(mUuid, true);
        if (cachedPw != null) {
            return cachedPw;
        } else {
            return mPassword;
        }
    }

    // Used by the Array Adapter
    @Override
    public String toString() {
        return mName;
    }

    public String getUUIDString() {
        return mUuid.toString();
    }

    public PrivateKey getKeystoreKey() {
        return mPrivateKey;
    }

    public String getSignedData(String b64data) {
        PrivateKey privkey = getKeystoreKey();
        byte[] data = Base64.decode(b64data, Base64.DEFAULT);
        // The Jelly Bean *evil* Hack
        // 4.2 implements the RSA/ECB/PKCS1PADDING in the OpenSSLprovider
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
            return processSignJellyBeans(privkey, data);
        }
        try {
            /* ECB is perfectly fine in this special case, since we are using it for
               the public/private part in the TLS exchange
             */
            @SuppressLint("GetInstance") Cipher rsaSigner = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
            rsaSigner.init(Cipher.ENCRYPT_MODE, privkey);
            byte[] signed_bytes = rsaSigner.doFinal(data);
            return Base64.encodeToString(signed_bytes, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException e) {
            VpnStatus.logError(R.string.error_rsa_sign, e.getClass().toString(), e.getLocalizedMessage());
            return null;
        }
    }

    private String processSignJellyBeans(PrivateKey privkey, byte[] data) {
        try {
            Method getKey = privkey.getClass().getSuperclass().getDeclaredMethod("getOpenSSLKey");
            getKey.setAccessible(true);
            // Real object type is OpenSSLKey
            Object opensslkey = getKey.invoke(privkey);
            getKey.setAccessible(false);
            Method getPkeyContext = opensslkey.getClass().getDeclaredMethod("getPkeyContext");
            // integer pointer to EVP_pkey
            getPkeyContext.setAccessible(true);
            int pkey = (Integer) getPkeyContext.invoke(opensslkey);
            getPkeyContext.setAccessible(false);
            // 112 with TLS 1.2 (172 back with 4.3), 36 with TLS 1.0
            byte[] signed_bytes = NativeUtils.rsasign(data, pkey);
            return Base64.encodeToString(signed_bytes, Base64.NO_WRAP);
        } catch (NoSuchMethodException | InvalidKeyException | InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
            VpnStatus.logError(R.string.error_rsa_sign, e.getClass().toString(), e.getLocalizedMessage());
            return null;
        }
    }

    class NoCertReturnedException extends Exception {
        public NoCertReturnedException(String msg) {
            super(msg);
        }
    }
}
