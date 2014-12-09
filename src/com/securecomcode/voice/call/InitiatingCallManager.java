/*
 * Copyright (C) 2011 Whisper Systems
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
import com.securecomcode.voice.crypto.zrtp.ZRTPInitiatorSocket;
import com.securecomcode.voice.network.RtpSocket;
import com.securecomcode.voice.signaling.LoginFailedException;
import com.securecomcode.voice.signaling.NetworkConnector;
import com.securecomcode.voice.signaling.NoSuchUserException;
import com.securecomcode.voice.signaling.OtpCounterProvider;
import com.securecomcode.voice.signaling.ServerMessageException;
import com.securecomcode.voice.signaling.SessionInitiationFailureException;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;
import com.securecomcode.voice.signaling.signals.CallSignalStateListener;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;

import java.net.InetSocketAddress;
import java.net.SocketException;

import static com.securecomcode.voice.util.Util.isDataConnectionAvailable;

/**
 * Call Manager for the coordination of outgoing calls.  It initiates
 * signaling, negotiates ZRTP, and kicks off the call audio manager.
 *
 * @author Moxie Marlinspike
 *
 */
public class InitiatingCallManager extends CallManager implements CallSignalStateListener {

  private final String localNumber;
  private final String password;
  private final byte[] zid;
  private boolean loopbackMode;

  public InitiatingCallManager(Context context, CallStateListener callStateListener,
                               String localNumber, String password,
                               String remoteNumber, byte[] zid)
  {
    super(context, callStateListener, remoteNumber, "InitiatingCallManager Thread");
    this.localNumber    = localNumber;
    this.password       = password;
    this.zid            = zid;
    this.loopbackMode   = ApplicationPreferencesActivity.getLoopbackEnabled(context);
  }

  @Override
  public void run() {

   

    if( loopbackMode ) {
      runLoopback();
      return;
    }

    try {
      callStateListener.notifyCallConnecting();

      try {
          signalingSocket = new SignalingSocket(context, Release.RELAY_SERVER_HOST,
                  Release.SERVER_PORT, localNumber, password,
                  OtpCounterProvider.getInstance(), this);
      }catch(SignalingException se){
          callStateListener.notifyClientFailure(se.getMessage());
      }

      sessionDescriptor = signalingSocket.initiateConnection(remoteNumber);

      int localPort = new NetworkConnector(sessionDescriptor.sessionId,
                                           sessionDescriptor.getFullServerName(),
                                           sessionDescriptor.relayPort).makeConnection();

      InetSocketAddress remoteAddress = new InetSocketAddress(sessionDescriptor.getFullServerName(),
                                                              sessionDescriptor.relayPort);

      secureSocket  = new SecureRtpSocket(new RtpSocket(localPort, remoteAddress));

      zrtpSocket    = new ZRTPInitiatorSocket(context, secureSocket, zid, remoteNumber);

      processSignals();

      callStateListener.notifyWaitingForResponder();

      super.run();
    } catch (NoSuchUserException nsue) {
      Log.w("InitiatingCallManager", nsue);
      callStateListener.notifyNoSuchUser();
    } catch (ServerMessageException ife) {
      Log.w("InitiatingCallManager", ife);
      callStateListener.notifyServerMessage(ife.getMessage());
    } catch (LoginFailedException lfe) {
      Log.w("InitiatingCallManager", lfe);
      callStateListener.notifyLoginFailed();
    } catch (SignalingException se) {
      Log.w("InitiatingCallManager", se);
      callStateListener.notifyServerFailure();
    } catch (SocketException e) {
      Log.w("InitiatingCallManager", e);
      callStateListener.notifyCallDisconnected();
    } catch( RuntimeException e ) {
      Log.e( "InitiatingCallManager", "Died with unhandled exception!");
      Log.w( "InitiatingCallManager", e );
      callStateListener.notifyClientFailure("");
    } catch (SessionInitiationFailureException e) {
      Log.w("InitiatingCallManager", e);
      callStateListener.notifyServerFailure();
    }
  }

  @Override
  protected void setSecureSocketKeys(MasterSecret masterSecret) {
    secureSocket.setKeys(masterSecret.getResponderSrtpKey(), masterSecret
        .getResponderMacKey(), masterSecret.getResponderSrtpSailt(),
        masterSecret.getInitiatorSrtpKey(), masterSecret
            .getInitiatorMacKey(), masterSecret
            .getInitiatorSrtpSalt());
  }

  //***************************
  // SOA's Loopback Code, for debugging.

  private void runLoopback() {
    try {
      super.doLoopback();
    } catch( Exception e ) {
      Log.e( "InitiatingCallManager", "Died with exception!");
      Log.w( "InitiatingCallManager", e );
      callStateListener.notifyClientFailure("");
    }
  }

    @Override
    public void notifyConnectedSending() {
        callStateListener.notifyConnectedSending();
    }
}
