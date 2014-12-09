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

package com.securecomcode.voice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.securecomcode.voice.codec.CodecSetupException;
import com.securecomcode.voice.contacts.PersonInfo;
import com.securecomcode.voice.crypto.zrtp.SASInfo;
import com.securecomcode.voice.directory.DirectoryUpdateReceiver;
import com.securecomcode.voice.monitor.MonitorConfigUpdateReceiver;
import com.securecomcode.voice.ui.ApplicationPreferencesActivity;
import com.securecomcode.voice.ui.CallControls;
import com.securecomcode.voice.ui.CallScreen;
import com.securecomcode.voice.ui.QualityReporting;
import com.securecomcode.voice.util.AudioUtils;
import com.securecomcode.voice.util.PeriodicActionUtils;


import java.security.Security;
import java.util.ArrayList;

/**
 * The main UI class for RedPhone.  Most of the heavy lifting is
 * done by RedPhoneService, so this activity is mostly responsible
 * for receiving events about the state of ongoing calls and displaying
 * the appropriate UI components.
 *
 * @author Moxie Marlinspike
 *
 */
public class RedPhone extends Activity {
  static {
    Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
  }


  private static final int REMOTE_TERMINATE = 0;
  private static final int LOCAL_TERMINATE  = 1;

  public static final int STATE_IDLE      = 0;
  public static final int STATE_RINGING   = 2;
  public static final int STATE_DIALING_CONNECTING   = 3;
  public static final int STATE_DIALING_CONNECTED_SENDING   = 4;
  public static final int STATE_DIALING_CONNECTED_WAITING   = 5;
  public static final int STATE_ANSWERING = 6;
  public static final int STATE_CONNECTED = 7;

  private static final int STANDARD_DELAY_FINISH    = 3000;
  public  static final int BUSY_SIGNAL_DELAY_FINISH = 5500;

  public static final int HANDLE_CALL_CONNECTED          = 0;
  public static final int HANDLE_WAITING_FOR_RESPONDER   = 1;
  public static final int HANDLE_SERVER_FAILURE          = 2;
  public static final int HANDLE_PERFORMING_HANDSHAKE    = 3;
  public static final int HANDLE_HANDSHAKE_FAILED        = 4;
  public static final int HANDLE_CONNECTING_TO_INITIATOR = 5;
  public static final int HANDLE_CALL_DISCONNECTED       = 6;
  public static final int HANDLE_CALL_RINGING            = 7;
  public static final int HANDLE_CODEC_INIT_FAILED       = 8;
  public static final int HANDLE_SERVER_MESSAGE          = 9;
  public static final int HANDLE_RECIPIENT_UNAVAILABLE   = 10;
  public static final int HANDLE_INCOMING_CALL           = 11;
  public static final int HANDLE_OUTGOING_CALL           = 12;
  public static final int HANDLE_CALL_BUSY               = 13;
  public static final int HANDLE_LOGIN_FAILED            = 14;
  public static final int HANDLE_CLIENT_FAILURE          = 15;
  public static final int HANDLE_DEBUG_INFO              = 16;
  public static final int HANDLE_NO_SUCH_USER            = 17;
  public static final int HANDLE_STATE_CONNECTED_SENDING = 18;
  public static final int HANDLE_STATE_CONNECTED_WAITING = 19;


  private final Handler callStateHandler = new CallStateHandler();

  private int state;
  private boolean deliveringTimingData = false;
  private RedPhoneService redPhoneService;
  private CallScreen callScreen;
  private BroadcastReceiver bluetoothStateReceiver;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    startServiceIfNecessary();
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    setContentView(R.layout.main);

    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

