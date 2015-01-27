/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2015 Securecom
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.securecomcode.voice.network;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.call.TrafficMonitor;
import com.securecomcode.voice.crypto.EncryptedSignalMessage;
import com.securecomcode.voice.crypto.InvalidEncryptedSignalException;
import com.securecomcode.voice.profiling.PeriodicTimer;
import com.securecomcode.voice.signaling.NetworkConnector;
import com.securecomcode.voice.signaling.SessionDescriptor;
import com.securecomcode.voice.signaling.SessionInitiationFailureException;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;


/**
 * RtpSocket wraps a {@link DatagramSocket}, allowing {@link RtpPacket}s to be sent a received.
 *
 * @author Stuart O. Anderson
 */
public class RtpSocket {
    private final byte[] buf = new byte[4096];
    private DatagramSocket socket;
    private Context context;
    private static String DELETE_SESSION = "DELETE /session/";
    private OpenPortSignalTask openPortSignalTimer;
    private volatile long lastPacketTimeStamp;
    private SessionDescriptor sessionDescriptor;
    private InetSocketAddress remoteAddress;
    private int localPort;
    private Thread openPortSignalThread;
    public static boolean isOPSThreadStarted = false;
    private long sessionId = 0;

    public RtpSocket(Context context, int localPort, InetSocketAddress remoteAddress, SessionDescriptor sessionDescriptor) throws SocketException {
        socket = new DatagramSocket(localPort);
        socket.setSoTimeout(1);
        socket.connect(new InetSocketAddress(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort()));
        this.context = context;
        this.sessionDescriptor = sessionDescriptor;
        this.remoteAddress = remoteAddress;
        this.localPort = localPort;
        openPortSignalTimer = new OpenPortSignalTask();
        Log.d("RtpSocket", "Connected to: " + remoteAddress.getAddress().getHostAddress());
    }

    public void setTimeout(int timeoutMillis) {
        try {
            socket.setSoTimeout(timeoutMillis);
        } catch (SocketException e) {
            Log.w("RtpSocket", e);
        }
    }

    private long totalSendTime = 0;
    private PeriodicTimer pt = new PeriodicTimer(10000);

    public void send(RtpPacket outPacket) throws IOException {
        long start = SystemClock.uptimeMillis();
        try {
            socket.send(new DatagramPacket(outPacket.getPacket(), outPacket.getPacketLength()));
        } catch (IOException e) {

        }
        long stop = SystemClock.uptimeMillis();
        totalSendTime += stop - start;
        if (pt.periodically()) {
            Log.d("RPS", "Send avg time:" + (totalSendTime / (double) pt.getPeriod()));
            totalSendTime = 0;
        }
    }

    public RtpPacket receive() throws IOException {
        try {
            DatagramPacket dataPack = new DatagramPacket(buf, buf.length);
            socket.setSoTimeout(1);
            socket.receive(dataPack);

            byte[] encrypted = new byte[dataPack.getLength()];
            System.arraycopy(dataPack.getData(), dataPack.getOffset(), encrypted, 0, encrypted.length);

            String prefix = new String(encrypted, 0, 4);

            //Update UI in case of reconnecting is in progress
            ApplicationPreferencesActivity.setDisplayReconnectingCallPreference(context, false);
            Intent _intent = new Intent(context, RedPhoneService.class);
            _intent.setAction(RedPhoneService.ACTION_CALL_RECONNECTING_TONE_STOP);
            context.startService(_intent);


            // check for delete message
            if (prefix.equals("sig:")) {
                sessionId = getSessionidFromEncryptedData(context, encrypted);
                Intent intent = new Intent(context, RedPhoneService.class);
                intent.setAction(RedPhoneService.ACTION_CALL_DISCONNECTED);
                intent.putExtra("session_id", sessionId);
                intent.putExtra("ExitTimeOut", "");
                context.startService(intent);
            }else{
                if(ApplicationPreferencesActivity.getIsInCall(context)) {
                    if(!isOPSThreadStarted) {
                        openPortSignalThread = new Thread(openPortSignalTimer);
                        openPortSignalThread.start();
                        isOPSThreadStarted = true;
                    }
                }
            }

            if (dataPack.getLength() > 0) {
                lastPacketTimeStamp = System.currentTimeMillis();
                TrafficMonitor.getInstance(context).updatePacketCount();
            }

            return new RtpPacket(encrypted, encrypted.length);
        } catch (SocketTimeoutException e) {
            //Do Nothing.
        } catch (IOException e) {
            if (!socket.isClosed()) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private long getSessionidFromEncryptedData(Context context, byte[] encrypted){
        long sessionid = 0;
        String plainmessage = "";

        ApplicationPreferencesActivity.setInCallStatusPreference(context, false);
        try {
            byte[] d = new byte[encrypted.length - 4];
            System.arraycopy(encrypted, 4, d, 0, d.length);
            EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context, d);
            plainmessage = new String(encryptedSignalMessage.getPlaintextNoBase64());
        } catch (InvalidEncryptedSignalException iese) {
            iese.printStackTrace();
        }

        if (!plainmessage.equals("")) {
            sessionid = new Long(plainmessage.substring(DELETE_SESSION.length(), DELETE_SESSION.length() + 18));
        }

        return sessionid;
    }

    public void close() {
        socket.close();
    }

    private class OpenPortSignalTask implements Runnable {
        @Override
        public void run() {
            while(ApplicationPreferencesActivity.getIsInCall(context)) {
                try {
                   Thread.sleep(1000);
                   long temp_time_stamp = System.currentTimeMillis() - lastPacketTimeStamp;

                   if (temp_time_stamp >= 1000 && temp_time_stamp <= 60000) {

                       if(!ApplicationPreferencesActivity.getIsInCall(context)) {
                           return;
                       }

                        ApplicationPreferencesActivity.setDisplayReconnectingCallPreference(context, true);
                        Thread.sleep(10);
                        if(temp_time_stamp >= 2000) {
                            Intent intent = new Intent(context, RedPhoneService.class);
                            intent.setAction(RedPhoneService.ACTION_CALL_RECONNECTING_TONE_START);
                            context.startService(intent);
                        }
                        int tempPort = new NetworkConnector(sessionDescriptor.sessionId,
                                sessionDescriptor.serverIP,
                                sessionDescriptor.relayPort).makeConnection();

                        DatagramSocket newSocket = new DatagramSocket(tempPort);
                        newSocket.setSoTimeout(1);
                        newSocket.connect(new InetSocketAddress(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort()));
                        synchronized (this) {
                            socket.close();
                            socket = newSocket;
                        }
                        localPort = tempPort;
                    } else if (temp_time_stamp > 60000) {
                        ApplicationPreferencesActivity.setDisplayReconnectingCallPreference(context, false);
                        Intent _intent = new Intent(context, RedPhoneService.class);
                        _intent.setAction(RedPhoneService.ACTION_CALL_RECONNECTING_TONE_STOP);
                        context.startService(_intent);

                        Intent intent = new Intent(context, RedPhoneService.class);
                        intent.setAction(RedPhoneService.ACTION_CALL_DISCONNECTED);
                        intent.putExtra("session_id", 0);
                        intent.putExtra("ExitTimeOut", "ExitTimeOut");
                        context.startService(intent);
                    }
                } catch (IOException ioe) {
                    //Do Nothing
                } catch (InterruptedException ie) {
                    //Do Nothing
                } catch (SessionInitiationFailureException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
