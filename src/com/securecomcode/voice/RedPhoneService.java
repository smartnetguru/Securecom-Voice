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

package com.securecomcode.voice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.securecomcode.voice.audio.IncomingRinger;
import com.securecomcode.voice.audio.OutgoingRinger;
import com.securecomcode.voice.call.CallManager;
import com.securecomcode.voice.call.CallStateListener;
import com.securecomcode.voice.call.InitiatingCallManager;
import com.securecomcode.voice.call.LockManager;
import com.securecomcode.voice.call.ResponderCallManager;
import com.securecomcode.voice.codec.CodecSetupException;
import com.securecomcode.voice.contacts.PersonInfo;
import com.securecomcode.voice.crypto.zrtp.SASInfo;
import com.securecomcode.voice.gcm.GCMRegistrarHelper;
import com.securecomcode.voice.monitor.CallDataImpl;
import com.securecomcode.voice.network.RtpSocket;
import com.securecomcode.voice.pstn.CallStateView;
import com.securecomcode.voice.pstn.IncomingPstnCallListener;
import com.securecomcode.voice.signaling.OtpCounterProvider;
import com.securecomcode.voice.signaling.SessionDescriptor;
import com.securecomcode.voice.signaling.SignalingException;
import com.securecomcode.voice.signaling.SignalingSocket;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;
import com.securecomcode.voice.ui.CallCard;
import com.securecomcode.voice.ui.CallQualityDialog;
import com.securecomcode.voice.ui.NotificationBarManager;
import com.securecomcode.voice.util.Base64;
import com.securecomcode.voice.util.CallLogger;
import com.securecomcode.voice.util.UncaughtExceptionHandlerManager;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;

import static com.securecomcode.voice.util.Util.isDataConnectionAvailable;

/**
 * The major entry point for all of the heavy lifting associated with
 * setting up, tearing down, or managing calls.  The service spins up
 * either from a broadcast listener that has detected an incoming call,
 * or from a UI element that wants to initiate an outgoing call.
 *
 * @author Moxie Marlinspike
 *
 */
public class RedPhoneService extends Service implements CallStateListener, CallStateView {
  public static final String ACTION_INCOMING_CALL = "com.securecomcode.voice.RedPhoneService.INCOMING_CALL";
  public static final String ACTION_OUTGOING_CALL = "com.securecomcode.voice.RedPhoneService.OUTGOING_CALL";
  public static final String ACTION_ANSWER_CALL   = "com.securecomcode.voice.RedPhoneService.ANSWER_CALL";
  public static final String ACTION_DENY_CALL     = "com.securecomcode.voice.RedPhoneService.DENYU_CALL";
  public static final String ACTION_HANGUP_CALL   = "com.securecomcode.voice.RedPhoneService.HANGUP";
  public static final String ACTION_SET_MUTE      = "com.securecomcode.voice.RedPhoneService.SET_MUTE";
  public static final String ACTION_CONFIRM_SAS   = "com.securecomcode.voice.RedPhoneService.CONFIRM_SAS";
  public static final String ACTION_CALL_DISCONNECTED   = "com.securecomcode.voice.RedPhoneService.CALL_DISCONNECTED";
  public static final String ACTION_CALL_RECONNECTING_TONE_START   = "com.securecomcode.voice.RedPhoneService.CALL_RECONNECTING_TONE_START";
  public static final String ACTION_CALL_RECONNECTING_TONE_STOP   = "com.securecomcode.voice.RedPhoneService.CALL_RECONNECTING_TONE_STOP";

  private static final String TAG = RedPhoneService.class.getName();

  private final List<Message> bufferedEvents = new LinkedList<Message>();
  private final IBinder binder               = new RedPhoneServiceBinder();
  private final Handler serviceHandler       = new Handler();

  private OutgoingRinger outgoingRinger;
  private IncomingRinger incomingRinger;

  private int state;
  private byte[] zid;
  private String localNumber;
  private String remoteNumber;
  private String password;
  private CallManager currentCallManager;
  private LockManager lockManager;
  private UncaughtExceptionHandlerManager uncaughtExceptionHandlerManager;

  private Handler handler;
  private IncomingPstnCallListener pstnCallListener;

  @Override
  public void onCreate() {
    super.onCreate();

    if (Release.DEBUG) Log.w("RedPhoneService", "Service onCreate() called...");

    initializeResources();
    initializeApplicationContext();
    initializeRingers();
    initializePstnCallListener();
    registerUncaughtExceptionHandler();

    CallDataImpl.clearCache(this);
  }

