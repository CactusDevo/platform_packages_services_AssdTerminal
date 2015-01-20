package org.simalliance.openmobileapi.assdterminal;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.MissingResourceException;

import org.simalliance.openmobileapi.service.ITerminalService;
import org.simalliance.openmobileapi.service.OpenLogicalChannelResponse;
import org.simalliance.openmobileapi.service.SmartcardError;
import org.simalliance.openmobileapi.service.CardException;


/**
 * Created by sevilser on 18/12/14.
 */
public final class AssdTerminal extends Service {

    private static final String TAG = "AssdTerminal";

    public static final String SD_TERMINAL = "SD";

    private final ITerminalService.Stub mTerminalBinder = new TerminalServiceImplementation();

    private static boolean JNILoaded = false;

    private boolean isOpenedSuccesful;
    @Override
    public IBinder onBind(Intent intent) {
        return mTerminalBinder;
    }

    @Override
    public void onCreate() {
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
        super.onDestroy();
    }


    static {
        try {
            Runtime.getRuntime().loadLibrary("assd");
            JNILoaded = true;
        } catch (Throwable t) {
        }
    }

    private native void close() throws Exception;

    private native boolean open() throws Exception;

    private native boolean isPresent() throws Exception;

    private native byte[] transmit(byte[] command) throws Exception;

    /**
     * Creates a formatted exception message.
     *
     * @param commandName the name of the command. <code>null</code> if not
     *            specified.
     * @param sw the response status word.
     * @return a formatted exception message.
     */
    static String createMessage(String commandName, int sw) {
        StringBuilder message = new StringBuilder();
        if (commandName != null) {
            message.append(commandName).append(" ");
        }
        message.append("SW1/2 error: ");
        message.append(Integer.toHexString(sw | 0x10000).substring(1));
        return message.toString();
    }

    /**
     * Creates a formatted exception message.
     *
     * @param commandName the name of the command. <code>null</code> if not
     *            specified.
     * @param message the message to be formatted.
     * @return a formatted exception message.
     */
    static String createMessage(String commandName, String message) {
        if (commandName == null) {
            return message;
        }
        return commandName + " " + message;
    }

    /**
     * Returns a concatenated response.
     *
     * @param r1 the first part of the response.
     * @param r2 the second part of the response.
     * @param length the number of bytes of the second part to be appended.
     * @return a concatenated response.
     */
    static byte[] appendResponse(byte[] r1, byte[] r2, int length) {
        byte[] rsp = new byte[r1.length + length];
        System.arraycopy(r1, 0, rsp, 0, r1.length);
        System.arraycopy(r2, 0, rsp, r1.length, length);
        return rsp;
    }