    initializeResources();
  }


  @Override
  public void onResume() {
    super.onResume();

    initializeServiceBinding();
    registerBluetoothReceiver();
  }


  @Override
  public void onPause() {
    super.onPause();

    unbindService(serviceConnection);
    unregisterReceiver(bluetoothStateReceiver);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfiguration) {
    super.onConfigurationChanged(newConfiguration);
  }

  private void startServiceIfNecessary() {
    Intent intent = this.getIntent();
    String action = null;

    if (intent != null)
      action = intent.getAction();

    if (action != null &&
        (action.equals(Intent.ACTION_CALL) || action.equals(Intent.ACTION_DIAL) ||
         action.equals("android.intent.action.CALL_PRIVILEGED")))
    {
      Log.w("RedPhone", "Calling startService from within RedPhone!");
      String number = Uri.decode(intent.getData().getEncodedSchemeSpecificPart());
      Intent serviceIntent = new Intent();
      serviceIntent.setClass(this, RedPhoneService.class);
      serviceIntent.putExtra(Constants.REMOTE_NUMBER, number);
      startService(serviceIntent);
    }
  }

  private void initializeServiceBinding() {
    Log.w("RedPhone", "Binding to RedPhoneService...");
    Intent bindIntent = new Intent(this, RedPhoneService.class);
    bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
  }

  private void initializeResources() {
    callScreen = (CallScreen)findViewById(R.id.callScreen);
    state      = STATE_IDLE;

    callScreen.setHangupButtonListener(new HangupButtonListener());
    callScreen.setIncomingCallActionListener(new IncomingCallActionListener());
    callScreen.setMuteButtonListener(new MuteButtonListener());
    callScreen.setAudioButtonListener(new AudioButtonListener());
    callScreen.setConfirmSasButtonListener(new ConfirmSasButtonListener());

    PeriodicActionUtils.scheduleUpdate(this, DirectoryUpdateReceiver.class);
    PeriodicActionUtils.scheduleUpdate(this, MonitorConfigUpdateReceiver.class);
  }

  private void sendInstallLink(String user) {
    String message =
        String.format(getString(R.string.RedPhone_id_like_to_call_you_securely_using_redphone_you_can_install_redphone_from_the_play_store_s),
                                "https://play.google.com/store/apps/details?id=com.securecomcode.voice");

    ArrayList<String> messages = SmsManager.getDefault().divideMessage(message);
    SmsManager.getDefault().sendMultipartTextMessage(user, null, messages, null, null);
  }

  private void handleSetMute(boolean enabled) {
    Intent intent = new Intent(this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_SET_MUTE);
    intent.putExtra(Constants.MUTE_VALUE, enabled);
    startService(intent);
  }

  private void handleAnswerCall() {
    state = STATE_ANSWERING;
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_answering));

    Intent intent = new Intent(this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_ANSWER_CALL);
    startService(intent);
  }

  private void handleDenyCall() {
    state = STATE_IDLE;

    Intent intent = new Intent(this, RedPhoneService.class);
    intent.setAction(RedPhoneService.ACTION_DENY_CALL);
    startService(intent);

    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_ending_call));
    delayedFinish();
  }

  private void handleIncomingCall(String remoteNumber) {
    state = STATE_RINGING;
    callScreen.setIncomingCall(PersonInfo.getInstance(this, remoteNumber));
  }

  private void handleOutgoingCall(String remoteNumber) {
    state = STATE_DIALING_CONNECTING;
    callScreen.setActiveCall(PersonInfo.getInstance(this, remoteNumber),
                             getString(R.string.RedPhone_connecting));
  }

  private void handleStateConnectedSending(String remoteNumber){
      state = STATE_DIALING_CONNECTED_SENDING;
      callScreen.setActiveCall(PersonInfo.getInstance(this, remoteNumber),
              getString(R.string.RedPhone_connected_sending));
  }

  private void handleStateConnectedWaiting(String remoteNumber){
      state = STATE_DIALING_CONNECTED_WAITING;
      callScreen.setActiveCall(PersonInfo.getInstance(this, remoteNumber),
              getString(R.string.RedPhone_connected_waiting));
  }

  private void handleTerminate( int terminationType ) {
    Log.w("RedPhone", "handleTerminate called");
    Log.w("RedPhone", "Termination Stack:", new Exception() );

    if( state == STATE_DIALING_CONNECTING ) {
      if (terminationType == LOCAL_TERMINATE) {
        callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                                 getString(R.string.RedPhone_cancelling_call));
      } else {
        callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                                 getString(R.string.RedPhone_call_rejected));
      }
    } else if (state != STATE_IDLE) {
      callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                               getString(R.string.RedPhone_ending_call));
    }

    state = STATE_IDLE;
    delayedFinish();
  }

  private void handleCallRinging() {
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_ringing));
  }

  private void handleCallBusy() {
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_busy));

    state = STATE_IDLE;
    delayedFinish(BUSY_SIGNAL_DELAY_FINISH);
  }

  private void handleCallConnected(SASInfo sas) {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES);
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_connected), sas);
    state = STATE_CONNECTED;
    redPhoneService.notifyCallConnectionUIUpdateComplete();
  }

  private void handleDebugInfo( String info ) {
//    debugCard.setInfo( info );
  }

  private void handleConnectingToInitiator() {
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_connecting));
  }

  private void handleHandshakeFailed() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_handshake_failed_exclamation));
    delayedFinish();
  }

  private void handleRecipientUnavailable() {
    state = STATE_IDLE;

    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_recipient_unavailable));
    delayedFinish();
  }

  private void handlePerformingHandshake() {
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_performing_handshake));
  }

  private void handleServerFailure() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                              getString(R.string.RedPhone_server_failed_exclamation));
    delayedFinish();
  }

  private void handleClientFailure(String msg) {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_client_failed));
    if( msg != null && !msg.equalsIgnoreCase("") && !isFinishing() ) {
      AlertDialog.Builder ad = new AlertDialog.Builder(this);
      ad.setTitle("Fatal Error");
      ad.setMessage(msg);
      ad.setCancelable(false);
      ad.setPositiveButton("Ok", new OnClickListener() {
        public void onClick(DialogInterface dialog, int arg) {
          RedPhone.this.handleTerminate(LOCAL_TERMINATE);
        }
      });
      ad.show();
    }
  }

  private void handleLoginFailed() {
    state = STATE_IDLE;
    callScreen.setActiveCall(redPhoneService.getRemotePersonInfo(),
                             getString(R.string.RedPhone_login_failed_exclamation));
    delayedFinish();
  }

  private void handleServerMessage(String message) {
    if( isFinishing() ) return; //we're already shutting down, this might crash
    AlertDialog.Builder ad = new AlertDialog.Builder(this);
    ad.setTitle(R.string.RedPhone_message_from_the_server);
    ad.setMessage(message);
    ad.setCancelable(false);
    ad.setPositiveButton(android.R.string.ok, new OnClickListener() {
      public void onClick(DialogInterface dialog, int arg) {
        RedPhone.this.handleTerminate(LOCAL_TERMINATE);
      }
    });
    ad.show();
  }

  private void handleNoSuchUser(final String user) {
    if (isFinishing()) return; // XXX Stuart added this check above, not sure why, so I'm repeating in ignorance. - moxie
    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
    dialog.setTitle(R.string.RedPhone_number_not_registered_with_redphone_exclamation);
    dialog.setIcon(android.R.drawable.ic_dialog_info);
    dialog.setMessage(R.string.RedPhone_the_number_you_dialed_is_not_registered_with_redphone_both_parties_of_a_call_need_to_have_redphone_installed);
    dialog.setCancelable(false);
    dialog.setPositiveButton(R.string.RedPhone_yes_exclamation, new OnClickListener() {
      public void onClick(DialogInterface dialog, int arg) {
        RedPhone.this.sendInstallLink(user);
        RedPhone.this.handleTerminate(LOCAL_TERMINATE);
      }
    });
    dialog.setNegativeButton(R.string.RedPhone_no_thanks_exclamation, new OnClickListener() {
      public void onClick(DialogInterface dialog, int arg) {
        RedPhone.this.handleTerminate(LOCAL_TERMINATE);
      }
    });
    dialog.show();
  }

  private void handleCodecFailure(CodecSetupException e) {
    Log.w("RedPhone", e);
    Toast.makeText(this, R.string.RedPhone_codec_failed_to_initialize, Toast.LENGTH_LONG).show();
    handleTerminate(LOCAL_TERMINATE);
  }

  private void delayedFinish() {
    delayedFinish(STANDARD_DELAY_FINISH);
  }

  private void delayedFinish(int delayMillis) {
    callStateHandler.postDelayed(new Runnable() {

    public void run() {
        RedPhone.this.finish();
    }}, delayMillis);
  }

  private class CallStateHandler extends Handler {
    @Override
    public void handleMessage(Message message) {
      Log.w("RedPhone", "Got message from service: " + message.what);
      switch (message.what) {
      case HANDLE_CALL_CONNECTED:          handleCallConnected((SASInfo)message.obj);               break;
      case HANDLE_SERVER_FAILURE:          handleServerFailure();                                   break;
      case HANDLE_PERFORMING_HANDSHAKE:    handlePerformingHandshake();                             break;
      case HANDLE_HANDSHAKE_FAILED:        handleHandshakeFailed();                                 break;
      case HANDLE_CONNECTING_TO_INITIATOR: handleConnectingToInitiator();                           break;
      case HANDLE_CALL_RINGING:            handleCallRinging();                                     break;
      case HANDLE_CALL_DISCONNECTED:       handleTerminate( REMOTE_TERMINATE );                     break;
      case HANDLE_SERVER_MESSAGE:          handleServerMessage((String)message.obj);                break;
      case HANDLE_NO_SUCH_USER:            handleNoSuchUser((String)message.obj);                   break;
      case HANDLE_RECIPIENT_UNAVAILABLE:   handleRecipientUnavailable();                            break;
      case HANDLE_CODEC_INIT_FAILED:	   handleCodecFailure( (CodecSetupException) message.obj ); break;
      case HANDLE_INCOMING_CALL:           handleIncomingCall((String)message.obj);                 break;
      case HANDLE_OUTGOING_CALL:           handleOutgoingCall((String)message.obj);                 break;
      case HANDLE_CALL_BUSY:               handleCallBusy();                                        break;
      case HANDLE_LOGIN_FAILED:            handleLoginFailed();                                     break;
      case HANDLE_CLIENT_FAILURE:   	   handleClientFailure((String)message.obj);                break;
      case HANDLE_DEBUG_INFO:			   handleDebugInfo((String)message.obj);					break;
      case HANDLE_STATE_CONNECTED_SENDING: handleStateConnectedSending((String)message.obj);		break;
      case HANDLE_STATE_CONNECTED_WAITING: handleStateConnectedWaiting((String)message.obj);	    break;

      }
    }
  }

  private class ConfirmSasButtonListener implements CallControls.ConfirmSasButtonListener {
    public void onClick() {
      Intent intent = new Intent(RedPhone.this, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_CONFIRM_SAS);
      startService(intent);
    }
  }

  private class HangupButtonListener implements CallControls.HangupButtonListener {
    public void onClick() {
      Log.w("RedPhone", "Hangup pressed, handling termination now...");
      Intent intent = new Intent(RedPhone.this, RedPhoneService.class);
      intent.setAction(RedPhoneService.ACTION_HANGUP_CALL);
      startService(intent);

      RedPhone.this.handleTerminate(LOCAL_TERMINATE);
    }
  }

  private class MuteButtonListener implements CallControls.MuteButtonListener {
    @Override
    public void onToggle(boolean isMuted) {
      RedPhone.this.handleSetMute(isMuted);
    }
  }

  private void registerBluetoothReceiver() {
    IntentFilter filter = new IntentFilter();
    filter.addAction(AudioUtils.getScoUpdateAction());
    bluetoothStateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        callScreen.notifyBluetoothChange();
      }
    };

    registerReceiver(bluetoothStateReceiver, filter);
    callScreen.notifyBluetoothChange();
  }

  private class AudioButtonListener implements CallControls.AudioButtonListener {
    @Override
    public void onAudioChange(AudioUtils.AudioMode mode) {
      switch(mode) {
        case DEFAULT:
          AudioUtils.enableDefaultRouting(RedPhone.this);
          break;
        case SPEAKER:
          AudioUtils.enableSpeakerphoneRouting(RedPhone.this);
          break;
        case HEADSET:
          AudioUtils.enableBluetoothRouting(RedPhone.this);
          break;
        default:
          throw new IllegalStateException("Audio mode " + mode + " is not supported.");
      }
    }
  }

  private class IncomingCallActionListener implements CallControls.IncomingCallActionListener {
    @Override
    public void onAcceptClick() {
      RedPhone.this.handleAnswerCall();
    }
    @Override
    public void onDenyClick() {
      RedPhone.this.handleDenyCall();
    }
  }

  private ServiceConnection serviceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      RedPhone.this.redPhoneService  = ((RedPhoneService.RedPhoneServiceBinder)service).getService();
      redPhoneService.setCallStateHandler(callStateHandler);

      PersonInfo personInfo = redPhoneService.getRemotePersonInfo();

      switch (redPhoneService.getState()) {
      case STATE_IDLE:      callScreen.reset();                                       break;
      case STATE_RINGING:   handleIncomingCall(personInfo.getNumber());               break;
      case STATE_DIALING_CONNECTING:   handleOutgoingCall(personInfo.getNumber());    break;
      case STATE_ANSWERING: handleAnswerCall();                                       break;
      case STATE_CONNECTED: handleCallConnected(redPhoneService.getCurrentCallSAS()); break;
      }
    }

    public void onServiceDisconnected(ComponentName name) {
      redPhoneService.setCallStateHandler(null);
    }
  };

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event)
  {

    boolean result = super.onKeyDown(keyCode, event);

    //limit the maximum volume to 0.9 [echo prevention]
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
      AudioManager audioManager = (AudioManager)
      ApplicationContext.getInstance().getContext().getSystemService(Context.AUDIO_SERVICE);
      int curVol = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
      int maxVol = (int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) * 0.9);
      Log.d("RedPhone", "volume up key press detected: " + curVol + " / " + maxVol );
      if(  curVol > maxVol ) {
        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,maxVol,0);
      }
    }
     return result;
  }
}