  @Override
  public void onStart(Intent intent, int startId) {
    if (Release.DEBUG) Log.w("RedPhoneService", "Service onStart() called...");
    if (intent == null) return;
    new Thread(new IntentRunnable(intent)).start();

    GCMRegistrarHelper.registerClient(this, false);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    unregisterReceiver(pstnCallListener);
    NotificationBarManager.setCallEnded(this);
    uncaughtExceptionHandlerManager.unregister();
  }

  private synchronized void onIntentReceived(Intent intent) {
    Log.w("RedPhoneService", "Received Intent: " + intent.getAction());

    if      (intent.getAction().equals(ACTION_INCOMING_CALL) && isBusy()) handleBusyCall(intent);
    else if (intent.getAction().equals(ACTION_INCOMING_CALL))             handleIncomingCall(intent);
    else if (intent.getAction().equals(ACTION_OUTGOING_CALL) && isIdle()) handleOutgoingCall(intent);
    else if (intent.getAction().equals(ACTION_ANSWER_CALL))               handleAnswerCall(intent);
    else if (intent.getAction().equals(ACTION_DENY_CALL))                 handleDenyCall(intent);
    else if (intent.getAction().equals(ACTION_HANGUP_CALL))               handleHangupCall(intent);
    else if (intent.getAction().equals(ACTION_CALL_DISCONNECTED))         handleCallDisconnected(intent);
    else if (intent.getAction().equals(ACTION_SET_MUTE))                  handleSetMute(intent);
    else if (intent.getAction().equals(ACTION_CONFIRM_SAS))               handleConfirmSas(intent);
    else if (intent.getAction().equals(ACTION_CALL_RECONNECTING_TONE_START))         handleReconnectingToneStart(intent);
    else if (intent.getAction().equals(ACTION_CALL_RECONNECTING_TONE_STOP))         handleReconnectingToneStop(intent);
  }

  ///// Initializers

  private void initializeRingers() {
    this.outgoingRinger = new OutgoingRinger(this);
    this.incomingRinger = new IncomingRinger(this);
  }

  private void initializePstnCallListener() {
    pstnCallListener = new IncomingPstnCallListener(this);
    registerReceiver(pstnCallListener, new IntentFilter("android.intent.action.PHONE_STATE"));
  }

  private void initializeApplicationContext() {
    ApplicationContext context = ApplicationContext.getInstance();
    context.setContext(this);
    context.setCallStateListener(this);
  }

  private void initializeResources() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    this.state            = RedPhone.STATE_IDLE;
    this.zid              = getZID();
    this.localNumber      = preferences.getString(Constants.NUMBER_PREFERENCE, "NO_SAVED_NUMBER!");
    this.password         = preferences.getString(Constants.PASSWORD_PREFERENCE, "NO_SAVED_PASSWORD!");