    public static String getType() {
        return SD_TERMINAL;
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
        public OpenLogicalChannelResponse internalOpenLogicalChannel(byte[] aid, SmartcardError error) throws RemoteException {
            if (!JNILoaded || !isOpenedSuccesful) {
                error.setError(CardException.class, "JNI failed or open SE failed");
                return null;
            }
            byte[] manageChannelCommand = new byte[] {
                    0x00, 0x70, 0x00, 0x00, 0x01
            };
            byte[] rsp = new byte[0];
            try {
                rsp = transmit(manageChannelCommand, 3, 0x9000, 0xFFFF, "MANAGE CHANNEL", error);
            } catch (CardException e) {
                Log.e(TAG, "Error while transmitting Manage Channel", e);
                error.setError(CardException.class, e.getMessage());
                return null;
            }
            if (rsp.length != 3) {
                error.setError(MissingResourceException.class, "unsupported MANAGE CHANNEL response data");
                return null;
            }
            int channelNumber = rsp[0] & 0xFF;
            if (channelNumber == 0 || channelNumber > 19) {
                error.setError(MissingResourceException.class, "invalid logical channel number returned");
                return null;
            }

            return new OpenLogicalChannelResponse(channelNumber, null);
        }

        @Override
        public void internalCloseLogicalChannel(int channelNumber, SmartcardError error)
                throws RemoteException {
            if (!JNILoaded || !isOpenedSuccesful) {
                error.setError(CardException.class, "JNI failed or open SE failed");
                return;
            }
            if (channelNumber > 0) {
                byte cla = (byte) channelNumber;
                if (channelNumber > 3) {
                    cla |= 0x40;
                }
                byte[] manageChannelClose = new byte[] {
                        cla, 0x70, (byte) 0x80, (byte) channelNumber
                };
                try {
                    transmit(manageChannelClose, 2, 0x9000, 0xFFFF, "MANAGE CHANNEL", error);
                } catch (CardException e) {
                    Log.e(TAG, "Error while Manage Channel", e);
                    error.setError(CardException.class, "Error while Manage Channel");
                }
            }
        }

        @Override
        public byte[] internalTransmit(byte[] command, SmartcardError error) throws RemoteException {
            if (!JNILoaded || !isOpenedSuccesful) {
                error.setError(CardException.class, "JNI failed or open SE failed");
                return new byte[0];
            }
            try {
                byte[] response = AssdTerminal.this.transmit(command);
                if (response == null) {
                    throw new CardException("transmit failed");
                }
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Error while transmit command", e);
                error.setError(CardException.class, "transmit failed");
                return new byte[0];
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
                Log.e(TAG, "Error while getting if sd is present", e);
                return false;
            }
        }

        /**
         * Transmits the specified command and returns the response. Optionally
         * checks the response length and the response status word. The status word
         * check is implemented as follows (sw = status word of the response):
         * <p>
         * if ((sw & swMask) != (swExpected & swMask)) throw new CardException();
         * </p>
         *
         * @param cmd the command APDU to be transmitted.
         * @param minRspLength the minimum length of received response to be
         *            checked.
         * @param swExpected the response status word to be checked.
         * @param swMask the mask to be used for response status word comparison.
         * @param commandName the name of the smart card command for logging
         *            purposes. May be <code>null</code>.
         * @return the response received.
         * @throws CardException if the transmit operation or the minimum response
         *             length check or the status word check failed.
         */
        public synchronized byte[] transmit(
                byte[] cmd,
                int minRspLength,
                int swExpected,
                int swMask,
                String commandName,
                SmartcardError error)
                throws CardException {
            byte[] rsp = null;
            try {
                rsp = protocolTransmit(cmd, error);
            } catch (Exception e) {
                if (commandName == null) {
                    throw new CardException(e.getMessage());
                } else {
                    throw new CardException(
                            createMessage(commandName, "transmit failed"), e);
                }
            }
            if (minRspLength > 0 && (rsp == null || rsp.length < minRspLength)) {
                throw new CardException(
                        createMessage(commandName, "response too small"));
            }
            if (swMask != 0) {
                if (rsp == null || rsp.length < 2) {
                    throw new CardException(
                            createMessage(commandName, "SW1/2 not available"));
                }
                int sw1 = rsp[rsp.length - 2] & 0xFF;
                int sw2 = rsp[rsp.length - 1] & 0xFF;
                int sw = (sw1 << 8) | sw2;
                if ((sw & swMask) != (swExpected & swMask)) {
                    throw new CardException(createMessage(commandName, sw));
                }
            }
            return rsp;
        }

        /**
         * Protocol specific implementation of the transmit operation. This method
         * is synchronized in order to handle GET RESPONSE and command repetition
         * without interruption by other commands.
         *
         * @param cmd the command to be transmitted.
         * @return the response received.
         * @throws CardException if the transmit operation failed.
         */
        protected synchronized byte[] protocolTransmit(byte[] cmd, SmartcardError error)
                throws CardException {
            byte[] command = cmd;
            byte[] rsp = null;
            try {
                rsp = internalTransmit(command, error);
            } catch (RemoteException e) {
                Log.e(TAG, "Error while internal transmit", e);
                throw new CardException(error.getMessage());
            }

            if (rsp.length >= 2) {
                int sw1 = rsp[rsp.length - 2] & 0xFF;
                int sw2 = rsp[rsp.length - 1] & 0xFF;
                if (sw1 == 0x6C) {
                    command[cmd.length - 1] = rsp[rsp.length - 1];
                    try {
                        rsp = internalTransmit(command, error);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Error while internal transmit", e);
                        throw new CardException(error.getMessage());
                    }
                } else if (sw1 == 0x61) {
                    byte[] getResponseCmd = new byte[] {
                            command[0], (byte) 0xC0, 0x00, 0x00, 0x00
                    };
                    byte[] response = new byte[rsp.length - 2];
                    System.arraycopy(rsp, 0, response, 0, rsp.length - 2);
                    while (true) {
                        getResponseCmd[4] = rsp[rsp.length - 1];
                        try {
                            rsp = internalTransmit(getResponseCmd,error);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Error while internal transmit", e);
                            throw new CardException(error.getMessage());
                        }
                        if (rsp.length >= 2 && rsp[rsp.length - 2] == 0x61) {
                            response = appendResponse(
                                    response, rsp, rsp.length - 2);
                        } else {
                            response = appendResponse(response, rsp, rsp.length);
                            break;
                        }
                    }
                    rsp = response;
                }
            }
            return rsp;
        }

        @Override
        public byte[] simIOExchange(int fileID, String filePath, byte[] cmd, org.simalliance.openmobileapi.service.SmartcardError error)
                throws RemoteException {
            throw new RemoteException("SIM IO error!");
        }
    }
}
