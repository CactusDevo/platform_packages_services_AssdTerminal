package org.simalliance.openmobileapi.assdterminal;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.NoSuchElementException;

import org.simalliance.openmobileapi.service.ITerminalService;
import org.simalliance.openmobileapi.service.OpenLogicalChannelResponse;
import org.simalliance.openmobileapi.service.SmartcardError;


public final class AssdTerminal extends Service {

    private static final String TAG = "AssdTerminal";

    public static final String SD_TERMINAL = "SD";

    public static final String ACTION_SD_STATE_CHANGED = "org.simalliance.openmobileapi.action.SD_STATE_CHANGED";

    private final ITerminalService.Stub mTerminalBinder = new TerminalServiceImplementation();

    private static boolean JNILoaded = false;

    private BroadcastReceiver mMediaReceiver;

    private boolean isOpenedSuccesful;
    @Override
    public IBinder onBind(Intent intent) throws SecurityException {
        return mTerminalBinder;
    }

    @Override
    public void onCreate() {
        registerMediaMountedEvent(this);
        if (!JNILoaded) {
            return;
        }
        try {
            isOpenedSuccesful = open();
        } catch (Exception e) {
            Log.e(TAG, "Error while Open method", e);
            isOpenedSuccesful = false;
        }
    }

    @Override
    public void onDestroy() {
        if (JNILoaded) {
            try {
                close();
            } catch (Exception e) {
                Log.e(TAG, "Error while Open method", e);
            }
        }
        isOpenedSuccesful = false;
        unregisterMediaMountedEvent(this);
        super.onDestroy();
    }


    static {
        Runtime.getRuntime().loadLibrary("assd");
        JNILoaded = true;
    }

    private native void close() throws Exception;

    private native boolean open() throws Exception;

    private native boolean isPresent() throws Exception;

    private native byte[] transmit(byte[] command) throws Exception;

    public static String getType() {
        return SD_TERMINAL;
    }

    private void registerMediaMountedEvent(Context context) {
        Log.v(TAG, "register MEDIA_MOUNTED event");

        IntentFilter intentFilter = new IntentFilter(
                "android.intent.action.MEDIA_MOUNTED");
        mMediaReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final boolean mediaMounted = "android.intent.action.MEDIA_MOUNTED".equals(
                        intent.getAction());
                if (mediaMounted) {
                    Log.i(TAG, "New Media is mounted. Checking access rules"
                            + " for updates.");
                    Intent i = new Intent(ACTION_SD_STATE_CHANGED);
                    sendBroadcast(i);
                }
            }
        };
        context.registerReceiver(mMediaReceiver, intentFilter);
    }

    private void unregisterMediaMountedEvent(Context context) {
        if (mMediaReceiver != null) {
            Log.v(TAG, "unregister MEDIA_MOUNTED event");
            context.unregisterReceiver(mMediaReceiver);
            mMediaReceiver = null;
        }
    }

    /**
     * The Terminal service interface implementation.
     */
    final class TerminalServiceImplementation extends ITerminalService.Stub {
        @Override
        public String getType() {
            return AssdTerminal.getType();
        }

        @Override
        public OpenLogicalChannelResponse internalOpenLogicalChannel(
                byte[] aid,
                SmartcardError error) throws RemoteException {
            try {
                if (!JNILoaded || !isOpenedSuccesful) {
                    throw new IllegalStateException("JNI failed or open SE failed");
                }
                byte[] manageChannelCommand = new byte[]{
                        0x00, 0x70, 0x00, 0x00, 0x01
                };
                byte[] rsp = AssdTerminal.this.transmit(manageChannelCommand);
                if (rsp.length != 3) {
                    throw new NoSuchElementException("unsupported MANAGE CHANNEL response data");
                }
                int channelNumber = rsp[0] & 0xFF;
                if (channelNumber == 0 || channelNumber > 19) {
                    throw new NoSuchElementException("invalid logical channel number returned");
                }

                byte[] selectResponse = null;
                if (aid != null) {
                    byte[] selectCommand = new byte[aid.length + 6];
                    selectCommand[0] = (byte) channelNumber;
                    if (channelNumber > 3) {
                        selectCommand[0] |= 0x40;
                    }
                    selectCommand[1] = (byte) 0xA4;
                    selectCommand[2] = 0x04;
                    selectCommand[4] = (byte) aid.length;
                    System.arraycopy(aid, 0, selectCommand, 5, aid.length);
                    try {
                        selectResponse = AssdTerminal.this.transmit(selectCommand);
                        int length = selectResponse.length;
                        if (!(selectResponse[length - 2] == 0x90 && selectResponse[length - 1] == 0x00)
                                && !(selectResponse[length - 2] == 0x62)
                                && !(selectResponse[length - 2] == 0x63)) {
                            throw new NoSuchElementException("Select command failed");
                        }
                    } catch (Exception e) {
                        internalCloseLogicalChannel(channelNumber, error);
                        throw e;
                    }
                }

                return new OpenLogicalChannelResponse(channelNumber, selectResponse);
            } catch (Exception e) {
                error.set(e);
                return null;
            }
        }

        @Override
        public void internalCloseLogicalChannel(
                int channelNumber,
                SmartcardError error) throws RemoteException {
            try {
                if (!JNILoaded || !isOpenedSuccesful) {
                    throw new RuntimeException("JNI failed or open SE failed");
                }
                if (channelNumber > 0) {
                    byte cla = (byte) channelNumber;
                    if (channelNumber > 3) {
                        cla |= 0x40;
                    }
                    byte[] manageChannelClose = new byte[]{
                            cla, 0x70, (byte) 0x80, (byte) channelNumber
                    };

                    AssdTerminal.this.transmit(manageChannelClose);
                }
            } catch (Exception e) {
                error.set(e);
            }
        }

        @Override
        public byte[] internalTransmit(byte[] command, SmartcardError error) throws RemoteException {
            try {
                if (!JNILoaded || !isOpenedSuccesful) {
                    throw new RuntimeException("JNI failed or open SE failed");
                }
                byte[] response = AssdTerminal.this.transmit(command);
                if (response == null) {
                    throw new RuntimeException("transmit failed");
                }
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Error while transmit command", e);
                error.set(e);
                return null;
            }
        }

        @Override
        public byte[] getAtr() {
            return null;
        }

        @Override
        public boolean isCardPresent() throws RemoteException {
            if (!JNILoaded || !isOpenedSuccesful) {
                return false;
            }
            try {
                return isPresent();
            } catch (Exception e) {
                Log.e(TAG, "Error while getting if SD is present", e);
                return false;
            }
        }

        @Override
        public byte[] simIOExchange(int fileID, String filePath, byte[] cmd, SmartcardError error)
                throws RemoteException {
            throw new RemoteException("SIM IO error!");
        }

        @Override
        public String getSeStateChangedAction() {
            return ACTION_SD_STATE_CHANGED;
        }
    }
}