    this.lockManager      = new LockManager(this);
  }

  private void registerUncaughtExceptionHandler() {
    uncaughtExceptionHandlerManager = new UncaughtExceptionHandlerManager();
    uncaughtExceptionHandlerManager.registerHandler(new ProximityLockRelease(lockManager));
  }

  /// Intent Handlers

  private void handleIncomingCall(Intent intent) {
    SessionDescriptor session = (SessionDescriptor)intent.getParcelableExtra(Constants.SESSION);
    remoteNumber              = extractRemoteNumber(intent);
    state                     = RedPhone.STATE_RINGING;

    ApplicationPreferencesActivity.setCallScreenEndButtonPressed(getApplicationContext(), false);
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    this.currentCallManager = new ResponderCallManager(this, this, remoteNumber, localNumber,
                                                       password, session, zid);
    this.currentCallManager.start();
  }

  private void handleOutgoingCall(Intent intent) {

    ApplicationPreferencesActivity.setCallScreenEndButtonPressed(getApplicationContext(), false);
    if(!isDataConnectionAvailable(getApplicationContext())){
        notifyClientFailure(getResources().getString(R.string.Warning_turn_on_packet_data_or_use_wi_fi_to_complete_this_action));
        return;
    }

    remoteNumber = extractRemoteNumber(intent);

    if (remoteNumber == null || remoteNumber.length() == 0)
      return;

    sendMessage(RedPhone.HANDLE_OUTGOING_CALL, remoteNumber);

    state = RedPhone.STATE_DIALING_CONNECTING;
    lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);
    this.currentCallManager = new InitiatingCallManager(this, this, localNumber, password,
                                                        remoteNumber, zid);
    this.currentCallManager.start();

    NotificationBarManager.setCallInProgress(this);

    CallLogger.logOutgoingCall(this, remoteNumber);
  }

  private void handleBusyCall(Intent intent) {
    SessionDescriptor session = (SessionDescriptor)intent.getParcelableExtra(Constants.SESSION);

    if (currentCallManager != null && session.equals(currentCallManager.getSessionDescriptor())) {
      Log.w("RedPhoneService", "Duplicate incoming call signal, ignoring...");
      return;
    }

    handleMissedCall(extractRemoteNumber(intent));

    try {
      SignalingSocket signalingSocket = new SignalingSocket(this, Release.MASTER_SERVER_HOST,
                                                            Release.SERVER_PORT,
                                                            localNumber, password,
                                                            OtpCounterProvider.getInstance(), null);

      signalingSocket.setBusy(session.sessionId);
      signalingSocket.close();
    } catch (SignalingException e) {
      Log.w("RedPhoneService", e);
    }
  }

  private void handleMissedCall(String remoteNumber) {
    CallLogger.logMissedCall(this, remoteNumber, System.currentTimeMillis());
    NotificationBarManager.notifyMissedCall(this, remoteNumber);
  }

  private void handleAnswerCall(Intent intent) {
    state = RedPhone.STATE_ANSWERING;
    incomingRinger.stop();
    CallLogger.logIncomingCall(this, remoteNumber);
    if (currentCallManager != null) {
      ((ResponderCallManager)this.currentCallManager).answer(true);
    }
  }

  private void handleDenyCall(Intent intent) {
    state = RedPhone.STATE_IDLE;
    incomingRinger.stop();
    CallLogger.logMissedCall(this, remoteNumber, System.currentTimeMillis());
    if(currentCallManager != null) {
      ((ResponderCallManager)this.currentCallManager).answer(false);
    }
    this.terminate();
  }

  private void handleHangupCall(Intent intent) {
    ApplicationPreferencesActivity.setCallScreenEndButtonPressed(getApplicationContext(), true);
    this.terminate();
  }

  private void handleCallDisconnected(Intent intent){
      try {
          if (currentCallManager != null && currentCallManager.getSessionDescriptor().sessionId == intent.getExtras().getLong("session_id")) {
              currentCallManager.resetSignalManager();
              notifyCallDisconnected();
          } else if (intent != null && intent.getExtras().getString("ExitTimeOut").equalsIgnoreCase("ExitTimeOut")) {
              notifyCallDisconnected();
          }
      }catch(Exception e){
          //Do Nothing
      }

  }

  private void handleSetMute(Intent intent) {
    if(currentCallManager != null) {
      currentCallManager.setMute(intent.getBooleanExtra(Constants.MUTE_VALUE, false));
    }
  }

  private void handleConfirmSas(Intent intent) {
    if (currentCallManager != null)
      currentCallManager.setSasVerified();
  }

  private void handleReconnectingToneStart(Intent intent){
      notifyCallReconnectingStart();
  }

  private void handleReconnectingToneStop(Intent intent){
      notifyCallReconnectingStop();
  }
  /// Helper Methods

  private boolean isBusy() {
    TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
    return ((currentCallManager != null && state != RedPhone.STATE_IDLE) ||
             telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE);
  }

  private boolean isIdle() {
    return state == RedPhone.STATE_IDLE;
  }

  private void shutdownAudio() {
    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
    am.setMode(AudioManager.MODE_NORMAL);
  }

  public int getState() {
    return state;
  }

  public SASInfo getCurrentCallSAS() {
    if (currentCallManager != null)
      return currentCallManager.getSasInfo();
    else
      return null;
  }

  public PersonInfo getRemotePersonInfo() {
    return PersonInfo.getInstance(this, remoteNumber);
  }

  private byte[] getZID() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    if (preferences.contains("ZID")) {
      try {
        return Base64.decode(preferences.getString("ZID", null));
      } catch (IOException e) {
        return setZID();
      }
    } else {
      return setZID();
    }
  }

  private byte[] setZID() {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

    try {
      byte[] zid        = new byte[12];
      SecureRandom.getInstance("SHA1PRNG").nextBytes(zid);
      String encodedZid = Base64.encodeBytes(zid);

      preferences.edit().putString("ZID", encodedZid).commit();

      return zid;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private String extractRemoteNumber(Intent i) {
    String number =  i.getStringExtra(Constants.REMOTE_NUMBER);

    if (number == null)
      number = i.getData().getSchemeSpecificPart();

    if (number.endsWith("*")) return number.substring(0, number.length()-1);
    else                      return number;
  }

  private void startCallCardActivity() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, RedPhone.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(activityIntent);
  }

  private synchronized void terminate() {
    ApplicationPreferencesActivity.setInCallStatusPreference(getApplicationContext(), false);
    RtpSocket.isOPSThreadStarted = false;
    lockManager.updatePhoneState(LockManager.PhoneState.PROCESSING);
    NotificationBarManager.setCallEnded(this);

    incomingRinger.stop();
    outgoingRinger.stop();

    if (currentCallManager != null) {
      currentCallManager.terminate();
      currentCallManager = null;
    }

    shutdownAudio();

    state = RedPhone.STATE_IDLE;
    lockManager.updatePhoneState(LockManager.PhoneState.IDLE);

    // XXX moxie@thoughtcrime.org -- Do we still need to stop the Service?
//    Log.d("RedPhoneService", "STOP SELF" );
    this.stopSelf();
  }

  public void maybeStartQualityMetricsActivity() {
    if(currentCallManager.getSessionDescriptor() == null
      || !currentCallManager.callConnected()
      || (!ApplicationPreferencesActivity.getDisplayDialogPreference(this)
      && ApplicationPreferencesActivity.wasUserNotifedOfCallQaulitySettings(this))) {
      return;
    }

    SessionDescriptor sessionDescriptor = currentCallManager.getSessionDescriptor();
    Intent callQualityDialogIntent = new Intent(this,CallQualityDialog.class);
    callQualityDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    callQualityDialogIntent.putExtra("callId",sessionDescriptor.sessionId);
    startActivity(callQualityDialogIntent);

    Notification notification = new NotificationCompat.Builder(this)
      .setAutoCancel(true)
      .setContentTitle(getResources().getText(R.string.CallQualityDialog__redphone))
      .setContentText(getResources().getText(R.string.CallQualityDialog__provide_call_quality_feedback))
      .setContentIntent(PendingIntent.getActivity(this, 0, callQualityDialogIntent, PendingIntent.FLAG_UPDATE_CURRENT))
      .setSmallIcon(R.drawable.registration_notification)
      .setDefaults(Notification.DEFAULT_LIGHTS)
      .setTicker(getResources().getText(R.string.CallQualityDialog__provide_call_quality_feedback))
      .build();

    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    nm.notify(CallQualityDialog.CALL_QUALITY_NOTIFICATION_ID, notification);
  }

  public void setCallStateHandler(Handler handler) {
    this.handler = handler;

    if (handler != null) {
      for (Message message : bufferedEvents) {
        handler.sendMessage(message);
      }

      bufferedEvents.clear();
    }
  }

  ///////// CallStateListener Implementation

  public void notifyCallStale() {
    Log.w("RedPhoneService", "Got a stale call, probably an old SMS...");
    handleMissedCall(remoteNumber);
    this.terminate();
  }

  public void notifyCallFresh() {
    Log.w("RedPhoneService", "Good call, time to ring and display call card...");
    sendMessage(RedPhone.HANDLE_INCOMING_CALL, remoteNumber);

    lockManager.updatePhoneState(LockManager.PhoneState.INTERACTIVE);

    startCallCardActivity();
    incomingRinger.start();

    NotificationBarManager.setCallInProgress(this);
  }

  public void notifyBusy() {
    Log.w("RedPhoneService", "Got busy signal from responder!");
    sendMessage(RedPhone.HANDLE_CALL_BUSY, null);
    outgoingRinger.playBusy();
    currentCallManager.resetSignalManager();
    serviceHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        RedPhoneService.this.terminate();
      }
    }, RedPhone.BUSY_SIGNAL_DELAY_FINISH);
  }

  public void notifyCallRinging() {
    outgoingRinger.playRing();
    sendMessage(RedPhone.HANDLE_CALL_RINGING, null);
  }

  public void notifyCallConnected(SASInfo sas) {
    outgoingRinger.playComplete();
    lockManager.updatePhoneState(LockManager.PhoneState.IN_CALL);
    state = RedPhone.STATE_CONNECTED;
    synchronized( this ) {
      sendMessage(RedPhone.HANDLE_CALL_CONNECTED, sas);
      try {
        wait();
      } catch (InterruptedException e) {
        throw new AssertionError( "Wait interrupted in RedPhoneService" );
      }
    }
  }
  public void notifyCallConnectionUIUpdateComplete() {
    synchronized( this ) {
      this.notify();
    }
  }
  public void notifyDebugInfo(String info) {
    sendMessage(RedPhone.HANDLE_DEBUG_INFO, info);
  }

  public void notifyConnectingtoInitiator() {
    sendMessage(RedPhone.HANDLE_CONNECTING_TO_INITIATOR, null);
  }

  public void notifyCallDisconnected() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    sendMessage(RedPhone.HANDLE_CALL_DISCONNECTED, null);
    this.terminate();
  }

  public void notifyHandshakeFailed() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_HANDSHAKE_FAILED, null);
    this.terminate();
  }

  public void notifyRecipientUnavailable() {
    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_RECIPIENT_UNAVAILABLE, null);
    this.terminate();
  }

  public void notifyPerformingHandshake() {
    outgoingRinger.playHandshake();
    sendMessage(RedPhone.HANDLE_PERFORMING_HANDSHAKE, null);
  }

  public void notifyServerFailure() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_SERVER_FAILURE, null);
    this.terminate();
  }

  public void notifyClientFailure(String message) {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_CLIENT_FAILURE, message);
    this.terminate();
  }

  public void notifyLoginFailed() {
    if (state == RedPhone.STATE_RINGING)
      handleMissedCall(remoteNumber);

    state = RedPhone.STATE_IDLE;
    outgoingRinger.playFailure();
    sendMessage(RedPhone.HANDLE_LOGIN_FAILED, null);
    this.terminate();
  }

  public void notifyNoSuchUser() {
    sendMessage(RedPhone.HANDLE_NO_SUCH_USER, remoteNumber);
    this.terminate();
  }

  public void notifyServerMessage(String message) {
    sendMessage(RedPhone.HANDLE_SERVER_MESSAGE, message);
    this.terminate();
  }

  public void notifyCodecInitFailed(CodecSetupException e) {
    sendMessage(RedPhone.HANDLE_CODEC_INIT_FAILED, e);
    this.terminate();
  }

  public void notifyClientError(String msg) {
    sendMessage(RedPhone.HANDLE_CLIENT_FAILURE,msg);
    this.terminate();
  }

  public void notifyClientError(int messageId) {
    notifyClientError(getString(messageId));
  }

  public void notifyCallConnecting() {
    outgoingRinger.playSonar();
  }

    public void notifyCallReconnectingStart() {
        if(outgoingRinger != null) {
            outgoingRinger.playReconnecting();
        }
    }

    public void notifyCallReconnectingStop() {
        if(outgoingRinger != null) {
            outgoingRinger.stop();
        }
    }

    @Override
    public void notifyConnectedSending() {
        sendMessage(RedPhone.HANDLE_STATE_CONNECTED_SENDING, remoteNumber);
    }

    public void notifyWaitingForResponder() {
        sendMessage(RedPhone.HANDLE_STATE_CONNECTED_WAITING, remoteNumber);
    }

  private void sendMessage(int code, Object extra) {
    Message message = Message.obtain();
    message.what    = code;
    message.obj     = extra;

    if (handler != null) handler.sendMessage(message);
    else    			       bufferedEvents.add(message);
  }

  private class IntentRunnable implements Runnable {
    private final Intent intent;

    public IntentRunnable(Intent intent) {
      this.intent = intent;
    }

    public void run() {
      onIntentReceived(intent);
    }
  }

  public class RedPhoneServiceBinder extends Binder {
    public RedPhoneService getService() {
      return RedPhoneService.this;
    }
  }

  @Override
  public boolean isInCall() {
    switch(state) {
      case RedPhone.STATE_IDLE:
        return false;
      case RedPhone.STATE_DIALING_CONNECTING:
      case RedPhone.STATE_RINGING:
      case RedPhone.STATE_ANSWERING:
      case RedPhone.STATE_CONNECTED:
        return true;
      default:
        Log.e(TAG, "Unhandled call state: " + state);
        return false;
    }
  }

  private static class ProximityLockRelease implements Thread.UncaughtExceptionHandler {
    private final LockManager lockManager;

    private ProximityLockRelease(LockManager lockManager) {
      this.lockManager = lockManager;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
      Log.d(TAG, "Uncaught exception - releasing proximity lock", throwable);
      lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
    }
  }
}
