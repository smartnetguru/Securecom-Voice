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
package com.securecomcode.voice.call;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.securecomcode.voice.RedPhoneService;
import com.securecomcode.voice.signaling.SessionDescriptor;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;
import com.securecomcode.voice.signaling.signals.ServerSignal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SignalManager {

  private ExecutorService queue = Executors.newSingleThreadExecutor();

  private final SignalingSocket signalingSocket;
  private final SessionDescriptor sessionDescriptor;
  private final CallStateListener callStateListener;
  private final Context context;

  private volatile boolean interrupted = false;

  public SignalManager(Context context, CallStateListener callStateListener,
                       SignalingSocket signalingSocket,
                       SessionDescriptor sessionDescriptor)
  {
    this.callStateListener = callStateListener;
    this.signalingSocket   = signalingSocket;
    this.sessionDescriptor = sessionDescriptor;
    this.context           = context;
    this.queue.execute(new SignalListenerTask());
  }

  public void terminate() {
    Log.w("SignalManager", "Queuing hangup signal...");
    if(this.queue.isShutdown()){
        this.queue =  Executors.newSingleThreadExecutor();
        this.queue.execute(new SignalListenerTask());
    }
    queue.execute(new Runnable() {
      public void run() {
        Log.w("SignalManager", "Sending hangup signal...");
        signalingSocket.setHangup(sessionDescriptor.sessionId);
        signalingSocket.close();
        queue.shutdownNow();
      }
    });

    interrupted = true;
  }

  public void shutdownQueue(){
      queue.execute(new Runnable() {
          public void run() {
              signalingSocket.close();
              queue.shutdownNow();
          }
      });
      interrupted = true;
  }

  private class SignalListenerTask implements Runnable {
    public void run() {
      try {
        while (!interrupted) {
          if (signalingSocket.waitForSignal()){
              break;
          }
        }

        if (!interrupted) {
          ServerSignal signal = signalingSocket.readSignal();
          long sessionId      = sessionDescriptor.sessionId;

          if (signal.isHangup(sessionId)) {
              Intent intent = new Intent(context, RedPhoneService.class);
              intent.setAction(RedPhoneService.ACTION_CALL_DISCONNECTED);
              intent.putExtra("session_id", sessionId);
              intent.putExtra("ExitTimeOut", "");
              context.startService(intent);
          }
          else if (signal.isRinging(sessionId)) {
              callStateListener.notifyCallRinging();
              signalingSocket.sendOkResponse();
          }
          else if (signal.isBusy(sessionId))  {
              callStateListener.notifyBusy();
              signalingSocket.sendOkResponse();
          }
          else if (signal.isKeepAlive())  {
              signalingSocket.sendOkResponse();
          }

        }

        interrupted = false;
        queue.execute(this);
      } catch (SignalingException e) {
        Log.w("CallManager", e);
        callStateListener.notifyCallDisconnected();
      }
    }
  }
}
