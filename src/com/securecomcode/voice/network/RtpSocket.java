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
import com.securecomcode.voice.call.CallManager;
import com.securecomcode.voice.call.TrafficMonitor;
import com.securecomcode.voice.crypto.CryptoEncodingException;
import com.securecomcode.voice.crypto.EncryptedSignalMessage;
import com.securecomcode.voice.crypto.InvalidEncryptedSignalException;
import com.securecomcode.voice.profiling.PeriodicTimer;
import com.securecomcode.voice.signaling.NetworkConnector;
import com.securecomcode.voice.signaling.SessionDescriptor;
import com.securecomcode.voice.signaling.SessionInitiationFailureException;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;
import com.securecomcode.voice.signaling.signals.OpenPortSignal;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;
import com.securecomcode.voice.util.Util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
    private DatagramSocket relaySocket;
    private DatagramSocket oldSocket;
    private Context context;
    private static String DELETE_SESSION = "DELETE /session/";
    private static String PEER_SESSION_STRING = "PEER /session/";
    private OpenPortSignalTask openPortSignalTimer;
    private volatile long lastPacketTimeStamp;
    private SessionDescriptor sessionDescriptor;
    private InetSocketAddress remoteAddress;
    private int localPort;
    private Thread openPortSignalThread;
    public  static boolean isOPSThreadStarted = false;
    private long sessionId = 0;
    private SignalingSocket signalingSocket;
    private boolean sendPeerRequest = false;
    private Thread peerThread = null;
    private Thread keepAliveThread = null;
    private boolean isKeepAlive = true;
    private String DELETE = "DELETE /session/";


    public RtpSocket(Context context, int localPort, InetSocketAddress remoteAddress, SessionDescriptor sessionDescriptor) throws SocketException {
        socket = new DatagramSocket(localPort);
        socket.setSoTimeout(1);
        socket.connect(new InetSocketAddress(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort()));
        relaySocket = socket;
        this.sendPeerRequest = ApplicationPreferencesActivity.getDirectConnectionPref(context);
        this.context = context;
        this.sessionDescriptor = sessionDescriptor;
        this.remoteAddress = remoteAddress;
        this.localPort = localPort;
        openPortSignalTimer = new OpenPortSignalTask();
        ApplicationPreferencesActivity.setDirectConnection(context, false);
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
            DatagramSocket tmp = socket;
            socket = relaySocket;
            tmp.close();
        }
        long stop = SystemClock.uptimeMillis();
        totalSendTime += stop - start;
        if (pt.periodically()) {
            Log.e("RPS", "Send avg time:" + (totalSendTime / (double) pt.getPeriod()));
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
                String temp = getPlainMessageFromEncryptedData(context, encrypted);
                if(temp.contains("DELETE")){
                    sessionId = getSessionidFromEncryptedData(context, encrypted);
                    Intent intent = new Intent(context, RedPhoneService.class);
                    intent.setAction(RedPhoneService.ACTION_CALL_DISCONNECTED);
                    intent.putExtra("session_id", sessionId);
                    intent.putExtra("ExitTimeOut", "");
                    context.startService(intent);
                }else if(temp.contains("PEER")){
                    if(peerThread != null || ApplicationPreferencesActivity.isDirectConnection(context)){
                        return null;
                    }

                    String stemp = getPeerDetailsFromEncryptedData(context, encrypted);
                    String[] values = stemp.split(":");

                    if (values[0] == null || values[1] == null) {
                        return null;
                    }

                    oldSocket = socket;
                    socket = doSendOpen();
                    localPort = socket.getLocalPort();

                    InetSocketAddress isockaddr = new InetSocketAddress(values[0], Integer.parseInt(values[1]));
                    peerThread = new Thread(new PeerTask(isockaddr));
                    peerThread.start();
                }
                return null;
            }else{
                if(ApplicationPreferencesActivity.getIsInCall(context)) {
                    if(!isOPSThreadStarted) {
                        openPortSignalThread = new Thread(openPortSignalTimer);
                        openPortSignalThread.start();
                        isOPSThreadStarted = true;
                    }
                }
            }

            RtpPacket inPacket = new RtpPacket(encrypted, encrypted.length);

            if (dataPack.getLength() > 0) {
                lastPacketTimeStamp = System.currentTimeMillis();
                TrafficMonitor.getInstance(context).updatePacketCount();
            }

            return inPacket;
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

        plainmessage = getPlainMessageFromEncryptedData(context, encrypted);

        if (!plainmessage.equals("")) {
            sessionid = new Long(plainmessage.substring(DELETE_SESSION.length(), DELETE_SESSION.length() + 18));
        }

        return sessionid;
    }

    private String getPeerDetailsFromEncryptedData(Context context, byte[] encrypted){
        String result = "";
        String plainmessage = "";
        plainmessage = getPlainMessageFromEncryptedData(context, encrypted);

        if(!plainmessage.equals("")){
            result = plainmessage.substring(PEER_SESSION_STRING.length()+19);
        }

        return result;
    }

    private String getPlainMessageFromEncryptedData(Context context, byte[] encrypted){
        String plainmessage = "";
        try {
            byte[] d = new byte[encrypted.length - 4];
            System.arraycopy(encrypted, 4, d, 0, d.length);
            EncryptedSignalMessage encryptedSignalMessage = new EncryptedSignalMessage(context, d, ApplicationPreferencesActivity.getSharedSecretPref(context));
            plainmessage = new String(encryptedSignalMessage.getPlaintextNoBase64());
        } catch (InvalidEncryptedSignalException iese) {
            iese.printStackTrace();
        }
        return plainmessage;
    }

    public void close() {

        String delete = DELETE+sessionDescriptor.sessionId;
        try {
            byte[] tbuf = EncryptedSignalMessage.encrypt(delete.getBytes(), ApplicationPreferencesActivity.getSharedSecretPref(context));
            tbuf = Util.concat("sig:".getBytes(), tbuf);
            DatagramPacket dp = new DatagramPacket(tbuf, tbuf.length);
            socket.send(dp);
            socket.send(dp);
        } catch (CryptoEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ApplicationPreferencesActivity.setIncomingCall(context, false);
        relaySocket.close();
        socket.close();
    }

    private class RelayKeepAliveTask implements Runnable {
        @Override
        public void run() {
            while(isKeepAlive){
                String req = "/keepalive";
                DatagramPacket packet = new DatagramPacket(req.getBytes(), req.length());
                try {
                    Thread.sleep(20000);
                    relaySocket.send(packet);

                    DatagramPacket dataPack = new DatagramPacket(buf, buf.length);
                    while (true) {
                        relaySocket.receive(dataPack);
                        if (dataPack.getLength() > 0) {
                            byte[] encrypted = new byte[dataPack.getLength()];
                            System.arraycopy(dataPack.getData(), dataPack.getOffset(), encrypted, 0, encrypted.length);

                            String prefix = new String(encrypted, 0, 4);

                            if (prefix.equals("sig:")) {
                                sessionId = getSessionidFromEncryptedData(context, encrypted);
                                Intent intent = new Intent(context, RedPhoneService.class);
                                intent.setAction(RedPhoneService.ACTION_CALL_DISCONNECTED);
                                intent.putExtra("session_id", sessionId);
                                intent.putExtra("ExitTimeOut", "");
                                context.startService(intent);
                                isKeepAlive = false;
                            }
                        }
                    }

                } catch (SocketTimeoutException ste){
                    //Do Nothing
                } catch (IOException e) {
                    //Do Nothing
                } catch (InterruptedException e) {
                    //Do Nothing
                }
            }
        }
    }

    private class PeerTask implements Runnable {
        private InetSocketAddress isockaddr;
        PeerTask(InetSocketAddress address){
            isockaddr = address;
        }
        @Override
        public void run() {
            byte[] buff = new byte[256];
            try {

                oldSocket.connect(isockaddr); 
                oldSocket.setSoTimeout(10000);
                String http_200_ok = "HTTP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n";
                String open =  "GET /open/"+sessionDescriptor.sessionId;
                String req = open;
                for(int i=0; i<20; i++) {

                    DatagramPacket packet = new DatagramPacket(req.getBytes(), req.length());
                    oldSocket.send(packet);
                    DatagramPacket tpacket = new DatagramPacket(buff, buff.length);
                    try {
                        oldSocket.receive(tpacket);
                        byte[] tmp = new byte[tpacket.getLength()];
                        System.arraycopy(tpacket.getData(), tpacket.getOffset(), tmp, 0, tmp.length);
                        String response = new String(tmp);
                        if(response.equals(open)){
                            req = http_200_ok;
                        }else if(response.equals(http_200_ok)){
                            socket = oldSocket;
                            isKeepAlive = true;
                            oldSocket.setSoTimeout(1);
                            ApplicationPreferencesActivity.setDirectConnection(context, true);
                            keepAliveThread = new Thread(new RelayKeepAliveTask());
                            break;
                        }

                        if (!isockaddr.equals(tpacket.getSocketAddress())) {
                            oldSocket.connect(tpacket.getSocketAddress());
                            continue;
                        }
                    } catch (IOException e) {

                    }
                }

            }catch (IOException se){

            }

            peerThread = null;
        }
    }

    private class OpenPortSignalTask implements Runnable {
        @Override
        public void run() {
            while(ApplicationPreferencesActivity.getIsInCall(context)) {
                try {

                   Thread.sleep(1000);

                    if(sendPeerRequest && ApplicationPreferencesActivity.getIncomingCall(context)) {
                        if(ApplicationPreferencesActivity.getIncomingCall(context)) {
                            signalingSocket = new SignalingSocket(context);
                            signalingSocket.sendPeer(sessionDescriptor.sessionId);
                            sendPeerRequest = false;
                        }
                    }

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
                        relaySocket.close();
                        if(socket.isClosed()) {
                            socket.close();
                        }
                        relaySocket = doSendOpen();
                        localPort = relaySocket.getLocalPort();
                        socket = relaySocket;
                        if(ApplicationPreferencesActivity.getDirectConnectionPref(context)) {
                            sendPeerRequest = (temp_time_stamp < 2000);
                        }
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
                        isKeepAlive = false;
                       ApplicationPreferencesActivity.setDirectConnection(context, false);
                    }
                } catch (InterruptedException ie) {
                    //Do Nothing
                } catch (SignalingException e){
                    //Do Nothing
                }
            }
        }
    }

    private DatagramSocket doSendOpen() {
        int tempPort;
        DatagramSocket newSocket = null;
        try {
            tempPort = new NetworkConnector(sessionDescriptor.sessionId,
                    sessionDescriptor.serverIP,
                    sessionDescriptor.relayPort).makeConnection();

            newSocket = new DatagramSocket(tempPort);
            newSocket.setSoTimeout(1);
            newSocket.connect(new InetSocketAddress(remoteAddress.getAddress().getHostAddress(), remoteAddress.getPort()));
            isKeepAlive = false;
            ApplicationPreferencesActivity.setDirectConnection(context, false);
        }catch(SessionInitiationFailureException e){
            //Do Nothing
        }catch(SocketException e){
            //Do Nothing
        }

        return newSocket;
    }
}
