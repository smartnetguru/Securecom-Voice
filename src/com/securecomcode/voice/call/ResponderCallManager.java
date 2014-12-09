/*
 * Copyright (C) 2011 Whisper Systems
 * Copyright (C) 2014 Securecom
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

package com.securecomcode.voice.call;

import android.content.Context;
import android.util.Log;

import com.securecomcode.voice.Release;
import com.securecomcode.voice.crypto.SecureRtpSocket;
import com.securecomcode.voice.crypto.zrtp.MasterSecret;
import com.securecomcode.voice.crypto.zrtp.ZRTPResponderSocket;
import com.securecomcode.voice.network.RtpSocket;
import com.securecomcode.voice.signaling.LoginFailedException;
import com.securecomcode.voice.signaling.NetworkConnector;
import com.securecomcode.voice.signaling.OtpCounterProvider;
import com.securecomcode.voice.signaling.SessionDescriptor;
import com.securecomcode.voice.signaling.SessionInitiationFailureException;
import com.securecomcode.voice.signaling.SessionStaleException;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;

import java.net.InetSocketAddress;
import java.net.SocketException;

/**
 * CallManager responsible for coordinating incoming calls.
 *
 * @author Moxie Marlinspike
 *
 */
public class ResponderCallManager extends CallManager {

  private final String localNumber;
  private final String password;
  private final byte[] zid;

  private int answer = 0;

  public ResponderCallManager(Context context, CallStateListener callStateListener,
                              String remoteNumber, String localNumber,
                              String password, SessionDescriptor sessionDescriptor,
                              byte[] zid)
  {
    super(context, callStateListener, remoteNumber, "ResponderCallManager Thread");
    this.localNumber       = localNumber;
    this.password          = password;
    this.sessionDescriptor = sessionDescriptor;
    this.zid               = zid;
  }

  @Override
  public void run() {
    try {
      signalingSocket = new SignalingSocket(context,
                                            Release.RELAY_SERVER_HOST,
                                            Release.SERVER_PORT,
                                            localNumber, password,
                                            OtpCounterProvider.getInstance(), null);

      signalingSocket.setRinging(sessionDescriptor.sessionId);
      callStateListener.notifyCallFresh();
      processSignals();
      if (!waitForAnswer()) {
        return;
      }

      int localPort = new NetworkConnector(sessionDescriptor.sessionId,
                                           sessionDescriptor.getFullServerName(),
                                           sessionDescriptor.relayPort).makeConnection();
      InetSocketAddress remoteAddress = new InetSocketAddress(sessionDescriptor.getFullServerName(),
                                                              sessionDescriptor.relayPort);

      secureSocket  = new SecureRtpSocket(new RtpSocket(localPort, remoteAddress));

      zrtpSocket    = new ZRTPResponderSocket(context, secureSocket, zid, remoteNumber, sessionDescriptor.version <= 0);

      callStateListener.notifyConnectingtoInitiator();

      super.run();
    } catch (SignalingException se) {
      Log.w( "ResponderCallManager", se );
      callStateListener.notifyServerFailure();
    } catch (SessionInitiationFailureException e) {
      Log.w("ResponderCallManager", e);
      callStateListener.notifyServerFailure();
    } catch (SessionStaleException e) {
      Log.w("ResponderCallManager", e);
      callStateListener.notifyCallStale();
    } catch (LoginFailedException lfe) {
      Log.w("ResponderCallManager", lfe);
      callStateListener.notifyLoginFailed();
    } catch (SocketException e) {
      Log.w("ResponderCallManager", e);
      callStateListener.notifyCallDisconnected();
    } catch( RuntimeException e ) {
      Log.e( "ResponderCallManager", "Died unhandled with exception!");
      Log.w( "ResponderCallManager", e );
      callStateListener.notifyClientFailure("");
    }
  }

  public synchronized void answer(boolean answer) {
    this.answer = (answer ? 1 : 2);
    notifyAll();
  }

  private synchronized boolean waitForAnswer() {
    try {
      while (answer == 0)
        wait();
    } catch (InterruptedException ie) {
      throw new IllegalArgumentException(ie);
    }

    return this.answer == 1;
  }

  @Override
  public void terminate() {
    synchronized (this) {
      if (answer == 0) {
        answer(false);
      }
    }

    super.terminate();
  }

  @Override
  protected void setSecureSocketKeys(MasterSecret masterSecret) {
    secureSocket.setKeys(masterSecret.getInitiatorSrtpKey(), masterSecret.getInitiatorMacKey(),
                         masterSecret.getInitiatorSrtpSalt(), masterSecret.getResponderSrtpKey(),
                         masterSecret.getResponderMacKey(), masterSecret.getResponderSrtpSailt());
  }

}
