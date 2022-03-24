/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.content.Context;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import ru.yourok.openvpn.BuildConfig;
import ru.yourok.openvpn.R;

import junit.framework.Assert;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Vector;

import de.blinkt.openvpn.VpnProfile;

public class OpenVpnManagementThread implements Runnable, OpenVPNManagement {
    private static final String TAG = "openvpn";
    private static final Vector<OpenVpnManagementThread> active = new Vector<>();
    private final Handler mResumeHandler;
    private LocalSocket mSocket;
    private VpnProfile mProfile;
    private OpenVPNService mOpenVPNService;
    private LinkedList<FileDescriptor> mFDList = new LinkedList<>();
    private LocalServerSocket mServerSocket;
    private boolean mWaitingForRelease = false;
    private long mLastHoldRelease = 0;
    private pauseReason lastPauseReason = pauseReason.noNetwork;
    private PausedStateCallback mPauseCallback;
    private boolean mShuttingDown;
    private Runnable mResumeHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (shouldBeRunning()) {
                releaseHoldCmd();
            }
        }
    };

    public OpenVpnManagementThread(VpnProfile profile, OpenVPNService openVpnService) {
        mProfile = profile;
        mOpenVPNService = openVpnService;
        mResumeHandler = new Handler(openVpnService.getMainLooper());
    }

    private static boolean stopOpenVPN() {
        synchronized (active) {
            boolean sendCMD = false;
            for (OpenVpnManagementThread mt : active) {
                sendCMD = mt.managmentCommand("signal SIGINT\n");
                try {
                    if (mt.mSocket != null) mt.mSocket.close();
                } catch (IOException e) {
                    // Ignore close error on already closed socket
                }
            }
            return sendCMD;
        }
    }

    public boolean openManagementInterface(@NonNull Context c) {
        // Could take a while to open connection
        int tries = 8;
        String socketName = (c.getCacheDir().getAbsolutePath() + "/" + "mgmtsocket");
        // The mServerSocketLocal is transferred to the LocalServerSocket, ignore warning
        LocalSocket mServerSocketLocal = new LocalSocket();
        while (tries > 0 && !mServerSocketLocal.isBound()) {
            try {
                mServerSocketLocal.bind(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.FILESYSTEM));
            } catch (IOException e) {
                // wait 300 ms before retrying
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
            }
            tries--;
        }
        try {
            mServerSocket = new LocalServerSocket(mServerSocketLocal.getFileDescriptor());
            return true;
        } catch (IOException e) {
            VpnStatus.logException(e);
        }
        return false;
    }

    /**
     * @param cmd command to write to management socket
     * @return true if command have been sent
     */
    public boolean managmentCommand(String cmd) {
        try {
            if (mSocket != null && mSocket.getOutputStream() != null) {
                mSocket.getOutputStream().write(cmd.getBytes());
                mSocket.getOutputStream().flush();
                return true;
            }
        } catch (IOException e) {
            // Ignore socket stack traces
        }
        return false;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048];
        //	mSocket.setSoTimeout(5); // Setting a timeout cannot be that bad
        String pendingInput = "";
        synchronized (active) {
            active.add(this);
        }
        try {
            // Wait for a client to connect
            mSocket = mServerSocket.accept();
            InputStream instream = mSocket.getInputStream();
            // Close the management socket after client connected
            try {
                mServerSocket.close();
            } catch (IOException e) {
                VpnStatus.logException(e);
            }
            // Closing one of the two sockets also closes the other
            //mServerSocketLocal.close();
            while (true) {
                int numbytesread = instream.read(buffer);
                if (numbytesread == -1) return;
                FileDescriptor[] fds = null;
                try {
                    fds = mSocket.getAncillaryFileDescriptors();
                } catch (IOException e) {
                    VpnStatus.logException("Error reading fds from socket", e);
                }
                if (fds != null) {
                    Collections.addAll(mFDList, fds);
                }
                String input = new String(buffer, 0, numbytesread, "UTF-8");
                pendingInput += input;
                pendingInput = processInput(pendingInput);
            }
        } catch (IOException e) {
            if (!e.getMessage().equals("socket closed") && !e.getMessage().equals("Connection reset by peer")) VpnStatus.logException(e);
        }
        synchronized (active) {
            active.remove(this);
        }
    }

    //! Hack O Rama 2000!
    private void protectFileDescriptor(FileDescriptor fd) {
        try {
            Method getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
            int fdint = (Integer) getInt.invoke(fd);
            // You can even get more evil by parsing toString() and extract the int from that :)
            boolean result = mOpenVPNService.protect(fdint);
            if (!result) VpnStatus.logWarning("Could not protect VPN socket");
            //ParcelFileDescriptor pfd = ParcelFileDescriptor.fromFd(fdint);
            //pfd.close();
            NativeUtils.jniclose(fdint);
            return;
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException | NullPointerException e) {
            VpnStatus.logException("Failed to retrieve fd from socket (" + fd + ")", e);
        }
        Log.d("Openvpn", "Failed to retrieve fd from socket: " + fd);
    }

    private String processInput(String pendingInput) {
        while (pendingInput.contains("\n")) {
            String[] tokens = pendingInput.split("\\r?\\n", 2);
            processCommand(tokens[0]);
            if (tokens.length == 1)
                // No second part, newline was at the end
                pendingInput = "";
            else pendingInput = tokens[1];
        }
        return pendingInput;
    }

    private void processCommand(String command) {
        //Log.i(TAG, "Line from managment" + command);
        if (command.startsWith(">") && command.contains(":")) {
            String[] parts = command.split(":", 2);
            String cmd = parts[0].substring(1);
            String argument = parts[1];
            switch (cmd) {
                case "INFO":
                /* Ignore greeting from management */
                    return;
                case "PASSWORD":
                    processPWCommand(argument);
                    break;
                case "HOLD":
                    handleHold(argument);
                    break;
                case "NEED-OK":
                    processNeedCommand(argument);
                    break;
                case "BYTECOUNT":
                    processByteCount(argument);
                    break;
                case "STATE":
                    if (!mShuttingDown) processState(argument);
                    break;
                case "PROXY":
                    processProxyCMD(argument);
                    break;
                case "LOG":
                    processLogMessage(argument);
                    break;
                case "RSA_SIGN":
                    processSignCommand(argument);
                    break;
                default:
                    VpnStatus.logWarning("MGMT: Got unrecognized command" + command);
                    Log.i(TAG, "Got unrecognized command" + command);
                    break;
            }
        } else if (command.startsWith("SUCCESS:")) {
            /* Ignore this kind of message too */
            return;
        } else if (command.startsWith("PROTECTFD: ")) {
            FileDescriptor fdtoprotect = mFDList.pollFirst();
            if (fdtoprotect != null) protectFileDescriptor(fdtoprotect);
        } else {
            Log.i(TAG, "Got unrecognized line from managment" + command);
            VpnStatus.logWarning("MGMT: Got unrecognized line from management:" + command);
        }
    }

    private void processLogMessage(String argument) {
        String[] args = argument.split(",", 4);
        // 0 unix time stamp
        // 1 log level N,I,E etc.
                /*
                  (b) zero or more message flags in a single string:
          I -- informational
          F -- fatal error
          N -- non-fatal error
          W -- warning
          D -- debug, and
                 */
        // 2 log message
        Log.d("OpenVPN", argument);
        VpnStatus.LogLevel level;
        switch (args[1]) {
            case "I":
                level = VpnStatus.LogLevel.INFO;
                break;
            case "W":
                level = VpnStatus.LogLevel.WARNING;
                break;
            case "D":
                level = VpnStatus.LogLevel.VERBOSE;
                break;
            case "F":
                level = VpnStatus.LogLevel.ERROR;
                break;
            default:
                level = VpnStatus.LogLevel.INFO;
                break;
        }
        int ovpnlevel = Integer.parseInt(args[2]) & 0x0F;
        String msg = args[3];
        if (msg.startsWith("MANAGEMENT: CMD")) ovpnlevel = Math.max(4, ovpnlevel);
        VpnStatus.logMessageOpenVPN(level, ovpnlevel, msg);
    }

    boolean shouldBeRunning() {
        if (mPauseCallback == null) return false;
        else return mPauseCallback.shouldBeRunning();
    }

    private void handleHold(String argument) {
        mWaitingForRelease = true;
        int waittime = Integer.parseInt(argument.split(":")[1]);
        if (shouldBeRunning()) {
            if (waittime > 1) VpnStatus.updateStateString("CONNECTRETRY", String.valueOf(waittime), R.string.state_waitconnectretry, ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET);
            mResumeHandler.postDelayed(mResumeHoldRunnable, waittime * 1000);
            if (waittime > 5) VpnStatus.logInfo(R.string.state_waitconnectretry, String.valueOf(waittime));
            else VpnStatus.logDebug(R.string.state_waitconnectretry, String.valueOf(waittime));
        } else {
            VpnStatus.updateStatePause(lastPauseReason);
        }
    }

    private void releaseHoldCmd() {
        mResumeHandler.removeCallbacks(mResumeHoldRunnable);
        if ((System.currentTimeMillis() - mLastHoldRelease) < 5000) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
        }
        mWaitingForRelease = false;
        mLastHoldRelease = System.currentTimeMillis();
        managmentCommand("hold release\n");
        managmentCommand("bytecount " + mBytecountInterval + "\n");
        managmentCommand("state on\n");
        //managmentCommand("log on all\n");
    }

    public void releaseHold() {
        if (mWaitingForRelease) releaseHoldCmd();
    }

    private void processProxyCMD(String argument) {
        String[] args = argument.split(",", 3);
        SocketAddress proxyaddr = ProxyDetection.detectProxy(mProfile);
        if (args.length >= 2) {
            String proto = args[1];
            if (proto.equals("UDP")) {
                proxyaddr = null;
            }
        }
        if (proxyaddr instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) proxyaddr;
            VpnStatus.logInfo(R.string.using_proxy, isa.getHostName(), isa.getPort());
            String proxycmd = String.format(Locale.ENGLISH, "proxy HTTP %s %d\n", isa.getHostName(), isa.getPort());
            managmentCommand(proxycmd);
        } else {
            managmentCommand("proxy NONE\n");
        }
    }

    private void processState(String argument) {
        String[] args = argument.split(",", 3);
        String currentstate = args[1];
        if (args[2].equals(",,")) VpnStatus.updateStateString(currentstate, "");
        else VpnStatus.updateStateString(currentstate, args[2]);
    }

    private void processByteCount(String argument) {
        //   >BYTECOUNT:{BYTES_IN},{BYTES_OUT}
        int comma = argument.indexOf(',');
        long in = Long.parseLong(argument.substring(0, comma));
        long out = Long.parseLong(argument.substring(comma + 1));
        VpnStatus.updateByteCount(in, out);
    }

    private void processNeedCommand(String argument) {
        int p1 = argument.indexOf('\'');
        int p2 = argument.indexOf('\'', p1 + 1);
        String needed = argument.substring(p1 + 1, p2);
        String extra = argument.split(":", 2)[1];
        String status = "ok";
        switch (needed) {
            case "PROTECTFD":
                FileDescriptor fdtoprotect = mFDList.pollFirst();
                protectFileDescriptor(fdtoprotect);
                break;
            case "DNSSERVER":
            case "DNS6SERVER":
                mOpenVPNService.addDNS(extra);
                break;
            case "DNSDOMAIN":
                mOpenVPNService.setDomain(extra);
                break;
            case "ROUTE": {
                String[] routeparts = extra.split(" ");
            /*
            buf_printf (&out, "%s %s %s dev %s", network, netmask, gateway, rgi->iface);
            else
            buf_printf (&out, "%s %s %s", network, netmask, gateway);
            */
                if (routeparts.length == 5) {
                    if (BuildConfig.DEBUG) Assert.assertEquals("dev", routeparts[3]);
                    mOpenVPNService.addRoute(routeparts[0], routeparts[1], routeparts[2], routeparts[4]);
                } else if (routeparts.length >= 3) {
                    mOpenVPNService.addRoute(routeparts[0], routeparts[1], routeparts[2], null);
                } else {
                    VpnStatus.logError("Unrecognized ROUTE cmd:" + Arrays.toString(routeparts) + " | " + argument);
                }
                break;
            }
            case "ROUTE6": {
                String[] routeparts = extra.split(" ");
                mOpenVPNService.addRoutev6(routeparts[0], routeparts[1]);
                break;
            }
            case "IFCONFIG":
                String[] ifconfigparts = extra.split(" ");
                int mtu = Integer.parseInt(ifconfigparts[2]);
                mOpenVPNService.setLocalIP(ifconfigparts[0], ifconfigparts[1], mtu, ifconfigparts[3]);
                break;
            case "IFCONFIG6":
                mOpenVPNService.setLocalIPv6(extra);
                break;
            case "PERSIST_TUN_ACTION":
                // check if tun cfg stayed the same
                status = mOpenVPNService.getTunReopenStatus();
                break;
            case "OPENTUN":
                if (sendTunFD(needed, extra)) return;
                else status = "cancel";
                // This not nice or anything but setFileDescriptors accepts only FilDescriptor class :(
                break;
            default:
                Log.e(TAG, "Unknown needok command " + argument);
                return;
        }
        String cmd = String.format("needok '%s' %s\n", needed, status);
        managmentCommand(cmd);
    }

    private boolean sendTunFD(String needed, String extra) {
        if (!extra.equals("tun")) {
            // We only support tun
            VpnStatus.logError(String.format("Device type %s requested, but only tun is possible with the Android API, sorry!", extra));
            return false;
        }
        ParcelFileDescriptor pfd = mOpenVPNService.openTun();
        if (pfd == null) return false;
        Method setInt;
        int fdint = pfd.getFd();
        try {
            setInt = FileDescriptor.class.getDeclaredMethod("setInt$", int.class);
            FileDescriptor fdtosend = new FileDescriptor();
            setInt.invoke(fdtosend, fdint);
            FileDescriptor[] fds = {fdtosend};
            mSocket.setFileDescriptorsForSend(fds);
            // Trigger a send so we can close the fd on our side of the channel
            // The API documentation fails to mention that it will not reset the file descriptor to
            // be send and will happily send the file descriptor on every write ...
            String cmd = String.format("needok '%s' %s\n", needed, "ok");
            managmentCommand(cmd);
            // Set the FileDescriptor to null to stop this mad behavior
            mSocket.setFileDescriptorsForSend(null);
            pfd.close();
            return true;
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IOException | IllegalAccessException exp) {
            VpnStatus.logException("Could not send fd over socket", exp);
        }
        return false;
    }

    private void processPWCommand(String argument) {
        //argument has the form 	Need 'Private Key' password
        // or  ">PASSWORD:Verification Failed: '%s' ['%s']"
        String needed;
        try {
            int p1 = argument.indexOf('\'');
            int p2 = argument.indexOf('\'', p1 + 1);
            needed = argument.substring(p1 + 1, p2);
            if (argument.startsWith("Verification Failed")) {
                proccessPWFailed(needed, argument.substring(p2 + 1));
                return;
            }
        } catch (StringIndexOutOfBoundsException sioob) {
            VpnStatus.logError("Could not parse management Password command: " + argument);
            return;
        }
        String pw = null;
        if (needed.equals("Private Key")) {
            pw = mProfile.getPasswordPrivateKey();
        } else if (needed.equals("Auth")) {
            pw = mProfile.getPasswordAuth();
            String usercmd = String.format("username '%s' %s\n", needed, VpnProfile.openVpnEscape(mProfile.mUsername));
            managmentCommand(usercmd);
        }
        if (pw != null) {
            String cmd = String.format("password '%s' %s\n", needed, VpnProfile.openVpnEscape(pw));
            managmentCommand(cmd);
        } else {
            mOpenVPNService.requestInputFromUser(R.string.password, needed);
            VpnStatus.logError(String.format("Openvpn requires Authentication type '%s' but no password/key information available", needed));
        }
    }

    private void proccessPWFailed(String needed, String args) {
        VpnStatus.updateStateString("AUTH_FAILED", needed + args, R.string.state_auth_failed, ConnectionStatus.LEVEL_AUTH_FAILED);
    }

    @Override
    public void networkChange(boolean samenetwork) {
        if (mWaitingForRelease) releaseHold();
        else if (samenetwork) managmentCommand("network-change\n");
        else managmentCommand("network-change\n");
    }

    @Override
    public void setPauseCallback(PausedStateCallback callback) {
        mPauseCallback = callback;
    }

    public void signalusr1() {
        mResumeHandler.removeCallbacks(mResumeHoldRunnable);
        if (!mWaitingForRelease) managmentCommand("signal SIGUSR1\n");
        else
            // If signalusr1 is called update the state string
            // if there is another for stopping
            VpnStatus.updateStatePause(lastPauseReason);
    }

    public void reconnect() {
        signalusr1();
        releaseHold();
    }

    private void processSignCommand(String b64data) {
        String signed_string = mProfile.getSignedData(b64data);
        if (signed_string == null) {
            managmentCommand("rsa-sig\n");
            managmentCommand("\nEND\n");
            stopOpenVPN();
            return;
        }
        managmentCommand("rsa-sig\n");
        managmentCommand(signed_string);
        managmentCommand("\nEND\n");
    }

    @Override
    public void pause(pauseReason reason) {
        lastPauseReason = reason;
        signalusr1();
    }

    @Override
    public void resume() {
        releaseHold();
        /* Reset the reason why we are disconnected */
        lastPauseReason = pauseReason.noNetwork;
    }

    @Override
    public boolean stopVPN(boolean replaceConnection) {
        boolean stopSucceed = stopOpenVPN();
        if (stopSucceed) {
            mShuttingDown = true;
        }
        return stopSucceed;
    }
}
