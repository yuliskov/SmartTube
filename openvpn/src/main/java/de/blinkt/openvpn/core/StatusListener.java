/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by arne on 09.11.16.
 */
public class StatusListener {
    private File mCacheDir;
    private IStatusCallbacks mCallback = new IStatusCallbacks.Stub() {
        @Override
        public void newLogItem(LogItem item) throws RemoteException {
            VpnStatus.newLogItem(item);
        }

        @Override
        public void updateStateString(String state, String msg, int resid, ConnectionStatus level) throws RemoteException {
            VpnStatus.updateStateString(state, msg, resid, level);
        }

        @Override
        public void updateByteCount(long inBytes, long outBytes) throws RemoteException {
            VpnStatus.updateByteCount(inBytes, outBytes);
        }

        @Override
        public void connectedVPN(String uuid) throws RemoteException {
            VpnStatus.setConnectedVPNProfile(uuid);
        }
    };
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            IServiceStatus serviceStatus = IServiceStatus.Stub.asInterface(service);
            try {
                /* Check if this a local service ... */
                if (service.queryLocalInterface("de.blinkt.openvpn.core.IServiceStatus") == null) {
                    // Not a local service
                    VpnStatus.setConnectedVPNProfile(serviceStatus.getLastConnectedVPN());
                    VpnStatus.setTrafficHistory(serviceStatus.getTrafficHistory());
                    ParcelFileDescriptor pfd = serviceStatus.registerStatusCallback(mCallback);
                    DataInputStream fd = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(pfd));
                    short len = fd.readShort();
                    byte[] buf = new byte[65336];
                    while (len != 0x7fff) {
                        fd.readFully(buf, 0, len);
                        LogItem logitem = new LogItem(buf, len);
                        VpnStatus.newLogItem(logitem, false);
                        len = fd.readShort();
                    }
                    fd.close();
                } else {
                    VpnStatus.initLogCache(mCacheDir);
                }
            } catch (RemoteException | IOException e) {
                e.printStackTrace();
                VpnStatus.logException(e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    void init(Context c) {
        Intent intent = new Intent(c, OpenVPNStatusService.class);
        intent.setAction(OpenVPNService.START_SERVICE);
        mCacheDir = c.getCacheDir();
        c.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
}
