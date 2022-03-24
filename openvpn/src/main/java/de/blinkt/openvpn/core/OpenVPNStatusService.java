/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * Created by arne on 08.11.16.
 */
public class OpenVPNStatusService extends Service implements VpnStatus.LogListener, VpnStatus.ByteCountListener, VpnStatus.StateListener {
    static final RemoteCallbackList<IStatusCallbacks> mCallbacks =
            new RemoteCallbackList<>();
    private static final OpenVPNStatusHandler mHandler = new OpenVPNStatusHandler();
    private static final int SEND_NEW_LOGITEM = 100;
    private static final int SEND_NEW_STATE = 101;
    private static final int SEND_NEW_BYTECOUNT = 102;
    private static final int SEND_NEW_CONNECTED_VPN = 103;
    static UpdateMessage mLastUpdateMessage;
    private static final IServiceStatus.Stub mBinder = new IServiceStatus.Stub() {
        @Override
        public ParcelFileDescriptor registerStatusCallback(IStatusCallbacks cb) throws RemoteException {
            final LogItem[] logbuffer = VpnStatus.getlogbuffer();
            if (mLastUpdateMessage != null)
                sendUpdate(cb, mLastUpdateMessage);
            mCallbacks.register(cb);
            try {
                final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                new Thread("pushLogs") {
                    @Override
                    public void run() {
                        DataOutputStream fd = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
                        try {
                            synchronized (VpnStatus.readFileLock) {
                                if (!VpnStatus.readFileLog) {
                                    VpnStatus.readFileLock.wait();
                                }
                            }
                        } catch (InterruptedException e) {
                            VpnStatus.logException(e);
                        }
                        try {
                            for (LogItem logItem : logbuffer) {
                                byte[] bytes = logItem.getMarschaledBytes();
                                fd.writeShort(bytes.length);
                                fd.write(bytes);
                            }
                            // Mark end
                            fd.writeShort(0x7fff);
                            fd.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.start();
                return pipe[0];
            } catch (IOException e) {
                e.printStackTrace();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                    throw new RemoteException(e.getMessage());
                }
                return null;
            }
        }
        @Override
        public void unregisterStatusCallback(IStatusCallbacks cb) throws RemoteException {
            mCallbacks.unregister(cb);
        }
        @Override
        public String getLastConnectedVPN() throws RemoteException {
            return VpnStatus.getLastConnectedVPNProfile();
        }
        @Override
        public void setCachedPassword(String uuid, int type, String password) {
            PasswordCache.setCachedPassword(uuid, type, password);
        }
        @Override
        public TrafficHistory getTrafficHistory() throws RemoteException {
            return VpnStatus.trafficHistory;
        }
    };
    private static void sendUpdate(IStatusCallbacks broadcastItem,
                                   UpdateMessage um) throws RemoteException {
        broadcastItem.updateStateString(um.state, um.logmessage, um.resId, um.level);
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        VpnStatus.addLogListener(this);
        VpnStatus.addByteCountListener(this);
        VpnStatus.addStateListener(this);
        mHandler.setService(this);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        VpnStatus.removeLogListener(this);
        VpnStatus.removeByteCountListener(this);
        VpnStatus.removeStateListener(this);
        mCallbacks.kill();
    }
    @Override
    public void newLog(LogItem logItem) {
        Message msg = mHandler.obtainMessage(SEND_NEW_LOGITEM, logItem);
        msg.sendToTarget();
    }
    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        Message msg = mHandler.obtainMessage(SEND_NEW_BYTECOUNT, Pair.create(in, out));
        msg.sendToTarget();
    }
    @Override
    public void updateState(String state, String logmessage, int localizedResId, ConnectionStatus level) {
        mLastUpdateMessage = new UpdateMessage(state, logmessage, localizedResId, level);
        Message msg = mHandler.obtainMessage(SEND_NEW_STATE, mLastUpdateMessage);
        msg.sendToTarget();
    }
    @Override
    public void setConnectedVPN(String uuid) {
        Message msg = mHandler.obtainMessage(SEND_NEW_CONNECTED_VPN, uuid);
        msg.sendToTarget();
    }
    static class UpdateMessage {
        public String state;
        public String logmessage;
        public ConnectionStatus level;
        int resId;
        UpdateMessage(String state, String logmessage, int resId, ConnectionStatus level) {
            this.state = state;
            this.resId = resId;
            this.logmessage = logmessage;
            this.level = level;
        }
    }
    private static class OpenVPNStatusHandler extends Handler {
        WeakReference<OpenVPNStatusService> service = null;
        private void setService(OpenVPNStatusService statusService) {
            service = new WeakReference<>(statusService);
        }
        @Override
        public void handleMessage(Message msg) {
            RemoteCallbackList<IStatusCallbacks> callbacks;
            if (service == null || service.get() == null)
                return;
            callbacks = service.get().mCallbacks;
            // Broadcast to all clients the new value.
            final int N = callbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    IStatusCallbacks broadcastItem = callbacks.getBroadcastItem(i);
                    switch (msg.what) {
                        case SEND_NEW_LOGITEM:
                            broadcastItem.newLogItem((LogItem) msg.obj);
                            break;
                        case SEND_NEW_BYTECOUNT:
                            Pair<Long, Long> inout = (Pair<Long, Long>) msg.obj;
                            broadcastItem.updateByteCount(inout.first, inout.second);
                            break;
                        case SEND_NEW_STATE:
                            sendUpdate(broadcastItem, (UpdateMessage) msg.obj);
                            break;
                        case SEND_NEW_CONNECTED_VPN:
                            broadcastItem.connectedVPN((String) msg.obj);
                            break;
                    }
                } catch (RemoteException e) {
                    // The RemoteCallbackList will take care of removing
                    // the dead object for us.
                }
            }
            callbacks.finishBroadcast();
        }
    }
}